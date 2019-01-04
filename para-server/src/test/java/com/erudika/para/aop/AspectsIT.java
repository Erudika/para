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
package com.erudika.para.aop;

import com.erudika.para.Para;
import com.erudika.para.ParaServer;
import com.erudika.para.cache.Cache;
import com.erudika.para.cache.MockCache;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.MockDAO;
import com.erudika.para.search.LuceneSearch;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Utils;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AspectsIT {

	private static final Logger logger = LoggerFactory.getLogger(AspectsIT.class);
	private static Sysprop s0;
	private static Sysprop s1;
	private static Sysprop s2;

	@BeforeClass
	public static void setUpClass() throws Exception {
		System.setProperty("para.env", "embedded");
		System.setProperty("para.print_logo", "false");
		System.setProperty("para.app_name", "para-test");
		System.setProperty("para.cluster_name", "para-test");
		System.setProperty("para.cache_enabled", "true");
		ParaServer.initialize(Modules.override(ParaServer.getCoreModules()).with(new Module() {
			public void configure(Binder binder) {
				binder.bind(DAO.class).to(MockDAO.class).asEagerSingleton();
				binder.bind(Cache.class).to(MockCache.class).asEagerSingleton();
				binder.bind(Search.class).to(LuceneSearch.class).asEagerSingleton();
			}
		}));

//		ElasticSearchUtils.createIndex(Config.getRootAppIdentifier());

		s0 = new Sysprop("s111");
		s0.setName("John Doe");
		s0.setTimestamp(Utils.timestamp());
		s0.setTags(CoreUtils.getInstance().addTags(s0.getTags(), "one", "two", "three"));

		s1 = new Sysprop("s222");
		s1.setName("Joe Black");
		s1.setTimestamp(Utils.timestamp());
		s1.setTags(CoreUtils.getInstance().addTags(s1.getTags(), "two", "four", "three"));

		s2 = new Sysprop("s333");
		s2.setName("Ann Smith");
		s2.setTimestamp(Utils.timestamp());
		s2.setTags(CoreUtils.getInstance().addTags(s2.getTags(), "four", "five", "three"));

		CoreUtils.getInstance().setDao(Para.getDAO());
		CoreUtils.getInstance().setCache(Para.getCache());
		CoreUtils.getInstance().setSearch(Para.getSearch());
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		Para.getDAO().deleteAll(Arrays.asList(s0, s1, s2));
		Para.destroy();
	}

	@Test
	public void test() throws InterruptedException {
		DAO d = Para.getDAO();
		Search s = Para.getSearch();
		Cache c = Para.getCache();
		System.setProperty("para.cache_enabled", "true");

		assertNotNull(s0.create());
		assertNotNull(d.read(s0.getId()));

		User uB = new User("invalid");
		uB.setIdentifier(null); // no identifier (username)
		uB.setPassword("badpass");
		uB.create();
		assertNull(d.read(uB.getId()));
		assertNull(s.findById(uB.getId()));
		assertNull(c.get(uB.getId()));

		uB.setEmail("tes1@test.com");
		uB.setIdentifier(uB.getEmail());
		uB.create();
		assertNotNull(d.read(uB.getId()));
		assertNotNull(s.findById(uB.getId()));
		assertNotNull(c.get(uB.getId()));

		logger.debug("---- cache remove -----");
		c.remove(uB.getId());
		assertNotNull(d.read(uB.getId()));
		assertTrue(c.contains(uB.getId()));
		logger.debug("---------");

		uB.delete();
		assertNull(d.read(uB.getId()));
		assertNull(s.findById(uB.getId()));
		assertNull(c.get(uB.getId()));

		ArrayList<Sysprop> list = new ArrayList<>();
		list.add(s0);
		list.add(s1);
		list.add(s2);

		d.createAll(list);
		assertNotNull(d.read(s0.getId()));
		assertNotNull(s.findById(s0.getId()));
		assertNotNull(c.get(s0.getId()));

		assertNotNull(d.read(s1.getId()));
		assertNotNull(s.findById(s1.getId()));
		assertNotNull(c.get(s1.getId()));

		assertNotNull(d.read(s2.getId()));
		assertNotNull(s.findById(s2.getId()));
		assertNotNull(c.get(s2.getId()));

		logger.debug("---- read all from cache ----");
		Map<String, User> map = d.readAll(Arrays.asList(s0.getId(), s1.getId(), s2.getId()), true);
		assertTrue(map.containsKey(s0.getId()));
		assertTrue(map.containsKey(s1.getId()));
		assertTrue(map.containsKey(s2.getId()));

		logger.debug("---- cache remove ----");
		c.remove(s1.getId());
		c.remove(s2.getId());
		d.readAll(Arrays.asList(s0.getId(), s1.getId(), s2.getId()), true);
		assertTrue(c.contains(s1.getId()));
		assertTrue(c.contains(s2.getId()));

		logger.debug("---- delete all ----");
		d.deleteAll(list);
		Thread.sleep(500);
		assertNull(d.read(s0.getId()));
		assertNull(s.findById(s0.getId()));
		assertNull(c.get(s0.getId()));

		assertNull(d.read(s1.getId()));
		assertNull(s.findById(s1.getId()));
		assertNull(c.get(s1.getId()));

		assertNull(d.read(s2.getId()));
		assertNull(s.findById(s2.getId()));
		assertNull(c.get(s2.getId()));

		// Removed in v1.18 - this will cause unexpected behavior in the future.
		// These "special" classes are not documented and this is the wrong
		// place to filter them out.

		// test skipping special classes calling batch methods
//		App app1 = new App("app1");
//		User user1 = new User("user1");
//		user1.setName("John Doe");
//		user1.setGroups(User.Groups.USERS.toString());
//		user1.setEmail("john1@asd.com");
//		user1.setIdentifier(user1.getEmail());
//		user1.setTimestamp(Utils.timestamp());
//		user1.setPassword("123456");
//		Tag t1 = new Tag("testtag123");
//
//		// test that Apps and Users are removed from batch operations
//		ArrayList<ParaObject> list1 = new ArrayList<ParaObject>();
//		list1.add(app1);
//		list1.add(user1);
//		list1.add(t1);
//		Para.getDAO().createAll(list1);
//
//		System.setProperty("para.cache_enabled", "false");
//		assertNull(Para.getDAO().read(app1.getId()));
//		assertNull(Para.getDAO().read(user1.getId()));
//		assertNotNull(Para.getDAO().read(t1.getId()));
//
//		assertNull(Para.getSearch().findById(app1.getId()));
//		assertNull(Para.getSearch().findById(user1.getId()));
//		assertNotNull(Para.getSearch().findById(t1.getId()));
//
//		System.setProperty("para.cache_enabled", "true");
//		assertFalse(Para.getCache().contains(app1.getId()));
//		assertFalse(Para.getCache().contains(user1.getId()));
//		assertTrue(Para.getCache().contains(t1.getId()));
	}

	@Test
	public void testFlags() throws InterruptedException {
		// default - store=true, index=true, cache=true
		Tag t1 = new Tag("tag1");
		Sysprop o11 = new Sysprop("obj11");
		Sysprop o12 = new Sysprop("obj12");
		Para.getDAO().create(t1);
		Para.getDAO().createAll(new LinkedList<>(Arrays.asList(o11, o12)));

		assertNotNull(Para.getDAO().read(t1.getId()));
		assertNotNull(Para.getDAO().read(o11.getId()));
		assertNotNull(Para.getDAO().read(o12.getId()));
		assertNotNull(Para.getSearch().findById(t1.getId()));
		assertNotNull(Para.getSearch().findById(o11.getId()));
		assertNotNull(Para.getSearch().findById(o12.getId()));
		assertNotNull(Para.getCache().get(t1.getId()));
		assertNotNull(Para.getCache().get(o11.getId()));
		assertNotNull(Para.getCache().get(o12.getId()));
		Para.getDAO().deleteAll(Arrays.asList(t1, o11, o12));
		assertNull(Para.getDAO().read(t1.getId()));
		assertNull(Para.getDAO().read(o11.getId()));
		assertNull(Para.getDAO().read(o12.getId()));
		assertNull(Para.getSearch().findById(t1.getId()));
		assertNull(Para.getSearch().findById(o11.getId()));
		assertNull(Para.getSearch().findById(o12.getId()));
		assertNull(Para.getCache().get(t1.getId()));
		assertNull(Para.getCache().get(o11.getId()));
		assertNull(Para.getCache().get(o12.getId()));
		// special case - readAll should always return an empty list and never null
		Map<String, ?> deleted = Para.getDAO().readAll(Arrays.asList(t1.getId(), o11.getId(), o12.getId()), true);
		assertNotNull(deleted);
		assertTrue(deleted.isEmpty());

		// not in DB - store=false, index=true, cache=true
		Tag t2 = new Tag("tag2");
		Sysprop o21 = new Sysprop("obj21");
		Sysprop o22 = new Sysprop("obj22");
		t2.setStored(false);
		o21.setStored(false);
		o22.setStored(false);
		Para.getDAO().create(t2);
		Para.getDAO().createAll(new LinkedList<>(Arrays.asList(o21, o22)));

		System.setProperty("para.cache_enabled", "false");
		assertNull(Para.getDAO().read(t2.getId()));
		assertNull(Para.getDAO().read(o21.getId()));
		assertNull(Para.getDAO().read(o22.getId()));
		assertNotNull(Para.getSearch().findById(t2.getId()));
		assertNotNull(Para.getSearch().findById(o21.getId()));
		assertNotNull(Para.getSearch().findById(o22.getId()));
		System.setProperty("para.cache_enabled", "true");
		assertNotNull(Para.getCache().get(t2.getId()));
		assertNotNull(Para.getCache().get(o21.getId()));
		assertNotNull(Para.getCache().get(o22.getId()));
		Para.getDAO().deleteAll(Arrays.asList(t2, o21, o22));
		assertNull(Para.getDAO().read(t2.getId()));
		assertNull(Para.getDAO().read(o21.getId()));
		assertNull(Para.getDAO().read(o22.getId()));
		assertNull(Para.getSearch().findById(t2.getId()));
		assertNull(Para.getSearch().findById(o21.getId()));
		assertNull(Para.getSearch().findById(o22.getId()));
		assertNull(Para.getCache().get(t2.getId()));
		assertNull(Para.getCache().get(o21.getId()));
		assertNull(Para.getCache().get(o22.getId()));

		// in memory - store=false, index=false, cache=true
		Tag t3 = new Tag("tag3");
		Sysprop o31 = new Sysprop("obj31");
		Sysprop o32 = new Sysprop("obj32");
		t3.setStored(false);
		t3.setIndexed(false);
		o31.setStored(false);
		o31.setIndexed(false);
		o32.setStored(false);
		o32.setIndexed(false);
		Para.getDAO().create(t3);
		Para.getDAO().createAll(new LinkedList<>(Arrays.asList(o31, o32)));

		System.setProperty("para.cache_enabled", "false");
		assertNull(Para.getDAO().read(t3.getId()));
		assertNull(Para.getDAO().read(o31.getId()));
		assertNull(Para.getDAO().read(o32.getId()));
		assertNull(Para.getSearch().findById(t3.getId()));
		assertNull(Para.getSearch().findById(o31.getId()));
		assertNull(Para.getSearch().findById(o32.getId()));
		System.setProperty("para.cache_enabled", "true");
		assertNotNull(Para.getCache().get(t3.getId()));
		assertNotNull(Para.getCache().get(o31.getId()));
		assertNotNull(Para.getCache().get(o32.getId()));
		Para.getDAO().deleteAll(Arrays.asList(t3, o31, o32));
		assertNull(Para.getDAO().read(t3.getId()));
		assertNull(Para.getDAO().read(o31.getId()));
		assertNull(Para.getDAO().read(o32.getId()));
		assertNull(Para.getSearch().findById(t3.getId()));
		assertNull(Para.getSearch().findById(o31.getId()));
		assertNull(Para.getSearch().findById(o32.getId()));
		assertNull(Para.getCache().get(t3.getId()));
		assertNull(Para.getCache().get(o31.getId()));
		assertNull(Para.getCache().get(o32.getId()));

		// not cached - store=true, index=true, cache=false
		Tag t4 = new Tag("tag4");
		Sysprop o41 = new Sysprop("obj41");
		Sysprop o42 = new Sysprop("obj42");
		t4.setCached(false);
		o41.setCached(false);
		o42.setCached(false);
		Para.getDAO().create(t4);
		Para.getDAO().createAll(new LinkedList<>(Arrays.asList(o41, o42)));

		System.setProperty("para.cache_enabled", "false");
		assertNotNull(Para.getDAO().read(t4.getId()));
		assertNotNull(Para.getDAO().read(o41.getId()));
		assertNotNull(Para.getDAO().read(o42.getId()));
		assertNotNull(Para.getSearch().findById(t4.getId()));
		assertNotNull(Para.getSearch().findById(o41.getId()));
		assertNotNull(Para.getSearch().findById(o42.getId()));
		System.setProperty("para.cache_enabled", "true");
		assertNull(Para.getCache().get(t4.getId()));
		assertNull(Para.getCache().get(o41.getId()));
		assertNull(Para.getCache().get(o42.getId()));
		Para.getDAO().deleteAll(Arrays.asList(t4, o41, o42));
		assertNull(Para.getDAO().read(t4.getId()));
		assertNull(Para.getDAO().read(o41.getId()));
		assertNull(Para.getDAO().read(o42.getId()));
		assertNull(Para.getSearch().findById(t4.getId()));
		assertNull(Para.getSearch().findById(o41.getId()));
		assertNull(Para.getSearch().findById(o42.getId()));
		assertNull(Para.getCache().get(t4.getId()));
		assertNull(Para.getCache().get(o41.getId()));
		assertNull(Para.getCache().get(o42.getId()));

		// only in DB - store=true, index=false, cache=false
		Tag t5 = new Tag("tag5");
		Sysprop o51 = new Sysprop("obj51");
		Sysprop o52 = new Sysprop("obj52");
		t5.setIndexed(false);
		t5.setCached(false);
		o51.setIndexed(false);
		o51.setCached(false);
		o52.setIndexed(false);
		o52.setCached(false);
		Para.getDAO().create(t5);
		Para.getDAO().createAll(new LinkedList<>(Arrays.asList(o51, o52)));

		System.setProperty("para.cache_enabled", "false");
		assertNotNull(Para.getDAO().read(t5.getId()));
		assertNotNull(Para.getDAO().read(o51.getId()));
		assertNotNull(Para.getDAO().read(o52.getId()));
		assertNull(Para.getSearch().findById(t5.getId()));
		assertNull(Para.getSearch().findById(o51.getId()));
		assertNull(Para.getSearch().findById(o52.getId()));
		System.setProperty("para.cache_enabled", "true");
		assertNull(Para.getCache().get(t5.getId()));
		assertNull(Para.getCache().get(o51.getId()));
		assertNull(Para.getCache().get(o52.getId()));
		Para.getDAO().deleteAll(Arrays.asList(t5, o51, o52));
		assertNull(Para.getDAO().read(t5.getId()));
		assertNull(Para.getDAO().read(o51.getId()));
		assertNull(Para.getDAO().read(o52.getId()));
		assertNull(Para.getSearch().findById(t5.getId()));
		assertNull(Para.getSearch().findById(o51.getId()));
		assertNull(Para.getSearch().findById(o52.getId()));
		assertNull(Para.getCache().get(t5.getId()));
		assertNull(Para.getCache().get(o51.getId()));
		assertNull(Para.getCache().get(o52.getId()));

		// only in index - store=false, index=true, cache=false
		Tag t6 = new Tag("tag6");
		Sysprop o61 = new Sysprop("obj61");
		Sysprop o62 = new Sysprop("obj62");
		o61.addProperty("dont_lose_this", o61.getId());
		o62.addProperty("dont_lose_this", o62.getId());
		t6.setStored(false);
		t6.setCached(false);
		o61.setStored(false);
		o61.setCached(false);
		o62.setStored(false);
		o62.setCached(false);
		Para.getDAO().create(t6);
		Para.getDAO().createAll(new LinkedList<>(Arrays.asList(o61, o62)));
		Thread.sleep(1000);

		System.setProperty("para.cache_enabled", "false");
		assertNull(Para.getDAO().read(t6.getId()));
		assertNull(Para.getDAO().read(o61.getId()));
		assertNull(Para.getDAO().read(o62.getId()));
		assertNotNull(Para.getSearch().findById(t6.getId()));
		assertNotNull(Para.getSearch().findById(o61.getId()));
		assertNotNull(Para.getSearch().findById(o62.getId()));
		// special case: read_from_index (query multiple objects)
		List<?> results = Para.getSearch().findByIds(Arrays.asList(o61.getId(), o62.getId()));
		assertNotNull(results);
		assertFalse(results.isEmpty());
		assertEquals(2, results.size());
		Sysprop first = ((Sysprop) results.get(0));
		Sysprop second = ((Sysprop) results.get(1));
		assertEquals(first.getId(), first.getProperty("dont_lose_this"));
		assertEquals(second.getId(), second.getProperty("dont_lose_this"));

		System.setProperty("para.cache_enabled", "true");
		assertNull(Para.getCache().get(t6.getId()));
		assertNull(Para.getCache().get(o61.getId()));
		assertNull(Para.getCache().get(o62.getId()));
		Para.getDAO().deleteAll(Arrays.asList(t6, o61, o62));
		assertNull(Para.getDAO().read(t6.getId()));
		assertNull(Para.getDAO().read(o61.getId()));
		assertNull(Para.getDAO().read(o62.getId()));
		assertNull(Para.getSearch().findById(t6.getId()));
		assertNull(Para.getSearch().findById(o61.getId()));
		assertNull(Para.getSearch().findById(o62.getId()));
		assertNull(Para.getCache().get(t6.getId()));
		assertNull(Para.getCache().get(o61.getId()));
		assertNull(Para.getCache().get(o62.getId()));
	}

}