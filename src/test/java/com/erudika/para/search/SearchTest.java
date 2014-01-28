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
package com.erudika.para.search;

import com.erudika.para.core.Address;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import com.erudika.para.persistence.DAO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
@Ignore
public abstract class SearchTest {
	
	protected static Search s;
	protected static String appName1 = "testapp1";
	protected static String appName2 = "testapp2";
	
	protected static User u;
	protected static User u1;
	protected static User u2;
	protected static Tag t;
	protected static Sysprop s1;
	protected static Sysprop s2;
	protected static Address a1;
	protected static Address a2;
	
	public static void init() {
		u = new User("111");
		u.setSearch(s);
		u.setName("John Doe");
		u.setGroups(User.Groups.USERS.toString());
		u.setEmail("john@asd.com");
		u.setIdentifier(u.getEmail());
		u.setTimestamp(System.currentTimeMillis());
		u.setPassword("123456");
		u.addTags("one", "two", "three");
		
		u1 = new User("222");
		u1.setSearch(s);
		u1.setName("Joe Black");
		u1.setGroups(User.Groups.USERS.toString());
		u1.setEmail("joe@asd.com");
		u1.setIdentifier(u1.getEmail());
		u1.setTimestamp(System.currentTimeMillis());
		u1.setPassword("123456");
		u1.addTags("two", "four", "three");
		
		u2 = new User("333");
		u2.setSearch(s);
		u2.setName("Ann Smith");
		u2.setGroups(User.Groups.USERS.toString());
		u2.setEmail("ann@asd.com");
		u2.setIdentifier(u2.getEmail());
		u2.setTimestamp(System.currentTimeMillis());
		u2.setPassword("123456");
		u2.addTags("four", "five", "three");
		
		t = new Tag("test");
		t.setSearch(s);
		t.setCount(3);
		t.setTimestamp(System.currentTimeMillis());
		
		a1 = new Address("adr1");
		a1.setName("Place 1");
		a1.setAddress("NYC");
		a1.setCountry("US");
		a1.setLatlng("40.67,-73.94");
		a1.setParentid(u.getId());
		a1.setCreatorid(u.getId());
		
		a2 = new Address("adr2");
		a2.setName("Place 2");
		a2.setAddress("NYC");
		a2.setCountry("US");
		a2.setLatlng("40.69,-73.95");
		a2.setParentid(t.getId());
		a2.setCreatorid(t.getId());
		
		s1 = new Sysprop("s1");
		s1.setName("This is a little test sentence. Testing, one, two, three.");
		s1.setTimestamp(System.currentTimeMillis());
		
		s2 = new Sysprop("s2");
		s2.setName("We are testing this thing. This sentence is a test. One, two.");
		s2.setTimestamp(System.currentTimeMillis());
		
		s.indexAll(Arrays.asList(u, u1, u2, t, s1, s2, a1, a2));
		try {
			Thread.sleep(1000);
		} catch (InterruptedException ex) {}
	}
	
	
	public static void cleanup() {
		u = null;
		t = null;
		u1 = null; 
		u2 = null; 
		s1 = null; 
		s2 = null; 
		a1 = null; 
		a2 = null;
	}

	@Test
	public void testFindById() {
		assertNull(s.findById(null, null));
		assertNull(s.findById("", u.getClassname()));
		assertNull(s.findById(u.getId(), "wrongtype"));
		assertNotNull(s.findById(u.getId(), u.getClassname()));
		assertNotNull(s.findById(t.getId(), t.getClassname()));
	}
 
	@Test
	public void testFindNearbyObjects() {
		assertTrue(s.findNearbyObjects(null, null, null, null, 100, 1, 1, null).isEmpty());
		ArrayList<User> l1 = s.findNearbyObjects(u.getClassname(), null, null, "*", 10, 40.60, -73.90, null);
		assertFalse(l1.isEmpty());
	}

	@Test
	public void testFindPrefix() {
		assertTrue(s.findPrefix("", null, null, "null", "xx").isEmpty());
		assertFalse(s.findPrefix(u.getClassname(), null, null, DAO.CN_NAME, "ann").isEmpty());
	}

