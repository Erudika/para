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
package com.erudika.para.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.jmx.JmxReporter;
import com.erudika.para.InitializeListener;
import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.rest.CustomResourceHandler;
import com.erudika.para.rest.RestUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.HealthUtils;
import com.erudika.para.utils.RegistryUtils;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import static com.erudika.para.Para.getCustomResourceHandlers;
import static com.erudika.para.metrics.Metrics.SYSTEM_METRICS_NAME;
import java.util.ArrayList;
import java.util.Objects;

/**
 * A centralized utility for managing and retrieving all Para performance metrics.
 * @author Jeremy Wiesner [jswiesner@gmail.com]
 */
public enum MetricsUtils implements InitializeListener, Runnable {

	/**
	 * Singleton.
	 */
	INSTANCE {

		private ScheduledFuture<?> scheduledRegistryCheck;

		@Override
		public void onInitialize() {
			if (!Config.getConfigBoolean("metrics_enabled", true)) {
				return;
			}
			// setup metrics log file reporting
			MetricRegistry systemRegistry = SharedMetricRegistries.tryGetDefault();
			if (systemRegistry == null) {
				systemRegistry = SharedMetricRegistries.setDefault(SYSTEM_METRICS_NAME);
			}
			Logger metricsLogger = LoggerFactory.getLogger("paraMetrics");
			int loggingRate = Config.getConfigInt("metrics.logging_rate", 60);
			if (loggingRate > 0) {
				Slf4jReporter.forRegistry(systemRegistry).outputTo(metricsLogger).build().
						start(loggingRate, TimeUnit.SECONDS);
			}

			// initialize metrics for the system and all existing applications
			initializeMetrics(SYSTEM_METRICS_NAME);

			// setup graphite reporting for the system
			String host = Config.getConfigParam("metrics.graphite.host", null);
			if (GRAPHITE_PERIOD > 0 && !StringUtils.isBlank(host)) {
				int port = Config.getConfigInt("metrics.graphite.port", 2003);
				String prefixSystem = MetricsUtils.GRAPHITE_SYS_PREFIX_TEMPLATE;
				if (INSTANCE_ID != null) {
					HashMap<String, Object> prefixContext = new HashMap<>();
					prefixContext.put("INSTANCE_ID", INSTANCE_ID);
					prefixSystem = Utils.compileMustache(prefixContext, prefixSystem);
				}
				GraphiteSettings settings = new GraphiteSettings(host, port);
				createGraphiteReporter(SYSTEM_METRICS_NAME, settings, prefixSystem);
			}

			if (HealthUtils.getInstance().isHealthy()) {
				// find all app objects even if there are more than 10000 apps in the system
				// apps will be added in chronological order, root app first, followed by child apps
				Pager pager = new Pager(1, "_docid", false, Config.DEFAULT_LIMIT);
				List<App> apps = new LinkedList<>();
				List<App> appsPage;
				do {
					appsPage = Para.getSearch().findQuery(Utils.type(App.class), "*", pager);
					apps.addAll(appsPage);
					logger.debug("Found a page of {} apps.", appsPage.size());
				} while (!appsPage.isEmpty());

				logger.info("Found root app '{}' and {} existing child app(s){}", Config.getRootAppIdentifier(),
						apps.isEmpty() ? 0 : apps.size() - 1, apps.isEmpty() || !logger.isDebugEnabled() ? "." : ":");

				for (App app : apps) {
					logger.debug("   {}{}", app.getAppIdentifier(), app.isRootApp() ? " (root app)" : "");
					initializeMetrics(app.getAppIdentifier());
				}
			}

			// schedule the regular check on metrics settings registries to establish app-specific reporting
			if (scheduledRegistryCheck == null) {
				scheduledRegistryCheck = Para.getScheduledExecutorService().
						scheduleAtFixedRate(this, 0, 1, TimeUnit.MINUTES);
			}

			// setup initialization/cleanup for all new apps
			App.addAppCreatedListener((App app) -> {
				if (app != null) {
					initializeMetrics(app.getAppIdentifier());
				}
			});

			// setup listeners for push metrics settings
			App.addAppSettingAddedListener((App app, String settingKey, Object settingValue) -> {
				if (app != null) {
					addAppSetting(app, settingKey, settingValue);
				}
			});
			App.addAppSettingRemovedListener((App app, String settingKey) -> {
				if (app != null) {
					removeAppSetting(app, settingKey);
				}
			});
		}

		@Override
		public void run() {
			syncAppMetricsReporters();
		}
	};

