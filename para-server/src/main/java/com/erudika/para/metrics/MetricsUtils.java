/*
 * Copyright 2013-2017 Erudika. https://erudika.com
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

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.Timer;
import com.erudika.para.AppCreatedListener;
import com.erudika.para.InitializeListener;
import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.rest.CustomResourceHandler;
import com.erudika.para.rest.RestUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.erudika.para.Para.getCustomResourceHandlers;
import com.erudika.para.utils.Pager;
import java.util.LinkedList;

/**
 * A centralized utility for managing and retrieving all Para performance metrics.
 * @author Jeremy Wiesner [jswiesner@gmail.com]
 */
public enum MetricsUtils implements InitializeListener {

	/**
	 * Singleton.
	 */
	INSTANCE {

		@Override
		public void onInitialize() {
			if (!Config.getConfigBoolean("metrics_enabled", true)) {
				return;
			}
			// setup metrics log file reporting
			MetricRegistry systemRegistry = SharedMetricRegistries.setDefault(SYSTEM_METRICS_NAME);
			Logger metricsLogger = LoggerFactory.getLogger("paraMetrics");
			int loggingRate = Config.getConfigInt("metrics.logging_rate", 60);
			if (loggingRate > 0) {
				Slf4jReporter.forRegistry(systemRegistry).outputTo(metricsLogger).build().
						start(loggingRate, TimeUnit.SECONDS);
			}

			// initialize metrics for the system and all existing applications
			MetricsUtils.initializeMetrics(SYSTEM_METRICS_NAME);

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
					apps.size() - 1, apps.isEmpty() || !logger.isDebugEnabled() ? "." : ":");
			for (App app : apps) {
				logger.debug("   {}{}", app.getAppIdentifier(), app.isRootApp() ? " (root app)" : "");
				MetricsUtils.initializeMetrics(app.getAppIdentifier());
			}

			// setup initialization for all new apps
			App.addAppCreatedListener(new AppCreatedListener() {
				public void onAppCreated(App app) {
					if (app != null) {
						MetricsUtils.initializeMetrics(app.getAppIdentifier());
					}
				}
			});
		}
	};

	private static final Logger logger = LoggerFactory.getLogger(MetricsUtils.class);
	private static final String SYSTEM_METRICS_NAME = "_system";
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	/**
	 * Provides access to the singleton instance methods.
	 * @return an instance of this class
	 */
	public static MetricsUtils getInstance() {
		return INSTANCE;
	}

	/**
	 * Instantiate timing of a particular class and method for a specific application.
	 * @param appid the application that invoked the request
	 * @param clazz the Class to be timed
	 * @param names one or more unique names to identify the timer - usually a method name
	 * @return a closeable context that encapsulates the timed method
	 */
	public static MetricsUtils.Context time(String appid, Class clazz, String... names) {
		String className = getClassName(clazz);
		Timer systemTimer = getTimer(SYSTEM_METRICS_NAME, className, names);
		Timer appTimer = appid == null || appid.isEmpty() ? null : getTimer(appid, className, names);
		return new MetricsUtils.Context(systemTimer, appTimer);
	}

	private static String getClassName(Class clazz) {
		if (clazz.getSimpleName().contains("EnhancerByGuice")) {
			clazz = clazz.getSuperclass();
		}
		if (CustomResourceHandler.class.isAssignableFrom(clazz)) {
			return clazz.getCanonicalName();
		} else {
			return clazz.getSimpleName();
		}
	}

	private static Timer getTimer(String registryName, String className, String... names) {
		return SharedMetricRegistries.getOrCreate(registryName).timer(MetricRegistry.name(className, names));
	}

	/**
	 * Initialize all the possible metrics for a specific registry (either the system registry or an application registry).
	 * This ensures that all metrics report with zero values from system startup or application creation.
	 * @param registryName the name of the registry to initialize. Either the system default name or an appid.
	 */
	private static void initializeMetrics(String registryName) {
		MetricRegistry registry = SharedMetricRegistries.getOrCreate(registryName);

		// register the DAO timers
		if (CoreUtils.getInstance().getDao() != null) {
			String daoClassName = getClassName(CoreUtils.getInstance().getDao().getClass());
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
			String searchClassName = getClassName(CoreUtils.getInstance().getSearch().getClass());
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
		}

		// register the cache timers
		if (Config.isCacheEnabled()) {
			String cacheClassName = getClassName(CoreUtils.getInstance().getCache().getClass());
			registry.timer(MetricRegistry.name(cacheClassName, "contains"));
			registry.timer(MetricRegistry.name(cacheClassName, "put"));
			registry.timer(MetricRegistry.name(cacheClassName, "get"));
			registry.timer(MetricRegistry.name(cacheClassName, "remove"));
			registry.timer(MetricRegistry.name(cacheClassName, "putAll"));
			registry.timer(MetricRegistry.name(cacheClassName, "getAll"));
			registry.timer(MetricRegistry.name(cacheClassName, "removeAll"));
		}

		// register timers on the REST endpoints
		String restUtilsClassName = getClassName(RestUtils.class);
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

		// register timers on custom resource handlers
		for (final CustomResourceHandler handler : getCustomResourceHandlers()) {
			String resourceHandlerClassName = getClassName(handler.getClass());
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
	 * Get metrics data in the form of a JSON string for the entire system or a specific app.
	 * @param appid the application to get metrics data for. If null, return system metrics.
	 * @param prettyPrint option to format the JSON string with pretty formatting
	 * @return a JSON string containing metrics data
	 * @throws JsonProcessingException if MetricRegistry cannot be parsed into a JSON object
	 */
	public static String getMetricsData(String appid, boolean prettyPrint) throws JsonProcessingException {
		MetricRegistry registry;
		if (appid == null) {
			registry = SharedMetricRegistries.getDefault();
		} else {
			registry = SharedMetricRegistries.getOrCreate(appid);
		}
		return getMetricsWriter(prettyPrint).writeValueAsString(registry);
	}

	private static ObjectWriter getMetricsWriter(boolean prettyPrint) {
		if (prettyPrint) {
			return JSON_MAPPER.writerWithDefaultPrettyPrinter();
		} else {
			return JSON_MAPPER.writer();
		}
	}

	/**
	 * An auto-closeable class that manages timers for both the overall system as well as specific application.
	 */
	public static final class Context implements Closeable {

		private final Timer.Context systemContext;
		private final Timer.Context appContext;

		private Context(Timer systemTimer, Timer appTimer) {
			this.systemContext = systemTimer.time();
			this.appContext = appTimer == null ? null : appTimer.time();
		}

		@Override
		public void close() {
			systemContext.stop();
			if (appContext != null) {
				appContext.stop();
			}
		}
	}
}
