/*
 * Copyright 2013-2026 Erudika. https://erudika.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For issues and patches go to: https://github.com/erudika
 */
package com.erudika.para.core.utils;

import com.erudika.para.core.utils.PluginResolver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link PluginResolver} against a local stub repository.
 *
 * <p>The resolver is pointed at a loopback HTTP server that serves a synthetic plugin JAR and
 * its dependencies, so the tests cover manifest parsing, checksum verification and the
 * fail-closed behaviour without reaching Maven Central.</p>
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
class PluginResolverTest {

	private HttpServer server;
	private Path libDir;
	private String baseUrl;
	private final Map<String, byte[]> repo = new ConcurrentHashMap<>();

	@BeforeEach
	void setUp() throws Exception {
		libDir = Files.createTempDirectory("para-plugin-lib");

		// One handler dispatches on path, so tests can override individual entries (for example to
		// publish a wrong digest) without registering a duplicate HttpServer context.
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			byte[] body = repo.get(exchange.getRequestURI().getPath());
			if (body == null) {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		});

		byte[] depA = "dependency-a".getBytes(StandardCharsets.UTF_8);
		byte[] depB = "dependency-b".getBytes(StandardCharsets.UTF_8);

		String manifest = "The following files have been resolved:\n"
				+ "   com.example:dependency-a:jar:1.0.0:compile -- module example.a\n"
				+ "   com.example:dependency-b:jar:2.0.0:compile -- module example.b\n";

		publish("/com/example/test-plugin/1.0.0/test-plugin-1.0.0.jar", buildPluginJar(manifest));
		publish("/com/example/dependency-a/1.0.0/dependency-a-1.0.0.jar", depA);
		publish("/com/example/dependency-b/2.0.0/dependency-b-2.0.0.jar", depB);

		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void downloadsPluginAndVerifiedDependencies() throws IOException {
		PluginResolver resolver = new PluginResolver(libDir, baseUrl);

		PluginResolver.ResolutionResult result = resolver.resolvePlugin("com.example", "test-plugin", "1.0.0");

		Assertions.assertTrue(Files.isRegularFile(libDir.resolve("test-plugin-1.0.0.jar")));
		Assertions.assertTrue(Files.isRegularFile(libDir.resolve("dependency-a-1.0.0.jar")));
		Assertions.assertEquals(2, result.getTotalDependencies());
		Assertions.assertEquals(2, result.getDownloadedDependencies().size());
		Assertions.assertNotNull(result.getPluginJar());
	}

	@Test
	void reusesAlreadyInstalledVerifiedArtifacts() throws IOException {
		PluginResolver resolver = new PluginResolver(libDir, baseUrl);
		resolver.resolvePlugin("com.example", "test-plugin", "1.0.0");

		PluginResolver again = new PluginResolver(libDir, baseUrl);
		PluginResolver.ResolutionResult second = again.resolvePlugin("com.example", "test-plugin", "1.0.0");

		Assertions.assertEquals(2, second.getTotalDependencies());
		Assertions.assertTrue(second.getDownloadedDependencies().isEmpty(),
				"second resolve should not re-download verified artifacts");
	}

	@Test
	void rejectsArtifactWhenChecksumDoesNotMatch() {
		// publish a deliberately wrong digest for dependency-b
		repo.put("/com/example/dependency-b/2.0.0/dependency-b-2.0.0.jar.sha256",
				"0000000000000000000000000000000000000000000000000000000000000000"
						.getBytes(StandardCharsets.UTF_8));

		PluginResolver resolver = new PluginResolver(libDir, baseUrl);
		Assertions.assertThrows(IOException.class,
				() -> resolver.resolvePlugin("com.example", "test-plugin", "1.0.0"));

		// the bad artifact must not be left behind, nor a partial temp file
		Assertions.assertFalse(Files.exists(libDir.resolve("dependency-b-2.0.0.jar")));
		Assertions.assertFalse(Files.exists(libDir.resolve("dependency-b-2.0.0.jar.part")));
	}

	@Test
	void failsClosedWhenNoChecksumIsPublished() {
		// remove both digests for dependency-b: the resolver must refuse to install it
		repo.remove("/com/example/dependency-b/2.0.0/dependency-b-2.0.0.jar.sha256");
		repo.remove("/com/example/dependency-b/2.0.0/dependency-b-2.0.0.jar.sha1");

		PluginResolver resolver = new PluginResolver(libDir, baseUrl);
		IOException e = Assertions.assertThrows(IOException.class,
				() -> resolver.resolvePlugin("com.example", "test-plugin", "1.0.0"));
		Assertions.assertTrue(e.getMessage().contains("checksum"),
				"expected a checksum failure but got: " + e.getMessage());
		Assertions.assertFalse(Files.exists(libDir.resolve("dependency-b-2.0.0.jar")));
	}