	private static final Logger logger = LoggerFactory.getLogger(MetricsUtils.class);
	private static final String INSTANCE_ID = Config.getConfigParam("instance_id", null);

	private static final Map<String, GraphiteReporter> GRAPHITE_REPORTERS = new HashMap<>();
	private static final Map<String, GraphiteSettings> GRAPHITE_SETTINGS = new HashMap<>();
	private static final String GRAPHITE_SYS_PREFIX_TEMPLATE = Config.getConfigParam("metrics.graphite.prefix_system", null);
	private static final String GRAPHITE_APP_PREFIX_TEMPLATE = Config.getConfigParam("metrics.graphite.prefix_apps", null);
	private static final int GRAPHITE_PERIOD = Config.getConfigInt("metrics.graphite.period", 0);

	/**
	 * The name of the registry holding app-specific settings for reporting metrics to Graphite.
	 */
	public static final String GRAPHITE_REGISTRY_NAME = "GraphiteReporter";

	/**
	 * The name of the app settings object that contains the info to push an app's metrics to Graphite.
	 */
	public static final String GRAPHITE_APP_SETTINGS_NAME = "metricsGraphiteReporter";

	/**
	 * A utility class for holding the settings for connecting to a Graphite server.
	 */
	private static final class GraphiteSettings extends HashMap<String, Object> {

		GraphiteSettings(String host, int port) {
			this.put("host", host);
			this.put("port", port);
		}

		public String getHost() {
			return (String) this.get("host");
		}

		public int getPort() {
			return (int) this.get("port");
		}

		public static GraphiteSettings parse(Object object) {
			Map map = (Map) object;
			if (map == null || !map.containsKey("host") || !map.containsKey("port")) {
				return null;
			}
			return new GraphiteSettings((String) map.get("host"), (int) map.get("port"));
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null || this.getClass() != obj.getClass()) {
				return false;
			}
			return Objects.equals(this.getHost(), ((GraphiteSettings) obj).getHost()) &&
					Objects.equals(this.getPort(), ((GraphiteSettings) obj).getPort());
		}

