/*
 * Copyright 2013 Alex Bogdanovski <albogdano@me.com>.
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

import com.erudika.para.cache.Cache;
import com.erudika.para.core.User;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class CachedSecurityContextRepository implements SecurityContextRepository {

	private final String KEY_PREFIX = "SC_";
	
	private Cache cache;

	public Cache getCache() {
		return cache;
	}

	@Inject
	public void setCache(Cache cache) {
		this.cache = cache;
	}

	public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
//		String remoteUser = requestResponseHolder.getRequest().getRemoteUser();
//		if(SecurityContextHolder.getContext().getAuthentication() == null) return blank;
		
		if (containsContext(requestResponseHolder.getRequest())) {
			UserAuthentication auth = (UserAuthentication) SecurityContextHolder.getContext().getAuthentication();
			String name = (auth == null) ? "anonymous" : auth.getName();
			return cache.get(KEY_PREFIX.concat(name));
		} else {
//			String ident = SecurityUtils.authenticateFromCookie(requestResponseHolder.getRequest(), requestResponseHolder.getResponse());
//			return StringUtils.isBlank(ident) ? SecurityContextHolder.createEmptyContext() : SecurityContextHolder.getContext();
			return SecurityContextHolder.createEmptyContext();
		}
	}

	public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
		if (context.getAuthentication() instanceof UserAuthentication) {
			UserAuthentication auth = (UserAuthentication) context.getAuthentication();
			String ident = auth.getName();
			if (!StringUtils.isBlank(ident)) {
				cache.put(KEY_PREFIX.concat(ident), context);
			}
		}
	}

	public boolean containsContext(HttpServletRequest request) {
		if(SecurityContextHolder.getContext().getAuthentication() == null) return false;
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return cache.contains(KEY_PREFIX.concat(auth.getName()));
	}

}
