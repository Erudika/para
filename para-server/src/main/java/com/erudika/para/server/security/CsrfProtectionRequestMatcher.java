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

import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * CSRF protection matcher - matches POST, PUT, PATCH, DELETE requests that are not in the ignored list and not
 * API or authentication requests. These requests will be rejected if they don't contain a valid CSRF token.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class CsrfProtectionRequestMatcher implements RequestMatcher {
	/**
	 * An instance of this class.
	 */
	public static final RequestMatcher INSTANCE = new CsrfProtectionRequestMatcher();
	private final Pattern allowedMethods = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");
	private final RegexRequestMatcher authEndpoints = new RegexRequestMatcher("^/\\w+_auth", null, true);
	private final RegexRequestMatcher passAuthEndpoint = new RegexRequestMatcher("^/password_auth", null, true);
	private final RegexRequestMatcher samlEndpoint = new RegexRequestMatcher("^/saml_auth.*", null, true);
	private final RegexRequestMatcher samlMetaEndpoint = new RegexRequestMatcher("^/saml_metadata.*", null, true);

	private CsrfProtectionRequestMatcher() {
	}

	@Override
	public boolean matches(HttpServletRequest request) {
		boolean matches = !RestRequestMatcher.INSTANCE.matches(request)
				&& !IgnoredRequestMatcher.INSTANCE.matches(request)
				&& !samlMetaEndpoint.matches(request)
				&& !samlEndpoint.matches(request)
				&& !authEndpoints.matches(request)
				&& !allowedMethods.matcher(request.getMethod()).matches();
		return matches || passAuthEndpoint.matches(request);
	}

}
