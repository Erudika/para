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
package com.erudika.para.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ConfigTest {

	@Test
	public void testGetConfigParam() {
		assertNull(Config.getConfigParam(null, null));
		assertNull(Config.getConfigParam("", null));
		assertNull(Config.getConfigParam("null", null));
		assertEquals("test", Config.getConfigParam("null", "test"));

		assertEquals("embedded", Config.getConfigParam("env", ""));
		System.setProperty("para.env", "production");
		assertEquals("production", Config.getConfigParam("env", ""));
	}

	@Test
	public void testGetConfig() {
		assertNotNull(Config.getConfig());
	}
}