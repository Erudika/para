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

import com.erudika.para.core.utils.Para;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigValue;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Ignored request matcher - returns true if the request should be ignored (CSRF, security, etc).
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class IgnoredRequestMatcher implements RequestMatcher {
	/**
	 * An instance of this class.
	 */
	public static final RequestMatcher INSTANCE = new IgnoredRequestMatcher();
	private final OrRequestMatcher orMatcher;

	private IgnoredRequestMatcher() {
		ConfigList c = Para.getConfig().ignoredPaths();
		List<RequestMatcher> list = new ArrayList<>();
		if (c != null) {
			for (ConfigValue configValue : c) {
				list.add(new AntPathRequestMatcher((String) configValue.unwrapped()));
			}
		} else {
			list.add(new AntPathRequestMatcher("/"));
		}
		orMatcher = new OrRequestMatcher(list);
	}

	@Override
	public boolean matches(HttpServletRequest request) {
		// Determine if the request should be ignored.
		return orMatcher.matches(request);
	}

}
