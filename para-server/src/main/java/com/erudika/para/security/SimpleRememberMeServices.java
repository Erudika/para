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

import com.erudika.para.utils.Utils;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

/**
 * A slightly modified version of {@link TokenBasedRememberMeServices}.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SimpleRememberMeServices extends TokenBasedRememberMeServices {

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
}
