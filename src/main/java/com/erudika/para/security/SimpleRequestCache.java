/*
 * Copyright 2013 Alex Bogdanovski <alex@erudika.com>.
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
 * You can reach the author at: https://github.com/albogdano
 */
package com.erudika.para.security;

import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.binary.Base64;
import org.springframework.security.web.PortResolver;
import org.springframework.security.web.PortResolverImpl;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class SimpleRequestCache implements RequestCache {

	private RequestMatcher requestMatcher = AnyRequestMatcher.INSTANCE;
	private PortResolver portResolver = new PortResolverImpl();
	
	@Override
	public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
		if (requestMatcher.matches(request)) {
			DefaultSavedRequest savedRequest = new DefaultSavedRequest(request, portResolver);
			Utils.setStateParam(Config.RETURNTO_COOKIE, 
					Base64.encodeBase64String(savedRequest.getRedirectUrl().getBytes()), request, response);
		}
	}

	@Override
	public SavedRequest getRequest(HttpServletRequest request, HttpServletResponse response) {
		return null;
	}

	@Override
	public HttpServletRequest getMatchingRequest(HttpServletRequest request, HttpServletResponse response) {
		return null;
	}

	@Override
	public void removeRequest(HttpServletRequest request, HttpServletResponse response) {
		Utils.removeStateParam(Config.RETURNTO_COOKIE, request, response);
	}
	
}
