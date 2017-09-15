/*
 * Copyright 2013-2017 Erudika. https://erudika.com
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
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import java.util.Collections;
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
 * Hazelcast implementation of the {@link Cache} interface.
 * Each application uses a separate distributed map.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 * @see Cache
 * @see HazelcastUtils
 */
@Singleton
public class HazelcastCache implements Cache {

	private static final Logger logger = LoggerFactory.getLogger(HazelcastCache.class);

	static {
		if (Config.isCacheEnabled()) {
			HazelcastUtils.getClient();
		}
	}

	/**
	 * No-args constructor.
	 */
	public HazelcastCache() {
	}

	HazelcastInstance client() {
		return HazelcastUtils.getClient();
	}

	@Override
	public boolean contains(String appid, String id) {
		if (StringUtils.isBlank(id) || StringUtils.isBlank(appid)) {
			return false;
		}
		try {
			return client().getMap(appid).containsKey(id);
		} catch (Exception e) {
			logger.error(null, e);
			return false;
		}
	}

	@Override
	public <T> void put(String appid, String id, T object) {
		if (!StringUtils.isBlank(id) && object != null && !StringUtils.isBlank(appid)) {
			logger.debug("Cache.put() {} {}", appid, id);
			try {
				if (isAsyncEnabled()) {
					client().getMap(appid).putAsync(id, object);
				} else {
					client().getMap(appid).put(id, object);
				}
			} catch (Exception e) {
				logger.error(null, e);
			}
		}
	}

	@Override
	public <T> void put(String appid, String id, T object, Long ttlSeconds) {
		if (!StringUtils.isBlank(id) && object != null && !StringUtils.isBlank(appid)) {
			logger.debug("Cache.put() {} {} ttl {}", appid, id, ttlSeconds);
			try {
				if (isAsyncEnabled()) {
					client().getMap(appid).putAsync(id, object, ttlSeconds, TimeUnit.SECONDS);
				} else {
					client().getMap(appid).put(id, object, ttlSeconds, TimeUnit.SECONDS);
				}
			} catch (Exception e) {
				logger.error(null, e);
			}
		}
	}

	@Override
	public <T> void putAll(String appid, Map<String, T> objects) {
		if (objects != null && !objects.isEmpty() && !StringUtils.isBlank(appid)) {
			Map<String, T> cleanMap = new LinkedHashMap<>(objects.size());
			for (Entry<String, T> entry : objects.entrySet()) {
				if (!StringUtils.isBlank(entry.getKey()) && entry.getValue() != null) {
					cleanMap.put(entry.getKey(), entry.getValue());
				}
			}
			try {
				logger.debug("Cache.putAll() {} {}", appid, objects.size());
				client().getMap(appid).putAll(cleanMap);
			} catch (Exception e) {
				logger.error(null, e);
			}
		}
	}

	@Override
	public <T> T get(String appid, String id) {
		if (StringUtils.isBlank(id) || StringUtils.isBlank(appid)) {
			return null;
		}
		try {
			Map<String, T> map = client().getMap(appid);
			T obj = map.get(id);
			logger.debug("Cache.get() {} {}", appid, (obj == null) ? null : id);
			return obj;
		} catch (Exception e) {
			logger.error(null, e);
			return null;
		}
	}

	@Override
	public <T> Map<String, T> getAll(String appid, List<String> ids) {
		if (ids == null || StringUtils.isBlank(appid)) {
			return Collections.emptyMap();
		}
		Map<String, T> result = new LinkedHashMap<>(ids.size(), 0.75f, true);
		ids.remove(null);
		try {
			IMap<String, T> imap = client().getMap(appid);
			Map<String, T> res = imap.getAll(new TreeSet<>(ids));
			for (String id : ids) {
				if (res.containsKey(id)) {
					result.put(id, res.get(id));
				}
			}
			logger.debug("Cache.getAll() {} {}", appid, ids.size());
		} catch (Exception e) {
			logger.error(null, e);
		}
		return result;
	}

	@Override
	public void remove(String appid, String id) {
		if (!StringUtils.isBlank(id) && !StringUtils.isBlank(appid)) {
			try {
				logger.debug("Cache.remove() {} {}", appid, id);
				client().getMap(appid).delete(id);
			} catch (Exception e) {
				logger.error(null, e);
			}
		}
	}

	@Override
	public void removeAll(String appid) {
		if (!StringUtils.isBlank(appid)) {
			try {
				logger.debug("Cache.removeAll() {}", appid);
				client().getMap(appid).clear();
			} catch (Exception e) {
				logger.error(null, e);
			}
		}
	}

	@Override
	public void removeAll(String appid, List<String> ids) {
		if (ids != null && !StringUtils.isBlank(appid)) {
			try {
				IMap<?, ?> map = client().getMap(appid);
				for (String id : ids) {
					if (!StringUtils.isBlank(id)) {
						map.delete(id);
					}
				}
				logger.debug("Cache.removeAll() {} {}", appid, ids.size());
			} catch (Exception e) {
				logger.error(null, e);
			}
		}
	}

	/**
	 * @return true if asynchronous caching is enabled.
	 */
	private boolean isAsyncEnabled() {
		return Config.getConfigBoolean("hc.async_enabled", false);
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
