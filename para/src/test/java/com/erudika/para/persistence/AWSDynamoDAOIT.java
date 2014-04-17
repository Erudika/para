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
package com.erudika.para.persistence;

import com.erudika.para.utils.Config;
import org.junit.AfterClass;
import static org.junit.Assert.assertFalse;
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
	}

	@AfterClass
	public static void tearDownClass() {
		AWSDynamoUtils.deleteTable(Config.APP_NAME_NS);
		AWSDynamoUtils.deleteTable(appid1);
		AWSDynamoUtils.deleteTable(appid2);
		AWSDynamoUtils.shutdownClient();
	}

	@Test
	public void testCreateDeleteExistsTable() throws InterruptedException {
		String appid1 = "test-index";
		String badAppid = "test index 123";

		AWSDynamoUtils.createTable("");
		assertFalse(AWSDynamoUtils.existsTable(""));

		AWSDynamoUtils.createTable(appid1);
		assertTrue(AWSDynamoUtils.existsTable(appid1));

		AWSDynamoUtils.deleteTable(appid1);
		assertFalse(AWSDynamoUtils.existsTable(appid1));

		assertFalse(AWSDynamoUtils.createTable(badAppid));
		assertFalse(AWSDynamoUtils.existsTable(badAppid));
		assertFalse(AWSDynamoUtils.deleteTable(badAppid));
	}

}
