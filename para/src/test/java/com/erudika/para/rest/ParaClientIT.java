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
package com.erudika.para.rest;

import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import com.erudika.para.search.ElasticSearchUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import java.util.ArrayList;
import java.util.Collections;
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
public class ParaClientIT {

	private static final Logger logger = LoggerFactory.getLogger(ParaClientIT.class);
	private static ParaClient pc;
	private static final String catsType = "cat";
	private static final String dogsType = "dog";

	@BeforeClass
	public static void setUpClass() {
		System.setProperty("para.env", "embedded");
		System.setProperty("para.cluster_name", Config.PARA + "-test");
		Para.main(new String[0]);
		Para.getDAO().delete(new App(Config.PARA));
		ParaClient temp = new ParaClient("", "");
		Map<String, String> credentials = temp.setup();
		if (credentials != null && credentials.containsKey("accessKey")) {
			String accessKey = credentials.get("accessKey");
			String secretKey = credentials.get("secretKey");
			pc = new ParaClient(accessKey, secretKey);
			logger.info("accessKey: {}, secretKey: {}", accessKey, secretKey);
		}
	}

	@AfterClass
	public static void tearDownClass() {
//		Para.destroy();
		Para.getDAO().delete(new App(Config.PARA));
		ElasticSearchUtils.deleteIndex(Config.PARA);
	}

	@Test
	public void testCreateReadUpdateDelete() {
		assertNull(pc.create(null));

		Tag t1 = pc.create(new Tag("test1"));
		User u1 = null;
		try {
			// validation fails
			u1 = pc.create(new User("u1"));
		} catch (Exception e) {}

		assertNotNull(t1);
		assertNull(u1);

		assertNull(pc.read(null, null));
		assertNull(pc.read("", ""));

		Tag tr = pc.read(t1.getType(), t1.getId());
		assertNotNull(tr);
		assertNotNull(tr.getTimestamp());
		assertEquals(t1.getTag(), t1.getTag());

		tr.setCount(15);
		Tag tu = pc.update(tr);
		assertNotNull(tu);
		assertEquals(tr.getCount(), tu.getCount());
		assertNotNull(tu.getUpdated());

		Sysprop s = new Sysprop();
		s.setType(dogsType);
		s.addProperty("foo", "bark!");
		s = pc.create(s);

		Sysprop dog = pc.read(dogsType, s.getId());
		assertTrue(dog.hasProperty("foo"));
		assertEquals("bark!", dog.getProperty("foo"));

		pc.delete(t1);
		assertNull(pc.read(tr.getType(), tr.getId()));
	}

	@Test
	public void testCreateReadUpdateDeleteAll() throws InterruptedException {
		ArrayList<Sysprop> list = new ArrayList<Sysprop>();

		for (int i = 0; i < 3; i++) {
			Sysprop s = new Sysprop();
			s.setType(dogsType);
			s.addProperty("foo", "bark!");
			list.add(s);
		}

		assertTrue(pc.createAll(null).isEmpty());
		assertTrue(pc.createAll(Collections.singletonList(null)).isEmpty());
		List<Sysprop> l1 = pc.createAll(list);
		assertEquals(3, l1.size());
		assertNotNull(l1.get(0).getId());

		assertTrue(pc.readAll(null).isEmpty());
		ArrayList<String> nl = new ArrayList<String>(3);
		assertTrue(pc.readAll(nl).isEmpty());
		nl.add(l1.get(0).getId());
		nl.add(l1.get(1).getId());
		nl.add(l1.get(2).getId());
		List<Sysprop> l2 = pc.readAll(nl);
		assertEquals(3, l2.size());
		assertEquals(l1.get(0).getId(), l2.get(0).getId());
		assertTrue(l2.get(0).hasProperty("foo"));
		assertEquals("bark!", l2.get(0).getProperty("foo"));

		assertTrue(pc.updateAll(null).isEmpty());
		assertTrue(pc.updateAll(Collections.singletonList(null)).isEmpty());
		l1.get(0).setName("NewName1");
		l1.get(1).setName("NewName2");
		l1.get(2).setName("NewName3");
		List<Sysprop> l3 = pc.updateAll(l1);
		assertEquals(l1.get(0).getName(), l3.get(0).getName());
		assertEquals(l1.get(1).getName(), l3.get(1).getName());
		assertEquals(l1.get(2).getName(), l3.get(2).getName());

		pc.deleteAll(nl);
		Thread.sleep(1000);

		List<Sysprop> l4 = pc.list(dogsType);
		assertTrue(l4.isEmpty());

		assertTrue(pc.getApp().getDatatypes().containsValue(dogsType));
	}

	@Test
	public void testList() throws InterruptedException {
		ArrayList<ParaObject> cats = new ArrayList<ParaObject>();
		for (int i = 0; i < 3; i++) {
			Sysprop s = new Sysprop(catsType + i);
			s.setType(catsType);
			cats.add(s);
		}
		pc.createAll(cats);
		Thread.sleep(1000);

		assertTrue(pc.list(null).isEmpty());
		assertTrue(pc.list("").isEmpty());

		List<Sysprop> list1 = pc.list(catsType);
		assertFalse(list1.isEmpty());
		assertEquals(3, list1.size());

		List<Sysprop> list2 = pc.list(catsType, new Pager(2));
		assertFalse(list2.isEmpty());
		assertEquals(2, list2.size());

		assertTrue(pc.getApp().getDatatypes().containsValue(catsType));
	}


	@Test
	public void testRead() {

	}
//	@Test
//	public void testRead() {
//
//	}
//	@Test
//	public void testRead() {
//
//	}

	@Test
	public void testGetTimestamp() {
		final Long t = pc.getTimestamp();
		assertNotNull(t);
	}
}
