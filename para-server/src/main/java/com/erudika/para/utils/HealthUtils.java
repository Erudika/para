package com.erudika.para.utils;

import com.erudika.para.InitializeListener;
import com.erudika.para.Para;
import com.erudika.para.core.App;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility for evaluating the health of the Para cluster.
 * @author Jeremy Wiesner [jswiesner@gmail.com]
 */
public enum HealthUtils implements InitializeListener, Runnable {

	/**
	 * Singleton.
	 */
	INSTANCE {

		private boolean healthy = false;
		private boolean wasHealthy = false;
		private ScheduledFuture<?> scheduledHealthCheck;
		private final List<String> failedServices = new ArrayList<>(3);
		private final int HEALTH_CHECK_INTERVAL_SECONDS = Config.getConfigInt("health.check_interval", 60);

		@Override
		public boolean isHealthy() {
			return healthy;
		}

		@Override
		public  synchronized void performHealthCheck() {
			String rootAppId = App.id(Config.getRootAppIdentifier());
			if (!StringUtils.isBlank(rootAppId)) {
				healthy = true;
				failedServices.clear();
				// read the root app from the DAO
				if (Para.getDAO() != null && Para.getDAO().read(rootAppId) == null) {
					healthy = false;
					failedServices.add("DAO");
				}
				// read the root app from the search, if enabled
				if (Config.isSearchEnabled() && Para.getSearch().findById(rootAppId) == null) {
					healthy = false;
					failedServices.add("Search");
				}
				// read the root app from the cache, if enabled
				if (Config.isCacheEnabled() && !Para.getCache().contains(rootAppId)) {
					healthy = false;
					failedServices.add("Cache");
				}
			}
			if (wasHealthy && !healthy) {
				logger.error("Server is no longer healthy! Health check failed for services: " +
						StringUtils.join(failedServices, ", "));
				Para.getCache().remove(rootAppId); // remove root app from cache so we hit the on DB next check
			}
			if (!wasHealthy && healthy) {
				logger.info("Server is healthy.");
			}
			wasHealthy = healthy;
		}

		@Override
		public void onInitialize() {
			performHealthCheck();
			if (!isHealthy()) {
				logger.warn("Server is unhealthy - root app not found. Open /v1/_setup in the browser to initialize Para.");
			}
			if (Config.getConfigBoolean("health_check_enabled", true) && scheduledHealthCheck == null) {
				scheduledHealthCheck = Para.getScheduledExecutorService().
						scheduleAtFixedRate(this, 30, HEALTH_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
			}
		}

		@Override
		public void run() {
			performHealthCheck();
		}
	};

	private static final Logger logger = LoggerFactory.getLogger(HealthUtils.class);

	/**
	 * Provides access to the singleton instance methods.
	 *
	 * @return an instance of this class
	 */
	public static HealthUtils getInstance() {
		return INSTANCE;
	}

	/**
	 * A method to query the general health of the Para server.
	 * @return true if server is healthy
	 */
	public abstract boolean isHealthy();

	/**
	 * Evaluate the health of the Para server by querying the root app object from the database, search and cache.
	 */
	public abstract void performHealthCheck();
}
