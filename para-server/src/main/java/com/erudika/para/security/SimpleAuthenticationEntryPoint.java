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

import com.erudika.para.rest.RestUtils;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/**
 * Authentication entry point.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SimpleAuthenticationEntryPoint extends LoginUrlAuthenticationEntryPoint {

	/**
	 * Default constructor.
	 * @param loginFormUrl url of the login page e.g. "/login.html"
	 */
	public SimpleAuthenticationEntryPoint(String loginFormUrl) {
		super(loginFormUrl);
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException authException) throws IOException, ServletException {
		if (isPreflight(request)) {
			response.setStatus(HttpServletResponse.SC_NO_CONTENT);
		} else if (isRestRequest(request)) {
			RestUtils.returnStatusResponse(response, HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage());
		} else {
			super.commence(request, response, authException);
		}
	}

	/**
	 * Checks if this is a X-domain pre-flight request.
	 * @param request request
	 * @return true if preflight
	 */
	private boolean isPreflight(HttpServletRequest request) {
		return HttpMethod.OPTIONS.equals(request.getMethod());
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
