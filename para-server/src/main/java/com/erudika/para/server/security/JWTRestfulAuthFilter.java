/*
 * Copyright 2013-2026 Erudika. https://erudika.com
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
import com.erudika.para.core.User;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.Utils;
import com.erudika.para.server.rest.RestUtils;
import com.erudika.para.server.security.filters.AmazonAuthFilter;
import com.erudika.para.server.security.filters.FacebookAuthFilter;
import com.erudika.para.server.security.filters.GenericOAuth2Filter;
import com.erudika.para.server.security.filters.GitHubAuthFilter;
import com.erudika.para.server.security.filters.GoogleAuthFilter;
import com.erudika.para.server.security.filters.LdapAuthFilter;
import com.erudika.para.server.security.filters.LinkedInAuthFilter;
import com.erudika.para.server.security.filters.MicrosoftAuthFilter;
import com.erudika.para.server.security.filters.PasswordAuthFilter;
import com.erudika.para.server.security.filters.PasswordlessAuthFilter;
import com.erudika.para.server.security.filters.SlackAuthFilter;
import com.erudika.para.server.security.filters.TwitterAuthFilter;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.GenericFilterBean;

/**
 * Security filter that intercepts authentication requests (usually coming from the client-side)
 * and validates JWT tokens.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class JWTRestfulAuthFilter extends GenericFilterBean {

	private static final Logger logger = LoggerFactory.getLogger(JWTRestfulAuthFilter.class);

	private final AuthenticationManager authenticationManager;
	private final PathPatternRequestMatcher authenticationRequestMatcher;

	private final FacebookAuthFilter facebookAuth;
	private final GoogleAuthFilter googleAuth;
	private final GitHubAuthFilter githubAuth;
	private final LinkedInAuthFilter linkedinAuth;
	private final TwitterAuthFilter twitterAuth;
	private final MicrosoftAuthFilter microsoftAuth;
	private final SlackAuthFilter slackAuth;
	private final AmazonAuthFilter amazonAuth;
	private final GenericOAuth2Filter oauth2Auth;
	private final LdapAuthFilter ldapAuth;
	private final PasswordAuthFilter passwordAuth;
	private final PasswordlessAuthFilter passwordlessAuth;

	/**
	 * The default filter mapping.
	 */
	public static final String JWT_ACTION = "jwt_auth";

	/**
	 * Default constructor.
	 * @param authenticationManager auth manage
	 * @param facebookAuth filter
	 * @param googleAuth filter
	 * @param githubAuth filter
	 * @param linkedinAuth filter
	 * @param twitterAuth filter
	 * @param microsoftAuth filter
	 * @param slackAuth filter
	 * @param amazonAuth filter
	 * @param oauth2Auth filter
	 * @param ldapAuth filter
	 * @param passwordAuth filter
	 * @param passwordlessAuth filter
	 */
	public JWTRestfulAuthFilter(AuthenticationManager authenticationManager,
			FacebookAuthFilter facebookAuth,
			GoogleAuthFilter googleAuth,
			GitHubAuthFilter githubAuth,
			LinkedInAuthFilter linkedinAuth,
			TwitterAuthFilter twitterAuth,
			MicrosoftAuthFilter microsoftAuth,
			SlackAuthFilter slackAuth,
			AmazonAuthFilter amazonAuth,
			GenericOAuth2Filter oauth2Auth,
			LdapAuthFilter ldapAuth,
			PasswordAuthFilter passwordAuth,
			PasswordlessAuthFilter passwordlessAuth) {
		this.authenticationManager = authenticationManager;
		this.facebookAuth = facebookAuth;
		this.googleAuth = googleAuth;
		this.githubAuth = githubAuth;
		this.linkedinAuth = linkedinAuth;
		this.twitterAuth = twitterAuth;
		this.microsoftAuth = microsoftAuth;
		this.slackAuth = slackAuth;
		this.amazonAuth = amazonAuth;
		this.oauth2Auth = oauth2Auth;
		this.ldapAuth = ldapAuth;
		this.passwordAuth = passwordAuth;
		this.passwordlessAuth = passwordlessAuth;
		this.authenticationRequestMatcher = PathPatternRequestMatcher.withDefaults().matcher("/" + JWT_ACTION);
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;
		if (authenticationRequestMatcher.matches(request)) {
			if (HttpMethod.POST.matches(request.getMethod())) {
				newTokenHandler(request, response);
			} else if (HttpMethod.GET.matches(request.getMethod())) {
				refreshTokenHandler(request, response);
			} else if (HttpMethod.DELETE.matches(request.getMethod())) {
				revokeAllTokensHandler(request, response);
			}
			return;
		} else if (RestRequestMatcher.INSTANCE_STRICT.matches(request) &&
				SecurityContextHolder.getContext().getAuthentication() == null) {
			try {
				// validate token if present
				JWTAuthentication jwtAuth = getJWTfromRequest(request);
				if (jwtAuth != null) {
					Authentication auth = authenticationManager.authenticate(jwtAuth);
					validateDelegatedTokenIfNecessary(jwtAuth);
					// success!
					SecurityContextHolder.getContext().setAuthentication(auth);
				} else {
					response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
				}
			} catch (AuthenticationException authenticationException) {
				response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
				logger.debug("AuthenticationManager rejected JWT Authentication.", authenticationException);
			}
		}

		chain.doFilter(request, response);
	}

	@SuppressWarnings("unchecked")
	private boolean newTokenHandler(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		ResponseEntity<?> res = RestUtils.getEntity(request.getInputStream(), Map.class);
		if (!res.getStatusCode().is2xxSuccessful()) {
			RestUtils.returnStatusResponse(response, res.getStatusCode().value(),
					Optional.ofNullable(res.getBody()).orElse("").toString());
			return false;
		}
		Map<String, Object> entity = (Map<String, Object>) res.getBody();
		String provider = (String) entity.get("provider");
		String appid = (String) entity.get(Config._APPID);
		String token = (String) entity.get("token");

		if (provider != null && appid != null && token != null) {
			// don't allow clients to create users on root app
			if (!App.isRoot(appid)) {
				App app = Para.getDAO().read(App.id(appid));
				if (app != null) {
					UserAuthentication userAuth = getOrCreateUser(app, provider, token);
					User user = SecurityUtils.getAuthenticatedUser(userAuth);
					if (user != null) {
						// issue token
						SignedJWT newJWT = SecurityUtils.generateJWToken(user, app);
						if (newJWT != null) {
							succesHandler(response, user, newJWT);
							return true;
						}
					} else {
						RestUtils.returnStatusResponse(response, HttpServletResponse.SC_BAD_REQUEST,
								"Failed to authenticate user with '" + provider + "'. Check if user is active.");
						return false;
					}
				} else {
					RestUtils.returnStatusResponse(response, HttpServletResponse.SC_BAD_REQUEST,
							"User belongs to app '" + appid + "' which does not exist. " +
									(App.isRoot(appid) ? "Make sure you have initialized Para." : ""));
					return false;
				}
			} else {
				RestUtils.returnStatusResponse(response, HttpServletResponse.SC_FORBIDDEN,
							"Can't authenticate user with app '" + appid + "' using provider '" + provider + "'. "
									+ "Reason: clients aren't allowed to access root app.");
					return false;
			}
		}
		RestUtils.returnStatusResponse(response, HttpServletResponse.SC_BAD_REQUEST,
				"Some of the required query parameters 'provider', 'appid', 'token', are missing.");
		return false;
	}

	private boolean refreshTokenHandler(HttpServletRequest request, HttpServletResponse response) {
		JWTAuthentication jwtAuth = getJWTfromRequest(request);
		if (jwtAuth != null) {
			try {
				User user = SecurityUtils.getAuthenticatedUser(jwtAuth);
				if (user != null) {
					// check and reissue token
					jwtAuth = (JWTAuthentication) authenticationManager.authenticate(jwtAuth);
					validateDelegatedTokenIfNecessary(jwtAuth);
					if (jwtAuth != null && jwtAuth.getApp() != null) {
						SignedJWT newToken = SecurityUtils.generateJWToken(user, jwtAuth.getApp());
						if (newToken != null) {
							succesHandler(response, user, newToken);
							return true;
						}
					}
				}
			} catch (Exception ex) {
				logger.debug(null, ex);
			}
		}
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
		RestUtils.returnStatusResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "User must reauthenticate.");
		return false;
	}

	private boolean revokeAllTokensHandler(HttpServletRequest request, HttpServletResponse response) {
		JWTAuthentication jwtAuth = getJWTfromRequest(request);
		if (jwtAuth != null) {
			try {
				User user = SecurityUtils.getAuthenticatedUser(jwtAuth);
				if (user != null) {
					jwtAuth = (JWTAuthentication) authenticationManager.authenticate(jwtAuth);
					validateDelegatedTokenIfNecessary(jwtAuth);
					if (jwtAuth != null && jwtAuth.getApp() != null) {
						user.resetTokenSecret();
						CoreUtils.getInstance().overwrite(jwtAuth.getApp().getAppIdentifier(), user);
						RestUtils.returnStatusResponse(response, HttpServletResponse.SC_OK,
								Utils.formatMessage("All tokens revoked for user {0}!", user.getId()));
						return true;
					}
				}
			} catch (Exception ex) {
				logger.debug(null, ex);
			}
		}
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		RestUtils.returnStatusResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
				"Invalid or expired token.");
		return false;
	}

	private void succesHandler(HttpServletResponse response, User user, final SignedJWT token) {
		if (user != null && token != null) {
			Map<String, Object> result = new HashMap<>();
			try {
				HashMap<String, Object> jwt = new HashMap<>();
				jwt.put("access_token", token.serialize());
				jwt.put("refresh", token.getJWTClaimsSet().getLongClaim("refresh"));
				jwt.put("expires", token.getJWTClaimsSet().getExpirationTime().getTime());
				result.put("jwt", jwt);
				result.put("user", user);
			} catch (ParseException ex) {
				logger.info("Unable to parse JWT.", ex);
				RestUtils.returnStatusResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Bad token.");
			}
			RestUtils.returnObjectResponse(response, result);
		} else {
			RestUtils.returnStatusResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Null token.");
		}
	}

	private JWTAuthentication getJWTfromRequest(HttpServletRequest request) {
		String token = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (token == null) {
			token = request.getParameter(HttpHeaders.AUTHORIZATION);
		}
		if (!StringUtils.isBlank(token) && token.contains("Bearer")) {
			try {
				SignedJWT jwt = SignedJWT.parse(token.substring(6).trim());
				String userid = jwt.getJWTClaimsSet().getSubject();
				String appid = (String) jwt.getJWTClaimsSet().getClaim(Config._APPID);
				App app = Para.getDAO().read(App.id(appid));
				if (app != null) {
					User user = Para.getDAO().read(app.getAppIdentifier(), userid);
					if (user != null) {
						// standard user JWT auth, restricted access through resource permissions
						return new JWTAuthentication(new AuthenticatedUserDetails(user)).withJWT(jwt).withApp(app);
					} else {
						// "super token" - subject is authenticated as app, full access
						return new JWTAuthentication(null).withJWT(jwt).withApp(app);
					}
				}
			} catch (ParseException e) {
				logger.debug("Unable to parse JWT.", e);
			}
		}
		return null;
	}

	private UserAuthentication getOrCreateUser(App app, String identityProvider, String accessToken)
			throws IOException {
		if ("facebook".equalsIgnoreCase(identityProvider)) {
			return facebookAuth.getOrCreateUser(app, accessToken);
		} else if ("google".equalsIgnoreCase(identityProvider)) {
			return googleAuth.getOrCreateUser(app, accessToken);
		} else if ("github".equalsIgnoreCase(identityProvider)) {
			return githubAuth.getOrCreateUser(app, accessToken);
		} else if ("linkedin".equalsIgnoreCase(identityProvider)) {
			return linkedinAuth.getOrCreateUser(app, accessToken);
		} else if ("twitter".equalsIgnoreCase(identityProvider)) {
			return twitterAuth.getOrCreateUser(app, accessToken);
		} else if ("microsoft".equalsIgnoreCase(identityProvider)) {
			return microsoftAuth.getOrCreateUser(app, accessToken);
		} else if ("slack".equalsIgnoreCase(identityProvider)) {
			return slackAuth.getOrCreateUser(app, accessToken);
		} else if ("amazon".equalsIgnoreCase(identityProvider)) {
			return amazonAuth.getOrCreateUser(app, accessToken);
		} else if ("oauth2".equalsIgnoreCase(identityProvider)) {
			return oauth2Auth.getOrCreateUser(app, accessToken);
		} else if ("oauth2second".equalsIgnoreCase(identityProvider)) {
			return oauth2Auth.getOrCreateUser(app, accessToken, "second");
		} else if ("oauth2third".equalsIgnoreCase(identityProvider)) {
			return oauth2Auth.getOrCreateUser(app, accessToken, "third");
		} else if ("ldap".equalsIgnoreCase(identityProvider)) {
			return ldapAuth.getOrCreateUser(app, accessToken);
		} else if ("passwordless".equalsIgnoreCase(identityProvider)) {
			return passwordlessAuth.getOrCreateUser(app, accessToken);
		} else if (Strings.CI.equalsAny(identityProvider, "password", "generic")) {
			try {
				return passwordAuth.getOrCreateUser(app, accessToken);
			} catch (Exception e) {
				logger.error("Failed to get authenticate user with password via JWT Auth filter: " + e.getMessage());
			}
		}
		return null;
	}

	private void validateDelegatedTokenIfNecessary(JWTAuthentication jwt) throws AuthenticationException, IOException {
		User user = SecurityUtils.getAuthenticatedUser(jwt);
		if (user != null && jwt != null) {
			String identityProvider = null;
			try {
				identityProvider = (String) jwt.getJwt().getJWTClaimsSet().getClaim("idp");
			} catch (ParseException ex) {
				logger.error(null, ex);
			}
			if (StringUtils.isBlank(identityProvider)) {
				identityProvider = user.getIdentityProvider(); // less reliable, because user identifier can change
			}
			App app = jwt.getApp();
			// Send user password (access token) to IDP for validation:
			// - if token delegation is enabled AND
			// - if the generic OAuth 2 filter is used
			if ("oauth2".equalsIgnoreCase(identityProvider) &&
					oauth2Auth.isAccessTokenDelegationEnabled(app, user) &&
					!oauth2Auth.isValidAccessToken(app, user)) {
				logger.debug("The access token delegated from '" + identityProvider + "' is invalid for " +
						user.getAppid() + "/" + user.getId());
				throw new AuthenticationServiceException("The access token delegated from '" +
						identityProvider + "' is invalid.");
			}
			// authentication success
		}
	}
}
