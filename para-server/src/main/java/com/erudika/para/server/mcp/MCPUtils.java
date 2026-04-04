package com.erudika.para.server.mcp;

import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.annotations.Documented;
import com.erudika.para.core.annotations.Stored;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.ParaConfig;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.server.security.SecurityUtils;
import com.erudika.para.server.utils.HealthUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Shared helper that exposes Para data as MCP-friendly structures.
 */
@Component
public class MCPUtils {

	private static final int DEFAULT_SEARCH_LIMIT = 10;
	private static final int MAX_SEARCH_RESULTS = 50;
	private final Environment springEnv;
	private final List<ConfigEntry> orderedEntries;
	private final Map<String, ConfigEntry> configIndex;
	private final Set<String> ignoredFields = Set.of("id", "timestamp", "appid", "parentid", "creatorid",
					"updated", "tags", "votes", "version", "stored", "indexed", "cached");

	/**
	 * Default constructor.
	 * @param springEnvironment spring env
	 */
	public MCPUtils(Environment springEnvironment) {
		this.springEnv = springEnvironment;
		this.orderedEntries = Collections.unmodifiableList(buildConfigEntries());
		this.configIndex = orderedEntries.stream().
				collect(Collectors.toMap(entry -> entry.key().
				toLowerCase(Locale.ROOT), entry -> entry, (left, right) -> left, LinkedHashMap::new));
	}

	App authApp() {
		if (!isMcpEnabled()) {
			throw new MCPException(MCPException.ErrorType.NOT_FOUND, "MCP Server is not enabled.");
		}
		App app = SecurityUtils.getPrincipalApp();
		if (app == null) {
			throw new MCPException(MCPException.ErrorType.INVALID_INPUT,
					"Authentication required. No valid app found in security context.");
		}
		return app;
	}

	static boolean isMcpEnabled() {
		return !Para.getConfig().mcpServerMode().equalsIgnoreCase("off");
	}

	static boolean isWriteEnabled() {
		return isMcpEnabled() && Para.getConfig().mcpServerMode().equalsIgnoreCase("rw");
	}

	static void requireWritePermission() {
		if (!isWriteEnabled()) {
			throw new MCPException(MCPException.ErrorType.INVALID_INPUT,
					"MCP server is in read-only mode - this operation requires 'rw' (read-write) mode.");
		}
	}

	static void requireRootApp(App app) {
		if (!app.isRootApp()) {
			throw new MCPException(MCPException.ErrorType.INVALID_INPUT,
					"Permission denied. Only the root app can perform this operation.");
		}
	}

	String getMcpMode() {
		return Para.getConfig().mcpServerMode();
	}

	boolean isReadOnly() {
		return !isWriteEnabled();
	}

	Map<String, Object> serverMetadata(App app) {
		ParaConfig config = Para.getConfig();
		Map<String, Object> snapshot = new LinkedHashMap<>();
		Map<String, Object> server = new LinkedHashMap<>();
		String contextPath = normalizeContextPath(config.serverContextPath());
		server.put("port", config.serverPort());
		server.put("healthy", HealthUtils.getInstance().isHealthy());
		server.put("version", Para.getVersion());
		server.put("revision", Para.getRevision());
		server.put("contextPath", contextPath);
		server.put("baseUri", "http://localhost:" + config.serverPort() + contextPath);
		snapshot.put("server", server);


		Map<String, Object> appInfo = new LinkedHashMap<>();
		appInfo.put("id", app.getId());
		appInfo.put("appIdentifier", app.getAppIdentifier());
		appInfo.put("name", app.getName());
		appInfo.put("timestamp", app.getTimestamp());
		appInfo.put("creatorid", app.getCreatorid());
		appInfo.put("isRootApp", app.isRootApp());
		appInfo.put("active", app.getActive());
		appInfo.put("tokenValiditySec", app.getTokenValiditySec());
		appInfo.put("shared", app.isSharingTable());
		server.put("currentParaApp", appInfo);

		Map<String, Object> environment = new LinkedHashMap<>();
		environment.put("appName", config.appName());
		environment.put("environment", config.environment());
		environment.put("cluster", config.clusterName());
		environment.put("workerId", config.workerId());
		snapshot.put("environment", environment);

		Map<String, Object> modules = new LinkedHashMap<>();
		modules.put("dao", config.daoPlugin());
		modules.put("search", config.searchPlugin());
		modules.put("cache", config.cachePlugin());
		modules.put("queue", config.queuePlugin());
		modules.put("fileStore", config.fileStoragePlugin());
		modules.put("emailer", config.emailerPlugin());
		snapshot.put("modules", modules);

		Map<String, Object> features = new LinkedHashMap<>();
		features.put("apiEnabled", config.apiEnabled());
		features.put("landingPageEnabled", config.landingPageEnabled());
		features.put("webhooksEnabled", config.webhooksEnabled());
		features.put("queuePollingEnabled", config.queuePollingEnabled());
		features.put("healthCheckEnabled", config.healthCheckEnabled());
		features.put("searchEnabled", config.isSearchEnabled());
		features.put("cacheEnabled", config.isCacheEnabled());
		snapshot.put("features", features);

		Map<String, Object> queuePolling = new LinkedHashMap<>();
		queuePolling.put("pollingIntervalSec", config.queuePollingIntervalSec());
		queuePolling.put("pollingWaitSec", config.queuePollingWaitSec());
		snapshot.put("queuePolling", queuePolling);

		Map<String, Object> configMetadata = new LinkedHashMap<>();
		configMetadata.put("totalConfigurationProperties", orderedEntries.size());
		configMetadata.put("referenceResource", "para:///config");
		configMetadata.put("searchTool", "config_search");
		snapshot.put("configurationMetadata", configMetadata);

		snapshot.put("mcpTransport", resolveMcpTransportMetadata(config.serverPort()));
		snapshot.put("instructions", "Use config_search to search for configuration options,"
				+ "para:///config to read all the configuration documentation.");
		return snapshot;
	}

