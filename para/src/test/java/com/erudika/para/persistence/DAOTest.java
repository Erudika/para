/*
 * Copyright 2013-2014 Erudika. http://erudika.com
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
package com.erudika.para.persistence;

import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import com.erudika.para.search.Search;
import java.util.Arrays;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Ignore;
import static org.mockito.Mockito.*;
/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Ignore
public abstract class DAOTest {

	protected static DAO dao;
	protected static String appid1 = "testapp1";
	protected static String appid2 = "testapp2";

	private User u;
	private Tag t;

	@Before
	public void setUp() {
		u = new User("111");
		u.setSearch(mock(Search.class));
		u.setDao(dao);
		u.setName("Name");
		u.setGroups(User.Groups.USERS.toString());
		u.setEmail("asd@asd.com");
		u.setIdentifier(u.getEmail());
		u.setPassword("123456");

		t = new Tag("test");
		t.setSearch(mock(Search.class));
		t.setDao(dao);
		t.setCount(3);

		dao.create(u);
		dao.create(t);
	}

	@After
	public void tearDown() {
		dao.delete(u);
		dao.delete(t);
	}

	@Test
	public void testCreate() {
		assertNull(dao.create(null));
		assertEquals(u.getId(), dao.create(u));
		assertNotNull(u.getTimestamp());

		User x = dao.read(u.getId());
		assertEquals(u.getEmail(), x.getEmail());
		x.setEmail(null);
		assertNotNull(dao.create(x));
		x = dao.read(u.getId());
		assertNull(x.getEmail());

		// test multiapp support
		u.setId(u.getId()+"-APP1");
		u.setName("UserApp1");
		dao.create(appid1, u);
		assertNotNull(dao.read(appid1, u.getId()));
		assertNull(dao.read(u.getId()));
		assertNull(dao.read(appid2, u.getId()));

		t.setId(t.getId()+"-APP2");
		t.setName("TagApp2");
		dao.create(appid2, t);
		assertNotNull(dao.read(appid2, t.getId()));
		assertNull(dao.read(t.getId()));
		assertNull(dao.read(appid1, t.getId()));
	}

	@Test
	public void testRead() {
		assertNull(dao.read(null));
		assertNull(dao.read("1"));
		assertNotNull(dao.read(u.getId()));
		assertEquals(u.getName(), dao.read(u.getId()).getName());
	}

	@Test
	public void testUpdate() {
		u.setName("Test Name");
		dao.update(u);
		User x = dao.read(u.getId());
		assertEquals(u.getName(), x.getName());
		assertNotNull(x.getUpdated());
	}

	@Test
	public void testDelete() {
		dao.delete(u);
		dao.delete(t);
		assertNull(dao.read(u.getId()));
		assertNull(dao.read(t.getId()));
	}

	@Test
	public void testCreateAllReadAllUpdateAllDeleteAll() {
		Tag t1 = new Tag("t1");
		Tag t2 = new Tag("t2");
		Tag t3 = new Tag("t3");
		dao.createAll(Arrays.asList(t1, t2, t3));
		assertNotNull(dao.read(t1.getId()));
		assertNotNull(dao.read(t2.getId()));
		assertNotNull(dao.read(t3.getId()));

		Map<String, Tag> tags = dao.readAll(Arrays.asList(t1.getId(), t2.getId(), t3.getId()), true);
		assertFalse(tags.isEmpty());
		assertTrue(tags.containsKey(t1.getId()));
		assertTrue(tags.containsKey(t2.getId()));
		assertTrue(tags.containsKey(t3.getId()));

		assertTrue(t1.equals(tags.get(t1.getId())));
		assertTrue(t2.equals(tags.get(t2.getId())));
		assertTrue(t3.equals(tags.get(t3.getId())));

		t1.setCount(10);
		t2.setCount(20);
		t3.setCount(30);
		dao.updateAll(Arrays.asList(t1, t2, t3));
		Tag tr1 = dao.read(t1.getId());
		Tag tr2 = dao.read(t1.getId());
		Tag tr3 = dao.read(t1.getId());
		assertEquals("10", tr1.getCount());
		assertEquals("20", tr2.getCount());
		assertEquals("30", tr3.getCount());

		dao.deleteAll(Arrays.asList(t1, t2, t3));
		assertNull(dao.read(t1.getId()));
		assertNull(dao.read(t2.getId()));
		assertNull(dao.read(t3.getId()));
	}

	@Test
	public void testReadPage() {
		// TODO
	}
}