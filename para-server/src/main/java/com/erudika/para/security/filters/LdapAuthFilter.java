/*
 * Copyright 2013-2017 Erudika. https://erudika.com
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

import com.eaio.uuid.UUID;
import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.core.User;
import com.erudika.para.security.AuthenticatedUserDetails;
import com.erudika.para.security.LDAPAuthentication;
import com.erudika.para.security.SecurityUtils;
import com.erudika.para.security.UserAuthentication;
import com.erudika.para.utils.Config;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.ldap.userdetails.InetOrgPerson;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

/**
 * A filter that handles authentication requests to an LDAP server.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class LdapAuthFilter extends AbstractAuthenticationProcessingFilter {

	private static final Logger LOG = LoggerFactory.getLogger(LdapAuthFilter.class);

	private static final String PASSWORD = Config.getConfigParam("security.ldap.password_param", "password");
	private static final String USERNAME = Config.getConfigParam("security.ldap.username_param", "username");

	/**
	 * The default filter mapping.
	 */
	public static final String LDAP_ACTION = "ldap_auth";

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public LdapAuthFilter(final String defaultFilterProcessesUrl) {
		super(defaultFilterProcessesUrl);
	}

	/**
	 * Handles an authentication request.
	 * @param request HTTP request
	 * @param response HTTP response
	 * @return an authentication object that contains the principal object if successful.
	 * @throws IOException ex
	 */
	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		final String requestURI = request.getRequestURI();
		UserAuthentication userAuth = null;
		String username = request.getParameter(USERNAME);
		String password = request.getParameter(PASSWORD);
		String appid = request.getParameter(Config._APPID);

		if (requestURI.endsWith(LDAP_ACTION) && !StringUtils.isBlank(username) && !StringUtils.isBlank(password)) {
			try	{
				App app = Para.getDAO().read(App.id(appid == null ? Config.getRootAppIdentifier() : appid));
				Authentication auth = new LDAPAuthentication(username, password).withApp(app);
				// set authentication in context to avoid warning message from SpringSecurityAuthenticationSource
				SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key",
						"anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
				Authentication ldapAuth = getAuthenticationManager().authenticate(auth);
				if (ldapAuth != null) {
					//success!
					userAuth = getOrCreateUser(app, ldapAuth);
				}
			} catch (Exception ex) {
				LOG.info("Failed to authenticate '{}' with LDAP server: {}", username, ex.getMessage());
			}
		}

		return SecurityUtils.checkIfActive(userAuth, SecurityUtils.getAuthenticatedUser(userAuth), true);
	}

	private UserAuthentication getOrCreateUser(App app, Authentication ldapAuth) {
		if (ldapAuth == null) {
			return null;
		}
		UserAuthentication userAuth = null;
		User user = new User();
		InetOrgPerson profile = (InetOrgPerson) ldapAuth.getPrincipal();

		if (profile != null && profile.isEnabled() && profile.isAccountNonLocked() && profile.isAccountNonExpired()) {
			String ldapAccountId = profile.getUsername();
			String email = profile.getMail();
			String name = StringUtils.join(profile.getCn(), ", ");

			if (StringUtils.isBlank(email)) {
				LOG.warn("Failed to create LDAP user '{}' with blank email.", ldapAccountId);
				return null;
			}

			user.setAppid(getAppid(app));
			user.setIdentifier(Config.LDAP_PREFIX.concat(ldapAccountId));
			user.setEmail(email);
			user = User.readUserForIdentifier(user);
			if (user == null) {
				//user is new
				user = new User();
				user.setActive(true);
				user.setAppid(getAppid(app));
				user.setEmail(StringUtils.isBlank(email) ? ldapAccountId + "@ldap.com" : email);
				user.setName(StringUtils.isBlank(name) ? "No Name" : name);
				user.setPassword(new UUID().toString());
				user.setIdentifier(Config.LDAP_PREFIX.concat(ldapAccountId));
				String id = user.create();
				if (id == null) {
					throw new AuthenticationServiceException("Authentication failed: cannot create new user.");
				}
			} else {
				boolean update = false;
				if (!StringUtils.isBlank(email) && !StringUtils.equals(user.getEmail(), email)) {
					user.setEmail(email);
					update = true;
				}
				if (update) {
					user.update();
				}
			}
			userAuth = new UserAuthentication(new AuthenticatedUserDetails(user));
		}
		return userAuth;
	}

	/**
	 * Calls an external API to get the user profile using a given access token.
	 * @param app the app where the user will be created, use null for root app
	 * @param accessToken access token - in the case of LDAP this is should be "uid:password"
	 * @return {@link UserAuthentication} object or null if something went wrong
	 * @throws IOException ex
	 */
	public UserAuthentication getOrCreateUser(App app, String accessToken) throws IOException {
		UserAuthentication userAuth = null;
		if (accessToken != null && accessToken.contains(Config.SEPARATOR)) {
			String[] parts = accessToken.split(Config.SEPARATOR, 2);
			String username = parts[0];
			String password = parts[1];
			try {
				Authentication auth = new LDAPAuthentication(username, password).withApp(app);

				// set authentication in context to avoid warning message from SpringSecurityAuthenticationSource
				SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key",
						"anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
				Authentication ldapAuth = getAuthenticationManager().authenticate(auth);
				if (ldapAuth != null) {
					//success!
					userAuth = getOrCreateUser(app, ldapAuth);
				}
			} catch (Exception ex) {
				LOG.info("Failed to authenticate '{}' with LDAP server: {}", username, ex.getMessage());
			}
		}
		return SecurityUtils.checkIfActive(userAuth, SecurityUtils.getAuthenticatedUser(userAuth), false);
	}

	private String getAppid(App app) {
		return (app == null) ? null : app.getAppIdentifier();
	}
}