	@Test
	public void testFindQuery() {
		assertTrue(s.findQuery(null, null, null, null).isEmpty());
		assertTrue(s.findQuery("", null, null, "*").isEmpty());
		assertTrue(s.findQuery(u.getClassname(), null, null, "_type:user").size() >= 3);
		assertFalse(s.findQuery(u.getClassname(), null, null, "ann").isEmpty());
		assertFalse(s.findQuery(u.getClassname(), null, null, "Ann").isEmpty());
	}

	@Test
	public void testFindSimilar() {
		assertTrue(s.findSimilar(t.getClassname(), null, null, "", 10).isEmpty());
		ArrayList<Sysprop> res = s.findSimilar(s1.getClassname(), s1.getId(), 
				new String[]{DAO.CN_NAME}, s1.getName(), 10);
		assertFalse(res.isEmpty());
		assertEquals(s2, res.get(0));
	}

	@Test
	public void testFindTagged() {
		int i0 = s.findTagged(u.getClassname(), null, null).size();
		int i1 = s.findTagged(u.getClassname(), null, null, "two").size();
		int i2 = s.findTagged(u.getClassname(), null, null, "one", "two").size();
		int i3 = s.findTagged(u.getClassname(), null, null, "three").size();
		int i4 = s.findTagged(u.getClassname(), null, null, "four", "three").size();
		int i5 = s.findTagged(u.getClassname(), null, null, "five", "three").size();
		int i6 = s.findTagged(t.getClassname(), null, null, "four", "three").size();
		
		assertEquals(0, i0);
		assertEquals(2, i1);
		assertEquals(1, i2);
		assertEquals(3, i3);
		assertEquals(2, i4);
		assertEquals(1, i5);
		assertEquals(0, i6);
	}

	@Test
	public void testFindTags() {
		assertTrue(s.findTags(null, 10).isEmpty());
		assertTrue(s.findTags("", 10).isEmpty());
		assertTrue(s.findTags("unknown", 10).isEmpty());
		assertTrue(s.findTags("", 10).isEmpty());
		assertTrue(s.findTags(t.getTag(), 10).size() >= 1);
	}

	@Test
	public void testFindTerm() {
		assertTrue(s.findTerm(null, null, null, null, null).isEmpty());
		assertTrue(s.findTerm(u.getClassname(), null, null, null, null).isEmpty());
		assertTrue(s.findTerm(u.getClassname(), null, null, "", "").isEmpty());
		assertTrue(s.findTerm(u.getClassname(), null, null, "term", null).isEmpty());
		assertEquals(1L, s.findTerm(u.getClassname(), null, null, DAO.CN_IDENTIFIER, u2.getIdentifier()).size());
		assertEquals(1L, s.findTerm(u.getClassname(), null, null, DAO.CN_EMAIL, u.getEmail()).size());
		assertTrue(s.findTerm(u.getClassname(), null, null, DAO.CN_CLASSNAME, u.getClassname()).size() >= 2);
	}

	@Test
	public void testFindTermInList() {
		assertTrue(s.findTermInList(u.getClassname(), null, null, DAO.CN_EMAIL, 
				Arrays.asList(new String[]{}), null, true, 10).isEmpty());
		assertEquals(1, s.findTermInList(u.getClassname(), null, null, DAO.CN_EMAIL, 
				Arrays.asList("email@test.com", u1.getEmail()), null, true, 10).size());
		assertEquals(3, s.findTermInList(u.getClassname(), null, null, DAO.CN_ID, 
				Arrays.asList(u.getId(), u1.getId(), u2.getId(), "xxx", "yyy"), null, true, 10).size());
	}

	@Test
	public void testFindTwoTerms() {
		assertEquals(1, s.findTwoTerms(u.getClassname(), null, null, 
				DAO.CN_CLASSNAME, u.getClassname(), DAO.CN_ID, u.getId()).size());
		assertTrue(s.findTwoTerms(u.getClassname(), null, null, 
				DAO.CN_CLASSNAME, null, DAO.CN_ID, "111").isEmpty());
		assertTrue(s.findTwoTerms(u.getClassname(), null, null, 
				DAO.CN_CLASSNAME, null, DAO.CN_ID, null).isEmpty());
		assertTrue(s.findTwoTerms(u.getClassname(), null, null, 
				DAO.CN_CLASSNAME, "bad", null, "bad").isEmpty());
		assertTrue(s.findTwoTerms(u.getClassname(), null, null, 
				null, "bad", null, "bad").isEmpty());
	}

