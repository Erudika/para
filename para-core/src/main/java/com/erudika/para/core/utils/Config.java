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

import com.erudika.para.core.annotations.Documented;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class loads configuration settings from a file and sets defaults.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public abstract class Config {

	private static final Logger logger = LoggerFactory.getLogger(Config.class);
	private com.typesafe.config.Config config;
	private Map<String, String> sortedConfigKeys; // config key => category
	private Map<String, Documented> annotatedMethodsMap; // config key => Documented

	// GLOBAL SETTINGS
	/** {@value #PARA}. */
	public static final String PARA = "para";
	/** {@value #_TYPE}. */
	public static final String _TYPE = "type";
	/** {@value #_APPID}. */
	public static final String _APPID = "appid";
	/** {@value #_CREATORID}. */
	public static final String _CREATORID = "creatorid";
	/** {@value #_ID}. */
	public static final String _ID = "id";
	/** {@value #_IDENTIFIER}. */
	public static final String _IDENTIFIER = "identifier";
	/** {@value #_KEY}. */
	public static final String _KEY = "key";
	/** {@value #_NAME}. */
	public static final String _NAME = "name";
	/** {@value #_PARENTID}. */
	public static final String _PARENTID = "parentid";
	/** {@value #_PASSWORD}. */
	public static final String _PASSWORD = "password";
	/** {@value #_RESET_TOKEN}. */
	public static final String _RESET_TOKEN = "token";
	/** {@value #_EMAIL_TOKEN}. */
	public static final String _EMAIL_TOKEN = "etoken";
	/** {@value #_TIMESTAMP}. */
	public static final String _TIMESTAMP = "timestamp";
	/** {@value #_UPDATED}. */
	public static final String _UPDATED = "updated";
	/** {@value #_TAGS}. */
	public static final String _TAGS = "tags";
	/** {@value #_EMAIL}. */
	public static final String _EMAIL = "email";
	/** {@value #_GROUPS}. */
	public static final String _GROUPS = "groups";
	/** {@value #_VERSION}. */
	public static final String _VERSION = "version";
	/** {@value #_PROPERTIES}. */
	public static final String _PROPERTIES = "properties";
	/** {@value #DEFAULT_LIMIT}. */
	public static final int	DEFAULT_LIMIT = 10000;

	/**
	 * USER IDENTIFIER PREFIXES
	 */
	/** Facebook prefix - defaults to 'fb:'. */
	public static final String FB_PREFIX = "fb:";
	/** Google prefix - defaults to 'gp:'. */
	public static final String GPLUS_PREFIX = "gp:";
	/** LinkedIn prefix - defaults to 'in:'. */
	public static final String LINKEDIN_PREFIX = "in:";
	/** Twitter prefix - defaults to 'tw:'. */
	public static final String TWITTER_PREFIX = "tw:";
	/** GitHub prefix - defaults to 'gh:'. */
	public static final String GITHUB_PREFIX = "gh:";
	/** Microsoft prefix - defaults to 'ms:'. */
	public static final String MICROSOFT_PREFIX = "ms:";
	/** Slack prefix - defaults to 'sl:'. */
	public static final String SLACK_PREFIX = "sl:";
	/** Mattermost prefix - defaults to 'mm:'. */
	public static final String MATTERMOST_PREFIX = "mm:";
	/** Amazon prefix - defaults to 'az:'. */
	public static final String AMAZON_PREFIX = "az:";
	/** OAuth2 generic prefix - defaults to 'oa2:'. */
	public static final String OAUTH2_PREFIX = "oa2:";
	/** OAuth2 second generic prefix - defaults to 'oa2second:'. */
	public static final String OAUTH2_SECOND_PREFIX = "oa2second:";
	/** OAuth2 third generic prefix - defaults to 'oa2third:'. */
	public static final String OAUTH2_THIRD_PREFIX = "oa2third:";
	/** LDAP prefix - defaults to 'ldap:'. */
	public static final String LDAP_PREFIX = "ldap:";
	/** SAML prefix - defaults to 'saml:'. */
	public static final String SAML_PREFIX = "saml:";
	/** Passwordless auth prefix - defaults to 'custom:'. */
	public static final String PASSWORDLESS_PREFIX = "custom:";

	/**
	 * The name of the configuration file, usually 'app-application.conf'.
	 * @return prefix-application.conf or the value of @{code config.file} system property.
	 */
	public String getConfigFilePath() {
		return System.getProperty("config.file", getConfigRootPrefix() + "-application.conf");
	}

	/**
	 * The root prefix of the configuration property names, e.g. "para".
	 * @return the root prefix for all config property keys.
	 */
	public abstract String getConfigRootPrefix();

	/**
	 * The fallback configuration, which will be used if the default cannot be loaded.
	 * @return a config object, defaults to {@link com.typesafe.config.ConfigFactory#empty()}.
	 */
	protected com.typesafe.config.Config getFallbackConfig() {
		return com.typesafe.config.ConfigFactory.empty();
	}

	/**
	 * A set of keys to exclude from the rendered config string.
	 * @return a set of keys
	 */
	protected Set<String> getKeysExcludedFromRendering() {
		return Collections.emptySet();
	}

	/**
	 * Header string to append to the start of the rendered config.
	 * @return any string
	 */
	protected String getRenderedHeader() {
		return "";
	}

	/**
	 * Footer string to append to the end of the rendered config.
	 * @return any string
	 */
	protected String getRenderedFooter() {
		return "";
	}

	/**
	 * Initializes the configuration class by loading the configuration file.
	 * @param conf overrides the default configuration
	 * @return {@link com.typesafe.config.Config}
	 */
	public com.typesafe.config.Config load(com.typesafe.config.Config conf) {
		try {
			// try to parse the locally stored app-application.conf file if it exists.
			Path localConfig = Paths.get(getConfigFilePath()).toAbsolutePath();
			if (StringUtils.isBlank(System.getProperty("config.resource")) &&
					StringUtils.isBlank(System.getProperty("config.file")) &&
					StringUtils.isBlank(System.getProperty("config.url")) &&
					Files.exists(localConfig)) {
				try {
					conf = parseFileWithoutIncludes(localConfig.toFile()).
							getConfig(getConfigRootPrefix()).withFallback(getFallbackConfig());
				} catch (Exception e) {
					logger.debug("Failed to parse local config {}", e.getMessage());
				}
			}
			config = ConfigFactory.load(ConfigParseOptions.defaults().setIncluder(NoopConfigIncluder.INSTANCE)).
					getConfig(getConfigRootPrefix()).withFallback(getFallbackConfig());
			if (conf != null && !conf.isEmpty()) {
				config = conf.withFallback(config);
			}
		} catch (Exception ex) {
			logger.warn("Failed to load configuration file 'application.(conf|json|properties)' for namespace '{}' - {}",
					getConfigRootPrefix(), ex.getMessage());
			config = getFallbackConfig();
		}
		return config;
	}

	/**
	 * Returns the boolean value of a configuration parameter.
	 * @param key the param key
	 * @param defaultValue the default param value
	 * @return the value of a param
	 */
	protected boolean getConfigBoolean(String key, boolean defaultValue) {
		return Boolean.parseBoolean(getConfigParam(key, Boolean.toString(defaultValue)));
	}

	/**
	 * Returns the integer value of a configuration parameter.
	 * @param key the param key
	 * @param defaultValue the default param value
	 * @return the value of a param
	 */
	protected int getConfigInt(String key, int defaultValue) {
		return NumberUtils.toInt(getConfigParam(key, Integer.toString(defaultValue)));
	}

	/**
	 * Returns the double value of a configuration parameter.
	 * @param key the param key
	 * @param defaultValue the default param value
	 * @return the value of a param
	 */
	protected double getConfigDouble(String key, double defaultValue) {
		return NumberUtils.toDouble(getConfigParam(key, Double.toString(defaultValue)));
	}

	/**
	 * Returns the string value of a configuration parameter or its default value.
	 * {@link System#getProperty(java.lang.String)} has precedence.
	 * @param key the param key
	 * @param defaultValue the default param value
	 * @return the value of a param
	 */
	protected String getConfigParam(String key, String defaultValue) {
		if (StringUtils.isBlank(key)) {
			return defaultValue;
		}
		String envKey = getConfigRootPrefix() + "_" + key.replaceAll("\\.", "_");
		String env = System.getenv().getOrDefault(envKey.toUpperCase(), System.getenv(envKey)); // support uppercase ENV vars
		String sys = System.getProperty(getConfigRootPrefix() + "." + key);
		if (!StringUtils.isBlank(sys)) {
			return sys;
		} else if (!StringUtils.isBlank(env)) {
			return env;
		} else {
			return (!StringUtils.isBlank(key) && getConfig().hasPath(key)) ? getConfig().getAnyRef(key).toString() : defaultValue;
		}
	}

	/**
	 * Returns the unwrapped value of a configuration parameter or its default value.
	 * @param key the param key
	 * @param defaultValue the default value
	 * @return object a raw unwrapped value
	 */
	public Object getConfigValue(String key, String defaultValue) {
		String valString = getConfigParam(key, defaultValue);
		try {
			// keep existing values from config file, but if any properties were set through
			// System.setProperty() to a new value - skip this step and use the new values
			if (getConfig().hasPath(key) && getConfig().getValue(key).unwrapped() != null &&
					Strings.CS.equals(valString, getConfig().getAnyRef(key).toString())) {
				return getConfig().getValue(key).unwrapped();
			}
			Documented doc = getAnnotatedMethodsMap().get(key);
			if (doc != null && doc.type().equals(String.class)) {
				return valString; // special case where we have a string containing numbers
			}
			Map<String, Object> v = ParaObjectUtils.getJsonReader(Map.class).readValue("{\"v\":" + valString + "}");
			return v.getOrDefault("v", defaultValue);
		} catch (Exception ex) {
			return valString;
		}
	}

	/**
	 * Returns the Config object.
	 * @return the config object
	 */
	public com.typesafe.config.Config getConfig() {
		if (config == null) {
			load(null);
		}
		return config;
	}

	/**
	 * Overwrites the internal config object with a new one.
	 * @param newConfig a new Config object
	 * @return returns this instance for chaining
	 */
	public Config overwriteConfig(com.typesafe.config.Config newConfig) {
		config = newConfig;
		return this;
	}

	/**
	 * Constructs a sorted set of configuration keys.
	 * Heavily relies on the {@link Documented} annotation for sort order.
	 * @return a set of map of config keys, without the root prefix (path), to config categories.
	 */
	public Map<String, String> getSortedConfigKeys() {
		if (sortedConfigKeys == null || sortedConfigKeys.isEmpty()) {
			List<Documented> annotatedMethods = Arrays.stream(this.getClass().getMethods()).
					filter(m -> m.isAnnotationPresent(Documented.class) && !m.isAnnotationPresent(Deprecated.class)).
					map(m -> m.getAnnotation(Documented.class)).
					filter(m -> !StringUtils.isBlank(m.identifier())).
					sorted((a1, a2) -> Integer.compare(a1.position(), a2.position())).
					collect(Collectors.toList());
			sortedConfigKeys = annotatedMethods.stream().
					collect(Collectors.toMap(a -> a.identifier(), a -> a.category(), (u, v) -> u, LinkedHashMap::new));
			annotatedMethodsMap = annotatedMethods.stream().
					collect(Collectors.toMap(a -> a.identifier(), a -> a, (u, v) -> u, LinkedHashMap::new));
		}
		return sortedConfigKeys;
	}

	private Map<String, Documented> getAnnotatedMethodsMap() {
		if (annotatedMethodsMap == null || annotatedMethodsMap.isEmpty()) {
			getSortedConfigKeys();
		}
		return annotatedMethodsMap;
	}

	/**
	 * Stores all available configuration to a file, overwriting the existing one.
	 * In case of HOCON, the commented lines are collected from existing file and
	 * appended at the end.
	 */
	public void store() {
		if (!getConfigBoolean("store_config_locally", true)) {
			return;
		}
		String confFile = Paths.get(getConfigFilePath()).toAbsolutePath().toString();
		boolean isJsonFile = Strings.CI.endsWith(confFile, ".json");
		File conf = new File(confFile);
		if (!conf.exists() || conf.canWrite()) {
			try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(conf));
					ByteArrayInputStream data = new ByteArrayInputStream(
							render(isJsonFile, getRenderedHeader(), getRenderedFooter()).getBytes("utf-8"))) {
				int read;
				byte[] bytes = new byte[1024];
				while ((read = data.read(bytes)) != -1) {
					bos.write(bytes, 0, read);
				}
				logger.debug("Configuration stored successfully in {}", confFile);
			} catch (Exception e) {
				logger.error(null, e);
			}
		} else {
			logger.warn("Failed to write configuration file {} to disk - check permissions.", confFile);
		}
	}

	/**
	 * @see #render(boolean, java.lang.String, java.lang.String)
	 * @param asJson if true, a JSON object will be rendered, otherwise the HOCON format is used
	 * @return config as string
	 */
	public String render(boolean asJson) {
		return render(asJson, "", "");
	}

	/**
	 * Renders the current configuration as a String, taking into account system properties and ENV precedence ordering.
	 * @param asJson if true, a JSON object will be rendered, otherwise the HOCON format is used
	 * @param hoconHeader file header
	 * @param hoconFooter file footer
	 * @return config as string
	 */
	public String render(boolean asJson, String hoconHeader, String hoconFooter) {
		if (asJson) {
			String conf = "{}";
			try {
				Map<String, Object> renderMap = new LinkedHashMap<>(getConfigMap());
				renderMap.values().removeIf(Objects::isNull);
				conf = ParaObjectUtils.getJsonWriter().writeValueAsString(renderMap);
			} catch (JsonProcessingException ex) {
				logger.error(null, ex);
			}
			return conf;
		} else {
			String category = "";
			StringBuilder sb = new StringBuilder(hoconHeader);
			if (!StringUtils.isBlank(hoconHeader) && !Strings.CS.endsWith(hoconHeader, "\n")) {
				sb.append("\n");
			}
			for (Map.Entry<String, Object> entry : getConfigMap().entrySet()) {
				String keyPrefixed = entry.getKey();
				String keyNoPrefix = Strings.CS.removeStart(keyPrefixed, getConfigRootPrefix() + ".");
				Object val = entry.getValue();
				Object valRendered = ConfigValueFactory.fromAnyRef(val).render(ConfigRenderOptions.concise().
						setComments(false).setOriginComments(false));
				if (val != null) {
					String cat = getSortedConfigKeys().get(keyNoPrefix);
					if (!StringUtils.isBlank(cat) && !category.equalsIgnoreCase(cat)) {
						// print category header
						if (!category.isEmpty()) {
							sb.append("\n");
						}
						category = getSortedConfigKeys().get(keyNoPrefix);
						sb.append("#############  ").append(category.toUpperCase()).append("  #############\n\n");
					}
					sb.append(keyPrefixed).append(" = ").append(valRendered).append("\n");
				}
			}
			sb.append("\n").append(hoconFooter).append("\n");
			return sb.toString();
		}
	}

	/**
	 * Renders all configuration options along with their documentation and default values.
	 * @param format one of "hocon", "json" or "markdown"
	 * @param groupByCategory if true, will group properties by category
	 * @return a HOCON, JSON or MD string
	 */
	public String renderConfigDocumentation(String format, boolean groupByCategory) {
		String category = "";
		StringBuilder sb = new StringBuilder();
		Map<String, Map<String, Map<String, String>>> jsonMapByCat = new LinkedHashMap<>();
		Map<String, Map<String, String>> jsonMap = new LinkedHashMap<>();

		if (!groupByCategory && "markdown".equalsIgnoreCase(format)) {
			sb.append("| Property key & Description | Default Value | Type |\n");
			sb.append("|  ---                       | ---           | ---  |\n");
		}

		for (Map.Entry<String, Documented> entry : getAnnotatedMethodsMap().entrySet()) {
			if (!getKeysExcludedFromRendering().contains(entry.getKey())) {
				if (groupByCategory) {
					category = renderCategoryHeader(format, category, entry, jsonMapByCat, sb);
				}
				renderConfigDescription(format, category, entry, jsonMapByCat, jsonMap, groupByCategory, sb);
			}
		}

		if (StringUtils.isBlank(format) || "json".equalsIgnoreCase(format)) {
			try {
				return ParaObjectUtils.getJsonWriter().writeValueAsString(groupByCategory ? jsonMapByCat : jsonMap);
			} catch (JsonProcessingException ex) {
				logger.error(null, ex);
			}
		}
		return sb.toString();
	}

	/**
	 * @return returns the configuration as a map of keys and values.
	 */
	public Map<String, Object> getConfigMap() {
		Map<String, Object> configMap = new LinkedHashMap<>();
		for (String keyNoPrefix : getSortedConfigKeys().keySet()) {
			// allow overrides via system props to be rendered, e.g. for auto-init
			String sval = getConfigParam(keyNoPrefix, "");
			Object defaultValue = null;
			if (!StringUtils.isBlank(sval) && !getConfig().hasPath(keyNoPrefix)) {
				defaultValue = getConfigValue(keyNoPrefix, "");
			}
			configMap.put(getConfigRootPrefix() + "." + keyNoPrefix, defaultValue);
		}
		for (Map.Entry<String, ConfigValue> entry : getConfig().entrySet()) {
			String keyNoPrefix = Strings.CS.removeStart(entry.getKey(), getConfigRootPrefix() + ".");
			String keyPrefixed = getConfigRootPrefix() + "." + keyNoPrefix;
			Object value = getConfigValue(keyNoPrefix, "");
			Object valueUnwrapped = ConfigValueFactory.fromAnyRef(value).unwrapped();
			if (!getKeysExcludedFromRendering().contains(keyNoPrefix)) {
				configMap.put(keyPrefixed, valueUnwrapped);
			}
		}
		return configMap;
	}

	/**
	 * Parses a HOCON file with includes disabled.
	 * @param file a file
	 * @return a Config object
	 */
	public static com.typesafe.config.Config parseFileWithoutIncludes(File file) {
		return ConfigFactory.parseFile(file, ConfigParseOptions.defaults().setIncluder(NoopConfigIncluder.INSTANCE));
	}

	/**
	 * Parses a HOCON string with includes disabled.
	 * @param config HOCON confing string
	 * @return a Config object
	 */
	public static com.typesafe.config.Config parseStringWithoutIncludes(String config) {
		return ConfigFactory.parseString(config, ConfigParseOptions.defaults().setIncluder(NoopConfigIncluder.INSTANCE));
	}

	private String renderCategoryHeader(String format, String category, Map.Entry<String, Documented> entry,
			Map<String, Map<String, Map<String, String>>> jsonMapByCat, StringBuilder sb) {
		String cat = getSortedConfigKeys().get(entry.getKey());
		if (!StringUtils.isBlank(cat) && !category.equalsIgnoreCase(cat)) {
			category = getSortedConfigKeys().getOrDefault(entry.getKey(), "");
			switch (StringUtils.trimToEmpty(format)) {
				case "markdown":
					if (!category.isEmpty()) {
						sb.append("\n");
					}
					sb.append("## ").append(StringUtils.capitalize(category)).append("\n\n");
					sb.append("| Property key & Description | Default Value | Type |\n");
					sb.append("|  ---                       | ---           | ---  |\n");
					break;
				case "hocon":
					if (!category.isEmpty()) {
						sb.append("\n");
					}
					sb.append("#############  ").append(category.toUpperCase()).append("  #############\n\n");
					break;
				default:
					jsonMapByCat.put(category, new LinkedHashMap<>());
			}
		}
		return category;
	}

	private void renderConfigDescription(String format, String category, Map.Entry<String, Documented> entry,
			Map<String, Map<String, Map<String, String>>> jsonMapByCat, Map<String, Map<String, String>> jsonMap,
			boolean groupByCategory, StringBuilder sb) {
		switch (StringUtils.trimToEmpty(format)) {
			case "markdown":
				String tags = Arrays.stream(entry.getValue().tags()).
						map(t -> " <kbd>" + t + "</kbd>").collect(Collectors.joining());
				sb.append("|`").append(getConfigRootPrefix()).append(".").append(entry.getKey()).append("`").
						append(tags).append("<br>").append(entry.getValue().description()).append(" | `").
						append(Optional.ofNullable(StringUtils.trimToNull(entry.getValue().value())).orElse(" ")).
						append("` | `").append(entry.getValue().type().getSimpleName()).append("`|\n");
				break;
			case "hocon":
				tags = Arrays.stream(entry.getValue().tags()).
						map(t -> "[" + t + "] ").collect(Collectors.joining());
				sb.append("# ").append(entry.getValue().description());
				sb.append("[type: ").append(entry.getValue().type().getSimpleName()).append("] ").append(tags).append("\n");
				sb.append(getConfigRootPrefix()).append(".").append(entry.getKey()).append(" = ").
						append(entry.getValue().value()).append("\n");
				break;
			default:
				Map<String, String> description = new HashMap<String, String>();
				description.put("description", entry.getValue().description());
				description.put("defaultValue", entry.getValue().value());
				description.put("type", entry.getValue().type().getSimpleName());
				description.put("tags", StringUtils.join(entry.getValue().tags(), ","));
				if (groupByCategory) {
					jsonMapByCat.get(category).put(getConfigRootPrefix() + "." + entry.getKey(), description);
				} else {
					jsonMap.put(getConfigRootPrefix() + "." + entry.getKey(), description);
				}
		}
	}

	/**
	 * Disables the "include" keyword in HOCON files.
	 */
	static final class NoopConfigIncluder implements ConfigIncluder {
		static final ConfigIncluder INSTANCE = new NoopConfigIncluder();

		private NoopConfigIncluder() {
		}

		@Override
		public ConfigIncluder withFallback(ConfigIncluder fallback) {
			return this;
		}

		@Override
		public ConfigObject include(ConfigIncludeContext context, String what) {
			return ConfigValueFactory.fromMap(Map.of());
		}
	}
}
