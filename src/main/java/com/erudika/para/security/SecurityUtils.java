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

import com.erudika.para.core.User;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public final class SecurityUtils {

	private static final Logger logger = LoggerFactory.getLogger(SecurityUtils.class);
	
	public static User getAuthenticatedUser(){
		if(SecurityContextHolder.getContext().getAuthentication() == null) return null;
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if(auth.isAuthenticated() && auth.getPrincipal() instanceof User){
			return (User) auth.getPrincipal();
		}else{
			return null;
		}
	}
	
	public static void clearSession(HttpServletRequest req, HttpServletResponse res){
//		Utils.removeStateParam(Config.AUTH_COOKIE, req, res);
		try {
			SecurityContextHolder.clearContext();
			req.logout();
		} catch (ServletException ex) {
			logger.warn(null, ex);
		}
	}	
}