	@Test
	void fallsBackToSha1WhenSha256IsAbsent() throws IOException {
		// Maven Central publishes SHA-256 for recent artifacts but not for all older ones
		repo.remove("/com/example/dependency-b/2.0.0/dependency-b-2.0.0.jar.sha256");
		byte[] depB = "dependency-b".getBytes(StandardCharsets.UTF_8);
		repo.put("/com/example/dependency-b/2.0.0/dependency-b-2.0.0.jar.sha1",
				hex(sha1(depB)).getBytes(StandardCharsets.UTF_8));

		PluginResolver resolver = new PluginResolver(libDir, baseUrl);
		resolver.resolvePlugin("com.example", "test-plugin", "1.0.0");

		Assertions.assertTrue(Files.isRegularFile(libDir.resolve("dependency-b-2.0.0.jar")));
	}

	@Test
	void reportsMissingManifestClearly() throws IOException {
		publish("/com/example/bare/1.0.0/bare-1.0.0.jar", buildPluginJar(null));
		PluginResolver resolver = new PluginResolver(libDir, baseUrl);

		IOException e = Assertions.assertThrows(IOException.class,
				() -> resolver.resolvePlugin("com.example", "bare", "1.0.0"));
		Assertions.assertTrue(e.getMessage().contains("plugin-deps.txt"),
				"expected a missing-manifest error but got: " + e.getMessage());
	}

	@Test
	void keepsOnlyTheHighestVersionOfAConflictingDependency() throws IOException {
		// para-search-lucene and para-search-elasticsearch both need Lucene, at different patches.
		String esManifest = "The following files have been resolved:\n"
				+ "   org.apache.lucene:lucene-core:jar:10.5.0:compile -- module org.apache.lucene.core\n"
				+ "   com.fasterxml.jackson.core:jackson-databind:jar:2.21.5:compile\n";
		String luceneManifest = "The following files have been resolved:\n"
				+ "   org.apache.lucene:lucene-core:jar:10.5.1:compile -- module org.apache.lucene.core\n"
				+ "   com.fasterxml.jackson.core:jackson-databind:jar:2.21.5:compile\n";

		byte[] core1050 = "core-10.5.0".getBytes(StandardCharsets.UTF_8);
		byte[] core1051 = "core-10.5.1".getBytes(StandardCharsets.UTF_8);
		byte[] jackson = "jackson-2.21.5".getBytes(StandardCharsets.UTF_8);

		publish("/org/apache/lucene/lucene-core/10.5.0/lucene-core-10.5.0.jar", core1050);
		publish("/org/apache/lucene/lucene-core/10.5.1/lucene-core-10.5.1.jar", core1051);
		publish("/com/fasterxml/jackson/core/jackson-databind/2.21.5/jackson-databind-2.21.5.jar", jackson);
		publish("/com/example/es-search/1.0.0/es-search-1.0.0.jar", buildPluginJar(esManifest));
		publish("/com/example/lucene-search/1.0.0/lucene-search-1.0.0.jar", buildPluginJar(luceneManifest));

		PluginResolver resolver = new PluginResolver(libDir, baseUrl);
		resolver.resolvePlugin("com.example", "es-search", "1.0.0");
		Assertions.assertTrue(Files.isRegularFile(libDir.resolve("lucene-core-10.5.0.jar")));

		// installing the plugin that needs the newer Lucene must replace the older copy
		PluginResolver.ResolutionResult result =
				resolver.resolvePlugin("com.example", "lucene-search", "1.0.0");

		Assertions.assertTrue(Files.isRegularFile(libDir.resolve("lucene-core-10.5.1.jar")));
		Assertions.assertFalse(Files.isRegularFile(libDir.resolve("lucene-core-10.5.0.jar")),
				"only one Lucene version may exist on a flat classpath");
		Assertions.assertEquals(1, result.getSupersededDependencies().size());
		Assertions.assertEquals("lucene-core-10.5.0.jar", result.getSupersededDependencies().get(0).getName());

		// a library both plugins agree on is untouched
		Assertions.assertTrue(Files.isRegularFile(libDir.resolve("jackson-databind-2.21.5.jar")));
		Assertions.assertTrue(Files.isRegularFile(libDir.resolve("es-search-1.0.0.jar")),
				"installing a plugin must not remove another plugin");
	}

