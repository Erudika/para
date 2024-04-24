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

import com.erudika.para.core.cache.Cache;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.Utils;
import com.erudika.para.server.utils.HttpUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.inject.Inject;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

	private String parameterName = "_csrf";
	private final String headerName = "X-CSRF-TOKEN";
	private final Map<String, Object[]> localCache = new ConcurrentHashMap<>();

	private Cache cache;

	/**
	 * Returns the cache object.
	 * @return the cache object
	 */
	public Cache getCache() {
		return cache;
	}

	/**
	 * Sets the cache object.
	 * @param cache a cache object
	 */
	@Inject
	public void setCache(Cache cache) {
		this.cache = cache;
	}

	/**
	 * Saves a CSRF token in cache.
	 * @param t (ignored)
	 * @param request HTTP request
	 * @param response HTTP response
	 */
	public void saveToken(CsrfToken t, HttpServletRequest request, HttpServletResponse response) {
		String ident = getIdentifierFromCookie(request);
		if (StringUtils.isBlank(ident) && StringUtils.isBlank(HttpUtils.getStateParam(Para.getConfig().authCookieName(), request))) {
			ident = Utils.generateSecurityToken(16);
			storeAnonIdentCookie(ident, request, response);
		}
		if (ident != null) {
			CsrfToken token = loadToken(request);
			if (token == null) {
				String anonid = HttpUtils.getStateParam(anonymousCsrfCookieName(), request);
				if (anonid != null) {
					token = loadTokenFromCache(ident);
					if (token == null) {
						HttpUtils.removeStateParam(Para.getConfig().csrfCookieName(), request, response);
						HttpUtils.removeStateParam(anonymousCsrfCookieName(), request, response);
						removeTokenFromCache(ident);
						return;
					}
				} else {
					token = generateToken(null);
				}
				storeTokenInCache(ident, token);
			}
			storeTokenAsCookie(token, request, response);
		}
	}

	/**
	 * Loads a CSRF token from cache.
	 * @param request HTTP request
	 * @return the token
	 */
	public CsrfToken loadToken(HttpServletRequest request) {
		CsrfToken token = null;
		String ident = getIdentifierFromCookie(request);
		if (ident != null) {
			String key = ident.concat(parameterName);
			token = loadTokenFromCache(key);
			String anonid = HttpUtils.getStateParam(anonymousCsrfCookieName(), request);
			if (anonid != null) {
				CsrfToken anonToken = loadTokenFromCache(anonid);
				if (!ident.equals(anonid) && anonToken != null && token != null) {
					// sync anon and auth csrf tokens
					//storeTokenInCache(anonid, token);
					storeTokenInCache(ident, anonToken);
					token = anonToken;
				}
			}
		}
		return isValidButNotInCookie(token, request) ? null : token;
	}

	private void storeTokenInCache(String key, CsrfToken token) {
		if (!key.endsWith(parameterName)) {
			key = key.concat(parameterName);
		}
		if (Para.getConfig().isCacheEnabled()) {
			cache.put(Para.getConfig().getRootAppIdentifier(), key, token, (long) Para.getConfig().sessionTimeoutSec());
		} else {
			localCache.put(key, new Object[]{token, System.currentTimeMillis()});
		}
	}

	private CsrfToken loadTokenFromCache(String key) {
		if (!key.endsWith(parameterName)) {
			key = key.concat(parameterName);
		}
		CsrfToken token = null;
		if (Para.getConfig().isCacheEnabled()) {
			token = cache.get(Para.getConfig().getRootAppIdentifier(), key);
		} else {
			Object[] arr = localCache.get(key);
			if (arr != null && arr.length == 2) {
				boolean expired = (((Long) arr[1]) + Para.getConfig().sessionTimeoutSec() * 1000) < System.currentTimeMillis();
				if (expired) {
					removeTokenFromCache(key);
				} else {
					token = (CsrfToken) arr[0];
				}
			}
		}
		return token;
	}

	private void removeTokenFromCache(String key) {
		if (!key.endsWith(parameterName)) {
			key = key.concat(parameterName);
		}
		if (Para.getConfig().isCacheEnabled()) {
			cache.remove(key);
		} else {
			localCache.remove(key);
		}
	}

	private String getIdentifierFromCookie(HttpServletRequest request) {
		String cookie = HttpUtils.getStateParam(Para.getConfig().authCookieName(), request);
		String ident = null;
		if (cookie != null) {
			String[] ctokens = Utils.base64dec(cookie).split(":");
			ident = Utils.base64dec(Utils.urlDecode(ctokens[0]));
		}
		if (ident == null) {
			ident = HttpUtils.getStateParam(anonymousCsrfCookieName(), request);
		}
		return ident;
	}

	private String getTokenFromCookie(HttpServletRequest request) {
		String tokenInCookie = HttpUtils.getStateParam(Para.getConfig().csrfCookieName(), request);
		if (!StringUtils.isBlank(tokenInCookie)) {
			return tokenInCookie;
		}
		return "";
	}

	private void storeTokenAsCookie(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
		if (isValidButNotInCookie(token, request)) {
			Cookie c = new Cookie(Para.getConfig().csrfCookieName(), token.getToken());
			c.setMaxAge(Para.getConfig().sessionTimeoutSec());
			// don't enable HttpOnly - javascript can't access the cookie if enabled
			c.setHttpOnly(false);
			c.setSecure("https".equalsIgnoreCase(request.getScheme()));
			c.setPath("/");
			response.addCookie(c);
		}
	}

	private void storeAnonIdentCookie(String anonid, HttpServletRequest request, HttpServletResponse response) {
		Cookie c = new Cookie(anonymousCsrfCookieName(), anonid);
		c.setMaxAge(Para.getConfig().sessionTimeoutSec());
		// don't enable HttpOnly - javascript can't access the cookie if enabled
		c.setHttpOnly(false);
		c.setSecure("https".equalsIgnoreCase(request.getScheme()));
		c.setPath("/");
		response.addCookie(c);
	}

	private boolean isValidButNotInCookie(CsrfToken token, HttpServletRequest request) {
		return token != null && !StringUtils.isBlank(token.getToken()) &&
				!StringUtils.equals(getTokenFromCookie(request), token.getToken());
	}

	/**
	 * Generates a CSRF token string.
	 * @param request HTTP request
	 * @return a new token
	 */
	public CsrfToken generateToken(HttpServletRequest request) {
		return new DefaultCsrfToken(headerName, parameterName, Utils.generateSecurityToken());
	}

	/**
	 * Sets the {@link HttpServletRequest} parameter name that the {@link CsrfToken} is expected to appear on.
	 * @param parameterName the new parameter name to use
	 */
	public void setParameterName(String parameterName) {
		Assert.hasLength(parameterName, "parameterName cannot be null or empty");
		this.parameterName = parameterName;
	}

	/**
	 * Sets the header name that the {@link CsrfToken} is expected to appear on
	 * and the header that the response will contain the {@link CsrfToken}.
	 *
	 * @param parameterName the new parameter name to use
	 */
	public void setHeaderName(String parameterName) {
		Assert.hasLength(parameterName, "parameterName cannot be null or empty");
		this.parameterName = parameterName;
	}

	private String anonymousCsrfCookieName() {
		return Para.getConfig().csrfCookieName() + "-anonid";
	}

}
