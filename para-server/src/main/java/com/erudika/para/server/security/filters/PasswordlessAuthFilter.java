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
package com.erudika.para.server.security.filters;

import com.erudika.para.core.App;
import com.erudika.para.core.User;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Para;
import com.erudika.para.server.security.AuthenticatedUserDetails;
import com.erudika.para.server.security.SecurityUtils;
import com.erudika.para.server.security.UserAuthentication;
import com.erudika.para.server.utils.HttpUtils;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

/**
 * A filter which simply authenticates a users without a password by just verifying a simple JWT. The assumption here
 * is that users are verified and authenticated externally - LDAP, SAML, custom authentication (SSO).
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class PasswordlessAuthFilter extends AbstractAuthenticationProcessingFilter {

	private static final Logger logger = LoggerFactory.getLogger(PasswordlessAuthFilter.class);

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
		String requestURI = request.getServletPath();
		UserAuthentication userAuth = null;
		boolean redirect = !"false".equals(request.getParameter("redirect"));
		User user = null;
		App app = null;
		UserAuthentication auth = null;
		// dont' use signin_success to prevent infinite loop of redirects!
		String returnToSuccess = Para.getConfig().getSettingForApp(app, "returnto",
				Para.getConfig().returnToPath());
		String returnToFailure = Para.getConfig().getSettingForApp(app, "signin_failure",
				Para.getConfig().signinFailurePath());

		if (requestURI.endsWith(PASSWORDLESS_ACTION)) {
			String token = StringUtils.defaultIfBlank(request.getParameter("token"), request.getParameter("jwt")); // JWT
			String appid = SecurityUtils.getAppidFromAuthRequest(request, token);
			app = Para.getDAO().read(App.id(appid));
			if (app != null) {
				userAuth = getOrCreateUser(app, token);
				if (userAuth != null) {
					user = ((AuthenticatedUserDetails) userAuth.getPrincipal()).getUser();
					user.setAppid(app.getAppIdentifier());
				}
			} else {
				if (redirect) {
					response.sendRedirect(returnToFailure, HttpStatus.FORBIDDEN.value());
				} else {
					response.sendError(HttpStatus.FORBIDDEN.value());
					response.setStatus(HttpStatus.FORBIDDEN.value());
				}
				return null;
			}
			auth = SecurityUtils.checkIfActive(userAuth, user, redirect);
			if (!redirect) {
				if (auth == null) {
					response.sendError(HttpStatus.FORBIDDEN.value());
					response.setStatus(HttpStatus.FORBIDDEN.value());
				} else {
					response.setContentType(MediaType.TEXT_PLAIN_VALUE);
					response.setStatus(HttpStatus.OK.value());
					response.getWriter().print(SecurityUtils.generateJWToken(user, app).serialize());
				}
				return null;
			} else {
				if (!URI.create(returnToSuccess).isAbsolute() || !URI.create(returnToFailure).isAbsolute()) {
					response.sendError(HttpStatus.BAD_REQUEST.value());
					response.setStatus(HttpStatus.BAD_REQUEST.value());
					String errorMsg = "Passwordless authentication failed for app '" + appid +
							"', reason: redirect URL must be absolute.";
					response.getWriter().print(errorMsg);
					logger.warn(errorMsg);
					return null;
				}
				if (auth == null) {
					response.sendRedirect(returnToFailure, HttpStatus.FORBIDDEN.value());
				} else {
					boolean httpOnly = "true".equals(StringUtils.defaultIfBlank(request.getParameter("httpOnlyCookie"), "true"));
					String sameSite = StringUtils.defaultIfBlank(request.getParameter("sameSiteCookie"), "Strict");
					String authCookieName = Para.getConfig().getSettingForApp(app, "auth_cookie",
							StringUtils.join(App.identifier(appid), "-auth"));
					String authCookieValue = SecurityUtils.generateJWToken(user, app).serialize();
					int maxAge = NumberUtils.toInt(Para.getConfig().getSettingForApp(app, "session_timeout", null),
							app.getTokenValiditySec().intValue());
					HttpUtils.setAuthCookie(authCookieName, authCookieValue, httpOnly, maxAge, sameSite, request, response);
					response.sendRedirect(returnToSuccess, HttpStatus.FOUND.value());
				}
			}
		}
		return auth;
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
		String secret = Para.getConfig().getSettingForApp(app, "app_secret_key", app.getSecret());
		try {
			SignedJWT jwt = SignedJWT.parse(accessToken);
			String email = jwt.getJWTClaimsSet().getStringClaim(Config._EMAIL);
			String name = jwt.getJWTClaimsSet().getStringClaim(Config._NAME);
			String identifier = jwt.getJWTClaimsSet().getStringClaim(Config._IDENTIFIER);
			String groups = jwt.getJWTClaimsSet().getStringClaim(Config._GROUPS);
			String picture = jwt.getJWTClaimsSet().getStringClaim("picture");
			String appid = app.getAppIdentifier();

			User u = new User();
			u.setAppid(appid);
			u.setIdentifier(identifier);
			u.setEmail(email);
			user = User.readUserForIdentifier(u);
			String userSecret = user != null ? user.getTokenSecret() : "";

			if (SecurityUtils.isValidJWToken(secret, jwt) || SecurityUtils.isValidJWToken(app.getSecret() + userSecret, jwt)) {
				// NOTE TO SELF:
				// do not overwrite 'u' here - overwrites the password hash!
				if (user == null) {
					user = new User();
					user.setActive(true);
					user.setAppid(appid);
					user.setName(name);
					user.setGroups(StringUtils.isBlank(groups) ? User.Groups.USERS.toString() : groups);
					user.setIdentifier(identifier);
					user.setEmail(email);
					user.setPicture(picture);
					// allow temporary first-time login without verifying email address
					user.create();
				} else {
					if (updateUserInfo(user, picture, email, name, accessToken, groups)) {
						user.update();
					}
				}
				userAuth = new UserAuthentication(new AuthenticatedUserDetails(user));
			} else {
				logger.info("Authentication request failed because the provided JWT token is invalid. appid: '" +
						app.getAppIdentifier() + "', user found: " + (user == null ? "none" : user.getId()) + ", "
								+ "user queried: " + identifier);
			}
		} catch (ParseException e) {
			logger.warn("Invalid token: " + e.getMessage());
		}
		return SecurityUtils.checkIfActive(userAuth, user, false);
	}

	private boolean updateUserInfo(User user, String picture, String email, String name, String accessToken, String groups) {
		boolean update = false;
		if (!Strings.CS.equals(user.getPicture(), picture)) {
			user.setPicture(picture);
			update = true;
		}
		if (!StringUtils.isBlank(email) && !Strings.CS.equals(user.getEmail(), email)) {
			user.setEmail(email);
			update = true;
		}
		if (!StringUtils.isBlank(name) && !Strings.CS.equals(user.getName(), name)) {
			user.setName(name);
			update = true;
		}
		if (!StringUtils.isBlank(groups) && !Strings.CS.equals(user.getGroups(), groups)) {
			user.setGroups(groups);
			CoreUtils.getInstance().overwrite(user.getAppid(), user);
			update = false;
		}
		return update;
	}
}
