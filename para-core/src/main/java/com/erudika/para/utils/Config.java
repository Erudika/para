/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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
	// GLOBAL SETTINGS
	/** {@value #PARA} */
	public static final String PARA = "para";
	/** {@value #_TYPE} */
	public static final String _TYPE = "type";
	/** {@value #_APPID} */
	public static final String _APPID = "appid";
	/** {@value #_CREATORID} */
	public static final String _CREATORID = "creatorid";
	/** {@value #_ID} */
	public static final String _ID = "id";
	/** {@value #_IDENTIFIER} */
	public static final String _IDENTIFIER = "identifier";
	/** {@value #_KEY} */
	public static final String _KEY = "key";
	/** {@value #_NAME} */
	public static final String _NAME = "name";
	/** {@value #_PARENTID} */
	public static final String _PARENTID = "parentid";
	/** {@value #_PASSWORD} */
	public static final String _PASSWORD = "password";
	/** {@value #_RESET_TOKEN} */
	public static final String _RESET_TOKEN = "token";
	/** {@value #_EMAIL_TOKEN} */
	public static final String _EMAIL_TOKEN = "etoken";
	/** {@value #_TIMESTAMP} */
	public static final String _TIMESTAMP = "timestamp";
	/** {@value #_UPDATED} */
	public static final String _UPDATED = "updated";
	/** {@value #_TAGS} */
	public static final String _TAGS = "tags";
	/** {@value #_EMAIL} */
	public static final String _EMAIL = "email";
	/** {@value #_GROUPS} */
	public static final String _GROUPS = "groups";

	private static final com.typesafe.config.Config emptyConfig =
			com.typesafe.config.ConfigFactory.empty().withValue(PARA,
					com.typesafe.config.ConfigValueFactory.fromMap(new HashMap<String, Object>(0)));

	static {
		init(emptyConfig);
	}

	/** {@value #DEFAULT_LIMIT} */
	public static final int	DEFAULT_LIMIT = Integer.MAX_VALUE;
	/** String separator - default is colon ':'. */
	public static final String SEPARATOR = getConfigParam("default_separator", ":");
	/** Facebook prefix - defaults to 'fb:'. */
	public static final String FB_PREFIX = "fb" + SEPARATOR;
	/** Google prefix - defaults to 'gp;'. */
	public static final String GPLUS_PREFIX = "gp" + SEPARATOR;
	/** LinkedIn prefix - defaults to 'in:'. */
	public static final String LINKEDIN_PREFIX = "in" + SEPARATOR;
	/** Twitter prefix - defaults to 'tw:'. */
	public static final String TWITTER_PREFIX = "tw" + SEPARATOR;
	/** GitHub prefix - defaults to 'gh;'. */
	public static final String GITHUB_PREFIX = "gh" + SEPARATOR;

	//////////  INITIALIZATION PARAMETERS  //////////////
	/**
	 * Maximum results per page - limits the number of items to show in search results. Default is 30.
	 */
	public static final int MAX_ITEMS_PER_PAGE = getConfigParamUnwrapped("max_items_per_page", 30);
	/**
	 * Pagination limit - default is 10 000.
	 */
	public static final int MAX_PAGES = getConfigParamUnwrapped("max_pages", 10000);
	/**
	 * Maximum image size (longest edge) - default is 1024 (pixels).
	 */
	public static final int MAX_IMG_SIZE_PX = getConfigParamUnwrapped("max_img_px", 1024);
	/**
	 * Minimum password length - default is 6 symbols.
	 */
	public static final int MIN_PASS_LENGTH = getConfigParamUnwrapped("min_password_length", 6);
	/**
	 * Maximum number of data types that can be defined per app - default is 256.
	 */
	public static final int MAX_DATATYPES_PER_APP = getConfigParamUnwrapped("max_datatypes_per_app", 256);
	/**
	 * Maximum size of incoming JSON objects - default is 1048576 (bytes).
	 */
	public static final int MAX_ENTITY_SIZE_BYTES = getConfigParamUnwrapped("max_entity_size_bytes", 1024 * 1024);
	/**
	 * Default character encoding - 'UTF-8'
	 */
	public static final String DEFAULT_ENCODING = getConfigParam("default_encoding", "UTF-8");
	/**
	 * For example: production, development, testing... etc. Default: "embedded"
	 */
	public static final String ENVIRONMENT = getConfigParam("env", "embedded");
	/**
	 * AWS Access Key
	 */
	public static final String AWS_ACCESSKEY = getAwsCredentials()[0];
	/**
	 * AWS ecret Key
	 */
	public static final String AWS_SECRETKEY = getAwsCredentials()[1];
	/**
	 * AWS Region
	 */
	public static final String AWS_REGION = getConfigParam("aws_region", "eu-west-1");
	/**
	 * Facebook app id (for authentication)
	 */
	public static final String FB_APP_ID = getConfigParam("fb_app_id", "");
	/**
	 * Facebook app secret (for authentication)
	 */
	public static final String FB_SECRET = getConfigParam("fb_secret", "");
	/**
	 * Google+ app id (for authentication)
	 */
	public static final String GPLUS_APP_ID = getConfigParam("gp_app_id", "");
	/**
	 * Google+ app secret (for authentication)
	 */
	public static final String GPLUS_SECRET = getConfigParam("gp_secret", "");
	/**
	 * LinkedIn app id (for authentication)
	 */
	public static final String LINKEDIN_APP_ID = getConfigParam("in_app_id", "");
	/**
	 * LinkedIn app secret (for authentication)
	 */
	public static final String LINKEDIN_SECRET = getConfigParam("in_secret", "");
	/**
	 * Twitter app id (for authentication)
	 */
	public static final String TWITTER_APP_ID = getConfigParam("tw_app_id", "");
	/**
	 * Twitter app secret (for authentication)
	 */
	public static final String TWITTER_SECRET = getConfigParam("tw_secret", "");
	/**
	 * GitHub app id (for authentication)
	 */
	public static final String GITHUB_APP_ID = getConfigParam("gh_app_id", "");
	/**
	 * GitHub app secret (for authentication)
	 */
	public static final String GITHUB_SECRET = getConfigParam("gh_secret", "");
	/**
	 * OpenExchangeRates.org API key
	 */
	public static final String OPENX_API_KEY = getConfigParam("openx_api_key", "");
	/**
	 * Google Maps API key
	 */
	public static final String GMAPS_API_KEY = getConfigParam("gmaps_api_key", "");
	/**
	 * The identifier of the first administrator (can be email, OpenID, or Facebook user id)
	 */
	public static final String ADMIN_IDENT = getConfigParam("admin_ident", "");
	/**
	 * The id of this deployment. In a multi-node environment each node should have a unique id.
	 */
	public static final String WORKER_ID = getConfigParam("worker_id", "1");
	/**
	 * The number of threads to use for the ExecutorService thread pool. Default is 2.
	 */
	public static final int EXECUTOR_THREADS = getConfigParamUnwrapped("executor_threads", 2);
	/**
	 * The name of the default application.
	 */
	public static final String APP_NAME = getConfigParam("app_name", PARA);
	/**
	 * The name of the default application without any spaces.
	 */
	public static final String APP_NAME_NS = Utils.noSpaces(APP_NAME, "-");
	/**
	 * The name of the authentication cookie.
	 */
	public static final String AUTH_COOKIE = getConfigParam("auth_cookie", APP_NAME_NS.concat("-auth"));
	/**
	 * The name of the "return to" cookie.
	 */
	public static final String RETURNTO_COOKIE = getConfigParam("returnto_cookie", APP_NAME_NS.concat("-returnto"));
	/**
	 * The email address for support.
	 */
	public static final String SUPPORT_EMAIL = getConfigParam("support_email", "support@myapp.co");
	/**
	 * The secret key for this deployment. Used as salt.
	 */
	public static final String APP_SECRET_KEY = getConfigParam("app_secret_key", Utils.MD5("paraseckey"));
	/**
	 * The package path (e.g. org.company.app.core) where all domain objects are defined.
	 */
	public static final String CORE_PACKAGE_NAME = getConfigParam("core_package_name", "");
	/**
	 * Request expiration in seconds. Default: 15 minutes
	 */
	public static final Long REQUEST_EXPIRES_AFTER_SEC = NumberUtils.toLong(getConfigParam("request_expires_after", ""), 15 * 60);
	/**
	 * Session timeout in seconds. Default: 24 hours
	 */
	public static final Long SESSION_TIMEOUT_SEC = NumberUtils.toLong(getConfigParam("session_timeout", ""), 24 * 60 * 60);
	/**
	 * Votes expire after X seconds. Default: 30 days
	 */
	public static final Long VOTE_EXPIRES_AFTER_SEC = NumberUtils.toLong(getConfigParam("vote_expires_after", ""), 30 * 24 * 60 * 60);
	/**
	 * A vote can be changed within X seconds of casting. Default: 30 seconds
	 */
	public static final Long VOTE_LOCKED_AFTER_SEC = NumberUtils.toLong(getConfigParam("vote_locked_after", ""), 30);
	/**
	 * Password reset window in seconds. Default: 30 minutes
	 */
	public static final Long PASSRESET_TIMEOUT_SEC = NumberUtils.toLong(getConfigParam("pass_reset_timeout", ""), 30 * 60);
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
	 * Read objects from index, not the data store. This WILL override the cache! Default: false
	 */
	public static final boolean READ_FROM_INDEX = Boolean.parseBoolean(getConfigParam("read_from_index", "false"));
	/**
	 * Production environment flag.
	 */
	public static final boolean IN_PRODUCTION = ENVIRONMENT.equals("production");
	/**
	 * Development environment flag.
	 */
	public static final boolean IN_DEVELOPMENT = ENVIRONMENT.equals("development");
	/**
	 * The name of the cluster (can be used to separate deployments)
	 */
	public static final String CLUSTER_NAME = getConfigParam("cluster_name", IN_PRODUCTION ? PARA + "-prod" : PARA + "-dev");

	/**
	 * Initializes the configuration class by loading the configuration file.
	 * @param conf overrides the default configuration
	 */
	public static void init(com.typesafe.config.Config conf) {
		try {
			config = ConfigFactory.load().getConfig(PARA).withFallback((conf == null) ? emptyConfig : conf);
			configMap = new HashMap<String, String>();
			for (Map.Entry<String, ConfigValue> con : config.entrySet()) {
				if (con.getValue().valueType() != ConfigValueType.LIST) {
					configMap.put(con.getKey(), config.getString(con.getKey()));
				}
			}
		} catch (Exception ex) {
			logger.warn("Para configuration file 'application.(conf|json|properties)' is missing from classpath.");
			config = emptyConfig;
		}
	}

	/**
	 * Returns the unwrapped value of a configuration parameter or its default value.
	 * @param key the param key
	 * @param defaultValue the default param value
	 * @param <V> the type of value to return
	 * @return the value of a param
	 */
	@SuppressWarnings("unchecked")
	public static <V> V getConfigParamUnwrapped(String key, V defaultValue) {
		if (config == null) {
			init(emptyConfig);
		}
		if (StringUtils.isBlank(key)) {
			return defaultValue;
		}
		ConfigValue raw = config.hasPath(key) ? config.getValue(key) : null;
		return (V) ((raw != null) ? raw.unwrapped() : defaultValue);
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
			init(emptyConfig);
		}
		if (StringUtils.isBlank(key)) {
			return null;
		}
		String sys = System.getProperty(key, System.getProperty(PARA + "." + key));
		if (!StringUtils.isBlank(sys)) {
			return sys;
		} else {
			return (!StringUtils.isBlank(key) && config.hasPath(key)) ? config.getString(key) : defaultValue;
		}
	}

	/**
	 * Returns a map of configuration parameters and their values.
	 * @return the main configuration map
	 */
	public static Map<String, String> getConfigMap() {
		if (configMap == null) {
			init(emptyConfig);
		}
		return configMap;
	}

	/**
	 * Returns the Config object.
	 * @return the config object
	 */
	public static com.typesafe.config.Config getConfig() {
		if (config == null) {
			init(emptyConfig);
		}
		return config;
	}

	/**
	 * Default: true only if {@link #ENVIRONMENT} = "production"
	 * @return true if caching is enabled
	 */
	public static boolean isCacheEnabled() {
		return Boolean.parseBoolean(getConfigParam("cache_enabled", "" + ENVIRONMENT.equals("production")));
	}

	/**
	 * Default: true
	 * @return true if indexing is enabled
	 */
	public static boolean isSearchEnabled() {
		return Boolean.parseBoolean(getConfigParam("search_enabled", "true"));
	}

	/**
	 * Try loading the AWS credentials from the config file.
	 * If they're missing try from the local metadata service.
	 * @return
	 */
	private static String[] getAwsCredentials() {
		String accessK = getConfigParam("aws_access_key", "");
		String secretK = getConfigParam("aws_secret_key", "");
		return new String[]{accessK, secretK};
	}

}
