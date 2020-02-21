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
import com.erudika.para.core.User;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

/**
 * An authentication provider that verifies JSON web tokens.
 * It uses the Nimbus JOSE + JWT library to validate tokens.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class JWTAuthenticationProvider implements AuthenticationProvider {

	/**
	 * Default constructor.
	 */
	public JWTAuthenticationProvider() {
	}

	@Override
	public Authentication authenticate(Authentication authentication) {
		JWTAuthentication jwtToken = (JWTAuthentication) authentication;
		if (jwtToken != null && supports(authentication.getClass())) {
			User user = SecurityUtils.getAuthenticatedUser(authentication);
			App app = jwtToken.getApp();
			if (app != null) {
				boolean isUser = user != null;
				// "super token" support, i.e. when a JWT is signed with app's secret key
				String secret = jwtToken.getApp().getSecret() + (isUser ? user.getTokenSecret() : "");
				if (SecurityUtils.isValidJWToken(secret, jwtToken.getJwt())) {
					return isUser ? jwtToken : new AppAuthentication(app);
				} else {
					throw new BadCredentialsException("Invalid or expired token.");
				}
			} else {
				throw new AuthenticationServiceException("App not found.");
			}
		} else {
			throw new AuthenticationServiceException("Unsupported token type.");
		}
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return JWTAuthentication.class.isAssignableFrom(authentication);
	}
}
