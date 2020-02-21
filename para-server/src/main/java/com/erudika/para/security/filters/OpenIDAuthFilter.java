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
package com.erudika.para.security.filters;

import com.erudika.para.core.User;
import com.erudika.para.security.AuthenticatedUserDetails;
import com.erudika.para.security.SecurityUtils;
import com.erudika.para.security.UserAuthentication;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.openid.OpenIDAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * A filter that handles authentication requests to OpenID providers.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class OpenIDAuthFilter extends OpenIDAuthenticationFilter {

	/**
	 * The default filter mapping.
	 */
	public static final String OPENID_ACTION = "openid_auth";

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public OpenIDAuthFilter(final String defaultFilterProcessesUrl) {
		setRequiresAuthenticationRequestMatcher(new RequestMatcher() {
			public boolean matches(HttpServletRequest request) {
				String uri = request.getRequestURI();
				boolean matches;
				if ("".equals(request.getContextPath())) {
					matches = uri.endsWith(defaultFilterProcessesUrl);
				} else {
					matches = uri.endsWith(request.getContextPath() + defaultFilterProcessesUrl);
				}
				return matches;
			}
		});
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
		User user = null;

		if (requestURI.endsWith(OPENID_ACTION)) {
			Authentication oidAuth = super.attemptAuthentication(request, response);

			if (oidAuth == null) {
				// hang on... redirecting to openid provider
				return null;
			} else {
				//success!
				AuthenticatedUserDetails userDetails = (AuthenticatedUserDetails) oidAuth.getPrincipal();
				userAuth = new UserAuthentication(userDetails);
				user = userDetails.getUser();
			}
		}

		return SecurityUtils.checkIfActive(userAuth, user, true);
	}
}