	@Test
	void neverRemovesADependencyAnotherPluginStillNeeds() throws IOException {
		// Two plugins share a library. When one stops needing it, the other's copy must survive.
		String withShared = "The following files have been resolved:\n"
				+ "   com.example:shared-lib:jar:1.0.0:compile\n";
		String stillNeedsShared = "The following files have been resolved:\n"
				+ "   com.example:shared-lib:jar:1.0.0:compile\n"
				+ "   com.example:dependency-b:jar:2.0.0:compile\n";
		String withoutShared = "The following files have been resolved:\n"
				+ "   com.example:dependency-b:jar:2.0.0:compile\n";

		publish("/com/example/shared-lib/1.0.0/shared-lib-1.0.0.jar", "shared".getBytes(StandardCharsets.UTF_8));
		publish("/com/example/dependency-b/2.0.0/dependency-b-2.0.0.jar", "b".getBytes(StandardCharsets.UTF_8));
		publish("/com/example/needs-shared/1.0.0/needs-shared-1.0.0.jar", buildPluginJar(withShared));
		publish("/com/example/needs-other/1.0.0/needs-other-1.0.0.jar", buildPluginJar(stillNeedsShared));

		PluginResolver resolver = new PluginResolver(libDir, baseUrl);
		resolver.resolvePlugin("com.example", "needs-shared", "1.0.0");
		resolver.resolvePlugin("com.example", "needs-other", "1.0.0");
		Assertions.assertTrue(Files.isRegularFile(libDir.resolve("shared-lib-1.0.0.jar")));

		// re-resolve needs-shared without the shared library; it must not take it away from needs-other
		publish("/com/example/needs-shared/2.0.0/needs-shared-2.0.0.jar", buildPluginJar(withoutShared));
		resolver.resolvePlugin("com.example", "needs-shared", "2.0.0");

		Assertions.assertTrue(Files.isRegularFile(libDir.resolve("shared-lib-1.0.0.jar")),
				"a dependency still claimed by another plugin must not be deleted");
	}

	@Test
	void neverRemovesAnotherPluginJar() throws IOException {
		// The Lucene plugin must not treat a plugin JAR as a stale dependency of a shorter name.
		String manifest = "The following files have been resolved:\n"
				+ "   com.example:dependency-a:jar:1.0.0:compile\n";
		publish("/com/example/dependency-a/1.0.0/dependency-a-1.0.0.jar", "a".getBytes(StandardCharsets.UTF_8));
		publish("/com/example/test-plugin/1.0.0/test-plugin-1.0.0.jar", buildPluginJar(manifest));
		// a plugin whose artifactId starts with the dependency artifactId
		Files.write(libDir.resolve("dependency-a-plugin-9.9.9.jar"), new byte[] {1});

		new PluginResolver(libDir, baseUrl).resolvePlugin("com.example", "test-plugin", "1.0.0");

		Assertions.assertTrue(Files.isRegularFile(libDir.resolve("dependency-a-plugin-9.9.9.jar")));
	}

	@Test
	void rejectsBlankCoordinates() {
		PluginResolver resolver = new PluginResolver(libDir, baseUrl);
		Assertions.assertThrows(IOException.class, () -> resolver.resolvePlugin("", "x", "1.0.0"));
		Assertions.assertThrows(IOException.class, () -> resolver.resolvePlugin("g", null, "1.0.0"));
	}

	/**
	 * Publishes an artifact plus a matching SHA-256 digest, the way Maven Central does.
	 */
	private void publish(String path, byte[] body) {
		repo.put(path, body);
		repo.put(path + ".sha256", hex(sha256(body)).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Builds a plugin JAR. A null manifest omits the entry entirely, as an unconfigured or
	 * older plugin JAR would.
	 */
	private static byte[] buildPluginJar(String manifest) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (JarOutputStream jos = new JarOutputStream(bos)) {
			jos.putNextEntry(new JarEntry("com/erudika/Marker.class"));
			jos.write(new byte[] {(byte) 0xCA, (byte) 0xFE});
			jos.closeEntry();
			if (manifest != null) {
				jos.putNextEntry(new JarEntry("META-INF/para/plugin-deps.txt"));
				jos.write(manifest.getBytes(StandardCharsets.UTF_8));
				jos.closeEntry();
			}
		}
		return bos.toByteArray();
	}

	private static byte[] sha256(byte[] data) {
		return digest("SHA-256", data);
	}

	private static byte[] sha1(byte[] data) {
		return digest("SHA-1", data);
	}

	private static byte[] digest(String algorithm, byte[] data) {
		try {
			return MessageDigest.getInstance(algorithm).digest(data);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(algorithm + " unavailable", e);
		}
	}

	private static String hex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}
}
