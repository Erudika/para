package com.erudika.para.utils;

import com.erudika.para.InitializeListener;
import com.erudika.para.Para;
import com.erudika.para.ParaServer;
import com.erudika.para.core.App;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
		private boolean rootAppExists = false;
		private ScheduledFuture<?> scheduledHealthCheck;
		private final List<String> failedServices = new ArrayList<>(3);
		private final int healthCheckInterval = Config.getConfigInt("health.check_interval", 60);

		{
			App.addAppCreatedListener((App app) -> {
				if (app.isRootApp()) {
					performHealthCheck();
				}
			});
		}

		@Override
		public boolean isHealthy() {
			return healthy;
		}

		@Override
		public synchronized void performHealthCheck() {
			String rootAppId = App.id(Config.getRootAppIdentifier());
			if (!StringUtils.isBlank(rootAppId)) {
				healthy = true;
				failedServices.clear();
				// last check was bad, remove root app from cache so we can hit the DB
				if (!wasHealthy) {
					Para.getCache().remove(rootAppId);
				}
				// read the root app from the DAO
				if (Para.getDAO() != null) {
					rootAppExists = Para.getDAO().read(rootAppId) != null;
					if (!rootAppExists) {
						healthy = false;
						failedServices.add("DAO");
					}
				}
				// read the root app from the search, if enabled
				if (healthy && Config.isSearchEnabled() && Para.getSearch().findById(rootAppId) == null) {
					healthy = false;
					failedServices.add("Search");
				}
				// test the cache by putting a dummy object in, then remove it
				// DO NOT assume that the root app object is still cached here from the last read() call above
				if (healthy && Config.isCacheEnabled()) {
					String cacheTestId = UUID.randomUUID().toString();
					Para.getCache().put(cacheTestId, "ok");
					healthy = Para.getCache().contains(cacheTestId);
					Para.getCache().remove(cacheTestId);
					if (!healthy) {
						failedServices.add("Cache");
					}
				}
			}
			if (wasHealthy && !healthy) {
				logger.error("Server is no longer healthy! Health check failed for services: " +
						StringUtils.join(failedServices, ", "));
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
				logger.warn("Server is unhealthy - " + (rootAppExists ?
						"the search index may be corrupted and may have to be rebuilt." :
						"root app not found. Open http://localhost:" + ParaServer.getServerPort() +
								"/v1/_setup in the browser to initialize Para."));
			}
			if (Config.getConfigBoolean("health_check_enabled", true) && scheduledHealthCheck == null) {
				scheduledHealthCheck = Para.getScheduledExecutorService().
						scheduleAtFixedRate(this, 30, healthCheckInterval, TimeUnit.SECONDS);
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
