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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class CaffeineCacheTest extends CacheTest {

	public CaffeineCacheTest() {
		super(new CaffeineCache());
	}

	@Test
	public void testVariableExpiration() {
		FakeTicker ticker = new FakeTicker();
		com.github.benmanes.caffeine.cache.Cache<String, Object> caffeine = Caffeine.newBuilder()
				.expireAfter(new Expiry<String, Object>() {
					public long expireAfterCreate(String key, Object value, long currentTime) {
						return TimeUnit.MINUTES.toNanos(10);
					}
					public long expireAfterUpdate(String key, Object value, long currentTime, long currentDuration) {
						return currentDuration;
					}
					public long expireAfterRead(String key, Object value, long currentTime, long currentDuration) {
						return currentDuration;
					}
				}) // default expiration
				.executor(Runnable::run)
				.ticker(ticker::read)
				.maximumSize(10)
				.build();

		CaffeineCache cache = new CaffeineCache(caffeine);
		cache.put("app", "exp1", "w", 10L);
		cache.put("app", "exp2", "x", 20L);
		cache.put("app", "exp3", "y", 30L);
		cache.put("app", "exp4", "z"); // default expiration

		assertNotNull(cache.get("app", "exp1"));
		assertNotNull(cache.get("app", "exp2"));
		assertNotNull(cache.get("app", "exp3"));
		assertNotNull(cache.get("app", "exp4"));

		ticker.advance(5, TimeUnit.SECONDS);
		assertNotNull(cache.get("app", "exp1"));
		assertNotNull(cache.get("app", "exp2"));
		assertNotNull(cache.get("app", "exp3"));
		assertNotNull(cache.get("app", "exp4"));

		ticker.advance(5, TimeUnit.SECONDS);
		assertNull(cache.get("app", "exp1"));
		assertNotNull(cache.get("app", "exp2"));
		assertNotNull(cache.get("app", "exp3"));
		assertNotNull(cache.get("app", "exp4"));

		ticker.advance(10, TimeUnit.SECONDS);
		assertNull(cache.get("app", "exp1"));
		assertNull(cache.get("app", "exp2"));
		assertNotNull(cache.get("app", "exp3"));
		assertNotNull(cache.get("app", "exp4"));

		ticker.advance(10, TimeUnit.SECONDS);
		assertNull(cache.get("app", "exp1"));
		assertNull(cache.get("app", "exp2"));
		assertNull(cache.get("app", "exp3"));
		assertNotNull(cache.get("app", "exp4"));

		ticker.advance(10, TimeUnit.MINUTES);
		assertNull(cache.get("app", "exp1"));
		assertNull(cache.get("app", "exp2"));
		assertNull(cache.get("app", "exp3"));
		assertNull(cache.get("app", "exp4"));
	}

}
