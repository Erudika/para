/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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

import com.eaio.uuid.UUID;
import com.erudika.para.core.User;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.openid.OpenIDAttribute;
import org.springframework.security.openid.OpenIDAuthenticationToken;

/**
 * Simple user service. Looks up users in the data store.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SimpleUserService implements UserDetailsService,
		AuthenticationUserDetailsService<OpenIDAuthenticationToken> {

	/**
	 * Loads a user from the data store.
	 * @param ident the user identifier
	 * @return a user object or null if user is not found
	 */
	public UserDetails loadUserByUsername(String ident) {
		User user = new User();
		user.setIdentifier(ident);
		user = loadUser(user);

		if (user == null) {
			throw new UsernameNotFoundException(ident);
		}

		return user;
	}

	/**
	 * Loads a user from the data store or creates a new user from an OpenID profile
	 * @param token the OpenID authentication token holding the user profile
	 * @return a user object or null if user is not found
	 */
	public UserDetails loadUserDetails(OpenIDAuthenticationToken token) {
		if (token == null) {
			return null;
		}

		User user = new User();
		user.setIdentifier(token.getIdentityUrl());
		user = loadUser(user);

		if (user == null) {
			// create new OpenID user
			String email = "email@domain.com";
			String firstName = null, lastName = null, fullName = null;
			List<OpenIDAttribute> attributes = token.getAttributes();

			for (OpenIDAttribute attribute : attributes) {
				if (attribute.getName().equals("email")) {
					email = attribute.getValues().get(0);
				}
				if (attribute.getName().equals("firstname")) {
					firstName = attribute.getValues().get(0);
				}
				if (attribute.getName().equals("lastname")) {
					lastName = attribute.getValues().get(0);
				}
				if (attribute.getName().equals("fullname")) {
					fullName = attribute.getValues().get(0);
				}
			}

			if (fullName == null) {
				if (firstName == null) {
					firstName = "No";
				}
				if (lastName == null) {
					lastName = "Name";
				}
				fullName = firstName.concat(" ").concat(lastName);
			}

			user = new User();
			user.setActive(true);
			user.setEmail(email);
			user.setName(fullName);
			user.setPassword(new UUID().toString());
			user.setIdentifier(token.getIdentityUrl());
			String id = user.create();
			if (id == null) {
				throw new BadCredentialsException("Authentication failed: cannot create new user.");
			}
		}

		return user;
	}

	private User loadUser(User u) {
		return User.readUserForIdentifier(u);
	}
}
