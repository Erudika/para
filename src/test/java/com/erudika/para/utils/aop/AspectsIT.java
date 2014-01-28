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
package com.erudika.para.utils.aop;

import com.erudika.para.Para;
import com.erudika.para.cache.Cache;
import com.erudika.para.cache.MockCache;
import com.erudika.para.core.User;
import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.MockDAO;
import com.erudika.para.search.ElasticSearchUtils;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
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
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class AspectsIT {
	
	private static final Logger logger = LoggerFactory.getLogger(AspectsIT.class);
	private static User u;
	private static User u1;
	private static User u2;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		System.setProperty("esembedded", "true");
		Para.initialize(new Module() {
			public void configure(Binder binder) {
				binder.bind(DAO.class).to(MockDAO.class);
				binder.bind(Cache.class).to(MockCache.class);				
			}
		});
		
		ElasticSearchUtils.createIndex(Config.APP_NAME_NS);
		
		u = new User("111");
		u.setName("John Doe");
		u.setGroups(User.Groups.USERS.toString());
		u.setEmail("john@asd.com");
		u.setIdentifier(u.getEmail());
		u.setTimestamp(System.currentTimeMillis());
		u.setPassword("123456");
		u.addTags("one", "two", "three");
		
		u1 = new User("222");
		u1.setName("Joe Black");
		u1.setGroups(User.Groups.USERS.toString());
		u1.setEmail("joe@asd.com");
		u1.setIdentifier(u1.getEmail());
		u1.setTimestamp(System.currentTimeMillis());
		u1.setPassword("123456");
		u1.addTags("two", "four", "three");
		
		u2 = new User("333");
		u2.setName("Ann Smith");
		u2.setGroups(User.Groups.USERS.toString());
		u2.setEmail("ann@asd.com");
		u2.setIdentifier(u2.getEmail());
		u2.setTimestamp(System.currentTimeMillis());
		u2.setPassword("123456");
		u2.addTags("four", "five", "three");
		
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		ElasticSearchUtils.deleteIndex(Config.APP_NAME_NS);
		Para.destroy();
		u = null;
		u1 = null;
		u2 = null;
	}

	@Test
	public void test() {
		DAO d = Para.getDAO();
		Search s = Para.getSearch();
		Cache c = Para.getCache();
		
		assertNotNull(u.create());
		assertNotNull(d.read(u.getId()));
		
		User uB = new User("invalid");
		uB.setIdentifier("badident");
		uB.setPassword("badpass");
		uB.create();
		assertNull(d.read(uB.getId()));
		
		uB.setEmail("tes1@test.com");
		uB.setIdentifier(uB.getEmail());
		
		uB.create();
		assertNotNull(d.read(uB.getId()));
		assertNotNull(s.findById(uB.getId(), uB.getClassname()));
		assertNotNull(c.get(uB.getId()));
		
		logger.debug("---- cache remove -----");
		c.remove(uB.getId());
		assertNotNull(d.read(uB.getId()));
		assertTrue(c.contains(uB.getId()));
		logger.debug("---------");
		
		uB.delete();
		assertNull(d.read(uB.getId()));
		assertNull(s.findById(uB.getId(), uB.getClassname()));
		assertNull(c.get(uB.getId()));
		
		ArrayList<User> list = new ArrayList<User>();
		list.add(u);
		list.add(u1);
		list.add(u2);
		
		d.createAll(list);
		assertNotNull(d.read(u.getId()));
		assertNotNull(s.findById(u.getId(), u.getClassname()));
		assertNotNull(c.get(u.getId()));
		
		assertNotNull(d.read(u1.getId()));
		assertNotNull(s.findById(u1.getId(), u1.getClassname()));
		assertNotNull(c.get(u1.getId()));
		
		assertNotNull(d.read(u2.getId()));
		assertNotNull(s.findById(u2.getId(), u2.getClassname()));
		assertNotNull(c.get(u2.getId()));
		
		logger.debug("---- read all from cache ----");
		Map<String, User> map = d.readAll(Arrays.asList(u.getId(), u1.getId(), u2.getId()), true);
		assertTrue(map.containsKey(u.getId()));
		assertTrue(map.containsKey(u1.getId()));
		assertTrue(map.containsKey(u2.getId()));
		
		logger.debug("---- cache remove ----");	
		c.remove(u1.getId());
		c.remove(u2.getId());
		d.readAll(Arrays.asList(u.getId(), u1.getId(), u2.getId()), true);
		assertTrue(c.contains(u1.getId()));
		assertTrue(c.contains(u2.getId()));
		
		logger.debug("---- delete all ----");	
		d.deleteAll(list);
		assertNull(d.read(u.getId()));
		assertNull(s.findById(u.getId(), u.getClassname()));
		assertNull(c.get(u.getId()));
		
		assertNull(d.read(u1.getId()));
		assertNull(s.findById(u1.getId(), u1.getClassname()));
		assertNull(c.get(u1.getId()));
		
		assertNull(d.read(u2.getId()));
		assertNull(s.findById(u2.getId(), u2.getClassname()));
		assertNull(c.get(u2.getId()));
	}

}