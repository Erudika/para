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
package com.erudika.para.aop;

import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.MockDAO;
import com.erudika.para.search.Search;
import java.util.ArrayList;
import java.util.List;
import static com.erudika.para.aop.AOPUtils.*;
import com.erudika.para.utils.Utils;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AOPUtilsTest {

	public AOPUtilsTest() {
	}

	@Test
	public void testUtils() throws Exception {
		Tag tag = new Tag("tag");
		Tag tag1 = new Tag("tag1");
		Tag tag2 = new Tag("tag2");
		Tag tag3 = new Tag("tag3");

		List<ParaObject> list1 = new ArrayList<>();
		list1.add(tag1);
		list1.add(tag2);
		list1.add(tag3);
		List<ParaObject> list2 = new ArrayList<>();
		list2.add(new Tag("tagzz1"));
		list2.add(new Tag("tagzz2"));
		list2.add(new Tag("tagzz3"));
		List<String> badList = new ArrayList<>();
		badList.add("XXXtagXXX");

		assertSame(tag, getArgOfParaObject(new Object[]{tag, "string"}));
		assertNull(getArgOfParaObject(new Object[]{"string"}));
		assertEquals(list1, getArgOfListOfType(new Object[]{list1}, ParaObject.class));
		assertEquals(list2, getArgOfListOfType(new Object[]{list2}, ParaObject.class));
		assertNull(getArgOfListOfType(new Object[]{badList}, ParaObject.class));

		assertNull(getFirstArgOfString(new Object[]{list1}));
		assertNotNull(getFirstArgOfString(new Object[]{Integer.valueOf(123), "asd"}));
		assertEquals("asd", getFirstArgOfString(new Object[]{Integer.valueOf(123), "asd"}));

		DAO dao = new MockDAO();
		Search search = getSearch(dao);

		assertNotNull(dao.create(tag));
		assertNotNull(dao.read(tag.getId()));
		assertNotNull(search.findById(tag.getId()));

		dao.delete(tag);
		assertNull(dao.read(tag.getId()));
		assertNull(search.findById(tag.getId()));

		dao.createAll(list1);
		assertNotNull(dao.read(tag1.getId()));
		assertNotNull(dao.read(tag2.getId()));
		assertNotNull(dao.read(tag3.getId()));
		assertNotNull(search.findById(tag1.getId()));
		assertNotNull(search.findById(tag2.getId()));
		assertNotNull(search.findById(tag3.getId()));

		dao.deleteAll(list1);
		assertNull(dao.read(tag1.getId()));
		assertNull(dao.read(tag2.getId()));
		assertNull(dao.read(tag3.getId()));
		assertNull(search.findById(tag1.getId()));
		assertNull(search.findById(tag2.getId()));
		assertNull(search.findById(tag3.getId()));

		ArrayList<ParaObject> list3 = new ArrayList<>();
		ArrayList<ParaObject> indexUs = new ArrayList<>();
		tag.setIndexed(false);
		tag.setStored(false);
		list3.add(tag);
		assertFalse(removeNotStoredNotIndexed(list3, null).isEmpty());
		assertTrue(list3.isEmpty());
		list3.clear();
		tag.setIndexed(true);
		tag.setStored(false);
		list3.add(tag);
		assertFalse(removeNotStoredNotIndexed(list3, indexUs).isEmpty());
		assertTrue(list3.isEmpty());
		assertFalse(indexUs.isEmpty());

		Sysprop s = new Sysprop("custom_123");
		s.setType("ok");
		checkAndFixType(s);
		assertEquals("ok", s.getType());

		s.setType(null);
		checkAndFixType(s);
		assertNotNull(s.getType());

		s.setType("___NOT_OK_");
		checkAndFixType(s);
		assertEquals("NOT_OK_", s.getType());

		s.setType("NOT/_OK_");
		checkAndFixType(s);
		assertEquals("NOT_OK_", s.getType());

		s.setType("NOT/OK/.OK");
		checkAndFixType(s);
		assertEquals("NOTOK.OK", s.getType());

		s.setType("____");
		checkAndFixType(s);
		assertEquals(Utils.type(Sysprop.class), s.getType());
	}

	private Search getSearch(final DAO dao) {
		Search search = mock(Search.class);

		doAnswer(new Answer<Boolean>() {
			public Boolean answer(InvocationOnMock invocation) throws Throwable {
				ParaObject p = (ParaObject) invocation.getArguments()[0];
				if (p != null) {
					dao.create(new Sysprop(p.getId().concat(":INDEXED")));
				}
				return null;
			}
		}).when(search).index((ParaObject) any());

		doAnswer(new Answer<Boolean>() {
			public Boolean answer(InvocationOnMock invocation) throws Throwable {
				ParaObject p = (ParaObject) invocation.getArguments()[0];
				if (p != null) {
					dao.delete(new Sysprop(p.getId().concat(":INDEXED")));
				}
				return null;
			}
		}).when(search).unindex((ParaObject) any());

		when(search.findById(anyString())).thenAnswer(new Answer<ParaObject>() {
			public ParaObject answer(InvocationOnMock invocation) throws Throwable {
				return dao.read((String) invocation.getArguments()[0]);
			}
		});
		return search;
	}
}