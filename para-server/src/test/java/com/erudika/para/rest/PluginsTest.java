/*
 * Copyright 2013-2026 Erudika. https://erudika.com
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
package com.erudika.para.rest;

import com.erudika.para.core.App;
import com.erudika.para.core.User;
import com.erudika.para.core.persistence.DAO;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Para;
import com.erudika.para.server.rest.PluginsApi;
import com.erudika.para.server.security.AppAuthentication;
import com.erudika.para.server.security.AuthenticatedUserDetails;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tests the plugin metadata endpoint.
 *
 * <p>Deliberately avoids installing anything: these cover the parts an admin UI depends on, namely
 * that the catalogue is complete, that default versions resolve from the filtered resource, and
 * that unknown plugins are rejected rather than silently accepted.</p>
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
class PluginsTest {

	private final PluginsApi controller = new PluginsApi();

	@BeforeEach
	void authenticateRootApp() {
		SecurityContextHolder.getContext().setAuthentication(
				new AppAuthentication(new App(Para.getConfig().appName())));
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void listsEveryOfficialPlugin() {
		ResponseEntity<?> response = controller.list();

		Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
		Map<String, Object> body = asMap(response.getBody());

		String[] expected = {
			"para-dao-sql", "para-dao-mongodb", "para-dao-cassandra", "para-dao-dynamodb",
			"para-search-lucene", "para-search-elasticsearch", "para-cache-hazelcast",
			"para-storage-s3", "para-queue-sqs", "para-email-ses"
		};
		for (String artifactId : expected) {
			Assertions.assertTrue(body.containsKey(artifactId), "missing plugin " + artifactId);
			@SuppressWarnings("unchecked")
			Map<String, Object> entry = (Map<String, Object>) body.get(artifactId);
			Assertions.assertNotNull(entry.get("type"));
			Assertions.assertNotNull(entry.get("implementationClasses"));
			Assertions.assertFalse(((java.util.List<?>) entry.get("implementationClasses")).isEmpty());
			Assertions.assertNotNull(entry.get("installed"));
			Assertions.assertNotNull(entry.get("active"));
		}
	}

	@Test
	void mapsPluginsToTheRightTypes() {
		Map<String, Object> body = asMap(controller.list().getBody());

		// The type tells the admin UI which config property controls the plugin: dao, search,
		// cache, fs, q or emailer.
		Assertions.assertEquals("DAO", entry(body, "para-dao-sql").get("type"));
		Assertions.assertEquals("DAO", entry(body, "para-dao-mongodb").get("type"));
		Assertions.assertEquals("DAO", entry(body, "para-dao-cassandra").get("type"));
		Assertions.assertEquals("DAO", entry(body, "para-dao-dynamodb").get("type"));
		Assertions.assertEquals("Search", entry(body, "para-search-lucene").get("type"));
		Assertions.assertEquals("Search", entry(body, "para-search-elasticsearch").get("type"));
		Assertions.assertEquals("Cache", entry(body, "para-cache-hazelcast").get("type"));
		Assertions.assertEquals("FileStore", entry(body, "para-storage-s3").get("type"));
		Assertions.assertEquals("Queue", entry(body, "para-queue-sqs").get("type"));
		Assertions.assertEquals("Emailer", entry(body, "para-email-ses").get("type"));
	}

	@Test
	void resolvesDefaultVersionsFromFilteredResource() {
		Properties props = new Properties();
		try (var is = getClass().getClassLoader().getResourceAsStream("plugin-versions.properties")) {
			Assertions.assertNotNull(is, "plugin-versions.properties is missing from the build");
			props.load(is);
		} catch (Exception e) {
			Assertions.fail("could not read plugin-versions.properties", e);
		}

		// If filtering did not run, the raw ${...} placeholders would still be present.
		for (String artifactId : props.stringPropertyNames()) {
			String value = props.getProperty(artifactId);
			Assertions.assertFalse(value.startsWith("${"),
					artifactId + " was not filtered: " + value);
			Assertions.assertTrue(value.matches("\\d+\\.\\d+\\.\\d+.*"),
					artifactId + " has an unexpected version: " + value);
		}
		Assertions.assertTrue(props.containsKey("com.erudika.para-dao-sql"));
		Assertions.assertTrue(props.containsKey("com.erudika.para-search-lucene"));
	}

	@Test
	void reportsTheRunningImplementationAsActive() {
		// "active" reflects the implementation that is actually running, not the plugin named in
		// configuration. A bare unit test runs Para's mock DAO, so stub a DAO that reports the class
		// name declared by para-dao-sql, then put the original back.
		CoreUtils utils = CoreUtils.getInstance();
		DAO original = utils.getDao();
		DAO running = Mockito.mock(DAO.class);
		Mockito.when(running.getDaoClassName()).thenReturn("H2DAO");
		try {
			utils.setDao(running);

			Map<String, Object> body = asMap(controller.list().getBody());

			// implementationClasses holds every ServiceLoader class name the plugin provides, so the
			// admin UI can show which value to put in the config for each one.
			@SuppressWarnings("unchecked")
			List<String> sqlImpls = (List<String>) entry(body, "para-dao-sql").get("implementationClasses");
			Assertions.assertNotNull(sqlImpls);
			Assertions.assertTrue(sqlImpls.contains("H2DAO"),
					"para-dao-sql should claim H2DAO but declares " + sqlImpls);
			Assertions.assertEquals(Boolean.TRUE, entry(body, "para-dao-sql").get("active"),
					"para-dao-sql should be active while H2DAO is running");
			Assertions.assertEquals(Boolean.FALSE, entry(body, "para-dao-mongodb").get("active"),
					"para-dao-mongodb should not be active while H2DAO is running");
		} finally {
			utils.setDao(original);
		}
	}

	@Test
	void rejectsUnknownPluginOnPreview() {
		Assertions.assertEquals(HttpStatus.NOT_FOUND, controller.preview("not-a-plugin").getStatusCode());
	}

	@Test
	void rejectsUnknownPluginOnInstall() {
		Assertions.assertEquals(HttpStatus.NOT_FOUND,
				controller.install("not-a-plugin", null).getStatusCode());
	}

	@Test
	void rejectsUnknownPluginOnUninstall() {
		Assertions.assertEquals(HttpStatus.NOT_FOUND,
				controller.uninstall("not-a-plugin", "1.0.0").getStatusCode());
	}

	@Test
	void uninstallsNothingWhenVersionIsAbsent() {
		Assertions.assertEquals(HttpStatus.NOT_FOUND,
				controller.uninstall("para-dao-sql", "0.0.0-does-not-exist").getStatusCode());
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> entry(Map<String, Object> body, String artifactId) {
		Object value = body.get(artifactId);
		Assertions.assertNotNull(value, "missing plugin " + artifactId);
		return (Map<String, Object>) value;
	}

	/**
	 * Guard against a plugin being added to the catalogue without a resolvable version, which would
	 * make the admin UI ask for a version it should already know.
	 */
	@Test
	void everyPluginHasADefaultVersion() throws Exception {
		Method m = Class.forName("com.erudika.para.server.rest.PluginsApi$PluginDescriptor")
				.getDeclaredMethod("defaultVersion", String.class);
		m.setAccessible(true);

		String[] all = {
			"para-dao-sql", "para-dao-mongodb", "para-dao-cassandra", "para-dao-dynamodb",
			"para-search-lucene", "para-search-elasticsearch", "para-cache-hazelcast",
			"para-storage-s3", "para-queue-sqs", "para-email-ses"
		};
		for (String artifactId : all) {
			Object version = m.invoke(null, artifactId);
			Assertions.assertNotNull(version);
			Assertions.assertFalse(version.toString().isBlank(),
					artifactId + " has no default version in plugin-versions.properties");
		}
	}

	@Test
	void rejectsRequestsWhenPluginsApiIsDisabled() {
		System.setProperty("para.plugins_api_enabled", "false");
		try {
			ResponseEntity<?> response = controller.list();
			Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
			Assertions.assertEquals("Plugins API is disabled.", asMap(response.getBody()).get("error"));
		} finally {
			System.clearProperty("para.plugins_api_enabled");
		}
	}

	@Test
	void rejectsNonRootApp() {
		SecurityContextHolder.getContext().setAuthentication(
				new AppAuthentication(new App("other-app")));
		ResponseEntity<?> response = controller.list();
		Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		Assertions.assertEquals("Only the root app can manage plugins.",
				asMap(response.getBody()).get("error"));
	}

	@Test
	void rejectsUnauthenticatedRequest() {
		SecurityContextHolder.clearContext();
		Assertions.assertEquals(HttpStatus.FORBIDDEN, controller.list().getStatusCode());
	}

	@Test
	void allowsUsersOfTheRootApp() {
		User user = new User("user-1");
		user.setAppid(Para.getConfig().appName());
		AuthenticatedUserDetails principal = new AuthenticatedUserDetails(user);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

		CoreUtils utils = CoreUtils.getInstance();
		DAO original = utils.getDao();
		DAO dao = Mockito.mock(DAO.class);
		// getPrincipalApp() resolves an authenticated user to the app it belongs to
		Mockito.when(dao.read(Para.getConfig().getRootAppIdentifier(), App.id(user.getAppid())))
				.thenReturn(new App(Para.getConfig().appName()));
		Mockito.when(dao.getDaoClassName()).thenReturn("MockDAO");
		try {
			utils.setDao(dao);
			Assertions.assertEquals(HttpStatus.OK, controller.list().getStatusCode());
		} finally {
			utils.setDao(original);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object body) {
		Assertions.assertNotNull(body, "expected a JSON body");
		return (Map<String, Object>) body;
	}
}
