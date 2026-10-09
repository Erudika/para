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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads Para plugins and their runtime dependencies from Maven Central into the plugin folder.
 *
 * <p>Plugins are published as thin JARs. Each one carries a precomputed list of its runtime
 * dependencies in {@code META-INF/para/plugin-deps.txt}, generated at build time by
 * {@code maven-dependency-plugin:list}. This class fetches a plugin JAR, reads that manifest
 * without executing anything, then downloads each listed artifact into the plugin folder where
 * {@link Para#getParaClassLoader()} can see it.</p>
 *
 * <p>Every downloaded artifact is checksum-verified before it is moved into place. SHA-256 is
 * preferred, SHA-1 is accepted as a fallback for artifacts that predate SHA-256 publication, and
 * a download is discarded if neither digest is available. Files are written to a temporary
 * location and atomically renamed, so a partial or corrupt download can never become visible to
 * the classloader.</p>
 *
 * <p>The plugin folder is a flat classpath, so at most one version of any given library is kept.
 * When two plugins disagree about a version, the higher one wins and the lower one is removed.
 * This keeps the classpath deterministic regardless of the order plugins were installed in.</p>
 *
 * <p>Version conflicts are resolved per artifact, not per library. Installing
 * {@code para-search-lucene} after {@code para-search-elasticsearch} therefore leaves the Lucene
 * modules that only Elasticsearch needs at the version Elasticsearch asked for, and upgrades only
 * the modules both plugins declare. For the official plugins this means Lucene patch versions can
 * coexist, which is safe because a patch release of the same Lucene minor version is binary
 * compatible.</p>
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class PluginResolver {

	private static final Logger logger = LoggerFactory.getLogger(PluginResolver.class);
	/** Location of the dependency manifest inside a plugin JAR. */
	private static final String MANIFEST_PATH = "META-INF/para/plugin-deps.txt";
	private static final String CENTRAL = "https://repo1.maven.org/maven2/";
	private static final int CONNECT_TIMEOUT_SECONDS = 15;
	private static final int REQUEST_TIMEOUT_SECONDS = 120;
	/** Cap on a single artifact, to bound damage from a misconfigured or hostile response. */
	private static final long MAX_ARTIFACT_BYTES = 512L * 1024L * 1024L;
	/** Cap on the decompressed size of the dependency manifest. */
	private static final int MAX_MANIFEST_BYTES = 1024 * 1024;

	private final HttpClient httpClient;
	private final Path pluginFolder;
	private final String repositoryUrl;

	/**
	 * Creates a resolver that writes into the configured plugin folder.
	 */
	public PluginResolver() {
		this(new File(Para.getConfig().pluginFolder()).toPath(), CENTRAL);
	}

	/**
	 * Creates a resolver that writes into the given folder.
	 * @param pluginFolder the destination folder for plugins and their dependencies
	 */
	public PluginResolver(Path pluginFolder) {
		this(pluginFolder, CENTRAL);
	}

	/**
	 * Creates a resolver that writes into the given folder and fetches from the given repository.
	 * @param pluginFolder the destination folder for plugins and their dependencies
	 * @param repositoryUrl base URL of the Maven repository to download from
	 */
	public PluginResolver(Path pluginFolder, String repositoryUrl) {
		this.pluginFolder = pluginFolder.toAbsolutePath().normalize();
		this.repositoryUrl = repositoryUrl.endsWith("/") ? repositoryUrl : repositoryUrl + "/";
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	/**
	 * Downloads a plugin and all of its runtime dependencies into the plugin folder.
	 *
	 * <p>This is the bootstrap sequence: the plugin JAR is fetched first, then its embedded
	 * manifest is read as a ZIP entry (no classloading, no code execution), then each listed
	 * dependency is fetched and verified. The plugin is not usable until this method returns
	 * without error, and a restart is required before the plugin can be loaded.</p>
	 *
	 * @param groupId the Maven groupId of the plugin
	 * @param artifactId the Maven artifactId of the plugin
	 * @param version the plugin version
	 * @return a result describing what was downloaded and what was already present
	 * @throws IOException if the plugin cannot be fetched, its manifest is missing or unreadable,
	 * or any artifact fails checksum verification
	 */
	public ResolutionResult resolvePlugin(String groupId, String artifactId, String version) throws IOException {
		if (StringUtils.isAnyBlank(groupId, artifactId, version)) {
			throw new IOException("Missing plugin coordinates");
		}
		Files.createDirectories(pluginFolder);
		logger.info("Resolving plugin {}:{}:{}", groupId, artifactId, version);

		String pluginFileName = artifactId + "-" + version + ".jar";
		Path pluginJar = pluginFolder.resolve(pluginFileName);

		if (Files.isRegularFile(pluginJar)) {
			logger.info("Plugin JAR already present: {}", pluginFileName);
		} else {
			download(artifactUrl(groupId, artifactId, version), pluginJar);
		}

		List<String> dependencyLines = readDependencyManifest(pluginJar);
		if (dependencyLines.isEmpty()) {
			logger.info("Plugin {} declares no runtime dependencies", artifactId);
			return new ResolutionResult(pluginJar.toFile(), List.of(), List.of(), 0);
		}

		// groupId:artifactId -> version, first declaration wins so that ordering is deterministic
		Map<String, String> coordinates = new LinkedHashMap<>();
		// Dependency file names do not include the groupId, so two groups sharing an artifactId would
		// collide on disk. Track the file name to make that visible rather than silently overwriting.
		Map<String, String> ownersByFileName = new LinkedHashMap<>();
		for (String line : dependencyLines) {
			String[] parts = line.trim().split(":");
			// format is groupId:artifactId:type:version:scope
			if (parts.length < 4 || StringUtils.isBlank(parts[0]) || StringUtils.isBlank(parts[1])) {
				continue;
			}
			String gav = parts[0] + ":" + parts[1];
			if (coordinates.putIfAbsent(gav, parts[3]) != null) {
				continue;
			}
			String fileName = parts[1] + "-" + parts[3] + ".jar";
			String previousOwner = ownersByFileName.putIfAbsent(fileName, gav);
			if (previousOwner != null && !previousOwner.equals(gav)) {
				logger.warn("Plugin {} maps both {} and {} onto the file {}. The folder is flat, so one"
						+ " of them will be unavailable at runtime", artifactId, previousOwner, gav, fileName);
			}
			logger.debug("Plugin {} requires {}", artifactId, gav);
		}

		Set<String> resolvedNames = new LinkedHashSet<>();
		List<File> downloaded = new ArrayList<>();
		List<File> superseded = new ArrayList<>();
		// dependency files that some plugin in this folder previously installed and therefore owns
		Set<String> managed = managedDependencies(null);
		for (Map.Entry<String, String> entry : coordinates.entrySet()) {
			String[] gav = entry.getKey().split(":");
			String depArtifactId = gav[1];
			String depVersion = entry.getValue();

			// The plugin folder is a flat classpath, so two copies of one library make class loading
			// depend on directory listing order. If a newer version is already installed for another
			// plugin, reuse it instead of downloading the one declared here.
			String newest = newestManagedVersion(depArtifactId, depVersion, managed);
			if (newest != null) {
				logger.info("{}:{} is already satisfied by {} installed for another plugin",
						depArtifactId, depVersion, newest);
				resolvedNames.add(depArtifactId + "-" + newest + ".jar");
				continue;
			}

			String depFileName = depArtifactId + "-" + depVersion + ".jar";
			resolvedNames.add(depFileName);
			Path target = pluginFolder.resolve(depFileName);
			if (Files.isRegularFile(target)) {
				// already downloaded, but re-verify so a corrupted or truncated file is repaired
				if (isValidChecksum(gav[0], depArtifactId, depVersion)) {
					logger.info("Dependency already present: {}", depFileName);
					continue;
				}
				logger.warn("Checksum mismatch for existing {}, re-downloading", depFileName);
			}
			download(artifactUrl(gav[0], depArtifactId, depVersion), target);
			downloaded.add(target.toFile());

			// Clean up older copies left behind by a previous install of a different plugin.
			superseded.addAll(removeOlderVersions(depArtifactId, depVersion, managed));
		}
		removeStaleDependencies(artifactId, resolvedNames);
		logger.info("Resolved plugin {}:{}:{} ({} dependencies downloaded, {} already present, {} superseded)",
				groupId, artifactId, version, downloaded.size(),
				coordinates.size() - downloaded.size(), superseded.size());
		return new ResolutionResult(pluginJar.toFile(), List.copyOf(downloaded),
				List.copyOf(superseded), coordinates.size());
	}

	/**
	 * Reads the dependency markers of every plugin in the folder, giving the set of dependency files
	 * that Para installed and therefore owns. Files not listed in any marker were put there by an
	 * operator and are never touched.
	 *
	 * @param excludeArtifactId a plugin whose marker to ignore, or null to include all of them
	 * @return the file names of all managed dependency JARs
	 */
	private Set<String> managedDependencies(String excludeArtifactId) {
		Set<String> managed = new LinkedHashSet<>();
		File[] markers = pluginFolder.toFile().listFiles(
				(dir, name) -> name.endsWith(".deps")
						&& !(excludeArtifactId != null && name.equals(excludeArtifactId + ".deps")));
		if (markers == null) {
			return managed;
		}
		for (File marker : markers) {
			try {
				managed.addAll(Files.readAllLines(marker.toPath()));
			} catch (IOException e) {
				logger.warn("Could not read dependency marker {}", marker.getName(), e);
			}
		}
		return managed;
	}

	/**
	 * Finds the highest version of an artifact already installed for another plugin.
	 *
	 * @param artifactId the Maven artifactId to look for
	 * @param requiredVersion the version the plugin being installed asks for
	 * @param managed the dependency files owned by plugins in this folder
	 * @return the higher installed version, or null if nothing newer is installed
	 */
	private String newestManagedVersion(String artifactId, String requiredVersion, Set<String> managed) {
		String prefix = artifactId + "-";
		String newest = null;
		for (String fileName : managed) {
			if (!fileName.startsWith(prefix) || !fileName.endsWith(".jar")) {
				continue;
			}
			String version = fileName.substring(prefix.length(), fileName.length() - ".jar".length());
			if (!Files.isRegularFile(pluginFolder.resolve(fileName))) {
				continue;
			}
			if (compareVersions(version, requiredVersion) > 0
					&& (newest == null || compareVersions(version, newest) > 0)) {
				newest = version;
			}
		}
		return newest;
	}

	/**
	 * Removes managed older versions of an artifact from the plugin folder, keeping the highest one.
	 *
	 * <p>Two plugins can require different versions of the same library. For example
	 * {@code para-search-lucene} and {@code para-search-elasticsearch} both pull in Lucene, at
	 * different patch versions. Because the folder is a flat classpath, leaving both copies installed
	 * would make it undefined which one wins. The highest version is kept, which is the convention
	 * Maven uses and is safe for the patch-level conflicts that occur between official plugins.</p>
	 *
	 * @param artifactId the Maven artifactId whose other versions should be removed
	 * @param keepVersion the version that was just installed and must be retained
	 * @param managed the dependency files owned by plugins in this folder
	 * @return the files that were removed
	 * @throws IOException if a stale file cannot be deleted
	 */
	private List<File> removeOlderVersions(String artifactId, String keepVersion, Set<String> managed)
			throws IOException {
		List<File> removed = new ArrayList<>();
		for (String fileName : managed) {
			String prefix = artifactId + "-";
			if (!fileName.startsWith(prefix) || !fileName.endsWith(".jar")) {
				continue;
			}
			String version = fileName.substring(prefix.length(), fileName.length() - ".jar".length());
			if (version.equals(keepVersion) || compareVersions(keepVersion, version) <= 0) {
				continue;
			}
			Path stale = pluginFolder.resolve(fileName);
			if (Files.deleteIfExists(stale)) {
				logger.warn("Removed older dependency {} in favour of {}:{}",
						fileName, artifactId, keepVersion);
				removed.add(stale.toFile());
			}
		}
		return removed;
	}

	/**
	 * Compares two dotted version strings numerically, falling back to a lexical comparison of any
	 * non-numeric segments. Returns a positive number if {@code a} is newer than {@code b}.
	 */
	private static int compareVersions(String a, String b) {
		String[] left = a.split("[.\\-]");
		String[] right = b.split("[.\\-]");
		int length = Math.max(left.length, right.length);
		for (int i = 0; i < length; i++) {
			String l = i < left.length ? left[i] : "0";
			String r = i < right.length ? right[i] : "0";
			int result;
			if (hasDigit(l) && hasDigit(r)) {
				result = Long.compare(Long.parseLong(stripLeadingZeros(l)), Long.parseLong(stripLeadingZeros(r)));
			} else {
				result = l.compareTo(r);
			}
			if (result != 0) {
				return result;
			}
		}
		return 0;
	}

	private static String stripLeadingZeros(String s) {
		int i = 0;
		while (i < s.length() - 1 && s.charAt(i) == '0') {
			i++;
		}
		return s.substring(i);
	}

	private static boolean hasDigit(String s) {
		if (s.isEmpty()) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isDigit(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Removes managed JARs that this plugin installed but no longer needs.
	 *
	 * <p>A plugin folder is shared across plugins, so a file is only deleted when two conditions
	 * hold: this plugin installed it, as recorded in its dependency marker, and no other plugin in
	 * the folder still claims it. Without the second check, upgrading one plugin would break
	 * another that shares the same library.</p>
	 *
	 * @param artifactId the plugin whose marker is being updated
	 * @param keep the dependency file names this plugin now requires
	 */
	private void removeStaleDependencies(String artifactId, Set<String> keep) {
		Path marker = pluginFolder.resolve(artifactId + ".deps");
		try {
			List<String> previous = Files.isRegularFile(marker) ? Files.readAllLines(marker) : List.of();
			Set<String> claimedByOthers = managedDependencies(artifactId);
			for (String old : previous) {
				if (keep.contains(old) || claimedByOthers.contains(old)) {
					continue;
				}
				Path stale = pluginFolder.resolve(old);
				if (Files.deleteIfExists(stale)) {
					logger.info("Removed stale dependency {}", old);
				}
			}
			Files.write(marker, keep);
		} catch (IOException e) {
			// Non-fatal: leftover files are a disk-usage concern, not a correctness one.
			logger.warn("Could not update dependency marker for plugin {}", artifactId, e);
		}
	}

	/**
	 * Reads the dependency manifest out of a plugin JAR.
	 *
	 * <p>The JAR is opened as a ZIP and the entry is read directly. Nothing on the classpath is
	 * touched, so this is safe to call on a JAR whose dependencies are not yet present.</p>
	 */
	private List<String> readDependencyManifest(Path pluginJar) throws IOException {
		List<String> lines = new ArrayList<>();
		try (JarFile jar = new JarFile(pluginJar.toFile())) {
			ZipEntry entry = jar.getEntry(MANIFEST_PATH);
			if (entry == null) {
				throw new IOException("Plugin JAR " + pluginJar.getFileName()
						+ " has no " + MANIFEST_PATH + " manifest; it was likely not built with"
						+ " maven-dependency-plugin configured");
			}
			try (InputStream is = jar.getInputStream(entry)) {
				byte[] buf = readBounded(is, MAX_MANIFEST_BYTES, MANIFEST_PATH);
				for (String line : new String(buf, StandardCharsets.UTF_8).split("\\R")) {
					String trimmed = line.trim();
					// skip the "The following files have been resolved:" header and comments
					if (!trimmed.isEmpty() && !trimmed.startsWith("The following")
							&& trimmed.contains("jar:") && !trimmed.startsWith("#")) {
						lines.add(trimmed);
					}
				}
			}
		}
		if (lines.isEmpty()) {
			logger.debug("Dependency manifest in {} is empty", pluginJar.getFileName());
		}
		return lines;
	}

	/**
	 * Downloads an artifact and verifies its checksum before moving it into place.
	 */
	private void download(String url, Path target) throws IOException {
		logger.debug("Downloading {}", url);
		byte[] payload = fetch(url);
		verifyChecksum(url, payload);

		Files.createDirectories(target.getParent());
		Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
		try {
			Files.write(tmp, payload);
			// atomic so a half-written JAR is never visible to the classloader
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(tmp);
		}
		logger.debug("Installed {}", target.getFileName());
	}

	/**
	 * Fetches a URL, enforcing a size ceiling.
	 */
	private byte[] fetch(String url) throws IOException {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
					.header("User-Agent", "Para-PluginResolver")
					.GET().build();
			HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200) {
				throw new IOException("Download failed with HTTP " + response.statusCode() + ": " + url);
			}
			byte[] body = response.body();
			if (body.length > MAX_ARTIFACT_BYTES) {
				throw new IOException("Artifact exceeds maximum size of " + MAX_ARTIFACT_BYTES + " bytes: " + url);
			}
			return body;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Download interrupted: " + url, e);
		}
	}

	/**
	 * Verifies a downloaded artifact against the checksum published by Maven Central.
	 * SHA-256 is preferred; SHA-1 is accepted only when no SHA-256 exists. Fails closed when
	 * neither digest is published, so an artifact is never installed unverified.
	 */
	private void verifyChecksum(String artifactUrl, byte[] payload) throws IOException {
		String baseUrl = artifactUrl.substring(0, artifactUrl.lastIndexOf('/'));
		String fileName = artifactUrl.substring(artifactUrl.lastIndexOf('/') + 1);

		String sha256 = fetchDigest(baseUrl, fileName + ".sha256");
		if (sha256 != null) {
			String actual = sha256(payload);
			if (!constantTimeEquals(sha256, actual)) {
				throw new IOException("SHA-256 checksum mismatch for " + fileName
						+ " (expected " + sha256 + ", got " + actual + "); download discarded");
			}
			logger.debug("Verified {} with SHA-256", fileName);
			return;
		}

		String sha1 = fetchDigest(baseUrl, fileName + ".sha1");
		if (sha1 != null) {
			String actual = sha1(payload);
			if (!constantTimeEquals(sha1, actual)) {
				throw new IOException("SHA-1 checksum mismatch for " + fileName
						+ " (expected " + sha1 + ", got " + actual + "); download discarded");
			}
			// SHA-1 is a weak digest. Surface it so operators know what verified the artifact.
			logger.warn("{} has no published SHA-256; falling back to SHA-1 (weak)", fileName);
			return;
		}

		throw new IOException("No published checksum (.sha256 or .sha1) for " + fileName
				+ "; refusing to install an unverified artifact");
	}

	/**
	 * Checks whether the currently installed file for an artifact is still valid.
	 */
	private boolean isValidChecksum(String groupId, String artifactId, String version) {
		Path target = pluginFolder.resolve(artifactId + "-" + version + ".jar");
		if (!Files.isRegularFile(target)) {
			return false;
		}
		String artifactUrl = artifactUrl(groupId, artifactId, version);
		String baseUrl = artifactUrl.substring(0, artifactUrl.lastIndexOf('/'));
		String fileName = artifactId + "-" + version + ".jar";
		try {
			byte[] existing = Files.readAllBytes(target);
			String sha256 = fetchDigest(baseUrl, fileName + ".sha256");
			if (sha256 != null) {
				return constantTimeEquals(sha256, sha256(existing));
			}
			String sha1 = fetchDigest(baseUrl, fileName + ".sha1");
			return sha1 != null && constantTimeEquals(sha1, sha1(existing));
		} catch (IOException e) {
			logger.debug("Could not verify existing {}", fileName, e);
			return false;
		}
	}

	/**
	 * Fetches a published digest, returning null when it is not present.
	 */
	private String fetchDigest(String baseUrl, String suffix) {
		try {
			byte[] body = fetch(baseUrl + "/" + suffix);
			return new String(body, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
		} catch (IOException e) {
			logger.debug("No published digest {}", suffix, e);
			return null;
		}
	}

	private String artifactUrl(String groupId, String artifactId, String version) {
		return repositoryUrl + groupId.replace('.', '/') + "/" + artifactId + "/" + version
				+ "/" + artifactId + "-" + version + ".jar";
	}

	private static String sha256(byte[] data) {
		return digest("SHA-256", data);
	}

	private static String sha1(byte[] data) {
		return digest("SHA-1", data);
	}

	private static String digest(String algorithm, byte[] data) {
		try {
			return hex(MessageDigest.getInstance(algorithm).digest(data));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(algorithm + " is required but unavailable", e);
		}
	}

	private static String hex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}

	private static boolean constantTimeEquals(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

	private static byte[] readBounded(InputStream is, int max, String what) throws IOException {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int total = 0;
		int n;
		while ((n = is.read(buf)) != -1) {
			total += n;
			if (total > max) {
				throw new IOException(what + " is larger than the permitted " + max + " bytes");
			}
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}

	/**
	 * The outcome of resolving a single plugin.
	 */
	public static final class ResolutionResult {

		private final File pluginJar;
		private final List<File> downloadedDependencies;
		private final List<File> supersededDependencies;
		private final int totalDependencies;

		ResolutionResult(File pluginJar, List<File> downloadedDependencies,
				List<File> supersededDependencies, int totalDependencies) {
			this.pluginJar = pluginJar;
			this.downloadedDependencies = Collections.unmodifiableList(downloadedDependencies);
			this.supersededDependencies = Collections.unmodifiableList(supersededDependencies);
			this.totalDependencies = totalDependencies;
		}

		/**
		 * Returns the plugin JAR in the plugin folder.
		 * @return the plugin JAR file
		 */
		public File getPluginJar() {
			return pluginJar;
		}

		/**
		 * Returns the dependency JARs that were downloaded by this call.
		 * @return an unmodifiable list of newly downloaded files, possibly empty
		 */
		public List<File> getDownloadedDependencies() {
			return downloadedDependencies;
		}

		/**
		 * Returns the older-version dependency JARs that were removed because another plugin required
		 * a newer version of the same library.
		 * @return an unmodifiable list of removed files, possibly empty
		 */
		public List<File> getSupersededDependencies() {
			return supersededDependencies;
		}

		/**
		 * Returns the total number of runtime dependencies the plugin declares.
		 * @return the dependency count
		 */
		public int getTotalDependencies() {
			return totalDependencies;
		}
	}
}
