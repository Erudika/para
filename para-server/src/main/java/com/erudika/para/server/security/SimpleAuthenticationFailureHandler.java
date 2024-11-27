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
import com.erudika.para.core.utils.Para;
import com.erudika.para.server.rest.RestUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Simple handler for successful authentication requests.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SimpleAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

	private RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException, ServletException {

		String appid = SecurityUtils.getAppidFromAuthRequest(request);
		if (!StringUtils.isBlank(appid)) {
			// try to reload custom redirect URI from app
			App app = Para.getDAO().read(App.id(appid));
			if (app != null) {
				String customURI = (String) app.getSetting("signin_failure");
				Set<String> hostUrlAliases = SecurityUtils.getHostUrlAliasesForReturn(app);
				String hostUrlParam = SecurityUtils.getHostUrlFromQueryStringOrStateParam(hostUrlAliases, request);
				if (app.isRootApp() && StringUtils.isBlank(customURI)) {
					customURI = Para.getConfig().signinFailurePath();
				}
				if (!StringUtils.isBlank(hostUrlParam)) {
					if (hostUrlAliases.contains(hostUrlParam) || StringUtils.startsWith(customURI, hostUrlParam)) {
						UriComponents hostUrl = UriComponentsBuilder.fromUriString(hostUrlParam).build();
						customURI = UriComponentsBuilder.fromUriString(customURI).host(hostUrl.getHost()).toUriString();
					} else {
						UriComponents customHost = UriComponentsBuilder.fromUriString(customURI).build();
						customURI = customHost.getScheme() + "://" + customHost.getHost();
					}
				}
				if (StringUtils.contains(customURI, "cause=?")) {
					customURI = customURI.replace("cause=?", "cause=" + exception.getMessage());
				}
				if (!StringUtils.isBlank(customURI)) {
					redirectStrategy.sendRedirect(request, response, customURI);
					return;
				}
			}
		}

		if (isRestRequest(request)) {
			RestUtils.returnStatusResponse(response, HttpServletResponse.SC_UNAUTHORIZED, exception.getMessage());
		} else {
			super.onAuthenticationFailure(request, response, exception);
		}
	}

	/**
	 * Checks if it is a rest request.
	 * @param request the request
	 * @return true if rest or ajax
	 */
	protected boolean isRestRequest(HttpServletRequest request) {
		return RestRequestMatcher.INSTANCE.matches(request) || AjaxRequestMatcher.INSTANCE.matches(request);
	}
}
