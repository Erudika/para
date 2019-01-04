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

import com.erudika.para.core.User.Groups;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.persistence.DAO;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.erudika.para.validation.ValidationUtils;
import java.util.ArrayList;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.junit.Before;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class UserTest {

	private static User u() {
		User u = new User(Utils.getNewId());
		u.setName("Name");
		u.setGroups(Groups.USERS.toString());
		u.setEmail(u.getId() + "@email.com");
		u.setIdentifier(u.getEmail());
		u.setPassword("123456");
		return u;
	}

	private static DAO dao() {
		return CoreUtils.getInstance().getDao(); //new MockDAO();
	}

	@Before
	public void setUp() {
		CoreUtils.getInstance().setSearch(mock(Search.class));
	}

	@After
	public void tearDown() {
	}

	@Test
	public void testSetEmail() {
		User u = u();
		assertNotNull(u.getPicture());
		assertTrue(ValidationUtils.isValidObject(u));
		u.setEmail("asd@asd");
		assertFalse(ValidationUtils.isValidObject(u));
	}

	@Test
	public void testSetCurrency() {
		User u = u();
		u.setCurrency("asd");
		assertEquals("EUR", u.getCurrency());
		u.setCurrency("usd");
		assertEquals("USD", u.getCurrency());
		u.setCurrency(null);
		assertEquals("EUR", u.getCurrency());
		u.setCurrency("");
		assertEquals("EUR", u.getCurrency());
	}

	@Test
	public void testCanModify() {
		User u = u();
		Sysprop p = new Sysprop("test");
		assertFalse(u.canModify(null));
		assertFalse(u.canModify(p));
		p.setCreatorid(u.getId());
		p.setAppid("1");
		u.setAppid("1");
		assertTrue(u.canModify(p));

		p.setCreatorid(null);
		p.setParentid(u.getId());
		assertTrue(u.canModify(p));

		p.setParentid(null);
		p.setId(u.getId());
		assertTrue(u.canModify(p));

		p.setId("1234");
		u.setGroups(Groups.ADMINS.toString());
		assertTrue(u.canModify(p));
		u.setGroups(Groups.USERS.toString());
	}

	@Test
	public void testCreate() {
		User u = u();
		u.setIdentifier(null);
		assertNull(u.create());
		u.setPassword("123");
		assertNull(u.create());
		u.setPassword("123456");
		u.setIdentifier("fb:2");
		assertNotNull(u.create());
		assertNotNull(dao().read(u.getIdentifier()));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testDelete() {
		User u = u();
		u.create();

		String secIdent = "t:1";
		ArrayList<ParaObject> list = new ArrayList<>();
		list.add(new Sysprop(u.getIdentifier()));
		list.add(new Sysprop(secIdent));

		when(CoreUtils.getInstance().getSearch().findTerms(anyString(), anyString(), any(Map.class),
				anyBoolean())).thenReturn(list);

		u.attachIdentifier(secIdent);
		assertNotNull(dao().read(secIdent));

		String oldId = u.getId();
		u.setId(null);
		u.delete();
		u.setId(oldId);
		assertNotNull(dao().read(u.getId()));
		assertNotNull(dao().read(u.getIdentifier()));
		assertNotNull(dao().read(secIdent));

		u.delete();
		assertNull(dao().read(u.getId()));
		assertNull(dao().read(u.getIdentifier()));
		assertNull(dao().read(secIdent));
	}

	@Test
	public void testAttachIdentifier() {
		User u = u();
		String secIdent = "t:2";
		u.attachIdentifier(secIdent);
		assertNull(User.readUserForIdentifier(u));
		assertNull(dao().read(secIdent));

		u.create();
		u.attachIdentifier(secIdent);
		assertNotNull(dao().read(u.getId()));
		assertNotNull(dao().read(secIdent));
	}

	@Test
	public void testDetachIdentifier() {
		User u = u();
		u.create();
		assertNotNull(dao().read(u.getIdentifier()));
		u.detachIdentifier(u.getIdentifier());
		assertNotNull(dao().read(u.getIdentifier()));

		String secIdent = "t:3";
		u.attachIdentifier(secIdent);
		assertNotNull(dao().read(secIdent));

		u.detachIdentifier(secIdent);
		assertNull(dao().read(secIdent));
	}

	@Test
	public void testIsFacebookUser() {
		User u = u();
		assertFalse(u.isFacebookUser());
		u.setIdentifier("fb:0");
		assertTrue(u.isFacebookUser());
	}

	@Test
	public void testIsAdmin() {
		User u = u();
		assertFalse(u.isAdmin());
		u.setGroups(Groups.ADMINS.toString());
		assertTrue(u.isAdmin());
	}

	@Test
	public void testIsModerator() {
		User u = u();
		assertFalse(u.isModerator());

		u.setGroups(Groups.ADMINS.toString());
		assertTrue(u.isAdmin());
		assertTrue(u.isModerator());

		u.setGroups(Groups.MODS.toString());
		assertTrue(u.isModerator());
	}

	@Test
	public void testReadUserForIdentifier() {
		User u = u();
		String secIdent = "fb:1";
		u.create();
		u.attachIdentifier(secIdent);

		u.setIdentifier(secIdent);
		assertNotNull(User.readUserForIdentifier(u));

		u.setIdentifier("1");
		assertNull(User.readUserForIdentifier(u));

		u.setIdentifier(u.getEmail());
		assertNotNull(User.readUserForIdentifier(u));

		u.setIdentifier("1234");
		assertNull(User.readUserForIdentifier(u));


		u.delete();

		u.setIdentifier(secIdent);
		assertNull(User.readUserForIdentifier(u));

		u.setIdentifier(u.getEmail());
		assertNull(User.readUserForIdentifier(u));
	}

	@Test
	public void testPasswordMatches() {
		User u = u();
		u.create();
		u.setPassword("123456");
		assertTrue(User.passwordMatches(u));

		User u1 = new User();
		u1.setIdentifier(u.getIdentifier());
		u1.setPassword("1234");
		assertFalse(User.passwordMatches(u1));

		u1.setPassword("");
		assertFalse(User.passwordMatches(u1));

		u1.setPassword(null);
		assertFalse(User.passwordMatches(u1));

		u1.setPassword(u.getPassword());
		u1.setIdentifier(null);
		assertFalse(User.passwordMatches(u1));
	}

	@Test
	public void testGeneratePasswordResetToken() {
		User u = u();
		String fail = u.generatePasswordResetToken();
		assertTrue(fail.isEmpty());
		Sysprop s = dao().read(u.getIdentifier());
		assertNull(s);

		u.create();

		String token1 = u.generatePasswordResetToken();
		s = dao().read(u.getIdentifier());
		assertNotNull(s);
		assertEquals(token1, s.getProperty(Config._RESET_TOKEN));

		String token2 = u.generatePasswordResetToken();
		s = dao().read(u.getIdentifier());
		assertNotNull(s);
		assertEquals(token2, s.getProperty(Config._RESET_TOKEN));
		assertNotEquals(token1, s.getProperty(Config._RESET_TOKEN));
	}

	@Test
	public void testResetPassword() {
		User u = u();
		u.create();
		String token = u.generatePasswordResetToken();
		String newpass = "1234567890";
		assertTrue(u.resetPassword(token, newpass));

		User u1 = new User();
		u1.setIdentifier(u.getIdentifier());
		u1.setPassword(newpass);
		assertTrue(User.passwordMatches(u1));

		assertFalse(u.resetPassword(token, "654321"));
		assertFalse(u.resetPassword(u.generatePasswordResetToken(), "1234"));
		assertFalse(u.resetPassword(u.generatePasswordResetToken(), "                  "));

		u.delete();
		dao().delete(new Sysprop(u.getIdentifier()));
		assertFalse(u.resetPassword(u.generatePasswordResetToken(), "654321"));
	}

	@Test
	public void testGenerateEmailConfirmationToken() {
		User u = u();
		u.delete();
		dao().delete(new Sysprop(u.getIdentifier()));
		String fail = u.generateEmailConfirmationToken();
		assertTrue(fail.isEmpty());
		Sysprop s = dao().read(u.getIdentifier());
		assertNull(s);

		u.create();

		String token1 = u.generateEmailConfirmationToken();
		s = dao().read(u.getIdentifier());
		assertNotNull(s);
		assertEquals(token1, s.getProperty(Config._EMAIL_TOKEN));

		String token2 = u.generateEmailConfirmationToken();
		s = dao().read(u.getIdentifier());
		assertNotNull(s);
		assertEquals(token2, s.getProperty(Config._EMAIL_TOKEN));
		assertNotEquals(token1, s.getProperty(Config._EMAIL_TOKEN));
	}

	@Test
	public void testActivateWithEmailToken() {
		User u = u();
		String fail = u.generateEmailConfirmationToken();
		assertTrue(fail.isEmpty());
		assertFalse(u.getActive());
		u.create();
		assertFalse(u.getActive());
		String token = u.generateEmailConfirmationToken();

		assertTrue(u.activateWithEmailToken(token));
		assertTrue(u.getActive());

		assertFalse(u.activateWithEmailToken(token));
		assertTrue(u.getActive());

		u.delete();
		dao().delete(new Sysprop(u.getIdentifier()));
		assertFalse(u.activateWithEmailToken(u.generatePasswordResetToken()));
	}

}