/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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

import com.erudika.para.utils.Config;
import static com.erudika.para.validation.Constraint.url;
import java.io.IOException;
import java.util.EnumSet;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AppTest {

	@BeforeClass
	public static void setUpClass() throws InterruptedException, IOException {
		System.setProperty("para.clients_can_access_root_app", "true");
	}

	@AfterClass
	public static void tearDownClass() {
		System.setProperty("para.clients_can_access_root_app", "false");
	}

	@Test
	public void testSetId() {
		App app = new App();
		assertNull(app.getId());
		app.setId(null);
		assertNull(app.getId());
		app.setId("test app");
		assertEquals("app:test-app", app.getId());
		app.setId("app:test app");
		assertEquals("app:test-app", app.getId());
		app.setId("app:app:test app ? @#$%^&>?<~`|\\;:./>-= COOL");
		assertEquals("app:test-app-cool", app.getId());
		app.setId("test app ? @#$%^&>?<~`|\\;:./>-= COOL");
		assertEquals("app:test-app-cool", app.getId());
		// if coming from db
		app.setId("app:test-app");
		assertEquals("app:test-app", app.getId());
	}

	@Test
	public void testGetAppIdentifier() {
		App app = new App();
		assertTrue(app.getAppIdentifier().isEmpty());
		app.setId(null);
		assertTrue(app.getAppIdentifier().isEmpty());
		app.setId("test app");
		assertEquals("test-app", app.getAppIdentifier());
		app.setId("app:test app");
		assertEquals("test-app", app.getAppIdentifier());
	}

	@Test
	public void testGetCredentials() {
		App app = new App();
		assertTrue(app.getCredentials().isEmpty());
		app.setId("test app");
		assertFalse(app.getCredentials().isEmpty());
	}

	@Test
	public void testAddRemoveDatatypes() {
		App app = new App();
		assertTrue(app.getDatatypes().isEmpty());
		app.addDatatype("", "type");
		app.addDatatype("", "");
		app.addDatatype("null", null);
		assertTrue(app.getDatatypes().isEmpty());

		app.addDatatype("types", "type");
		assertTrue(app.getDatatypes().containsKey("types"));
		app.addDatatype("typess", "type");
		assertFalse(app.getDatatypes().containsKey("typess"));
		app.addDatatype("types", "typee");
		assertFalse(app.getDatatypes().containsValue("typee"));
	}

	@Test
	public void testGetAllValidationConstraints() {
		App app = new App(Config.PARA);
		assertTrue(app.getAllValidationConstraints().size() > 2);
		assertTrue(app.getAllValidationConstraints().size() > 2);
		assertTrue(app.getAllValidationConstraints().containsKey("app"));
		assertTrue(app.getAllValidationConstraints(new String[]{null}).isEmpty());
		assertTrue(app.getAllValidationConstraints("123").isEmpty());
		assertFalse(app.getAllValidationConstraints(new String[0]).isEmpty());
		assertFalse(app.getAllValidationConstraints().isEmpty());
		assertFalse(app.getAllValidationConstraints("tag").isEmpty());
	}

	@Test
	public void testAddRemoveValidationConstraint() {
		App app = new App(Config.PARA);

		// add
		app.addValidationConstraint("testtype", "urlField", url());
		assertTrue(app.getValidationConstraints().get("testtype").containsKey("urlField"));
		assertTrue(app.getValidationConstraints().get("testtype").get("urlField").containsKey("url"));

		// remove
		app.removeValidationConstraint("testtype", "urlField", "url");
		assertTrue(app.getValidationConstraints().get("testtype").containsKey("urlField"));
		assertTrue(app.getValidationConstraints().get("testtype").get("urlField").isEmpty());
		assertFalse(app.getValidationConstraints().get("testtype").get("urlField").containsKey("url"));
	}

	@Test
	public void testGetAllResourcePermissions() {
		App app = new App(Config.PARA);
		assertTrue(app.getAllResourcePermissions().isEmpty());
		app.grantResourcePermission("123", App.ALLOW_ALL, App.AllowedMethods.READ);
		assertTrue(app.getAllResourcePermissions().containsKey("123"));
		assertTrue(app.getAllResourcePermissions(new String[]{null}).isEmpty());
		assertFalse(app.getAllResourcePermissions("123").isEmpty());
		assertFalse(app.getAllResourcePermissions(new String[0]).isEmpty());
		assertFalse(app.getAllResourcePermissions().isEmpty());
	}

	@Test
	public void testIsAllowedTo() {
		App app = new App(Config.PARA);
		assertFalse(app.isAllowedTo(null, null, null));
		assertFalse(app.isAllowedTo("", " ", ""));
		assertFalse(app.isAllowedTo(App.ALLOW_ALL, App.ALLOW_ALL, App.AllowedMethods.GET.toString()));

		app.grantResourcePermission("123", App.ALLOW_ALL, App.AllowedMethods.READ);
		assertFalse(app.isAllowedTo("123", App.ALLOW_ALL, App.AllowedMethods.POST.toString()));
		assertTrue(app.isAllowedTo("123", "_test", App.AllowedMethods.GET.toString()));
		assertTrue(app.isAllowedTo("123", "_test1", App.AllowedMethods.READ_ONLY.toString()));

		app.revokeAllResourcePermissions("123");
		assertFalse(app.isAllowedTo("123", "_test", App.AllowedMethods.GET.toString()));
		assertFalse(app.isAllowedTo("123", "_test1", App.AllowedMethods.READ_ONLY.toString()));
	}

	@Test
	public void testGrantRevokeResourcePermission() {
		App app = new App(Config.PARA);
		assertFalse(app.isAllowedTo(App.ALLOW_ALL, App.ALLOW_ALL, App.AllowedMethods.GET.toString()));

		app.grantResourcePermission(null, App.ALLOW_ALL, App.AllowedMethods.READ);
		assertTrue(app.getResourcePermissions().isEmpty());
		app.grantResourcePermission("", App.ALLOW_ALL, App.AllowedMethods.READ);
		assertTrue(app.getResourcePermissions().isEmpty());
		app.grantResourcePermission("123", "", App.AllowedMethods.READ);
		assertTrue(app.getResourcePermissions().isEmpty());
		app.grantResourcePermission("123", App.ALLOW_ALL, EnumSet.noneOf(App.AllowedMethods.class));
		assertTrue(app.getResourcePermissions().isEmpty());

		assertFalse(app.isAllowedTo("123", "test", "GET"));
		app.grantResourcePermission("123", App.ALLOW_ALL, App.AllowedMethods.READ);
		assertTrue(app.isAllowedTo("123", App.ALLOW_ALL, App.AllowedMethods.READ_ONLY.toString()));
		assertTrue(app.isAllowedTo("123", "res", "gEt"));
		assertFalse(app.isAllowedTo("1234", App.ALLOW_ALL, App.AllowedMethods.READ_ONLY.toString()));

		app.revokeResourcePermission("123", "_test");
		assertTrue(app.isAllowedTo("123", App.ALLOW_ALL, App.AllowedMethods.READ_ONLY.toString()));
		app.revokeResourcePermission(App.ALLOW_ALL, App.ALLOW_ALL);
		assertTrue(app.isAllowedTo("123", App.ALLOW_ALL, App.AllowedMethods.READ_ONLY.toString()));
		assertTrue(app.isAllowedTo("123", App.ALLOW_ALL, "get"));
		assertFalse(app.isAllowedTo("123", App.ALLOW_ALL, "bad_method"));
		app.revokeResourcePermission("123", App.ALLOW_ALL);
		assertFalse(app.isAllowedTo("123", App.ALLOW_ALL, App.AllowedMethods.READ_ONLY.toString()));
	}

}
