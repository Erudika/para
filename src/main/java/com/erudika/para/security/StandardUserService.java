/*
 * Copyright 2013 Alex Bogdanovski <albogdano@me.com>.
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
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.openid.OpenIDAttribute;
import org.springframework.security.openid.OpenIDAuthenticationToken;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class StandardUserService implements UserDetailsService, 
		AuthenticationUserDetailsService<OpenIDAuthenticationToken> {

	public UserDetails loadUserByUsername(String ident) throws UsernameNotFoundException {
		User user = User.readUserForIdentifier(ident);

		if (user == null) {
			throw new UsernameNotFoundException(ident);
		}

		return user;
	}

	public UserDetails loadUserDetails(OpenIDAuthenticationToken token) {
		if (token == null) {
			return null;
		}

		String identifier = token.getIdentityUrl();
		User user = User.readUserForIdentifier(identifier);

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
			user.setEmail(email);
			user.setName(fullName);
			user.setIdentifier(identifier);
			String id = user.create();
			if (id == null) {
				throw new BadCredentialsException("Authentication failed: cannot create new user.");
			}
		}

		return user;
	}
}