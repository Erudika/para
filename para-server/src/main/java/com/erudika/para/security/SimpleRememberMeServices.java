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

import com.erudika.para.core.App;
import com.erudika.para.core.User;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.util.TimeZone;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

/**
 * A slightly modified version of {@link TokenBasedRememberMeServices}.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SimpleRememberMeServices extends TokenBasedRememberMeServices {

	/**
	 * Default constructor.
	 * @param key a key
	 * @param userDetailsService a user object
	 */
	public SimpleRememberMeServices(String key, UserDetailsService userDetailsService) {
		super(key, userDetailsService);
	}

	@Override
	protected String encodeCookie(String[] cookieTokens) {
		if (cookieTokens.length > 0) {
			// This is a workaround for the issue with user identifiers
			// which start with "xy:" where "xy" is the type of auth provider used.
			// The problem comes from the fact that both Para and Spring use ":" as
			// delimeter and that breaks the cookie parsing.
			cookieTokens[0] = Utils.base64enc(cookieTokens[0].getBytes());
		}
		return super.encodeCookie(cookieTokens);
	}

	@Override
	protected String[] decodeCookie(String cookieValue) {
		String[] cookieTokens = super.decodeCookie(cookieValue);
		if (cookieTokens.length > 0) {
			cookieTokens[0] = Utils.base64dec(cookieTokens[0]);
		}
		return cookieTokens;
	}

	@Override
	protected String makeTokenSignature(long tokenExpiryTime, String username, String password) {
		if (username != null) {
			username = Utils.base64enc(username.getBytes());
		}
		String sig = super.makeTokenSignature(tokenExpiryTime, username, password);
		return sig;
	}

	@Override
	protected String retrieveUserName(Authentication authentication) {
		if (authentication.getPrincipal() instanceof UserDetails) {
			User u = ((AuthenticatedUserDetails) authentication.getPrincipal()).getUser();
			if (!App.isRoot(u.getAppid())) {
				// we mark the user a part of another app so that it can be found later
				return u.getAppid() + "/" + u.getIdentifier();
			}
			return u.getIdentifier();
		}
		return authentication.getPrincipal().toString();
	}

	@Override
	protected void setCookie(String[] tokens, int maxAge, HttpServletRequest request, HttpServletResponse response) {
		String cookieValue = encodeCookie(tokens);
		String authCookie = Config.getConfigParam("auth_cookie", Config.PARA.concat("-auth"));
		String expires = DateFormatUtils.format(System.currentTimeMillis() + (maxAge * 1000),
				"EEE, dd-MMM-yyyy HH:mm:ss z", TimeZone.getTimeZone("GMT"));
		String contextPath = request.getContextPath();
		String path = contextPath.length() > 0 ? contextPath : "/";
		StringBuilder sb = new StringBuilder();
		sb.append(authCookie).append("=").append(cookieValue).append(";");
		sb.append("Path=").append(path).append(";");
		sb.append("Expires=").append(expires).append(";");
		sb.append("Max-Age=").append(maxAge).append(";");
		sb.append("HttpOnly;");
		sb.append("SameSite=Lax");
		response.addHeader(javax.ws.rs.core.HttpHeaders.SET_COOKIE, sb.toString());
	}


}
