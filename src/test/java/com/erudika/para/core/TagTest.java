/*
 * Copyright 2013 Alex Bogdanovski <alex@erudika.com>.
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
package com.erudika.para.core;

import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.MockDAO;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class TagTest {

	private DAO dao;
	private Tag t;

	@Before
	public void setUp() {
		dao = new MockDAO();
		t = new Tag("test");
		t.setCreatorid("111");
		t.setDao(dao);
	}

	@Test
	public void testId() {
		assertEquals("tag:test", t.getId());
		t.setId("test1");
		assertEquals("test1", t.getId());
	}

	@Test
	public void testGetType() {
		t.setType("asd");
		assertEquals("asd", t.getType());
	}

	@Test
	public void testCount() {
		assertTrue(t.getCount() == 0);
		t.incrementCount();
		assertTrue(t.getCount() == 1);
		t.incrementCount();
		assertTrue(t.getCount() == 2);
		t.decrementCount();
		t.decrementCount();
		t.decrementCount();
		assertTrue(t.getCount() == -1);
	}

	@Test
	public void testSetCount() {
		t.setCount(999);
		assertTrue(t.getCount() == 999);
	}

	@Test
	public void testGetTag() {
		assertEquals("test", t.getTag());
	}

	@Test
	public void testSetTag() {
		t.setTag("test1");
		assertEquals("tag:test1", t.getId());
	}

	@Test
	public void testIncrementCount() {
	}

	@Test
	public void testDecrementCount() {
		t.create();
		assertTrue(t.exists());

		t.setCount(2);
		t.decrementCount();
		assertEquals(1, t.getCount().intValue());
		t.decrementCount();

		assertFalse(t.exists());
	}

	@Test
	public void testEquals() {
		Tag t1 = new Tag("tag1");
		Tag t2 = new Tag("tag2");

		assertFalse(t1.equals(t2));
		t1.setId("tag2");
		assertFalse(t1.equals(t2));
	}
}