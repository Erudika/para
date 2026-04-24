/*
 * Copyright 2013-2026 Erudika. http://erudika.com
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

import com.erudika.para.core.App;
import com.erudika.para.core.annotations.Documented;
import static com.erudika.para.core.utils.Config.PARA;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.stream.Streams;

/**
 * Para configuration.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ParaConfig extends Config {

	/**
	 * No-args constructor.
	 */
	public ParaConfig() {
	}

	/**
	 * The configuration root prefix.
	 * @return PARA
	 */
	@Override
	public String getConfigRootPrefix() {
		return PARA;
	}

	/* **************************************************************************************************************
	 * Core                                                                                                    Core *
	 ****************************************************************************************************************/

	/**
	 * Returns the name of the default application.
	 * @return The name of the default application.
	 */
	@Documented(position = 10,
			identifier = "app_name",
			value = PARA,
			category = "Core",
			description = "The formal name of the web application.")
	public String appName() {
		return getConfigParam("app_name", PARA);
	}

	/**
	 * Overrides the secret key for the root Para app `app:para` once, upon first initialization.
	 * @return the secret key override
	 */
	@Documented(position = 11,
			identifier = "root_secret_override",
			category = "Core",
			description = "Overrides the secret key for the root Para app `app:para` once, upon first initialization.")
	public String rootSecretOverride() {
		return getConfigParam("root_secret_override", "");
	}

	/**
	 * The context path (subpath) of the web application, defaults to the root path `/`.
	 * @return the context path
	 */
	@Documented(position = 20,
			identifier = "context_path",
			category = "Core",
			tags = {"requires restart"},
			description = "The context path (subpath) of the web application, defaults to the root path `/`.")
	public String serverContextPath() {
		String context = getConfigParam("context_path", "");
		return StringUtils.stripEnd((StringUtils.isBlank(context)
				? System.getProperty("server.servlet.context-path", "") : context), "/");
	}

	/**
	 * The network port of this Para server. Port number should be a number above `1024`.
	 * @return the server port
	 */
	@Documented(position = 30,
			identifier = "port",
			value = "8080",
			type = Integer.class,
			category = "Core",
			tags = {"requires restart"},
			description = "The network port of this Para server. Port number should be a number above `1024`.")
	public int serverPort() {
		return NumberUtils.toInt(System.getProperty("server.port"), getConfigInt("port", 8080));
	}

	/**
	 * Enables/disables certain features, depending on the environment.
	 * Can be one of `production`, `development” or `embedded`.
	 * @return the environment name
	 */
	@Documented(position = 40,
			identifier = "",
			value = "embedded",
			category = "Core",
			tags = {"requires restart"},
			description = "Enables/disables certain features, depending on the environment. "
					+ "Can be one of `production`, `development” or `embedded`.")
	public String environment() {
		return getConfigParam("env", "embedded");
	}

	/**
	 * Selects the `DAO` implementation at runtime. Can be `AWSDynamoDAO`, `MongoDBDAO`,
	 * `CassandraDAO`, etc. Each implementation has its own configuration properties.
	 * @return the DAO plugin name
	 */
	@Documented(position = 50,
			identifier = "dao",
			value = "H2DAO",
			category = "Core",
			tags = {"requires restart"},
			description = "Selects the `DAO` implementation at runtime. Can be `AWSDynamoDAO`, `MongoDBDAO`, "
					+ "`CassandraDAO`, etc. Each implementation has its own configuration properties.")
	public String daoPlugin() {
		return getConfigParam("dao", "H2DAO");
	}

	/**
	 * Selects the `Search` implementation at runtime. Can be `LuceneSearch`, `ElasticSearch`, etc.
	 * @return the search plugin name
	 */
	@Documented(position = 60,
			identifier = "search",
			value = "LuceneSearch",
			category = "Core",
			tags = {"requires restart"},
			description = "Selects the `Search` implementation at runtime. Can be `LuceneSearch`, `ElasticSearch`, etc.")
	public String searchPlugin() {
		return getConfigParam("search", "LuceneSearch");
	}

	/**
	 * Selects the `Cache` implementation at runtime. Can be one of `CaffeineSearch`, `HazelcastCache`.
	 * @return the cache plugin name
	 */
	@Documented(position = 70,
			identifier = "cache",
			value = "CaffeineSearch",
			category = "Core",
			tags = {"requires restart"},
			description = "Selects the `Cache` implementation at runtime. Can be one of `CaffeineSearch`, `HazelcastCache`.")
	public String cachePlugin() {
		return getConfigParam("cache", "CaffeineSearch");
	}

	/**
	 * Selects the `Queue` implementation at runtime. Can be one of `LocalQueue`, `AWSQueue`.
	 * @return the queue plugin name
	 */
	@Documented(position = 80,
			identifier = "q",
			value = "LocalQueue",
			category = "Core",
			tags = {"requires restart"},
			description = "Selects the `Queue` implementation at runtime. Can be one of `LocalQueue`, `AWSQueue`.")
	public String queuePlugin() {
		return getConfigParam("q", "LocalQueue");
	}

	/**
	 * Selects the `FileStore` implementation at runtime. Can be one of `LocalFileStore`, `AWSFileStore`.
	 * @return the file storage plugin name
	 */
	@Documented(position = 90,
			identifier = "fs",
			value = "LocalFileStore",
			category = "Core",
			tags = {"requires restart"},
			description = "Selects the `FileStore` implementation at runtime. Can be one of `LocalFileStore`, `AWSFileStore`.")
	public String fileStoragePlugin() {
		return getConfigParam("fs", "LocalFileStore");
	}

	/**
	 * Selects the `Emailer` implementation at runtime.
	 * Can be one of `AWSEmailer`, `JavaMailEmailer`, `NoopEmailer`.
	 * @return the emailer plugin name
	 */
	@Documented(position = 100,
			identifier = "emailer",
			value = "NoopEmailer",
			category = "Core",
			description = "Selects the `Emailer` implementation at runtime. "
					+ "Can be one of `AWSEmailer`, `JavaMailEmailer`, `NoopEmailer`. ")
	public String emailerPlugin() {
		return getConfigParam("emailer", "NoopEmailer");
	}

	/**
	 * Enable/disable full-text search functionality.
	 * @return true if search is enabled
	 */
	@Documented(position = 110,
			identifier = "search_enabled",
			value = "true",
			type = Boolean.class,
			category = "Core",
			description = "Enable/disable full-text search functionality.")
	public boolean isSearchEnabled() {
		return getConfigBoolean("search_enabled", true);
	}

	/**
	 * Enable/disable object caching. Enabled in `production` mode by default.
	 * @return true if cache is enabled
	 */
	@Documented(position = 120,
			identifier = "cache_enabled",
			value = "false",
			type = Boolean.class,
			category = "Core",
			description = "Enable/disable object caching. Enabled in `production` mode by default.")
	public boolean isCacheEnabled() {
		return getConfigBoolean("cache_enabled", environment().equals("production"));
	}

	/**
	 * Enable/disable webhooks functionality using `Webhook` objects. Requires a queue.
	 * @return true if webhooks are enabled
	 */
	@Documented(position = 130,
			identifier = "webhooks_enabled",
			value = "false",
			type = Boolean.class,
			category = "Core",
			description = "Enable/disable webhooks functionality using `Webhook` objects. Requires a queue.")
	public boolean webhooksEnabled() {
		return Boolean.parseBoolean(getConfigParam("webhooks_enabled", "false"));
	}

	/**
	 * Enable/disable the Para RESTful API.
	 * @return true if API is enabled
	 */
	@Documented(position = 140,
			identifier = "api_enabled",
			value = "false",
			type = Boolean.class,
			category = "Core",
			description = "Enable/disable the Para RESTful API.")
	public boolean apiEnabled() {
		return Boolean.parseBoolean(getConfigParam("api_enabled", "true"));
	}

	/**
	 * The name of the Para cluster. Used by some of the plugins to isolate deployments.
	 * @return the cluster name
	 */
	@Documented(position = 150,
			identifier = "cluster_name",
			value = "para-prod",
			category = "Core",
			description = "The name of the Para cluster. Used by some of the plugins to isolate deployments.")
	public String clusterName() {
		return getConfigParam("cluster_name", inProduction() ? PARA + "-prod" : PARA + "-dev");
	}

	/**
	 * The package path (e.g. `org.company.app.core`) where all domain classes are defined.
	 * Specify this when integrating your app with Para core/client, to get deserialization working.
	 * @return the core package name
	 * @deprecated
	 */
	@Documented(position = 160,
			identifier = "core_package_name",
			category = "Core",
			tags = {"deprecated"},
			description = "The package path (e.g. `org.company.app.core`) where all domain classes are defined. "
					+ "Specify this when integrating your app with Para core/client, to get deserialization working.")
	@Deprecated
	public String corePackageName() {
		return getConfigParam("core_package_name", "");
	}

	/**
	 * The identifier of the first administrator (can be email or social login ID).
	 * @return the admin identifier
	 */
	@Documented(position = 170,
			identifier = "admin_ident",
			category = "Core",
			description = "The identifier of the first administrator (can be email or social login ID).")
	public String adminIdentifier() {
		return getConfigParam("admin_ident", "");
	}

	/**
	 * Node number, 1 to 128. Used mainly for ID generation.
	 * Each instance of Para should have a unique worker id.
	 * @return the worker id
	 */
	@Documented(position = 180,
			identifier = "worker_id",
			value = "1",
			category = "Core",
			description = "Node number, 1 to 128. Used mainly for ID generation."
					+ "Each instance of Para should have a unique worker id.")
	public String workerId() {
		return getConfigParam("worker_id", "1");
	}

	/**
	 * The number of threads to use for the `ExecutorService` thread pool.
	 * @return the number of executor threads
	 */
	@Documented(position = 190,
			identifier = "executor_threads",
			value = "2",
			type = Integer.class,
			category = "Core",
			description = "The number of threads to use for the `ExecutorService` thread pool.")
	public int executorThreads() {
		return getConfigInt("executor_threads", 2);
	}

	/**
	 * The number of threads to use for the `ExecutorService` thread pool.
	 * @return the number of executor threads
	 */
	@Documented(position = 191,
			identifier = "executor_service_enabled",
			value = "true",
			type = Boolean.class,
			category = "Core",
			description = "Enable/disable the asyncronous exectution of task through the executor service.")
	public boolean executorServiceEnabled() {
		return getConfigBoolean("executor_service_enabled", true);
	}

	/**
	 * The number of maximum failed webhook delivery attemts. Webhooks with too many failed
	 * deliveries will be disabled automatically.
	 * @return the maximum failed webhook attempts
	 */
	@Documented(position = 200,
			identifier = "max_failed_webhook_attempts",
			value = "10",
			type = Integer.class,
			category = "Core",
			description = "The number of maximum failed webhook delivery attemts. Webhooks with too many failed "
					+ "deliveries will be disabled automatically.")
	public int maxFailedWebhookAttempts() {
		return getConfigInt("max_failed_webhook_attempts", 10);
	}

	/**
	 * Controls the number of documents to reindex in a single batch.
	 * By default is equal to page size for reading the docs from DB.
	 * @param max the default batch size
	 * @return the reindex batch size
	 */
	@Documented(position = 210,
			identifier = "reindex_batch_size",
			value = "100",
			type = Integer.class,
			category = "Core",
			description = "Controls the number of documents to reindex in a single batch. By default is equal to page size for reading the docs from DB.")
	public int reindexBatchSize(int max) {
		return getConfigInt("reindex_batch_size", max);
	}

	/**
	 * Enable/disable the data synchronization between database and search index.
	 * @return true if sync is enabled
	 */
	@Documented(position = 220,
			identifier = "sync_index_with_db",
			value = "true",
			type = Boolean.class,
			category = "Core",
			description = "Enable/disable the data synchronization between database and search index.")
	public boolean syncIndexWithDatabaseEnabled() {
		return getConfigBoolean("sync_index_with_db", true);
	}

	/**
	 * Enable/disable reading data from search index instead of database. Used for data recovery.
	 * @return true if reading from index is enabled
	 */
	@Documented(position = 230,
			identifier = "read_from_index",
			value = "false",
			type = Boolean.class,
			category = "Core",
			description = "Enable/disable reading data from search index instead of database. Used for data recovery.")
	public boolean readFromIndexEnabled() {
		return getConfigBoolean("read_from_index", false);
	}

	/**
	 * Maximum number of data types which can be defined in each Para app.
	 * @return the maximum data types per app
	 */
	@Documented(position = 240,
			identifier = "max_datatypes_per_app",
			value = "256",
			type = Integer.class,
			category = "Core",
			description = "Maximum number of data types which can be defined in each Para app.")
	public int maxDatatypesPerApp() {
		return getConfigInt("max_datatypes_per_app", 256);
	}

	/**
	 * Maximum size (in bytes) of incoming JSON payload entities in requests to the API.
	 * @return the maximum entity size in bytes
	 */
	@Documented(position = 250,
			identifier = "max_entity_size_bytes",
			value = "1048576",
			type = Integer.class,
			category = "Core",
			description = "Maximum size (in bytes) of incoming JSON payload entities in requests to the API.")
	public int maxEntitySizeBytes() {
		return getConfigInt("max_entity_size_bytes", 1024 * 1024);
	}

	/**
	 * The health check interval, in seconds.
	 * @return the health check interval
	 */
	@Documented(position = 260,
			identifier = "health.check_interval",
			value = "60",
			type = Integer.class,
			category = "Core",
			description = "The health check interval, in seconds.")
	public int healthCheckInvervalSec() {
		return getConfigInt("health.check_interval", 60);
	}

	/**
	 * Enable/disable the health check functionality in Para.
	 * @return true if health check is enabled
	 */
	@Documented(position = 270,
			identifier = "health_check_enabled",
			value = "true",
			type = Boolean.class,
			category = "Core",
			description = "Enable/disable the health check functionality in Para.")
	public boolean healthCheckEnabled() {
		return getConfigBoolean("health_check_enabled", true);
	}

	/* **************************************************************************************************************
	 * Basic Authentication                                                                    Basic Authentication *
	 ****************************************************************************************************************/

	/**
	 * Facebook OAuth2 app ID.
	 * @return the FB app id
	 */


	@Documented(position = 280,
			identifier = "fb_app_id",
			category = "Basic Authentication",
			description = "Facebook OAuth2 app ID.")
	public String facebookAppId() {
		return getConfigParam("fb_app_id", "");
	}

	/**
	 * Facebook app secret key.
	 * @return the FB secret
	 */
	@Documented(position = 290,
			identifier = "fb_secret",
			category = "Basic Authentication",
			description = "Facebook app secret key.")
	public String facebookSecret() {
		return getConfigParam("fb_secret", "");
	}

	/**
	 * Google OAuth2 app ID.
	 * @return the Google app id
	 */
	@Documented(position = 300,
			identifier = "gp_app_id",
			category = "Basic Authentication",
			description = "Google OAuth2 app ID.")
	public String googleAppId() {
		return getConfigParam("gp_app_id", "");
	}

	/**
	 * Google app secret key.
	 * @return the Google secret
	 */
	@Documented(position = 310,
			identifier = "gp_secret",
			category = "Basic Authentication",
			description = "Google app secret key.")
	public String googleSecret() {
		return getConfigParam("gp_secret", "");
	}

	/**
	 * LinkedIn OAuth2 app ID.
	 * @return the LinkedIn app id
	 */
	@Documented(position = 320,
			identifier = "in_app_id",
			category = "Basic Authentication",
			description = "LinkedIn OAuth2 app ID.")
	public String linkedinAppId() {
		return getConfigParam("in_app_id", "");
	}

	/**
	 * LinkedIn app secret key.
	 * @return the LinkedIn secret
	 */
	@Documented(position = 330,
			identifier = "in_secret",
			category = "Basic Authentication",
			description = "LinkedIn app secret key.")
	public String linkedinSecret() {
		return getConfigParam("in_secret", "");
	}

	/**
	 * Twitter OAuth app ID.
	 * @return the Twitter app id
	 */
	@Documented(position = 340,
			identifier = "tw_app_id",
			category = "Basic Authentication",
			description = "Twitter OAuth app ID.")
	public String twitterAppId() {
		return getConfigParam("tw_app_id", "");
	}

	/**
	 * Twitter app secret key.
	 * @return the Twitter secret
	 */
	@Documented(position = 350,
			identifier = "tw_secret",
			category = "Basic Authentication",
			description = "Twitter app secret key.")
	public String twitterSecret() {
		return getConfigParam("tw_secret", "");
	}

	/**
	 * GitHub OAuth2 app ID.
	 * @return the GitHub app id
	 */
	@Documented(position = 360,
			identifier = "gh_app_id",
			category = "Basic Authentication",
			description = "GitHub OAuth2 app ID.")
	public String githubAppId() {
		return getConfigParam("gh_app_id", "");
	}

	/**
	 * GitHub app secret key.
	 * @return the GitHub secret
	 */
	@Documented(position = 370,
			identifier = "gh_secret",
			category = "Basic Authentication",
			description = "GitHub app secret key.")
	public String githubSecret() {
		return getConfigParam("gh_secret", "");
	}

	/**
	 * Microsoft OAuth2 app ID.
	 * @return the Microsoft app id
	 */
	@Documented(position = 380,
			identifier = "ms_app_id",
			category = "Basic Authentication",
			description = "Microsoft OAuth2 app ID.")
	public String microsoftAppId() {
		return getConfigParam("ms_app_id", "");
	}

	/**
	 * Microsoft app secret key.
	 * @return the Microsoft secret
	 */
	@Documented(position = 390,
			identifier = "ms_secret",
			category = "Basic Authentication",
			description = "Microsoft app secret key.")
	public String microsoftSecret() {
		return getConfigParam("ms_secret", "");
	}

	/**
	 * Microsoft OAuth2 tenant ID.
	 * @return the Microsoft tenant id
	 */
	@Documented(position = 400,
			identifier = "ms_tenant_id",
			value = "common",
			category = "Basic Authentication",
			description = "Microsoft OAuth2 tenant ID.")
	public String microsoftTenantId() {
		return getConfigParam("ms_tenant_id", "common");
	}

	/**
	 * Amazon OAuth2 app ID.
	 * @return the Amazon app id
	 */
	@Documented(position = 410,
			identifier = "az_app_id",
			category = "Basic Authentication",
			description = "Amazon OAuth2 app ID.")
	public String amazonAppId() {
		return getConfigParam("az_app_id", "");
	}

	/**
	 * Amazon app secret key.
	 * @return the Amazon secret
	 */
	@Documented(position = 420,
			identifier = "az_secret",
			category = "Basic Authentication",
			description = "Amazon app secret key.")
	public String amazonSecret() {
		return getConfigParam("az_secret", "");
	}

	/**
	 * Slack OAuth2 app ID.
	 * @return the Slack app id
	 */
	@Documented(position = 430,
			identifier = "sl_app_id",
			category = "Basic Authentication",
			description = "Slack OAuth2 app ID.")
	public String slackAppId() {
		return getConfigParam("sl_app_id", "");
	}

	/**
	 * Slack app secret key.
	 * @return the Slack secret
	 */
	@Documented(position = 440,
			identifier = "sl_secret",
			category = "Basic Authentication",
			description = "Slack app secret key.")
	public String slackSecret() {
		return getConfigParam("sl_secret", "");
	}

	/**
	 * Mattermost OAuth2 app ID.
	 * @return the Mattermost app id
	 */
	@Documented(position = 450,
			identifier = "mm_app_id",
			category = "Basic Authentication",
			description = "Mattermost OAuth2 app ID.")
	public String mattermostAppId() {
		return getConfigParam("mm_app_id", "");
	}

	/**
	 * Mattermost app secret key.
	 * @return the Mattermost secret
	 */
	@Documented(position = 460,
			identifier = "mm_secret",
			category = "Basic Authentication",
			description = "Mattermost app secret key.")
	public String mattermostSecret() {
		return getConfigParam("mm_secret", "");
	}

	/**
	 * Return the OAuth app ID and secret key for a given app by reading the app settings, or the config file.
	 *
	 * @param app the app in which to look for these keys
	 * @param prefix a service prefix: "fb" for facebook, "tw" for twitter etc. See {@link Config}
	 * @return an array ["app_id", "secret_key"] or ["", ""]
	 */
	public String[] getOAuthKeysForApp(App app, String prefix) {
		prefix = Strings.CS.removeEnd(prefix + "", separator());
		String appIdKey = prefix + "_app_id";
		String secretKey = prefix + "_secret";
		String[] keys = new String[]{"", ""};

		if (app != null) {
			Map<String, Object> settings = app.getSettings();
			if (settings.containsKey(appIdKey) && settings.containsKey(secretKey)) {
				keys[0] = settings.get(appIdKey) + "";
				keys[1] = settings.get(secretKey) + "";
			} else if (app.isRootApp()) {
				keys[0] = getConfigParam(appIdKey, "");
				keys[1] = getConfigParam(secretKey, "");
			}
		}
		return keys;
	}

	/* **************************************************************************************************************
	 * Security                                                                                            Security *
	 ****************************************************************************************************************/

	/**
	 * Enable/disable the CORS filter. It adds CORS headers to API responses.
	 * @return true if CORS is enabled
	 */


	@Documented(position = 470,
			identifier = "cors_enabled",
			value = "true",
			type = Boolean.class,
			category = "Security",
			tags = {"requires restart"},
			description = "Enable/disable the CORS filter. It adds CORS headers to API responses.")
	public boolean corsEnabled() {
		return Boolean.parseBoolean(getConfigParam("cors_enabled", "true"));
	}

	/**
	 * Enable/disable CSRF protection which checks for valid CSRF tokens in write requests.
	 * @return true if CSRF protection is enabled
	 */
	@Documented(position = 480,
			identifier = "security.csrf_protection",
			value = "true",
			type = Boolean.class,
			category = "Security",
			tags = {"requires restart"},
			description = "Enable/disable CSRF protection which checks for valid CSRF tokens in write requests.")
	public boolean csrfProtectionEnabled() {
		return getConfigBoolean("security.csrf_protection", true);
	}

	/**
	 * Enable/disable CSRF protection with single-page application mode enabled.
	 * @return true if CSRF protection with SPA is enabled
	 */
	@Documented(position = 480,
			identifier = "security.csrf_protection_with_spa",
			value = "true",
			type = Boolean.class,
			category = "Security",
			tags = {"requires restart"},
			description = "Enable/disable CSRF protection with single-page application mode enabled.")
	public boolean csrfProtectionWithSpaEnabled() {
		return getConfigBoolean("security.csrf_protection_with_spa", false);
	}

	/**
	 * The name of the authorization cookie.
	 * @return the auth cookie name
	 */
	@Documented(position = 500,
			identifier = "auth_cookie",
			value = "para-auth",
			category = "Security",
			description = "The name of the authorization cookie.")
	public String authCookieName() {
		return getConfigParam("auth_cookie", PARA.concat("-auth"));
	}

	/**
	 * Expiration period for signed API request, in seconds.
	 * @return the request expiration period
	 */
	@Documented(position = 510,
			identifier = "request_expires_after",
			value = "900",
			type = Integer.class,
			category = "Security",
			description = "Expiration period for signed API request, in seconds.")
	public int requestExpiresAfterSec() {
		return NumberUtils.toInt(getConfigParam("request_expires_after", ""), 15 * 60);
	}

	/**
	 * Expiration period for JWTs (access token), in seconds.
	 * @return the JWT expiration period
	 */
	@Documented(position = 520,
			identifier = "jwt_expires_after",
			value = "86400",
			type = Integer.class,
			category = "Security",
			description = "Expiration period for JWTs (access token), in seconds.")
	public int jwtExpiresAfterSec() {
		return NumberUtils.toInt(getConfigParam("jwt_expires_after", ""), 24 * 60 * 60);
	}

	/**
	 * JWT refresh interval, after which a new token is issued, in seconds.
	 * @return the JWT refresh interval
	 */
	@Documented(position = 530,
			identifier = "jwt_refresh_interval",
			value = "3600",
			type = Integer.class,
			category = "Security",
			description = "JWT refresh interval, after which a new token is issued, in seconds.")
	public int jwtRefreshIntervalSec() {
		return NumberUtils.toInt(getConfigParam("jwt_refresh_interval", ""), 60 * 60);
	}

	/**
	 * Expiration period for short-lived ID tokens, in seconds.
	 * @return the ID token expiration period
	 */
	@Documented(position = 540,
			identifier = "id_token_expires_after",
			value = "60",
			type = Integer.class,
			category = "Security",
			description = "Expiration period for short-lived ID tokens, in seconds.")
	public int idTokenExpiresAfterSec() {
		return NumberUtils.toInt(getConfigParam("id_token_expires_after", ""), 60);
	}

	/**
	 * Expiration period for the login session, in seconds.
	 * @return the session timeout
	 */
	@Documented(position = 550,
			identifier = "session_timeout",
			value = "86400",
			type = Integer.class,
			category = "Security",
			description = "Expiration period for the login session, in seconds.")
	public int sessionTimeoutSec() {
		return NumberUtils.toInt(getConfigParam("session_timeout", ""), 24 * 60 * 60);
	}

	/**
	 * The minimum length of user passwords.
	 * @return the min password length
	 */
	@Documented(position = 560,
			identifier = "min_password_length",
			value = "8",
			type = Integer.class,
			category = "Security",
			description = "The minimum length of user passwords.")
	public int minPasswordLength() {
		return getConfigInt("min_password_length", 8);
	}

	/**
	 * The time window in which passwords can be reset, in seconds.
	 * After that the token in the email expires.
	 * @return the password reset timeout
	 */
	@Documented(position = 570,
			identifier = "pass_reset_timeout",
			value = "1800",
			type = Integer.class,
			category = "Security",
			description = "The time window in which passwords can be reset, in seconds. "
					+ "After that the token in the email expires.")
	public int passwordResetTimeoutSec() {
		return NumberUtils.toInt(getConfigParam("pass_reset_timeout", ""), 30 * 60);
	}

	/**
	 * The maximum number of passord matching attempts for user accounts per time unit.
	 * After that the account is locked and user cannot login until the lock has expired.
	 * @return the max password matching attempts
	 */
	@Documented(position = 571,
			identifier = "max_pass_matching_attemts",
			value = "20",
			type = Integer.class,
			category = "Security",
			description = "The maximum number of passord matching attempts for user accounts per time unit. "
					+ "After that the account is locked and user cannot login until the lock has expired.")
	public int maxPasswordMatchingAttempts() {
		return NumberUtils.toInt(getConfigParam("max_pass_matching_attemts", ""), 20);
	}

	/**
	 * The time to force a user to wait until they can try to log back in, in hours.
	 * @return the password matching lock period in hours
	 */
	@Documented(position = 572,
			identifier = "pass_matching_lock_period_hours",
			value = "1",
			type = Integer.class,
			category = "Security",
			description = "The time to force a user to wait until they can try to log back in, in hours.")
	public int passwordMatchingLockPeriodHours() {
		return NumberUtils.toInt(getConfigParam("pass_matching_lock_period_hours", ""), 1);
	}

	/**
	 * The name of the cookie used to remember which URL the user requested and will be
	 * redirected to after login.
	 * @return the return-to cookie name
	 */
	@Documented(position = 580,
			identifier = "returnto_cookie",
			value = "para-returnto",
			category = "Security",
			description = "The name of the cookie used to remember which URL the user requested and will be "
					+ "redirected to after login.")
	public String returnToCookieName() {
		return getConfigParam("returnto_cookie", PARA.concat("-returnto"));
	}

	/**
	 * The email of the webmaster/support team. Para will send emails to this email.
	 * @return the support email address
	 */
	@Documented(position = 590,
			identifier = "support_email",
			value = "support@myapp.co",
			category = "Security",
			description = "The email of the webmaster/support team. Para will send emails to this email.")
	public String supportEmail() {
		return getConfigParam("support_email", "support@myapp.co");
	}

	/**
	 * Enable/disable email verification after the initial user registration. Users with unverified
	 * emails won't be able to sign in, unless they use a social login provider.
	 * @return true if unverified emails are allowed
	 */
	@Documented(position = 600,
			identifier = "security.allow_unverified_emails",
			value = "false",
			type = Boolean.class,
			category = "Security",
			description = "Enable/disable email verification after the initial user registration. Users with unverified "
					+ "emails won't be able to sign in, unless they use a social login provider.")
	public boolean allowUnverifiedEmails() {
		return getConfigBoolean("security.allow_unverified_emails", false);
	}

	/**
	 * Protects a named resource by requiring users to authenticated before accessing it.
	 * A protected resource has a `{name}` and value like this `[\"/{path}\", \"/{path}/**\", [\"{role}\"
	 * or {http_method}]]`. The value is an array of relative paths which are matche by an ANT pattern
	 * matcher. This array can contain a subarray which lists all the HTTP methods that require
	 * authentication and the user roles that are allowed to access this particular resource.
	 * No HTTP methods means that all requests to this resource require authentication.
	 * @return a configuration object for protected paths
	 */
	public ConfigObject protectedPaths() {
		return getConfig().hasPath("security.protected") ? getConfig().getObject("security.protected") : null;
	}

	/**
	 * A list of ignored paths which will not require authentication before accessing.
	 * A list of paths like this `[\"/{path1}\", \"/{path2}/**\", ...]`.
	 * @return a configuration list for ignored paths
	 */
	@Documented(position = 611,
			identifier = "security.ignored",
			type = ConfigList.class,
			category = "Security",
			description = "A list of ignored paths which will not require authentication before accessing. "
					+ "A list of paths like this `[\"/{path1}\", \"/{path2}/**\", ...]`.")
	public ConfigList ignoredPaths() {
		return getConfig().hasPath("security.ignored") ? getConfig().getList("security.ignored") : null;
	}

	/**
	 * The path to the login page.
	 * @return the sign-in path
	 */
	@Documented(position = 620,
			identifier = "security.signin",
			value = "/signin",
			category = "Security",
			description = "The path to the login page.")
	public String signinPath() {
		return getConfigParam("security.signin", "/signin");
	}

	/**
	 * The default page to send users to when they login.
	 * @return the sign-in success path
	 */
	@Documented(position = 630,
			identifier = "security.signin_success",
			value = "/",
			category = "Security",
			description = "The default page to send users to when they login.")
	public String signinSuccessPath() {
		return getConfigParam("security.signin_success", "/");
	}

	/**
	 * The default page to send users to when login fails.
	 * @return the sign-in failure path
	 */
	@Documented(position = 640,
			identifier = "security.signin_failure",
			value = "/signin?error",
			category = "Security",
			description = "The default page to send users to when login fails.")
	public String signinFailurePath() {
		return getConfigParam("security.signin_failure", "/signin?error");
	}

	/**
	 * The path to the logout page.
	 * @return the sign-out path
	 */
	@Documented(position = 650,
			identifier = "security.signout",
			value = "/signout",
			category = "Security",
			description = "The path to the logout page.")
	public String signoutPath() {
		return getConfigParam("security.signout", "/signout");
	}

	/**
	 * The default page to send users to when they logout.
	 * @return the sign-out success path
	 */
	@Documented(position = 660,
			identifier = "security.signout_success",
			value = "/signin",
			category = "Security",
			description = "The default page to send users to when they logout.")
	public String signoutSuccessPath() {
		return getConfigParam("security.signout_success", signinPath());
	}

	/**
	 * The path to redirect to when 403 code is returned.
	 * @return the access denied path
	 */
	@Documented(position = 670,
			identifier = "security.access_denied",
			value = "/403",
			category = "Security",
			description = "The path to redirect to when 403 code is returned.")
	public String accessDeniedPath() {
		return getConfigParam("security.access_denied", "/error/403");
	}

	/**
	 * The path to return to when an authentication request succeeds.
	 * @return the return-to path
	 */
	@Documented(position = 680,
			identifier = "security.returnto",
			value = "returnto",
			category = "Security",
			description = "The path to return to when an authentication request succeeds.")
	public String returnToPath() {
		return getConfigParam("security.returnto", "returnto");
	}

	/**
	 * Enable/disable remember me functionality.
	 * @return true if remember me is enabled
	 * @deprecated
	 */
	@Documented(position = 690,
			identifier = "security.remember_me",
			value = "true",
			type = Boolean.class,
			category = "Security",
			tags = {"deprecated"},
			description = "Enable/disable remember me functionality.")
	@Deprecated
	public boolean rememberMeEnabled() {
		return getConfigBoolean("security.remember_me", true);
	}

	/**
	 * Salt.
	 * @return the app secret key
	 * @deprecated
	 */
	@Documented(position = 700,
			identifier = "app_secret_key",
			value = "md5('paraseckey')",
			category = "Security",
			tags = {"deprecated"},
			description = "Salt.")
	@Deprecated
	public String appSecretKey() {
		return getConfigParam("app_secret_key", Utils.md5("paraseckey"));
	}

	/* **************************************************************************************************************
	 * River & Queue                                                                                  River & Queue *
	 ****************************************************************************************************************/

	/**
	 * Returns the default queue name which will be polled for incoming JSON messages.
	 * @return The default queue name which will be polled for incoming JSON messages.
	 */
	@Documented(position = 710,
			identifier = "default_queue_name",
			value = "para-default",
			category = "River & Queue",
			description = "The name of the queue used by Para.")
	public String defaultQueueName() {
		return getConfigParam("default_queue_name", PARA + "-default");
	}

	/**
	 * Enable/disable polling the queue for message. This controls the 'river' feature in Para.
	 * @return true if queue polling is enabled
	 */
	@Documented(position = 720,
			identifier = "queue_link_enabled",
			value = "false",
			type = Boolean.class,
			category = "River & Queue",
			description = "Enable/disable polling the queue for message. This controls the 'river' feature in Para.")
	public boolean queuePollingEnabled() {
		return getConfigBoolean("queue_link_enabled", false);
	}

	/**
	 * The polling sleep time, in seconds.
	 * @return the polling wait time
	 */
	@Documented(position = 730,
			identifier = "queue.polling_sleep_seconds",
			value = "60",
			type = Integer.class,
			category = "River & Queue",
			description = "")
	public int queuePollingWaitSec() {
		return getConfigInt("queue.polling_sleep_seconds", 60);
	}

	/**
	 * The polling interval of the Para river, in seconds. Polls queue for messages.
	 * @return the polling interval
	 */
	@Documented(position = 740,
			identifier = "queue.polling_interval_seconds",
			value = "10",
			type = Integer.class,
			category = "River & Queue",
			description = "The polling interval of the Para river, in seconds. Polls queue for messages.")
	public int queuePollingIntervalSec() {
		return getConfigInt("queue.polling_interval_seconds", 10);
	}

	/**
	 * The maximum number of attempts at reading an object from database and indexing it, when the
	 * operation was received from the queue.
	 * @return the max indexing retries
	 */
	@Documented(position = 750,
			identifier = "river.max_indexing_retries",
			value = "5",
			type = Integer.class,
			category = "River & Queue",
			description = "The maximum number of attempts at reading an object from database and indexing it, when the "
					+ "operation was received from the queue.")
	public int riverMaxIndexingRetries() {
		return getConfigInt("river.max_indexing_retries", 5);
	}

	/**
	 * The time interval between the sending of each batch of index synchronization messages to
	 * the queue, in seconds.
	 * @return the global sync interval
	 */
	@Documented(position = 760,
			identifier = "indexing_sync_interval_sec",
			value = "10",
			type = Integer.class,
			category = "River & Queue",
			description = "The time interval between the sending of each batch of index synchronization messages to "
					+ "the queue, in seconds.")
	public int globalSyncIntervalSec() {
		return getConfigInt("indexing_sync_interval_sec", 10);
	}

	/* **************************************************************************************************************
	 * Metrics                                                                                              Metrics *
	 ****************************************************************************************************************/

	/**
	 * Enable/disable the built-in metrics around CRUD methods.
	 * @return true if metrics are enabled
	 */


	@Documented(position = 770,
			identifier = "metrics_enabled",
			value = "true",
			type = Boolean.class,
			category = "Metrics",
			description = "Enable/disable the built-in metrics around CRUD methods.")
	public boolean metricsEnabled() {
		return getConfigBoolean("metrics_enabled", true);
	}

	/**
	 * The rate at which the metrics logger will write to file, in seconds.
	 * @return the metrics logging interval
	 */
	@Documented(position = 780,
			identifier = "metrics.logging_rate",
			value = "60",
			type = Integer.class,
			category = "Metrics",
			description = "The rate at which the metrics logger will write to file, in seconds.")
	public int metricsLoggingIntervalSec() {
		return getConfigInt("metrics.logging_rate", 60);
	}

	/**
	 * The URL of the Graphite host to push metrics to.
	 * @return the Graphite host
	 */
	@Documented(position = 790,
			identifier = "metrics.graphite.host",
			category = "Metrics",
			description = "The URL of the Graphite host to push metrics to.")
	public String metricsGraphiteHost() {
		return getConfigParam("metrics.graphite.host", null);
	}

	/**
	 * The port number of the Graphite server.
	 * @return the Graphite port
	 */
	@Documented(position = 800,
			identifier = "metrics.graphite.port",
			value = "2003",
			type = Integer.class,
			category = "Metrics",
			description = "The port number of the Graphite server.")
	public int metricsGraphitePort() {
		return getConfigInt("metrics.graphite.port", 2003);
	}

	/**
	 * The prefix to apply to system metric names.
	 * @return the Graphite system prefix
	 */
	@Documented(position = 810,
			identifier = "metrics.graphite.prefix_system",
			category = "Metrics",
			description = "")
	public String metricsGraphitePrefixSystem() {
		return getConfigParam("metrics.graphite.prefix_system", null);
	}

	/**
	 * The prefix to apply to metric names, e.g. `com.erudika.para.{{INSTANCE_ID}}`.
	 * @return the Graphite apps prefix
	 */
	@Documented(position = 820,
			identifier = "metrics.graphite.prefix_apps",
			category = "Metrics",
			description = "The prefix to apply to metric names, e.g. `com.erudika.para.{{INSTANCE_ID}}`.")
	public String metricsGraphitePrefixApps() {
		return getConfigParam("metrics.graphite.prefix_apps", null);
	}

	/**
	 * The period for how often to push system metrics in seconds. Disabled by default.
	 * @return the Graphite push period
	 */
	@Documented(position = 830,
			identifier = "metrics.graphite.period",
			value = "0",
			type = Integer.class,
			category = "Metrics",
			description = "The period for how often to push system metrics in seconds. Disabled by default.")
	public int metricsGraphitePeriodSec() {
		return getConfigInt("metrics.graphite.period", 0);
	}

	/**
	 * Enable/disable JMX reporting for all metrics.
	 * @return true if JMX reporting is enabled
	 */
	@Documented(position = 840,
			identifier = "metrics.jmx_enabled",
			value = "false",
			type = Boolean.class,
			category = "Metrics",
			description = "Enable/disable JMX reporting for all metrics.")
	public boolean metricsJmxEnabled() {
		return getConfigBoolean("metrics.jmx_enabled", false);
	}


	/* **************************************************************************************************************
	 * LDAP Authentication                                                                      LDAP Authentication *
	 ****************************************************************************************************************/

	/**
	 * LDAP password parameter name.
	 * @return the LDAP password parameter
	 */


	@Documented(position = 850,
			identifier = "security.ldap.password_param",
			value = "password",
			category = "LDAP Authentication",
			description = "LDAP password parameter name.")
	public String ldapPasswordParameter() {
		return getConfigParam("security.ldap.password_param", "password");
	}

	/**
	 * LDAP username parameter name.
	 * @return the LDAP username parameter
	 */
	@Documented(position = 860,
			identifier = "security.ldap.username_param",
			value = "username",
			category = "LDAP Authentication",
			description = "LDAP username parameter name.")
	public String ldapUsernameParameter() {
		return getConfigParam("security.ldap.username_param", "username");
	}

	/**
	 * Returns a map of LDAP configuration properties for a given app, read from app.settings or config file.
	 *
	 * @param app the app in which to look for these keys
	 * @return a map of keys and values
	 */
	public Map<String, String> getLdapSettingsForApp(App app) {
		Map<String, String> ldapSettings = new HashMap<>();
		if (app != null) {
			ldapSettings.put("security.ldap.server_url", "ldap://localhost:8389/");
			ldapSettings.put("security.ldap.active_directory_domain", "");
			ldapSettings.put("security.ldap.ad_mode_enabled", "");
			ldapSettings.put("security.ldap.base_dn", "dc=springframework,dc=org");
			ldapSettings.put("security.ldap.bind_dn", "");
			ldapSettings.put("security.ldap.bind_pass", "");
			ldapSettings.put("security.ldap.user_search_base", "");
			ldapSettings.put("security.ldap.user_search_filter", "(cn={0})");
			ldapSettings.put("security.ldap.user_dn_pattern", "uid={0}");
			ldapSettings.put("security.ldap.password_attribute", "userPassword");
			ldapSettings.put("security.ldap.displayname_attribute", "cn");
			//ldapSettings.put("security.ldap.compare_passwords", "false"); //don't remove comment
			Map<String, Object> settings = app.getSettings();
			for (Map.Entry<String, String> entry : ldapSettings.entrySet()) {
				if (settings.containsKey(entry.getKey())) {
					entry.setValue(settings.get(entry.getKey()) + "");
				} else if (app.isRootApp()) {
					entry.setValue(getConfigParam(entry.getKey(), entry.getValue()));
				}
			}
		}
		return ldapSettings;
	}

	/* **************************************************************************************************************
	 * File Storage                                                                                    File Storage *
	 ****************************************************************************************************************/

	/**
	 * The S3 bucket where files will be stored by `FileStore` implementations.
	 * @param region the AWS region
	 * @return the S3 bucket name
	 */


	@Documented(position = 870,
			identifier = "s3.bucket",
			value = "org.paraio.us-east-1",
			category = "File Storage",
			description = "The S3 bucket where files will be stored by `FileStore` implementations.")
	public String awsS3Bucket(String region) {
		return getConfigParam("s3.bucket", "org.paraio." +	region);
	}

	/**
	 * Maximum file size for files uploaded to S3, in megabytes.
	 * @return the max file size in MB
	 */
	@Documented(position = 880,
			identifier = "s3.max_filesize_mb",
			value = "10",
			type = Integer.class,
			category = "File Storage",
			description = "Maximum file size for files uploaded to S3, in megabytes.")
	public int awsS3MaxFileSizeMb() {
		return getConfigInt("s3.max_filesize_mb", 10);
	}

	/**
	 * The local folder for file storage, when `LocalFileStore` is used.
	 * @return the local storage folder
	 */
	@Documented(position = 890,
			identifier = "localstorage.folder",
			category = "File Storage",
			description = "The local folder for file storage, when `LocalFileStore` is used.")
	public String localFileStoreFolder() {
		return getConfigParam("localstorage.folder", "");
	}

	/**
	 * Maximum file size for files stored locally, in megabytes.
	 * @return the max local file size in MB
	 */
	@Documented(position = 900,
			identifier = "localstorage.max_filesize_mb",
			value = "10",
			type = Integer.class,
			category = "File Storage",
			description = "Maximum file size for files stored locally, in megabytes.")
	public int localFileStoreMaxFileSizeMb() {
		return getConfigInt("localstorage.max_filesize_mb", 10);
	}

	/* **************************************************************************************************************
	 * Para Client                                                                                      Para Client *
	 ****************************************************************************************************************/

	/**
	 * SSL protocols allowed for a successul connection.
	 * @return the allowed SSL protocols
	 */


	@Documented(position = 910,
			identifier = "client.ssl_protocols",
			value = "TLSv1.3",
			category = "Para Client",
			description = "SSL protocols allowed for a successul connection.")
	public String clientSslProtocols() {
		return getConfigParam("client.ssl_protocols", "TLSv1.3");
	}

	/**
	 * The SSL key store location. This contains the certificates used by the Para client.
	 * @return the SSL key store location
	 */
	@Documented(position = 920,
			identifier = "client.ssl_keystore",
			category = "Para Client",
			description = "The SSL key store location. This contains the certificates used by the Para client.")
	public String clientSslKeystore() {
		return getConfigParam("client.ssl_keystore", "");
	}

	/**
	 * The SSL key store password.
	 * @return the SSL key store password
	 */
	@Documented(position = 930,
			identifier = "client.ssl_keystore_password",
			category = "Para Client",
			description = "The SSL key store password.")
	public String clientSslKeystorePassword() {
		return getConfigParam("client.ssl_keystore_password", "");
	}

	/**
	 * The SSL trust store location. This contains the certificates and CAs which the client trusts.
	 * @return the SSL trust store location
	 */
	@Documented(position = 940,
			identifier = "client.ssl_truststore",
			category = "Para Client",
			description = "The SSL trust store location. This contains the certificates and CAs which the client trusts.")
	public String clientSslTruststore() {
		return getConfigParam("client.ssl_truststore", "");
	}

	/**
	 * The SSL trust store password.
	 * @return the SSL trust store password
	 */
	@Documented(position = 950,
			identifier = "",
			category = "Para Client",
			description = "The SSL trust store password.")
	public String clientSslTruststorePassword() {
		return getConfigParam("client.ssl_truststore_password", "");
	}

	/**
	 * Enable/disable `User-Agent` header in Para client.
	 * @return true if User-Agent header is enabled
	 */
	@Documented(position = 960,
			identifier = "user_agent_id_enabled",
			value = "true",
			type = Boolean.class,
			category = "Para Client",
			description = "Enable/disable `User-Agent` header in Para client.")
	public boolean clientUserAgentEnabled() {
		return getConfigBoolean("user_agent_id_enabled", true);
	}

	/**
	 * Returns the value of the app setting, read from from app.settings or from the config file if app is root.
	 * @param app the app in which to look for these keys
	 * @param key setting key
	 * @param defaultValue default value
	 * @return the value of the configuration property as string
	 */
	public String getSettingForApp(App app, String key, String defaultValue) {
		if (app != null) {
			Map<String, Object> settings = app.getSettings();
			if (settings.containsKey(key)) {
				return String.valueOf(settings.getOrDefault(key, defaultValue));
			} else if (app.isRootApp()) {
				return getConfigParam(key, defaultValue);
			}
		}
		return defaultValue;
	}

	/* **************************************************************************************************************
	 * Elasticsearch Search                                                                    Elasticsearch Search *
	 ****************************************************************************************************************/

	/**
	 * Eleasticsearch flavor - either `elasticsearch` or `opensearch`.
	 * @return the ES flavor
	 */


	@Documented(position = 970,
			identifier = "es.flavor",
			value = "elasticsearch",
			category = "Elasticsearch Search",
			description = "Eleasticsearch flavor - either `elasticsearch` or `opensearch`.")
	public String elasticsearchFlavor() {
		return getConfigParam("es.flavor", "elasticsearch");
	}

	/**
	 * The number of shards per index. Used when creating the root app index.
	 * @return the root index shards
	 */
	@Documented(position = 980,
			identifier = "es.shards",
			value = "2",
			type = Integer.class,
			category = "Elasticsearch Search",
			description = "The number of shards per index. Used when creating the root app index.")
	public int elasticsearchRootIndexShards() {
		return getConfigInt("es.shards", 2);
	}

	/**
	 * The number of shards per index for a child apps.
	 * @return the child index shards
	 */
	@Documented(position = 990,
			identifier = "es.shards_for_child_apps",
			value = "1",
			type = Integer.class,
			category = "Elasticsearch Search",
			description = "The number of shards per index for a child apps.")
	public int elasticsearchChildIndexShards() {
		return getConfigInt("es.shards_for_child_apps", 1);
	}

	/**
	 * The number of copies to store of the root index.
	 * @return the root index replicas
	 */
	@Documented(position = 1000,
			identifier = "es.replicas",
			value = "0",
			type = Integer.class,
			category = "Elasticsearch Search",
			description = "The number of copies to store of the root index.")
	public int elasticsearchRootIndexReplicas() {
		return getConfigInt("es.replicas", 0);
	}

	/**
	 * The number of copies to store of each child app index.
	 * @return the child index replicas
	 */
	@Documented(position = 1010,
			identifier = "es.replicas_for_child_apps",
			value = "0",
			type = Integer.class,
			category = "Elasticsearch Search",
			description = "The number of copies to store of each child app index.")
	public int elasticsearchChildIndexReplicas() {
		return getConfigInt("es.replicas_for_child_apps", 0);
	}

	/**
	 * Switches between normal indexing and indexing with nested key/value objects for custom properties.
	 * When this is `false` (normal mode), Para objects will be indexed without modification but this
	 * could lead to a field mapping explosion and crash the ES cluster.
	 * @return true if nested mode is enabled
	 */
	@Documented(position = 1020,
			identifier = "es.use_nested_custom_fields",
			value = "false",
			type = Boolean.class,
			category = "Elasticsearch Search",
			description = "Switches between normal indexing and indexing with nested key/value objects for custom properties. "
					+ "When this is `false` (normal mode), Para objects will be indexed without modification but this "
					+ "could lead to a field mapping explosion and crash the ES cluster.")
	public boolean elasticsearchNestedModeEnabled() {
		return getConfigBoolean("es.use_nested_custom_fields", false);
	}

	/**
	 * Enable/disable asynchronous operations when indexing/unindexing.
	 * @return true if async mode is enabled
	 */
	@Documented(position = 1030,
			identifier = "es.async_enabled",
			value = "false",
			type = Boolean.class,
			category = "Elasticsearch Search",
			description = "Enable/disable asynchronous operations when indexing/unindexing.")
	public boolean elasticsearchAsyncModeEnabled() {
		return getConfigBoolean("es.async_enabled", false);
	}

	/**
	 * Eanble/disable immediately flushing the requests in `BulkProcessor`, concurrently (in another thread).
	 * @return true if bulk flush is enabled
	 */
	@Documented(position = 1040,
			identifier = "es.bulk.flush_immediately",
			value = "true",
			type = Boolean.class,
			category = "Elasticsearch Search",
			description = "Eanble/disable immediately flushing the requests in `BulkProcessor`, concurrently (in another thread).")
	public boolean elasticsearchBulkFlushEnabled() {
		return getConfigBoolean("es.bulk.flush_immediately", true);
	}

	/**
	 * The scheme to use when connecting to the Elasticsearch server - `http` or `https`.
	 * @return the REST client scheme
	 */
	@Documented(position = 1050,
			identifier = "es.restclient_scheme",
			value = "http",
			category = "Elasticsearch Search",
			description = "The scheme to use when connecting to the Elasticsearch server - `http` or `https`.")
	public String elasticsearchRestClientScheme() {
		return getConfigParam("es.restclient_scheme", inProduction() ? "https" : "http");
	}

	/**
	 * The ES server hostname.
	 * @return the REST client host
	 */
	@Documented(position = 1060,
			identifier = "es.restclient_host",
			value = "localhost",
			category = "Elasticsearch Search",
			description = "The ES server hostname.")
	public String elasticsearchRestClientHost() {
		return getConfigParam("es.restclient_host", "localhost");
	}

	/**
	 * The ES server port number.
	 * @return the REST client port
	 */
	@Documented(position = 1070,
			identifier = "es.restclient_port",
			value = "9200",
			type = Integer.class,
			category = "Elasticsearch Search",
			description = "The ES server port number.")
	public int elasticsearchRestClientPort() {
		return getConfigInt("es.restclient_port", 9200);
	}

	/**
	 * Enable/disable request signing using the AWS V4 algorithm. For use with Amazon OpenSearch.
	 * @return true if request signing is enabled
	 */
	@Documented(position = 1080,
			identifier = "es.sign_requests_to_aws",
			value = "false",
			type = Boolean.class,
			category = "Elasticsearch Search",
			description = "Enable/disable request signing using the AWS V4 algorithm. For use with Amazon OpenSearch.")
	public boolean elasticsearchSignRequestsForAwsEnabled() {
		return getConfigBoolean("es.sign_requests_to_aws", elasticsearchRestClientHost().contains("amazonaws.com"));
	}

	/**
	 * The context path where ES is deployed, if any.
	 * @return the REST client context path
	 */
	@Documented(position = 1090,
			identifier = "es.restclient_context_path",
			category = "Elasticsearch Search",
			description = "The context path where ES is deployed, if any.")
	public String elasticsearchRestClientContextPath() {
		return getConfigParam("es.restclient_context_path", "");
	}

	/**
	 * Automatically make a replica copy of the index to the number of nodes specified.
	 * @return the auto expand replicas setting
	 */
	@Documented(position = 1100,
			identifier = "es.auto_expand_replicas",
			value = "0-1",
			category = "Elasticsearch Search",
			description = "Automatically make a replica copy of the index to the number of nodes specified.")
	public String elasticsearchAutoExpandReplicas() {
		return getConfigParam("es.auto_expand_replicas", "0-1");
	}

	/**
	 * Enable/disable root index sharing by child apps configured with `isSharingIndex = true`.
	 * @return true if root index sharing is enabled
	 */
	@Documented(position = 1110,
			identifier = "es.root_index_sharing_enabled",
			value = "false",
			type = Boolean.class,
			category = "Elasticsearch Search",
			description = "Enable/disable root index sharing by child apps configured with `isSharingIndex = true`.")
	public boolean elasticsearchRootIndexSharingEnabled() {
		return getConfigBoolean("es.root_index_sharing_enabled", false);
	}

	/**
	 * Makes ES track the actual number of hits, even if they are more than the 10000.
	 * @return true if tracking total hits
	 */
	@Documented(position = 1120,
			identifier = "es.track_total_hits",
			value = "true",
			type = Boolean.class,
			category = "Elasticsearch Search",
			description = "Makes ES track the actual number of hits, even if they are more than the 10000. ")
	public String elasticsearchTrackTotalHits() {
		return getConfigParam("es.track_total_hits", "true");
	}

	/**
	 * The AWS region where ES is deployed. Used for calculating request signatures.
	 * @return the AWS region for ES
	 */
	@Documented(position = 1130,
			identifier = "es.aws_region",
			value = "eu-west-1",
			category = "Elasticsearch Search",
			description = "The AWS region where ES is deployed. Used for calculating request signatures.")
	public String elasticsearchAwsRegion() {
		return getConfigParam("es.aws_region", "eu-west-1");
	}

	/**
	 * The username to use for authentication with ES.
	 * @return the ES auth user
	 */
	@Documented(position = 1140,
			identifier = "es.basic_auth_login",
			category = "Elasticsearch Search",
			description = "The username to use for authentication with ES.")
	public String elasticsearchAuthUser() {
		return getConfigParam("es.basic_auth_login", "");
	}

	/**
	 * The password to use for authentication with ES.
	 * @return the ES auth password
	 */
	@Documented(position = 1150,
			identifier = "es.basic_auth_password",
			category = "Elasticsearch Search",
			description = "The password to use for authentication with ES.")
	public String elasticsearchAuthPassword() {
		return getConfigParam("es.basic_auth_password", "");
	}

	/**
	 * `BulkProcessor` flush threshold, in megabytes.
	 * @return the ES bulk size limit in MB
	 */
	@Documented(position = 1160,
			identifier = "es.bulk.size_limit_mb",
			value = "5",
			type = Integer.class,
			category = "Elasticsearch Search",
			description = "`BulkProcessor` flush threshold, in megabytes.")
	public int elasticsearchBulkSizeLimitMb() {
		return getConfigInt("es.bulk.size_limit_mb", 5);
	}

	/**
	 * `BulkProcessor` flush threshold in terms of batch size.
	 * @return the ES bulk action limit
	 */
	@Documented(position = 1170,
			identifier = "es.bulk.action_limit",
			value = "1000",
			type = Integer.class,
			category = "Elasticsearch Search",
			description = "`BulkProcessor` flush threshold in terms of batch size.")
	public int elasticsearchBulkActionLimit() {
		return getConfigInt("es.bulk.action_limit", 1000);
	}

	/**
	 * `BulkProcessor` number of concurrent requests (0 means synchronous execution).
	 * @return the ES bulk concurrent requests
	 */
	@Documented(position = 1180,
			identifier = "es.bulk.concurrent_requests",
			value = "1",
			type = Integer.class,
			category = "Elasticsearch Search",
			description = "`BulkProcessor` number of concurrent requests (0 means synchronous execution).")
	public int elasticsearchBulkConcurrentRequests() {
		return getConfigInt("es.bulk.concurrent_requests", 1);
	}

	/**
	 * `BulkProcessor` flush interval, in milliseconds.
	 * @return the ES bulk flush interval
	 */
	@Documented(position = 1190,
			identifier = "es.bulk.flush_interval_ms",
			value = "5000",
			type = Integer.class,
			category = "Elasticsearch Search",
			description = "`BulkProcessor` flush interval, in milliseconds.")
	public int elasticsearchBulkFlushIntervalSec() {
		return getConfigInt("es.bulk.flush_interval_ms", 5000);
	}

	/**
	 * `BulkProcessor` inital backoff delay, in milliseconds.
	 * @return the ES bulk backoff delay
	 */
	@Documented(position = 1200,
			identifier = "es.bulk.backoff_initial_delay_ms",
			value = "50",
			type = Integer.class,
			category = "Elasticsearch Search",
			description = "`BulkProcessor` inital backoff delay, in milliseconds.")
	public int elasticsearchBulkBackoffDelayMs() {
		return getConfigInt("es.bulk.backoff_initial_delay_ms", 50);
	}

	/**
	 * `BulkProcessor` number of retries.
	 * @return the ES bulk backoff retries
	 */
	@Documented(position = 1210,
			identifier = "es.bulk.max_num_retries",
			value = "8",
			type = Integer.class,
			category = "Elasticsearch Search",
			description = "`BulkProcessor` number of retries.")
	public int elasticsearchBulkBackoffRetries() {
		return getConfigInt("es.bulk.max_num_retries", 8);
	}

	/**
	 * Enable/disable the Elasticsearch proxy endpoint.
	 * @return true if ES proxy is enabled
	 */
	@Documented(position = 1220,
			identifier = "es.proxy_enabled",
			value = "false",
			type = Boolean.class,
			category = "Elasticsearch Search",
			description = "Enable/disable the Elasticsearch proxy endpoint.")
	public boolean elasticsearchProxyEnabled() {
		return getConfigBoolean("es.proxy_enabled", false);
	}

	/**
	 * The path to the ES proxy endpoint.
	 * @return the ES proxy path
	 */
	@Documented(position = 1230,
			identifier = "es.proxy_path",
			value = "_elasticsearch",
			category = "Elasticsearch Search",
			description = "The path to the ES proxy endpoint.")
	public String elasticsearchProxyPath() {
		return getConfigParam("es.proxy_path", "_elasticsearch");
	}

	/**
	 * Enable/disable rebuilding indices through the Elasticsearch proxy endpoint.
	 * @return true if ES proxy reindexing is enabled
	 */
	@Documented(position = 1240,
			identifier = "es.proxy_reindexing_enabled",
			value = "false",
			type = Boolean.class,
			category = "Elasticsearch Search",
			description = "Enable/disable rebuilding indices through the Elasticsearch proxy endpoint.")
	public boolean elasticsearchProxyReindexingEnabled() {
		return getConfigBoolean("es.proxy_reindexing_enabled", false);
	}

	/* **************************************************************************************************************
	 * Lucene Search                                                                                  Lucene Search *
	 ****************************************************************************************************************/

	/**
	 * The data folder where Lucene stores its indexes.
	 * @return the Lucene data folder
	 */


	@Documented(position = 1250,
			identifier = "lucene.dir",
			value = "./",
			category = "Lucene Search",
			description = "The data folder where Lucene stores its indexes.")
	public String luceneDataFolder() {
		return getConfigParam("lucene.dir", Paths.get(".").toAbsolutePath().normalize().toString());
	}

	/* **************************************************************************************************************
	 * MongoDB DAO                                                                                      MongoDB DAO *
	 ****************************************************************************************************************/

	/**
	 * The MongoDB URI string - verrides host, port, user and password if set.
	 * @return the MongoDB connection URI
	 */


	@Documented(position = 1260,
			identifier = "mongodb.uri",
			category = "MongoDB DAO",
			description = "The MongoDB URI string - verrides host, port, user and password if set.")
	public String mongoConnectionUri() {
		return getConfigParam("mongodb.uri", "");
	}

	/**
	 * The database name that Para will use. The database should exist before starting Para.
	 * @return the MongoDB database name
	 */
	@Documented(position = 1270,
			identifier = "mongodb.database",
			value = "para",
			category = "MongoDB DAO",
			description = "The database name that Para will use. The database should exist before starting Para.")
	public String mongoDatabase() {
		return getConfigParam("mongodb.database", getRootAppIdentifier());
	}

	/**
	 * The hostname of the MongoDB server.
	 * @return the MongoDB host
	 */
	@Documented(position = 1280,
			identifier = "mongodb.host",
			value = "localhost",
			category = "MongoDB DAO",
			description = "The hostname of the MongoDB server.")
	public String mongoHost() {
		return getConfigParam("mongodb.host", "localhost");
	}

	/**
	 * The MongoDB server port.
	 * @return the MongoDB port
	 */
	@Documented(position = 1290,
			identifier = "mongodb.port",
			value = "27017",
			type = Integer.class,
			category = "MongoDB DAO",
			description = "The MongoDB server port.")
	public int mongoPort() {
		return getConfigInt("mongodb.port", 27017);
	}

	/**
	 * The username with access to the MongoDB database.
	 * @return the MongoDB auth user
	 */
	@Documented(position = 1300,
			identifier = "mongodb.user",
			category = "MongoDB DAO",
			description = "The username with access to the MongoDB database.")
	public String mongoAuthUser() {
		return getConfigParam("mongodb.user", "");
	}

	/**
	 * The MongoDB user's password.
	 * @return the MongoDB auth password
	 */
	@Documented(position = 1310,
			identifier = "mongodb.password",
			category = "MongoDB DAO",
			description = "The MongoDB user's password.")
	public String mongoAuthPassword() {
		return getConfigParam("mongodb.password", "");
	}

	/**
	 * Enable/disable the SSL/TLS transport layer.
	 * @return true if MongoDB SSL is enabled
	 */
	@Documented(position = 1320,
			identifier = "mongodb.ssl_enabled",
			value = "false",
			type = Boolean.class,
			category = "MongoDB DAO",
			description = "Enable/disable the SSL/TLS transport layer.")
	public boolean mongoSslEnabled() {
		return getConfigBoolean("mongodb.ssl_enabled", false);
	}

	/**
	 * Allows a connection to any host over SSL by ignoring the certificate validation.
	 * @return true if all MongoDB SSL certificates are allowed
	 */
	@Documented(position = 1330,
			identifier = "mongodb.ssl_allow_all",
			value = "false",
			type = Boolean.class,
			category = "MongoDB DAO",
			description = "Allows a connection to any host over SSL by ignoring the certificate validation.")
	public boolean mongoSslAllowAll() {
		return getConfigBoolean("mongodb.ssl_allow_all", false);
	}

	/* **************************************************************************************************************
	 * SQL DAO                                                                                              SQL DAO *
	 ****************************************************************************************************************/

	/**
	 * The hostname of the H2 server. Setting this will enable H2’s “server mode” and start a TCP server.
	 * @return the H2 host
	 */


	@Documented(position = 1340,
			identifier = "db.hostname",
			category = "SQL DAO",
			description = "The hostname of the H2 server. Setting this will enable H2’s “server mode” and start a TCP server.")
	public String h2Host() {
		return getConfigParam("db.hostname", "");
	}

	/**
	 * The data directory for storing H2 databases.
	 * @return the H2 data folder
	 */
	@Documented(position = 1350,
			identifier = "db.dir",
			value = "./data",
			category = "SQL DAO",
			description = "The data directory for storing H2 databases.")
	public String h2DataFolder() {
		return getConfigParam("db.dir", "./data");
	}

	/**
	 * The username with access to the H2 database.
	 * @return the H2 user
	 */
	@Documented(position = 1360,
			identifier = "db.user",
			value = "para",
			category = "SQL DAO",
			description = "The username with access to the H2 database.")
	public String h2User() {
		return getConfigParam("db.user", getRootAppIdentifier());
	}

	/**
	 * The password of the H2 user.
	 * @return the H2 password
	 */
	@Documented(position = 1370,
			identifier = "",
			value = "secret",
			category = "SQL DAO",
			description = "The password of the H2 user.")
	public String h2Password() {
		return getConfigParam("db.password", "secret");
	}

	/**
	 * Parameters for the H2 TCP server.
	 * @return the H2 server parameters
	 */
	@Documented(position = 1380,
			identifier = "db.tcpServer",
			category = "SQL DAO",
			description = "Parameters for the H2 TCP server.")
	public String h2ServerParameters() {
		String tcpPrefix = StringUtils.isBlank(h2Host()) ? "" : "tcp://" + h2Host() + "/";
		return getConfigParam("db.tcpServer", tcpPrefix.isEmpty() ? "-baseDir " + h2DataFolder() : "");
	}

	/**
	 * The server URL to connect to, *without* the `jdbc:` prefix.
	 * @return the SQL host URL
	 */
	@Documented(position = 1390,
			identifier = "sql.url",
			category = "SQL DAO",
			description = "The server URL to connect to, *without* the `jdbc:` prefix.")
	public String sqlHostUrl() {
		return getConfigParam("sql.url", null);
	}

	/**
	 * The fully-qualified class name for your SQL driver.
	 * @return the SQL driver class name
	 */
	@Documented(position = 1400,
			identifier = "sql.driver",
			category = "SQL DAO",
			description = "The fully-qualified class name for your SQL driver.")
	public String sqlDriver() {
		return getConfigParam("sql.driver", null);
	}

	/**
	 * The username with access to the database.
	 * @return the SQL user
	 */
	@Documented(position = 1410,
			identifier = "sql.user",
			value = "user",
			category = "SQL DAO",
			description = "The username with access to the database.")
	public String sqlUser() {
		return getConfigParam("sql.user", "user");
	}

	/**
	 * The database user's password.
	 * @return the SQL password
	 */
	@Documented(position = 1420,
			identifier = "sql.password",
			value = "secret",
			category = "SQL DAO",
			description = "The database user's password.")
	public String sqlPassword() {
		return getConfigParam("sql.password", "secret");
	}

	/* **************************************************************************************************************
	 * Cassandra DAO                                                                                  Cassandra DAO *
	 ****************************************************************************************************************/

	/**
	 * Comma-separated Cassandra server hosts (contact points).
	 * @return the Cassandra hosts
	 */


	@Documented(position = 1430,
			identifier = "cassandra.hosts",
			value = "localhost",
			category = "Cassandra DAO",
			description = "Comma-separated Cassandra server hosts (contact points).")
	public String cassandraHosts() {
		return getConfigParam("cassandra.hosts", "localhost");
	}

	/**
	 * The name of the Cassandra keyspace to use.
	 * @return the Cassandra keyspace
	 */
	@Documented(position = 1440,
			identifier = "cassandra.keyspace",
			value = "para",
			category = "Cassandra DAO",
			description = "The name of the Cassandra keyspace to use.")
	public String cassandraKeyspace() {
		return getConfigParam("cassandra.keyspace", Config.PARA);
	}

	/**
	 * The Cassandra username with access to the database.
	 * @return the Cassandra user
	 */
	@Documented(position = 1450,
			identifier = "cassandra.user",
			category = "Cassandra DAO",
			description = "The Cassandra username with access to the database.")
	public String cassandraUser() {
		return getConfigParam("cassandra.user", "");
	}

	/**
	 * The password for the Cassandra user.
	 * @return the Cassandra password
	 */
	@Documented(position = 1460,
			identifier = "cassandra.password",
			category = "Cassandra DAO",
			description = "The password for the Cassandra user.")
	public String cassandraPassword() {
		return getConfigParam("cassandra.password", "");
	}

	/**
	 * The Cassandra server port to connect to.
	 * @return the Cassandra port
	 */
	@Documented(position = 1470,
			identifier = "cassandra.port",
			value = "9042",
			type = Integer.class,
			category = "Cassandra DAO",
			description = "The Cassandra server port to connect to.")
	public int cassandraPort() {
		return getConfigInt("cassandra.port", 9042);
	}

	/**
	 * Replication factor for the Cassandra keyspace.
	 * @return the Cassandra replication factor
	 */
	@Documented(position = 1480,
			identifier = "cassandra.replication_factor",
			value = "1",
			type = Integer.class,
			category = "Cassandra DAO",
			description = "Replication factor for the Cassandra keyspace.")
	public int cassandraReplicationFactor() {
		return getConfigInt("cassandra.replication_factor", 1);
	}

	/**
	 * Enable/disable the SSL/TLS transport in Cassandra client.
	 * @return true if Cassandra SSL is enabled
	 */
	@Documented(position = 1490,
			identifier = "cassandra.ssl_enabled",
			value = "false",
			type = Boolean.class,
			category = "Cassandra DAO",
			description = "Enable/disable the SSL/TLS transport in Cassandra client.")
	public boolean cassandraSslEnabled() {
		return getConfigBoolean("cassandra.ssl_enabled", false);
	}

	/**
	 * The protocols allowed for successful connection to Cassandra cluster.
	 * @return the Cassandra SSL protocols
	 */
	@Documented(position = 1500,
			identifier = "cassandra.ssl_protocols",
			value = "TLSv1.3",
			category = "Cassandra DAO",
			description = "The protocols allowed for successful connection to Cassandra cluster.")
	public String cassandraSslProtocols() {
		return getConfigParam("cassandra.ssl_protocols", "TLSv1.3");
	}

	/**
	 * Cassandra client key store, containing the certificates to use.
	 * @return the Cassandra SSL key store
	 */
	@Documented(position = 1510,
			identifier = "cassandra.ssl_keystore",
			category = "Cassandra DAO",
			description = "Cassandra client key store, containing the certificates to use.")
	public String cassandraSslKeystore() {
		return getConfigParam("cassandra.ssl_keystore", "");
	}

	/**
	 * Password for the Cassandra client key store.
	 * @return the Cassandra SSL key store password
	 */
	@Documented(position = 1520,
			identifier = "cassandra.ssl_keystore_password",
			category = "Cassandra DAO",
			description = "Password for the Cassandra client key store.")
	public String cassandraSslKeystorePassword() {
		return getConfigParam("cassandra.ssl_keystore_password", "");
	}

	/**
	 * Cassandra client trust store, containing trusted certificates and CAs.
	 * @return the Cassandra trust store
	 */
	@Documented(position = 1530,
			identifier = "cassandra.ssl_truststore",
			category = "Cassandra DAO",
			description = "Cassandra client trust store, containing trusted certificates and CAs.")
	public String cassandraTruststore() {
		return getConfigParam("cassandra.ssl_truststore", "");
	}

	/**
	 * Password for the Cassandra trust store.
	 * @return the Cassandra trust store password
	 */
	@Documented(position = 1540,
			identifier = "cassandra.ssl_truststore_password",
			category = "Cassandra DAO",
			description = "Password for the Cassandra trust store.")
	public String cassandraTruststorePassword() {
		return getConfigParam("cassandra.ssl_truststore_password", "");
	}

	/* **************************************************************************************************************
	 * AWS DynamoDB DAO                                                                            AWS DynamoDB DAO *
	 ****************************************************************************************************************/

	/**
	 * Enable/disable SSE (encryption-at-rest) using own KMS, instead of AWS-owned CMK
	 * for all newly created DynamoDB tables.
	 * @return true if encryption is enabled
	 */


	@Documented(position = 1550,
			identifier = "dynamodb.sse_enabled",
			value = "false",
			type = Boolean.class,
			category = "AWS DynamoDB DAO",
			description = "Enable/disable SSE (encryption-at-rest) using own KMS, instead of AWS-owned CMK "
					+ "for all newly created DynamoDB tables.")
	public boolean awsDynamoEncryptionEnabled() {
		return getConfigBoolean("dynamodb.sse_enabled", false);
	}

	/**
	 * Toggles global table settings for the specified regions.
	 * @return the replica regions
	 */
	@Documented(position = 1560,
			identifier = "dynamodb.replica_regions",
			category = "AWS DynamoDB DAO",
			description = "Toggles global table settings for the specified regions.")
	public String awsDynamoReplicaRegions() {
		return getConfigParam("dynamodb.replica_regions", "");
	}

	/**
	 * Enable/disable point-in-time backups in DynamoDB.
	 * @return true if DynamoDB backups are enabled
	 */
	@Documented(position = 1570,
			identifier = "dynamodb.backups_enabled",
			value = "false",
			type = Boolean.class,
			category = "AWS DynamoDB DAO",
			description = "Enable/disable point-in-time backups in DynamoDB.")
	public boolean awsDynamoBackupsEnabled() {
		return getConfigBoolean("dynamodb.backups_enabled", inProduction());
	}

	/**
	 * Enable/disable provisioned billing as an alternative to on-demand billing in DynamoDB.
	 * @return true if provisioned billing is enabled
	 */
	@Documented(position = 1580,
			identifier = "dynamodb.provisioned_mode_enabled",
			value = "false",
			type = Boolean.class,
			category = "AWS DynamoDB DAO",
			description = "Enable/disable provisioned billing as an alternative to on-demand billing in DynamoDB.")
	public boolean awsDynamoProvisionedBillingEnabled() {
		return getConfigBoolean("dynamodb.provisioned_mode_enabled", false);
	}

	/**
	 * The maximum read capacity when creating a table with provisioned mode enabled.
	 * @return the initial read capacity
	 */
	@Documented(position = 1590,
			identifier = "dynamodb.max_read_capacity",
			value = "10",
			type = Integer.class,
			category = "AWS DynamoDB DAO",
			description = "The maximum read capacity when creating a table with provisioned mode enabled.")
	public int awsDynamoMaxInitialReadCapacity() {
		return getConfigInt("dynamodb.max_read_capacity", 10);
	}

	/**
	 * The maximum write capacity when creating a table with provisioned mode enabled.
	 * @return the initial write capacity
	 */
	@Documented(position = 1600,
			identifier = "dynamodb.max_write_capacity",
			value = "",
			type = Integer.class,
			category = "AWS DynamoDB DAO",
			description = "The maximum write capacity when creating a table with provisioned mode enabled.")
	public int awsDynamoMaxInitialWriteCapacity() {
		return getConfigInt("dynamodb.max_write_capacity", 5);
	}

	/* **************************************************************************************************************
	 * Caffeine Cache                                                                                Caffeine Cache *
	 ****************************************************************************************************************/

	/**
	 * Cache eviction policy - objects are evicted from Caffeine cache after this time.
	 * @return the eviction timeout in minutes
	 */


	@Documented(position = 1610,
			identifier = "caffeine.evict_after_minutes",
			value = "10",
			type = Integer.class,
			category = "Caffeine Cache",
			description = "Cache eviction policy - objects are evicted from Caffeine cache after this time.")
	public int caffeineEvictAfterMin() {
		return getConfigInt("caffeine.evict_after_minutes", 10);
	}

	/**
	 * Maximum size for the Caffeine cache map.
	 * @return the maximum cache size
	 */
	@Documented(position = 1620,
			identifier = "caffeine.cache_size",
			value = "10000",
			type = Integer.class,
			category = "Caffeine Cache",
			description = "Maximum size for the Caffeine cache map.")
	public long caffeineCacheSize() {
		return getConfigInt("caffeine.cache_size", 10000);
	}

	/* **************************************************************************************************************
	 * Hazelcast Cache                                                                              Hazelcast Cache *
	 ****************************************************************************************************************/

	/**
	 * Enable/disable asynchronous operations in the Hazelcast client.
	 * @return true if Hazelcast async is enabled
	 */


	@Documented(position = 1630,
			identifier = "hc.async_enabled",
			value = "true",
			type = Boolean.class,
			category = "Hazelcast Cache",
			description = "Enable/disable asynchronous operations in the Hazelcast client.")
	public boolean hazelcastAsyncEnabled() {
		return getConfigBoolean("hc.async_enabled", false);
	}

	/**
	 * Time-to-live value (how long the objects stay cached) for cached objects, in seconds.
	 * @return the Hazelcast TTL in seconds
	 */
	@Documented(position = 1640,
			identifier = "hc.ttl_seconds",
			value = "3600",
			type = Integer.class,
			category = "Hazelcast Cache",
			description = "Time-to-live value (how long the objects stay cached) for cached objects, in seconds.")
	public int hazelcastTtlSec() {
		return getConfigInt("hc.ttl_seconds", 3600);
	}

	/**
	 * Enable/disable EC2 auto-discovery feature when deploying to AWS.
	 * @return true if EC2 discovery is enabled
	 */
	@Documented(position = 1650,
			identifier = "hc.ec2_discovery_enabled",
			value = "true",
			type = Boolean.class,
			category = "Hazelcast Cache",
			description = "Enable/disable EC2 auto-discovery feature when deploying to AWS.")
	public boolean hazelcastEc2DiscoveryEnabled() {
		return getConfigBoolean("hc.ec2_discovery_enabled", true);
	}

	/**
	 * The AWS access key to use if EC2 auto-discovery is enabled in Hazelcast.
	 * @return the AWS access key
	 */
	@Documented(position = 1660,
			identifier = "hc.aws_access_key",
			category = "Hazelcast Cache",
			description = "The AWS access key to use if EC2 auto-discovery is enabled in Hazelcast.")
	public String hazelcastAwsAccessKey() {
		return getConfigParam("hc.aws_access_key", System.getenv("AWS_ACCESS_KEY_ID"));
	}

	/**
	 * The AWS secret key to use if EC2 auto-discovery is enabled in Hazelcast.
	 * @return the AWS secret key
	 */
	@Documented(position = 1670,
			identifier = "hc.aws_secret_key",
			category = "Hazelcast Cache",
			description = "The AWS secret key to use if EC2 auto-discovery is enabled in Hazelcast.")
	public String hazelcastAwsSecretKey() {
		return getConfigParam("hc.aws_secret_key", System.getenv("AWS_SECRET_ACCESS_KEY"));
	}

	/**
	 * The AWS region to use if EC2 auto-discovery is enabled in Hazelcast.
	 * @return the AWS region
	 */
	@Documented(position = 1680,
			identifier = "hc.aws_region",
			category = "Hazelcast Cache",
			description = "The AWS region to use if EC2 auto-discovery is enabled in Hazelcast.")
	public String hazelcastAwsRegion() {
		return getConfigParam("hc.aws_region", System.getenv("AWS_REGION"));
	}

	/**
	 * EC2 security group for cloud discovery of Hazelcast nodes.
	 * @return the discovery group name
	 */
	@Documented(position = 1690,
			identifier = "hc.discovery_group",
			value = "hazelcast",
			category = "Hazelcast Cache",
			description = "EC2 security group for cloud discovery of Hazelcast nodes.")
	public String hazelcastEc2DiscoveryGroup() {
		return getConfigParam("hc.discovery_group", "hazelcast");
	}

	/**
	 * Maximum number of objects to keep in Hazelcast cache.
	 * @return the maximum cache size
	 */
	@Documented(position = 1700,
			identifier = "hc.max_size",
			value = "5000",
			type = Integer.class,
			category = "Hazelcast Cache",
			description = "Maximum number of objects to keep in Hazelcast cache.")
	public int hazelcastMaxCacheSize() {
		return getConfigInt("hc.max_size", 5000);
	}

	/**
	 * Hazelcast cache eviction policy - `LRU` or `LFU`.
	 * @return the eviction policy
	 */
	@Documented(position = 1710,
			identifier = "hc.eviction_policy",
			value = "LRU",
			category = "Hazelcast Cache",
			description = "Hazelcast cache eviction policy - `LRU` or `LFU`.")
	public String hazelcastEvictionPolicy() {
		return getConfigParam("hc.eviction_policy", "LRU");
	}

	/* **************************************************************************************************************
	 * Miscellaneous                                                                                  Miscellaneous *
	 ****************************************************************************************************************/

	/**
	 * Maximum results per page - limits the number of items to show in search results.
	 * @return the maximum items per page
	 */


	@Documented(position = 1720,
			identifier = "max_items_per_page",
			value = "30",
			type = Integer.class,
			category = "Miscellaneous",
			description = "Maximum results per page - limits the number of items to show in search results.")
	public int maxItemsPerPage() {
		return getConfigInt("max_items_per_page", 30);
	}

	/**
	 * Pagination limit - sets the highest page number possible.
	 * @return the maximum pages
	 */
	@Documented(position = 1730,
			identifier = "max_pages",
			value = "1000",
			type = Integer.class,
			category = "Miscellaneous",
			description = "Pagination limit - sets the highest page number possible.")
	public int maxPages() {
		return getConfigInt("max_pages", 1000);
	}

	/**
	 * Pagination limit - sets the maximum value for the `limit` request parameter, when it is used.
	 * @return the maximum page limit
	 */
	@Documented(position = 1740,
			identifier = "max_page_limit",
			value = "256",
			type = Integer.class,
			category = "Miscellaneous",
			description = "Pagination limit - sets the maximum value for the `limit` request parameter, when it is used.")
	public int maxPageLimit() {
		return getConfigInt("max_page_limit", 256);
	}

	/**
	 * Enable/disable the Para access log.
	 * @return true if access log is enabled
	 */
	@Documented(position = 1750,
			identifier = "access_log_enabled",
			value = "true",
			type = Boolean.class,
			category = "Miscellaneous",
			description = "Enable/disable the Para access log.")
	public boolean accessLogEnabled() {
		return getConfigBoolean("access_log_enabled", true);
	}

	/**
	 * The name of the shared database table, used by shared apps.
	 * @return the shared table name
	 */
	@Documented(position = 1760,
			identifier = "shared_table_name",
			value = "0",
			category = "Miscellaneous",
			description = "The name of the shared database table, used by shared apps.")
	public String sharedTableName() {
		return getConfigParam("shared_table_name", "0");
	}

	/**
	 * Enable/disable throwing an exception when a write operation fails with errors.
	 * @return true if exception on write errors is enabled
	 */
	@Documented(position = 1770,
			identifier = "fail_on_write_errors",
			value = "true",
			type = Boolean.class,
			category = "Miscellaneous",
			description = "Enable/disable throwing an exception when a write operation fails with errors.")
	public boolean exceptionOnWriteErrorsEnabled() {
		return getConfigBoolean("fail_on_write_errors", true);
	}

	/**
	 * The maximum number of objects to import, in each batch, when restoring data from backup.
	 * @return the import batch size
	 */
	@Documented(position = 1780,
			identifier = "import_batch_size",
			value = "100",
			type = Integer.class,
			category = "Miscellaneous",
			description = "The maximum number of objects to import, in each batch, when restoring data from backup.")
	public int importBatchSize() {
		return getConfigInt("import_batch_size", 100);
	}

	/**
	 * Enable/disable the GZIP filter for compressing API response entities.
	 * @return true if GZIP is enabled
	 */
	@Documented(position = 1790,
			identifier = "gzip_enabled",
			value = "false",
			type = Boolean.class,
			category = "Miscellaneous",
			description = "Enable/disable the GZIP filter for compressing API response entities.")
	public boolean gzipEnabled() {
		return Boolean.parseBoolean(getConfigParam("gzip_enabled", "true"));
	}

	/**
	 * Enable/disable debuging info for each AWS V4 request signature.
	 * @return true if debuging of request signatures is enabled
	 */
	@Documented(position = 1800,
			identifier = "debug_request_signatures",
			value = "false",
			type = Boolean.class,
			category = "Miscellaneous",
			description = "Enable/disable debuging info for each AWS V4 request signature.")
	public boolean debugRequestSignaturesEnabled() {
		return getConfigBoolean("debug_request_signatures", false);
	}

	/**
	 * Vote expiration timeout, in seconds. Users can vote again on the same content after
	 * this period has elapsed. Default is 30 days.
	 * @return the vote expiration timeout
	 */
	@Documented(position = 1810,
			identifier = "vote_expires_after",
			value = "2592000",
			type = Integer.class,
			category = "Miscellaneous",
			description = "Vote expiration timeout, in seconds. Users can vote again on the same content after "
					+ "this period has elapsed. Default is 30 days.")
	public int voteExpiresAfterSec() {
		return NumberUtils.toInt(getConfigParam("vote_expires_after", ""), 30 * 24 * 60 * 60);
	}

	/**
	 * Vote locking period, in seconds. Vote cannot be changed after this period has elapsed.
	 * Default is 30 sec.
	 * @return the vote locking period
	 */
	@Documented(position = 1820,
			identifier = "vote_locked_after",
			value = "30",
			type = Integer.class,
			category = "Miscellaneous",
			description = "Vote locking period, in seconds. Vote cannot be changed after this period has elapsed. "
					+ "Default is 30 sec.")
	public int voteLockedAfterSec() {
		return NumberUtils.toInt(getConfigParam("vote_locked_after", ""), 30);
	}

	/**
	 * The folder from which Para will load its JAR plugin files.
	 * @return the plugin folder path
	 */
	@Documented(position = 1830,
			identifier = "plugin_folder",
			value = "lib/",
			category = "Miscellaneous",
			description = "The folder from which Para will load its JAR plugin files.")
	public String pluginFolder() {
		return getConfigParam("plugin_folder", "lib/");
	}

	/**
	 * **For internal use only!** Prepends `appid` fields with a space for all shared apps.
	 * @return true if appid space prefix is enabled
	 */
	@Documented(position = 1840,
			identifier = "prepend_shared_appids_with_space",
			value = "false",
			type = Boolean.class,
			category = "Miscellaneous",
			description = "**For internal use only!** Prepends `appid` fields with a space for all shared apps.")
	public boolean appIdSpacePrefixEnabled() {
		return getConfigBoolean("prepend_shared_appids_with_space", false);
	}

	/**
	 * Enable/disable version number printing in Para logs.
	 * @return true if version banner is enabled
	 */
	@Documented(position = 1850,
			identifier = "print_version",
			value = "true",
			type = Boolean.class,
			category = "Miscellaneous",
			description = "Enable/disable version number printing in Para logs.")
	public boolean versionBannerEnabled() {
		return getConfigBoolean("print_version", true);
	}

	/**
	 * Enable/disable printing the Para ASCII logo on startup.
	 * @return true if logo banner is enabled
	 */
	@Documented(position = 1860,
			identifier = "print_logo",
			value = "true",
			type = Boolean.class,
			category = "Miscellaneous",
			description = "Enable/disable printing the Para ASCII logo on startup.")
	public boolean logoBannerEnabled() {
		return getConfigBoolean("print_logo", true);
	}

	/**
	 * Sets the Markdown soft break character.
	 * @return the Markdown soft break character
	 */
	@Documented(position = 1870,
			identifier = "markdown_soft_break",
			value = "<br>",
			category = "Miscellaneous",
			description = "Sets the Markdown soft break character.")
	public String markdownSoftBreak() {
		return getConfigParam("markdown_soft_break", "<br>");
	}

	/**
	 * A whitelist of domains, links to which will be allowed to be followed by web
	 * crawlers (comma-separated list).
	 * @return the allowed domains for crawlers
	 */
	@Documented(position = 1871,
			identifier = "markdown_allowed_follow_domains",
			category = "Miscellaneous",
			description = "A whitelist of domains, links to which will be allowed to be followed by web "
					+ "crawlers (comma-separated list).")
	public String[] markdownAllowFollowDomains() {
		return StringUtils.split(getConfigParam("markdown_allowed_follow_domains", ""), ',');
	}

	/**
	 * AWS region to use in the `AWSEmailer` implementation.
	 * @return the SES region
	 */
	@Documented(position = 1880,
			identifier = "aws_ses_region",
			value = "eu-west-1",
			category = "Miscellaneous",
			description = "AWS region to use in the `AWSEmailer` implementation.")
	public String awsSesRegion() {
		return getConfigParam("aws_ses_region", "eu-west-1");
	}

	/**
	 * Enable/disable PID file generation on startup.
	 * @return true if PID file generation is enabled
	 */
	@Documented(position = 1890,
			identifier = "pidfile_enabled",
			value = "true",
			type = Boolean.class,
			category = "Miscellaneous",
			description = "Enable/disable PID file generation on startup.")
	public boolean pidFileEnabled() {
		return getConfigBoolean("pidfile_enabled", true);
	}

	/**
	 * String separator - default is colon `:`.
	 * @return the string separator
	 */
	@Documented(position = 1900,
			identifier = "default_separator",
			value = ":",
			category = "Miscellaneous",
			description = "String separator - default is colon `:`.")
	public String separator() {
		return getConfigParam("default_separator", ":");
	}

	/**
	 * Default character encoding - `UTF-8`.
	 * @return the default encoding
	 */
	@Documented(position = 1910,
			identifier = "default_encoding",
			value = "UTF-8",
			category = "Miscellaneous",
			description = "Default character encoding - `UTF-8`.")
	public String defaultEncoding() {
		return getConfigParam("default_encoding", "UTF-8");
	}

	/**
	 * Enable/disable the landing page when opening the server address in the browser.
	 * @return true if landing page is enabled
	 */
	@Documented(position = 1920,
			identifier = "landing_page_enabled",
			value = "true",
			type = Boolean.class,
			category = "Miscellaneous",
			description = "Enable/disable the landing page when opening the server address in the browser.")
	public boolean landingPageEnabled() {
		return getConfigBoolean("landing_page_enabled", true);
	}

	/**
	 * Enable/disable the MCP server. Possible values: `off`, `r` - read-only, `rw` - full access.
	 * @return the MCP server mode
	 */
	@Documented(position = 1930,
			identifier = "mcp_server_mode",
			value = "off",
			category = "Miscellaneous",
			description = "Enable/disable the MCP server. Possible values: `off`, `r` - read-only, `rw` - full access.")
	public String mcpServerMode() {
		return getConfigParam("mcp_server_mode", "off");
	}

	/**
	 * Returns the list of regions where a table should be replicated (global table).
	 * Currently, this is only used in combination with DynamoDB and SQS but could be implemented with other services.
	 * @return a list of regions
	 */
	public List<String> replicaRegions() {
		if (!StringUtils.isBlank(awsDynamoReplicaRegions())) {
			String[] regions = Para.getConfig().awsDynamoReplicaRegions().split("\\s*,\\s*");
			if (regions != null && regions.length > 0) {
				return Streams.of(regions).filter(StringUtils::isNotBlank).toList();
			}
		}
		return Collections.emptyList();
	}

	/**
	 * Returns true if the environment is "development".
	 * @return development environment flag
	 */
	public boolean inDevelopment() {
		return environment().equals("development");
	}

	/**
	 * Returns true if the environment is "production".
	 * @return production environment flag
	 */
	public boolean inProduction() {
		return environment().equals("production");
	}

	/**
	 * Returns the name of the root Para app, without any spaces.
	 * @return the root app identifier
	 */
	public String getRootAppIdentifier() {
		return App.identifier(App.id(appName()));
	}

}
