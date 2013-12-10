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
package com.erudika.para.core;

import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.MockDAO;
import com.erudika.para.search.Search;
import java.util.ArrayList;
import org.apache.commons.lang3.mutable.MutableLong;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class PObjectTest {
	
	@Test
	public void testGetClassname() {
		Tag tag = new Tag();
		PObject p = new PObject() {};
		assertNotNull(tag.getClassname());
		assertNotNull(p.getClassname());
		assertEquals("", p.getClassname());
		assertEquals("tag", tag.getClassname());
		assertEquals("", p.getClassname());
	}

	@Test
	public void testSetClassname() {
		Tag tag = new Tag();
		tag.setClassname("bag");
		assertEquals("tag", tag.getClassname());		
	}

	@Test
	public void testGetObjectURL() {
		assertEquals("/tags/tag", new Tag("tag").getObjectURL());
		assertEquals("/users/1", new User("1").getObjectURL());
		assertEquals("/votes", new Vote(null,null,null).getObjectURL());
	}
	
	@Test
	public void testLinks() {
		Search search = mock(Search.class);
		DAO dao = new MockDAO();
		
		Tag t = new Tag("tag");
		User u = new User("111");
		User u3 = new User("333");
		
		u.setDao(dao);
		u.setSearch(search);
		
		Linker l1 = new Linker(User.class, Tag.class, "111", "222");
		Linker l2 = new Linker(User.class, User.class, "111", "333");
		
		u.link(t.getClass(), t.getId());
		u.link(u3.getClass(), u3.getId());
		
		assertTrue(u.isLinked(t));
		assertTrue(u.isLinked(u3));
		
		ArrayList<ParaObject> list = new ArrayList<ParaObject>();
		list.add(l1);
		list.add(l2);
		
		when(search.findTwoTerms(anyString(), (MutableLong) any(), (MutableLong) any(), anyString(), 
				any(), anyString(), any(), anyBoolean(), anyString(), anyBoolean(), anyInt())).
			thenReturn(list);
		
		assertEquals(0, u.getLinkedObjects(Tag.class, null, null).size());
		assertEquals(0, u.getLinkedObjects(User.class, null, null).size());
		
		u.unlinkAll();
		
		assertNull(dao.read(l1.getId()));
		assertNull(dao.read(l2.getId()));
	}

	@Test
	public void testClassname() {
		assertEquals("user", PObject.classname(User.class));
		assertEquals("tag", PObject.classname(Tag.class));
		assertEquals("paraobject", PObject.classname(ParaObject.class));
		assertEquals("vote", PObject.classname(Vote.class));
		assertEquals("", PObject.classname(null));
	}

	@Test
	public void testGetPlural() {
		assertEquals("users", new User().getPlural());
		assertEquals("addresses", new Address().getPlural());
		assertEquals("votes", new Vote().getPlural());
		assertEquals("linkers", new Linker().getPlural());
	}
	
	@Test
	public void testEquals() {
		User u1 = new User("111");
		User u2 = new User("111");
		assertTrue(u1.equals(u2));
	}
}