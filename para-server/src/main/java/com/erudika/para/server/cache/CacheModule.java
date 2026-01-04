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
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Para;
import java.util.ServiceLoader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The default cache module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Configuration
public class CacheModule {

	@Bean
	public Cache getCache() {
		Cache cache;
		String selectedCache = Para.getConfig().cachePlugin();
		if (StringUtils.isBlank(selectedCache) || "inmemory".equalsIgnoreCase(selectedCache)) {
			cache = bindToDefault();
		} else {
			Cache cachePlugin = loadExternalCache(selectedCache);
			if (cachePlugin != null) {
				cache = cachePlugin;
			} else {
				// default fallback
				cache = bindToDefault();
			}
		}
		CoreUtils.getInstance().setCache(cache);
		return Para.getCache();
	}

	Cache bindToDefault() {
		return new CaffeineCache();
	}

	/**
	 * Scans the classpath for Cache implementations, through the
	 * {@link ServiceLoader} mechanism and returns one.
	 * @param classSimpleName the name of the class name to look for and load
	 * @return a Cache instance if found, or null
	 */
	final Cache loadExternalCache(String classSimpleName) {
		ServiceLoader<Cache> cacheLoader = ServiceLoader.load(Cache.class, Para.getParaClassLoader());
		for (Cache cache : cacheLoader) {
			if (cache != null && classSimpleName.equalsIgnoreCase(cache.getClass().getSimpleName())) {
				return cache;
			}
		}
		return null;
	}

}
