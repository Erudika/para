/*
 * Copyright 2013-2016 Erudika. http://erudika.com
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
package com.erudika.para;

import com.erudika.para.cache.Cache;
import com.erudika.para.persistence.DAO;
import com.erudika.para.rest.CustomResourceHandler;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.util.Modules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventListener;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main utility class and entry point.
 * Dependency injection is initialized with the provided modules.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class Para {

	public static final String LOGO;
	static {
		String[] logo = {"",
			"      ____  ___ _ ____ ___ _ ",
			"     / __ \\/ __` / ___/ __` /",
			"    / /_/ / /_/ / /  / /_/ / ",
			"   / .___/\\__,_/_/   \\__,_/  ",
			"  /_/                        ", ""};
		StringBuilder sb = new StringBuilder();
		for (String line : logo) {
			sb.append(line).append("\n");
		}
		LOGO = sb.toString();
	}

	private static final Logger logger = LoggerFactory.getLogger(Para.class);
	private static final List<DestroyListener> destroyListeners = new ArrayList<DestroyListener>();
	private static final List<InitializeListener> initListeners = new ArrayList<InitializeListener>();
	private static final ExecutorService exec = Executors.newFixedThreadPool(Config.EXECUTOR_THREADS);
	private static Injector injector;

	public Para() { }

	/**
	 * Initializes the Para core modules and allows the user to override them. Call this method first.
	 *
	 * @param modules a list of modules that override the main modules
	 */
	public static void initialize(Module... modules) {
		if (injector == null) {
			printLogo();
			try {
				logger.info("--- Para.initialize() [{}] ---", Config.ENVIRONMENT);
				Stage stage = Config.IN_PRODUCTION ? Stage.PRODUCTION : Stage.DEVELOPMENT;

				List<Module> coreModules = Arrays.asList(modules);
				List<Module> externalModules = getExternalModules();

				if (coreModules.isEmpty() && externalModules.isEmpty()) {
					logger.warn("No implementing modules found. Aborting...");
					destroy();
					return;
				}

				if (!externalModules.isEmpty()) {
					injector = Guice.createInjector(stage, Modules.override(coreModules).with(externalModules));
				} else {
					injector = Guice.createInjector(stage, coreModules);
				}

				for (InitializeListener initListener : initListeners) {
					initListener.onInitialize();
				}
			} catch (Exception e) {
				logger.error(null, e);
			}
		}
	}

	/**
	 * Calls all registered listeners on exit. Call this method last.
	 */
	public static void destroy() {
		try {
			if (injector != null) {
				logger.info("--- Para.destroy() ---");
				injector = null;
				for (DestroyListener destroyListener : destroyListeners) {
					destroyListener.onDestroy();
				}
			}
			if (!exec.isShutdown()) {
				exec.shutdown();
				exec.awaitTermination(60, TimeUnit.SECONDS);
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	/**
	 * Inject dependencies into a given object.
	 *
	 * @param obj the object we inject into
	 */
	public static void injectInto(Object obj) {
		if (obj == null) {
			return;
		}
		if (injector == null) {
			handleNotInitializedError();
		}
		injector.injectMembers(obj);
	}

	/**
	 * Return an instance of some class if it has been wired through DI.
	 * @param <T> any type
	 * @param type any type
	 * @return an object
	 */
	public static <T> T getInstance(Class<T> type) {
		if (injector == null) {
			handleNotInitializedError();
		}
		return injector.getInstance(type);
	}

	/**
	 * @return an instance of the core persistence class.
	 * @see DAO
	 */
	public static DAO getDAO() {
		return getInstance(DAO.class);
	}

	/**
	 * @return an instance of the core search class.
	 * @see Search
	 */
	public static Search getSearch() {
		return getInstance(Search.class);
	}

	/**
	 * @return an instance of the core cache class.
	 * @see Cache
	 */
	public static Cache getCache() {
		return getInstance(Cache.class);
	}

	/**
	 * @return an instance of the configuration map.
	 * @see Config
	 */
	public static Map<String, String> getConfig() {
		return Config.getConfigMap();
	}

	/**
	 * Registers a new initialization listener.
	 *
	 * @param il the listener
	 */
	public static void addInitListener(InitializeListener il) {
		initListeners.add(il);
	}

	/**
	 * Registers a new destruction listener.
	 *
	 * @param dl the listener
	 */
	public static void addDestroyListener(DestroyListener dl) {
		destroyListeners.add(dl);
	}

	/**
	 * Returns the Para executor service
	 * @return a fixed thread executor service
	 */
	public static ExecutorService getExecutorService() {
		return exec;
	}

	/**
	 * Executes a {@link java.lang.Runnable} asynchronously
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
	 * This listener is exectuted when the Para runtime starts.
	 */
	public interface InitializeListener extends EventListener {

		/**
		 * Code to execute right after initialization.
		 */
		void onInitialize();
	}

	/**
	 * This listener is exectuted when the Para runtime stops.
	 */
	public interface DestroyListener extends EventListener {

		/**
		 * Code to execute on exit.
		 */
		void onDestroy();
	}

	/**
	 * Try loading external {@link com.erudika.para.rest.CustomResourceHandler} classes.
	 * These will handle custom API requests.
	 * via {@link java.util.ServiceLoader#load(java.lang.Class)}.
	 * @return a loaded list of  ServletContextListener class.
	 */
	public static List<CustomResourceHandler> getCustomResourceHandlers() {
		ServiceLoader<CustomResourceHandler> loader = ServiceLoader.load(CustomResourceHandler.class);
		List<CustomResourceHandler> externalResources = new ArrayList<CustomResourceHandler>();
		for (CustomResourceHandler handler : loader) {
			if (handler != null) {
				injectInto(handler);
				externalResources.add(handler);
			}
		}
		return externalResources;
	}

	private static List<Module> getExternalModules() {
		ServiceLoader<Module> moduleLoader = ServiceLoader.load(Module.class);
		List<Module> externalModules = new ArrayList<Module>();
		for (Module module : moduleLoader) {
			externalModules.add(module);
		}
		return externalModules;
	}

	private static void handleNotInitializedError() {
		throw new IllegalStateException("Call Para.initialize() first!");
	}

	/**
	 * Prints the Para logo to System.out.
	 */
	public static void printLogo() {
		if (Boolean.parseBoolean(Config.getConfigParam("print_logo", "true"))) {
			System.out.print(LOGO);
		}
	}

}
