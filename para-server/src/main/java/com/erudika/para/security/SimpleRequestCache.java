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

import com.erudika.para.utils.Config;
import com.erudika.para.utils.HttpUtils;
import com.erudika.para.utils.Utils;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.web.PortResolver;
import org.springframework.security.web.PortResolverImpl;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Simple cache implementation for saving authentication request.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SimpleRequestCache implements RequestCache {

	private final RequestMatcher anyRequestMatcher = AnyRequestMatcher.INSTANCE;
	private final RequestMatcher ajaxRequestMatcher = AjaxRequestMatcher.INSTANCE;
	private final PortResolver portResolver = new PortResolverImpl();

	/**
	 * Saves a request in cache.
	 * @param request HTTP request
	 * @param response HTTP response
	 */
	@Override
	public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
		if (anyRequestMatcher.matches(request) && !ajaxRequestMatcher.matches(request)) {
			DefaultSavedRequest savedRequest = new DefaultSavedRequest(request, portResolver);
			HttpUtils.setStateParam(Config.RETURNTO_COOKIE,
					Utils.base64enc(savedRequest.getRedirectUrl().getBytes()), request, response);
		}
	}

	/**
	 * Returns a cached request. NOT USED!
	 * @param request HTTP request
	 * @param response HTTP response
	 * @return null
	 */
	@Override
	public SavedRequest getRequest(HttpServletRequest request, HttpServletResponse response) {
		return null;
	}

	/**
	 * Returns a matching request. NOT USED!
	 * @param request HTTP request
	 * @param response HTTP response
	 * @return null
	 */
	@Override
	public HttpServletRequest getMatchingRequest(HttpServletRequest request, HttpServletResponse response) {
		return null;
	}

	/**
	 * Removes a saved request from cache.
	 * @param request HTTP request
	 * @param response HTTP response
	 */
	@Override
	public void removeRequest(HttpServletRequest request, HttpServletResponse response) {
		HttpUtils.removeStateParam(Config.RETURNTO_COOKIE, request, response);
	}

}
