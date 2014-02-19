/*
 * Copyright 2013 Alex Bogdanovski <alex@erudika.com>.
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
 * You can reach the author at: https://github.com/albogdano
 */
package com.erudika.para.utils;

//import static com.erudika.para.utils.Utils.MD5;

import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.erudika.para.web.ParaContextListener;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import static com.erudika.para.utils.Utils.getInitParam;
//import static com.erudika.para.utils.Utils.initConfig;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public final class Config {

	private static final Logger logger = LoggerFactory.getLogger(Config.class);
	private static com.typesafe.config.Config config;
	private static Map<String, String> configMap;

	static {
		init(null);
	}

	// GLOBAL SETTINGS
	/** {@value #PARA} */
	public static final String PARA = "para";
	/** {@value #_AUTHTOKEN} */
	public static final String _AUTHTOKEN = "authtoken";
	/** {@value #_CLASSNAME} */
	public static final String _CLASSNAME = "classname";
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
	/** {@value #_SALT} */
	public static final String _SALT = "salt";
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

	/** {@value #MAX_ITEMS_PER_PAGE} */
	public static final int MAX_ITEMS_PER_PAGE = 30;
	/** {@value #DEFAULT_LIMIT} */
	public static final int	DEFAULT_LIMIT = Integer.MAX_VALUE;
	/** {@value #MAX_PAGES} */
	public static final int MAX_PAGES = 10000;
	/** {@value #MAX_IMG_SIZE_PX} */
	public static final int MAX_IMG_SIZE_PX = 800;
	/** {@value #MIN_PASS_LENGTH} */
	public static final int MIN_PASS_LENGTH = 6;
	/** {@value #SEPARATOR} */
	public static final String SEPARATOR = ":";
	/** {@value #DEFAULT_ENCODING} */
	public static final String DEFAULT_ENCODING = "UTF-8";
	/** {@value #FB_PREFIX} */
	public static final String FB_PREFIX = "fb" + SEPARATOR;
	/** {@value #FXRATES_KEY} */
	public static final String FXRATES_KEY = "fxrates";

	//////////  INITIALIZATION PARAMETERS  //////////////
	/**
	 * For example: production, development, testing... etc.
	 */
	public static final String ENVIRONMENT = getConfigParam("env", "");
	/**
	 * AWS Access Key
	 */
	public static final String AWS_ACCESSKEY = getConfigParam("aws_access_key", getAwsCredentials()[0]);
	/**
	 * AWS ecret Key
	 */
	public static final String AWS_SECRETKEY = getConfigParam("aws_secret_key", getAwsCredentials()[1]);
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
	 * Session timeout in seconds. Default: 24 hours
	 */
	public static final Long SESSION_TIMEOUT_SEC = NumberUtils.toLong(getConfigParam("session_timeout", ""), 24 * 60 * 60);
	/**
	 * Votes expire after X seconds. Default: 30 days
	 */
	public static final Long VOTE_EXPIRES_AFTER_SEC = NumberUtils.toLong(getConfigParam("vote_expires_after", ""), 30 * 24 * 60 * 60); //1 month in seconds
	/**
	 * A vote can be changed within X seconds of casting. Default: 30 seconds
	 */
	public static final Long VOTE_LOCKED_AFTER_SEC = NumberUtils.toLong(getConfigParam("vote_locked_after", ""), 30); // 30 sec
	/**
	 * Password reset window in seconds. Default: 30 minutes
	 */
	public static final Long PASSRESET_TIMEOUT_SEC = NumberUtils.toLong(getConfigParam("pass_reset_timeout", ""), 30 * 60); // 30 MIN
	/**
	 * Enable object caching. Default: true
	 */
	public static final boolean CACHE_ENABLED = Boolean.parseBoolean(getConfigParam("cache_enabled", "true"));
	/**
	 * Enable object indexing. Default: true
	 */
	public static final boolean SEARCH_ENABLED = Boolean.parseBoolean(getConfigParam("search_enabled", "true"));
	/**
	 * Read objects from index, not the data store. This WILL override the cache.
	 */
	public static final boolean READ_FROM_INDEX = Boolean.parseBoolean(getConfigParam("read_from_index", "true"));
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
			config = ConfigFactory.load().getConfig("para");

			if (conf != null) {
				config = conf.withFallback(config);
			}

			configMap = new HashMap<String, String>();
			for (Map.Entry<String, ConfigValue> con : config.entrySet()) {
				if (con.getValue().valueType() != ConfigValueType.LIST) {
					configMap.put(con.getKey(), config.getString(con.getKey()));
				}
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}
	}

	/**
	 * Returns the value of a configuration parameter or its default value.
	 * @param key the param key
	 * @param defaultValue the default param value
	 * @return the value of a param
	 */
	public static String getConfigParam(String key, String defaultValue) {
		if (config == null) {
			init(null);
		}
		return (!StringUtils.isBlank(key) && config.hasPath(key)) ? config.getString(key) : defaultValue;
	}

	/**
	 * Returns a map of configuration parameters and their values.
	 * @return the main configuration map
	 */
	public static Map<String, String> getConfigMap() {
		if (configMap == null) {
			init(null);
		}
		return configMap;
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

	private static String[] getAwsCredentials() {
		InstanceProfileCredentialsProvider ipcp = new InstanceProfileCredentialsProvider();
		try {
			ipcp.refresh();
			return new String[]{ipcp.getCredentials().getAWSAccessKeyId(), ipcp.getCredentials().getAWSSecretKey()};
		} catch (Exception e) {
			return new String[]{"", ""};
		}
	}

}
