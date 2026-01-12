/*
 * Copyright 2013-2026 Erudika. https://erudika.com
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
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.cache.Cache;
import com.erudika.para.core.email.Emailer;
import com.erudika.para.core.listeners.DestroyListener;
import com.erudika.para.core.listeners.IOListener;
import com.erudika.para.core.listeners.InitializeListener;
import com.erudika.para.core.persistence.DAO;
import com.erudika.para.core.queue.Queue;
import com.erudika.para.core.rest.CustomResourceHandler;
import com.erudika.para.core.search.Search;
import com.erudika.para.core.storage.FileStore;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main Para utility class and entry point.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class Para {

	private static final ParaConfig CONF = new ParaConfig();
	private static final Logger logger = LoggerFactory.getLogger(Para.class);
	private static final Set<DestroyListener> DESTROY_LISTENERS = new LinkedHashSet<DestroyListener>();
	private static final Set<InitializeListener> INIT_LISTENERS = new LinkedHashSet<InitializeListener>();
	private static final Set<IOListener> IO_LISTENERS = new LinkedHashSet<IOListener>();
	private static final Set<IOListener> SEARCH_LISTENERS = new LinkedHashSet<IOListener>();
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(CONF.executorThreads());
	private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(CONF.executorThreads());
	private static ClassLoader paraClassLoader;
	private static volatile boolean isInitialized = false;
	private static volatile boolean isHealthy = true;

	/**
	 * No-args constructor.
	 */
	private Para() { }

	static {
		addInitListener(CoreUtils.getInstance());
	}

	/**
	 * Executes all initialize listeners and prints logo. Call this method first.
	 */
	public static void initialize() {
		if (isInitialized) {
			return;
		}
		isInitialized = true;
		try {
			logger.info("--- Para.initialize() [{}] {} ---", CONF.environment(), getVersion());

			for (InitializeListener initListener : INIT_LISTENERS) {
				if (initListener != null) {
					initListener.onInitialize();
					logger.debug("Executed {}.onInitialize().", initListener.getClass().getName());
				}
			}
		} catch (Exception e) {
			logger.error("Failed to initialize Para.", e);
		}
	}

	/**
	 * Calls all registered listeners on exit. Call this method last.
	 */
	public static void destroy() {
		try {
			logger.info("--- Para.destroy() ---");
			for (DestroyListener destroyListener : DESTROY_LISTENERS) {
				if (destroyListener != null) {
					destroyListener.onDestroy();
					logger.debug("Executed {}.onDestroy().", destroyListener.getClass().getName());
				}
			}
			if (!EXECUTOR.isShutdown()) {
				EXECUTOR.shutdown();
				EXECUTOR.awaitTermination(60, TimeUnit.SECONDS);
			}
			if (!SCHEDULER.isShutdown()) {
				SCHEDULER.shutdown();
				SCHEDULER.awaitTermination(60, TimeUnit.SECONDS);
			}
		} catch (InterruptedException e) {
			logger.error("Interrupted while shutting down Para.", e);
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * @return an instance of the core persistence class.
	 * @see DAO
	 */
	public static DAO getDAO() {
		return CoreUtils.getInstance().getDao();
	}

	/**
	 * @return an instance of the core search class.
	 * @see Search
	 */
	public static Search getSearch() {
		return CoreUtils.getInstance().getSearch();
	}

	/**
	 * @return an instance of the core cache class.
	 * @see Cache
	 */
	public static Cache getCache() {
		return CoreUtils.getInstance().getCache();
	}

	/**
	 * @return an instance of the core queue class.
	 * @see Queue
	 */
	public static Queue getQueue() {
		return CoreUtils.getInstance().getQueue();
	}

	/**
	 * @return an instance of the core FileStore class.
	 * @see FileStore
	 */
	public static FileStore getFileStore() {
		return CoreUtils.getInstance().getFileStore();
	}

	/**
	 * @return an instance of the core Emailer class.
	 * @see Emailer
	 */
	public static Emailer getEmailer() {
		return CoreUtils.getInstance().getEmailer();
	}

	/**
	 * Registers a new initialization listener.
	 *
	 * @param il the listener
	 */
	public static void addInitListener(InitializeListener il) {
		if (il != null) {
			INIT_LISTENERS.add(il);
		}
	}

	/**
	 * @return a list of {@link InitializeListener}
	 */
	public static Set<InitializeListener> getInitListeners() {
		return Collections.unmodifiableSet(INIT_LISTENERS);
	}

	/**
	 * Registers a new destruction listener.
	 *
	 * @param dl the listener
	 */
	public static void addDestroyListener(DestroyListener dl) {
		if (dl != null) {
			DESTROY_LISTENERS.add(dl);
		}
	}

	/**
	 * @return a list of {@link DestroyListener}
	 */
	public static Set<DestroyListener> getDestroyListeners() {
		return Collections.unmodifiableSet(DESTROY_LISTENERS);
	}

	/**
	 * Registers a new Para I/O listener.
	 *
	 * @param iol the listener
	 */
	public static void addIOListener(IOListener iol) {
		if (iol != null) {
			IO_LISTENERS.add(iol);
		}
	}

	/**
	 * Returns a list of I/O listeners (callbacks).
	 * @return the list of registered listeners
	 */
	public static Set<IOListener> getIOListeners() {
		return Collections.unmodifiableSet(IO_LISTENERS);
	}

	/**
	 * Registers a new Para I/O listener for listening to search queries.
	 *
	 * @param iol the listener
	 */
	public static void addSearchQueryListener(IOListener iol) {
		if (iol != null) {
			SEARCH_LISTENERS.add(iol);
		}
	}

	/**
	 * Returns a list of I/O listeners for search queries.
	 * @return the list of registered listeners for listening to search queries
	 */
	public static Set<IOListener> getSearchQueryListeners() {
		return Collections.unmodifiableSet(SEARCH_LISTENERS);
	}

	/**
	 * Returns the Para executor service.
	 * @return a fixed thread executor service
	 */
	public static ExecutorService getExecutorService() {
		return EXECUTOR;
	}

	/**
	 * Returns the Para scheduled executor service.
	 * @return a scheduled executor service
	 */
	public static ScheduledExecutorService getScheduledExecutorService() {
		return SCHEDULER;
	}

	/**
	 * The health status of the server.
	 * @return true if healthy.
	 */
	public static boolean isHealthy() {
		return isHealthy;
	}

	/**
	 * Sets the health status of the server.
	 * @param isHealthy status
	 */
	public static void setHealthy(boolean isHealthy) {
		Para.isHealthy = isHealthy;
	}

	/**
	 * Executes a {@link java.lang.Runnable} asynchronously.
	 * @param runnable a task
	 */
	public static void asyncExecute(Runnable runnable) {
		if (runnable != null) {
			try {
				Para.getExecutorService().execute(runnable);
			} catch (RejectedExecutionException ex) {
				logger.warn(ex.getMessage());
				try {
					runnable.run();
				} catch (Exception e) {
					logger.error(null, e);
				}
			}
		}
	}

	/**
	 * Executes a {@link java.lang.Runnable} at a fixed interval, asynchronously.
	 * @param task a task
	 * @param delay run after
	 * @param interval run at this interval of time
	 * @param t time unit
	 * @return a Future
	 */
	public static ScheduledFuture<?> asyncExecutePeriodically(Runnable task, long delay, long interval, TimeUnit t) {
		if (task != null) {
			try {
				return Para.getScheduledExecutorService().scheduleAtFixedRate(task, delay, interval, t);
			} catch (RejectedExecutionException ex) {
				logger.warn(ex.getMessage());
			}
		}
		return null;
	}

	/**
	 * Creates a simple rate limiter.
	 * @param rateLimitPerMin rate limit per minute
	 * @param rateLimitPerHour rate limit per hour
	 * @return a new rate limiter instance
	 */
	public static RateLimiter createRateLimiter(int rateLimitPerMin, int rateLimitPerHour) {
		return new RateLimiter(rateLimitPerMin, rateLimitPerHour);
	}

	/**
	 * Creates a simple rate limiter.
	 * @param rateLimitPerMin rate limit per minute
	 * @param rateLimitPerHour rate limit per hour
	 * @param rateLimitPerDay rate limit per day
	 * @return a new rate limiter instance
	 */
	public static RateLimiter createRateLimiter(int rateLimitPerMin, int rateLimitPerHour, int rateLimitPerDay) {
		return new RateLimiter(rateLimitPerMin, rateLimitPerHour, rateLimitPerDay);
	}

	/**
	 * Try loading external {@link com.erudika.para.core.rest.CustomResourceHandler} classes.
	 * These will handle custom API requests.
	 * via {@link java.util.ServiceLoader#load(java.lang.Class)}.
	 * @return a loaded list of  ServletContextListener class.
	 */
	public static List<CustomResourceHandler> getCustomResourceHandlers() {
		ServiceLoader<CustomResourceHandler> loader = ServiceLoader.
				load(CustomResourceHandler.class, Para.getParaClassLoader());
		List<CustomResourceHandler> externalResources = new ArrayList<>();
		for (CustomResourceHandler handler : loader) {
			if (handler != null) {
				externalResources.add(handler);
			}
		}
		return externalResources;
	}

	/**
	 * Returns the {@link URLClassLoader} classloader for Para.
	 * Used for loading JAR files from 'lib/*.jar'.
	 * @return a classloader
	 */
	public static ClassLoader getParaClassLoader() {
		if (paraClassLoader == null) {
			try {
				ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
				List<URL> jars = new ArrayList<>();
				File lib = new File(CONF.pluginFolder());
				if (lib.exists() && lib.isDirectory()) {
					for (File file : lib.listFiles((dir, fname) -> fname.toLowerCase().endsWith(".jar"))) {
						jars.add(file.toURI().toURL());
					}
				}
				paraClassLoader = new URLClassLoader(jars.toArray(URL[]::new), currentClassLoader);
				// Thread.currentThread().setContextClassLoader(paraClassLoader);
			} catch (Exception e) {
				logger.error(null, e);
			}
		}
		return paraClassLoader;
	}

	/**
	 * Explicitly registers core classes for reflection.
	 * @param classes a list of classes
	 */
	public static void registerCoreClasses(Set<Class<? extends ParaObject>> classes) {
		ParaObjectUtils.registerCoreClasses(classes);
	}

	/**
	 * Creates the root application and returns the credentials for it.
	 * @return credentials for the root app
	 */
	public static Map<String, String> setup() {
		return newApp(Para.getConfig().getRootAppIdentifier(), CONF.appName(), false, false);
	}

	/**
	 * Creates a new application and returns the credentials for it.
	 * @param appid the app identifier
	 * @param name the full name of the app
	 * @param sharedTable false if the app should have its own table
	 * @param sharedIndex false if the app should have its own index
	 * @return credentials for the root app
	 */
	public static Map<String, String> newApp(String appid, String name, boolean sharedTable, boolean sharedIndex) {
		return newApp(appid, name, null, sharedTable, sharedIndex);
	}

	/**
	 * Creates a new application and returns the credentials for it.
	 * @param appid the app identifier
	 * @param name the full name of the app
	 * @param creatorid the id of the {@link com.erudika.para.core.User} who owns this app
	 * @param sharedTable false if the app should have its own table
	 * @param sharedIndex false if the app should have its own index
	 * @return credentials for the root app
	 */
	public static Map<String, String> newApp(String appid, String name, String creatorid,
			boolean sharedTable, boolean sharedIndex) {
		Map<String, String> creds = new TreeMap<>();
		creds.put("message", "All set!");
		if (StringUtils.isBlank(appid)) {
			return creds;
		}
		App app = new App(appid);
		if (!app.exists()) {
			app.setName(StringUtils.isBlank(name) ? appid : name);
			app.setSharingTable(sharedTable);
			app.setSharingIndex(sharedIndex);
			app.setCreatorid(creatorid);
			app.setActive(true);
			String id = app.create();
			if (id != null) {
				logger.info("Created {} app '{}', sharingTable = {}, sharingIndex = {}.",
						app.isRootApp() ? "root" : "new", app.getAppIdentifier(), sharedTable, sharedIndex);
				creds.putAll(app.getCredentials());
				creds.put("message", "Save these keys - they are shown only once!");
			} else {
				logger.error("Failed to create app '{}'!", appid);
				creds.put("message", "Error - app was not created.");
			}
		}
		return creds;
	}

	/**
	 * The current version of Para.
	 * @return version string, from pom.xml
	 */
	public static String getVersion() {
		return VersionInfo.getVersion();
	}

	/**
	 * The current Git revision of Para.
	 * @return revision string
	 */
	public static String getRevision() {
		return VersionInfo.getRevision();
	}

	/**
	 * @return the default Para CONF instance.
	 */
	public static ParaConfig getConfig() {
		return CONF;
	}
}
