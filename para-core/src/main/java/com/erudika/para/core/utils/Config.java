/*
 * Copyright 2013-2022 Erudika. https://erudika.com
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
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.ConfigValueType;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
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
	private List<Documented> annotatedMethods;

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

	/**
	 * USER IDENTIFIER PREFIXES
	 */
	/** {@value #DEFAULT_LIMIT}. */
	public static final int	DEFAULT_LIMIT = 10000;
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
	 */
	protected final void init(com.typesafe.config.Config conf) {
		try {
			config = ConfigFactory.load().getConfig(getConfigRootPrefix()).withFallback(getFallbackConfig());
			if (conf != null) {
				config = conf.withFallback(config);
			}
			getSortedConfigKeys();
		} catch (Exception ex) {
			logger.warn("Failed to load configuration file 'application.(conf|json|properties)' for namespace '{}' - {}",
					getConfigRootPrefix(), ex.getMessage());
			config = getFallbackConfig();
		}
	}

	/**
	 * Returns the boolean value of a configuration parameter.
	 * @param key the param key
	 * @param defaultValue the default param value
	 * @return the value of a param
	 */
	public boolean getConfigBoolean(String key, boolean defaultValue) {
		return Boolean.parseBoolean(getConfigParam(key, Boolean.toString(defaultValue)));
	}

	/**
	 * Returns the integer value of a configuration parameter.
	 * @param key the param key
	 * @param defaultValue the default param value
	 * @return the value of a param
	 */
	public int getConfigInt(String key, int defaultValue) {
		return NumberUtils.toInt(getConfigParam(key, Integer.toString(defaultValue)));
	}

	/**
	 * Returns the double value of a configuration parameter.
	 * @param key the param key
	 * @param defaultValue the default param value
	 * @return the value of a param
	 */
	public double getConfigDouble(String key, double defaultValue) {
		return NumberUtils.toDouble(getConfigParam(key, Double.toString(defaultValue)));
	}

	/**
	 * Returns the value of a configuration parameter or its default value.
	 * {@link System#getProperty(java.lang.String)} has precedence.
	 * @param key the param key
	 * @param defaultValue the default param value
	 * @return the value of a param
	 */
	public String getConfigParam(String key, String defaultValue) {
		if (config == null) {
			init(null);
		}
		if (StringUtils.isBlank(key)) {
			return defaultValue;
		}
		String keyVar = key.replaceAll("\\.", "_");
		String env = System.getenv(keyVar) == null ? System.getenv(getConfigRootPrefix() + "_" + keyVar) : System.getenv(keyVar);
		String sys = System.getProperty(key, System.getProperty(getConfigRootPrefix() + "." + key));
		if (!StringUtils.isBlank(sys)) {
			return sys;
		} else if (!StringUtils.isBlank(env)) {
			return env;
		} else {
			return (!StringUtils.isBlank(key) && config.hasPath(key)) ? config.getAnyRef(key).toString() : defaultValue;
		}
	}

	/**
	 * @see #getConfigParam(java.lang.String, java.lang.String)
	 * @param key key
	 * @param defaultValue value
	 * @param type type
	 * @return object
	 */
	public Object getConfigParam(String key, String defaultValue, ConfigValueType type) {
		try {
			switch (type) {
				case STRING:
					return getConfigParam(key, defaultValue);
				case BOOLEAN:
					return Boolean.parseBoolean(getConfigParam(key, defaultValue));
				case NUMBER:
					return NumberFormat.getInstance().parse(getConfigParam(key, defaultValue));
				case LIST:
					String arr = getConfigParam(key, defaultValue);
					arr = StringUtils.remove(arr, "]");
					arr = StringUtils.remove(arr, "[");
					arr = StringUtils.remove(arr, "\"");
					arr = StringUtils.remove(arr, "\"");
					List<Object> list = new ArrayList<>();
					for (String str : arr.split("\\s,\\s")) {
						if (NumberUtils.isParsable(str)) {
							list.add(NumberFormat.getInstance().parse(str));
						} else {
							list.add(str);
						}
					}
					return list;
				default:
					return getConfigParam(key, defaultValue);
				//case OBJECT:
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}
		return getConfigParam(key, defaultValue);
	}

	/**
	 * Returns the Config object.
	 * @return the config object
	 */
	public com.typesafe.config.Config getConfig() {
		if (config == null) {
			init(null);
		}
		return config;
	}

	/**
	 * Constructs a sorted set of configuration keys.
	 * Heavily relies on the {@link Documented} annotation for sort order.
	 * @return a set of map of config keys, without the root prefix (path), to config categories.
	 */
	public Map<String, String> getSortedConfigKeys() {
		if (sortedConfigKeys == null || sortedConfigKeys.isEmpty()) {
			annotatedMethods = Arrays.stream(this.getClass().getMethods()).
					filter(m -> m.isAnnotationPresent(Documented.class)).
					map(m -> m.getAnnotation(Documented.class)).
					filter(m -> !StringUtils.isBlank(m.identifier())).
					sorted((a1, a2) -> Integer.compare(a1.position(), a2.position())).
					collect(Collectors.toList());
			sortedConfigKeys = annotatedMethods.stream().
					collect(Collectors.toMap(a -> a.identifier(), a -> a.category(), (u, v) -> u, LinkedHashMap::new));
		}
		return sortedConfigKeys;
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
		String confFile = Paths.get(System.getProperty("config.file", "application.conf")).toAbsolutePath().toString();
		boolean isJsonFile = confFile.equalsIgnoreCase(".json");
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
				logger.info("Configuration stored successfully in {}", confFile);
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
	 *
	 * Renders the current configuration as a String, taking into account system properties and ENV precedence ordering.
	 * @param asJson if true, a JSON object will be rendered, otherwise the HOCON format is used
	 * @param hoconHeader file header
	 * @param hoconFooter file footer
	 * @return config as string
	 */
	public String render(boolean asJson, String hoconHeader, String hoconFooter) {
		Map<String, Object> configMap = new LinkedHashMap<>();
		if (asJson) {
			for (String key : getSortedConfigKeys().keySet()) {
				configMap.put(getConfigRootPrefix() + "." + key, null);
			}
			for (Map.Entry<String, ConfigValue> entry : getConfig().entrySet()) {
				if (!getKeysExcludedFromRendering().contains(entry.getKey())) {
					configMap.put(getConfigRootPrefix() + "." + entry.getKey(), ConfigValueFactory.
							fromAnyRef(getConfigParam(entry.getKey(), "", entry.getValue().valueType())).unwrapped());
				}
			}
			String conf = "{}";
			try {
				conf = ParaObjectUtils.getJsonWriter().writeValueAsString(configMap);
			} catch (JsonProcessingException ex) {
				logger.error(null, ex);
			}
			return conf;
		} else {
			for (String key : getSortedConfigKeys().keySet()) {
				configMap.put(key, null);
			}
			for (Map.Entry<String, ConfigValue> entry : getConfig().entrySet()) {
				if (!getKeysExcludedFromRendering().contains(entry.getKey())) {
					configMap.put(entry.getKey(), ConfigValueFactory.
							fromAnyRef(getConfigParam(entry.getKey(), "", entry.getValue().valueType())).
							render(ConfigRenderOptions.concise().setComments(false).setOriginComments(false)));
				}
			}
			String category = "";
			StringBuilder sb = new StringBuilder(hoconHeader);
			if (!StringUtils.endsWith(hoconHeader, "\n")) {
				sb.append("\n");
			}
			for (Map.Entry<String, Object> entry : configMap.entrySet()) {
				if (entry.getValue() != null) {
					String cat = getSortedConfigKeys().get(entry.getKey());
					if (!StringUtils.isBlank(cat) && !category.equalsIgnoreCase(cat)) {
						// print category header
						if (!category.isEmpty()) {
							sb.append("\n");
						}
						category = getSortedConfigKeys().get(entry.getKey());
						sb.append("#############  ").append(category.toUpperCase()).append("  #############\n\n");
					}
					sb.append(getConfigRootPrefix()).append(".").append(entry.getKey()).
							append(" = ").append(entry.getValue()).append("\n");
				}
			}
			sb.append("\n").append(hoconFooter).append("\n");
			return sb.toString();
		}
	}

	public String renderConfigDocumentation(String format, boolean groupByCategory) {
		Map<String, Documented> configMap = annotatedMethods.stream().
					collect(Collectors.toMap(a -> a.identifier(), a -> a, (u, v) -> u, LinkedHashMap::new));

		String category = "";
		StringBuilder sb = new StringBuilder();
		Map<String, Map<String, Map<String, String>>> jsonMapByCat = new LinkedHashMap<>();
		Map<String, Map<String, String>> jsonMap = new LinkedHashMap<>();

		if (!groupByCategory && "markdown".equalsIgnoreCase(format)) {
			sb.append("| Property key | Description | Default value | Type |\n");
			sb.append("| ---          | ---         | ---           | ---  |\n");
		}

		for (Map.Entry<String, Documented> entry : configMap.entrySet()) {
			if (!getKeysExcludedFromRendering().contains(entry.getKey())) {
				if (groupByCategory) {
					renderCategoryHeader(format, category, entry, jsonMapByCat, sb);
				}
				renderConfigDescription(format, category, entry, jsonMapByCat, jsonMap, groupByCategory, sb);
			}
		}

		if (StringUtils.isBlank(format) || "json".equalsIgnoreCase(format)) {
			try {
				return ParaObjectUtils.getJsonWriter().writeValueAsString(jsonMapByCat);
			} catch (JsonProcessingException ex) {
				logger.error(null, ex);
			}
		}
		return sb.toString();
	}

	private void renderCategoryHeader(String format, String category, Map.Entry<String, Documented> entry,
			Map<String, Map<String, Map<String, String>>> jsonMapByCat, StringBuilder sb) {
		String cat = getSortedConfigKeys().get(entry.getKey());
		if (!StringUtils.isBlank(cat) && !category.equalsIgnoreCase(cat)) {
			category = getSortedConfigKeys().get(entry.getKey());
			switch (format) {
				case "markdown":
					if (!category.isEmpty()) {
						sb.append("\n");
					}
					sb.append("## ").append(StringUtils.capitalize(category)).append("\n\n");
					sb.append("| Property key | Description | Default value | Type |\n");
					sb.append("| ---          | ---         | ---           | ---  |\n");
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
	}

	private void renderConfigDescription(String format, String category, Map.Entry<String, Documented> entry,
			Map<String, Map<String, Map<String, String>>> jsonMapByCat, Map<String, Map<String, String>> jsonMap,
			boolean groupByCategory, StringBuilder sb) {
		switch (format) {
			case "markdown":
				sb.append("|`").append(getConfigRootPrefix()).append(".").append(entry.getKey()).append("` | ").
						append(entry.getValue().description()).append(" | `").
						append(entry.getValue().value()).append("` | `").
						append(entry.getValue().type().getSimpleName()).append("`|\n");
				break;
			case "hocon":
				sb.append("# ").append(entry.getValue().description());
				sb.append("[type: ").append(entry.getValue().type().getSimpleName()).append("]\n");
				sb.append(getConfigRootPrefix()).append(".").append(entry.getKey()).append(" = ").
						append(entry.getValue().value()).append("\n");
				break;
			default:
				Map<String, String> description = new HashMap<String, String>();
				description.put("description", entry.getValue().description());
				description.put("defaultValue", entry.getValue().value());
				description.put("type", entry.getValue().type().getSimpleName());
				if (groupByCategory) {
					jsonMapByCat.get(category).put(getConfigRootPrefix() + "." + entry.getKey(), description);
				} else {
					jsonMap.put(getConfigRootPrefix() + "." + entry.getKey(), description);
				}
		}
	}

}
