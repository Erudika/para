/*
 * Copyright 2013-2022 Erudika. http://erudika.com
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
import com.typesafe.config.ConfigObject;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * Para configuration.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ParaConfig extends Config {

	@Override
	public String getConfigRootPrefix() {
		return PARA;
	}

	/**
	 * @return String separator - default is colon ':'.
	 */
	public String separator() {
		return getConfigParam("default_separator", ":");
	}

	/**
	 * @return Maximum results per page - limits the number of items to show in search results. Default is 30.
	 */
	public int maxItemsPerPage() {
		return getConfigInt("max_items_per_page", 30);
	}

	/**
	 * @return Pagination limit - highest page number, default is 1000.
	 */
	public int maxPages() {
		return getConfigInt("max_pages", 1000);
	}

	/**
	 * @return Pagination limit - maximum number of results per page, default is 256.
	 */
	public int maxPageLimit() {
		return getConfigInt("max_page_limit", 256);
	}

	/**
	 * @return Minimum password length - default is 8 symbols.
	 */
	public int minPasswordLength() {
		return getConfigInt("min_password_length", 8);
	}

	/**
	 * @return Maximum number of data types that can be defined per app - default is 256.
	 */
	public int maxDatatypesPerApp() {
		return getConfigInt("max_datatypes_per_app", 256);
	}

	/**
	 * @return Maximum size of incoming JSON objects - default is 1048576 (bytes).
	 */
	public int maxEntitySizeBytes() {
		return getConfigInt("max_entity_size_bytes", 1024 * 1024);
	}

	/**
	 * @return Default character encoding - 'UTF-8'.
	 */
	public String defaultEncoding() {
		return getConfigParam("default_encoding", "UTF-8");
	}

	/**
	 * @return For example: production, development, testing... etc. Default: "embedded"
	 */
	public String environment() {
		return getConfigParam("env", "embedded");
	}

	/**
	 * @return Facebook app id (for authentication).
	 */
	public String facebookAppId() {
		return getConfigParam("fb_app_id", "");
	}

	/**
	 * @return Facebook app secret (for authentication).
	 */
	public String facebookSecret() {
		return getConfigParam("fb_secret", "");
	}

	/**
	 * @return Google app id (for authentication).
	 */
	public String googleAppId() {
		return getConfigParam("gp_app_id", "");
	}

	/**
	 * @return Google+ app secret (for authentication).
	 */
	public String googleSecret() {
		return getConfigParam("gp_secret", "");
	}

	/**
	 * @return LinkedIn app id (for authentication).
	 */
	public String linkedinAppId() {
		return getConfigParam("in_app_id", "");
	}

	/**
	 * @return LinkedIn app secret (for authentication).
	 */
	public String linkedinSecret() {
		return getConfigParam("in_secret", "");
	}

	/**
	 * @return Twitter app id (for authentication).
	 */
	public String twitetAppId() {
		return getConfigParam("tw_app_id", "");
	}

	/**
	 * @return Twitter app secret (for authentication).
	 */
	public String twitterSecret() {
		return getConfigParam("tw_secret", "");
	}

	/**
	 * @return GitHub app id (for authentication).
	 */
	public String githubAppId() {
		return getConfigParam("gh_app_id", "");
	}

	/**
	 * @return GitHub app secret (for authentication).
	 */
	public String githubSecret() {
		return getConfigParam("gh_secret", "");
	}

	/**
	 * @return Microsoft app id (for authentication).
	 */
	public String microsoftAppId() {
		return getConfigParam("ms_app_id", "");
	}

	/**
	 * @return Microsoft app secret (for authentication).
	 */
	public String microsoftSecret() {
		return getConfigParam("ms_secret", "");
	}

	/**
	 * @return Slack app id (for authentication).
	 */
	public String slackAppId() {
		return getConfigParam("sl_app_id", "");
	}

	/**
	 * @return Slack app secret (for authentication).
	 */
	public String slackSecret() {
		return getConfigParam("sl_secret", "");
	}

	/**
	 * @return Mattermost app id (for authentication).
	 */
	public String mattermostAppId() {
		return getConfigParam("mm_app_id", "");
	}

	/**
	 * @return Mattermost app secret (for authentication).
	 */
	public String mattermostSecret() {
		return getConfigParam("mm_secret", "");
	}

	/**
	 * @return Amazon app id (for authentication).
	 */
	public String amazonAppId() {
		return getConfigParam("az_app_id", "");
	}

	/**
	 * @return Amazon app secret (for authentication).
	 */
	public String amazonSecret() {
		return getConfigParam("az_secret", "");
	}

	/**
	 * @return The identifier of the first administrator (can be email, OpenID, or Facebook user id).
	 */
	public String adminIdentifier() {
		return getConfigParam("admin_ident", "");
	}

	/**
	 * @return The id of this deployment. In a multi-node environment each node should have a unique id.
	 */
	public String workerId() {
		return getConfigParam("worker_id", "1");
	}

	/**
	 * @return The number of threads to use for the ExecutorService thread pool. Default is 2.
	 */
	public int executorThreads() {
		return getConfigInt("executor_threads", 2);
	}

	/**
	 * @return The name of the default application.
	 */
	public String appName() {
		return getConfigParam("app_name", PARA);
	}

	/**
	 * @return The name of the "return to" cookie.
	 */
	public String returnToCookieName() {
		return getConfigParam("returnto_cookie", PARA.concat("-returnto"));
	}

	/**
	 * @return The email address for support.
	 */
	public String supportEmail() {
		return getConfigParam("support_email", "support@myapp.co");
	}

	/**
	 * @return The secret key for this deployment. Used as salt.
	 */
	public String appSecretKey() {
		return getConfigParam("app_secret_key", Utils.md5("paraseckey"));
	}

	/**
	 * @return The default queue name which will be polled for incoming JSON messages.
	 */
	public String defaultQueueName() {
		return getConfigParam("default_queue_name", PARA + "-default");
	}

	/**
	 * @return The package path (e.g. org.company.app.core) where all domain objects are defined.
	 */
	public String corePackageName() {
		return getConfigParam("core_package_name", "");
	}

	/**
	 * @return Expiration of signed API request, in seconds. Default: 15 minutes
	 */
	public int requestExpiresAfterSec() {
		return NumberUtils.toInt(getConfigParam("request_expires_after", ""), 15 * 60);
	}

	/**
	 * @return JWT (access token) expiration in seconds. Default: 24 hours.
	 */
	public int jwtExpiresAfterSec() {
		return NumberUtils.toInt(getConfigParam("jwt_expires_after", ""), 24 * 60 * 60);
	}

	/**
	 * @return JWT refresh interval - tokens will be auto-refreshed at this interval of time. Default: 1 hour
	 */
	public int jwtRefreshIntervalSec() {
		return NumberUtils.toInt(getConfigParam("jwt_refresh_interval", ""), 60 * 60);
	}

	/**
	 * @return ID token expiration in seconds. Default: 60 seconds
	 */
	public int idTokenExpiresAfterSec() {
		return NumberUtils.toInt(getConfigParam("id_token_expires_after", ""), 60);
	}

	/**
	 * @return Session timeout in seconds. Default: 24 hours
	 */
	public int sessionTimeoutSec() {
		return NumberUtils.toInt(getConfigParam("session_timeout", ""), 24 * 60 * 60);
	}

	/**
	 * @return Votes expire after X seconds. Default: 30 days
	 */
	public int voteExpiresAfterSec() {
		return NumberUtils.toInt(getConfigParam("vote_expires_after", ""), 30 * 24 * 60 * 60);
	}

	/**
	 * @return A vote can be changed within X seconds of casting. Default: 30 seconds
	 */
	public int voteLockedAfterSec() {
		return NumberUtils.toInt(getConfigParam("vote_locked_after", ""), 30);
	}

	/**
	 * @return Password reset window in seconds. Default: 30 minutes
	 */
	public int passwordResetTimeoutSec() {
		return NumberUtils.toInt(getConfigParam("pass_reset_timeout", ""), 30 * 60);
	}

	/**
	 * @return Enable the RESTful API. Default: true
	 */
	public boolean apiEnabled() {
		return Boolean.parseBoolean(getConfigParam("api_enabled", "true"));
	}

	/**
	 * @return Enable the CORS filter for API requests. Default: true
	 */
	public boolean corsEnabled() {
		return Boolean.parseBoolean(getConfigParam("cors_enabled", "true"));
	}

	/**
	 * @return Enable the GZIP filter for API requests. Default: false
	 */
	public boolean gzipEnabled() {
		return Boolean.parseBoolean(getConfigParam("gzip_enabled", "false"));
	}

	/**
	 * @return Enable webhooks for CRUD methods. Requires a queue. Default: false
	 */
	public boolean webhooksEnabled() {
		return Boolean.parseBoolean(getConfigParam("webhooks_enabled", "false"));
	}

	/**
	 * @return Production environment flag.
	 */
	public boolean inProduction() {
		return environment().equals("production");
	}

	/**
	 * @return Development environment flag.
	 */
	public boolean inDevelopment() {
		return environment().equals("development");
	}

	/**
	 * @return The name of the cluster (can be used to separate deployments).
	 */
	public String clusterName() {
		return getConfigParam("cluster_name", inProduction() ? PARA + "-prod" : PARA + "-dev");
	}

	/**
	 * Default: true only if {@link #environment() } = "production".
	 *
	 * @return true if caching is enabled
	 */
	public boolean isCacheEnabled() {
		return getConfigBoolean("cache_enabled", environment().equals("production"));
	}

	/**
	 * Default: true.
	 *
	 * @return true if indexing is enabled
	 */
	public boolean isSearchEnabled() {
		return getConfigBoolean("search_enabled", true);
	}

	/**
	 * @return The name of the root Para app, without any spaces.
	 */
	public String getRootAppIdentifier() {
		return App.identifier(App.id(appName()));
	}

	public boolean appIdSpacePrefixEnabled() {
		return getConfigBoolean("prepend_shared_appids_with_space", false);
	}

	public boolean versionBannerEnabled() {
		return getConfigBoolean("print_version", true);
	}

	public String pluginFolder() {
		return getConfigParam("plugin_folder", "lib/");
	}

	public boolean logoBannerEnabled() {
		return getConfigBoolean("print_logo", true);
	}

	public String markdownSoftBreak() {
		return getConfigParam("markdown_soft_break", "<br>");
	}

	public boolean queuePollingEnabled() {
		return getConfigBoolean("queue_link_enabled", false);
	}

	public boolean accessLogEnabled() {
		return getConfigBoolean("access_log_enabled", true);
	}

	@Documented(position = 110,
			identifier = "context_path",
			category = "Core",
			tags = {"requires restart"},
			description = "The context path (subpath) of the web application, defaults to the root path `/`.")
	public String serverContextPath() {
		String context = getConfigParam("context_path", "");
		return StringUtils.stripEnd((StringUtils.isBlank(context)
				? System.getProperty("server.servlet.context-path", "") : context), "/");
	}

	@Documented(position = 60,
			identifier = "port",
			value = "8080",
			type = Integer.class,
			category = "Core",
			tags = {"requires restart"},
			description = "The network port of this Para server. Port number should be a number above `1024`.")
	public int serverPort() {
		return NumberUtils.toInt(System.getProperty("server.port"), getConfigInt("port", 8080));
	}

	public boolean pidFileEnabled() {
		return getConfigBoolean("pidfile_enabled", true);
	}

	public String cachePlugin() {
		return getConfigParam("cache", "");
	}

	public int caffeineEvictAfterMin() {
		return getConfigInt("caffeine.evict_after_minutes", 10);
	}

	public long caffeineCacheSize() {
		return getConfigInt("caffeine.cache_size", 10000);
	}

	public String awsSesRegion() {
		return getConfigParam("aws_ses_region", "eu-west-1");
	}

	public String emailerPlugin() {
		return getConfigParam("emailer", "");
	}

	public boolean metricsEnabled() {
		return getConfigBoolean("metrics_enabled", true);
	}

	public int metricsLoggingIntervalSec() {
		return getConfigInt("metrics.logging_rate", 60);
	}

	public String metricsGraphiteHost() {
		return getConfigParam("metrics.graphite.host", null);
	}

	public int metricsGraphitePort() {
		return getConfigInt("metrics.graphite.port", 2003);
	}

	public String metricsGraphitePrefixSystem() {
		return getConfigParam("metrics.graphite.prefix_system", null);
	}

	public String metricsGraphitePrefixApps() {
		return getConfigParam("metrics.graphite.prefix_apps", null);
	}

	public int metricsGraphitePeriodSec() {
		return getConfigInt("metrics.graphite.period", 0);
	}

	public boolean metricsJmxEnabled() {
		return getConfigBoolean("metrics.jmx_enabled", false);
	}

	/**
	 * The name of the shared table. Default is {@code 0}.
	 * @return asd
	 */
	public String sharedTableName() {
		return getConfigParam("shared_table_name", "0");
	}

	/**
	 * Toggles SSE (encryption-at-rest) using own KMS, instead of AWS-owned CMK for all newly created DynamoDB tables.
	 * Default is {@code false}.
	 * @return asd
	 */
	public boolean awsDynamoEncryptionEnabled() {
		return getConfigBoolean("dynamodb.sse_enabled", false);
	}

	/**
	 * Toggles global tables settings for the specified regions.
	 * @return asd
	 */
	public String awsDynamoReplicaRegions() {
		return getConfigParam("dynamodb.replica_regions", "");
	}

	/**
	 * Toggles point-in-time backups. Default is {@code true}.
	 * @return asd
	 */
	public boolean awsDynamoBackupsEnabled() {
		return getConfigBoolean("dynamodb.backups_enabled", inProduction());
	}

	/**
	 * Toggles between provisioned billing and on-demand billing.
	 * @return asd
	 */
	public boolean awsDynamoProvisionedBillingEnabled() {
		return getConfigBoolean("dynamodb.provisioned_mode_enabled", true);
	}

	public int awsDynamoMaxInitialReadCapacity() {
		return getConfigInt("dynamodb.max_read_capacity", 10);
	}

	public int awsDynamoMaxInitialWriteCapacity() {
		return getConfigInt("dynamodb.max_write_capacity", 5);
	}

	public boolean exceptionOnWriteErrorsEnabled() {
		return getConfigBoolean("fail_on_write_errors", true);
	}

	public String daoPlugin() {
		return getConfigParam("dao", "");
	}

	public boolean awsSqsLocalQueueEnabled() {
		return getConfigBoolean("aws_sqs_local", false);
	}

	public int queuePollingWaitSec() {
		return getConfigInt("queue.polling_sleep_seconds", 60);
	}

	/**
	 * The polling interval in seconds for this river. Polls queue ever X seconds.
	 * @return asd
	 */
	public int queuePollingIntervalSec() {
		return getConfigInt("queue.polling_interval_seconds", inProduction() ? 20 : 5);
	}

	public String queuePlugin() {
		return getConfigParam("q", "");
	}

	public int maxFailedWebhookAttempts() {
		return getConfigInt("max_failed_webhook_attempts", 10);
	}

	public int riverMaxIndexingRetries() {
		return getConfigInt("river.max_indexing_retries", 5);
	}

	public int importBatchSize() {
		return getConfigInt("import_batch_size", 100);
	}

	public String searchPlugin() {
		return getConfigParam("search", "");
	}

	public boolean csrfProtectionEnabled() {
		return getConfigBoolean("security.csrf_protection", true);
	}

	public String csrfCookieName() {
		return getConfigParam("security.csrf_cookie", "para-csrf-token");
	}

	public String authCookieName() {
		return getConfigParam("auth_cookie", PARA.concat("-auth"));
	}

	public String anonymousCsrfCookieName() {
		return csrfCookieName() + "-anonid";
	}

	public ConfigObject protectedPaths() {
		return getConfig().getObject("security.protected");
	}

	public String signinPath() {
		return getConfigParam("security.signin", "/signin");
	}

	public String signinSuccessPath() {
		return getConfigParam("security.signin_success", "/");
	}

	public String signinFailurePath() {
		return getConfigParam("security.signin_failure", "/signin?error");
	}

	public String signoutPath() {
		return getConfigParam("security.signout", "/signout");
	}

	public String signoutSuccessPath() {
		return getConfigParam("security.signout_success", signinPath());
	}

	public String accessDeniedPath() {
		return getConfigParam("security.access_denied", "/403");
	}

	public String returnToPath() {
		return getConfigParam("security.returnto", "returnto");
	}

	public boolean rememberMeEnabled() {
		return getConfigBoolean("security.remember_me", true);
	}

	public boolean debugRequestSignaturesEnabled() {
		return getConfigBoolean("debug_request_signatures", false);
	}

	@Documented(position = 1000,
			identifier = "security.ldap.password_param",
			value = "password",
			category = "LDAP Authentication",
			description = "LDAP password parameter name.")
	public String ldapPasswordParameter() {
		return getConfigParam("security.ldap.password_param", "password");
	}

	@Documented(position = 1010,
			identifier = "security.ldap.username_param",
			value = "username",
			category = "LDAP Authentication",
			description = "LDAP username parameter name.")
	public String ldapUsernameParameter() {
		return getConfigParam("security.ldap.username_param", "username");
	}

	@Documented(position = 410,
			identifier = "security.allow_unverified_emails",
			value = "false",
			type = Boolean.class,
			category = "Security",
			description = "Enable/disable email verification after the initial user registration. Users with unverified "
					+ "emails won't be able to sign in, unless they use a social login provider.")
	public boolean allowUnverifiedEmails() {
		return getConfigBoolean("security.allow_unverified_emails", false);
	}

	public String awsS3Bucket(String region) {
		return getConfigParam("para.s3.bucket", "org.paraio." +	region);
	}

	public int awsS3MaxFileSizeMb() {
		return getConfigInt("para.s3.max_filesize_mb", 10);
	}

	public String localFileStoreFolder() {
		return getConfigParam("para.localstorage.folder", "");
	}

	public int localFileStoreMaxFileSizeMb() {
		return getConfigInt("para.localstorage.max_filesize_mb", 10);
	}

	public String fileStoragePlugin() {
		return getConfigParam("fs", "");
	}

	public int healthCheckInvervalSec() {
		return getConfigInt("health.check_interval", 60);
	}

	public boolean healthCheckEnabled() {
		return getConfigBoolean("health_check_enabled", true);
	}

	public String clientSslProtocols() {
		return getConfigParam("client.ssl_protocols", "TLSv1.3");
	}

	public String clientSslKeystore() {
		return getConfigParam("client.ssl_keystore", "");
	}

	public String clientSslKeystorePassword() {
		return getConfigParam("client.ssl_keystore_password", "");
	}

	public String clientSslTruststore() {
		return getConfigParam("client.ssl_truststore", "");
	}

	public String clientSslTruststorePassword() {
		return getConfigParam("client.ssl_truststore_password", "");
	}

	public boolean clientUserAgentEnabled() {
		return getConfigBoolean("user_agent_id_enabled", true);
	}

	public int globalSyncIntervalSec() {
		return getConfigInt("indexing_sync_interval_sec", 10);
	}

	public String cassandraHosts() {
		return getConfigParam("cassandra.hosts", "localhost");
	}

	public String cassandraKeyspace() {
		return getConfigParam("cassandra.keyspace", Config.PARA);
	}

	public String cassandraUser() {
		return getConfigParam("cassandra.user", "");
	}

	public int cassandraPort() {
		return getConfigInt("cassandra.port", 9042);
	}

	public String cassandraPassword() {
		return getConfigParam("cassandra.password", "");
	}

	public int cassandraReplicationFactor() {
		return getConfigInt("cassandra.replication_factor", 1);
	}

	public boolean cassandraSslEnabled() {
		return getConfigBoolean("cassandra.ssl_enabled", false);
	}

	public String cassandraSslProtocols() {
		return getConfigParam("cassandra.ssl_protocols", "TLSv1.3");
	}

	public String cassandraSslKeystore() {
		return getConfigParam("cassandra.ssl_keystore", "");
	}

	public String cassandraSslKeystorePassword() {
		return getConfigParam("cassandra.ssl_keystore_password", "");
	}

	public String cassandraTruststore() {
		return getConfigParam("cassandra.ssl_truststore", "");
	}

	public String cassandraTruststorePassword() {
		return getConfigParam("cassandra.ssl_truststore_password", "");
	}

	public String h2Host() {
		return getConfigParam("db.hostname", "");
	}

	public String h2DataFolder() {
		return getConfigParam("db.dir", "./data");
	}

	public String h2User() {
		return getConfigParam("db.user", getRootAppIdentifier());
	}

	public String h2Password() {
		return getConfigParam("db.password", "secret");
	}

	public String h2ServerParameters() {
		String tcpPrefix = StringUtils.isBlank(h2Host()) ? "" : "tcp://" + h2Host() + "/";
		return getConfigParam("db.tcpServer", tcpPrefix.isEmpty() ? "-baseDir " + h2DataFolder() : "");
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

		/**
	 * Return the OAuth app ID and secret key for a given app by reading the app settings, or the config file.
	 * @param app the app in which to look for these keys
	 * @param prefix a service prefix: "fb" for facebook, "tw" for twitter etc. See {@link Config}
	 * @return an array ["app_id", "secret_key"] or ["", ""]
	 */
	public String[] getOAuthKeysForApp(App app, String prefix) {
		prefix = StringUtils.removeEnd(prefix + "", separator());
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
			ldapSettings.put("security.ldap.base_dn", "dc=springframework,dc=org");
			ldapSettings.put("security.ldap.bind_dn", "");
			ldapSettings.put("security.ldap.bind_pass", "");
			ldapSettings.put("security.ldap.user_search_base", "");
			ldapSettings.put("security.ldap.user_search_filter", "(cn={0})");
			ldapSettings.put("security.ldap.user_dn_pattern", "uid={0}");
			ldapSettings.put("security.ldap.password_attribute", "userPassword");
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

	public String sqlHostUrl() {
		return getConfigParam("sql.url", null);
	}

	public String sqlDriver() {
		return getConfigParam("sql.driver", null);
	}

	public String sqlUser() {
		return getConfigParam("sql.user", "user");
	}

	public String sqlPassword() {
		return getConfigParam("sql.password", "secret");
	}

	public int reindexBatchSize(int max) {
		return getConfigInt("reindex_batch_size", max);
	}

	public boolean syncIndexWithDatabaseEnabled() {
		return getConfigBoolean("sync_index_with_db", true);
	}

	public boolean readFromIndexEnabled() {
		return getConfigBoolean("read_from_index", false);
	}

	public String luceneDataFolder() {
		return getConfigParam("lucene.dir", Paths.get(".").toAbsolutePath().normalize().toString());
	}

	public String elasticsearchFlavor() {
		return getConfigParam("es.flavor", "elasticsearch");
	}

	public int elasticsearchRootIndexShards() {
		return getConfigInt("es.shards", 2);
	}

	public int elasticsearchChildIndexShards() {
		return getConfigInt("es.shards_for_child_apps", 1);
	}

	public int elasticsearchRootIndexReplicas() {
		return getConfigInt("es.replicas", 0);
	}

	public int elasticsearchChildIndexReplicas() {
		return getConfigInt("es.replicas_for_child_apps", 0);
	}

	/**
	 * Switches between normal indexing and indexing with nested key/value objects for Sysprop.properties.
	 * When this is 'false' (normal mode), Para objects will be indexed without modification but this could lead to
	 * a field mapping explosion and crash the ES cluster.
	 *
	 * When set to 'true' (nested mode), Para objects will be indexed with all custom fields flattened to an array of
	 * key/value properties: properties: [{"k": "field", "v": "value"},...]. This is done for Sysprop objects with
	 * containing custom properties. This mode prevents an eventual field mapping explosion.
	 * @return asd
	 */
	public boolean elasticsearchNestedModeEnabled() {
		return getConfigBoolean("es.use_nested_custom_fields", false);
	}

	public boolean elasticsearchAsyncModeEnabled() {
		return getConfigBoolean("es.async_enabled", false);
	}

	public boolean elasticsearchBulkFlushEnabled() {
		return getConfigBoolean("es.bulk.flush_immediately", true);
	}

	public String elasticsearchRestClientScheme() {
		return getConfigParam("es.restclient_scheme", inProduction() ? "https" : "http");
	}

	public String elasticsearchRestClientHost() {
		return getConfigParam("es.restclient_host", "localhost");
	}

	public int elasticsearchRestClientPort() {
		return getConfigInt("es.restclient_port", 9200);
	}

	public boolean elasticsearchSignRequestsForAwsEnabled() {
		return getConfigBoolean("es.sign_requests_to_aws", elasticsearchRestClientHost().contains("amazonaws.com"));
	}

	public String elasticsearchRestClientContextPath() {
		return getConfigParam("es.restclient_context_path", "");
	}

	public String elasticsearchAutoExpandReplicas() {
		return getConfigParam("es.auto_expand_replicas", "0-1");
	}

	public boolean elasticsearchRootIndexSharingEnabled() {
		return getConfigBoolean("es.root_index_sharing_enabled", false);
	}

	public String elasticsearchTrackTotalHits() {
		return getConfigParam("es.track_total_hits", "true");
	}

	public String elasticsearchAwsRegion() {
		return getConfigParam("es.aws_region", "eu-west-1");
	}

	public String elasticsearchAuthUser() {
		return getConfigParam("es.basic_auth_login", "");
	}

	public String elasticsearchAuthPassword() {
		return getConfigParam("es.basic_auth_password", "");
	}

	public int elasticsearchBulkSizeLimitMb() {
		return getConfigInt("es.bulk.size_limit_mb", 5);
	}

	public int elasticsearchBulkActionLimit() {
		return getConfigInt("es.bulk.action_limit", 1000);
	}

	public int elasticsearchBulkConcurrentRequests() {
		return getConfigInt("es.bulk.concurrent_requests", 1);
	}

	public int elasticsearchBulkFlushIntervalSec() {
		return getConfigInt("es.bulk.flush_interval_ms", 5000);
	}

	public int elasticsearchBulkBackoffDelayMs() {
		return getConfigInt("es.bulk.backoff_initial_delay_ms", 50);
	}

	public int elasticsearchBulkBackoffRetries() {
		return getConfigInt("es.bulk.max_num_retries", 8);
	}

	public String elasticsearchProxyPath() {
		return getConfigParam("es.proxy_path", "_elasticsearch");
	}

	public boolean elasticsearchProxyEnabled() {
		return getConfigBoolean("es.proxy_enabled", false);
	}

	public boolean elasticsearchProxyReindexingEnabled() {
		return getConfigBoolean("es.proxy_reindexing_enabled", false);
	}

	public boolean hazelcastAsyncEnabled() {
		return getConfigBoolean("hc.async_enabled", false);
	}

	public int hazelcastTtlSec() {
		return getConfigInt("hc.ttl_seconds", 3600);
	}

	public boolean hazelcastEc2DiscoveryEnabled() {
		return getConfigBoolean("hc.ec2_discovery_enabled", true);
	}

	public String hazelcastAwsAccessKey() {
		return getConfigParam("aws_access_key", System.getenv("AWS_ACCESS_KEY_ID"));
	}

	public String hazelcastAwsSecretKey() {
		return getConfigParam("aws_secret_key", System.getenv("AWS_SECRET_ACCESS_KEY"));
	}

	public String hazelcastEc2DiscoveryGroup() {
		return getConfigParam("hc.discovery_group", "hazelcast");
	}

	public int hazelcastMaxCacheSize() {
		return getConfigInt("hc.max_size", 5000);
	}

	public Object hazelcastEvictionPolicy() {
		return getConfigParam("hc.eviction_policy", "LRU");
	}

	public boolean mongoSslEnabled() {
		return getConfigBoolean("mongodb.ssl_enabled", false);
	}

	public boolean mongoSslAllowAll() {
		return getConfigBoolean("mongodb.ssl_allow_all", false);
	}

	public String mongoConnectionUri() {
		return getConfigParam("mongodb.uri", "");
	}

	public String mongoDatabase() {
		return getConfigParam("mongodb.database", getRootAppIdentifier());
	}

	public String mongoHost() {
		return getConfigParam("mongodb.host", "localhost");
	}

	public int mongoPort() {
		return getConfigInt("mongodb.port", 27017);
	}

	public String mongoAuthUser() {
		return getConfigParam("mongodb.user", "");
	}

	public String mongoAuthPassword() {
		return getConfigParam("mongodb.password", "");
	}

}
