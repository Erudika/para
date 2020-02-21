/*
 * Copyright 2013-2020 Erudika. http://erudika.com
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
package com.erudika.para.security;

import com.erudika.para.core.App;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import java.util.Map;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.test.unboundid.LdapTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class LdapAuthTest {

	private static LDAPAuthenticator bindAuthenticator;
	private static LDAPAuthenticator passComparingAuthenticator;
	private static InMemoryDirectoryServer server;
	private static Authentication bob  = new UsernamePasswordAuthenticationToken("bob", "bobspassword");
	private static Authentication ben  = new UsernamePasswordAuthenticationToken("ben", "benspassword");

	@BeforeClass
	public static void setUpClass() throws Exception {
		InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=springframework,dc=org");
		config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("LDAP", 8389));
		config.addAdditionalBindCredentials("uid=admin,ou=system", "secret");
		server = new InMemoryDirectoryServer(config);
		server.startListening();

		LdapTestUtils.loadLdif(server, new ClassPathResource("test-server.ldif"));
		Map<String, String> defaultSettings = SecurityUtils.getLdapSettingsForApp(new App("test"));
		defaultSettings.put("security.ldap.user_dn_pattern", "uid={0},ou=people");
		bindAuthenticator = new LDAPAuthenticator(defaultSettings);
		defaultSettings.put("security.ldap.compare_passwords", "true");
		passComparingAuthenticator = new LDAPAuthenticator(defaultSettings);
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		server.shutDown("LDAP", true);
	}

	@Test
	public void testAuthenticationWithCorrectPasswordSucceeds() {
		DirContextAdapter user1 = (DirContextAdapter) passComparingAuthenticator.authenticate(ben);
		System.out.println(user1.getDn());
		assertEquals("ben", user1.getStringAttribute("uid"));
		DirContextAdapter user2 = (DirContextAdapter) bindAuthenticator.authenticate(bob);
		assertEquals("bob", user2.getStringAttribute("uid"));
	}

	@Test
	public void testAuthenticationWithInvalidUserNameFails() {
		try {
			bindAuthenticator.authenticate(new UsernamePasswordAuthenticationToken("nonexistentsuser", "password"));
			fail("Shouldn't be able to bind with invalid username");
		} catch (Exception expected) { }
	}

	@Test
	public void testAuthenticationWithUserSearch() throws Exception {
		DirContextOperations result = bindAuthenticator.
				authenticate(new UsernamePasswordAuthenticationToken("mouse, jerry", "jerryspassword"));
		assertEquals("Mouse, Jerry", result.getStringAttribute("cn"));
	}

}
