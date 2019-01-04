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

import com.erudika.para.utils.Utils;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class LinkerTest {
	@Test
	public void test() {
		Linker l1 = new Linker(Utils.type(User.class), Utils.type(Tag.class), "111", "222");
		Linker l2 = new Linker(Utils.type(Tag.class), Utils.type(User.class), "222", "111");
		assertTrue(l1.getId().equals(l2.getId()));
		assertEquals("id2", l1.getIdFieldNameFor(Utils.type(User.class)));
		assertEquals("id1", l1.getIdFieldNameFor(Utils.type(Tag.class)));
		assertEquals("id1", l2.getIdFieldNameFor(Utils.type(Tag.class)));
		assertEquals("id2", l2.getIdFieldNameFor(Utils.type(User.class)));

		assertEquals(l1.getId1(), "222");
		assertEquals(l1.getId2(), "111");
		assertEquals(l2.getId1(), "222");
		assertEquals(l2.getId2(), "111");

		assertTrue(!l1.isFirst(Utils.type(User.class)));
		assertTrue(l1.isFirst(Utils.type(Tag.class)));
		assertTrue(l2.isFirst(Utils.type(Tag.class)));
		assertTrue(!l2.isFirst(Utils.type(User.class)));
		assertTrue(l1.equals(l2));
	}
}