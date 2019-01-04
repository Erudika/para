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
package com.erudika.para.core;

import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.persistence.DAO;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SyspropTest {

	private Sysprop s() {
		Sysprop s = new Sysprop("5");
		s.addProperty("test1", "ok");
		s.addProperty("test2", "nope");
		s.addProperty("test3", "sure");
		s.addProperty("test4", false);
		s.addProperty("test5", 42);
		return s;
	}

	public DAO dao() {
		return CoreUtils.getInstance().getDao(); //new MockDAO();
	}

	@Test
	public void testAddRemoveHasProperty() {
		Sysprop s = s();
		s.setProperties(null);
		assertTrue(s.getProperties().isEmpty());
		s.addProperty(null, "asd");
		assertTrue(s.getProperties().isEmpty());
		s.addProperty("123", "123").addProperty("123", "1234").addProperty("", "123");
		assertTrue(s.getProperties().size() == 1);
		assertFalse(s.hasProperty(""));
		assertFalse(s.hasProperty(null));
		assertFalse(s.hasProperty("141"));
		assertTrue(s.hasProperty("123"));
		s.removeProperty("123");
		assertTrue(s.getProperties().isEmpty());
	}

	@Test
	public void testGetProperty() {
		Sysprop s = s();
		s.create();
		Sysprop s1 = dao().read(s.getId());
		assertEquals(s.getProperty("test5"), s1.getProperty("test5"));
		assertEquals(s.getProperty("test4"), s1.getProperty("test4"));
		assertEquals(s.getProperty("test3"), s1.getProperty("test3"));
		assertEquals(s.getProperty("test2"), s1.getProperty("test2"));
		assertEquals(s.getProperty("test1"), s1.getProperty("test1"));
		assertFalse((Boolean) s1.getProperty("test4"));
		assertTrue((Integer) s1.getProperty("test5") == 42);
	}

}