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
package com.erudika.para.server.rest;

import com.erudika.para.core.App;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.PluginResolver;
import com.erudika.para.server.ParaServer;
import static com.erudika.para.server.security.SecurityUtils.getPrincipalApp;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for inspecting and installing Para plugins.
 *
 * <p>
 * Plugins are thin JARs published to Maven Central, each carrying a manifest of its runtime dependencies. This
 * controller lets an admin UI list what is available, preview what an install would fetch, install it, and remove
 * it.</p>
 *
 * <p>
 * Access is restricted to the root app. Requests coming from any other app are rejected with
 * {@code 403 Forbidden}. The whole API can be turned off with the {@code plugins_api_enabled} config property.</p>
 *
 * <p>
 * A restart is required after installing or removing a plugin, because the plugin classloader is built once during
 * startup and cached. Selecting a different plugin via configuration also requires a restart, matching the
 * {@code requires restart} tag on those config properties.</p>
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@RestController
@RequestMapping(value = ParaServer.API_PATH + "/_plugins", produces = MediaType.APPLICATION_JSON_VALUE)
public final class PluginsApi {

	private static final Logger logger = LoggerFactory.getLogger(PluginsApi.class);
	private static final String GROUP_ID = "com.erudika";
	private static final Map<String, PluginDescriptor> CATALOGUE = new LinkedHashMap<>();

	static {
		add("para-dao-sql", "SQL", "DAO", List.of("H2DAO", "SqlDAO"),
				"Relational database DAO: H2, MySQL, PostgreSQL, MariaDB, SQL Server, Oracle, SQLite");
		add("para-dao-mongodb", "MongoDB", "DAO", List.of("MongoDBDAO"), "MongoDB document store DAO");
		add("para-dao-cassandra", "Cassandra", "DAO", List.of("CassandraDAO"), "Apache Cassandra DAO");
		add("para-dao-dynamodb", "DynamoDB", "DAO", List.of("AWSDynamoDAO"), "AWS DynamoDB DAO");
		add("para-search-lucene", "Lucene", "Search", List.of("LuceneSearch"),
				"Embedded full-text search index using Apache Lucene");
		add("para-search-elasticsearch", "Elasticsearch", "Search", List.of("ElasticSearch"),
				"Full-text search backed by an Elasticsearch or an OpenSearch cluster");
		add("para-cache-hazelcast", "Hazelcast", "Cache", List.of("HazelcastCache"),
				"Distributed in-memory cache");
		add("para-storage-s3", "S3", "FileStore", List.of("AWSFileStore"),
				"File storage backed by Amazon S3");
		add("para-queue-sqs", "SQS", "Queue", List.of("AWSQueue"), "Message queue backed by Amazon SQS");
		add("para-email-ses", "SES", "Emailer", List.of("AWSEmailer"), "Transactional email via Amazon SES");
	}

	private static void add(String artifactId, String displayName, String type,
			List<String> implClassNames, String description) {
		CATALOGUE.put(artifactId, new PluginDescriptor(artifactId, displayName, type, implClassNames, description));
	}

	/**
	 * No-args constructor.
	 */
	public PluginsApi() {
	}

