/*
 * Copyright 2013-2020 Erudika. https://erudika.com
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
package com.erudika.para.cache;

import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class MockCache implements Cache {

	private static final Logger logger = LoggerFactory.getLogger(MockCache.class);
	private Map<String, Map<String, Object>> maps = new ConcurrentHashMap<>();

	@Override
	public boolean contains(String appid, String id) {
		if (StringUtils.isBlank(id) || StringUtils.isBlank(appid)) {
			return false;
		}
		if (isExpired((Long) getMap(appid).get(id + ":ttl"))) {
			remove(appid, id);
			return false;
		} else {
			return getMap(appid).containsKey(id);
		}
	}

	@Override
	public <T> void put(String appid, String id, T object) {
		if (!StringUtils.isBlank(id) && object != null && !StringUtils.isBlank(appid)) {
			getMap(appid).put(id, object);
			logger.debug("Cache.put() {} {}", appid, id);
		}
	}

	@Override
	public <T> void put(String appid, String id, T object, Long ttlSeconds) {
		if (ttlSeconds == null || ttlSeconds <= 0L) {
			put(appid, id, object);
			return;
		}
		if (!StringUtils.isBlank(id) && object != null && !StringUtils.isBlank(appid)) {
			getMap(appid).put(id, object);
			getMap(appid).put(id + ":ttl", Utils.timestamp() + ttlSeconds * 1000);
			logger.debug("Cache.put() {} {} ttl {}", appid, id, ttlSeconds);
		}
	}

	@Override
	public <T> void putAll(String appid, Map<String, T> objects) {
		if (objects != null && !objects.isEmpty() && !StringUtils.isBlank(appid)) {
			Map<String, T> cleanMap = new LinkedHashMap<>(objects.size());
			for (Map.Entry<String, T> entry : objects.entrySet()) {
				if (!StringUtils.isBlank(entry.getKey()) && entry.getValue() != null) {
					cleanMap.put(entry.getKey(), entry.getValue());
				}
			}
			getMap(appid).putAll(cleanMap);
			logger.debug("Cache.putAll() {} {}", appid, objects.size());
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T get(String appid, String id) {
		if (StringUtils.isBlank(id) || StringUtils.isBlank(appid)) {
			return null;
		}
		if (isExpired((Long) getMap(appid).get(id + ":ttl"))) {
			remove(appid, id);
			logger.debug("Cache.get() {}", appid);
			return null;
		} else {
			logger.debug("Cache.get() {} {}", appid, id);
			return (T) getMap(appid).get(id);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Map<String, T> getAll(String appid, List<String> ids) {
		Map<String, T> map1 = new LinkedHashMap<>();
		if (ids == null || StringUtils.isBlank(appid)) {
			return map1;
		}
		ids.remove(null);
		for (String id : ids) {
			if (!isExpired((Long) getMap(appid).get(id + ":ttl"))) {
				T t = (T) getMap(appid).get(id);
				if (t != null) {
					map1.put(id, t);
				}
			} else {
				remove(appid, id);
			}
		}
		logger.debug("Cache.getAll() {} {}", appid, ids.size());
		return map1;
	}

	@Override
	public void remove(String appid, String id) {
		if (!StringUtils.isBlank(id) && !StringUtils.isBlank(appid)) {
			logger.debug("Cache.remove() {} {}", appid, id);
			getMap(appid).remove(id);
		}
	}

	@Override
	public void removeAll(String appid) {
		if (!StringUtils.isBlank(appid)) {
			logger.debug("Cache.removeAll() {}", appid);
			getMap(appid).clear();
		}
	}

	@Override
	public void removeAll(String appid, List<String> ids) {
		if (ids != null && !StringUtils.isBlank(appid)) {
			for (String id : ids) {
				if (!StringUtils.isBlank(id)) {
					remove(appid, id);
				}
			}
			logger.debug("Cache.removeAll() {} {}", appid, ids.size());
		}
	}

	private boolean isExpired(Long ttl) {
		if (ttl == null) {
			return false;
		}
		return Utils.timestamp() > ttl;
	}

	private Map<String, Object> getMap(String appid) {
		if (!maps.containsKey(appid)) {
			maps.put(appid, new  HashMap<>());
		}
		return maps.get(appid);
	}

	////////////////////////////////////////////////////

	@Override
	public boolean contains(String id) {
		return contains(Config.getRootAppIdentifier(), id);
	}

	@Override
	public <T> void put(String id, T object) {
		put(Config.getRootAppIdentifier(), id, object);
	}

	@Override
	public <T> void putAll(Map<String, T> objects) {
		putAll(Config.getRootAppIdentifier(), objects);
	}

	@Override
	public <T> T get(String id) {
		return get(Config.getRootAppIdentifier(), id);
	}

	@Override
	public <T> Map<String, T> getAll(List<String> ids) {
		return getAll(Config.getRootAppIdentifier(), ids);
	}

	@Override
	public void remove(String id) {
		remove(Config.getRootAppIdentifier(), id);
	}

	@Override
	public void removeAll() {
		removeAll(Config.getRootAppIdentifier());
	}

	@Override
	public void removeAll(List<String> ids) {
		removeAll(Config.getRootAppIdentifier(), ids);
	}

}
