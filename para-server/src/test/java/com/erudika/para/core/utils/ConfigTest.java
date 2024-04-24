/*
 * Copyright 2013-2022 Erudika. https://erudika.com
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
package com.erudika.para.core.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;


/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ConfigTest {

	@Test
	public void testGetConfigParam() {
		assertNull(Para.getConfig().getConfigParam(null, null));
		assertNull(Para.getConfig().getConfigParam("", null));
		assertNull(Para.getConfig().getConfigParam("null", null));
		assertEquals("test", Para.getConfig().getConfigParam("null", "test"));

		assertEquals("embedded", Para.getConfig().environment());
		System.setProperty("para.env", "production");
		assertEquals("production", Para.getConfig().environment());
	}

	@Test
	public void testGetConfig() {
		assertNotNull(Para.getConfig().getConfig());
	}
}