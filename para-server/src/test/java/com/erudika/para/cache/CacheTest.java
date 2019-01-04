/*
 * Copyright 2013-2019 Erudika. https://erudika.com
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

import com.erudika.para.core.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Ignore
public abstract class CacheTest {

	private final Cache c;
	private final String testApp1 = "testApp1";
	private final String testApp2 = "testApp2";

	public CacheTest(Cache c) {
		this.c = c;
	}

	@Before
	public void setUp() {
		c.removeAll();
	}

	@Test
	public void testContains() {
		assertFalse(c.contains(null));
		assertFalse(c.contains(""));
		assertFalse(c.contains("123"));
	}

	@Test
	public void testPut() throws InterruptedException {
		c.put("", "empty");
		c.put("1", "");
		c.put("2", null);
		c.put("123", Integer.valueOf(123));
		c.put("1234", new User("111"));
		assertFalse(c.contains(""));
		assertTrue(c.contains("1"));
		assertFalse(c.contains("2"));
		assertTrue(c.contains("123"));
		assertTrue(c.get("123") instanceof Integer);
		assertTrue(c.get("1234") instanceof User);

		c.remove("123");
		assertFalse(c.contains("123"));

		// test multiapp support
		c.put(testApp1, "123", "123");
		c.put(testApp2, "456", "456");
		assertFalse(c.contains("123") && c.contains("456"));
		assertFalse(c.contains(testApp1, "456"));
		assertFalse(c.contains(testApp2, "123"));
		c.put(testApp2, "123", "456");
		assertEquals("456", c.get(testApp2, "123"));

		c.put(testApp1, "test", "test", 1L);
		assertTrue(c.contains(testApp1, "test"));
		assertFalse(c.contains("test"));
		assertFalse(c.contains(testApp2, "test"));
		Thread.sleep(2000);
		assertFalse(c.contains(testApp1, "test"));
	}

	@Test
	public void testPutAllRemoveAll() {
		Map<String, String> map = new HashMap<>();
		map.put("123", "test");
		map.put("123", "test1");
		map.put("1234", "test");
		map.put("", "test");
		map.put("1", "");
		map.put("2", null);
		map.put(null, "");

		c.putAll(map);
		assertFalse(c.contains(""));
		assertTrue(c.contains("1"));
		assertFalse(c.contains("2"));
		assertTrue(c.contains("1234"));
		assertTrue(c.contains("123"));
		assertEquals("test1", c.get("123"));

		c.removeAll(new ArrayList<>(map.keySet()));
		assertFalse(c.contains("1"));
		assertFalse(c.contains("2"));
		assertFalse(c.contains("1234"));
		assertFalse(c.contains("123"));

		// test multiapp support
		c.putAll(testApp1, map);
		c.putAll(testApp2, map);
		assertTrue(c.contains(testApp1, "123"));
		assertTrue(c.contains(testApp2, "123"));
		assertEquals("test1", c.get(testApp1, "123"));
		assertEquals("test1", c.get(testApp2, "123"));
		c.removeAll(testApp1);
		assertFalse(c.contains(testApp1, "1"));
		assertFalse(c.contains(testApp1, "2"));
		assertFalse(c.contains(testApp1, "1234"));
		assertFalse(c.contains(testApp1, "123"));
		assertTrue(c.contains(testApp2, "123"));
		assertEquals("test1", c.get(testApp2, "123"));
	}

	@Test
	public void testGet() {
		assertNull(c.get("123"));
		assertNull(c.get(null));
		assertNull(c.get(""));
		c.put("123", "test123");
		assertEquals("test123", c.get("123"));
	}

	@Test
	public void testGetAll() {
		c.put("123", "123");
		ArrayList<String> list = new ArrayList<>();
		list.add("123");
		list.add("456");
		list.add("null");
		list.add("");
		list.add(null);

		Map<String, ?> map = c.getAll(list);
		assertEquals(1, map.size());
		c.put("456", "456");
		map = c.getAll(list);
		assertEquals(2, map.size());
	}

	@Test
	public void testRemove() {
		c.put(null, "");
		c.remove(null);
		c.remove("");
		c.remove("xxx");
		c.put("123", "123");
		assertFalse(c.contains("xxx"));
		assertTrue(c.contains("123"));
	}

	@Test
	public void testRemoveAll_0args() {
		c.put("1", "1");
		c.put("12", "12");
		c.put("123", "123");
		c.put("1234", "1234");
		c.put("12345", "12345");
		c.put("123456", "123456");
		assertTrue(c.contains("1234"));
		c.removeAll();
		assertFalse(c.contains("1234"));
	}
}