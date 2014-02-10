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
package com.erudika.para.i18n;

import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.MockDAO;
import com.erudika.para.search.Search;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class LanguageUtilsTest {

	private LanguageUtils lu;
	private String appName = "para-test";

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

	@Test
	public void testReadWriteLanguage() {
		assertNotNull(lu.readLanguage(appName, null));
		assertNotNull(lu.readLanguage(appName, ""));
		assertTrue(lu.readLanguage(appName, "").containsKey("hello"));
		assertEquals("hello", lu.readLanguage(appName, "").get("hello"));

		lu.writeLanguage(appName, "", es);
		assertEquals("hello", lu.readLanguage(appName, "es").get("hello"));

		lu.writeLanguage(appName, "es", es);
		assertEquals(es.get("hello"), lu.readLanguage(appName, "es").get("hello"));
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
		assertTrue(!lu.getDefaultLanguage().isEmpty());
	}

	@Test
	public void testSetDefaultLanguage() {
		lu.setDefaultLanguage(null);
		assertTrue(lu.getDefaultLanguage().isEmpty());
		lu.setDefaultLanguage(es);
		assertEquals(es.get("hello"), lu.getDefaultLanguage().get("hello"));
	}

	@Test
	public void testReadAllTranslationsForKey() {
		assertTrue(lu.readAllTranslationsForKey(appName, null, null, null).isEmpty());
	}

	@Test
	public void testGetApprovedTransKeys() {
		lu.writeLanguage(appName, "es", es);
		assertTrue(lu.getApprovedTransKeys(appName, null).isEmpty());
		assertTrue(lu.getApprovedTransKeys(appName, "xxx").isEmpty());
		assertEquals(es.keySet(), lu.getApprovedTransKeys(appName, "es"));
	}

	@Test
	public void testGetTranslationProgressMap() {
		lu.writeLanguage(appName, "es", es);
		lu.writeLanguage(appName, "de", de);
		assertEquals("66", lu.getTranslationProgressMap(appName).get("de").toString());
		assertEquals("100", lu.getTranslationProgressMap(appName).get("es").toString());
		assertEquals("100", lu.getTranslationProgressMap(appName).get("en").toString());
	}

	@Test
	public void testGetAllLocales() {
		assertNotNull(lu.getAllLocales());
		assertFalse(lu.getAllLocales().isEmpty());
		assertNotNull(lu.getAllLocales().get("en"));
	}

	@Test
	public void testApproveTranslation() {
		lu.writeLanguage(appName, "es", es);
		lu.writeLanguage(appName, "de", de);
		assertEquals("66", lu.getTranslationProgressMap(appName).get("de").toString());
		assertEquals("100", lu.getTranslationProgressMap(appName).get("es").toString());

		assertFalse(lu.approveTranslation(appName, null, null, null));
		assertFalse(lu.approveTranslation(appName, "", "", ""));
		assertFalse(lu.approveTranslation(appName, "en", "asd", "asd"));
		assertFalse(lu.approveTranslation(appName, "en", "asd", "asd"));

		assertTrue(lu.approveTranslation(appName, "de", "hello", "hallo"));
		assertEquals("100", lu.getTranslationProgressMap(appName).get("de").toString());
	}

	@Test
	public void testDisapproveTranslation() {
		lu.writeLanguage(appName, "es", es);
		lu.writeLanguage(appName, "de", de);
		assertEquals("66", lu.getTranslationProgressMap(appName).get("de").toString());
		assertEquals("100", lu.getTranslationProgressMap(appName).get("es").toString());

		assertFalse(lu.disapproveTranslation(appName, null, null));
		assertFalse(lu.disapproveTranslation(appName, "", ""));
		assertFalse(lu.disapproveTranslation(appName, "en", "asd"));
		assertFalse(lu.disapproveTranslation(appName, "en", "asd"));

		assertFalse(lu.disapproveTranslation(appName, "de", "hello"));

		assertTrue(lu.disapproveTranslation(appName, "de", "yes"));
		assertEquals("33", lu.getTranslationProgressMap(appName).get("de").toString());

		assertTrue(lu.disapproveTranslation(appName, "de", "what"));
		assertEquals("0", lu.getTranslationProgressMap(appName).get("de").toString());

		// one more time
		assertTrue(lu.approveTranslation(appName, "de", "hello", "hallooo"));
		assertEquals("33", lu.getTranslationProgressMap(appName).get("de").toString());

		assertTrue(lu.approveTranslation(appName, "de", "yes", "yaa"));
		assertEquals("66", lu.getTranslationProgressMap(appName).get("de").toString());

		assertTrue(lu.approveTranslation(appName, "de", "what", "waas"));
		assertEquals("100", lu.getTranslationProgressMap(appName).get("de").toString());
	}
}