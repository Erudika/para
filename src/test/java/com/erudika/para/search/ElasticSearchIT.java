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
package com.erudika.para.search;

import com.erudika.para.persistence.DAO;
import com.erudika.para.utils.Config;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class ElasticSearchIT extends SearchTest {

	@BeforeClass
	public static void setUpClass() {
		System.setProperty("esembedded", "true");
		s = new ElasticSearch(mock(DAO.class));
		ElasticSearchUtils.createIndex(Config.APP_NAME_NS);
		ElasticSearchUtils.createIndex(appName1);
		ElasticSearchUtils.createIndex(appName2);
		SearchTest.init();
	}

	@AfterClass
	public static void tearDownClass() {
		ElasticSearchUtils.deleteIndex(Config.APP_NAME_NS);
		ElasticSearchUtils.deleteIndex(appName1);
		ElasticSearchUtils.deleteIndex(appName2);
		ElasticSearchUtils.shutdownClient();
		SearchTest.cleanup();
	}

	@Test
	public void testCreateDeleteExistsIndex() throws InterruptedException {
		String appName3 = "test-index";
		String badAppName = "test index 123";

		ElasticSearchUtils.createIndex("");
		assertFalse(ElasticSearchUtils.existsIndex(""));

		ElasticSearchUtils.createIndex(appName3);
		assertTrue(ElasticSearchUtils.existsIndex(appName3));

		assertTrue(ElasticSearchUtils.optimizeIndex(appName3));

		ElasticSearchUtils.deleteIndex(appName3);
		assertFalse(ElasticSearchUtils.existsIndex(appName3));

		assertFalse(ElasticSearchUtils.createIndex(badAppName));
		assertFalse(ElasticSearchUtils.existsIndex(badAppName));
		assertFalse(ElasticSearchUtils.deleteIndex(appName3));
		assertFalse(ElasticSearchUtils.deleteIndex(badAppName));
	}

	@Test
	public void testRebuildIndex() {
		// TODO
	}

	@Test
	public void testGetSearchClusterMetadata() {
		assertFalse(ElasticSearchUtils.getSearchClusterMetadata().isEmpty());
	}

	@Test
	public void testGetIndexNameForAlias() throws InterruptedException {
		ElasticSearchUtils.createIndex("test-index");
		assertNull(ElasticSearchUtils.getIndexNameForAlias(""));
		assertEquals("test-index1", ElasticSearchUtils.getIndexNameForAlias("test-index"));
		ElasticSearchUtils.deleteIndex("test-index");
	}
}