		@Override
		public int hashCode() {
			return 67 * Objects.hashCode(this.getPort()) + Objects.hashCode(this.getHost());
		}
	}

	/**
	 * Provides access to the singleton instance methods.
	 * @return an instance of this class
	 */
	public static MetricsUtils getInstance() {
		return INSTANCE;
	}

	/**
	 * Initialize all the possible metrics for a specific registry (either the system registry or an application registry).
	 * This ensures that all metrics report with zero values from system startup or application creation.
	 * @param registryName the name of the registry to initialize. Either the system default name or an appid.
	 */
	private static void initializeMetrics(String registryName) {
		MetricRegistry registry = SharedMetricRegistries.getOrCreate(registryName);

		// register the DAO timers
		if (Para.getDAO() != null) {
			String daoClassName = Metrics.getClassName(Para.getDAO().getClass());
			registry.timer(MetricRegistry.name(daoClassName, "create"));
			registry.timer(MetricRegistry.name(daoClassName, "read"));
			registry.timer(MetricRegistry.name(daoClassName, "update"));
			registry.timer(MetricRegistry.name(daoClassName, "delete"));
			registry.timer(MetricRegistry.name(daoClassName, "createAll"));
			registry.timer(MetricRegistry.name(daoClassName, "readAll"));
			registry.timer(MetricRegistry.name(daoClassName, "readPage"));
			registry.timer(MetricRegistry.name(daoClassName, "updateAll"));
			registry.timer(MetricRegistry.name(daoClassName, "deleteAll"));
		}

		// register the search timers
		if (Config.isSearchEnabled()) {
			String searchClassName = Metrics.getClassName(Para.getSearch().getClass());
			registry.timer(MetricRegistry.name(searchClassName, "index"));
			registry.timer(MetricRegistry.name(searchClassName, "unindex"));
			registry.timer(MetricRegistry.name(searchClassName, "indexAll"));
			registry.timer(MetricRegistry.name(searchClassName, "unindexAll"));
			registry.timer(MetricRegistry.name(searchClassName, "findById"));
			registry.timer(MetricRegistry.name(searchClassName, "findByIds"));
			registry.timer(MetricRegistry.name(searchClassName, "findNearby"));
			registry.timer(MetricRegistry.name(searchClassName, "findPrefix"));
			registry.timer(MetricRegistry.name(searchClassName, "findQuery"));
			registry.timer(MetricRegistry.name(searchClassName, "findNestedQuery"));
			registry.timer(MetricRegistry.name(searchClassName, "findSimilar"));
			registry.timer(MetricRegistry.name(searchClassName, "findTagged"));
			registry.timer(MetricRegistry.name(searchClassName, "findTags"));
			registry.timer(MetricRegistry.name(searchClassName, "findTermInList"));
			registry.timer(MetricRegistry.name(searchClassName, "findTerms"));
			registry.timer(MetricRegistry.name(searchClassName, "findWildcard"));
			registry.timer(MetricRegistry.name(searchClassName, "getCount"));
			registry.timer(MetricRegistry.name(searchClassName, "rebuildIndex"));
		}

		// register the cache timers
		if (Config.isCacheEnabled()) {
			String cacheClassName = Metrics.getClassName(Para.getCache().getClass());
			registry.timer(MetricRegistry.name(cacheClassName, "contains"));
			registry.timer(MetricRegistry.name(cacheClassName, "put"));
			registry.timer(MetricRegistry.name(cacheClassName, "get"));
			registry.timer(MetricRegistry.name(cacheClassName, "remove"));
			registry.timer(MetricRegistry.name(cacheClassName, "putAll"));
			registry.timer(MetricRegistry.name(cacheClassName, "getAll"));
			registry.timer(MetricRegistry.name(cacheClassName, "removeAll"));
		}

		// register timers on the REST endpoints
		if (Config.API_ENABLED) {
			String restUtilsClassName = Metrics.getClassName(RestUtils.class);
			registry.timer(MetricRegistry.name(restUtilsClassName, "crud", "read"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "crud", "create"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "crud", "overwrite"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "crud", "update"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "crud", "delete"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "batch", "read"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "batch", "create"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "batch", "update"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "batch", "delete"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "links", "read"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "links", "delete"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "links", "create"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "search", "id"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "search", "ids"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "search", "nested"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "search", "nearby"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "search", "prefix"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "search", "similar"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "search", "tagged"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "search", "in"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "search", "terms"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "search", "wildcard"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "search", "count"));
			registry.timer(MetricRegistry.name(restUtilsClassName, "search", "default"));
		}

		// register timers on custom resource handlers
		for (final CustomResourceHandler handler : getCustomResourceHandlers()) {
			String resourceHandlerClassName = Metrics.getClassName(handler.getClass());
			registry.timer(MetricRegistry.name(resourceHandlerClassName, "handleGet"));
			registry.timer(MetricRegistry.name(resourceHandlerClassName, "handlePost"));
			registry.timer(MetricRegistry.name(resourceHandlerClassName, "handlePatch"));
			registry.timer(MetricRegistry.name(resourceHandlerClassName, "handlePut"));
			registry.timer(MetricRegistry.name(resourceHandlerClassName, "handleDelete"));
		}

		if (Config.getConfigBoolean("metrics.jmx_enabled", false)) {
			JmxReporter.forRegistry(registry).inDomain(registryName).build().start();
		}
	}

	/**
	 * Publish an app's @{link MetricRegistry} to Graphite.
	 * @param appid the name of the app.
	 * @param settings settings specifying the host URL and port of the Graphite server.
	 */
	private static void createAppGraphiteReporter(String appid, GraphiteSettings settings) {
		HashMap<String, Object> prefixContext = new HashMap<>();
		prefixContext.put("APP_ID", appid);
		if (INSTANCE_ID != null) {
			prefixContext.put("INSTANCE_ID", INSTANCE_ID);
		}
		String appPrefix = Utils.compileMustache(prefixContext, GRAPHITE_APP_PREFIX_TEMPLATE);
		createGraphiteReporter(appid, settings, appPrefix);
	}

	/**
	 * Publish a specific @{link MetricRegistry} to Graphite.
	 * @param registryName the name of the registry. Either the system default name or an appid.
	 * @param settings settings specifying the host URL and port of the Graphite server.
	 * @param prefix an optional prefix to apply to the reported metrics.
	 */
	private static void createGraphiteReporter(String registryName, GraphiteSettings settings, String prefix) {
		Graphite graphite = new Graphite(settings.getHost(), settings.getPort());
		GraphiteReporter reporter = GraphiteReporter.forRegistry(SharedMetricRegistries.getOrCreate(registryName))
				.prefixedWith(prefix)
				.build(graphite);
		reporter.start(GRAPHITE_PERIOD, TimeUnit.SECONDS);
		GRAPHITE_REPORTERS.put(registryName, reporter);
		GRAPHITE_SETTINGS.put(registryName, settings);
		logger.info("Created Graphite reporter for registry \"{}\", pushing to {{}:{}}", registryName, settings.getHost(),
				settings.getPort());
	}

	/**
	 * A listener method to process new settings registered on applications (including the root app).
	 * @param app the application the setting was added to.
	 * @param key the name of the setting
	 * @param value the value of the setting
	 */
	private static void addAppSetting(App app, String key, Object value) {
		if (GRAPHITE_APP_SETTINGS_NAME.equals(key)) {
			// validate the graphite reporter settings and, if valid, save them to the registry
			if (Map.class.isAssignableFrom(value.getClass())) {
				Map graphiteSettings = (Map) value;
				if (graphiteSettings.containsKey("host") && graphiteSettings.containsKey("port")) {
					String host = (String) graphiteSettings.get("host");
					Integer port = (Integer) graphiteSettings.get("port");
					if (!StringUtils.isBlank(host) && port != null && port > 0) {
						GraphiteSettings settings = new GraphiteSettings(host, port);
						RegistryUtils.putValue(GRAPHITE_REGISTRY_NAME, app.getAppIdentifier(), settings);
					}
				}
			}
		}
	}

	/**
	 * A listener method to process removed settings for an application (including the root app).
	 * @param app the application the setting was removed from.
	 * @param key the name of the setting
	 */
	public static void removeAppSetting(App app, String key) {
		if (GRAPHITE_APP_SETTINGS_NAME.equals(key)) {
			RegistryUtils.removeValue(GRAPHITE_REGISTRY_NAME, app.getAppIdentifier());
		}
	}

	/**
	 * A scheduled check of metrics setting registries to detect changes and apply them.
	 *
	 * Note: this method keeps the local registry of reporters in sync with the central one in the database.
	 * This ensures that all nodes in a cluster push metrics to the corresponding Graphite servers for each app.
	 */
	private static void syncAppMetricsReporters() {
		// check for app-specific graphite push settings
		Map<String, Object> graphiteRegistry = RegistryUtils.getRegistry(GRAPHITE_REGISTRY_NAME);
		if (graphiteRegistry != null && GRAPHITE_PERIOD > 0) {
			// iterate the registry values
			for (Map.Entry<String, Object> iter : graphiteRegistry.entrySet()) {
				String appid = iter.getKey();
				GraphiteSettings settings = GraphiteSettings.parse(iter.getValue());
				if (settings == null) {
					continue;
				}
				// close an existing reporter
				if (GRAPHITE_REPORTERS.containsKey(appid)) {
					if (!settings.equals(GRAPHITE_SETTINGS.get(appid))) {
						// the new settings aren't the same, stop the existing reporter and replace it with a new one
						GRAPHITE_REPORTERS.get(appid).stop();
						GRAPHITE_REPORTERS.remove(appid);
						GRAPHITE_SETTINGS.remove(appid);
						createAppGraphiteReporter(appid, settings);
					}
				} else {
					// no existing reporter for this app, create it
					createAppGraphiteReporter(appid, settings);
				}
			}
			// check if any of the graphite reporters was disabled by an app, if so, remove it
			List<Map.Entry<String, GraphiteReporter>> appsToRemove = new ArrayList<>();
			for (Map.Entry<String, GraphiteReporter> iter : GRAPHITE_REPORTERS.entrySet()) {
				if (!iter.getKey().equals(SYSTEM_METRICS_NAME) && !graphiteRegistry.containsKey(iter.getKey())) {
					appsToRemove.add(iter);
				}
			}
			for (Map.Entry<String, GraphiteReporter> iter : appsToRemove) {
				iter.getValue().stop();
				GRAPHITE_REPORTERS.remove(iter.getKey());
				GRAPHITE_SETTINGS.remove(iter.getKey());
			}
		}
	}
}
