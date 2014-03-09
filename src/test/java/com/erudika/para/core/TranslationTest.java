/*
 * Copyright 2013-2014 Erudika. http://erudika.com
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

import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.MockDAO;
import com.erudika.para.search.Search;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import static org.mockito.Mockito.*;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class TranslationTest {

	private DAO dao;
	private Translation t;

	@Before
	public void setUp() {
		dao = new MockDAO();
		t = new Translation("en", "test", "value");
		t.setCreatorid("111");
		t.setName("test.123");
		t.setDao(dao);
		t.setSearch(mock(Search.class));
	}

	@Test
	public void testApproved() {
		assertFalse(t.isApproved());
		t.approve();
		assertTrue(t.isApproved());
		t.disapprove();
		assertFalse(t.isApproved());
	}

}