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
package com.erudika.para.persistence;

import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import com.erudika.para.search.Search;
import java.util.Arrays;
import java.util.Map;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Ignore;
import static org.mockito.Mockito.*;
/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@Ignore
public abstract class DAOTest extends TestCase {
	
	protected DAO dao;
	
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
	public void tearDown(){
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
	public void testPutGetRemoveColumn() {
		dao.putColumn(u.getId(), DAO.OBJECTS, DAO.CN_NAME, "New Name");
		assertEquals("New Name", dao.read(u.getId()).getName());
		dao.putColumn(u.getId(), DAO.OBJECTS, DAO.CN_NAME, null);
		assertEquals("New Name", dao.read(u.getId()).getName());
		dao.putColumn(u.getId(), DAO.OBJECTS, DAO.CN_NAME, "");
		assertEquals("New Name", dao.read(u.getId()).getName());
		
		assertEquals(u.getId(), dao.getColumn(u.getId(), DAO.OBJECTS, DAO.CN_ID));
		dao.putColumn(u.getId(), DAO.OBJECTS, DAO.CN_NAME, "xxx");
		assertEquals("xxx", dao.getColumn(u.getId(), DAO.OBJECTS, DAO.CN_NAME));
		assertNull(dao.getColumn(u.getId(), DAO.OBJECTS, null));
		assertNull(dao.getColumn(u.getId(), DAO.OBJECTS, ""));
		assertNull(dao.getColumn(null, DAO.OBJECTS, ""));
		
		dao.putColumn(u.getId(), DAO.OBJECTS, DAO.CN_UPDATED, "123");
		assertEquals("123", dao.getColumn(u.getId(), DAO.OBJECTS, DAO.CN_UPDATED));
		dao.removeColumn(u.getId(), DAO.OBJECTS, DAO.CN_UPDATED);
		assertNull(dao.read(u.getId()).getUpdated());
	}

	@Test
	public void testExistsColumn() {
		assertFalse(dao.existsColumn(null, null, null));
		assertFalse(dao.existsColumn(u.getId(), DAO.OBJECTS, null));
		assertTrue(dao.existsColumn(u.getId(), DAO.OBJECTS, DAO.CN_NAME));
		assertFalse(dao.existsColumn(u.getId(), DAO.OBJECTS, "testcol"));
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
		assertEquals("10", dao.getColumn(t1.getId(), DAO.OBJECTS, "count"));
		assertEquals("20", dao.getColumn(t2.getId(), DAO.OBJECTS, "count"));
		assertEquals("30", dao.getColumn(t3.getId(), DAO.OBJECTS, "count"));
		
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