	@Test
	public void testFindWildcard() {
		assertTrue(s.findWildcard(u.getClassname(), null, null, null, null).isEmpty());
		assertTrue(s.findWildcard(u.getClassname(), null, null, "", "").isEmpty());
		assertFalse(s.findWildcard(u.getClassname(), null, null, DAO.CN_EMAIL, "ann*").isEmpty());
		assertFalse(s.findWildcard(u.getClassname(), null, null, DAO.CN_NAME, "an*").isEmpty());
	}

	@Test
	public void testGetBeanCount() {
		assertEquals(0, s.getBeanCount(null).intValue());
		assertEquals(0, s.getBeanCount("").intValue());
		assertEquals(0, s.getBeanCount("test").intValue());
		assertTrue(s.getBeanCount(u.getClassname()).intValue() >= 3);
	}

	@Test
	public void testGetCount() throws InterruptedException {
		assertEquals(0L, s.getCount(u.getClassname(), null, null).intValue());
		Thread.sleep(500);
		assertEquals(0L, s.getCount(u.getClassname(), DAO.CN_ID, "").intValue());
		Thread.sleep(500);
		assertEquals(1L, s.getCount(u.getClassname(), DAO.CN_ID, u.getId()).intValue());
		Thread.sleep(500);
		assertEquals(0L, s.getCount(appName1, u.getClassname(), DAO.CN_ID, u.getId()).intValue());
	}

	@Test
	public void testIndex(){
		s.index(null);
		User ux = new User("test-xxx");
		s.index(ux);
		assertNotNull(s.findById(ux.getId(), u.getClassname()));
		assertNotNull(s.findById(u.getId(), u.getClassname()));
		assertNotNull(s.findById(t.getId(), t.getClassname()));
		s.unindex(ux);
		
		// test multiapp support
		ux.setId(u.getId()+"-APP1");
		ux.setName("UserApp1");
		s.index(appName1, ux);
		assertNotNull(s.findById(appName1, ux.getId(), ux.getClassname()));
		assertNull(s.findById(ux.getId(), ux.getClassname()));
		assertNull(s.findById(appName2, ux.getId(), ux.getClassname()));
		
		Tag tx = new Tag(t.getId()+"-APP2");
		tx.setName("TagApp2");
		s.index(appName2, tx);
		assertNotNull(s.findById(appName2, tx.getId(), tx.getClassname()));
		assertNull(s.findById(tx.getId(), tx.getClassname()));
		assertNull(s.findById(appName1, tx.getId(), tx.getClassname()));
	}

	@Test
	public void testUnindex() {
		Tag tu = new Tag("test-unindex");
		s.index(tu);
		assertNotNull(s.findById(tu.getId(), tu.getClassname()));
		s.unindex(tu);
		assertNull(s.findById(tu.getId(), tu.getClassname()));
	}
	
	@Test
	public void testIndexAllUnindexAll() {
		Tag tt1 = new Tag("test-all1");
		tt1.setSearch(s);
		Tag tt2 = new Tag("test-all2");
		tt1.setSearch(s);
		Tag tt3 = new Tag("test-all3");
		tt1.setSearch(s);
		List<Tag> tags = new ArrayList<Tag> ();
		tags.add(tt1);
		tags.add(tt2);
		tags.add(tt3);
		
		s.indexAll(tags);
		
		assertNotNull(s.findById(tt1.getId(), t.getClassname()));
		assertNotNull(s.findById(tt2.getId(), t.getClassname()));
		assertNotNull(s.findById(tt3.getId(), t.getClassname()));
		
		s.unindexAll(tags);
		
		assertNull(s.findById(tt1.getId(), t.getClassname()));
		assertNull(s.findById(tt2.getId(), t.getClassname()));
		assertNull(s.findById(tt3.getId(), t.getClassname()));
	}
	
}