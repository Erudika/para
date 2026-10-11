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

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CaffeineCacheTest {

	@Test
	void hotEntriesExpireAfterTheyBecomeIdle() {
		FakeTicker ticker = new FakeTicker();
		CaffeineCache cache = new CaffeineCache(100, Duration.ofSeconds(10), ticker);
		cache.put("app", "para", "root");

		ticker.advance(9, TimeUnit.SECONDS);
		assertTrue(cache.contains("app", "para"));
		ticker.advance(9, TimeUnit.SECONDS);
		assertEquals("root", cache.get("app", "para"));
		ticker.advance(9, TimeUnit.SECONDS);
		assertTrue(cache.contains("app", "para"));

		ticker.advance(11, TimeUnit.SECONDS);
		assertNull(cache.get("app", "para"));
	}

	@Test
	void explicitTtlDoesNotExtendOnRead() {
		FakeTicker ticker = new FakeTicker();
		CaffeineCache cache = new CaffeineCache(100, Duration.ofMinutes(10), ticker);
		cache.put("app", "expiring", "value", 10L);

		ticker.advance(5, TimeUnit.SECONDS);
		assertEquals("value", cache.get("app", "expiring"));
		ticker.advance(5, TimeUnit.SECONDS);
		assertNull(cache.get("app", "expiring"));
	}

	@Test
	void removeAllForAppDoesNotRemoveOtherApps() {
		FakeTicker ticker = new FakeTicker();
		CaffeineCache cache = new CaffeineCache(100, Duration.ofMinutes(10), ticker);
		cache.put("app-one", "para", "one");
		cache.put("app-two", "para", "two");

		cache.removeAll("app-one");

		assertFalse(cache.contains("app-one", "para"));
		assertEquals("two", cache.get("app-two", "para"));
	}

	@Test
	void appAndObjectIdsCannotCollide() {
		FakeTicker ticker = new FakeTicker();
		CaffeineCache cache = new CaffeineCache(100, Duration.ofMinutes(10), ticker);
		cache.put("a", "b:c", "first");
		cache.put("a:b", "c", "second");

		assertEquals("first", cache.get("a", "b:c"));
		assertEquals("second", cache.get("a:b", "c"));
	}

	@Test
	void appKeyMetadataDoesNotCompeteForCacheSpace() {
		FakeTicker ticker = new FakeTicker();
		CaffeineCache cache = new CaffeineCache(1, Duration.ofMinutes(10), ticker);
		cache.put("app", "para", "root");

		assertTrue(cache.contains("app", "para"));
		assertEquals("root", cache.get("app", "para"));
	}
}
