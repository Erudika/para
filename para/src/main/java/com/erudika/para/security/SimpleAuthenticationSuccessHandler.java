/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

/**
 * Simple handler for successful authentication requests.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SimpleAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		if (isRestRequest(request)) {
			RestUtils.returnStatusResponse(response, HttpServletResponse.SC_NO_CONTENT, "Authentication success.");
		} else {
			super.onAuthenticationSuccess(request, response, authentication);
		}
	}

	@Override
	protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response) {
		String cookie = Utils.getStateParam(Config.RETURNTO_COOKIE, request);
		if (cookie != null) {
			cookie = Utils.base64dec(cookie);
			Utils.removeStateParam(Config.RETURNTO_COOKIE, request, response);
			return cookie;
		} else {
			return super.determineTargetUrl(request, response);
		}
	}

	/**
	 * Checks if it is a rest request
	 * @param request request
	 * @return true if rest or ajax
	 */
	protected boolean isRestRequest(HttpServletRequest request) {
		return RestRequestMatcher.INSTANCE.matches(request) || AjaxRequestMatcher.INSTANCE.matches(request);
	}
}
