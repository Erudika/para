/*
 * Copyright 2013-2016 Erudika. http://erudika.com
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
import static com.erudika.para.persistence.DAOTest.dao;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import java.util.ArrayList;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AWSDynamoDAOIT extends DAOTest {

	@BeforeClass
	public static void setUpClass() throws InterruptedException {
		dao = new AWSDynamoDAO();
		AWSDynamoUtils.createTable(Config.APP_NAME_NS);
		AWSDynamoUtils.createTable(appid1);
		AWSDynamoUtils.createTable(appid2);
		AWSDynamoUtils.createTable(appid3);
		AWSDynamoUtils.createSharedTable(1, 1);
	}

	@AfterClass
	public static void tearDownClass() {
		AWSDynamoUtils.deleteTable(Config.APP_NAME_NS);
		AWSDynamoUtils.deleteTable(appid1);
		AWSDynamoUtils.deleteTable(appid2);
		AWSDynamoUtils.deleteTable(appid3);
		AWSDynamoUtils.deleteTable(AWSDynamoUtils.SHARED_TABLE);
		AWSDynamoUtils.shutdownClient();
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
		assertNotNull(dao.create(app.getAppIdentifier(), s));

		assertNull(dao.read(app2.getAppIdentifier(), s.getId()));

		Sysprop sr = dao.read(s.getAppid(), s.getId());
		assertNull(dao.read(s.getId())); // not in root table
		assertFalse(AWSDynamoUtils.existsTable(s.getAppid().trim()));
		assertNotNull(sr);
		assertEquals(s.getId(), sr.getId());

		s.setName("I'm shared");
		dao.update(s.getAppid(), s);
		sr = dao.read(s.getAppid(), s.getId());
		assertNull(dao.read(s.getId())); // not in root table
		assertEquals("I'm shared", sr.getName());

		dao.delete(sr);
		assertNotNull(dao.read(s.getAppid(), s.getId())); // not in root table
		dao.delete(s.getAppid(), s);
		assertNull(dao.read(s.getAppid(), s.getId()));
	}

	@Test
	public void testBatchCRUDSharedTable() {

	}

	@Test
	public void testReadPageSharedTable() {
		App app = new App("shared-app2");
		App app2 = new App("shared-app3");
		app.setSharingTable(true);
		app2.setSharingTable(true);

		ArrayList<Sysprop> list = new ArrayList<Sysprop>();
		for (int i = 0; i < 22; i++) {
			Sysprop s = new Sysprop("id_" + i);
			s.addProperty("prop" + i, i);
			s.setAppid(app.getAppIdentifier());
			list.add(s);
		}
		dao.createAll(app.getAppIdentifier(), list);

		Sysprop s = new Sysprop("sharedobj2");
		assertNotNull(dao.create(app2.getAppIdentifier(), s));
		dao.create(app2.getAppIdentifier(), s);

		Pager p = new Pager(10);
		assertTrue(dao.readPage(null, null).isEmpty());
		assertFalse(dao.readPage(app.getAppIdentifier(), null).isEmpty());
		assertEquals(10, dao.readPage(app.getAppIdentifier(), p).size()); // page 1
		assertEquals(10, dao.readPage(app.getAppIdentifier(), p).size()); // page 2
		assertEquals(2, dao.readPage(app.getAppIdentifier(), p).size());  // page 3
		assertTrue(dao.readPage(app.getAppIdentifier(), p).isEmpty());  // end
		assertEquals(22, p.getCount());

		assertEquals(1, dao.readPage(app2.getAppIdentifier(), null).size());
	}

}
