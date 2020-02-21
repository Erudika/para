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

import com.erudika.para.utils.Utils;
import java.util.Arrays;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.AbstractLdapAuthenticator;
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
public final class LDAPAuthenticator implements LdapAuthenticator {

	private static final Logger logger = LoggerFactory.getLogger(LDAPAuthenticator.class);
	private AbstractLdapAuthenticator authenticator = null;

	/**
	 * Default constructor.
	 * @param ldapSettings LDAP config map for an app
	 */
	public LDAPAuthenticator(Map<String, String> ldapSettings) {
		if (ldapSettings != null && ldapSettings.containsKey("security.ldap.server_url")) {
			String serverUrl = ldapSettings.get("security.ldap.server_url");
			String baseDN = ldapSettings.get("security.ldap.base_dn");
			String bindDN = Utils.noSpaces(ldapSettings.get("security.ldap.bind_dn"), "%20");
			String bindPass = ldapSettings.get("security.ldap.bind_pass");
			String userSearchBase = ldapSettings.get("security.ldap.user_search_base");
			String userSearchFilter = ldapSettings.get("security.ldap.user_search_filter");
			String userDnPattern = ldapSettings.get("security.ldap.user_dn_pattern");
			String passAttribute = ldapSettings.get("security.ldap.password_attribute");
			boolean usePasswordComparison = ldapSettings.containsKey("security.ldap.compare_passwords");

			DefaultSpringSecurityContextSource contextSource =
					new DefaultSpringSecurityContextSource(Arrays.asList(serverUrl), baseDN);
			contextSource.setAuthenticationSource(new SpringSecurityAuthenticationSource());
			contextSource.setCacheEnvironmentProperties(false);
			if (!bindDN.isEmpty()) {
				// this is usually not required for authentication - leave blank
				contextSource.setUserDn(bindDN);
			}
			if (!bindPass.isEmpty()) {
				// this is usually not required for authentication - leave blank
				contextSource.setPassword(bindPass);
			}
			LdapUserSearch userSearch = new FilterBasedLdapUserSearch(userSearchBase, userSearchFilter, contextSource);

			if (usePasswordComparison) {
				PasswordComparisonAuthenticator p = new PasswordComparisonAuthenticator(contextSource);
				p.setPasswordAttributeName(passAttribute);
				p.setUserDnPatterns(getUserDnPatterns(userDnPattern));
				p.setUserSearch(userSearch);
				authenticator = p;
			} else {
				BindAuthenticator b = new BindAuthenticator(contextSource);
				b.setUserDnPatterns(getUserDnPatterns(userDnPattern));
				b.setUserSearch(userSearch);
				authenticator = b;
			}
		}
	}

	@Override
	public DirContextOperations authenticate(Authentication authentication) {
		try {
			if (authenticator != null) {
				return authenticator.authenticate(authentication);
			}
		} catch (Exception e) {
			logger.warn("Failed to authenticate user with LDAP server: {}", e.getMessage());
		}
		throw new AuthenticationServiceException("LDAP user not found.");
	}

	private String[] getUserDnPatterns(String userDnPattern) {
		if (StringUtils.isBlank(userDnPattern)) {
			return new String[]{""};
		}
		if (userDnPattern.contains("|")) {
			return userDnPattern.split("\\|");
		}
		return new String[]{userDnPattern};
	}
}
