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
package com.erudika.para.search;

import com.erudika.para.core.Address;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	protected static String appid1 = "testapp1";
	protected static String appid2 = "testapp2";

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
		u.setTimestamp(Utils.timestamp());
		u.setPassword("123456");
		u.addTags("one", "two", "three");

		u1 = new User("222");
		u1.setSearch(s);
		u1.setName("Joe Black");
		u1.setGroups(User.Groups.USERS.toString());
		u1.setEmail("joe@asd.com");
		u1.setIdentifier(u1.getEmail());
		u1.setTimestamp(Utils.timestamp());
		u1.setPassword("123456");
		u1.addTags("two", "four", "three");

		u2 = new User("333");
		u2.setSearch(s);
		u2.setName("Ann Smith");
		u2.setGroups(User.Groups.USERS.toString());
		u2.setEmail("ann@asd.com");
		u2.setIdentifier(u2.getEmail());
		u2.setTimestamp(Utils.timestamp());
		u2.setPassword("123456");
		u2.addTags("four", "five", "three");

		t = new Tag("test");
		t.setSearch(s);
		t.setCount(3);
		t.setTimestamp(Utils.timestamp());

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
		s1.setTimestamp(Utils.timestamp());

		s2 = new Sysprop("s2");
		s2.setName("We are testing this thing. This sentence is a test. One, two.");
		s2.setTimestamp(Utils.timestamp());

		s.indexAll(Arrays.asList(u, u1, u2, t, s1, s2, a1, a2));
		try {
			Thread.sleep(1000);
		} catch (InterruptedException ex) { }
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
		assertNull(s.findById(""));
		assertNotNull(s.findById(u.getId()));
		assertNotNull(s.findById(t.getId()));
	}

	@Test
	public void testfindNearby() {
		assertTrue(s.findNearby(null, null, 100, 1, 1).isEmpty());
		List<User> l1 = s.findNearby(u.getType(), "*", 10, 40.60, -73.90);
		assertFalse(l1.isEmpty());
	}

	@Test
	public void testFindPrefix() {
		assertTrue(s.findPrefix("", "null", "xx").isEmpty());
		assertFalse(s.findPrefix(u.getType(), Config._NAME, "ann").isEmpty());
	}

	@Test
	public void testFindQuery() {
		assertTrue(s.findQuery(null, null).isEmpty());
		assertTrue(s.findQuery("", "*").isEmpty());
		assertTrue(s.findQuery(u.getType(), "_type:user").size() >= 3);
		assertFalse(s.findQuery(u.getType(), "ann").isEmpty());
		assertFalse(s.findQuery(u.getType(), "Ann").isEmpty());

		Pager p = new Pager();
		assertEquals(0, p.getCount());
		List<?> res = s.findQuery(u.getType(), "*", p);
		assertEquals(res.size(), p.getCount());
		assertTrue(p.getCount() > 0);
	}

	@Test
	public void testFindSimilar() {
		assertTrue(s.findSimilar(t.getType(), "", null, null).isEmpty());
		assertTrue(s.findSimilar(t.getType(), "", new String[0], "").isEmpty());
		List<Sysprop> res = s.findSimilar(s1.getType(), s1.getId(), new String[]{Config._NAME}, s1.getName());
		assertFalse(res.isEmpty());
		assertEquals(s2, res.get(0));
	}

	@Test
	public void testFindTagged() {
		int i0 = s.findTagged(u.getType(), null).size();
		int i1 = s.findTagged(u.getType(), new String[]{"two"}).size();
		int i2 = s.findTagged(u.getType(), new String[]{"one", "two"}).size();
		int i3 = s.findTagged(u.getType(), new String[]{"three"}).size();
		int i4 = s.findTagged(u.getType(), new String[]{"four", "three"}).size();
		int i5 = s.findTagged(u.getType(), new String[]{"five", "three"}).size();
		int i6 = s.findTagged(t.getType(), new String[]{"four", "three"}).size();

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
		assertTrue(s.findTags(null).isEmpty());
		assertTrue(s.findTags("").isEmpty());
		assertTrue(s.findTags("unknown").isEmpty());
		assertTrue(s.findTags("").isEmpty());
		assertTrue(s.findTags(t.getTag()).size() >= 1);
	}

	@Test
	public void testFindTermInList() {
		assertTrue(s.findTermInList(u.getType(), Config._EMAIL, Arrays.asList(new String[0])).isEmpty());
		assertEquals(1, s.findTermInList(u.getType(), Config._EMAIL, 
				Arrays.asList("email@test.com", u1.getEmail())).size());
		assertEquals(3, s.findTermInList(u.getType(), Config._ID,
				Arrays.asList(u.getId(), u1.getId(), u2.getId(), "xxx", "yyy")).size());
	}

	@Test
	public void testFindTerms() {
		// many terms
		Map<String, Object> terms = new HashMap<String, Object>();
//		terms.put(Config._TYPE, u.getType());
		terms.put(Config._ID, u.getId());

		Map<String, Object> terms1 = new HashMap<String, Object>();
		terms1.put(Config._TYPE, null);
		terms1.put(Config._ID, "");

		Map<String, Object> terms2 = new HashMap<String, Object>();
		terms2.put(" ", "bad");
		terms2.put("", "");

		assertEquals(1, s.findTerms(u.getType(), terms, true).size());
		assertTrue(s.findTerms(u.getType(), terms1, true).isEmpty());
		assertTrue(s.findTerms(u.getType(), terms2, true).isEmpty());

		// single term
		assertTrue(s.findTerms(null, null, true).isEmpty());
		assertTrue(s.findTerms(u.getType(), Collections.singletonMap("", null), true).isEmpty());
		assertTrue(s.findTerms(u.getType(), Collections.singletonMap("", ""), true).isEmpty());
		assertTrue(s.findTerms(u.getType(), Collections.singletonMap("term", null), true).isEmpty());
		assertEquals(1L, s.findTerms(u.getType(), 
				Collections.singletonMap(Config._IDENTIFIER, u2.getIdentifier()), true).size());
		assertEquals(1L, s.findTerms(u.getType(), 
				Collections.singletonMap(Config._EMAIL, u.getEmail()), true).size());
		assertTrue(s.findTerms(u.getType(), 
				Collections.singletonMap(Config._TYPE, u.getType()), true).size() >= 2);
	}

	@Test
	public void testFindWildcard() {
		assertTrue(s.findWildcard(u.getType(), null, null).isEmpty());
		assertTrue(s.findWildcard(u.getType(), "", "").isEmpty());
		assertFalse(s.findWildcard(u.getType(), Config._EMAIL, "ann*").isEmpty());
		assertFalse(s.findWildcard(u.getType(), Config._NAME, "an*").isEmpty());
	}

	@Test
	public void testgetCount() {
		assertEquals(0, s.getCount(null).intValue());
		assertEquals(0, s.getCount("").intValue());
		assertEquals(0, s.getCount("test").intValue());
		assertTrue(s.getCount(u.getType()).intValue() >= 3);
	}

	@Test
	public void testGetCount() throws InterruptedException {
		assertEquals(0L, s.getCount(u.getType(), null, null).intValue());
//		Thread.sleep(500);
		assertEquals(0L, s.getCount(u.getType(), Collections.singletonMap(Config._ID, "")).intValue());
//		Thread.sleep(500);
		assertEquals(1L, s.getCount(u.getType(), Collections.singletonMap(Config._ID, u.getId())).intValue());
//		Thread.sleep(500);
		assertEquals(0L, s.getCount(appid1, u.getType(), Collections.singletonMap(Config._ID, u.getId())).intValue());
	}

	@Test
	public void testIndex() {
		s.index(null);
		User ux = new User("test-xxx");
		s.index(ux);
		assertNotNull(s.findById(ux.getId()));
		assertNotNull(s.findById(u.getId()));
		assertNotNull(s.findById(t.getId()));
		s.unindex(ux);

		// test multiapp support
		ux.setId(u.getId()+"-APP1");
		ux.setName("UserApp1");
		s.index(appid1, ux);
		assertNotNull(s.findById(appid1, ux.getId()));
		assertNull(s.findById(ux.getId()));
		assertNull(s.findById(appid2, ux.getId()));

		Tag tx = new Tag(t.getId()+"-APP2");
		tx.setName("TagApp2");
		s.index(appid2, tx);
		assertNotNull(s.findById(appid2, tx.getId()));
		assertNull(s.findById(tx.getId()));
		assertNull(s.findById(appid1, tx.getId()));
	}

	@Test
	public void testUnindex() {
		Tag tu = new Tag("test-unindex");
		s.index(tu);
		assertNotNull(s.findById(tu.getId()));
		s.unindex(tu);
		assertNull(s.findById(tu.getId()));
	}

	@Test
	public void testIndexAllUnindexAll() {
		Tag tt1 = new Tag("test-all1");
		tt1.setSearch(s);
		Tag tt2 = new Tag("test-all2");
		tt1.setSearch(s);
		Tag tt3 = new Tag("test-all3");
		tt1.setSearch(s);
		List<Tag> tags = new ArrayList<Tag>();
		tags.add(tt1);
		tags.add(tt2);
		tags.add(tt3);

		s.indexAll(tags);

		assertNotNull(s.findById(tt1.getId()));
		assertNotNull(s.findById(tt2.getId()));
		assertNotNull(s.findById(tt3.getId()));

		s.unindexAll(tags);

		assertNull(s.findById(tt1.getId()));
		assertNull(s.findById(tt2.getId()));
		assertNull(s.findById(tt3.getId()));
	}

}