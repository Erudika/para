/*
 * Copyright 2013 Alex Bogdanovski <albogdano@me.com>.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class MockCache implements Cache {
	
	private Map<String, Object> map = new HashMap<String, Object>();
		
	@Override
	public boolean contains(String id) {
		if(StringUtils.isBlank(id)) return false;
		if(isExpired((Long) map.get(id+":ttl"))){
			remove(id);
			return false;
		}else{
			return map.containsKey(id);
		}
	}
	
	@Override
	public <T> void put(String id, T object) {
		if(StringUtils.isBlank(id) || object == null) return;
		map.put(id, object);
	}

	@Override
	public <T> void put(String id, T object, Long ttl_seconds) {
		if(StringUtils.isBlank(id) || object == null) return;
		map.put(id, object);
		map.put(id+":ttl", System.currentTimeMillis() + ttl_seconds*1000);
		
	}

	@Override
	public <T> void putAll(Map<String, T> objects) {
		if(objects == null || objects.isEmpty()) return;
		objects.remove(null);
		objects.remove("");
		map.putAll(objects);
	}

	@Override
	public <T> T get(String id) {
		if(StringUtils.isBlank(id)) return null;
		if(isExpired((Long) map.get(id+":ttl"))){
			remove(id);
			return null;
		}else{
			return (T) map.get(id);
		}
	}

	@Override
	public <T> Map<String, T> getAll(List<String> ids) {
		Map<String, T> map1 = new TreeMap<String, T>();
		if(ids == null) return map1;
		ids.remove(null);
		for (String id : ids) {
			if(!isExpired((Long) map.get(id+":ttl"))){
				T t = (T) map.get(id);
				if(t != null) map1.put(id, t);
			}else{
				remove(id);
			}
		}
		return map1;
	}
	
	@Override
	public void remove(String id) {
		if(StringUtils.isBlank(id)) return;
		map.remove(id);
	}

	@Override
	public void removeAll() {
		map.clear();
	}
	
	@Override
	public void removeAll(List<String> ids) {
		if(ids == null) return;
		for (String id : ids) {
			remove(id);
		}
	}
	
	private boolean isExpired(Long ttl){
		if(ttl == null) return false;
		return System.currentTimeMillis() > ttl;
	}
}
