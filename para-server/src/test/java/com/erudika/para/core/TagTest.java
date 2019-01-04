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
package com.erudika.para.core;

import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.MockDAO;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class TagTest {

	private DAO dao;
	private Tag t;

	@Before
	public void setUp() {
		dao = new MockDAO();
		t = new Tag("test");
		t.setCreatorid("111");
	}

	@Test
	public void testId() {
		assertEquals("tag:test", t.getId());
		t.setId("test1");
		assertEquals("tag:test1", t.getId());

		t.setId("tag:tag:test tag ? @#$%^&>?<~`|\\;:./>-= COOL");
		assertEquals("tag:test-tag-cool", t.getId());
		assertEquals("test-tag-cool", t.getTag());
		t.setId("tag ? @#$%^&>?<~`|\\;:./>-= COOL");
		assertEquals("tag:tag-cool", t.getId());
		assertEquals("tag-cool", t.getTag());

		t.setId("tag:tag-test1");
		assertEquals("tag:tag-test1", t.getId());
		assertEquals("tag-test1", t.getTag());

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
		assertEquals("test1", t.getTag());
		t.setTag(null);
		assertTrue(t.getTag().isEmpty());
		t.setTag("tag:test tag ? @#$%^&>?<~`|\\;:./>-= COOL");
		assertEquals("tag-test-tag-cool", t.getTag());
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
		assertTrue(t1.equals(t2));
	}
}