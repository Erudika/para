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

import com.erudika.para.persistence.MockDAO;
import static com.erudika.para.search.SearchTest.s;
import com.erudika.para.utils.Config;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class ElasticSearchIT extends SearchTest{
	
	@BeforeClass
	public static void setUpClass() {
		System.setProperty("esembedded", "true");
		s = new ElasticSearch(new MockDAO());
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
	}

}