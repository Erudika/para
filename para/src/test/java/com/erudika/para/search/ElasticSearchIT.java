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
package com.erudika.para.search;

import com.erudika.para.core.Tag;
import com.erudika.para.persistence.DAO;
import com.erudika.para.utils.Config;
import java.util.List;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.mockito.Mockito.mock;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ElasticSearchIT extends SearchTest {

	@BeforeClass
	public static void setUpClass() {
		System.setProperty("para.env", "embedded");
		s = new ElasticSearch(mock(DAO.class));
		ElasticSearchUtils.createIndex(Config.APP_NAME_NS);
		ElasticSearchUtils.createIndex(appid1);
		ElasticSearchUtils.createIndex(appid2);
		SearchTest.init();
	}

	@AfterClass
	public static void tearDownClass() {
		ElasticSearchUtils.deleteIndex(Config.APP_NAME_NS);
		ElasticSearchUtils.deleteIndex(appid1);
		ElasticSearchUtils.deleteIndex(appid2);
		ElasticSearchUtils.shutdownClient();
		SearchTest.cleanup();
	}

	@Test
	public void testCreateDeleteExistsIndex() throws InterruptedException {
		String appid3 = "test-index";
		String badAppid = "test index 123";

		ElasticSearchUtils.createIndex("");
		assertFalse(ElasticSearchUtils.existsIndex(""));

		ElasticSearchUtils.createIndex(appid3);
		assertTrue(ElasticSearchUtils.existsIndex(appid3));

		assertTrue(ElasticSearchUtils.optimizeIndex(appid3));

		ElasticSearchUtils.deleteIndex(appid3);
		assertFalse(ElasticSearchUtils.existsIndex(appid3));

		assertFalse(ElasticSearchUtils.createIndex(badAppid));
		assertFalse(ElasticSearchUtils.existsIndex(badAppid));
		assertFalse(ElasticSearchUtils.deleteIndex(appid3));
		assertFalse(ElasticSearchUtils.deleteIndex(badAppid));
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

	@Test
	public void testSharedIndex() {
		String app1 = "myapp1";
		String app2 = "myapp2";
		String shared = "shared-index";
		assertTrue(ElasticSearchUtils.createIndex(shared));
		assertTrue(ElasticSearchUtils.addIndexAlias(shared, app1, true));
		assertTrue(ElasticSearchUtils.addIndexAlias(shared, app2, true));

		try	{
			Tag t1 = new Tag("t1");
			Tag t2 = new Tag("t2");
			Tag t3 = new Tag("t3");

			t1.setAppid(app1);
			t2.setAppid(app2);
			t3.setAppid(app1);

			// enable on-index routing by setting the shardKey for each object
			t1.setShardKey(t1.getAppid());
			t2.setShardKey(t2.getAppid());
			t3.setShardKey(t3.getAppid());

			s.index(t1.getAppid(), t1);
			s.index(t2.getAppid(), t2);
			s.index(t3.getAppid(), t3);

			// enable on-search routing by prefixing the routing value to the alias.
			// "_" means the same value as the alias
			app1 = "_:" + app1;
			app2 = "_:" + app2;

			// top view of all docs in shared index
			assertEquals(3, s.getCount(shared, "tag").intValue());
			// local view for each app space
			assertEquals(2, s.getCount(app1, "tag").intValue());
			assertEquals(1, s.getCount(app2, "tag").intValue());

			List<Tag> l = s.findQuery(app1, "tag", "*");
			assertEquals(2, l.size());
		} finally {
			ElasticSearchUtils.deleteIndex(shared);
		}
	}
}