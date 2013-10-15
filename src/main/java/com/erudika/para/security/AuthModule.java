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

import com.erudika.para.persistence.DAO;
import com.erudika.para.cache.Cache;
import com.erudika.para.core.User;
import com.erudika.para.utils.Utils;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.openid.AxFetchListFactory;
import org.springframework.security.openid.OpenIDAttribute;
import org.springframework.security.openid.OpenIDAuthenticationFilter;
import org.springframework.security.openid.OpenIDAuthenticationToken;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class AuthModule extends OpenIDAuthenticationFilter { 

	private static final String OPENID_ACTION = "openid_auth";
	private static final String FACEBOOK_ACTION = "facebook_auth";
	private static final String PASSWORD_ACTION = "password_auth";
	private static final String PASSWORD = "password";
	private static final String EMAIL = "email";
	private static final String APP_KEY = Utils.APP_SECRET_KEY;	// salt for token gen
	private static final Logger log = Logger.getLogger(AuthModule.class.getName());
	private static final ObjectMapper mapper = new ObjectMapper();

	@Inject private DAO dao;
	
	AuthModule() {}

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
		Utils.removeStateParam(Utils.AUTH_COOKIE, req, res);
		try {
			req.logout();
		} catch (ServletException ex) {
			log.log(Level.WARNING, null, ex);
		}
		SecurityContextHolder.clearContext();		
	}
	
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) 
			throws AuthenticationException, IOException {
		String requestURI = request.getRequestURI();
		UserAuthentication userAuth = null;
		User user = null;
		
		if(requestURI.endsWith(FACEBOOK_ACTION)){
			//Facebook Connect Authentication 
			String fbSig = request.getParameter("fbsig");
			String fbEmail = request.getParameter("fbemail");
			String fbName = request.getParameter("fbname");
			String fbID = verifiedFacebookID(fbSig);
			
			if (fbID != null) {
				//success!
				user = User.readUserForIdentifier(fbID, dao);
				if(user == null){
					//user is new
					user = new User();
					user.setEmail(StringUtils.isBlank(fbEmail) ? "email@domain.com" : fbEmail);
					user.setName(StringUtils.isBlank(fbName) ? "No Name" : fbName);
					user.setIdentifier(fbID);
					String id = user.create();
					if(id == null){
						throw new BadCredentialsException("Authentication failed: cannot create new user.");
					}
				}
				userAuth = new UserAuthentication(user);
			}
		}else if(requestURI.endsWith(PASSWORD_ACTION)){
			String email = request.getParameter(EMAIL);
			String pass = request.getParameter(PASSWORD);
			
			if(User.passwordMatches(pass, email, dao)){
				//success!
				user = User.readUserForIdentifier(email, dao);
				userAuth = new UserAuthentication(user);
			}
		}else if(requestURI.endsWith(OPENID_ACTION)){
			Authentication oidAuth = super.attemptAuthentication(request, response);
			
			if(oidAuth == null){
				// hang on... redirecting to openid provider
				return null;
			}else{
				//success!
				user = (User) oidAuth.getPrincipal();
				userAuth = new UserAuthentication(user);
			}
		}
		
		if(userAuth == null || user == null || !user.isEnabled() || user.getIdentifier() == null) {
			throw new BadCredentialsException("Bad credentials.");
		}else{
			setAuthCookie(user, request, response);
		}
		return userAuth;
	}
		
	private void setAuthCookie(User user, HttpServletRequest request, HttpServletResponse response){
		if(user.getCurrentIdentifier() == null) return;
		Long authstamp = System.currentTimeMillis();
		String ident = user.getCurrentIdentifier();
		user.setIdentifier(ident);
		user.setAuthstamp(authstamp.toString());
		user.update();
		Utils.setStateParam(Utils.AUTH_COOKIE, Base64.encodeBase64URLSafeString(ident.getBytes()).trim().
				concat(Utils.SEPARATOR).concat(getCookieHash(user)), request, response, true);
	}
		
	private static String getCookieHash(User user){
		String authtoken = user.getAuthtoken();
		String ident = user.getCurrentIdentifier();
		if(StringUtils.isBlank(authtoken)) log.log(Level.WARNING, "Authtoken is blank!");
		return Utils.MD5(ident.
				concat(Utils.SEPARATOR).
				concat(StringUtils.trimToEmpty(user.getAuthstamp())).
				concat(Utils.SEPARATOR).
				concat(StringUtils.trimToEmpty(authtoken)).
				concat(Utils.SEPARATOR).concat(APP_KEY));
	}
	
	public static String getCSRFtoken(User user){
		if(user == null || user.getAuthtoken() == null || user.getCurrentIdentifier() == null) return "";
		String authtoken = user.getAuthtoken();
		String ident = user.getCurrentIdentifier();
		if(StringUtils.isBlank(authtoken)) log.log(Level.WARNING, "Authtoken is blank!");
		// Sectoken (stoken)
		return Utils.MD5(ident.
				concat(Utils.SEPARATOR).
				concat(StringUtils.trimToEmpty(authtoken)).
				concat(Utils.SEPARATOR).
				concat(StringUtils.trimToEmpty(user.getAuthstamp())));
	}
	
	private String verifiedFacebookID(String fbSig){
		if(!StringUtils.contains(fbSig, ".")) return null;
		String fbid = null;
		
		try {
			String[] parts = fbSig.split("\\.");
			byte[] sig = Base64.decodeBase64(parts[0]);
			byte[] json = Base64.decodeBase64(parts[1]);
			byte[] encodedJSON = parts[1].getBytes();	// careful, we compute against the base64 encoded version
			String decodedJSON = new String(json);
			JsonNode root = mapper.readTree(decodedJSON);
			
			if(StringUtils.contains(decodedJSON, "HMAC-SHA256")){
				SecretKey secret = new SecretKeySpec(Utils.FB_SECRET.getBytes(), "HMACSHA256");
				Mac mac = Mac.getInstance("HMACSHA256");
				mac.init(secret);
				byte[] digested = mac.doFinal(encodedJSON);
				if(Arrays.equals(sig, digested)){
					fbid = root.get("user_id").getTextValue();
				}
			}
		} catch (Exception ex) {
			log.log(Level.WARNING, "Failed to decode FB sig: {0}", ex);
		}

		return fbid;
	}
	
	public static class UserAuthentication implements Authentication {

		private static final long serialVersionUID = 1L;
		private final User principal;
		private final Object details;
		private boolean authenticated;

		public UserAuthentication(User principal) {
			this.principal = principal;
			this.details = principal;
			this.authenticated = true;
		}

		public Collection<GrantedAuthority> getAuthorities() {
			return new HashSet<GrantedAuthority>(principal.getAuthorities());
		}

		public Object getCredentials() {
			return principal.getAuthtoken();
		}

		public Object getDetails() {
			return details;
		}

		public Object getPrincipal() {
			return principal;
		}

		public boolean isAuthenticated() {
			return authenticated;
		}

		public void setAuthenticated(boolean isAuthenticated) {
			throw new UnsupportedOperationException();
		}

		public String getName() {
			return (principal.getCurrentIdentifier() != null) ? 
					principal.getCurrentIdentifier() : 
					principal.getIdentifier();
		}
	}

	public static class DefaultAxFetchListFactory implements AxFetchListFactory {
		private static final long serialVersionUID = 1L;
		public List<OpenIDAttribute> createAttributeList(String identifier) {
			List<OpenIDAttribute> list = new ArrayList<OpenIDAttribute>();
			if(identifier != null && identifier.matches("https://www.google.com/.*")){
				OpenIDAttribute email = new OpenIDAttribute("email", "http://axschema.org/contact/email");
				OpenIDAttribute first = new OpenIDAttribute("firstname", "http://axschema.org/namePerson/first");
				OpenIDAttribute last = new OpenIDAttribute("lastname", "http://axschema.org/namePerson/last");
				email.setCount(1);
				email.setRequired(true);
				first.setRequired(true);
				last.setRequired(true);
				list.add(email);
				list.add(first);
				list.add(last);
			}
			return list;
		}
	}
	
	public static class StandardUserService implements UserDetailsService, 
			AuthenticationUserDetailsService<OpenIDAuthenticationToken> {
		
		@Inject private DAO dao;
		
		public UserDetails loadUserByUsername(String ident) throws UsernameNotFoundException {
			User user = User.readUserForIdentifier(ident, dao);

			if (user == null) {
				throw new UsernameNotFoundException(ident);
			}

			return user;
		}
		
		public UserDetails loadUserDetails(OpenIDAuthenticationToken token) {
			if(token == null) return null;

			String identifier = token.getIdentityUrl();
			User user = User.readUserForIdentifier(identifier, dao);

			if(user == null){
				// create new OpenID user
				String email = "email@domain.com";
				String firstName = null, lastName = null, fullName = null;
				List<OpenIDAttribute> attributes = token.getAttributes();

				for (OpenIDAttribute attribute : attributes) {
					if (attribute.getName().equals("email")) email = attribute.getValues().get(0);
					if (attribute.getName().equals("firstname")) firstName = attribute.getValues().get(0);
					if (attribute.getName().equals("lastname")) lastName = attribute.getValues().get(0);
					if (attribute.getName().equals("fullname")) fullName = attribute.getValues().get(0);
				}

				if (fullName == null) {
					if (firstName == null) firstName = "No";
					if (lastName == null) lastName = "Name";
					fullName = firstName.concat(" ").concat(lastName);
				}

				user = new User();
				user.setEmail(email);
				user.setName(fullName);
				user.setIdentifier(identifier);
				String id = user.create();
				if(id == null){
					throw new BadCredentialsException("Authentication failed: cannot create new user.");
				}
			}

			return user;
		}
	}
	
	public static class HazelcastSecurityContextRepository implements SecurityContextRepository{

		private String KEY_PREFIX = "SC_";
		
		@Inject private Cache memgrid;
		@Inject private DAO dao;
		
		public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
			SecurityContext ctx = memgrid.get(KEY_PREFIX.concat(requestResponseHolder.getRequest().getRemoteUser()));
			if(ctx != null){
				return ctx;
			}else {
				String ident = authenticateFromCookie(requestResponseHolder.getRequest(), requestResponseHolder.getResponse());
				return StringUtils.isBlank(ident) ? SecurityContextHolder.createEmptyContext() : SecurityContextHolder.getContext();
			}
		}

		public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
			if(context.getAuthentication() instanceof UserAuthentication){
				UserAuthentication auth = (UserAuthentication) context.getAuthentication();
				String ident = auth.getName();
				if(!StringUtils.isBlank(ident)){
					memgrid.put(KEY_PREFIX.concat(ident), context);
				}
			}
		}

		public boolean containsContext(HttpServletRequest request) {
			if(request.getRemoteUser() == null) return false;
			return memgrid.contains(KEY_PREFIX.concat(request.getRemoteUser()));
		}
		
		private String authenticateFromCookie(HttpServletRequest req, HttpServletResponse res) {
			String cookie = Utils.getStateParam(Utils.AUTH_COOKIE, req, res);
			User user = getAuthenticatedUser();
			if (cookie != null && user == null && !StringUtils.isBlank(cookie) && cookie.contains(Utils.SEPARATOR)) {
				String[] tuparts = cookie.split(Utils.SEPARATOR);
				String identifier = new String(Base64.decodeBase64(tuparts[0]));
				String hash = tuparts[1];

				user = User.readUserForIdentifier(identifier, dao);
				if (user.getCurrentIdentifier() != null && user.getAuthtoken() != null && user.getAuthstamp() != null) {
					String h = getCookieHash(user);

					if (StringUtils.equals(hash, h)) {
						SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
						return identifier;
					} else {
						Utils.removeStateParam(Utils.AUTH_COOKIE, req, res);
					}
				}
			}
			return null;
		}
	}
}
