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
package com.erudika.para.search;

import com.erudika.para.core.Address;
import com.erudika.para.core.App;
import com.erudika.para.core.Linker;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.utils.CoreUtils;
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
 * @author Alex Bogdanovski [alex@erudika.com]
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
	protected static Linker l1;

	public static void init() {
		CoreUtils.getInstance().setSearch(s);
		u = new User("111");
		u.setName("John Doe");
		u.setGroups(User.Groups.USERS.toString());
		u.setEmail("john@asd.com");
		u.setIdentifier(u.getEmail());
		u.setTimestamp(1000000000L);
		u.setPassword("123456");
		u.setTags(CoreUtils.getInstance().addTags(u.getTags(), "one", "two", "three"));

		u1 = new User("222");
		u1.setName("Joe Black");
		u1.setGroups(User.Groups.USERS.toString());
		u1.setEmail("joe@asd.com");
		u1.setIdentifier(u1.getEmail());
		u1.setTimestamp(Utils.timestamp());
		u1.setPassword("123456");
		u1.setTags(CoreUtils.getInstance().addTags(u1.getTags(), "two", "four", "three"));

		u2 = new User("333");
		u2.setName("Ann Smith");
		u2.setGroups(User.Groups.USERS.toString());
		u2.setEmail("ann@asd.com");
		u2.setIdentifier(u2.getEmail());
		u2.setTimestamp(Utils.timestamp());
		u2.setPassword("123456");
		u2.setTags(CoreUtils.getInstance().addTags(u2.getTags(), "four", "five", "three"));

		t = new Tag("test");
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
		s1.addProperty("text", "This is a little test sentence. Testing, one, two, three.");
		s1.setTimestamp(Utils.timestamp());

		s2 = new Sysprop("s2");
		s2.addProperty("text", "We are testing this thing. This sentence is a test. One, two.");
		s2.setTimestamp(Utils.timestamp());

		Sysprop linked1 = new Sysprop("link1");
		Sysprop linked2 = new Sysprop("link2");
		linked1.addProperty("text", "hello kitty");
		linked2.addProperty("text", "hello doggy");
		l1 = new Linker("cat", "dog", "id1", "id2");
		l1.addNestedObject(linked1);
		l1.addNestedObject(linked2);

		s.indexAll(Arrays.asList(u, u1, u2, t, s1, s2, a1, a2, l1));
		try {
			Thread.sleep(1000);
		} catch (InterruptedException ex) { }
	}


	public static void cleanup() {
		s.unindexAll(Arrays.asList(u, u1, u2, t, s1, s2, a1, a2, l1));
		u = null;
		t = null;
		u1 = null;
		u2 = null;
		s1 = null;
		s2 = null;
		a1 = null;
		a2 = null;
		l1 = null;
	}

	@Test
	public void testFindById() {
		assertNull(s.findById(null, null));
		assertNull(s.findById(""));
		assertNotNull(s.findById(u.getId()));
		assertNotNull(s.findById(t.getId()));
	}

	@Test
	public void testFindByIds() {
		assertTrue(s.findByIds(null).isEmpty());
		assertTrue(s.findByIds(new ArrayList<>(0)).isEmpty());
		List<?> list = s.findByIds(Arrays.asList(new String[]{u.getId(), t.getId()}));
		assertFalse(list.isEmpty());
		assertEquals(2, list.size());
	}

	@Test
	public void testfindNearby() {
		assertTrue(s.findNearby(null, null, 100, 1, 1).isEmpty());
		List<User> l1 = s.findNearby(u.getType(), "*", 10, 40.60, -73.90);
		assertFalse(l1.isEmpty());
		List<User> l2 = s.findNearby(a1.getType(), "*", 10, 40.60, -73.90);
		assertFalse(l2.isEmpty());
	}

	@Test
	public void testFindPrefix() {
		assertTrue(s.findPrefix(null, null, "").isEmpty());
		assertTrue(s.findPrefix("", "null", "xx").isEmpty());
		assertFalse(s.findPrefix(u.getType(), Config._NAME, "Ann").isEmpty());
	}

	@Test
	public void testFindQuery() {
		assertTrue(s.findQuery(null, null).isEmpty());
		assertFalse(s.findQuery("", "*").isEmpty()); // will find *
		assertTrue(s.findQuery(u.getType(), "type:user").size() >= 3);
		assertFalse(s.findQuery(u.getType(), "Ann*").isEmpty());
//		assertFalse(s.findQuery(u.getType(), "name:(Ann Smith)").isEmpty());
		assertTrue(s.findQuery(null, "*").size() > 4);
		// bad query syntax? - replace with *
		assertFalse(s.findQuery(u.getType(), "AND").isEmpty());
		assertFalse(s.findQuery(u.getType(), "AND ? OR").isEmpty());
		assertFalse(s.findQuery(u.getType(), "? OR").isEmpty());

		Pager p = new Pager();
		assertEquals(0, p.getCount());
		List<?> res = s.findQuery(u.getType(), "*", p);
		assertEquals(res.size(), p.getCount());
		assertTrue(p.getCount() > 0);
	}

	@Test
	public void testFindNestedQuery() throws InterruptedException {
		assertTrue(s.findNestedQuery(null, null, null).isEmpty());
		assertTrue(s.findNestedQuery(l1.getType(), null, null).isEmpty());

		assertFalse(s.findNestedQuery(l1.getType(), "properties.text", "kitty").isEmpty());
		assertFalse(s.findNestedQuery(l1.getType(), "properties.text", "doggy").isEmpty());
	}

	@Test
	public void testFindSimilar() {
		assertTrue(s.findSimilar(t.getType(), "", null, null).isEmpty());
		assertTrue(s.findSimilar(t.getType(), "", new String[0], "").isEmpty());
		List<Sysprop> res = s.findSimilar(s1.getType(), s1.getId(),
				new String[]{"properties.text"}, (String) s1.getProperty("text"));
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
		Map<String, Object> terms = new HashMap<>();
//		terms.put(Config._TYPE, u.getType());
		terms.put(Config._ID, u.getId());

		Map<String, Object> terms1 = new HashMap<>();
		terms1.put(Config._TYPE, null);
		terms1.put(Config._ID, "");

		Map<String, Object> terms2 = new HashMap<>();
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
		assertTrue(s.findTerms(u.getType(), Collections.singletonMap(Config._NAME, "Ann Smith"), true).size() >= 1);
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
		assertFalse(s.findWildcard(u.getType(), Config._NAME, "An*").isEmpty());
	}

	@Test
	public void testGetCount() {
		assertTrue(s.getCount(null).intValue() > 4);
		assertNotEquals(0, s.getCount("").intValue());
		assertEquals(0, s.getCount("test").intValue());
		assertTrue(s.getCount(u.getType()).intValue() >= 3);

		assertEquals(0L, s.getCount(u.getType(), null, null).intValue());
		assertEquals(0L, s.getCount(u.getType(), Collections.singletonMap(Config._ID, " ")).intValue());
		assertEquals(1L, s.getCount(u.getType(), Collections.singletonMap(Config._ID, u.getId())).intValue());
		assertEquals(0L, s.getCount(appid1, u.getType(), Collections.singletonMap(Config._ID, u.getId())).intValue());
		assertTrue(s.getCount(null, Collections.singletonMap(Config._TYPE, u.getType())).intValue() > 1);
	}

	@Test
	public void testPaginationAndSorting() {
		Pager pager = new Pager(2);
		List<User> page1 = s.findQuery(u.getType(), "type:user", pager);
		pager.setPage(2);
		List<User> page2 = s.findQuery(u.getType(), "type:user", pager);
		pager.setPage(3);
		List<User> page3 = s.findQuery(u.getType(), "type:user", pager);

		assertEquals(2, page1.size());
		assertEquals(1, page2.size());
		assertEquals(0, page3.size());
		assertTrue(s.findQuery(u.getType(), "type:user", new Pager(3, 2)).isEmpty());

		Pager pager2 = new Pager(3);
		pager2.setPage(0);
		pager2.setLimit(3);
		pager2.setSortby(Config._ID);
		pager2.setDesc(false);
		List<User> sortedById = s.findQuery(u.getType(), "type:user", pager2);
		assertFalse(sortedById.isEmpty());
		assertEquals(u.getId(), sortedById.get(0).getId());
		assertEquals(u1.getId(), sortedById.get(1).getId());
		assertEquals(u2.getId(), sortedById.get(2).getId());

		Pager pager3 = new Pager(3);
		pager3.setSortby(Config._ID);
		pager3.setDesc(true);
		List<User> sortedByIdReversed = s.findQuery(u.getType(), "type:user", pager3);
		assertEquals(u2.getId(), sortedByIdReversed.get(0).getId());
		assertEquals(u1.getId(), sortedByIdReversed.get(1).getId());
		assertEquals(u.getId(), sortedByIdReversed.get(2).getId());
	}

	@Test
	public void testIndex() {
		s.index(null);
		Sysprop ux = new Sysprop("test-xxx");
		s.index(ux);
		assertNotNull(s.findById(ux.getId()));
		assertNotNull(s.findById(u.getId()));
		assertNotNull(s.findById(t.getId()));
		s.unindex(ux);

		// test multiapp support
		App ap1 = new App(appid1);
		App ap2 = new App(appid2);
		ap1.setSharingIndex(true);
		ap2.setSharingIndex(true);
//		String routedAppid1 = ap1.getAppidWithRouting();
//		String routedAppid2 = ap2.getAppidWithRouting();
		ux.setId(u.getId() + "-APP1");
		ux.setAppid(appid1);
		ux.setName("UserApp1");
		s.index(appid1, ux);
		assertNotNull(s.findById(appid1, ux.getId()));
		assertNull(s.findById(ux.getId()));
		assertNull(s.findById(appid2, ux.getId()));
		s.unindex(appid1, ux);

		Tag tx = new Tag(t.getId() + "-APP2");
		tx.setName("TagApp2");
		tx.setAppid(appid2);
		s.index(appid2, tx);
		assertNotNull(s.findById(appid2, tx.getId()));
		assertNull(s.findById(tx.getId()));
		assertNull(s.findById(appid1, tx.getId()));
		s.unindex(appid2, tx);
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
	public void testIndexAllUnindexAll() throws InterruptedException {
		Tag tt1 = new Tag("test-all1");
		Tag tt2 = new Tag("test-all2");
		Tag tt3 = new Tag("test-all3");
		List<Tag> tags = new ArrayList<>();
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

		Sysprop sp1 = new Sysprop("sps1");
		Sysprop sp2 = new Sysprop("sps2");
		Sysprop sp3 = new Sysprop("sps3");
		sp1.setName("xx");
		sp2.setName("xx");
		sp3.setName("sps3");
		sp1.setTimestamp(123L);
		sp2.setTimestamp(123L);
		sp3.setTimestamp(1234L);
		s.index(sp1);
		s.index(sp2);
		s.index(sp3);

		Thread.sleep(1000);

		Map<String, Object> terms = new HashMap<>();
		terms.put(Config._NAME, "xx");
		terms.put(Config._TIMESTAMP, 123L);

		s.unindexAll(terms, true);
		Thread.sleep(2000);

		assertNull(s.findById(sp1.getId()));
		assertNull(s.findById(sp2.getId()));
		assertNotNull(s.findById(sp3.getId()));
	}

	@Test
	public void testSearchAfter() throws InterruptedException {
		ArrayList<Sysprop> list = new ArrayList<>();
		for (int i = 0; i < 22; i++) {
			Sysprop obj = new Sysprop("id_" + i);
			obj.addProperty("prop" + i, i);
			list.add(obj);
		}
		String appid3 = "testapp3";
		s.indexAll(appid3, list);

		Pager p = new Pager(10);
		p.setDesc(false);
		List<ParaObject> page1 = s.findQuery(appid3, null, "*", p);
		assertEquals(10, page1.size()); // page 1

		List<ParaObject> page2 = s.findQuery(appid3, null, "*", p);
		assertEquals(10, page2.size()); // page 2

		List<ParaObject> page3 = s.findQuery(appid3, null, "*", p);
		assertEquals(2, page3.size());  // page 3

		List<ParaObject> page4 = s.findQuery(appid3, null, "*", p);
		assertTrue(page4.isEmpty());  // end
		assertEquals(22, p.getCount());
		s.unindexAll(appid3, list);
	}

}