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

import java.util.Collection;
import java.util.Collections;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * A simple wrapper for the authentication object.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class UserAuthentication implements Authentication {
	private static final long serialVersionUID = 1L;

	private final AuthenticatedUserDetails principal;
	private final boolean authenticated;

	/**
	 * Default constructor.
	 * @param principal the user object
	 */
	public UserAuthentication(AuthenticatedUserDetails principal) {
		this.principal = principal;
		this.authenticated = true;
	}

	/**
	 * A list of roles for the authenticated user.
	 * @return a list of roles
	 */
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if (principal == null) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableCollection(principal.getAuthorities());
	}

	/**
	 * Always null (unused).
	 * @return credentials string
	 */
	public Object getCredentials() {
		return null;
	}

	/**
	 * The user details object.
	 * @return the user
	 */
	public Object getDetails() {
		return principal;
	}

	/**
	 * The user details object.
	 * @return the user
	 */
	public Object getPrincipal() {
		return principal;
	}

	/**
	 * Checks if the user is authenticated.
	 * @return true if authenticated
	 */
	public boolean isAuthenticated() {
		return authenticated;
	}

	/**
	 * Not supported.
	 * @param isAuthenticated true if authenticated
	 */
	public void setAuthenticated(boolean isAuthenticated) {
		throw new UnsupportedOperationException();
	}

	/**
	 * The user identifier.
	 * @return the identifier
	 */
	public String getName() {
		if (principal == null) {
			return null;
		}
		return principal.getIdentifier();
	}
}
