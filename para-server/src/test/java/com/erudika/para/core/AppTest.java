/*
 * Copyright 2013-2020 Erudika. https://erudika.com
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

import static com.erudika.para.core.App.ALLOW_ALL;
import static com.erudika.para.core.App.AllowedMethods.ALL;
import static com.erudika.para.core.App.AllowedMethods.DELETE;
import static com.erudika.para.core.App.AllowedMethods.GET;
import static com.erudika.para.core.App.AllowedMethods.GUEST;
import static com.erudika.para.core.App.AllowedMethods.NONE;
import static com.erudika.para.core.App.AllowedMethods.OWN;
import static com.erudika.para.core.App.AllowedMethods.PATCH;
import static com.erudika.para.core.App.AllowedMethods.POST;
import static com.erudika.para.core.App.AllowedMethods.PUT;
import static com.erudika.para.core.App.AllowedMethods.READ;
import static com.erudika.para.core.App.AllowedMethods.READ_AND_WRITE;
import static com.erudika.para.core.App.AllowedMethods.READ_ONLY;
import static com.erudika.para.core.App.AllowedMethods.READ_WRITE;
import static com.erudika.para.core.App.AllowedMethods.WRITE;
import static com.erudika.para.core.App.AllowedMethods.WRITE_ONLY;
import com.erudika.para.utils.Config;
import static com.erudika.para.validation.Constraint.url;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	public void testCreate() {
		App app = new App("test");
		app.setAppid("notRoot");
		assertNull(app.create());
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
		app.setId(" test-app");
		assertEquals("app:test-app", app.getId());
		assertEquals("test-app", app.getAppIdentifier());
		app.setId("  test-app  ");
		assertEquals("app:test-app", app.getId());
		assertEquals("test-app", app.getAppIdentifier());
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
		assertTrue(app.getValidationConstraints().isEmpty());
	}

	@Test
	public void testGetAllResourcePermissions() {
		App app = new App(Config.PARA);
		assertTrue(app.getAllResourcePermissions().isEmpty());
		app.grantResourcePermission("123", ALLOW_ALL, READ);
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
		assertFalse(app.isAllowedTo(ALLOW_ALL, ALLOW_ALL, GET.toString()));

		app.grantResourcePermission("123", ALLOW_ALL, READ);
		assertFalse(app.isAllowedTo("123", ALLOW_ALL, POST.toString()));
		assertTrue(app.isAllowedTo("123", "_test", GET.toString()));
		assertTrue(app.isAllowedTo("123", "_test1", READ_ONLY.toString()));

		app.revokeAllResourcePermissions("123");
		assertFalse(app.isAllowedTo("123", "_test", GET.toString()));
		assertFalse(app.isAllowedTo("123", "_test1", READ_ONLY.toString()));

		app.grantResourcePermission(ALLOW_ALL, ALLOW_ALL, EnumSet.of(GET, GUEST));
		assertTrue(app.isAllowedTo("", "sysprops", GET.toString()));
		assertFalse(app.isAllowedTo("", "sysprops", POST.toString()));
		assertTrue(app.isAllowedTo("123", "any", READ_ONLY.toString()));
		assertTrue(app.isAllowedTo("", "any", READ_ONLY.toString()));
	}

	@Test
	public void testGrantRevokeResourcePermission() {
		App app = new App(Config.PARA);
		assertFalse(app.isAllowedTo(ALLOW_ALL, ALLOW_ALL, GET.toString()));

		app.grantResourcePermission(null, ALLOW_ALL, READ);
		assertTrue(app.getResourcePermissions().isEmpty());
		app.grantResourcePermission("", ALLOW_ALL, READ);
		assertTrue(app.getResourcePermissions().isEmpty());
		app.grantResourcePermission("123", "", READ);
		assertTrue(app.getResourcePermissions().isEmpty());
		app.grantResourcePermission("123", ALLOW_ALL, EnumSet.noneOf(App.AllowedMethods.class));
		assertTrue(app.getResourcePermissions().isEmpty());

		app.grantResourcePermission("123", "res", NONE);
		assertFalse(app.getResourcePermissions().isEmpty());
		assertFalse(app.isAllowedTo("123", "test", "GET"));

		assertFalse(app.isAllowedTo("123", "test", "GET"));
		app.grantResourcePermission("123", ALLOW_ALL, READ);
		assertTrue(app.isAllowedTo("123", ALLOW_ALL, READ_ONLY.toString()));

		app.grantResourcePermission("1235", ALLOW_ALL, EnumSet.of(READ_ONLY, PATCH));
		assertTrue(app.isAllowedTo("1235", ALLOW_ALL, READ_ONLY.toString()));
		assertFalse(app.isAllowedTo("1235", ALLOW_ALL, PATCH.toString()));
		assertFalse(app.isAllowedTo("1235", ALLOW_ALL, DELETE.toString()));

		app.grantResourcePermission("1236", ALLOW_ALL, EnumSet.of(READ_ONLY, WRITE_ONLY));
		assertTrue(app.isAllowedTo("1236", ALLOW_ALL, ALLOW_ALL));

		// explicitly denied above
		assertFalse(app.isAllowedTo("123", "res", "gEt"));
		assertTrue(app.isAllowedTo("123", "res1/res2", "gEt"));
		assertFalse(app.isAllowedTo("1234", ALLOW_ALL, READ_ONLY.toString()));

		app.revokeResourcePermission("123", "_test");
		assertTrue(app.isAllowedTo("123", ALLOW_ALL, READ_ONLY.toString()));
		app.revokeResourcePermission(ALLOW_ALL, ALLOW_ALL);
		assertTrue(app.isAllowedTo("123", ALLOW_ALL, READ_ONLY.toString()));
		assertTrue(app.isAllowedTo("123", ALLOW_ALL, "get"));
		assertFalse(app.isAllowedTo("123", ALLOW_ALL, "bad_method"));
		app.revokeResourcePermission("123", ALLOW_ALL);
		assertFalse(app.isAllowedTo("123", ALLOW_ALL, READ_ONLY.toString()));

		app.grantResourcePermission(ALLOW_ALL, "/0123/", READ);
		assertTrue(app.isAllowedTo(ALLOW_ALL, "0123", "get"));
		// dots not allowed in resource path - causes Elasticsearch to fail
		app.grantResourcePermission(ALLOW_ALL, "/4.5.6/", READ);
		assertTrue(app.isAllowedTo(ALLOW_ALL, "456", "get"));
		assertFalse(app.isAllowedTo("4.5.6", ALLOW_ALL, "get"));

		// "OWN" permission should be added too
		app.grantResourcePermission("123", "todo", EnumSet.of(READ_WRITE, OWN));
		app.grantResourcePermission("123", "todo2", EnumSet.of(READ_ONLY, OWN));
		assertTrue(app.getResourcePermissions().get("123").get("todo").contains(OWN.toString()));
		assertTrue(app.getResourcePermissions().get("123").get("todo2").contains(OWN.toString()));
	}

	@Test
	public void testIsAllowed() {
		App app = new App();
		assertFalse(app.isAllowed(null, null, null));
		assertFalse(app.isAllowed("user1", "posts", "GET"));

		app.setResourcePermissions(new HashMap<String, Map<String, List<String>>>() {{
			put("user1", new HashMap<String, List<String>>() {{
				put("posts", Arrays.asList("GET", "PUT"));
				put("posts/123", Arrays.asList("DELETE"));
				// subpaths
				put("_users/admins", Arrays.asList("GET"));
				put("_users/admins/321", Arrays.asList("PUT", "POST"));
				// simple "allow all"
				put("users", Arrays.asList(ALLOW_ALL));
			}});
			put(ALLOW_ALL, new HashMap<String, List<String>>() {{
				// simple "allow all"
				put("super/secret/resource", Arrays.asList(NONE.toString()));
			}});
		}});

		assertTrue(app.isAllowed("user1", "posts", "get"));
		assertTrue(app.isAllowed("user1", "posts", "PUT"));
		assertFalse(app.isAllowed("user1", "posts123", "PUT")); // !!! important
		assertTrue(app.isAllowed("user1", "posts/456", "PUT"));
		assertFalse(app.isAllowed("user1", "posts", "DELETE"));
		assertTrue(app.isAllowed("user1", "posts/123", "DELETE"));
		assertFalse(app.isAllowed("user1", "posts/1234", "DELETE")); // !!! important
		assertFalse(app.isAllowed("user1", "posts", "ELSE"));
		// subpaths
		assertFalse(app.isAllowed("user1", "_users", "GET"));
		assertTrue(app.isAllowed("user1", "_users/admins", "GET"));
		assertFalse(app.isAllowed("user1", "_users/admins", "PUT"));
		assertTrue(app.isAllowed("user1", "_users/admins/123", "GET"));
		assertFalse(app.isAllowed("user1", "_users/admins/123", "PUT"));
		assertFalse(app.isAllowed("user1", "_users/admin", "GET"));
		assertFalse(app.isAllowed("user1", "_users/123", "GET"));
		assertFalse(app.isAllowed("user1", "_users/123", "PUT"));
		assertTrue(app.isAllowed("user1", "_users/admins/321", "GET"));
		assertTrue(app.isAllowed("user1", "_users/admins/321", "POST"));
		assertFalse(app.isAllowed("user1", "_users/admins/321", "DELETE"));
		// allow all
		assertTrue(app.isAllowed("user1", "users/12345", "DELETE"));
		assertTrue(app.isAllowed("user1", "users/12345", "PUT"));
		assertTrue(app.isAllowed("user1", "users/12345", "POST"));
		assertFalse(app.isAllowed("user2", "users/12345", "POST"));
		assertTrue(app.isAllowed("user1", "users/12345", "ELSE"));
		// deny all
		assertFalse(app.isAllowed(ALLOW_ALL, "super/secret/resource", "GET"));
		assertFalse(app.isAllowed(ALLOW_ALL, "super/secret/resource", "PUT"));
		assertFalse(app.isAllowed("user1", "super/secret/resource", "GET"));
		assertFalse(app.isAllowed("user2", "super/secret/resource", "POST"));
		assertFalse(app.isAllowed("user2", "super/secret/resource", NONE.toString()));
		// guest access
		assertFalse(app.isAllowed(ALLOW_ALL, "users/12345", GUEST.toString()));
		assertFalse(app.isAllowed(ALLOW_ALL, "guest/access", GUEST.toString()));
		app.grantResourcePermission(ALLOW_ALL, "guest/access", READ, true);
		assertTrue(app.isAllowed(ALLOW_ALL, "guest/access", "GET"));
		assertTrue(app.isAllowed(ALLOW_ALL, "guest/access", GUEST.toString()));

		app.grantResourcePermission(ALLOW_ALL, "guest/test", EnumSet.of(PUT, READ_WRITE), false);
		assertFalse(app.isAllowed(ALLOW_ALL, "guest/test", GUEST.toString()));

		app.grantResourcePermission(ALLOW_ALL, "publicRes", ALL, true);
		assertTrue(app.isAllowed(ALLOW_ALL, "publicRes/test", GUEST.toString()));

		app.grantResourcePermission(ALLOW_ALL, "publicSubResource", READ_AND_WRITE, false);
		assertFalse(app.isAllowed(ALLOW_ALL, "publicSubResource", GUEST.toString()));
		assertFalse(app.isAllowed(ALLOW_ALL, "publicSubResource/test", GUEST.toString()));
		app.grantResourcePermission(ALLOW_ALL, "publicSubResource/test", EnumSet.of(GUEST), false);
		assertTrue(app.isAllowed(ALLOW_ALL, "publicSubResource/test", GUEST.toString()));
		// this is not valid because subject can't be authenticated
		app.grantResourcePermission("someUser1", "illegalPublic", EnumSet.of(GET, GUEST), true);
		assertFalse(app.isAllowed(ALLOW_ALL, "illegalPublic", GUEST.toString()));
		assertFalse(app.isAllowed("someUser1", "illegalPublic", GUEST.toString()));
		// test wildcard permissions
		app.grantResourcePermission(ALLOW_ALL, "/wildcard/*", READ);
		assertFalse(app.isAllowedTo(ALLOW_ALL, "wildcard", "get"));
		assertTrue(app.isAllowedTo(ALLOW_ALL, "wildcard/123", "get"));
		assertTrue(app.isAllowedTo(ALLOW_ALL, "wildcard/123/456", "get"));
		assertFalse(app.isAllowedTo(ALLOW_ALL, "wildcard/123/456", "POST"));

		app.grantResourcePermission(ALLOW_ALL, "/notwildcard/test", READ);
		assertFalse(app.isAllowedTo(ALLOW_ALL, "notwildcard", "get"));
		assertFalse(app.isAllowedTo(ALLOW_ALL, "notwildcard/123", "get"));
		assertTrue(app.isAllowedTo(ALLOW_ALL, "notwildcard/test", "get"));
	}

	@Test
	public void testIsDeniedExplicitly() {
		App app = new App();
		App app2 = new App();

		User u = new User("u123");
		User u2 = new User("someone_else");

		String res1 = "some/resource/1";
		String res2 = "cant/touch/this";

		assertFalse(app.isDeniedExplicitly(u.getId(), res2, "GET"));

		app.grantResourcePermission(ALLOW_ALL, res1, WRITE);
		assertTrue(app.isAllowedTo(u.getId(), res1, "POST"));
		assertTrue(app.isAllowedTo(u2.getId(), res1, "POST"));

		assertFalse(app.isDeniedExplicitly(u.getId(), ALLOW_ALL, "GET"));
		assertFalse(app.isDeniedExplicitly(u2.getId(), ALLOW_ALL, "GET"));
		assertFalse(app.isDeniedExplicitly(u.getId(), ALLOW_ALL, "POST"));
		assertFalse(app.isDeniedExplicitly(u.getId(), res1, "POST"));
		assertFalse(app.isDeniedExplicitly(u2.getId(), res1, "PUT"));

		// explicit restrictions
		app.grantResourcePermission(u2.getId(), res2, NONE);
		assertFalse(app.isAllowedTo(u2.getId(), res2, "GET"));
		assertTrue(app.isDeniedExplicitly(u2.getId(), res2, "GET"));
		assertTrue(app.isDeniedExplicitly(u2.getId(), res2, "DELETE"));
		assertFalse(app.isDeniedExplicitly(u2.getId(), ALLOW_ALL, "PUT"));

		// specific restrictions per "id" take precedence
		app.grantResourcePermission(ALLOW_ALL, res2, READ);
		assertFalse(app.isAllowedTo(u2.getId(), res2, "GET"));
		assertTrue(app.isDeniedExplicitly(u2.getId(), res2, "GET"));

		app.grantResourcePermission(ALLOW_ALL, ALLOW_ALL, READ);
		assertFalse(app.isDeniedExplicitly(u2.getId(), "this/is/test", "GET"));
		assertTrue(app.isDeniedExplicitly(u2.getId(), res2, "GET"));

		// wildcard restrictions
		assertFalse(app2.isDeniedExplicitly(u.getId(), res2, "POST"));
		app2.grantResourcePermission(ALLOW_ALL, res2, READ);
		assertFalse(app2.isAllowedTo(u.getId(), res2, "POST"));
		assertFalse(app2.isDeniedExplicitly(u.getId(), res2, "POST"));
		assertFalse(app2.isAllowedTo(u2.getId(), res2, "PUT"));
		assertFalse(app2.isDeniedExplicitly(u2.getId(), res2, "PUT"));
	}

}
