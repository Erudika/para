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

import com.erudika.para.core.User;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@RunWith(Parameterized.class)
public class CacheIT {
	
	@Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][]{
			{new MockCache()}, 
			{new HazelcastCache()}
		});
	}
	
	private Cache c;
	
	public CacheIT(Cache c) {
		this.c = c;
	}
	
	@Before
	public void setUp(){
		c.removeAll();
	}
	
	@Test
	public void testContains() {
		assertFalse(c.contains(null));
		assertFalse(c.contains(""));
		assertFalse(c.contains("123"));
	}

	@Test
	public void testPut_String_GenericType() {
		c.put("", "empty");
		assertFalse(c.contains(""));
		c.put("123", new Integer(123));
		c.put("1234", new User("111"));
		assertTrue(c.contains("123"));
		assertTrue(c.get("123") instanceof Integer);
		assertTrue(c.get("1234") instanceof User);
		c.remove("123");
		assertFalse(c.contains("123"));
	}

	@Test
	public void testPut_3args() throws InterruptedException {
		c.put("test", "test", 1L);
		assertTrue(c.contains("test"));
		Thread.sleep(2000);
		assertFalse(c.contains("test"));
	}

	@Test
	public void testPutAllRemoveAll() {
		Map<String, String> map = new HashMap<String, String>();
		map.put("123", "test");
		map.put("123", "test1");
		map.put("1234", "test");
		map.put("", "test");
//		map.put("", null);
		map.put(null, "");
		c.putAll(map);
		assertEquals("test1", c.get("123"));
		c.removeAll(new ArrayList<String>(map.keySet()));
		assertFalse(c.contains("1234"));
		assertFalse(c.contains("123"));
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
		ArrayList<String> list = new ArrayList<String>();
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