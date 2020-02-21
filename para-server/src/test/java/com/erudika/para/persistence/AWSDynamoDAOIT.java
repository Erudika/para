/*
 * Copyright 2013-2020 Erudika. https://erudika.com
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

import com.erudika.para.core.App;
import com.erudika.para.core.Sysprop;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * DynamoDB integration test.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AWSDynamoDAOIT extends DAOTest {

	private static final String ROOT_APP_NAME = "para-test";

	public AWSDynamoDAOIT() {
		super(new AWSDynamoDAO());
	}

	@BeforeClass
	public static void setUpClass() throws InterruptedException {
		System.setProperty("para.prepend_shared_appids_with_space", "true");
		System.setProperty("para.app_name", ROOT_APP_NAME);
		AWSDynamoUtils.createTable(Config.getRootAppIdentifier());
		AWSDynamoUtils.createTable(appid1);
		AWSDynamoUtils.createTable(appid2);
		AWSDynamoUtils.createTable(appid3);
		AWSDynamoUtils.createSharedTable(1, 1);
	}

	@AfterClass
	public static void tearDownClass() {
		AWSDynamoUtils.deleteTable(Config.getRootAppIdentifier());
		AWSDynamoUtils.deleteTable(appid1);
		AWSDynamoUtils.deleteTable(appid2);
		AWSDynamoUtils.deleteTable(appid3);
		AWSDynamoUtils.deleteTable(AWSDynamoUtils.SHARED_TABLE);
		AWSDynamoUtils.shutdownClient();
		System.setProperty("para.prepend_shared_appids_with_space", "false");
	}

	@Test
	public void testCreateDeleteExistsTable() throws InterruptedException {
		String testappid1 = "test-index";
		String badAppid = "test index 123";

		AWSDynamoUtils.createTable("");
		assertFalse(AWSDynamoUtils.existsTable(""));

		AWSDynamoUtils.createTable(testappid1);
		assertTrue(AWSDynamoUtils.existsTable(testappid1));

		AWSDynamoUtils.deleteTable(testappid1);
		assertFalse(AWSDynamoUtils.existsTable(testappid1));

		assertFalse(AWSDynamoUtils.createTable(badAppid));
		assertFalse(AWSDynamoUtils.existsTable(badAppid));
		assertFalse(AWSDynamoUtils.deleteTable(badAppid));
	}

	@Test
	public void testCRUDSharedTable() {
		assertTrue(AWSDynamoUtils.existsTable(AWSDynamoUtils.SHARED_TABLE));

		App app = new App("shared-app1");
		App app2 = new App("shared-app2");
		app.setSharingTable(true);
		app2.setSharingTable(true);

		Sysprop s = new Sysprop("sharedobj1");
		s.setAppid(app.getAppIdentifier());
		assertNotNull(dao().create(app.getAppIdentifier(), s));

		assertNull(dao().read(app2.getAppIdentifier(), s.getId()));

		Sysprop sr = dao().read(s.getAppid(), s.getId());
		assertNull(dao().read(s.getId())); // not in root table
		assertFalse(AWSDynamoUtils.existsTable(s.getAppid().trim()));
		assertNotNull(sr);
		assertEquals(s.getId(), sr.getId());

		s.setName("I'm shared");
		dao().update(s.getAppid(), s);
		sr = dao().read(s.getAppid(), s.getId());
		assertNotNull(sr);
		assertNull(dao().read(s.getId())); // not in root table
		assertEquals("I'm shared", sr.getName());

		dao().delete(sr); // no effect - different App (root appid)
		assertNotNull(dao().read(s.getAppid(), s.getId())); // not in root table
		dao().delete(s.getAppid(), s);
		assertNull(dao().read(s.getAppid(), s.getId()));
	}

	@Test
	public void testBatchCRUDSharedTable() {
		Sysprop t1 = new Sysprop("sps1");
		Sysprop t2 = new Sysprop("sps2");
		Sysprop t3 = new Sysprop("sps3");
		Sysprop t4 = new Sysprop("sps4");
		App app1 = new App("batch-shared-app1");
		app1.setSharingTable(true);
		App app2 = new App("batch-shared-app2");
		app2.setSharingTable(true);

		t1.setAppid(app1.getAppIdentifier());
		t2.setAppid(app1.getAppIdentifier());
		t3.setAppid(app2.getAppIdentifier());
		t4.setAppid(app2.getAppIdentifier());

		// multi app support
		dao().createAll(app1.getAppIdentifier(), Arrays.asList(t1, t2));
		dao().createAll(app2.getAppIdentifier(), Arrays.asList(t3, t4));
		Sysprop s = dao().read(app1.getAppIdentifier(), t2.getId());
		assertNotNull(s);
		assertEquals(app1.getAppIdentifier(), s.getAppid());
		assertNull(dao().read(t2.getId()));
		assertNull(dao().read(app2.getAppIdentifier(), t2.getId()));

		assertNotNull(dao().read(app2.getAppIdentifier(), t3.getId()));
		assertNotNull(dao().read(app2.getAppIdentifier(), t4.getId()));

		// app1 must not see app2's objects, and vice versa
		Map<String, Sysprop> m1 = dao().readAll(app1.getAppIdentifier(), Arrays.asList(t3.getId(), t4.getId()), true);
		Map<String, Sysprop> m2 = dao().readAll(app2.getAppIdentifier(), Arrays.asList(t1.getId(), t2.getId()), true);
		assertNull(m1.get(t3.getId()));
		assertNull(m1.get(t4.getId()));
		assertNull(m2.get(t1.getId()));
		assertNull(m2.get(t2.getId()));

		Map<String, Sysprop> props = dao().readAll(app1.getAppIdentifier(), Arrays.asList(t1.getId(), t2.getId()), true);
		assertFalse(props.isEmpty());
		assertTrue(props.containsKey(t1.getId()));
		assertTrue(props.containsKey(t2.getId()));
		assertFalse(props.containsKey(t3.getId()));
		assertTrue(t1.equals(props.get(t1.getId())));
		assertTrue(t2.equals(props.get(t2.getId())));

		t1.setName("Name 1");
		t2.setName("Name 2");
		t3.setName("Name 3");
		// these should go through (custom types support)
		t1.setType("type1");
		t2.setType("type2");
		t3.setType("type3");

		dao().updateAll(app1.getAppIdentifier(), Arrays.asList(t1, t2));
		Sysprop tr1 = dao().read(app1.getAppIdentifier(), t1.getId());
		Sysprop tr2 = dao().read(app1.getAppIdentifier(), t2.getId());
		assertNull(dao().read(app1.getAppIdentifier(), t3.getId()));

		assertNotNull(tr1);
		assertNotNull(tr2);

		assertEquals(t1.getId(), tr1.getId());
		assertEquals(t2.getId(), tr2.getId());
		assertEquals(Utils.type(Sysprop.class), tr1.getType());
		assertEquals(Utils.type(Sysprop.class), tr2.getType());
		assertEquals(t1.getName(), tr1.getName());
		assertEquals(t2.getName(), tr2.getName());
		assertNotNull(t1.getUpdated());
		assertNotNull(t2.getUpdated());

		dao().deleteAll(null);
		dao().deleteAll(app1.getAppIdentifier(), Arrays.asList(t1, t2, t3));

		assertNull(dao().read(app1.getAppIdentifier(), t1.getId()));
		assertNull(dao().read(app1.getAppIdentifier(), t2.getId()));
		assertNotNull(dao().read(app2.getAppIdentifier(), t3.getId()));
		dao().deleteAll(app2.getAppIdentifier(), Arrays.asList(t3, t4));
		assertNull(dao().read(app2.getAppIdentifier(), t1.getId()));
		assertNull(dao().read(app2.getAppIdentifier(), t2.getId()));

		// update locked field test
		Sysprop ts4 = new Sysprop();
		ts4.setParentid("123");
		dao().create(app1.getAppIdentifier(), ts4);
		ts4.setParentid("321");
		ts4.setType("type4");
		dao().update(app1.getAppIdentifier(), ts4);
		Sysprop tr4 = dao().read(app1.getAppIdentifier(), ts4.getId());
		assertEquals(ts4.getId(), tr4.getId());
		assertEquals(Utils.type(Sysprop.class), tr4.getType());
		assertEquals("123", tr4.getParentid());
	}

	@Test
	public void testReadPageSharedTable() {
		App app3 = new App("shared-app3");
		App app4 = new App("shared-app4");
		app3.setSharingTable(true);
		app4.setSharingTable(true);

		ArrayList<Sysprop> list = new ArrayList<>();
		for (int i = 0; i < 22; i++) {
			Sysprop s = new Sysprop("id_" + i);
			s.addProperty("prop" + i, i);
			s.setAppid(app3.getAppIdentifier());
			list.add(s);
		}
		dao().createAll(app3.getAppIdentifier(), list);

		Sysprop s = new Sysprop("sharedobj2");
		assertNotNull(dao().create(app4.getAppIdentifier(), s));
		dao().create(app4.getAppIdentifier(), s);

		Pager p = new Pager(10);
		assertTrue(dao().readPage(null, null).isEmpty());
		assertFalse(dao().readPage(app3.getAppIdentifier(), null).isEmpty());
		assertEquals(10, dao().readPage(app3.getAppIdentifier(), p).size()); // page 1
		assertEquals(10, dao().readPage(app3.getAppIdentifier(), p).size()); // page 2
		assertEquals(2, dao().readPage(app3.getAppIdentifier(), p).size());  // page 3
		assertTrue(dao().readPage(app3.getAppIdentifier(), p).isEmpty());  // end
		assertEquals(22, p.getCount());

		assertEquals(1, dao().readPage(app4.getAppIdentifier(), null).size());

		// test deleteAllFromSharedTable()
		AWSDynamoUtils.deleteAllFromSharedTable(app3.getAppIdentifier());
		assertEquals(0, dao().readPage(app3.getAppIdentifier(), null).size());
		assertEquals(1, dao().readPage(app4.getAppIdentifier(), null).size());
	}

	@Test
	public void testReadAllPartial() {
		Sysprop s1 = new Sysprop("read-partially1");
		Sysprop s2 = new Sysprop("read-partially2");
		s1.setType("customtype");
		s2.setType("customtype");

		dao().create(ROOT_APP_NAME, s1);
		dao().create(ROOT_APP_NAME, s2);

		Map<String, Sysprop> res = dao().readAll(ROOT_APP_NAME, Arrays.asList(s1.getId(), s2.getId()), false);
		assertFalse(res.isEmpty());
		assertNotNull(res.get(s1.getId()));
		assertNotNull(res.get(s2.getId()));
		assertEquals(s1.getType(), res.get(s1.getId()).getType());
		assertEquals(s1.getType(), res.get(s2.getId()).getType());
		dao().deleteAll(ROOT_APP_NAME, Arrays.asList(s1, s2));
	}

	@Test
	public void testOptimisticLockingOnUpdate() {
		Sysprop s1 = new Sysprop("conditional-update1");
		s1.setVersion(1L); // enable optimistic locking on object
		dao().create(s1);
		assertEquals(Long.valueOf(1L), s1.getVersion());
		Sysprop sr1 = dao().read(s1.getId());
		assertEquals(Long.valueOf(1L), sr1.getVersion());
		sr1.setName("Updated");
		dao().update(sr1);
		assertEquals(Long.valueOf(2L), sr1.getVersion());
		s1.setName("FAIL");
		dao().update(s1);
		assertEquals(Long.valueOf(-1L), s1.getVersion()); // failed update
		Sysprop sr2 = dao().read(s1.getId());
		sr2.setName("SUCCESS");
		dao().update(sr2);
		assertEquals(Long.valueOf(3L), sr2.getVersion()); // successful update
		Sysprop sr3 = dao().read(s1.getId());
		assertEquals("SUCCESS", sr3.getName());

		sr3.setVersion(null); // disable optimistic locking
		dao().update(sr3);
		assertEquals(Long.valueOf(0L), sr3.getVersion());
		s1.setName("Disabled");
		dao().update(s1);
		Sysprop sr4 = dao().read(s1.getId());
		assertEquals("Disabled", sr4.getName());
	}
}
