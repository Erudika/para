/*
 * Copyright 2013-2016 Erudika. http://erudika.com
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

import com.erudika.para.core.User;
import com.erudika.para.utils.Config;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

/**
 * A filter that handles simple authentication requests with email and password.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class PasswordAuthFilter extends AbstractAuthenticationProcessingFilter {

	private static final String PASSWORD = "password";
	private static final String EMAIL = "email";

	/**
	 * The default filter mapping
	 */
	public static final String PASSWORD_ACTION = "password_auth";

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public PasswordAuthFilter(String defaultFilterProcessesUrl) {
		super(defaultFilterProcessesUrl);
	}

	/**
	 * Handles an authentication request.
	 * @param request HTTP request
	 * @param response HTTP response
	 * @return an authentication object that contains the principal object if successful.
	 * @throws IOException ex
	 * @throws ServletException ex
	 */
	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		String requestURI = request.getRequestURI();
		Authentication userAuth = null;
		User user = new User();

		if (requestURI.endsWith(PASSWORD_ACTION)) {
			user.setIdentifier(request.getParameter(EMAIL));
			user.setPassword(request.getParameter(PASSWORD));

			if (User.passwordMatches(user) && StringUtils.contains(user.getIdentifier(), "@")) {
				//success!
				user = User.readUserForIdentifier(user);
				userAuth = new UserAuthentication(new AuthenticatedUserDetails(user));
			}
		}

		if (userAuth == null || user == null || user.getIdentifier() == null) {
			throw new BadCredentialsException("Bad credentials.");
		} else if (!user.getActive()) {
			throw new LockedException("Account is locked.");
		}
		return userAuth;
	}


	/**
	 * Authenticates or creates a {@link User} using an email and password.
	 * Access token must be in the format: "email:full_name:password" or "email::password_hash"
	 * @param appid app identifier of the parent app, use null for root app
	 * @param accessToken token in the format "email:full_name:password" or "email::password_hash"
	 * @return {@link UserAuthentication} object or null if something went wrong
	 */
	public UserAuthentication getOrCreateUser(String appid, String accessToken) {
		UserAuthentication userAuth = null;
		if (accessToken != null && accessToken.contains(Config.SEPARATOR)) {
			String[] parts = accessToken.split(Config.SEPARATOR, 3);
			String email = parts[0];
			String name = parts[1];
			String pass = (parts.length > 2) ? parts[2] : "";

			User u = new User();
			u.setIdentifier(email);
			u.setPassword(pass);

			User user = User.readUserForIdentifier(u);
			if (user == null) {
				u.setActive(Config.getConfigBoolean("security.allow_unverified_emails", false));
				u.setName(name);
				u.setIdentifier(email);
				u.setEmail(email);
				u.setPassword(pass);
				if (u.create() != null) {
					// allow temporary first-time login without verifying email address
					userAuth = new UserAuthentication(new AuthenticatedUserDetails(u));
				}
			} else if (User.passwordMatches(u)) {
				userAuth = new UserAuthentication(new AuthenticatedUserDetails(user));
			}
		}
		return userAuth;
	}
}
