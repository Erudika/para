/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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
import com.erudika.para.cache.Cache;
import com.erudika.para.cache.MockCache;
import com.erudika.para.core.App;
import com.erudika.para.core.CoreUtils;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.MockDAO;
import com.erudika.para.search.ElasticSearchUtils;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.google.inject.Binder;
import com.google.inject.Module;
import java.util.ArrayList;
import java.util.Arrays;
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
		System.setProperty("para.app_name", "para-test");
		System.setProperty("para.cluster_name", "para-test");
		System.setProperty("para.cache_enabled", "true");
		Para.initialize(new Module() {
			public void configure(Binder binder) {
				binder.bind(DAO.class).to(MockDAO.class);
				binder.bind(Cache.class).to(MockCache.class);
			}
		});

		ElasticSearchUtils.createIndex(Config.APP_NAME_NS);

		s0 = new Sysprop("111");
		s0.setName("John Doe");
		s0.setTimestamp(Utils.timestamp());
		s0.setTags(CoreUtils.addTags(s0.getTags(), "one", "two", "three"));

		s1 = new Sysprop("222");
		s1.setName("Joe Black");
		s1.setTimestamp(Utils.timestamp());
		s1.setTags(CoreUtils.addTags(s1.getTags(), "two", "four", "three"));

		s2 = new Sysprop("333");
		s2.setName("Ann Smith");
		s2.setTimestamp(Utils.timestamp());
		s2.setTags(CoreUtils.addTags(s2.getTags(), "four", "five", "three"));

	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		ElasticSearchUtils.deleteIndex(Config.APP_NAME_NS);
		Para.destroy();
		s0 = null;
		s1 = null;
		s2 = null;
	}

	@Test
	public void test() {
		DAO d = Para.getDAO();
		Search s = Para.getSearch();
		Cache c = Para.getCache();

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

		ArrayList<Sysprop> list = new ArrayList<Sysprop>();
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
		assertNull(d.read(s0.getId()));
		assertNull(s.findById(s0.getId()));
		assertNull(c.get(s0.getId()));

		assertNull(d.read(s1.getId()));
		assertNull(s.findById(s1.getId()));
		assertNull(c.get(s1.getId()));

		assertNull(d.read(s2.getId()));
		assertNull(s.findById(s2.getId()));
		assertNull(c.get(s2.getId()));

		// test skipping special classes calling batch methods
		App app1 = new App("app1");
		User user1 = new User("user1");
		user1.setName("John Doe");
		user1.setGroups(User.Groups.USERS.toString());
		user1.setEmail("john1@asd.com");
		user1.setIdentifier(user1.getEmail());
		user1.setTimestamp(Utils.timestamp());
		user1.setPassword("123456");
		Tag t1 = new Tag("testtag123");

		ArrayList<ParaObject> list1 = new ArrayList<ParaObject>();
		list1.add(app1);
		list1.add(user1);
		list1.add(t1);
		Para.getDAO().createAll(list1);

		assertNull(Para.getDAO().read(app1.getId()));
		assertNull(Para.getDAO().read(user1.getId()));
		assertNotNull(Para.getDAO().read(t1.getId()));

		assertNull(Para.getSearch().findById(app1.getId()));
		assertNull(Para.getSearch().findById(user1.getId()));
		assertNotNull(Para.getSearch().findById(t1.getId()));

		assertFalse(Para.getCache().contains(app1.getId()));
		assertFalse(Para.getCache().contains(user1.getId()));
		assertTrue(Para.getCache().contains(t1.getId()));


	}

}