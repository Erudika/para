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

import com.erudika.para.core.User;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public final class SecurityUtils {

	private static final Logger logger = LoggerFactory.getLogger(SecurityUtils.class);
	private static final String APP_KEY = Config.APP_SECRET_KEY;	// salt for token gen
	
	public static User getAuthenticatedUser(){
		if(SecurityContextHolder.getContext().getAuthentication() == null) return null;
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if(auth instanceof UserAuthentication && auth.isAuthenticated()){
			return (User) auth.getPrincipal();
		}else{
			return null;
		}
	}
	
	public static void clearSession(HttpServletRequest req, HttpServletResponse res){
//		req.getSession().invalidate();
		Utils.removeStateParam(Config.AUTH_COOKIE, req, res);
		try {
			req.logout();
		} catch (ServletException ex) {
			logger.warn(null, ex);
		}
		SecurityContextHolder.clearContext();		
	}
	
	public static String getCSRFtoken(User user){
		if(user == null || user.getAuthtoken() == null || user.getCurrentIdentifier() == null){
			return Utils.MD5("anonymous");
		}
		String authtoken = user.getAuthtoken();
		String ident = user.getCurrentIdentifier();
		if(StringUtils.isBlank(authtoken)) logger.warn("Authtoken is blank!");
		// Sectoken (stoken)
		return Utils.MD5(ident.
				concat(Config.SEPARATOR).
				concat(StringUtils.trimToEmpty(authtoken)).
				concat(Config.SEPARATOR).
				concat(StringUtils.trimToEmpty(user.getAuthstamp())));
	}
	
//	protected static void setAuthCookie(User user, HttpServletRequest request, HttpServletResponse response){
//		if(user.getCurrentIdentifier() == null) return;
//		Long authstamp = System.currentTimeMillis();
//		String ident = user.getCurrentIdentifier();
//		user.setIdentifier(ident);
//		user.setAuthstamp(authstamp.toString());
//		user.update();
//		Utils.setStateParam(Config.AUTH_COOKIE, Base64.encodeBase64URLSafeString(ident.getBytes()).trim().
//				concat(Config.SEPARATOR).concat(getCookieHash(user)), request, response, true);
//	}
		
//	protected static String getCookieHash(User user){
//		String authtoken = user.getAuthtoken();
//		String ident = user.getCurrentIdentifier();
//		if(StringUtils.isBlank(authtoken)) logger.warn("Authtoken is blank!");
//		return Utils.MD5(ident.
//				concat(Config.SEPARATOR).
//				concat(StringUtils.trimToEmpty(user.getAuthstamp())).
//				concat(Config.SEPARATOR).
//				concat(StringUtils.trimToEmpty(authtoken)).
//				concat(Config.SEPARATOR).concat(APP_KEY));
//	}
	
//	protected static String authenticateFromCookie(HttpServletRequest req, HttpServletResponse res) {
//		String cookie = Utils.getStateParam(Config.AUTH_COOKIE, req, res);
//		User user = SecurityUtils.getAuthenticatedUser();
//		if (cookie != null && user == null && !StringUtils.isBlank(cookie) && cookie.contains(Config.SEPARATOR)) {
//			String[] tuparts = cookie.split(Config.SEPARATOR);
//			String identifier = new String(Base64.decodeBase64(tuparts[0]));
//			String hash = tuparts[1];
//
//			user = User.readUserForIdentifier(identifier);
//			if (user.getCurrentIdentifier() != null && user.getAuthtoken() != null && user.getAuthstamp() != null) {
//				String h = SecurityUtils.getCookieHash(user);
//
//				if (StringUtils.equals(hash, h)) {
//					SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
//					return identifier;
//				} else {
//					Utils.removeStateParam(Config.AUTH_COOKIE, req, res);
//				}
//			}
//		}
//		return null;
//	}
}
