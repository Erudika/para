/*
 * Copyright 2013-2022 Erudika. https://erudika.com
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
package com.erudika.para.server.security;

import com.erudika.para.core.User;
import org.apache.commons.lang3.Strings;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Simple user service. Looks up users in the data store.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SimpleUserService implements UserDetailsService {

	/**
	 * Loads a user from the data store.
	 * @param ident the user identifier
	 * @return a user object or null if user is not found
	 */
	public UserDetails loadUserByUsername(String ident) {
		User user = new User();
		// check if the cookie has an appid prefix
		// and load user from the corresponding app
		if (Strings.CS.contains(ident, "/")) {
			String[] parts = ident.split("/");
			user.setAppid(parts[0]);
			ident = parts[1];
		}
		user.setIdentifier(ident);
		user = loadUser(user);

		if (user == null) {
			throw new UsernameNotFoundException(ident);
		}

		return new AuthenticatedUserDetails(user);
	}

	private User loadUser(User u) {
		User authUser = SecurityUtils.getAuthenticatedUser();
		if (authUser != null) {
			return authUser;
		} else {
			return User.readUserForIdentifier(u);
		}
	}
}
