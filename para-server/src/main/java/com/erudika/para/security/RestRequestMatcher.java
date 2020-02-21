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
package com.erudika.para.security;

import javax.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Rest request matcher - returns true if the request is RESTful.
 * True if the URI starts with /vX.Y.Z (.Y.Z are optional)
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class RestRequestMatcher implements RequestMatcher {
	/**
	 * An instance of this class. Does not match /v1/_me.
	 */
	public static final RequestMatcher INSTANCE = new RestRequestMatcher();
	/**
	 * An instance of this class. Matches /v1/_me.
	 */
	public static final RequestMatcher INSTANCE_STRICT = new RestRequestMatcher(true);
	private static final RegexRequestMatcher REGEX = new RegexRequestMatcher("^/v\\d[\\.\\d]*/(?!(_me)).*", null, true);
	private static final RegexRequestMatcher REGEX_STRICT = new RegexRequestMatcher("^/v\\d[\\.\\d]*/.*", null, true);

	private final boolean strict;

	private RestRequestMatcher() {
		this.strict = false;
	}

	private RestRequestMatcher(boolean strict) {
		this.strict = strict;
	}

	@Override
	public boolean matches(HttpServletRequest request) {
		// Determine if the request is RESTful.
		return strict ? REGEX_STRICT.matches(request) : REGEX.matches(request);
	}

}
