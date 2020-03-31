/*
 * Copyright 2013-2020 Erudika. https://erudika.com
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
package com.erudika.para.utils;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class loads configuration settings from a file and sets defaults.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class Config {

	private static final Logger logger = LoggerFactory.getLogger(Config.class);
	private static com.typesafe.config.Config config;
	private static Map<String, String> configMap;

	private Config() { }

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

	static {
		init(null);
	}

	/**
	 * USER IDENTIFIER PREFIXES
	 */
	/** {@value #DEFAULT_LIMIT}. */
	public static final int	DEFAULT_LIMIT = 10000;
	/** String separator - default is colon ':'. */
	public static final String SEPARATOR = getConfigParam("default_separator", ":");
	/** Facebook prefix - defaults to 'fb:'. */
	public static final String FB_PREFIX = "fb" + SEPARATOR;
	/** Google prefix - defaults to 'gp:'. */
	public static final String GPLUS_PREFIX = "gp" + SEPARATOR;
	/** LinkedIn prefix - defaults to 'in:'. */
	public static final String LINKEDIN_PREFIX = "in" + SEPARATOR;
	/** Twitter prefix - defaults to 'tw:'. */
	public static final String TWITTER_PREFIX = "tw" + SEPARATOR;
	/** GitHub prefix - defaults to 'gh:'. */
	public static final String GITHUB_PREFIX = "gh" + SEPARATOR;
	/** Microsoft prefix - defaults to 'ms:'. */
	public static final String MICROSOFT_PREFIX = "ms" + SEPARATOR;
	/** Slack prefix - defaults to 'sl:'. */
	public static final String SLACK_PREFIX = "sl" + SEPARATOR;
	/** Mattermost prefix - defaults to 'mm:'. */
	public static final String MATTERMOST_PREFIX = "mm" + Config.SEPARATOR;
	/** Amazon prefix - defaults to 'az:'. */
	public static final String AMAZON_PREFIX = "az" + Config.SEPARATOR;
	/** OAuth2 generic prefix - defaults to 'oa2:'. */
	public static final String OAUTH2_PREFIX = "oa2" + SEPARATOR;
	/** OAuth2 second generic prefix - defaults to 'oa2second:'. */
	public static final String OAUTH2_SECOND_PREFIX = "oa2second" + SEPARATOR;
	/** OAuth2 third generic prefix - defaults to 'oa2third:'. */
	public static final String OAUTH2_THIRD_PREFIX = "oa2third" + SEPARATOR;
	/** LDAP prefix - defaults to 'ldap:'. */
	public static final String LDAP_PREFIX = "ldap" + SEPARATOR;
	/** SAML prefix - defaults to 'saml:'. */
	public static final String SAML_PREFIX = "saml" + SEPARATOR;

	//////////  INITIALIZATION PARAMETERS  //////////////
	/**
	 * Maximum results per page - limits the number of items to show in search results. Default is 30.
	 */
	public static final int MAX_ITEMS_PER_PAGE = getConfigInt("max_items_per_page", 30);
	/**
	 * Pagination limit - highest page number, default is 1000.
	 */
	public static final int MAX_PAGES = getConfigInt("max_pages", 1000);
	/**
	 * Pagination limit - maximum number of results per page, default is 256.
	 */
	public static final int MAX_PAGE_LIMIT = getConfigInt("max_page_limit", 256);
	/**
	 * Maximum image size (longest edge) - default is 1024 (pixels).
	 */
	public static final int MAX_IMG_SIZE_PX = getConfigInt("max_img_px", 1024);
	/**
	 * Minimum password length - default is 6 symbols.
	 */
	public static final int MIN_PASS_LENGTH = getConfigInt("min_password_length", 6);
	/**
	 * Maximum number of data types that can be defined per app - default is 256.
	 */
	public static final int MAX_DATATYPES_PER_APP = getConfigInt("max_datatypes_per_app", 256);
	/**
	 * Maximum size of incoming JSON objects - default is 1048576 (bytes).
	 */
	public static final int MAX_ENTITY_SIZE_BYTES = getConfigInt("max_entity_size_bytes", 1024 * 1024);
	/**
	 * Default character encoding - 'UTF-8'.
	 */
	public static final String DEFAULT_ENCODING = getConfigParam("default_encoding", "UTF-8");
	/**
	 * For example: production, development, testing... etc. Default: "embedded"
	 */
	public static final String ENVIRONMENT = getConfigParam("env", "embedded");
	/**
	 * Facebook app id (for authentication).
	 */
	public static final String FB_APP_ID = getConfigParam("fb_app_id", "");
	/**
	 * Facebook app secret (for authentication).
	 */
	public static final String FB_SECRET = getConfigParam("fb_secret", "");
	/**
	 * Google+ app id (for authentication).
	 */
	public static final String GPLUS_APP_ID = getConfigParam("gp_app_id", "");
	/**
	 * Google+ app secret (for authentication).
	 */
	public static final String GPLUS_SECRET = getConfigParam("gp_secret", "");
	/**
	 * LinkedIn app id (for authentication).
	 */
	public static final String LINKEDIN_APP_ID = getConfigParam("in_app_id", "");
	/**
	 * LinkedIn app secret (for authentication).
	 */
	public static final String LINKEDIN_SECRET = getConfigParam("in_secret", "");
	/**
	 * Twitter app id (for authentication).
	 */
	public static final String TWITTER_APP_ID = getConfigParam("tw_app_id", "");
	/**
	 * Twitter app secret (for authentication).
	 */
	public static final String TWITTER_SECRET = getConfigParam("tw_secret", "");
	/**
	 * GitHub app id (for authentication).
	 */
	public static final String GITHUB_APP_ID = getConfigParam("gh_app_id", "");
	/**
	 * GitHub app secret (for authentication).
	 */
	public static final String GITHUB_SECRET = getConfigParam("gh_secret", "");
	/**
	 * Microsoft app id (for authentication).
	 */
	public static final String MICROSOFT_APP_ID = getConfigParam("ms_app_id", "");
	/**
	 * Microsoft app secret (for authentication).
	 */
	public static final String MICROSOFT_SECRET = getConfigParam("ms_secret", "");
	/**
	 * Slack app id (for authentication).
	 */
	public static final String SLACK_APP_ID = getConfigParam("sl_app_id", "");
	/**
	 * Slack app secret (for authentication).
	 */
	public static final String SLACK_SECRET = getConfigParam("sl_secret", "");
	/**
	 * Mattermost app id (for authentication).
	 */
	public static final String MATTERMOST_APP_ID = getConfigParam("mm_app_id", "");
	/**
	 * Mattermost app secret (for authentication).
	 */
	public static final String MATTERMOST_SECRET = getConfigParam("mm_secret", "");
	/**
	 * Amazon app id (for authentication).
	 */
	public static final String AMAZON_APP_ID = getConfigParam("az_app_id", "");
	/**
	 * Amazon app secret (for authentication).
	 */
	public static final String AMAZON_SECRET = getConfigParam("az_secret", "");
	/**
	 * The identifier of the first administrator (can be email, OpenID, or Facebook user id).
	 */
	public static final String ADMIN_IDENT = getConfigParam("admin_ident", "");
	/**
	 * The id of this deployment. In a multi-node environment each node should have a unique id.
	 */
	public static final String WORKER_ID = getConfigParam("worker_id", "1");
	/**
	 * The number of threads to use for the ExecutorService thread pool. Default is 2.
	 */
	public static final int EXECUTOR_THREADS = getConfigInt("executor_threads", 2);
	/**
	 * The name of the default application.
	 */
	public static final String APP_NAME = getConfigParam("app_name", PARA);
	/**
	 * The name of the "return to" cookie.
	 */
	public static final String RETURNTO_COOKIE = getConfigParam("returnto_cookie", PARA.concat("-returnto"));
	/**
	 * The email address for support.
	 */
	public static final String SUPPORT_EMAIL = getConfigParam("support_email", "support@myapp.co");
	/**
	 * The secret key for this deployment. Used as salt.
	 */
	public static final String APP_SECRET_KEY = getConfigParam("app_secret_key", Utils.md5("paraseckey"));
	/**
	 * The default queue name which will be polled for incoming JSON messages.
	 */
	public static final String DEFAULT_QUEUE_NAME = getConfigParam("default_queue_name", PARA + "-default");
	/**
	 * The package path (e.g. org.company.app.core) where all domain objects are defined.
	 */
	public static final String CORE_PACKAGE_NAME = getConfigParam("core_package_name", "");
	/**
	 * Expiration of signed API request, in seconds. Default: 15 minutes
	 */
	public static final int REQUEST_EXPIRES_AFTER_SEC = NumberUtils.toInt(getConfigParam("request_expires_after", ""), 15 * 60);
	/**
	 * JWT (access token) expiration in seconds. Default: 1 week
	 */
	public static final int JWT_EXPIRES_AFTER_SEC = NumberUtils.toInt(getConfigParam("jwt_expires_after", ""), 7 * 24 * 60 * 60);
	/**
	 * JWT refresh interval - tokens will be auto-refreshed at this interval of time. Default: 1 hour
	 */
	public static final int JWT_REFRESH_INTERVAL_SEC = NumberUtils.toInt(getConfigParam("jwt_refresh_interval", ""), 60 * 60);
	/**
	 * Session timeout in seconds. Default: 24 hours
	 */
	public static final int SESSION_TIMEOUT_SEC = NumberUtils.toInt(getConfigParam("session_timeout", ""), 24 * 60 * 60);
	/**
	 * Votes expire after X seconds. Default: 30 days
	 */
	public static final int VOTE_EXPIRES_AFTER_SEC = NumberUtils.toInt(getConfigParam("vote_expires_after", ""), 30 * 24 * 60 * 60);
	/**
	 * A vote can be changed within X seconds of casting. Default: 30 seconds
	 */
	public static final int VOTE_LOCKED_AFTER_SEC = NumberUtils.toInt(getConfigParam("vote_locked_after", ""), 30);
	/**
	 * Password reset window in seconds. Default: 30 minutes
	 */
	public static final int PASSRESET_TIMEOUT_SEC = NumberUtils.toInt(getConfigParam("pass_reset_timeout", ""), 30 * 60);
	/**
	 * Enable the RESTful API. Default: true
	 */
	public static final boolean API_ENABLED = Boolean.parseBoolean(getConfigParam("api_enabled", "true"));
	/**
	 * Enable the CORS filter for API requests. Default: true
	 */
	public static final boolean CORS_ENABLED = Boolean.parseBoolean(getConfigParam("cors_enabled", "true"));
	/**
	 * Enable the GZIP filter for API requests. Default: false
	 */
	public static final boolean GZIP_ENABLED = Boolean.parseBoolean(getConfigParam("gzip_enabled", "false"));
	/**
	 * Enable webhooks for CRUD methods. Requires a queue. Default: false
	 */
	public static final boolean WEBHOOKS_ENABLED = Boolean.parseBoolean(getConfigParam("webhooks_enabled", "false"));
	/**
	 * Production environment flag.
	 */
	public static final boolean IN_PRODUCTION = ENVIRONMENT.equals("production");
	/**
	 * Development environment flag.
	 */
	public static final boolean IN_DEVELOPMENT = ENVIRONMENT.equals("development");
	/**
	 * The name of the cluster (can be used to separate deployments).
	 */
	public static final String CLUSTER_NAME = getConfigParam("cluster_name", IN_PRODUCTION ? PARA + "-prod" : PARA + "-dev");

	/**
	 * Initializes the configuration class by loading the configuration file.
	 * @param conf overrides the default configuration
	 */
	public static void init(com.typesafe.config.Config conf) {
		try {
			config = ConfigFactory.load().getConfig(PARA);

			if (conf != null) {
				config = conf.withFallback(config);
			}

			configMap = new HashMap<>();
			for (Map.Entry<String, ConfigValue> con : config.entrySet()) {
				if (con.getValue().valueType() != ConfigValueType.LIST) {
					configMap.put(con.getKey(), config.getString(con.getKey()));
				}
			}
		} catch (Exception ex) {
			logger.warn("Para configuration file 'application.(conf|json|properties)' is missing from classpath.");
			config = com.typesafe.config.ConfigFactory.empty();
		}
	}

	/**
	 * Returns the boolean value of a configuration parameter.
	 * @param key the param key
	 * @param defaultValue the default param value
	 * @return the value of a param
	 */
	public static boolean getConfigBoolean(String key, boolean defaultValue) {
		return Boolean.parseBoolean(getConfigParam(key, Boolean.toString(defaultValue)));
	}

	/**
	 * Returns the integer value of a configuration parameter.
	 * @param key the param key
	 * @param defaultValue the default param value
	 * @return the value of a param
	 */
	public static int getConfigInt(String key, int defaultValue) {
		return NumberUtils.toInt(getConfigParam(key, Integer.toString(defaultValue)));
	}

	/**
	 * Returns the double value of a configuration parameter.
	 * @param key the param key
	 * @param defaultValue the default param value
	 * @return the value of a param
	 */
	public static double getConfigDouble(String key, double defaultValue) {
		return NumberUtils.toDouble(getConfigParam(key, Double.toString(defaultValue)));
	}

	/**
	 * Returns the value of a configuration parameter or its default value.
	 * {@link System#getProperty(java.lang.String)} has precedence.
	 * @param key the param key
	 * @param defaultValue the default param value
	 * @return the value of a param
	 */
	public static String getConfigParam(String key, String defaultValue) {
		if (config == null) {
			init(null);
		}
		if (StringUtils.isBlank(key)) {
			return defaultValue;
		}
		String keyVar = key.replaceAll("\\.", "_");
		String env = System.getenv(keyVar) == null ? System.getenv(PARA + "_" + keyVar) : System.getenv(keyVar);
		String sys = System.getProperty(key, System.getProperty(PARA + "." + key));
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
	public static com.typesafe.config.Config getConfig() {
		if (config == null) {
			init(null);
		}
		return config;
	}

	/**
	 * Default: true only if {@link #ENVIRONMENT} = "production".
	 * @return true if caching is enabled
	 */
	public static boolean isCacheEnabled() {
		return getConfigBoolean("cache_enabled", ENVIRONMENT.equals("production"));
	}

	/**
	 * Default: true.
	 * @return true if indexing is enabled
	 */
	public static boolean isSearchEnabled() {
		return getConfigBoolean("search_enabled", true);
	}

	/**
	 * @return The name of the default application without any spaces.
	 */
	public static String getRootAppIdentifier() {
		return Utils.noSpaces(Config.getConfigParam("app_name", PARA), "-");
	}
}
