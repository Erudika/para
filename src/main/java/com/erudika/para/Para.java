/*
 * Copyright 2013 Alex Bogdanovski <alex@erudika.com>.
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
 * You can reach the author at: https://github.com/albogdano
 */
package com.erudika.para;

import com.erudika.para.cache.CacheModule;
import com.erudika.para.email.EmailModule;
import com.erudika.para.i18n.I18nModule;
import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.PersistenceModule;
import com.erudika.para.queue.QueueModule;
import com.erudika.para.search.Search;
import com.erudika.para.search.SearchModule;
import com.erudika.para.security.SecurityModule;
import com.erudika.para.storage.StorageModule;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.aop.AOPModule;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class Para {
	
	private static final Logger logger = LoggerFactory.getLogger(Para.class);
	private static List<DestroyListener> destroyListeners = new ArrayList<DestroyListener>();
	private static List<InitializeListener> initListeners = new ArrayList<InitializeListener>();
	
	private static Injector injector;
	
	public static void initialize(Module... modules){
		if(injector == null){
			try {
				Stage stage = Config.IN_PRODUCTION ? Stage.PRODUCTION : Stage.DEVELOPMENT;
				List<Module> coreModules = getCoreModules();
				List<Module> externalModules = getExternalModules(modules);
				
				if(!externalModules.isEmpty()){
					injector = Guice.createInjector(stage, Modules.override(coreModules).with(externalModules));
				}else{
					injector = Guice.createInjector(stage, coreModules);
				}
				
				for (InitializeListener destroyListener : initListeners) {
					destroyListener.onInitialize();
				}
			} catch (Exception e) {
				logger.error(null, e);
			}
		}
	}
	
	public static void destroy(){
		try {
			for (DestroyListener destroyListener : destroyListeners) {
				destroyListener.onDestroy();
			}
		}catch (Exception e) {
			logger.error(null, e);
		}
	}
	
	public static void injectInto(Object obj) {
		if (obj == null) return;
		if (injector == null) handleNotInitializedError();
		injector.injectMembers(obj);
	}
	
	public static DAO getDAO(){
		if (injector == null) handleNotInitializedError();
		return injector.getInstance(DAO.class);
	}
	
	public static Search getSearch(){
		if (injector == null) handleNotInitializedError();
		return injector.getInstance(Search.class);
	}
	
	public static Map<String, String> getConfig(){
		return Config.getConfigMap();
	}
	
	public static void addInitListener(InitializeListener il){
		initListeners.add(il);
	}

	public static void addDestroyListener(DestroyListener dl){
		destroyListeners.add(dl);
	}
	
	public static interface InitializeListener extends EventListener {
		public abstract void onInitialize();
	}
	
	public static interface DestroyListener extends EventListener {
		public abstract void onDestroy();
	}
		
	private static List<Module> getCoreModules(){
		List<Module> coreModules = new ArrayList<Module>();
		coreModules.add(new AOPModule());
		coreModules.add(new CacheModule());
		coreModules.add(new EmailModule());
		coreModules.add(new I18nModule());
		coreModules.add(new PersistenceModule());
		coreModules.add(new QueueModule());
		coreModules.add(new SearchModule());
		coreModules.add(new SecurityModule());
		coreModules.add(new StorageModule());
		return coreModules;
	}
	
	private static List<Module> getExternalModules(Module... modules){
		ServiceLoader<Module> moduleLoader = ServiceLoader.load(Module.class);
		List<Module> externalModules = new ArrayList<Module>();
		for (Module module : moduleLoader) {
			externalModules.add(module);
		}
		if (modules != null && modules.length > 0) {
			externalModules.addAll(Arrays.asList(modules));
		}
		return externalModules;
	}
	
	private static void handleNotInitializedError(){
		throw new IllegalStateException("Call Para.initialize() first!");
	}
}
