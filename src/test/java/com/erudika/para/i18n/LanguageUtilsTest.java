/*
 * Copyright 2013 Alex Bogdanovski <albogdano@me.com>.
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
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class LanguageUtilsTest {
	
	private LanguageUtils lu;
	private Map<String, String> deflang = new HashMap<String, String>(){
		private static final long serialVersionUID = 1L;{
		put("hello", "hello");
		put("yes", "yes");
		put("what", "what");
	}};
	
	private Map<String, String> es = new HashMap<String, String>(){
		private static final long serialVersionUID = 1L;{
		put("hello", "hola");
		put("yes", "si");
		put("what", "que");
	}};
	
	private Map<String, String> de = new HashMap<String, String>(){
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
		assertNotNull(lu.readLanguage(null));
		assertNotNull(lu.readLanguage(""));
		assertTrue(lu.readLanguage("").containsKey("hello"));		
		assertEquals("hello", lu.readLanguage("").get("hello"));		
		
		lu.writeLanguage("", es);
		assertEquals("hello", lu.readLanguage("es").get("hello"));
		
		lu.writeLanguage("es", es);
		assertEquals(es.get("hello"), lu.readLanguage("es").get("hello"));
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
		assertTrue(lu.readAllTranslationsForKey(null, null, null, null).isEmpty());
	}

	@Test
	public void testGetApprovedTransKeys() {
		lu.writeLanguage("es", es);
		assertTrue(lu.getApprovedTransKeys(null).isEmpty());
		assertTrue(lu.getApprovedTransKeys("xxx").isEmpty());
		assertEquals(es.keySet(), lu.getApprovedTransKeys("es"));
	}

	@Test
	public void testGetTranslationProgressMap() {
		lu.writeLanguage("es", es);
		lu.writeLanguage("de", de);	
		assertEquals("66", lu.getTranslationProgressMap().get("de").toString());
		assertEquals("100", lu.getTranslationProgressMap().get("es").toString());
		assertEquals("100", lu.getTranslationProgressMap().get("en").toString());
	}

	@Test
	public void testGetAllLocales() {
		assertNotNull(lu.getAllLocales());
		assertFalse(lu.getAllLocales().isEmpty());
		assertNotNull(lu.getAllLocales().get("en"));
	}

	@Test
	public void testApproveTranslation() {
		lu.writeLanguage("es", es);
		lu.writeLanguage("de", de);	
		assertEquals("66", lu.getTranslationProgressMap().get("de").toString());
		assertEquals("100", lu.getTranslationProgressMap().get("es").toString());
		
		assertFalse(lu.approveTranslation(null, null, null));
		assertFalse(lu.approveTranslation("", "", ""));
		assertFalse(lu.approveTranslation("en", "asd", "asd"));
		assertFalse(lu.approveTranslation("en", "asd", "asd"));
		
		assertTrue(lu.approveTranslation("de", "hello", "hallo"));
		assertEquals("100", lu.getTranslationProgressMap().get("de").toString());
	}

	@Test
	public void testDisapproveTranslation() {
		lu.writeLanguage("es", es);
		lu.writeLanguage("de", de);	
		assertEquals("66", lu.getTranslationProgressMap().get("de").toString());
		assertEquals("100", lu.getTranslationProgressMap().get("es").toString());
		
		assertFalse(lu.disapproveTranslation(null, null));
		assertFalse(lu.disapproveTranslation("", ""));
		assertFalse(lu.disapproveTranslation("en", "asd"));
		assertFalse(lu.disapproveTranslation("en", "asd"));
		
		assertFalse(lu.disapproveTranslation("de", "hello"));
		
		assertTrue(lu.disapproveTranslation("de", "yes"));
		assertEquals("33", lu.getTranslationProgressMap().get("de").toString());
		
		assertTrue(lu.disapproveTranslation("de", "what"));
		assertEquals("0", lu.getTranslationProgressMap().get("de").toString());
		
		// one more time
		assertTrue(lu.approveTranslation("de", "hello", "hallooo"));
		assertEquals("33", lu.getTranslationProgressMap().get("de").toString());	
		
		assertTrue(lu.approveTranslation("de", "yes", "yaa"));
		assertEquals("66", lu.getTranslationProgressMap().get("de").toString());
		
		assertTrue(lu.approveTranslation("de", "what", "waas"));
		assertEquals("100", lu.getTranslationProgressMap().get("de").toString());	
	}
}