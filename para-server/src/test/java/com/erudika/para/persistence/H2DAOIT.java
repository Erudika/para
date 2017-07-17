/*
 * Copyright 2013-2017 Erudika. https://erudika.com
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

import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class H2DAOIT extends DAOTest {

	public H2DAOIT() {
		super(new H2DAO());
	}

	@BeforeClass
	public static void setUpClass() throws InterruptedException {
//		H2Utils.createTable(Config.getRootAppIdentifier());
		H2Utils.createTable(appid1);
		H2Utils.createTable(appid2);
		H2Utils.createTable(appid3);
	}

	@AfterClass
	public static void tearDownClass() {
//		H2Utils.deleteTable(Config.getRootAppIdentifier());
		H2Utils.deleteTable(appid1);
		H2Utils.deleteTable(appid2);
		H2Utils.deleteTable(appid3);
		H2Utils.shutdownClient();
	}

}
