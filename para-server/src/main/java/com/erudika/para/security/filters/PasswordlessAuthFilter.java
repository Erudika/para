/*
 * Copyright 2013-2019 Erudika. https://erudika.com
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
package com.erudika.para.security.filters;

import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.core.User;
import com.erudika.para.security.AuthenticatedUserDetails;
import com.erudika.para.security.SecurityUtils;
import com.erudika.para.security.UserAuthentication;
import com.erudika.para.utils.Config;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.text.ParseException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

/**
 * A filter which simply authenticates a users without a password by just verifying a simple JWT. The assumption here
 * is that users are verified and authenticated externally - LDAP, SAML, custom authentication (SSO).
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class PasswordlessAuthFilter extends AbstractAuthenticationProcessingFilter {

	/**
	 * The default filter mapping.
	 */
	public static final String PASSWORDLESS_ACTION = "passwordless_auth";

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public PasswordlessAuthFilter(String defaultFilterProcessesUrl) {
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
		UserAuthentication userAuth = null;
		User user = null;

		if (requestURI.endsWith(PASSWORDLESS_ACTION)) {
			String appid = SecurityUtils.getAppidFromAuthRequest(request);
			String token = request.getParameter("token"); // JWT
			App app = Para.getDAO().read(App.id(appid));
			if (app != null) {
				userAuth = getOrCreateUser(app, token);
				if (userAuth != null) {
					user = (User) userAuth.getPrincipal();
					user.setAppid(app.getAppIdentifier());
				}
			}
		}
		return SecurityUtils.checkIfActive(userAuth, user, true);
	}

	/**
	 * Authenticates or creates a {@link User} using an signed JWT token.
	 * Access token must be a valid JWT signed with "para.app_secret_key".
	 * @param app the app where the user will be created, use null for root app
	 * @param accessToken JWT
	 * @return {@link UserAuthentication} object or null if something went wrong
	 */
	public UserAuthentication getOrCreateUser(App app, String accessToken) {
		UserAuthentication userAuth = null;
		User user = new User();
		String secret = SecurityUtils.getSettingForApp(app, "app_secret_key", "");
		try {
			SignedJWT jwt = SignedJWT.parse(accessToken);
			if (SecurityUtils.isValidJWToken(secret, jwt) && app != null) {
				String email = jwt.getJWTClaimsSet().getStringClaim(Config._EMAIL);
				String name = jwt.getJWTClaimsSet().getStringClaim(Config._NAME);
				String identifier = jwt.getJWTClaimsSet().getStringClaim(Config._IDENTIFIER);;
				String appid = app.getAppIdentifier();

				User u = new User();
				u.setAppid(appid);
				u.setIdentifier(identifier);
				u.setEmail(email);
				// NOTE TO SELF:
				// do not overwrite 'u' here - overwrites the password hash!
				user = User.readUserForIdentifier(u);
				if (user == null) {
					user = new User();
					user.setActive(true);
					user.setAppid(appid);
					user.setName(name);
					user.setIdentifier(identifier);
					user.setEmail(email);
					if (user.create() != null) {
						// allow temporary first-time login without verifying email address
						userAuth = new UserAuthentication(new AuthenticatedUserDetails(user));
					}
				} else {
					userAuth = new UserAuthentication(new AuthenticatedUserDetails(user));
				}
			}
		} catch (ParseException e) {
			logger.warn("Invalid token: " + e.getMessage());
		}
		return SecurityUtils.checkIfActive(userAuth, user, false);
	}
}
