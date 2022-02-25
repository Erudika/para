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

import com.typesafe.config.ConfigFactory;
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
	 * @return a config object.
	 */
	public com.typesafe.config.Config getFallbackConfig() {
		return com.typesafe.config.ConfigFactory.empty();
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
			return (!StringUtils.isBlank(key) && config.hasPath(key)) ? config.getString(key) : defaultValue;
		}
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
}
