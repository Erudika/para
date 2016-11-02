/*
 * Copyright 2013-2016 Erudika. https://erudika.com
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

import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.core.User;
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
import java.text.ParseException;
import java.util.Date;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
				claimsSet.claim("appid", app.getId());
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
	 * Return the OAuth appid and secret key for a given app or default to the ones in the config file.
	 * @param appid the app in which to look for these keys
	 * @param prefix a service prefix: "fb" for facebook, "tw" for twitter etc. See {@link Config}
	 * @param request an auth request
	 * @return an array ["app_id", "secret_key"] or ["", ""]
	 */
	public static String[] getCustomAuthSettings(String appid, String prefix, HttpServletRequest request) {
		prefix = StringUtils.removeEnd(prefix + "", Config.SEPARATOR);
		String appIdKey = prefix + "_app_id";
		String secretKey = prefix + "_secret";
		String authAppId = Config.getConfigParam(appIdKey, "");
		String authSecret = Config.getConfigParam(secretKey, "");
		String[] keys = new String[]{authAppId, authSecret};

		if (appid != null) {
			App app = new App(appid);
			if (!StringUtils.isBlank(appid) && !app.isRootApp()) {
				app = Para.getDAO().read(app.getId());
				if (app != null) {
					Map<String, Object> settings = app.getSettings();
					if (settings.containsKey(appIdKey) && settings.containsKey(secretKey)) {
						authAppId = settings.get(appIdKey) + "";
						authSecret = settings.get(secretKey) + "";
						keys[0] = authAppId;
						keys[1] = authSecret;
					}
					// why not also set these while we have the custom settings loaded
					if (request != null) {
						if (settings.containsKey("signin_success")) {
							request.setAttribute(Config.AUTH_SIGNIN_SUCCESS_ATTR, settings.get("signin_success"));
						}
						if (settings.containsKey("signin_failure")) {
							request.setAttribute(Config.AUTH_SIGNIN_FAILURE_ATTR, settings.get("signin_failure"));
						}
					}
				}
			}
		}
		return keys;
	}
}