	Optional<Map<String, Object>> describeConfig(String identifier) {
		return resolveEntry(identifier).map(entry -> entry.toMap(readConfigValue(entry)));
	}

	List<Map<String, Object>> searchConfig(String query, Integer limit) {
		String needle = StringUtils.trimToEmpty(query).toLowerCase(Locale.ROOT);
		int normalizedLimit = normalizeLimit(limit);
		return orderedEntries.stream()
				.filter(entry -> entry.matches(needle))
				.limit(normalizedLimit)
				.map(entry -> entry.toMap(readConfigValue(entry)))
				.collect(Collectors.toList());
	}

	String renderConfigDocumentation() {
		return Para.getConfig().renderConfigDocumentation("markdown", true);
	}

	List<String> suggestions(String identifier, int limit) {
		String needle = StringUtils.trimToEmpty(identifier).toLowerCase(Locale.ROOT);
		int normalizedLimit = Math.max(1, limit);
		return orderedEntries.stream()
				.map(ConfigEntry::key)
				.filter(key -> needle.isEmpty() || key.toLowerCase(Locale.ROOT).contains(needle))
				.limit(normalizedLimit)
				.collect(Collectors.toList());
	}

	Map<String, Class<?>> getRequiredFields(ParaObject obj) {
		Map<String, Class<?>> required = new LinkedHashMap<>();
		Class<?> clazz = obj.getClass();
		while (clazz != null && !clazz.equals(Object.class)) {
			for (Field field : clazz.getDeclaredFields()) {
				String fieldName = field.getName();
				// Skip ignored fields
				if (ignoredFields.contains(fieldName)) {
					continue;
				}
				boolean isStored = field.isAnnotationPresent(Stored.class);
				boolean isRequired = field.isAnnotationPresent(NotBlank.class)
						|| field.isAnnotationPresent(NotEmpty.class)
						|| field.isAnnotationPresent(NotNull.class);

				if (isStored && isRequired && !required.containsKey(fieldName)) {
					required.put(fieldName, field.getType());
				}
			}
			clazz = clazz.getSuperclass();
		}
		return required;
	}

	static ReadResourceResult jsonResource(String uri, Object payload) {
		return textResource(uri, "application/json", toJson(payload));
	}

	static ReadResourceResult textResource(String uri, String mimeType, String body) {
		return new ReadResourceResult(List.of(new TextResourceContents(uri, mimeType, body)));
	}

	String buildUnknownKeyMessage(String key) {
		String safeKey = StringUtils.defaultIfBlank(key, "<empty>");
		List<String> suggestions = suggestions(key, 5);
		if (suggestions.isEmpty()) {
			return "Unknown configuration key: " + safeKey;
		}
		return "Unknown configuration key: " + safeKey + ". Did you mean: " + String.join(", ", suggestions) + "?";
	}

	static String toJson(Object payload) {
		try {
			return ParaObjectUtils.getJsonMapper().writeValueAsString(payload);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize MCP payload.", e);
		}
	}

	private Optional<ConfigEntry> resolveEntry(String identifier) {
		if (StringUtils.isBlank(identifier)) {
			return Optional.empty();
		}
		return Optional.ofNullable(configIndex.get(identifier.trim().toLowerCase(Locale.ROOT)));
	}

