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
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import com.erudika.para.persistence.DAO;
import com.erudika.para.utils.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.mutable.MutableLong;
import org.elasticsearch.action.admin.indices.alias.get.IndicesGetAliasesResponse;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.search.SearchHits;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;
import static org.mockito.Mockito.mock;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
@Ignore
public abstract class SearchTest {
	
	protected static Search s;
	protected static User u;
	protected static User u1;
	protected static User u2;
	protected static Tag t;
	protected static Sysprop s1;
	protected static Sysprop s2;
	
	protected static String appName1 = "para-test1";
	protected static String appName2 = "para-test2";
	
	public SearchTest() {
		DAO dao = mock(DAO.class);
		u = new User("111");
		u.setSearch(s);
		u.setDao(dao);
		u.setName("John Doe");
		u.setGroups(User.Groups.USERS.toString());
		u.setEmail("john@asd.com");
		u.setIdentifier(u.getEmail());
		u.setTimestamp(System.currentTimeMillis());
		u.setPassword("123456");
		
		u1 = new User("222");
		u1.setSearch(s);
		u1.setDao(dao);
		u1.setName("Joe Black");
		u1.setGroups(User.Groups.USERS.toString());
		u1.setEmail("joe@asd.com");
		u1.setIdentifier(u.getEmail());
		u1.setTimestamp(System.currentTimeMillis());
		u1.setPassword("123456");
		
		u2 = new User("333");
		u2.setSearch(s);
		u2.setDao(dao);
		u2.setName("Ann Smith");
		u2.setGroups(User.Groups.USERS.toString());
		u2.setEmail("ann@asd.com");
		u2.setIdentifier(u.getEmail());
		u2.setTimestamp(System.currentTimeMillis());
		u2.setPassword("123456");

		t = new Tag("test");
		t.setSearch(s);
		t.setDao(dao);
		t.setCount(3);
		t.setTimestamp(System.currentTimeMillis());

		s1 = new Sysprop("s1");
		s1.setName("This is a little test sentence. Testing, one, two, three.");
		s1.setTimestamp(System.currentTimeMillis());

		s2 = new Sysprop("s2");
		s2.setName("We are testing this thing. This sentence is a test. One, two.");
		s2.setTimestamp(System.currentTimeMillis());

		s.index(u);
		s.index(u1);
		s.index(u2);
		s.index(t);
		s.index(s1);
		s.index(s2);
	}
			
	@Before
	public void setUp() {
	}
	
	@After
	public void tearDown() {
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
	public void testFindNearbyObjects() throws InterruptedException {
		Address a1 = new Address();
		a1.setName("Place 1");
		a1.setAddress("NYC");
		a1.setCountry("US");
		a1.setLatlng("40.67,-73.94");
		a1.setParentid(u.getId());
		a1.setCreatorid(u.getId());
		
		Address a2 = new Address();
		a2.setName("Place 2");
		a2.setAddress("NYC");
		a2.setCountry("US");
		a2.setLatlng("40.69,-73.95");
		a2.setParentid(t.getId());
		a2.setCreatorid(t.getId());
		
		s.index(a1);
		s.index(a2);
		
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
		assertTrue(s.findQuery("", null, null, "*").isEmpty());
		ArrayList<User> res = s.findQuery(u.getClassname(), null, null, "_type:user");
		assertEquals(3, res.size());
		ArrayList<User> res1 = s.findQuery(u.getClassname(), null, null, "ann");
		assertFalse(res1.isEmpty());
	}

	@Test
	public void testFindSimilar() throws InterruptedException {
		assertTrue(s.findSimilar(t.getClassname(), null, null, "", 10).isEmpty());
		
		ArrayList<Sysprop> res = s.findSimilar(s1.getClassname(), s1.getId(), new String[]{DAO.CN_NAME}, s1.getName(), 10);
		assertFalse(res.isEmpty());
		assertEquals(s2, res.get(0));
			
//		ParaObject userDefined = Utils.fromJSON("{\"classname\":\"testtype\", \"name\":\"testname\", \"id\":\"123\"}");
//		assertEquals("testtype", userDefined.getClassname());		
	}

	@Test
	public void testFindTagged() {
	}

	@Test
	public void testFindTags() {
	}

	@Test
	public void testFindTerm_5args() {
	}

	@Test
	public void testFindTerm_8args() {
	}

	@Test
	public void testFindTermInList() {
	}

	@Test
	public void testFindTwoTerms_7args() {
	}

	@Test
	public void testFindTwoTerms_10args() {
	}

	@Test
	public void testFindTwoTerms_11args() {
	}

	@Test
	public void testFindWildcard_5args() {
	}

	@Test
	public void testFindWildcard_8args() {
	}

	@Test
	public void testGetBeanCount() {
	}

	@Test
	public void testGetCount_3args() {
	}

	@Test
	public void testGetCount_5args() {
	}

	@Test
	public void testGetSearchClusterMetadata() {
	}

	@Test
	public void testGetSource() {
	}

	@Test
	public void testIndex_ParaObject_String() {
	}

	@Test
	public void testIndex() throws InterruptedException {
		
		
		// test multiapp support
		u.setId(u.getId()+"-APP1");
		u.setName("UserApp1");
		s.index(appName1, u);
		assertNotNull(s.findById(appName1, u.getId(), u.getClassname()));
		assertNull(s.findById(u.getId(), u.getClassname()));
		assertNull(s.findById(appName2, u.getId(), u.getClassname()));
		
		t.setId(t.getId()+"-APP2");
		t.setName("TagApp2");
		s.index(appName2, t);
		assertNotNull(s.findById(appName2, t.getId(), t.getClassname()));
		assertNull(s.findById(t.getId(), t.getClassname()));
		assertNull(s.findById(appName1, t.getId(), t.getClassname()));
	}

	@Test
	public void testIndexAll() {
	}

	@Test
	public void testOptimizeIndex() {
	}

	@Test
	public void testRebuildIndex() {
	}

	@Test
	public void testSearchQuery() {
	}

	@Test
	public void testUnindex() {
	}

	@Test
	public void testUnindexAll() {
	}
}