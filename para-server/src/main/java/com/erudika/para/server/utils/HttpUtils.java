/*
 * Copyright 2013-2022 Erudika. http://erudika.com
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
package com.erudika.para.server.utils;

import com.erudika.para.core.utils.Para;
import com.erudika.para.server.security.SecurityUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.TimeZone;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.http.HttpHeaders;

/**
 * Various utilities for HTTP stuff - cookies, AJAX, etc.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class HttpUtils {

	/**
	 * Default private constructor.
	 */
	private HttpUtils() { }

	/**
	 * Checks if a request comes from JavaScript.
	 * @param request HTTP request
	 * @return true if AJAX
	 */
	public static boolean isAjaxRequest(HttpServletRequest request) {
		return "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With")) ||
				"XMLHttpRequest".equalsIgnoreCase(request.getParameter("X-Requested-With"));
	}

	/////////////////////////////////////////////
	//    	   COOKIE & STATE UTILS
	/////////////////////////////////////////////

	/**
	 * Sets a cookie.
	 * @param name the name
	 * @param value the value
	 * @param req HTTP request
	 * @param res HTTP response
	 */
	public static void setStateParam(String name, String value, HttpServletRequest req, HttpServletResponse res) {
		setStateParam(name, value, req, res, false);
	}

	/**
	 * Sets a cookie.
	 * @param name the name
	 * @param value the value
	 * @param req HTTP request
	 * @param res HTTP response
	 * @param httpOnly HTTP only flag
	 */
	public static void setStateParam(String name, String value, HttpServletRequest req,
			HttpServletResponse res, boolean httpOnly) {
		setRawCookie(name, value, req, res, httpOnly, -1);
	}

	/**
	 * Reads a cookie.
	 * @param name the name
	 * @param req HTTP request
	 * @return the cookie value
	 */
	public static String getStateParam(String name, HttpServletRequest req) {
		return getCookieValue(req, name);
	}

	/**
	 * Deletes a cookie.
	 * @param name the name
	 * @param req HTTP request
	 * @param res HTTP response
	 */
	public static void removeStateParam(String name, HttpServletRequest req,
			HttpServletResponse res) {
		setRawCookie(name, "", req, res, false, 0);
	}

	/**
	 * Sets a cookie.
	 * @param name the name
	 * @param value the value
	 * @param req HTTP request
	 * @param res HTTP response
	 * @param httpOnly HTTP only flag
	 * @param maxAge max age
	 */
	public static void setRawCookie(String name, String value, HttpServletRequest req,
			HttpServletResponse res, boolean httpOnly, int maxAge) {
		if (StringUtils.isBlank(name) || value == null || req == null || res == null) {
			return;
		}
		Cookie cookie = new Cookie(name, value);
		cookie.setHttpOnly(httpOnly);
		cookie.setMaxAge(maxAge < 0 ? Para.getConfig().sessionTimeoutSec() : maxAge);
		cookie.setPath("/");
		cookie.setSecure(req.isSecure());
		res.addCookie(cookie);
	}

	/**
	 * Reads a cookie.
	 * @param name the name
	 * @param req HTTP request
	 * @return the cookie value
	 */
	public static String getCookieValue(HttpServletRequest req, String name) {
		if (StringUtils.isBlank(name) || req == null) {
			return null;
		}
		Cookie[] cookies = req.getCookies();
		if (cookies == null) {
			return null;
		}
		//Otherwise, we have to do a linear scan for the cookie.
		for (Cookie cookie : cookies) {
			if (cookie.getName().equals(name)) {
				return cookie.getValue();
			}
		}
		return null;
	}

	/**
	 * Sets the auth cookie.
	 * @param name name
	 * @param value value
	 * @param maxAge maxAge
	 * @param request request
	 * @param response response
	 */
	public static void setAuthCookie(String name, String value, int maxAge,
			HttpServletRequest request, HttpServletResponse response) {
		setAuthCookie(name, value, true, maxAge, "Lax", request, response);
	}

	/**
	 * Sets the auth cookie.
	 * @param name name
	 * @param value value
	 * @param httpOnly HTTP only flag
	 * @param maxAge cookie validity in seconds
	 * @param sameSite SameSite value
	 * @param request request
	 * @param response response
	 */
	public static void setAuthCookie(String name, String value, boolean httpOnly, int maxAge, String sameSite,
			HttpServletRequest request, HttpServletResponse response) {
		String expires = DateFormatUtils.format(System.currentTimeMillis() + (maxAge * 1000),
				"EEE, dd-MMM-yyyy HH:mm:ss z", TimeZone.getTimeZone("GMT"));
		String contextPath = request.getContextPath();
		String path = contextPath.length() > 0 ? contextPath : "/";
		StringBuilder sb = new StringBuilder();
		sb.append(name).append("=").append(value).append(";");
		sb.append("Path=").append(path).append(";");
		sb.append("Expires=").append(expires).append(";");
		sb.append("Max-Age=").append(maxAge).append(";");
		if (httpOnly) {
			sb.append("HttpOnly;");
		}
		if (Strings.CI.startsWith(SecurityUtils.getRedirectUrl(request), "https://") || request.isSecure()) {
			sb.append("Secure;");
		}
		if (!StringUtils.isBlank(sameSite)) {
			sb.append("SameSite=").append(sameSite);
		}
		response.addHeader(HttpHeaders.SET_COOKIE, sb.toString());
	}
}
