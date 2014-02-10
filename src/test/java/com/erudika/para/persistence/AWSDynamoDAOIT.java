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
package com.erudika.para.persistence;

import com.erudika.para.utils.Config;
import org.junit.AfterClass;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class AWSDynamoDAOIT extends DAOTest {

	@BeforeClass
	public static void setUpClass() throws InterruptedException {
		dao = new AWSDynamoDAO();
		AWSDynamoUtils.createTable(Config.APP_NAME_NS);
		AWSDynamoUtils.createTable(appName1);
		AWSDynamoUtils.createTable(appName2);
	}

	@AfterClass
	public static void tearDownClass() {
		AWSDynamoUtils.deleteTable(Config.APP_NAME_NS);
		AWSDynamoUtils.deleteTable(appName1);
		AWSDynamoUtils.deleteTable(appName2);
		AWSDynamoUtils.shutdownClient();
	}

	@Test
	public void testCreateDeleteExistsTable() throws InterruptedException {
		String appName1 = "test-index";
		String badAppName = "test index 123";

		AWSDynamoUtils.createTable("");
		assertFalse(AWSDynamoUtils.existsTable(""));

		AWSDynamoUtils.createTable(appName1);
		assertTrue(AWSDynamoUtils.existsTable(appName1));

		AWSDynamoUtils.deleteTable(appName1);
		assertFalse(AWSDynamoUtils.existsTable(appName1));

		assertFalse(AWSDynamoUtils.createTable(badAppName));
		assertFalse(AWSDynamoUtils.existsTable(badAppName));
		assertFalse(AWSDynamoUtils.deleteTable(badAppName));
	}

}
