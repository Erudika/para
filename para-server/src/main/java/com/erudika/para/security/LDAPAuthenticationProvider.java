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
package com.erudika.para.security;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.authentication.AbstractLdapAuthenticationProvider;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.ldap.userdetails.InetOrgPersonContextMapper;

/**
 * An authentication provider that supports LDAP and AD.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class LDAPAuthenticationProvider implements AuthenticationProvider {

	/**
	 * Default constructor.
	 */
	public LDAPAuthenticationProvider() {
	}

	@Override
	public Authentication authenticate(Authentication authentication) {
		LDAPAuthentication auth = (LDAPAuthentication) authentication;
		if (auth != null && supports(authentication.getClass())) {
			Map<String, String> ldapSettings = auth.getLdapSettings();
			if (!ldapSettings.isEmpty()) {
				String adDomain = ldapSettings.get("security.ldap.active_directory_domain"); // set this to enable AD
				String ldapServerURL = ldapSettings.get("security.ldap.server_url");
				String searchFilter = ldapSettings.get("security.ldap.user_search_filter");
				AbstractLdapAuthenticationProvider ldapProvider;
				if (StringUtils.isBlank(adDomain)) {
					ldapProvider = new LdapAuthenticationProvider(new LDAPAuthenticator(ldapSettings));
				} else {
					// Fix for https://github.com/Erudika/scoold/issues/67
					authentication = new LDAPAuthentication(StringUtils.substringBefore(auth.getName(), "@"), auth.getCredentials());
					String rootDn = ldapSettings.get("security.ldap.base_dn");
					ldapProvider = StringUtils.isBlank(rootDn)
							? new ActiveDirectoryLdapAuthenticationProvider(adDomain, ldapServerURL)
							: new ActiveDirectoryLdapAuthenticationProvider(adDomain, ldapServerURL, rootDn);
					((ActiveDirectoryLdapAuthenticationProvider) ldapProvider).setConvertSubErrorCodesToExceptions(true);
					if (!StringUtils.isBlank(searchFilter)) {
						((ActiveDirectoryLdapAuthenticationProvider) ldapProvider).setSearchFilter(searchFilter);
					}
				}
				ldapProvider.setUserDetailsContextMapper(new InetOrgPersonContextMapper());
				return ldapProvider.authenticate(authentication);
			} else {
				throw new AuthenticationServiceException("LDAP configuration is missing.");
			}
		} else {
			throw new AuthenticationServiceException("Unsupported authentication type.");
		}
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}
}
