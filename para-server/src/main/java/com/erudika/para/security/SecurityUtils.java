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

import com.erudika.para.core.App;
import com.erudika.para.core.User;
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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
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
	 * Extracts a User object from the security context
	 * @return an authenticated user or null if a user is not authenticated
	 */
	public static User getAuthenticatedUser() {
		return getAuthenticatedUser(SecurityContextHolder.getContext().getAuthentication());
	}

	/**
	 * Extracts a User object from the security context
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
	 * Extracts a App object from the security context
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
	 * Generates a new JWT token.
	 * @param secret secret used for generating the token
	 * @param claimsSet JWT claims
	 * @return a new JWT or null
	 */
	public static SignedJWT generateJWToken(String secret, JWTClaimsSet claimsSet) {
		if (claimsSet != null && secret != null) {
			try {
				JWSSigner signer = new MACSigner(secret);
				SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
				signedJWT.sign(signer);
				return signedJWT;
			} catch (JOSEException e) {
				logger.warn("Unable to sign JWT.", e);
			}
		}
		return null;
	}
}