	/**
	 * Lists the official plugins with their type, whether each is installed, and whether it is the one currently
	 * selected by configuration.
	 *
	 * @return a map keyed by Maven artifactId
	 */
	@GetMapping
	public ResponseEntity<?> list() {
		ResponseEntity<?> denied = denyIfNotAuthorized();
		if (denied != null) {
			return denied;
		}
		Map<String, Object> result = new TreeMap<>();
		Map<String, String> activePlugins = getAllActivePlugins();
		for (PluginDescriptor descriptor : CATALOGUE.values()) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("artifactId", descriptor.artifactId);
			entry.put("name", descriptor.displayName);
			entry.put("description", descriptor.description);
			entry.put("type", descriptor.type);
			entry.put("implementationClasses", descriptor.implClassNames);
			entry.put("installed", isInstalled(descriptor.artifactId));
			entry.put("active", isActive(activePlugins, descriptor));
			result.put(descriptor.artifactId, entry);
		}
		return ResponseEntity.ok(result);
	}

	/**
	 * Lists every JAR in the plugin folder, including plugins outside the official catalogue such as third-party or
	 * locally built ones.
	 *
	 * @return a sorted list of file names
	 */
	@GetMapping("/installed")
	public ResponseEntity<?> installed() {
		ResponseEntity<?> denied = denyIfNotAuthorized();
		if (denied != null) {
			return denied;
		}
		LinkedHashMap<String, Object> found = new LinkedHashMap<>();
		Map<String, String> activePlugins = getAllActivePlugins();
		for (Map.Entry<String, String> entry : activePlugins.entrySet()) {
			found.put(entry.getKey(), Map.of("active", true, "classes", List.of(entry.getValue())));
		}
		File[] files = pluginFolder().listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
		if (files != null) {
			for (File file : files) {
				String fn = Strings.CI.removeEnd(file.getName(), ".jar");
				String key = CATALOGUE.keySet().stream().filter(k -> Strings.CI.startsWith(fn, k)).findAny().orElse(fn);
				PluginDescriptor pd = CATALOGUE.get(key);
				if (pd != null) {
					boolean isActive = isActive(activePlugins, pd);
					found.put(key, Map.of("active", isActive,
							"file", file.getName(),
							"classes", isActive ? List.of(activePlugins.get(key)) : pd.implClassNames));
				}
			}
		}
		return ResponseEntity.ok(found);
	}

	/**
	 * Reports what installing a plugin would download, without touching the plugin folder.
	 *
	 * <p>
	 * Resolution happens in a temporary directory that is deleted afterwards, so an admin UI can show real numbers
	 * behind a confirmation prompt.</p>
	 *
	 * @param artifactId the Maven artifactId of the plugin
	 * @return the plugin's declared dependencies and the total download size
	 */
	@GetMapping("/{artifactId}/preview")
	public ResponseEntity<?> preview(@PathVariable String artifactId) {
		ResponseEntity<?> denied = denyIfNotAuthorized();
		if (denied != null) {
			return denied;
		}
		PluginDescriptor descriptor = CATALOGUE.get(artifactId);
		if (descriptor == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		if (descriptor.version.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
					.body(error("No default version is known for " + artifactId
							+ "; pass ?version=<version> to preview a specific release"));
		}
		Path tmpDir = null;
		try {
			tmpDir = Files.createTempDirectory("para-plugin-preview");
			PluginResolver resolver = new PluginResolver(tmpDir);
			PluginResolver.ResolutionResult result
					= resolver.resolvePlugin(GROUP_ID, artifactId, descriptor.version);

			long totalBytes = 0;
			List<String> deps = new ArrayList<>();
			for (File f : result.getDownloadedDependencies()) {
				deps.add(f.getName());
				totalBytes += f.length();
			}
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("artifactId", artifactId);
			body.put("version", descriptor.version);
			body.put("dependencies", result.getTotalDependencies());
			body.put("dependencyFiles", deps);
			body.put("downloadSizeBytes", totalBytes);
			body.put("installed", isInstalled(artifactId));
			return ResponseEntity.ok(body);
		} catch (IOException e) {
			logger.error("Could not preview plugin {}", artifactId, e);
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(e.getMessage()));
		} finally {
			deleteRecursively(tmpDir);
		}
	}

	/**
	 * Downloads a plugin and all of its dependencies into the plugin folder.
	 *
	 * <p>
	 * Every artifact is checksum-verified before it is written. Para must be restarted before the plugin can be
	 * loaded.</p>
	 *
	 * @param artifactId the Maven artifactId of the plugin
	 * @param request optional JSON body with a {@code version} field
	 * @return a summary of the installation
	 */
	@PostMapping("/{artifactId}/install")
	public ResponseEntity<?> install(@PathVariable String artifactId,
			@RequestBody(required = false) Map<String, Object> request) {
		ResponseEntity<?> denied = denyIfNotAuthorized();
		if (denied != null) {
			return denied;
		}
		PluginDescriptor descriptor = CATALOGUE.get(artifactId);
		if (descriptor == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		String version = descriptor.version;
		if (request != null && request.get("version") instanceof String v && !v.isBlank()) {
			version = v.trim();
		}
		if (version.isEmpty()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(error("A version is required for " + artifactId));
		}
		try {
			PluginResolver.ResolutionResult result
					= new PluginResolver().resolvePlugin(GROUP_ID, artifactId, version);
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("installed", true);
			body.put("artifactId", artifactId);
			body.put("version", version);
			body.put("dependencies", result.getTotalDependencies());
			body.put("newlyDownloaded", result.getDownloadedDependencies().size());
			body.put("restartRequired", true);
			body.put("message", "Plugin installed. Restart Para to load it.");
			logger.info("Installed plugin {}:{} via API", artifactId, version);
			return ResponseEntity.ok(body);
		} catch (IOException e) {
			logger.error("Failed to install plugin {}:{}", artifactId, version, e);
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(e.getMessage()));
		}
	}

	/**
	 * Removes a plugin JAR from the plugin folder.
	 *
	 * <p>
	 * Only the plugin JAR is deleted. Its dependencies stay, because plugins routinely share them and a dependency JAR
	 * carries no record of which plugin wanted it.</p>
	 *
	 * @param artifactId the Maven artifactId of the plugin
	 * @param version the installed version
	 * @return an empty body on success
	 */
	@DeleteMapping("/{artifactId}/{version}")
	public ResponseEntity<?> uninstall(@PathVariable String artifactId, @PathVariable String version) {
		ResponseEntity<?> denied = denyIfNotAuthorized();
		if (denied != null) {
			return denied;
		}
		File pluginJar = new File(pluginFolder(), artifactId + "-" + version + ".jar");
		if (!pluginJar.isFile()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		if (!pluginJar.delete()) {
			logger.error("Could not delete plugin JAR {}", pluginJar);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
		logger.info("Uninstalled plugin {}:{} - restart required", artifactId, version);
		return ResponseEntity.ok().build();
	}

	/**
	 * Rejects the request unless the plugins API is enabled and the caller is the root app. Root app users are
	 * allowed because {@code getPrincipalApp()} resolves the app behind an authenticated user.
	 *
	 * @return a {@code 403 Forbidden} response when access is denied, or {@code null} when the request may proceed
	 */
	private static ResponseEntity<?> denyIfNotAuthorized() {
		if (!Para.getConfig().pluginsApiEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("Plugins API is disabled."));
		}
		App app = getPrincipalApp();
		if (app == null || !app.isRootApp()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(error("Only the root app can manage plugins."));
		}
		return null;
	}

	private static File pluginFolder() {
		return new File(Para.getConfig().pluginFolder());
	}

	private static Map<String, String> getAllActivePlugins() {
		Map<String, String> active = new HashMap<>();
		for (String activePlugin : List.of(Para.getDAO().getDaoClassName(),
				Para.getSearch().getSearchClassName(),
				Para.getCache().getCacheClassName(),
				Para.getFileStore().getFileStoreClassName(),
				Para.getEmailer().getEmailerClassName())) {
			CATALOGUE.entrySet().stream().forEach(p -> {
				if (p.getValue().implClassNames.contains(activePlugin)) {
					active.put(p.getKey(), activePlugin);
				}
			});
		}
		return active;
	}

	private static boolean isInstalled(String artifactId) {
		File[] jars = pluginFolder().listFiles(
				(dir, name) -> name.startsWith(artifactId + "-") && name.endsWith(".jar"));
		return jars != null && jars.length > 0;
	}

	private static boolean isActive(Map<String, String> activePlugins, PluginDescriptor pd) {
		if (activePlugins == null || pd == null) {
			return false;
		}
		return pd.implClassNames.stream().anyMatch(p -> p.equalsIgnoreCase(activePlugins.get(pd.artifactId)));
	}

	private static Map<String, String> error(String message) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("error", message);
		return body;
	}

	private static void deleteRecursively(Path path) {
		if (path == null) {
			return;
		}
		try {
			if (!Files.exists(path)) {
				return;
			}
			try (var walk = Files.walk(path)) {
				walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
						.forEach(p -> {
							try {
								Files.deleteIfExists(p);
							} catch (IOException e) {
								// best effort: a leftover file in the system temp dir is harmless
								logger.debug("Could not delete {}", p, e);
							}
						});
			}
		} catch (IOException e) {
			logger.debug("Could not clean up preview directory {}", path, e);
		}
	}

	/**
	 * Metadata about one official plugin.
	 */
	private static final class PluginDescriptor {

		private final String artifactId;
		private final String displayName;
		private final String type;
		/**
		 * Every implementation class the plugin registers via ServiceLoader.
		 */
		private final List<String> implClassNames;
		private final String description;
		private final String version;

		PluginDescriptor(String artifactId, String displayName, String type,
				List<String> implClassNames, String description) {
			this.artifactId = artifactId;
			this.displayName = displayName;
			this.type = type;
			this.implClassNames = List.copyOf(implClassNames);
			this.description = description;
			this.version = defaultVersion(artifactId);
		}

		/**
		 * Reads the plugin version baked into this build from {@code plugin-versions.properties}, which Maven filters
		 * from the version properties in {@code para-server/pom.xml}. Returns an empty string when unknown, in which
		 * case the API asks the caller for a version rather than guessing.
		 */
		private static String defaultVersion(String artifactId) {
			try (InputStream is = PluginsApi.class.getClassLoader()
					.getResourceAsStream("plugin-versions.properties")) {
				if (is == null) {
					return "";
				}
				Properties props = new Properties();
				props.load(is);
				return props.getProperty("com.erudika." + artifactId, "").trim();
			} catch (IOException e) {
				logger.warn("Could not read plugin-versions.properties", e);
				return "";
			}
		}
	}
}
