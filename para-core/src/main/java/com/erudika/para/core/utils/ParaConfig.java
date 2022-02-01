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
import org.apache.commons.lang3.math.NumberUtils;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ParaConfig extends Config {


	@Override
	public String getConfigRootPrefix() {
		return Config.PARA;
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
	 * @return JWT (access token) expiration in seconds. Default: 1 week
	 */
	public int jwtExpiresAfterSec() {
		return NumberUtils.toInt(getConfigParam("jwt_expires_after", ""), 7 * 24 * 60 * 60);
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
		return App.identifier(App.id(getConfigParam("app_name", PARA)));
	}
}
