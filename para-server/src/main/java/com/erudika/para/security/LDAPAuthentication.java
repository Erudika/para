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

import com.erudika.para.core.App;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * Represents a user authentication with LDAP.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class LDAPAuthentication extends UsernamePasswordAuthenticationToken {

	private static final long serialVersionUID = 1L;

	private App app;

	/**
	 * Default constructor.
	 * @param principal an LDAP uid
	 * @param credentials password
	 */
	public LDAPAuthentication(Object principal, Object credentials) {
		super(principal, credentials);
	}

	/**
	 * @param app {@link App}
	 * @return this
	 */
	public LDAPAuthentication withApp(App app) {
		this.app = app;
		return this;
	}

	/**
	 * @return the {@link App}
	 */
	public App getApp() {
		return app;
	}

	/**
	 * @return LDAP settings map
	 */
	public Map<String, String> getLdapSettings() {
		return SecurityUtils.getLdapSettingsForApp(app);
	}

}
