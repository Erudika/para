package com.erudika.para.utils;

import com.erudika.para.Para;
import com.erudika.para.core.App;

/**
 * A utility for evaluating the health of the Para cluster.
 * @author Jeremy Wiesner [jswiesner@gmail.com]
 */
public final class HealthUtils {

	private static final int HEALTH_CHECK_INTERVAL_MS = 1000 * Config.getConfigInt("health.check_interval", 60);
	private static volatile long lastHealthCheckTime = -1L;
	private static boolean healthy = false;

	private HealthUtils() { }

	/**
	 * A method to query the general health of the Para server.
	 * @return true if server is healthy
	 */
	public static boolean isHealthy() {
		if (isTimeForHealthCheck()) {
			performHealthCheck();
		}
		return healthy;
	}

	/**
	 * Evaluate the health of the Para server by querying the root app object from the database, search and cache.
	 */
	private static synchronized void performHealthCheck() {
		if (isTimeForHealthCheck()) {
			healthy = true;
			String rootAppId = App.id(Config.getRootAppIdentifier());
			if (rootAppId != null && !rootAppId.isEmpty()) {
				// read the root app from the DAO
				if (Para.getDAO() != null && Para.getDAO().read(rootAppId) == null) {
					healthy = false;
				}
				// read the root app from the search, if enabled
				if (healthy && Config.isSearchEnabled() && Para.getSearch().findById(rootAppId) == null) {
					healthy = false;
				}
				// read the root app from the cache, if enabled
				if (healthy && Config.isCacheEnabled() && !Para.getCache().contains(rootAppId)) {
					healthy = false;
				}
			}
			lastHealthCheckTime = Utils.timestamp();
		}
	}

	private static boolean isTimeForHealthCheck() {
		return (Utils.timestamp() - lastHealthCheckTime) > HEALTH_CHECK_INTERVAL_MS;
	}
}
