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
package com.erudika.para.cache;

import com.erudika.para.utils.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
@Singleton
public class HazelcastCache implements Cache {

	private static final Logger logger = LoggerFactory.getLogger(HazelcastCache.class);
	private HazelcastInstance hcInstance;

	public HazelcastCache() {
	}
	
	HazelcastInstance client(){
		if(hcInstance == null){
			hcInstance = HazelcastUtils.getClient();
		}		
		return hcInstance;
	}

	@Override
	public boolean contains(String appName, String id) {
		if(StringUtils.isBlank(id) || StringUtils.isBlank(appName)) return false;
		return client().getMap(appName).containsKey(id);
	}
	
	@Override
	public <T> void put(String appName, String id, T object) {
		if(StringUtils.isBlank(id) || object == null || StringUtils.isBlank(appName)) return;
		logger.debug("Cache.put() {} {}", appName, id);
		client().getMap(appName).putAsync(id, object);
	}

	@Override
	public <T> void put(String appName, String id, T object, Long ttl_seconds) {
		if(StringUtils.isBlank(id) || object == null || StringUtils.isBlank(appName)) return;
		logger.debug("Cache.put() {} {} ttl {}", appName, id, ttl_seconds);	
		client().getMap(appName).putAsync(id, object, ttl_seconds, TimeUnit.SECONDS);
	}

	@Override
	public <T> void putAll(String appName, Map<String, T> objects) {
		if(objects == null || objects.isEmpty() || StringUtils.isBlank(appName)) return;
		objects.remove(null);
		objects.remove("");
		logger.debug("Cache.putAll() {} {}", appName, objects.size());		
		client().getMap(appName).putAll(objects);
	}
	
	@Override
	public <T> T get(String appName, String id) {
		if(StringUtils.isBlank(id) || StringUtils.isBlank(appName)) return null;
		T obj = (T) client().getMap(appName).get(id); 
		logger.debug("Cache.get() {} {}", appName, (obj == null) ? null : id);
		return obj;
	}

	@Override
	public <T> Map<String, T> getAll(String appName, List<String> ids) {
		Map<String, T> map = new LinkedHashMap<String, T>();
		if(ids == null || StringUtils.isBlank(appName)) return map;
		ids.remove(null);
		for (Entry<Object, Object> entry : client().getMap(appName).getAll(new TreeSet<Object>(ids)).entrySet()) {
			map.put((String) entry.getKey(), (T) entry.getValue());
		}
		logger.debug("Cache.getAll() {} {}", appName, ids.size());
		return map;
	}
	
	@Override
	public void remove(String appName, String id) {
		if(StringUtils.isBlank(id) || StringUtils.isBlank(appName)) return;
		logger.debug("Cache.remove() {} {}", appName, id);		
		client().getMap(appName).delete(id);
	}

	@Override
	public void removeAll(String appName) {
		if(StringUtils.isBlank(appName)) return;
		logger.debug("Cache.removeAll() {}", appName);
		client().getMap(appName).clear();
	}
	
	@Override
	public void removeAll(String appName, List<String> ids) {
		if(ids == null || StringUtils.isBlank(appName)) return;
		IMap<?,?> map = client().getMap(appName);
		for (String id : ids) {
			map.delete(id);
		}
		logger.debug("Cache.removeAll() {} {}", appName, ids.size());
	}
	
	////////////////////////////////////////////////////
	
	@Override
	public boolean contains(String id) {
		return contains(Config.APP_NAME_NS, id);
	}

	@Override
	public <T> void put(String id, T object) {
		put(Config.APP_NAME_NS, id, object);
	}
	
	@Override
	public <T> void putAll(Map<String, T> objects) {
		putAll(Config.APP_NAME_NS, objects);
	}
	
	@Override
	public <T> T get(String id) {
		return get(Config.APP_NAME_NS, id);
	}
	
	@Override
	public <T> Map<String, T> getAll(List<String> ids) {
		return getAll(Config.APP_NAME_NS, ids);
	}
	
	@Override
	public void remove(String id) {
		remove(Config.APP_NAME_NS, id);
	}
	
	@Override
	public void removeAll() {
		removeAll(Config.APP_NAME_NS);
	}
	
	@Override
	public void removeAll(List<String> ids) {
		removeAll(Config.APP_NAME_NS, ids);
	}
	
}
