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

import com.erudika.para.core.App;
import com.erudika.para.core.User;
import com.erudika.para.core.rest.Signer;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.Utils;
import com.erudika.para.server.rest.RestUtils;
import com.erudika.para.server.utils.BufferedRequestWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static jakarta.ws.rs.HttpMethod.DELETE;
import static jakarta.ws.rs.HttpMethod.GET;
import static jakarta.ws.rs.HttpMethod.PATCH;
import static jakarta.ws.rs.HttpMethod.POST;
import static jakarta.ws.rs.HttpMethod.PUT;
import java.io.IOException;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

/**
 * Authenticates API access for {@link com.erudika.para.core.App} and {@link com.erudika.para.core.User} objects.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class RestAuthFilter extends GenericFilterBean implements InitializingBean {

	private static final Logger LOG = LoggerFactory.getLogger(SecurityUtils.class);
	private final boolean apiSecurityEnabled;

	/**
	 * Default constructor.
	 */
	public RestAuthFilter() {
		this.apiSecurityEnabled = true;
	}

	/**
	 * @param apiSecurityEnabled true if API security is enabled
	 */
	public RestAuthFilter(boolean apiSecurityEnabled) {
		this.apiSecurityEnabled = apiSecurityEnabled;
	}

	/**
	 * Authenticates an application or user or guest.
	 * @param req a request
	 * @param res a response
	 * @param chain filter chain
	 * @throws IOException ex
	 * @throws ServletException ex
	 */
	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
			throws IOException, ServletException {
		// We must wrap the request in order to read the InputStream twice:
		// - first read - used to calculate the signature
		// - second read - used as request payload
		BufferedRequestWrapper request = new BufferedRequestWrapper((HttpServletRequest) req);
		HttpServletResponse response = (HttpServletResponse) res;
		boolean proceed = true;
		try {
			// users are allowed to GET '/_me' - used on the client-side for checking authentication
			String appid = RestUtils.extractAccessKey(request);
			boolean isApp = !StringUtils.isBlank(appid);
			boolean isGuest = RestUtils.isAnonymousRequest(request);

			if (isGuest && RestRequestMatcher.INSTANCE.matches(request)) {
				proceed = guestAuthRequestHandler(appid, (HttpServletRequest) req, response);
			} else if (!isApp && RestRequestMatcher.INSTANCE.matches(request)) {
				proceed = userAuthRequestHandler((HttpServletRequest) req, response);
			} else if (isApp && RestRequestMatcher.INSTANCE_STRICT.matches(request)) {
				proceed = appAuthRequestHandler(appid, request, response);
			}
		} catch (Exception e) {
			LOG.error("Failed to authorize request.", e);
		}

		if (proceed || !apiSecurityEnabled) {
			chain.doFilter(request, res);
		}
	}

	private boolean guestAuthRequestHandler(String appid, HttpServletRequest request, HttpServletResponse response) {
		String reqUri = request.getRequestURI();
		String method = request.getMethod();
		if (!StringUtils.isBlank(appid)) {
			App parentApp = Para.getDAO().read(App.id(appid));
			if (hasPermission(parentApp, null, request)) {
				SecurityContextHolder.getContext().setAuthentication(new AppAuthentication(parentApp));
				return true;
			} else {
				RestUtils.returnStatusResponse(response, HttpServletResponse.SC_FORBIDDEN,
						Utils.formatMessage("You don't have permission to access this resource. "
								+ "[user: {0}, resource: {1} {2}]", "[GUEST]", method, reqUri));
				return false;
			}
		}
		RestUtils.returnStatusResponse(response, HttpServletResponse.SC_UNAUTHORIZED, Utils.
				formatMessage("You don't have permission to access this resource. [{0} {1}]", method, reqUri));
		return false;
	}

	private boolean userAuthRequestHandler(HttpServletRequest request, HttpServletResponse response) {
		Authentication userAuth = SecurityContextHolder.getContext().getAuthentication();
		User user = SecurityUtils.getAuthenticatedUser(userAuth);
		String reqUri = request.getRequestURI();
		String method = request.getMethod();
		App parentApp;
		if (userAuth instanceof JWTAuthentication) {
			parentApp = ((JWTAuthentication) userAuth).getApp();
		} else {
			parentApp = SecurityUtils.getAuthenticatedApp();
		}

		if (userAuth != null) {
			if (user == null) {
				// special case: app authenticated with JWT token (admin token)
				Object[] fail = doAppChecks(parentApp, request);
				if (fail == null) {
					return true;
				} else {
					RestUtils.returnStatusResponse(response, (Integer) fail[0], (String) fail[1]);
					return false;
				}
			} else if (user.getActive()) {
				if (parentApp == null) {
					parentApp = Para.getDAO().read(App.id(user.getAppid()));
				}
				if (hasPermission(parentApp, user, request)) {
					return true;
				} else {
					RestUtils.returnStatusResponse(response, HttpServletResponse.SC_FORBIDDEN,
							Utils.formatMessage("You don't have permission to access this resource. "
									+ "[user: {0}, resource: {1} {2}]", user.getId(), method, reqUri));
					return false;
				}
			}
		}
		RestUtils.returnStatusResponse(response, HttpServletResponse.SC_UNAUTHORIZED, Utils.
				formatMessage("You don't have permission to access this resource. [{0} {1}]", method, reqUri));
		return false;
	}

	private boolean appAuthRequestHandler(String appid, HttpServletRequest request, HttpServletResponse response) {
		String date = RestUtils.extractDate(request);
		Date d = Signer.parseAWSDate(date);
		boolean requestExpired = (d != null) && (System.currentTimeMillis()
				> (d.getTime() + (Para.getConfig().requestExpiresAfterSec() * 1000)));

		if (apiSecurityEnabled && (StringUtils.isBlank(date) || requestExpired)) {
			RestUtils.returnStatusResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Request has expired.");
			return false;
		}

		App app = Para.getDAO().read(App.id(appid));
		Object[] failures = doAppChecks(app, request);

		if (failures == null) {
			if (!apiSecurityEnabled || SecurityUtils.isValidSignature(request, app.getSecret())) {
				SecurityContextHolder.getContext().setAuthentication(new AppAuthentication(app));
				return true;
			}
			RestUtils.returnStatusResponse(response, HttpServletResponse.SC_FORBIDDEN,
					Utils.formatMessage("Invalid signature for request {0} {1} coming from app {2}",
							request.getMethod(), request.getRequestURI(), app.getAppIdentifier()));
			LOG.warn("Invalid signature for request {} {} coming from app '{}' ({})", request.getMethod(),
					request.getRequestURI() + (StringUtils.isBlank(request.getQueryString()) ? "" :
							"?" + request.getQueryString()), app.getAppIdentifier(), request.getHeader("User-Agent"));
		} else {
			RestUtils.returnStatusResponse(response, (Integer) failures[0], (String) failures[1]);
		}
		return false;
	}

	private boolean hasPermission(App parentApp, User user, HttpServletRequest request) {
		if (parentApp == null) {
			return false;
		}
		// App admin should have unlimited access
		if (user != null && user.isAdmin()) {
			return true;
		}
		String resourcePath = RestUtils.extractResourcePath(request);
		if (resourcePath.matches("^_permissions/.+") && request.getMethod().equals(GET)) {
			return true; // allow permission checks, i.e. pc.isAllowed(), to go through
		}
		// we allow empty user ids - this means that the request is unauthenticated
		String subjectid = (user == null) ? "" : user.getId();
		return parentApp.isAllowedTo(subjectid, resourcePath, request.getMethod());
	}

	private Object[] doAppChecks(App app, HttpServletRequest request) {
		if (app == null) {
			return new Object[]{HttpServletResponse.SC_NOT_FOUND, "App not found."};
		}
		if (!app.getActive()) {
			return new Object[]{HttpServletResponse.SC_FORBIDDEN,
				Utils.formatMessage("App not active. [{0}]", app.getId())};
		}
		if (app.getReadOnly() && isWriteRequest(request)) {
			return new Object[]{HttpServletResponse.SC_FORBIDDEN,
				Utils.formatMessage("App is in read-only mode. [{0}]", app.getId())};
		}
		return null;
	}

	private boolean isWriteRequest(HttpServletRequest req) {
		return req != null &&
				(POST.equals(req.getMethod()) ||
				PUT.equals(req.getMethod()) ||
				DELETE.equals(req.getMethod()) ||
				PATCH.equals(req.getMethod()));
	}

}
