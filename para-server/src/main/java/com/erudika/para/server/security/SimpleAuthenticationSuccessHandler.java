/*
 * Copyright 2013-2026 Erudika. https://erudika.com
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
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.Utils;
import com.erudika.para.server.rest.RestUtils;
import com.erudika.para.server.utils.HttpUtils;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Simple handler for successful authentication requests.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SimpleAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

	/**
	 * Creates the handler that customizes successful login redirects.
	 */
	public SimpleAuthenticationSuccessHandler() {
		// default constructor
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {

		User u = SecurityUtils.getAuthenticatedUser(authentication);

		String appid = StringUtils.defaultIfBlank(SecurityUtils.getAppidFromAuthRequest(request), u.getAppid());
		if (!StringUtils.isBlank(appid)) {
			// try to reload custom redirect URI from app
			App app = Para.getDAO().read(App.id(appid));
			if (app != null) {
				String customURI = (String) app.getSetting("signin_success");
				Set<String> hostUrlAliases = SecurityUtils.getHostUrlAliasesForReturn(app);
				String hostUrlParam = SecurityUtils.getHostUrlFromQueryStringOrStateParam(hostUrlAliases, request);
				if (app.isRootApp() && StringUtils.isBlank(customURI)) {
					customURI = Para.getConfig().signinSuccessPath();
				}
				if (!StringUtils.isBlank(hostUrlParam)) {
					if (hostUrlAliases.contains(hostUrlParam) || Strings.CS.startsWith(customURI, hostUrlParam)) {
						UriComponents hostUrl = UriComponentsBuilder.fromUriString(hostUrlParam).build();
						customURI = UriComponentsBuilder.fromUriString(customURI).host(hostUrl.getHost()).toUriString();
					} else {
						UriComponents customHost = UriComponentsBuilder.fromUriString(customURI).build();
						customURI = customHost.getScheme() + "://" + customHost.getHost();
					}
				}
				if (Strings.CS.contains(customURI, "jwt=?")) {
					SignedJWT newJWT = SecurityUtils.generateJWToken(u, app);
					customURI = customURI.replace("jwt=?", "jwt=" + newJWT.serialize());
				}
				if (Strings.CS.contains(customURI, "jwt=id")) {
					SignedJWT newJWT = SecurityUtils.generateIdToken(u, app);
					customURI = customURI.replace("jwt=id", "jwt=" + newJWT.serialize());
				}
				if (!StringUtils.isBlank(customURI)) {
					if (!response.isCommitted()) {
						redirectStrategy.sendRedirect(request, response, customURI);
					}
					return;
				}
			}
		}

		if (isRestRequest(request)) {
			RestUtils.returnStatusResponse(response, HttpServletResponse.SC_NO_CONTENT, "Authentication success.");
		} else {
			super.onAuthenticationSuccess(request, response, authentication);
		}
	}

	@Override
	protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response) {
		String cookie = HttpUtils.getStateParam(Para.getConfig().returnToCookieName(), request);
		if (cookie != null) {
			cookie = Utils.base64dec(cookie);
			HttpUtils.removeStateParam(Para.getConfig().returnToCookieName(), request, response);
			return cookie;
		} else {
			return super.determineTargetUrl(request, response);
		}
	}

	/**
	 * Checks if it is a rest request.
	 * @param request request
	 * @return true if rest or ajax
	 */
	protected boolean isRestRequest(HttpServletRequest request) {
		return RestRequestMatcher.INSTANCE.matches(request) || AjaxRequestMatcher.INSTANCE.matches(request);
	}
}
