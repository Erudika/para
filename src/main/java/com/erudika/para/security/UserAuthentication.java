/*
 * Copyright 2013 Alex Bogdanovski <alex@erudika.com>.
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
 * You can reach the author at: https://github.com/albogdano
 */
package com.erudika.para.security;

import com.erudika.para.core.User;
import java.util.Collection;
import java.util.HashSet;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class UserAuthentication implements Authentication {
	private static final long serialVersionUID = 1L;

	private final User principal;
	private final Object details;
	private boolean authenticated;

	public UserAuthentication(User principal) {
		this.principal = principal;
		this.details = principal;
		this.authenticated = true;
	}

	public Collection<GrantedAuthority> getAuthorities() {
		return new HashSet<GrantedAuthority>(principal.getAuthorities());
	}

	public Object getCredentials() {
		return principal.getAuthtoken();
	}

	public Object getDetails() {
		return details;
	}

	public Object getPrincipal() {
		return principal;
	}

	public boolean isAuthenticated() {
		return authenticated;
	}

	public void setAuthenticated(boolean isAuthenticated) {
		throw new UnsupportedOperationException();
	}

	public String getName() {
		return principal.getIdentifier();
	}
}
