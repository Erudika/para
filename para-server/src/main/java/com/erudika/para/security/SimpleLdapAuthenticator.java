/*
 * Copyright 2013-2017 Erudika. http://erudika.com
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

import com.erudika.para.utils.Config;
import java.util.Arrays;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.authentication.encoding.LdapShaPasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticator;
import org.springframework.security.ldap.authentication.PasswordComparisonAuthenticator;
import org.springframework.security.ldap.authentication.SpringSecurityAuthenticationSource;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.search.LdapUserSearch;

/**
 * LDAP authenticator for either bind-based or password comparison authentication.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class SimpleLdapAuthenticator implements LdapAuthenticator {

	static final String SERVER_URL = Config.getConfigParam("security.ldap.server_url", "ldap://localhost:8389/");
	static final String BASE_DN = Config.getConfigParam("security.ldap.base_dn", "dc=springframework,dc=org");
	static final String BIND_DN = Config.getConfigParam("security.ldap.bind_dn", "");
	static final String BIND_PASS = Config.getConfigParam("security.ldap.bind_pass", "");

	static final String USER_SEARCH_BASE = Config.getConfigParam("security.ldap.user_search_base", "");
	static final String USER_SEARCH_FILTER = Config.getConfigParam("security.ldap.user_search_filter", "(cn={0})");
	static final String DN_PATTERN = Config.getConfigParam("security.ldap.user_dn_pattern", "uid={0},ou=people");
	static final String PASS_ATTRIBUTE = Config.getConfigParam("security.ldap.password_attribute", "userPassword");

	private final PasswordComparisonAuthenticator p;
	private final BindAuthenticator b;
	private final LdapContextSource contextSource;

	/**
	 * Default constructor.
	 */
	public SimpleLdapAuthenticator() {
		contextSource = getDefaultContextSource();
		LdapUserSearch userSearch = new FilterBasedLdapUserSearch(USER_SEARCH_BASE, USER_SEARCH_FILTER, contextSource);

		b = new BindAuthenticator(contextSource);
		b.setUserDnPatterns(new String[]{DN_PATTERN});
		b.setUserSearch(userSearch);

		p = new PasswordComparisonAuthenticator(contextSource);
		p.setPasswordAttributeName(PASS_ATTRIBUTE);
		p.setPasswordEncoder(new LdapShaPasswordEncoder());
		p.setUserDnPatterns(new String[]{DN_PATTERN});
		p.setUserSearch(userSearch);
	}

	@Override
	public DirContextOperations authenticate(Authentication authentication) {
		try {
			return p.authenticate(authentication);
		} catch (Exception e) {
			return b.authenticate(authentication);
		}
	}

	private LdapContextSource getDefaultContextSource() {
		DefaultSpringSecurityContextSource ldapContextSource =
				new DefaultSpringSecurityContextSource(Arrays.asList(SERVER_URL), BASE_DN);
		ldapContextSource.setAuthenticationSource(new SpringSecurityAuthenticationSource());
		ldapContextSource.setCacheEnvironmentProperties(false);
		if (!BIND_DN.isEmpty()) {
			ldapContextSource.setUserDn(BIND_DN);
		}
		if (!BIND_PASS.isEmpty()) {
			ldapContextSource.setPassword(BIND_PASS);
		}
		return ldapContextSource;
	}

}
