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

import com.erudika.para.utils.Config;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * DynamoDB integration test.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class MapDBDAOIT extends DAOTest {

	public MapDBDAOIT() {
		super(new MapDBDAO());
	}

	@BeforeClass
	public static void setUpClass() throws InterruptedException {
	}

	@AfterClass
	public static void tearDownClass() {
		MapDBDAO.getDBFileForAppid(Config.APP_NAME_NS).delete();
		MapDBDAO.getDBFileForAppid(appid1).delete();
		MapDBDAO.getDBFileForAppid(appid2).delete();
		MapDBDAO.getDBFileForAppid(appid3).delete();
	}

}
