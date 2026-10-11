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
package com.erudika.para.server.cache;

import com.erudika.para.core.cache.Cache;
import com.erudika.para.core.utils.Para;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the {@link Cache} interface using Caffeine.
 * Multitenancy uses collision-safe composite keys. Default entries expire after a period without access;
 * entries with an explicit TTL keep that fixed lifetime.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class CaffeineCache implements Cache {

	private static final Logger logger = LoggerFactory.getLogger(CaffeineCache.class);
	private final com.github.benmanes.caffeine.cache.Cache<String, Object> cache;

	/**
	 * Default constructor.
	 */
	public CaffeineCache() {
		this(Para.getConfig().caffeineCacheSize(),
				Duration.ofMinutes(Para.getConfig().caffeineEvictAfterMin()), Ticker.systemTicker());
	}

	/**
	 * Creates a cache with explicit limits. This constructor supports tests with a controllable ticker.
	 * @param maximumSize maximum number of entries
	 * @param defaultExpiration default idle time before an entry expires
	 * @param ticker ticker used to measure time
	 */
	CaffeineCache(long maximumSize, Duration defaultExpiration, Ticker ticker) {
		long defaultExpirationNanos = defaultExpiration.toNanos();
		cache = Caffeine.newBuilder()
				.maximumSize(maximumSize)
				.ticker(ticker)
				.expireAfter(new Expiry<String, Object>() {
					@Override
					public long expireAfterCreate(String key, Object value, long currentTime) {
						return expirationFor(value, defaultExpirationNanos);
					}

					@Override
					public long expireAfterUpdate(String key, Object value, long currentTime, long currentDuration) {
						return expirationFor(value, defaultExpirationNanos);
					}

					@Override
					public long expireAfterRead(String key, Object value, long currentTime, long currentDuration) {
						if (value instanceof CacheEntry entry && entry.ttlNanos > 0L) {
							// Explicit TTL values keep their original deadline.
							return currentDuration;
						}
						// A read refreshes the idle timeout for normal cached objects.
						return defaultExpirationNanos;
					}
				})
				.build();
	}

	private static long expirationFor(Object value, long defaultExpirationNanos) {
		if (value instanceof CacheEntry entry && entry.ttlNanos > 0L) {
			return entry.ttlNanos;
		}
		return defaultExpirationNanos;
	}

	private static final class CacheEntry {
		private final Object value;
		private final long ttlNanos;

		private CacheEntry(Object value, long ttlNanos) {
			this.value = value;
			this.ttlNanos = ttlNanos;
		}
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
			cache.put(key(appid, id), new CacheEntry(object, 0L));
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
			cache.put(key, new CacheEntry(object, TimeUnit.SECONDS.toNanos(ttlSeconds)));
			logger.debug("Cache.put() {} {} ttl {}", appid, id, ttlSeconds);
		}
	}

	@Override
	public <T> void putAll(String appid, Map<String, T> objects) {
		if (objects != null && !objects.isEmpty() && !StringUtils.isBlank(appid)) {
			Map<String, Object> cleanMap = new LinkedHashMap<>(objects.size());
			for (Map.Entry<String, T> entry : objects.entrySet()) {
				if (!StringUtils.isBlank(entry.getKey()) && entry.getValue() != null) {
					cleanMap.put(key(appid, entry.getKey()), new CacheEntry(entry.getValue(), 0L));
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
		CacheEntry entry = (CacheEntry) cache.getIfPresent(key);
		return entry == null ? null : (T) entry.value;
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
			String prefix = keyPrefix(appid);
			cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
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
		return keyPrefix(appid) + id;
	}

	private String keyPrefix(String appid) {
		// Length-prefix the app id so separators inside either value cannot cause key collisions.
		return appid.length() + ":" + appid;
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
