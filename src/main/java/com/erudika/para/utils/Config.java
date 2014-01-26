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
	public static final String PARA = "para";
	
	public static final int MAX_ITEMS_PER_PAGE = 30;
	public static final int	DEFAULT_LIMIT = Integer.MAX_VALUE;
	public static final int MAX_PAGES = 10000;
	public static final int MAX_IMG_SIZE_PX = 800;
	public static final int MIN_PASS_LENGTH = 6;
	public static final String SEPARATOR = ":";
	public static final String DEFAULT_ENCODING = "UTF-8";
	public static final String FB_PREFIX = "fb" + SEPARATOR;
	public static final String FXRATES_KEY = "fxrates";
	
	//////////  INITIALIZATION PARAMETERS  //////////////
	public static final String AWS_ACCESSKEY = getConfigParam("aws_access_key", getAwsCredentials()[0]);
	public static final String AWS_SECRETKEY = getConfigParam("aws_secret_key", getAwsCredentials()[1]);
	public static final String AWS_REGION = getConfigParam("aws_region", "eu-west-1");
	public static final String FB_APP_ID = getConfigParam("fb_app_id", "");
	public static final String FB_SECRET = getConfigParam("fb_secret", "");
	public static final String OPENX_API_KEY = getConfigParam("openx_api_key", "");
	public static final String GMAPS_API_KEY = getConfigParam("gmaps_api_key", "");
	public static final String ADMIN_IDENT = getConfigParam("admin_ident", "");
	public static final String WORKER_ID = getConfigParam("worker_id", "1");
	public static final String APP_NAME = getConfigParam("app_name", "MyApp");
	public static final String APP_NAME_NS = APP_NAME.replaceAll("\\s", "-").toLowerCase();
	public static final String AUTH_COOKIE = getConfigParam("auth_cookie", APP_NAME_NS.concat("-auth"));
	public static final String RETURNTO_COOKIE = getConfigParam("returnto_cookie", APP_NAME_NS.concat("-returnto"));
	public static final String SUPPORT_EMAIL = getConfigParam("support_email", "support@myapp.co");
	public static final String APP_SECRET_KEY = getConfigParam("app_secret_key", Utils.MD5("secret"));
	public static final String CORE_PACKAGE_NAME = getConfigParam("core_package_name", "");
	public static final Long SESSION_TIMEOUT_SEC = NumberUtils.toLong(getConfigParam("session_timeout", ""), 24 * 60 * 60);
	public static final Long VOTE_EXPIRES_AFTER_SEC = NumberUtils.toLong(getConfigParam("vote_expires_after", ""), 30 * 24 * 60 * 60); //1 month in seconds
	public static final Long VOTE_LOCKED_AFTER_SEC = NumberUtils.toLong(getConfigParam("vote_locked_after", ""), 30); // 30 sec
	public static final Long PASSRESET_TIMEOUT_SEC = NumberUtils.toLong(getConfigParam("pass_reset_timeout", ""), 30 * 60); // 30 MIN
	public static final boolean CACHE_ENABLED = Boolean.parseBoolean(getConfigParam("cache_enabled", "true"));
	public static final boolean SEARCH_ENABLED = Boolean.parseBoolean(getConfigParam("search_enabled", "true"));
	public static final boolean READ_FROM_INDEX = Boolean.parseBoolean(getConfigParam("read_from_index", "true")); 
	public static final boolean IN_PRODUCTION = Boolean.parseBoolean(getConfigParam("production", "false")); 
	public static final String CLUSTER_NAME = getConfigParam("cluster_name", IN_PRODUCTION ? PARA + "-prod" : PARA + "-dev");
//	public static final String ES_HOSTS = getInitParam("eshosts", "localhost");
//	public static final int READ_CAPACITY = NumberUtils.toInt(getInitParam("read_capacity", "10"));
	
	public static void init(com.typesafe.config.Config conf){
		try {
			config = ConfigFactory.load().getConfig("para");
			
			if(conf != null){
				config = conf.withFallback(config);
			}
			
			configMap = new HashMap<String, String>();
			for (Map.Entry<String, ConfigValue> con : config.entrySet()) {
				if(con.getValue().valueType() != ConfigValueType.LIST){
					configMap.put(con.getKey(), config.getString(con.getKey()));
				}
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}
	}
	
	public static String getConfigParam(String key, String defaultValue){
		if(config == null) init(null);
		return (!StringUtils.isBlank(key) && config.hasPath(key)) ? config.getString(key) : defaultValue;
	}
	
	public static Map<String, String> getConfigMap(){
		if(configMap == null) init(null);
		return configMap;
	}
	
	public static com.typesafe.config.Config getConfig(){
		if(config == null) init(null);
		return config;
	}

	private static String[] getAwsCredentials(){
		InstanceProfileCredentialsProvider ipcp = new InstanceProfileCredentialsProvider();
		try {
			ipcp.refresh();
			return new String[]{ipcp.getCredentials().getAWSAccessKeyId(), ipcp.getCredentials().getAWSSecretKey()};
		} catch (Exception e) {
			return new String[]{"", ""};
		}
	}
	
}
