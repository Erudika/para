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
import com.codahale.metrics.Timer;

import java.io.Closeable;

/**
 * A centralized utility for creating and managing all application performance metrics.
 * @author Jeremy Wiesner [jswiesner@gmail.com]
 */
public final class MetricsUtils {

	private static final String SYSTEM_METRICS_NAME = "_system";

	private MetricsUtils() { }

	/**
	 * Instantiate timing of a particular class and method for a specific application.
	 * @param appid the application that invoked the request
	 * @param clazz the Class to be timed
	 * @param methodName the name of the method to be timed
	 * @return a closeable context that spans the timed method
	 */
	public static MetricsUtils.Context time(String appid, Class clazz, String methodName) {
		Timer systemTimer = getTimer(SYSTEM_METRICS_NAME, clazz, methodName);
		Timer appTimer = getTimer(appid, clazz, methodName);
		return new MetricsUtils.Context(systemTimer, appTimer);
	}

	private static Timer getTimer(String registryName, Class clazz, String methodName) {
		return SharedMetricRegistries.getOrCreate(registryName).timer(MetricRegistry.name(clazz, methodName));
	}

	/**
	 * An auto-closeable class that manages timers for both the overall system as well as specific application.
	 */
	public static final class Context implements Closeable {

		private final Timer.Context systemContext;
		private final Timer.Context appContext;

		private Context(Timer systemTimer, Timer appTimer) {
			this.systemContext = systemTimer.time();
			this.appContext = appTimer.time();
		}

		@Override
		public void close() {
			systemContext.stop();
			appContext.stop();
		}
	}
}