	private Object readConfigValue(ConfigEntry entry) {
		Object rawValue = Para.getConfig().getConfigValue(entry.key(), entry.defaultValue());
		return maskIfSensitive(entry, rawValue);
	}

	private Object maskIfSensitive(ConfigEntry entry, Object rawValue) {
		if (rawValue == null) {
			return null;
		}
		if (Config.isSensitiveData(entry.key())) {
			return "<redacted>";
		}
		// If value looks like a credential pattern, redact it
		if (rawValue instanceof String strValue) {
			// Redact values that look like tokens (long alphanumeric strings)
			if (strValue.length() > 32 && strValue.matches("[A-Za-z0-9+/=_-]+")) {
				return "<redacted>";
			}
		}
		return rawValue;
	}

	private int normalizeLimit(Integer limit) {
		if (limit == null || limit < 1) {
			return DEFAULT_SEARCH_LIMIT;
		}
		return Math.min(limit, MAX_SEARCH_RESULTS);
	}

	private String normalizeContextPath(String contextPath) {
		if (StringUtils.isBlank(contextPath) || "/".equals(contextPath)) {
			return "";
		}
		return contextPath;
	}

	private Map<String, Object> resolveMcpTransportMetadata(int serverPort) {
		Map<String, Object> transport = new LinkedHashMap<>();
		String protocol = springEnv.getProperty("spring.ai.mcp.server.protocol", "sse");
		transport.put("protocol", protocol);
		if ("streamable".equalsIgnoreCase(protocol) || "stateless".equalsIgnoreCase(protocol)) {
			transport.put("endpoint", springEnv.getProperty("spring.ai.mcp.server.streamable-http.mcp-endpoint", "/mcp"));
		} else {
			transport.put("endpoint", springEnv.getProperty("spring.ai.mcp.server.sse-endpoint", "/sse"));
			transport.put("messageEndpoint", springEnv.getProperty("spring.ai.mcp.server.sse-message-endpoint", "/mcp/message"));
		}
		transport.put("baseUrl", springEnv.getProperty("spring.ai.mcp.server.base-url", "http://localhost:" + serverPort));
		return transport;
	}

	private List<ConfigEntry> buildConfigEntries() {
		ParaConfig config = Para.getConfig();
		String prefix = config.getConfigRootPrefix();
		return Arrays.stream(config.getClass().getMethods())
				.filter(method -> method.isAnnotationPresent(Documented.class))
				.map(method -> method.getAnnotation(Documented.class))
				.filter(doc -> StringUtils.isNotBlank(doc.identifier()))
				.sorted(Comparator.comparingInt(Documented::position))
				.map(doc -> new ConfigEntry(prefix, doc))
				.collect(Collectors.toList());
	}

	private static final class ConfigEntry {

		private final String key;
		private final String prefixedKey;
		private final String category;
		private final String description;
		private final String defaultValue;
		private final Class<?> type;
		private final List<String> tags;
		private final int position;
		private final String searchText;

		ConfigEntry(String rootPrefix, Documented doc) {
			this.key = doc.identifier();
			this.prefixedKey = StringUtils.isBlank(rootPrefix) ? key : rootPrefix + "." + key;
			this.category = doc.category();
			this.description = doc.description();
			this.defaultValue = doc.value();
			this.type = doc.type();
			this.tags = Collections.unmodifiableList(Arrays.stream(doc.tags())
					.map(String::trim)
					.filter(StringUtils::isNotBlank)
					.collect(Collectors.toList()));
			this.position = doc.position();
			this.searchText = (key + " " + description + " " + category + " "
					+ String.join(" ", tags)).toLowerCase(Locale.ROOT);
		}

		String key() {
			return key;
		}

		String defaultValue() {
			return defaultValue;
		}

		List<String> tags() {
			return tags;
		}

		boolean matches(String needle) {
			return StringUtils.isBlank(needle) || searchText.contains(needle);
		}

		Map<String, Object> toMap(Object sanitizedValue) {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("key", key);
			payload.put("prefixedKey", prefixedKey);
			payload.put("category", category);
			payload.put("description", description);
			payload.put("type", type.getSimpleName());
			payload.put("defaultValue", defaultValue);

			Set<String> orderedTags = new LinkedHashSet<>(tags);
			payload.put("tags", orderedTags);
			payload.put("requiresRestart", orderedTags.stream()
					.anyMatch(tag -> "requires restart".equalsIgnoreCase(tag)));
			payload.put("position", position);
			payload.put("resourceUri", "para:///config/" + key);
			if (sanitizedValue != null) {
				payload.put("value", sanitizedValue);
			}
			return payload;
		}
	}
}
