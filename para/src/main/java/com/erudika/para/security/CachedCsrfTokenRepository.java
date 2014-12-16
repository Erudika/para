/*
 * Copyright 2013-2014 Erudika. http://erudika.com
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

import com.eaio.uuid.UUID;
import com.erudika.para.cache.Cache;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.util.Assert;

/**
 * A {@link CsrfTokenRepository} that stores the {@link CsrfToken} in {@link Cache}.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class CachedCsrfTokenRepository implements CsrfTokenRepository {

	private static final Logger logger = LoggerFactory.getLogger(CachedCsrfTokenRepository.class);

	private static final String DEFAULT_CSRF_PARAMETER_NAME = "_csrf";
	private static final String DEFAULT_CSRF_HEADER_NAME = "X-CSRF-TOKEN";

	private String parameterName = DEFAULT_CSRF_PARAMETER_NAME;
	private String headerName = DEFAULT_CSRF_HEADER_NAME;

	private Map<String, Object[]> localCache = new HashMap<String, Object[]>();

	private Cache cache;

	/**
	 * Returns the cache object.
	 * @return the cache object
	 */
	public Cache getCache() {
		return cache;
	}

	/**
	 * Sets the cache object
	 * @param cache a cache object
	 */
	@Inject
	public void setCache(Cache cache) {
		this.cache = cache;
	}

	/**
	 * Saves a CSRF token in cache.
	 * @param token the token
	 * @param request HTTP request
	 * @param response HTTP response
	 * @see org.springframework.security.web.csrf.CsrfTokenRepository#saveToken(org.springframework.security.web.csrf.CsrfToken, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
		if (token != null) {
			String ident = getIdentifierFromCookie(request);
			if (ident != null) {
				if (Config.isCacheEnabled()) {
					String key = ident.concat(parameterName);
					storeTokenAsCookie((CsrfToken) cache.get(Config.APP_NAME_NS, key), request, response);
				} else {
					String key = Config.APP_NAME_NS.concat(ident).concat(parameterName);
					storeTokenAsCookie((CsrfToken) localCache.get(key)[0], request, response);
				}
			}
		}
	}

	/**
	 * Loads a CSRF token from cache.
	 * @param request HTTP request
	 * @return the token
	 * @see org.springframework.security.web.csrf.CsrfTokenRepository#loadToken(javax.servlet.http.HttpServletRequest)
	 */
	public CsrfToken loadToken(HttpServletRequest request) {
		String ident = getIdentifierFromCookie(request);
		CsrfToken token = null;
		if (ident != null) {
			if (Config.isCacheEnabled()) {
				String key = ident.concat(parameterName);
				token = cache.get(Config.APP_NAME_NS, key);
				if (token == null) {
					cache.put(Config.APP_NAME_NS, key, generateToken(null), Config.SESSION_TIMEOUT_SEC);
				}
			} else {
				String key = Config.APP_NAME_NS.concat(ident).concat(parameterName);
				Object[] arr = localCache.get(key);
				if (arr != null && arr.length == 2) {
					boolean expired = (((Long) arr[1]) + Config.SESSION_TIMEOUT_SEC * 1000) < System.currentTimeMillis();
					if (expired) {
						localCache.remove(key);
					} else {
						token = (CsrfToken) arr[0];
					}
				} else {
					localCache.put(key, new Object[]{generateToken(null), System.currentTimeMillis()});
				}
			}
		}
		if (token == null) {
			String t = request.getParameter(parameterName);
			token = (t == null) ? null : new DefaultCsrfToken(headerName, parameterName, t);
		}
		return token;
	}

	private String getIdentifierFromCookie(HttpServletRequest request) {
		String cookie = Utils.getStateParam(Config.AUTH_COOKIE, request);
		String ident = null;
		if (cookie != null) {
			String[] ctokens = Utils.base64dec(cookie).split(":");
			ident = Utils.base64dec(ctokens[0]);
		}
		return ident;
	}

	private void storeTokenAsCookie(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
		String cookieName = Config.getConfigParam("security.csrf_cookie", "");
		if (!StringUtils.isBlank(cookieName) && token != null) {
			String storedToken = Utils.getStateParam(cookieName, request);
			if (!StringUtils.equals(storedToken, token.getToken())) {
				Cookie c = new Cookie(cookieName, token.getToken());
				c.setMaxAge(Config.SESSION_TIMEOUT_SEC.intValue());
				c.setPath("/");
				response.addCookie(c);
			}
		}
	}

	/**
	 * Generates a CSRF token string.
	 * @param request HTTP request
	 * @return a new token
	 * @see org.springframework.security.web.csrf.CsrfTokenRepository#generateToken(javax.servlet.http.HttpServletRequest)
	 */
	public CsrfToken generateToken(HttpServletRequest request) {
		return new DefaultCsrfToken(headerName, parameterName, new UUID().toString());
	}

	/**
	 * Sets the {@link HttpServletRequest} parameter name that the {@link CsrfToken} is expected to appear on
	 * @param parameterName the new parameter name to use
	 */
	public void setParameterName(String parameterName) {
		Assert.hasLength(parameterName, "parameterName cannot be null or empty");
		this.parameterName = parameterName;
	}

	/**
	 * Sets the header name that the {@link CsrfToken} is expected to appear on and the header that the response will
	 * contain the {@link CsrfToken}.
	 *
	 * @param parameterName the new parameter name to use
	 */
	public void setHeaderName(String parameterName) {
		Assert.hasLength(parameterName, "parameterName cannot be null or empty");
		this.parameterName = parameterName;
	}

}
