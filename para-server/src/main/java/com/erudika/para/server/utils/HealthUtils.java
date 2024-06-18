package com.erudika.para.server.utils;

import com.erudika.para.core.App;
import com.erudika.para.core.listeners.InitializeListener;
import com.erudika.para.core.utils.Para;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
			String rootAppId = App.id(Para.getConfig().getRootAppIdentifier());
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
				if (healthy && Para.getConfig().isSearchEnabled() && Para.getSearch().findById(rootAppId) == null) {
					healthy = false;
					failedServices.add("Search");
				}
				// test the cache by putting a dummy object in, then remove it
				// DO NOT assume that the root app object is still cached here from the last read() call above
				if (healthy && Para.getConfig().isCacheEnabled()) {
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
			String confFile = Paths.get(Para.getConfig().getConfigFilePath()).toAbsolutePath().toString();
			if (!isHealthy()) {
				if (rootAppExists) {
					logger.warn("Server is unhealthy - the search index may be corrupted and may have to be rebuilt.");
				} else {
					saveConfigFile(confFile, Para.setup());
				}
			} else if (isHealthy() && !Para.getConfig().inProduction()) {
				App root = Para.getDAO().read(App.id(Para.getConfig().getRootAppIdentifier()));
				try {
					if (root != null && (!Files.exists(Path.of(confFile)) || !loadConfigFile(confFile).contains(root.getSecret()))) {
						saveConfigFile(confFile, Map.of("accessKey", root.getId(), "secretKey", root.getSecret()));
					}
				} catch (IOException ex) {
					logger.error(null, ex);
				}
			}

			if (Para.getConfig().healthCheckEnabled() && scheduledHealthCheck == null) {
				scheduledHealthCheck = Para.getScheduledExecutorService().
						scheduleAtFixedRate(this, 30, Para.getConfig().healthCheckInvervalSec(), TimeUnit.SECONDS);
			}
		}

		private void saveConfigFile(String confFile, Map<String, String> rootAppCredentials) {
			if (rootAppCredentials.containsKey("secretKey")) {
				String confString = "";
				try {
					confString = loadConfigFile(confFile);
				} catch (IOException e) {
					logger.info("Initialized root app with access key '{}' and secret '{}', "
							+ "but could not write these to {}.",
							rootAppCredentials.get("accessKey"), rootAppCredentials.get("secretKey"), confFile);
				}
				String accessKey = "para.root_access_key = \"" + rootAppCredentials.get("accessKey") + "\"";
				String secretKey = "para.root_secret_key = \"" + rootAppCredentials.get("secretKey") + "\"";
				if (confString.contains("para.root_access_key")) {
					confString = confString.replaceAll("para\\.root_access_key\\s*=\\s*\".*?\"", accessKey);
				} else {
					confString += "\n" + accessKey;
				}
				if (confString.contains("para.root_secret_key")) {
					confString = confString.replaceAll("para\\.root_secret_key\\s*=\\s*\".*?\"", secretKey);
				} else {
					confString += "\n" + secretKey;
				}
				Para.getFileStore().store(confFile, new ByteArrayInputStream(confString.
						getBytes(StandardCharsets.UTF_8)));
				logger.info("Saved root app credentials to {}.", confFile);
			} else {
				logger.warn("Server is unhealthy - failed to initialize root app. Open http://localhost:" +
						Para.getConfig().serverPort() + "/v1/_setup in the browser to initialize Para manually.");
			}
		}

		private String loadConfigFile(String confFile) throws IOException {
			try (InputStream ref = getClass().getClassLoader().getResourceAsStream("reference.conf");
						InputStream config = Optional.ofNullable(Para.getFileStore().load(confFile)).orElse(ref)) {
				return new String(config.readAllBytes(), StandardCharsets.UTF_8);
			} catch (Exception e) {
				return "";
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
