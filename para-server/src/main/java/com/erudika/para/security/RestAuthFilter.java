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

import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.User;
import com.erudika.para.rest.RestUtils;
import com.erudika.para.rest.Signer;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import static javax.ws.rs.HttpMethod.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

/**
 * Authenticates API access for {@link com.erudika.para.core.App} and {@link com.erudika.para.core.User} objects.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class RestAuthFilter extends GenericFilterBean implements InitializingBean {

	private final Signer signer;

	/**
	 * Default constructor
	 * @param signer a request signer instance for request signature verification
	 */
	public RestAuthFilter(Signer signer) {
		this.signer = signer;
	}

	/**
	 * Authenticates an application.
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

		// users are allowed to GET '/_me' - used on the client-side for checking authentication
		String appid = RestUtils.extractAccessKey(request);
		boolean isApp = !StringUtils.isBlank(appid);
		boolean isGuest = isApp && RestUtils.isAnonymousRequest(request);
		boolean proceed = true;

		if (isGuest && RestRequestMatcher.INSTANCE.matches(request)) {
			proceed = guestAuthRequestHandler(appid, (HttpServletRequest) req, response);
		} else if (!isApp && RestRequestMatcher.INSTANCE.matches(request)) {
			proceed = userAuthRequestHandler((HttpServletRequest) req, response);
		} else if (isApp && RestRequestMatcher.INSTANCE_STRICT.matches(request)) {
			proceed = appAuthRequestHandler(appid, request, response);
		}

		if (proceed) {
			chain.doFilter(request, res);
		}
	}

	private boolean guestAuthRequestHandler(String appid, HttpServletRequest request, HttpServletResponse response) {
		String reqUri = request.getRequestURI();
		String method = request.getMethod();
		if (!StringUtils.isBlank(appid)) {
			App parentApp = Para.getDAO().read(App.id(appid));
			if (!hasPermission(parentApp, null, request)) {
				RestUtils.returnStatusResponse(response, HttpServletResponse.SC_FORBIDDEN,
						Utils.formatMessage("You don't have permission to access this resource. "
								+ "[user: {0}, resource: {1} {2}]", "[GUEST]", method, reqUri));
				return false;
			}
		} else {
			RestUtils.returnStatusResponse(response, HttpServletResponse.SC_UNAUTHORIZED, Utils.
					formatMessage("You don't have permission to access this resource. [{0} {1}]", method, reqUri));
		}
		return true;
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

		if (user == null) {
			// special case: app authenticated with JWT token (admin token)
			Object[] fail = doAppChecks(parentApp, request);
			if (fail != null) {
				RestUtils.returnStatusResponse(response, (Integer) fail[0], (String) fail[1]);
				return false;
			}
		} else if (user.getActive()) {
			if (parentApp == null) {
				parentApp = Para.getDAO().read(App.id(user.getAppid()));
			}
			if (!hasPermission(parentApp, user, request)) {
				RestUtils.returnStatusResponse(response, HttpServletResponse.SC_FORBIDDEN,
						Utils.formatMessage("You don't have permission to access this resource. "
								+ "[user: {0}, resource: {1} {2}]", user.getId(), method, reqUri));
				return false;
			}
		} else {
			RestUtils.returnStatusResponse(response, HttpServletResponse.SC_UNAUTHORIZED, Utils.
					formatMessage("You don't have permission to access this resource. [{0} {1}]", method, reqUri));
			return false;
		}
		return true;
	}

	private boolean appAuthRequestHandler(String appid, BufferedRequestWrapper request, HttpServletResponse response) {
		String date = RestUtils.extractDate(request);
		Date d = Signer.parseAWSDate(date);
		boolean requestExpired = (d != null) && (System.currentTimeMillis()
				> (d.getTime() + (Config.REQUEST_EXPIRES_AFTER_SEC * 1000)));

		if (StringUtils.isBlank(date) || requestExpired) {
			RestUtils.returnStatusResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Request has expired.");
			return false;
		}

		App app = Para.getDAO().read(App.id(appid));
		Object[] fail = doAppChecks(app, request);

		if (fail == null) {
			if (signer.isValidSignature(request, app.getSecret())) {
				SecurityContextHolder.getContext().setAuthentication(new AppAuthentication(app));
				return true;
			}
			RestUtils.returnStatusResponse(response, HttpServletResponse.SC_FORBIDDEN, "Request signature is invalid.");
		} else {
			RestUtils.returnStatusResponse(response, (Integer) fail[0], (String) fail[1]);
		}
		return false;
	}

	private boolean hasPermission(App parentApp, User user, HttpServletRequest request) {
		if (parentApp == null) {
			return false;
		}
		String resourcePath = RestUtils.extractResourcePath(request);
		String method = request.getMethod();
		String uid = (user == null) ? "" : user.getId();
		boolean isAllowed = parentApp.isAllowedTo(uid, resourcePath, method);
		if (parentApp.isDeniedExplicitly(uid, resourcePath, method)) {
			return false;
		}
		return (isAllowed || canModify(user, resourcePath, method));
	}

	private boolean canModify(User user, String resourcePath, String method) {
		if (user == null || resourcePath == null || method == null) {
			return false;
		}
		ParaObject po = RestUtils.readResourcePath(user.getAppid(), resourcePath);
		if (po == null) {
			return false;
		}
		if (user.getId().equals(po.getId())) {
			String path = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
			String prefix = (po.getObjectURI().startsWith("/") ? po.getObjectURI() : "/" + po.getObjectURI()) + "/";
			if (StringUtils.startsWithIgnoreCase(path, prefix) && !path.equals(prefix)) {
				// implicit permissions: object can access children and its own subresources
				return true;
			}
			// a user object can only read and update itself
			return (method.equals(GET) || method.equals("PATCH") || method.equals(PUT));
		}
		return user.canModify(po);
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

	private class BufferedRequestWrapper extends HttpServletRequestWrapper {

		ByteArrayInputStream bais;
		ByteArrayOutputStream baos;
		BufferedServletInputStream bsis;
		byte[] buffer;

		public BufferedRequestWrapper(HttpServletRequest req) throws IOException {
			super(req);
			InputStream is = req.getInputStream();
			baos = new ByteArrayOutputStream();
			byte buf[] = new byte[1024];
			int length;
			while ((length = is.read(buf)) > 0) {
				baos.write(buf, 0, length);
			}
			buffer = baos.toByteArray();
		}

		public ServletInputStream getInputStream() {
			try {
				bais = new ByteArrayInputStream(buffer);
				bsis = new BufferedServletInputStream(bais);
			} catch (Exception ex) {
				logger.error(ex);
			}
			return bsis;
		}

	}

	private class BufferedServletInputStream extends ServletInputStream {
		ByteArrayInputStream bais;

		public BufferedServletInputStream(ByteArrayInputStream bais) {
			this.bais = bais;
		}

		@Override
		public int available() {
			return bais.available();
		}

		@Override
		public int read() {
			return bais.read();
		}

		@Override
		public int read(byte[] buf, int off, int len) {
			return bais.read(buf, off, len);
		}

		@Override
		public boolean isFinished() {
			return bais.available() <= 0;
		}

		@Override
		public boolean isReady() {
			return !isFinished();
		}

		@Override
		public void setReadListener(ReadListener rl) {
		}
	}

	private boolean isWriteRequest(HttpServletRequest req) {
		return req != null &&
				(POST.equals(req.getMethod()) ||
				PUT.equals(req.getMethod()) ||
				DELETE.equals(req.getMethod()) ||
				"PATCH".equals(req.getMethod()));
	}

}
