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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
@Singleton
public class MockCache implements Cache {
	
	private Map<String, Map<String, Object>> maps = new HashMap<String, Map<String, Object>>();
	
	@Override
	public boolean contains(String appName, String id) {
		if(StringUtils.isBlank(id) || StringUtils.isBlank(appName)) return false;
		if(isExpired((Long) getMap(appName).get(id+":ttl"))){
			remove(appName, id);
			return false;
		}else{
			return getMap(appName).containsKey(id);
		}
	}
	
	@Override
	public <T> void put(String appName, String id, T object) {
		if(StringUtils.isBlank(id) || object == null || StringUtils.isBlank(appName)) return;
		getMap(appName).put(id, object);
	}

	@Override
	public <T> void put(String appName, String id, T object, Long ttl_seconds) {
		if(StringUtils.isBlank(id) || object == null || StringUtils.isBlank(appName)) return;
		getMap(appName).put(id, object);
		getMap(appName).put(id+":ttl", System.currentTimeMillis() + ttl_seconds*1000);
	}

	@Override
	public <T> void putAll(String appName, Map<String, T> objects) {
		if(objects == null || objects.isEmpty() || StringUtils.isBlank(appName)) return;
		objects.remove(null);
		objects.remove("");
		getMap(appName).putAll(objects);
	}

	@Override
	public <T> T get(String appName, String id) {
		if(StringUtils.isBlank(id) || StringUtils.isBlank(appName)) return null;
		if(isExpired((Long) getMap(appName).get(id+":ttl"))){
			remove(appName, id);
			return null;
		}else{
			return (T) getMap(appName).get(id);
		}
	}

	@Override
	public <T> Map<String, T> getAll(String appName, List<String> ids) {
		Map<String, T> map1 = new TreeMap<String, T>();
		if(ids == null || StringUtils.isBlank(appName)) return map1;
		ids.remove(null);
		for (String id : ids) {
			if(!isExpired((Long) getMap(appName).get(id+":ttl"))){
				T t = (T) getMap(appName).get(id);
				if(t != null) map1.put(id, t);
			}else{
				remove(appName, id);
			}
		}
		return map1;
	}
	
	@Override
	public void remove(String appName, String id) {
		if(StringUtils.isBlank(id) || StringUtils.isBlank(appName)) return;
		getMap(appName).remove(id);
	}

	@Override
	public void removeAll(String appName) {
		if(StringUtils.isBlank(appName)) return;
		getMap(appName).clear();
	}
	
	@Override
	public void removeAll(String appName, List<String> ids) {
		if(ids == null || StringUtils.isBlank(appName)) return;
		for (String id : ids) {
			remove(appName, id);
		}
	}
	
	private boolean isExpired(Long ttl){
		if(ttl == null) return false;
		return System.currentTimeMillis() > ttl;
	}
	
	private Map<String, Object> getMap(String appName){
		if(!maps.containsKey(appName)){
			maps.put(appName, new  HashMap<String, Object>());
		}
		return maps.get(appName);
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
