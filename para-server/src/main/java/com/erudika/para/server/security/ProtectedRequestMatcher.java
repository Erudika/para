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

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedList;
import java.util.List;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Protected request matcher - returns true if the request matches any of the configured protected paths (incl wildcards).
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class ProtectedRequestMatcher implements RequestMatcher {

	private static final List<RequestMatcher> REST_MATCHERS = new LinkedList<>();
	private OrRequestMatcher orMatcher = null;

	/**
	 * An instance of this class.
	 */
	public static final ProtectedRequestMatcher INSTANCE = new ProtectedRequestMatcher();

	private ProtectedRequestMatcher() {
		RestRequestMatcher.getCustomProtectedPaths().stream().forEach(path -> {
			path.getPatterns().forEach(pattern
					-> path.getMethods().forEach(method
							-> REST_MATCHERS.add(PathPatternRequestMatcher.withDefaults().matcher(method, pattern))));
		});
		if (!REST_MATCHERS.isEmpty()) {
			this.orMatcher = new OrRequestMatcher(REST_MATCHERS);
		}
	}

	/**
	 * Matches the preconfigured list of protected paths.
	 * @param request a request
	 * @return true if path is /v1/...
	 */
	@Override
	public boolean matches(HttpServletRequest request) {
		// Determine if the request is to a protected resource
		return (orMatcher != null && orMatcher.matches(request));
	}

}
