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

import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.User;
import com.erudika.para.core.rest.Signer;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.Utils;
import com.erudika.para.server.security.filters.PasswordlessAuthFilter;
import com.erudika.para.server.security.filters.SAMLAuthFilter;
import com.erudika.para.server.utils.BufferedRequestWrapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
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
	 * Returns the current authenticated {@link App} object.
	 *
	 * @return an App object or null
	 */
	public static App getPrincipalApp() {
		App app = SecurityUtils.getAuthenticatedApp();
		if (app != null) {
			return app;
		}
		// avoid reading app from DB if it's found in the security context
		app = SecurityUtils.getAppFromJWTAuthentication();
		if (app != null) {
			return app;
		}
		app = SecurityUtils.getAppFromLdapAuthentication();
		if (app != null) {
			return app;
		}
		User user = SecurityUtils.getAuthenticatedUser();
		if (user != null) {
			return Para.getDAO().read(Para.getConfig().getRootAppIdentifier(), App.id(user.getAppid()));
		}
		logger.warn("Unauthenticated request - app not found in security context.");
		return null;
	}

	/**
	 * An app can edit itself or delete itself. It can't read, edit, overwrite or delete other apps, unless it is the
	 * root app.
	 *
	 * @param app an app
	 * @param object another object
	 * @return true if app passes the check
	 */
	public static boolean checkImplicitAppPermissions(App app, ParaObject object) {
		if (app != null && object != null) {
			return isNotAnApp(object.getType()) || app.getId().equals(object.getId()) || app.isRootApp();
		}
		return false;
	}

	/**
	 * @param type some type
	 * @return true if type of object is not "app"
	 */
	public static boolean isNotAnApp(String type) {
		return !StringUtils.equals(type, Utils.type(App.class));
	}

	/**
	 * Check if a user can modify an object. If there's no user principal found, this returns true.
	 *
	 * @param app app in context
	 * @param object some object
	 * @return true if user is the owner/creator of the object.
	 */
	public static boolean checkIfUserCanModifyObject(App app, ParaObject object) {
		User user = SecurityUtils.getAuthenticatedUser();
		if (user != null && app != null && object != null) {
			if (app.permissionsContainOwnKeyword(user, object)) {
				return user.canModify(object);
			}
		}
		return true; // skip
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
			if (!StringUtils.isBlank(secret) && jwt != null) {
				JWSVerifier verifier = new MACVerifier(secret);
				if (jwt.verify(verifier)) {
					Date referenceTime = new Date();
					JWTClaimsSet claims = jwt.getJWTClaimsSet();

					Date expirationTime = claims.getExpirationTime();
					Date notBeforeTime = claims.getNotBeforeTime();
					boolean expired = expirationTime == null || expirationTime.before(referenceTime);
					boolean notYetValid = notBeforeTime != null && notBeforeTime.after(referenceTime);

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
					if ("true".equals(Para.getConfig().getSettingForApp(app, "security.one_session_per_user", "true"))) {
						user.resetTokenSecret();
						CoreUtils.getInstance().overwrite(app.getAppIdentifier(), user);
					}
					claimsSet.subject(user.getId());
					claimsSet.claim("idp", user.getIdentityProvider());
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
	 * Short-lived identity token, proving that a Para user has been successfully authenticated against an IDP.
	 * Works with the passwordless auth filter.
	 * @param user user object
	 * @param app app object
	 * @return a signed JWT or null
	 */
	public static SignedJWT generateIdToken(User user, App app) {
		if (app != null && user != null) {
			try {
				Date now = new Date();
				JWTClaimsSet.Builder claimsSet = new JWTClaimsSet.Builder();
				claimsSet.issueTime(now);
				claimsSet.expirationTime(new Date(now.getTime() + (Para.getConfig().idTokenExpiresAfterSec() * 1000)));
				claimsSet.notBeforeTime(now);
				claimsSet.claim(Config._APPID, app.getId());
				claimsSet.claim(Config._NAME, user.getName());
				claimsSet.claim(Config._EMAIL, user.getEmail());
				claimsSet.claim(Config._IDENTIFIER, user.getIdentifier());
				if (StringUtils.startsWithIgnoreCase(user.getPicture(), "http")) {
					claimsSet.claim("picture", user.getPicture());
				} else {
					claimsSet.claim("picture", "https://gravatar.com/avatar/" + Utils.md5(user.getEmail()) +
							"?size=400&d=retro&r=pg");
				}
				claimsSet.subject(user.getId());
				JWSSigner signer = new MACSigner(app.getSecret() + user.getTokenSecret());
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
		long interval = Para.getConfig().jwtRefreshIntervalSec();
		// estimate when the next token refresh should be
		// usually every hour, or halfway until the time it expires
		if (tokenValiditySec < (2 * interval)) {
			interval = (tokenValiditySec / 2);
		}
		return System.currentTimeMillis() + (interval * 1000);
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
				logger.debug("Bad credentials. {}", userAuth);
				return null;
			}
		} else if (!user.getActive()) {
			if (throwException) {
				throw new LockedException("Account " + user.getId() + " (" + user.getAppid() + "/" +
						user.getIdentifier() + ") is locked.");
			} else {
				logger.warn("Account {} ({}/{}) is locked.", user.getId(), user.getAppid(), user.getIdentifier());
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
			if (incoming instanceof BufferedRequestWrapper) {
				entity = incoming.getInputStream();
			} else {
				entity = new BufferedRequestWrapper(incoming).getInputStream();
			}
		} catch (IOException ex) {
			logger.error(null, ex);
			entity = null;
		}

		Signer signer = new Signer();
		Map<String, String> sig = signer.sign(httpMethod, endpoint, path, headers, params, entity, accessKey, secretKey);

		String auth2 = sig.get(HttpHeaders.AUTHORIZATION);
		String recreatedSig = StringUtils.substringAfter(auth2, "Signature=");

		boolean signaturesMatch = StringUtils.equals(givenSig, recreatedSig);
		if (Para.getConfig().debugRequestSignaturesEnabled()) {
			logger.info("Incoming client signature for request {} {}: {} == {} calculated by server, matching: {}",
					httpMethod, path, givenSig, recreatedSig, signaturesMatch);
		}
		return signaturesMatch;
	}

	/**
	 * @param request HTTP request
	 * @return the URL with the correct protocol, read from X-Forwarded-Proto and CloudFront-Forwarded-Proto headers.
	 */
	public static String getRedirectUrl(HttpServletRequest request) {
		String url = request.getRequestURL().toString();
		boolean hasQueryString = false;
		List<String> qs = new LinkedList<>();
		// allow clients to use /oauth2_auth?appid={appid} as an alternative to ?state={appid}
		if (!StringUtils.isBlank(request.getParameter(Config._APPID))) {
			qs.add(Config._APPID + "=" + request.getParameter(Config._APPID));
			hasQueryString = true;
		}
		// allow client servers to run on multiple different public URLs and to override "signin_success/failure"
		if (!StringUtils.isBlank(request.getParameter("host_url"))) {
			qs.add("host_url=" + request.getParameter("host_url"));
			hasQueryString = true;
		}
		if (hasQueryString) {
			url += "?" + String.join("&", qs.toArray(String[]::new));
		}
		if (!StringUtils.isBlank(request.getHeader("X-Forwarded-Proto"))) {
			return request.getHeader("X-Forwarded-Proto") + url.substring(url.indexOf(':'));
		} else if (!StringUtils.isBlank(request.getHeader("CloudFront-Forwarded-Proto"))) {
			return request.getHeader("CloudFront-Forwarded-Proto") + url.substring(url.indexOf(':'));
		}
		return url;
	}

	/**
	 * @param request HTTP request
	 * @return the appid if it's present in either the 'state' or 'appid' query parameters
	 */
	public static String getAppidFromAuthRequest(HttpServletRequest request) {
		String appidFromState = request.getParameter("state");
		String appidFromAppid = request.getParameter(Config._APPID);
		if (StringUtils.isBlank(appidFromState) && StringUtils.isBlank(appidFromAppid)) {
			if (StringUtils.startsWith(request.getRequestURI(), SAMLAuthFilter.SAML_ACTION + "/")) {
				return StringUtils.trimToNull(request.getRequestURI().substring(SAMLAuthFilter.SAML_ACTION.length() + 1));
			} else if (StringUtils.startsWith(request.getRequestURI(), "/" + PasswordlessAuthFilter.PASSWORDLESS_ACTION)) {
				String token = request.getParameter("token"); // JWT
				JWTClaimsSet claims = null;
				try {
					SignedJWT jwt = new SignedJWT(Base64URL.from(StringUtils.substringBefore(token, ".")),
							Base64URL.from(StringUtils.substringBetween(token, ".")),
							Base64URL.from(StringUtils.substringAfterLast(token, ".")));
					claims = jwt.getJWTClaimsSet();
				} catch (ParseException ex) {
					logger.error(null, ex);
				}
				return claims != null ? (String) claims.getClaim(Config._APPID) : null;
			} else {
				return null;
			}
		} else if (!StringUtils.isBlank(appidFromAppid)) {
			return StringUtils.trimToNull(appidFromAppid);
		} else  {
			// allow state parameter to contain appid and an index of "host_url" to return to, i.e. ?state={appid}|2
			return StringUtils.trimToNull(StringUtils.substringBefore(appidFromState, "|"));
		}
	}

	/**
	 * @param app the Para app object
	 * @return a set of host URLs if found in app settings
	 */
	public static Set<String> getHostUrlAliasesForReturn(App app) {
		String hostUrlAliases = (String) app.getSetting("security.hosturl_aliases");
		if (!StringUtils.isBlank(hostUrlAliases)) {
			String[] domains = hostUrlAliases.split("\\s*,\\s*");
			if (domains != null && domains.length > 0) {
				Set<String> list = new LinkedHashSet<>();
				for (String domain : domains) {
					if (StringUtils.startsWithAny(domain, "http://", "https://")) {
						list.add(domain);
					}
				}
				return list;
			}
		}
		return Collections.emptySet();
	}

	/**
	 * @param hostUrlAliases host URL aliases
	 * @param request request
	 * @return a host URL or null
	 */
	public static String getHostUrlFromQueryStringOrStateParam(Set<String> hostUrlAliases, HttpServletRequest request) {
		if (request != null) {
			String hostUrlParam = request.getParameter("host_url");
			if (StringUtils.isBlank(hostUrlParam)) {
				String state = request.getParameter("state");
				if (StringUtils.contains(state, "|") && NumberUtils.isDigits(StringUtils.substringAfterLast(state, "|"))) {
					int index = Math.abs(NumberUtils.toInt(StringUtils.substringAfterLast(state, "|"), 0));
					if (hostUrlAliases != null && index < hostUrlAliases.size() && index >= 0) {
						return hostUrlAliases.toArray(String[]::new)[index];
					} else {
						return null;
					}
				}
			}
			return hostUrlParam;
		}
		return null;
	}
}
