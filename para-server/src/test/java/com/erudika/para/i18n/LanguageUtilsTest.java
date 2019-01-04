/*
 * Copyright 2013-2019 Erudika. https://erudika.com
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
package com.erudika.para.i18n;

import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.MockDAO;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class LanguageUtilsTest {

	private LanguageUtils lu;

	private Map<String, String> deflang = new HashMap<String, String>() {
		private static final long serialVersionUID = 1L;{
		put("hello", "hello");
		put("yes", "yes");
		put("what", "what");
	}};

	private Map<String, String> es = new HashMap<String, String>() {
		private static final long serialVersionUID = 1L;{
		put("hello", "hola");
		put("yes", "si");
		put("what", "que");
	}};

	private Map<String, String> de = new HashMap<String, String>() {
		private static final long serialVersionUID = 1L;{
		put("hello", "hello");
		put("yes", "ja");
		put("what", "was");
	}};

	@Before
	public void setUp() {
		DAO dao = new MockDAO();
		lu = new LanguageUtils(mock(Search.class), dao);
		lu.setDefaultLanguage(deflang);
	}

	@After
	public void tearDown() {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		Files.delete(FileSystems.getDefault().getPath("./lang_es.properties"));
		Files.delete(FileSystems.getDefault().getPath("./lang_de.properties"));
	}

	@Test
	public void testReadWriteLanguage() {
		String appid = "para-test-rwl";
		assertNotNull(lu.readLanguage(appid, null));
		assertNotNull(lu.readLanguage(appid, ""));
		assertTrue(lu.readLanguage(appid, "").containsKey("hello"));
		assertEquals("hello", lu.readLanguage(appid, "").get("hello"));

		lu.writeLanguage(appid, "", es, true);
		assertEquals(es.get("hello"), lu.readLanguage(appid, "es").get("hello"));

		lu.writeLanguage(appid, "es", es, true);
		assertEquals(es.get("hello"), lu.readLanguage(appid, "es").get("hello"));
	}

	@Test
	public void testGetProperLocale() {
		assertNotNull(lu.getProperLocale("en"));
		assertNotNull(lu.getProperLocale("en"));
		assertNotNull(lu.getProperLocale("es"));
		assertEquals(lu.getProperLocale("en_GB"), lu.getProperLocale("en"));
		assertEquals(lu.getProperLocale("es_XX"), lu.getProperLocale("es"));
		assertEquals(lu.getProperLocale("en_GB"), lu.getProperLocale(null));
	}

	@Test
	public void testGetDefaultLanguage() {
		assertTrue(!lu.getDefaultLanguage(Config.getRootAppIdentifier()).isEmpty());
	}

	@Test
	public void testSetDefaultLanguage() {
		lu.setDefaultLanguage(null);
		assertNotNull(lu.getDefaultLanguage(Config.getRootAppIdentifier()));
		assertFalse(lu.getDefaultLanguage(Config.getRootAppIdentifier()).isEmpty());
		lu.setDefaultLanguage(es);
		assertEquals(es.get("hello"), lu.getDefaultLanguage(Config.getRootAppIdentifier()).get("hello"));
	}

	@Test
	public void testReadAllTranslationsForKey() {
		assertTrue(lu.readAllTranslationsForKey("x", null, null, null).isEmpty());
	}

	@Test
	public void testGetApprovedTransKeys() {
		String appid = "para-test-gatk";
		lu.writeLanguage(appid, "es", es, true);
		assertTrue(lu.getApprovedTransKeys(appid, null).isEmpty());
		assertTrue(lu.getApprovedTransKeys(appid, "xxx").isEmpty());
		assertEquals(es.keySet(), lu.getApprovedTransKeys(appid, "es"));
	}

	@Test
	public void testGetTranslationProgressMap() {
		String appid = "para-test-gtp";
		lu.writeLanguage(appid, "es", es, true);
		lu.writeLanguage(appid, "de", de, true);
		assertEquals("66", lu.getTranslationProgressMap(appid).get("de").toString());
		assertEquals("100", lu.getTranslationProgressMap(appid).get("es").toString());
		assertEquals("100", lu.getTranslationProgressMap(appid).get("en").toString());
	}

	@Test
	public void testGetAllLocales() {
		assertNotNull(lu.getAllLocales());
		assertFalse(lu.getAllLocales().isEmpty());
		assertNotNull(lu.getAllLocales().get("en"));
	}

	@Test
	public void testApproveTranslation() {
		String appid = "para-test-gat";
		lu.writeLanguage(appid, "es", es, true);
		lu.writeLanguage(appid, "de", de, true);
		assertEquals("66", lu.getTranslationProgressMap(appid).get("de").toString());
		assertEquals("100", lu.getTranslationProgressMap(appid).get("es").toString());

		assertFalse(lu.approveTranslation(appid, null, null, null));
		assertFalse(lu.approveTranslation(appid, "", "", ""));
		assertFalse(lu.approveTranslation(appid, "en", "asd", "asd"));
		assertFalse(lu.approveTranslation(appid, "en", "asd", "asd"));

		assertTrue(lu.approveTranslation(appid, "de", "hello", "hallo"));
		assertEquals("100", lu.getTranslationProgressMap(appid).get("de").toString());
	}

	@Test
	public void testDisapproveTranslation() {
		String appid = "para-test-dt";
		lu.writeLanguage(appid, "es", es, true);
		lu.writeLanguage(appid, "de", de, true);
		assertEquals("66", lu.getTranslationProgressMap(appid).get("de").toString());
		assertEquals("100", lu.getTranslationProgressMap(appid).get("es").toString());

		assertFalse(lu.disapproveTranslation(appid, null, null));
		assertFalse(lu.disapproveTranslation(appid, "", ""));
		assertFalse(lu.disapproveTranslation(appid, "en", "asd"));
		assertFalse(lu.disapproveTranslation(appid, "en", "asd"));

		assertTrue(lu.disapproveTranslation(appid, "de", "yes"));
		assertEquals("33", lu.getTranslationProgressMap(appid).get("de").toString());

		assertTrue(lu.disapproveTranslation(appid, "de", "what"));
		assertEquals("0", lu.getTranslationProgressMap(appid).get("de").toString());

		// one more time
		assertTrue(lu.approveTranslation(appid, "de", "hello", "hallooo"));
		assertEquals("33", lu.getTranslationProgressMap(appid).get("de").toString());

		assertTrue(lu.approveTranslation(appid, "de", "yes", "yaa"));
		assertEquals("66", lu.getTranslationProgressMap(appid).get("de").toString());

		assertTrue(lu.approveTranslation(appid, "de", "what", "waas"));
		assertEquals("100", lu.getTranslationProgressMap(appid).get("de").toString());
	}
}