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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.Timer;
import com.erudika.para.rest.CustomResourceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;

/**
 * A centralized utility for managing and retrieving all Para performance metrics.
 * @author Jeremy Wiesner [jswiesner@gmail.com]
 */
public final class MetricsUtils {

	private static final String SYSTEM_METRICS_NAME = "_system";

	private MetricsUtils() { }

	static {
		// setup metrics reporting
		MetricRegistry systemRegistry = SharedMetricRegistries.setDefault(SYSTEM_METRICS_NAME);
		Logger metricsLogger = LoggerFactory.getLogger("paraMetrics");
		Slf4jReporter.forRegistry(systemRegistry).outputTo(metricsLogger).build().start(1, TimeUnit.MINUTES);
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
		if (clazz.isAssignableFrom(CustomResourceHandler.class)) {
			return clazz.getName();
		} else {
			return clazz.getSimpleName();
		}
	}

	private static Timer getTimer(String registryName, String className, String... names) {
		return SharedMetricRegistries.getOrCreate(registryName).timer(MetricRegistry.name(className, names));
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
