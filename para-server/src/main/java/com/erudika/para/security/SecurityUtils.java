/*
 * Copyright 2013-2018 Erudika. https://erudika.com
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
import com.erudika.para.rest.Signer;
import com.erudika.para.utils.BufferedRequestWrapper;
import com.erudika.para.utils.Config;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class with helper methods for authentication.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class SecurityUtils {

	private static final Logger logger = LoggerFactory.getLogger(SecurityUtils.class);

	private SecurityUtils() { }

	/**
	 * Extracts a User object from the security context.
	 * @return an authenticated user or null if a user is not authenticated
	 */
	public static User getAuthenticatedUser() {
		return getAuthenticatedUser(SecurityContextHolder.getContext().getAuthentication());
	}

	/**
	 * Extracts a User object from the security context.
	 * @param auth the authentication object
	 * @return an authenticated user or null if a user is not authenticated
	 */
	public static User getAuthenticatedUser(Authentication auth) {
		User user = null;
		if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUserDetails) {
			user = ((AuthenticatedUserDetails) auth.getPrincipal()).getUser();
		}
		return user;
	}

	/**
	 * Extracts a App object from the security context.
	 * @return an authenticated app or null if a app is not authenticated
	 */
	public static App getAuthenticatedApp() {
		App app = null;
		if (SecurityContextHolder.getContext().getAuthentication() != null) {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth.isAuthenticated() && auth.getPrincipal() instanceof App) {
				app = (App) auth.getPrincipal();
			}
		}
		return app;
	}

	/**
	 * @return returns the current app associated with the authenticated user
	 */
	public static App getAppFromJWTAuthentication() {
		App app = null;
		if (SecurityContextHolder.getContext().getAuthentication() != null) {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth instanceof JWTAuthentication) {
				app = ((JWTAuthentication) auth).getApp();
			}
		}
		return app;
	}

	/**
	 * @return returns the current app associated with the authenticated user
	 */
	public static App getAppFromLdapAuthentication() {
		App app = null;
		if (SecurityContextHolder.getContext().getAuthentication() != null) {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth instanceof LDAPAuthentication) {
				app = ((LDAPAuthentication) auth).getApp();
			}
		}
		return app;
	}

	/**
	 * Clears the session. Deletes cookies and clears the security context.
	 * @param req HTTP request
	 */
	public static void clearSession(HttpServletRequest req) {
		SecurityContextHolder.clearContext();
		if (req != null) {
			HttpSession session = req.getSession(false);
			if (session != null) {
				session.invalidate();
			}
		}
	}

	/**
	 * Validates a JWT token.
	 * @param secret secret used for generating the token
	 * @param jwt token to validate
	 * @return true if token is valid
	 */
	public static boolean isValidJWToken(String secret, SignedJWT jwt) {
		try {
			if (secret != null && jwt != null) {
				JWSVerifier verifier = new MACVerifier(secret);
				if (jwt.verify(verifier)) {
					Date referenceTime = new Date();
					JWTClaimsSet claims = jwt.getJWTClaimsSet();

					Date expirationTime = claims.getExpirationTime();
					Date notBeforeTime = claims.getNotBeforeTime();
					boolean expired = expirationTime == null || expirationTime.before(referenceTime);
					boolean notYetValid = notBeforeTime == null || notBeforeTime.after(referenceTime);

					return !(expired || notYetValid);
				}
			}
		} catch (JOSEException e) {
			logger.warn(null, e);
		} catch (ParseException ex) {
			logger.warn(null, ex);
		}
		return false;
	}

	/**
	 * Generates a new "super" JWT token for apps.
	 * @param app the app object
	 * @return a new JWT or null
	 */
	public static SignedJWT generateSuperJWToken(App app) {
		return generateJWToken(null, app);
	}

	/**
	 * Generates a new JWT token.
	 * @param user a User object belonging to the app
	 * @param app the app object
	 * @return a new JWT or null
	 */
	public static SignedJWT generateJWToken(User user, App app) {
		if (app != null) {
			try {
				Date now = new Date();
				JWTClaimsSet.Builder claimsSet = new JWTClaimsSet.Builder();
				String userSecret = "";
				claimsSet.issueTime(now);
				claimsSet.expirationTime(new Date(now.getTime() + (app.getTokenValiditySec() * 1000)));
				claimsSet.notBeforeTime(now);
				claimsSet.claim("refresh", getNextRefresh(app.getTokenValiditySec()));
				claimsSet.claim(Config._APPID, app.getId());
				if (user != null) {
					claimsSet.subject(user.getId());
					userSecret = user.getTokenSecret();
				}
				JWSSigner signer = new MACSigner(app.getSecret() + userSecret);
				SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet.build());
				signedJWT.sign(signer);
				return signedJWT;
			} catch (JOSEException e) {
				logger.warn("Unable to sign JWT: {}.", e.getMessage());
			}
		}
		return null;
	}

	/**
	 * Decides when the next token refresh should be.
	 * @param tokenValiditySec token validity period
	 * @return a refresh timestamp to be used by API clients
	 */
	private static long getNextRefresh(long tokenValiditySec) {
		long interval = Config.JWT_REFRESH_INTERVAL_SEC;
		// estimate when the next token refresh should be
		// usually every hour, or halfway until the time it expires
		if (tokenValiditySec < (2 * interval)) {
			interval = (tokenValiditySec / 2);
		}
		return System.currentTimeMillis() + (interval * 1000);
	}

	/**
	 * Return the OAuth app ID and secret key for a given app by reading the app settings, or the config file.
	 * @param app the app in which to look for these keys
	 * @param prefix a service prefix: "fb" for facebook, "tw" for twitter etc. See {@link Config}
	 * @return an array ["app_id", "secret_key"] or ["", ""]
	 */
	public static String[] getOAuthKeysForApp(App app, String prefix) {
		prefix = StringUtils.removeEnd(prefix + "", Config.SEPARATOR);
		String appIdKey = prefix + "_app_id";
		String secretKey = prefix + "_secret";
		String[] keys = new String[]{"", ""};

		if (app != null) {
			Map<String, Object> settings = app.getSettings();
			if (settings.containsKey(appIdKey) && settings.containsKey(secretKey)) {
				keys[0] = settings.get(appIdKey) + "";
				keys[1] = settings.get(secretKey) + "";
			} else if (app.isRootApp()) {
				keys[0] = Config.getConfigParam(appIdKey, "");
				keys[1] = Config.getConfigParam(secretKey, "");
			}
		}
		return keys;
	}

	/**
	 * Returns a map of LDAP configuration properties for a given app,  read from app.settings or config file.
	 * @param app the app in which to look for these keys
	 * @return a map of keys and values
	 */
	public static Map<String, String> getLdapSettingsForApp(App app) {
		Map<String, String> ldapSettings = new HashMap<>();
		if (app != null) {
			ldapSettings.put("security.ldap.server_url", "ldap://localhost:8389/");
			ldapSettings.put("security.ldap.active_directory_domain", "");
			ldapSettings.put("security.ldap.base_dn", "dc=springframework,dc=org");
			ldapSettings.put("security.ldap.bind_dn", "");
			ldapSettings.put("security.ldap.bind_pass", "");
			ldapSettings.put("security.ldap.user_search_base", "");
			ldapSettings.put("security.ldap.user_search_filter", "(cn={0})");
			ldapSettings.put("security.ldap.user_dn_pattern", "uid={0},ou=people");
			ldapSettings.put("security.ldap.password_attribute", "userPassword");
			//ldapSettings.put("security.ldap.compare_passwords", "false"); //don't remove comment
			Map<String, Object> settings = app.getSettings();
			for (Map.Entry<String, String> entry : ldapSettings.entrySet()) {
				if (settings.containsKey(entry.getKey())) {
					entry.setValue(settings.get(entry.getKey()) + "");
				} else if (app.isRootApp()) {
					entry.setValue(Config.getConfigParam(entry.getKey(), entry.getValue()));
				}
			}
		}
		return ldapSettings;
	}

	/**
	 * Checks if account is active.
	 * @param userAuth user authentication object
	 * @param user user object
	 * @param throwException throw or not
	 * @return the authentication object if {@code user.active == true}
	 */
	public static UserAuthentication checkIfActive(UserAuthentication userAuth, User user, boolean throwException) {
		if (userAuth == null || user == null || user.getIdentifier() == null) {
			if (throwException) {
				throw new BadCredentialsException("Bad credentials.");
			} else {
				logger.error("Bad credentials.");
				return null;
			}
		} else if (!user.getActive()) {
			if (throwException) {
				throw new LockedException("Account " + user.getId() + " is locked.");
			} else {
				logger.error("Account {} is locked.", user.getId());
				return null;
			}
		}
		return userAuth;
	}

	/**
	 * Validates the signature of the request.
	 * @param incoming the incoming HTTP request containing a signature
	 * @param secretKey the app's secret key
	 * @return true if the signature is valid
	 */
	public static boolean isValidSignature(HttpServletRequest incoming, String secretKey) {
		if (incoming == null || StringUtils.isBlank(secretKey)) {
			return false;
		}
		String auth = incoming.getHeader(HttpHeaders.AUTHORIZATION);
		String givenSig = StringUtils.substringAfter(auth, "Signature=");
		String sigHeaders = StringUtils.substringBetween(auth, "SignedHeaders=", ",");
		String credential = StringUtils.substringBetween(auth, "Credential=", ",");
		String accessKey = StringUtils.substringBefore(credential, "/");

		if (StringUtils.isBlank(auth)) {
			givenSig = incoming.getParameter("X-Amz-Signature");
			sigHeaders = incoming.getParameter("X-Amz-SignedHeaders");
			credential = incoming.getParameter("X-Amz-Credential");
			accessKey = StringUtils.substringBefore(credential, "/");
		}

		Set<String> headersUsed = new HashSet<>(Arrays.asList(sigHeaders.split(";")));
		Map<String, String> headers = new HashMap<>();
		for (Enumeration<String> e = incoming.getHeaderNames(); e.hasMoreElements();) {
			String head = e.nextElement().toLowerCase();
			if (headersUsed.contains(head)) {
				headers.put(head, incoming.getHeader(head));
			}
		}

		Map<String, String> params = new HashMap<>();
		for (Map.Entry<String, String[]> param : incoming.getParameterMap().entrySet()) {
			params.put(param.getKey(), param.getValue()[0]);
		}

		String path = incoming.getRequestURI();
		String endpoint = StringUtils.removeEndIgnoreCase(incoming.getRequestURL().toString(), path);
		String httpMethod = incoming.getMethod();
		InputStream entity;
		try {
			entity = new BufferedRequestWrapper(incoming).getInputStream();
			if (entity.available() <= 0) {
				entity = null;
			}
		} catch (IOException ex) {
			logger.error(null, ex);
			entity = null;
		}

		Signer signer = new Signer();
		Map<String, String> sig = signer.sign(httpMethod, endpoint, path, headers, params, entity, accessKey, secretKey);

		String auth2 = sig.get(HttpHeaders.AUTHORIZATION);
		String recreatedSig = StringUtils.substringAfter(auth2, "Signature=");

		return StringUtils.equals(givenSig, recreatedSig);
	}
}
