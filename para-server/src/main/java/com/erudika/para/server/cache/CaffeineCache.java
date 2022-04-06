/*
 * Copyright 2013-2022 Erudika. https://erudika.com
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
package com.erudika.para.server.cache;

import com.erudika.para.core.cache.Cache;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.Utils;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the {@link Cache} interface using Caffeine.
 * Multitenancy is achieved by caching objects from each app using composite keys: {@code prefix_objectId}.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class CaffeineCache implements Cache {

	private static final Logger logger = LoggerFactory.getLogger(CaffeineCache.class);
	private static final int DEFAULT_EXPIRATION_MIN = Para.getConfig().caffeineEvictAfterMin();
	private final com.github.benmanes.caffeine.cache.Cache<String, Object> cache;

	/**
	 * Default constructor.
	 */
	public CaffeineCache() {
		cache = Caffeine.newBuilder()
			.maximumSize(Para.getConfig().caffeineCacheSize())
			.expireAfter(new Expiry<String, Object>() {
				public long expireAfterCreate(String key, Object value, long currentTime) {
					return TimeUnit.MINUTES.toNanos(DEFAULT_EXPIRATION_MIN);
				}
				public long expireAfterUpdate(String key, Object value, long currentTime, long currentDuration) {
					return currentDuration;
				}
				public long expireAfterRead(String key, Object value, long currentTime, long currentDuration) {
					return currentDuration;
				}
			})
			.build();
	}

	/**
	 * @param cache cache
	 */
	CaffeineCache(com.github.benmanes.caffeine.cache.Cache<String, Object> cache) {
		this.cache = cache;
	}

	@Override
	public boolean contains(String appid, String id) {
		if (StringUtils.isBlank(id) || StringUtils.isBlank(appid)) {
			return false;
		}
		boolean exists = get(appid, id) != null;
		logger.debug("Cache.contains({}) {}", id, exists);
		return exists;
	}

	@Override
	public <T> void put(String appid, String id, T object) {
		if (!StringUtils.isBlank(id) && object != null && !StringUtils.isBlank(appid)) {
			cache.put(key(appid, id), object);
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
			String key = key(appid, id);
			cache.policy().expireVariably().ifPresent((t) -> {
				t.put(key, object, ttlSeconds, TimeUnit.SECONDS);
			});
			logger.debug("Cache.put() {} {} ttl {}", appid, id, ttlSeconds);
		}
	}

	@Override
	public <T> void putAll(String appid, Map<String, T> objects) {
		if (objects != null && !objects.isEmpty() && !StringUtils.isBlank(appid)) {
			Map<String, T> cleanMap = new LinkedHashMap<>(objects.size());
			for (Map.Entry<String, T> entry : objects.entrySet()) {
				if (!StringUtils.isBlank(entry.getKey()) && entry.getValue() != null) {
					cleanMap.put(key(appid, entry.getKey()), entry.getValue());
				}
			}
			cache.putAll(cleanMap);
			logger.debug("Cache.putAll() {} {}", appid, objects.size());
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T get(String appid, String id) {
		if (StringUtils.isBlank(id) || StringUtils.isBlank(appid)) {
			return null;
		}
		String key = key(appid, id);
		logger.debug("Cache.get() {} {}", appid, id);
		return (T) cache.getIfPresent(key);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Map<String, T> getAll(String appid, List<String> ids) {
		if (ids == null || StringUtils.isBlank(appid)) {
			return Collections.emptyMap();
		}
		Map<String, T> map1 = new LinkedHashMap<>(ids.size());
		ids.remove(null);
		for (String id : ids) {
			T t = get(appid, id);
			if (t != null) {
				map1.put(id, t);
			}
		}
		logger.debug("Cache.getAll() {} {}", appid, ids.size());
		return map1;
	}

	@Override
	public void remove(String appid, String id) {
		if (!StringUtils.isBlank(id) && !StringUtils.isBlank(appid)) {
			logger.debug("Cache.remove() {} {}", appid, id);
			cache.invalidate(key(appid, id));
		}
	}

	@Override
	public void removeAll(String appid) {
		if (!StringUtils.isBlank(appid)) {
			logger.debug("Cache.removeAll() {}", appid);
			cache.asMap().remove("key_prefix_" + appid);
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

	private String key(String appid, String id) {
		return cache.asMap().computeIfAbsent("key_prefix_" + appid, k -> Utils.getNewId()) + "_" + id;
	}

	////////////////////////////////////////////////////

	@Override
	public boolean contains(String id) {
		return contains(Para.getConfig().getRootAppIdentifier(), id);
	}

	@Override
	public <T> void put(String id, T object) {
		put(Para.getConfig().getRootAppIdentifier(), id, object);
	}

	@Override
	public <T> void putAll(Map<String, T> objects) {
		putAll(Para.getConfig().getRootAppIdentifier(), objects);
	}

	@Override
	public <T> T get(String id) {
		return get(Para.getConfig().getRootAppIdentifier(), id);
	}

	@Override
	public <T> Map<String, T> getAll(List<String> ids) {
		return getAll(Para.getConfig().getRootAppIdentifier(), ids);
	}

	@Override
	public void remove(String id) {
		remove(Para.getConfig().getRootAppIdentifier(), id);
	}

	@Override
	public void removeAll() {
		removeAll(Para.getConfig().getRootAppIdentifier());
	}

	@Override
	public void removeAll(List<String> ids) {
		removeAll(Para.getConfig().getRootAppIdentifier(), ids);
	}

}
