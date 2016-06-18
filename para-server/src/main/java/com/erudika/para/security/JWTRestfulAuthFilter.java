/*
 * Copyright 2013-2016 Erudika. http://erudika.com
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
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.User;
import com.erudika.para.rest.RestUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.GenericFilterBean;

/**
 * Security filter that intercepts authentication requests (usually coming from the client-side)
 * and validates JWT tokens.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class JWTRestfulAuthFilter extends GenericFilterBean {

	private AuthenticationManager authenticationManager;
	private AntPathRequestMatcher authenticationRequestMatcher;

	private FacebookAuthFilter facebookAuth;
	private GoogleAuthFilter googleAuth;
	private GitHubAuthFilter githubAuth;
	private LinkedInAuthFilter linkedinAuth;
	private TwitterAuthFilter twitterAuth;
	private MicrosoftAuthFilter microsoftAuth;
	private PasswordAuthFilter passwordAuth;

	/**
	 * The default filter mapping
	 */
	public static final String JWT_ACTION = "jwt_auth";

	public JWTRestfulAuthFilter(String defaultFilterProcessesUrl) {
		Assert.hasLength(defaultFilterProcessesUrl);
		setFilterProcessesUrl(defaultFilterProcessesUrl);
	}

	private void setFilterProcessesUrl(String filterProcessesUrl) {
		this.authenticationRequestMatcher = new AntPathRequestMatcher(filterProcessesUrl);
	}

	protected AuthenticationManager getAuthenticationManager() {
		return authenticationManager;
	}

	public void setAuthenticationManager(AuthenticationManager authenticationManager) {
		this.authenticationManager = authenticationManager;
	}

	@Override
	public void afterPropertiesSet() {
		Assert.notNull(authenticationManager, "authenticationManager cannot be null");
	}

	public FacebookAuthFilter getFacebookAuth() {
		return facebookAuth;
	}

	@Inject
	public void setFacebookAuth(FacebookAuthFilter facebookAuth) {
		this.facebookAuth = facebookAuth;
	}

	public GoogleAuthFilter getGoogleAuth() {
		return googleAuth;
	}

	@Inject
	public void setGoogleAuth(GoogleAuthFilter googleAuth) {
		this.googleAuth = googleAuth;
	}

	public GitHubAuthFilter getGithubAuth() {
		return githubAuth;
	}

	@Inject
	public void setGithubAuth(GitHubAuthFilter githubAuth) {
		this.githubAuth = githubAuth;
	}

	public LinkedInAuthFilter getLinkedinAuth() {
		return linkedinAuth;
	}

	@Inject
	public void setLinkedinAuth(LinkedInAuthFilter linkedinAuth) {
		this.linkedinAuth = linkedinAuth;
	}

	public TwitterAuthFilter getTwitterAuth() {
		return twitterAuth;
	}

	@Inject
	public void setTwitterAuth(TwitterAuthFilter twitterAuth) {
		this.twitterAuth = twitterAuth;
	}

	public MicrosoftAuthFilter getMicrosoftAuth() {
		return microsoftAuth;
	}

	@Inject
	public void setMicrosoftAuth(MicrosoftAuthFilter microsoftAuth) {
		this.microsoftAuth = microsoftAuth;
	}

	public PasswordAuthFilter getPasswordAuth() {
		return passwordAuth;
	}

	@Inject
	public void setPasswordAuth(PasswordAuthFilter passwordAuth) {
		this.passwordAuth = passwordAuth;
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;
		if (authenticationRequestMatcher.matches(request)) {
			if (HttpMethod.POST.equals(request.getMethod())) {
				newTokenHandler(request, response);
			} else if (HttpMethod.GET.equals(request.getMethod())) {
				refreshTokenHandler(request, response);
			} else if (HttpMethod.DELETE.equals(request.getMethod())) {
				revokeAllTokensHandler(request, response);
			}
			return;
		} else if (RestRequestMatcher.INSTANCE_STRICT.matches(request) &&
				SecurityContextHolder.getContext().getAuthentication() == null) {
			try {
				// validate token if present
				JWTAuthentication jwt = getJWTfromRequest(request);
				if (jwt != null) {
					Authentication auth = authenticationManager.authenticate(jwt);
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
		Response res = RestUtils.getEntity(request.getInputStream(), Map.class);
		if (res.getStatusInfo() != Response.Status.OK) {
			RestUtils.returnStatusResponse(response, res.getStatus(), res.getEntity().toString());
			return false;
		}
		Map<String, Object> entity = (Map<String, Object>) res.getEntity();
		String provider = (String) entity.get("provider");
		String appid = (String) entity.get("appid");
		String token = (String) entity.get("token");

		if (provider != null && appid != null && token != null) {
			App app = new App(appid);
			// don't allow clients to create users on root app unless this is explicitly configured
			if (!app.isRootApp() || Config.getConfigBoolean("clients_can_access_root_app", false)) {
				UserAuthentication userAuth = getOrCreateUser(app.getAppIdentifier(), provider, token);
				User user = SecurityUtils.getAuthenticatedUser(userAuth);
				if (user != null) {
					app = Para.getDAO().read(app.getId());
					if (app != null) {
						// issue token
						SignedJWT newJWT = SecurityUtils.generateJWToken(user, app);
						if (newJWT != null) {
							succesHandler(response, user, newJWT);
							return true;
						}
					} else {
						RestUtils.returnStatusResponse(response, HttpServletResponse.SC_BAD_REQUEST,
								"User belongs to an app that does not exist.");
						return false;
					}
				} else {
					RestUtils.returnStatusResponse(response, HttpServletResponse.SC_BAD_REQUEST,
							"Failed to authenticate user with " + provider);
					return false;
				}
			} else {
				RestUtils.returnStatusResponse(response, HttpServletResponse.SC_FORBIDDEN,
							"Can't authenticate user with app '" + app.getId() + "'");
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
					if (jwtAuth != null && jwtAuth.getApp() != null) {
						SignedJWT newToken = SecurityUtils.generateJWToken(user, jwtAuth.getApp());
						if (newToken != null) {
							succesHandler(response, user, newToken);
							return true;
						}
					}
				}
			} catch (Exception ex) {
				logger.debug(ex);
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
					if (jwtAuth != null && jwtAuth.getApp() != null) {
						user.resetTokenSecret();
						CoreUtils.getInstance().overwrite(jwtAuth.getApp().getAppIdentifier(), user);
						RestUtils.returnStatusResponse(response, HttpServletResponse.SC_OK,
								Utils.formatMessage("All tokens revoked for user {0}!", user.getId()));
						return true;
					}
				}
			} catch (Exception ex) {
				logger.debug(ex);
			}
		}
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		RestUtils.returnStatusResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
				"Invalid or expired token.");
		return false;
	}

	private void succesHandler(HttpServletResponse response, User user, final SignedJWT token) {
		if (user != null && token != null) {
			Map<String, Object> result = new HashMap<String, Object>();
			result.put("user", user);
			result.put("jwt", new HashMap<String, Object>() { {
					try {
						put("access_token", token.serialize());
						put("refresh", token.getJWTClaimsSet().getLongClaim("refresh"));
						put("expires", token.getJWTClaimsSet().getExpirationTime().getTime());
					} catch (ParseException ex) {
						logger.info("Unable to parse JWT.", ex);
					}
			} });
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
				String appid = (String) jwt.getJWTClaimsSet().getClaim("appid");
				App app = new App(appid);
				User user = Para.getDAO().read(app.getAppIdentifier(), userid);
				app = Para.getDAO().read(app.getId());
				if (app != null) {
					if (user != null) {
						return new JWTAuthentication(new AuthenticatedUserDetails(user)).withJWT(jwt).withApp(app);
					} else {
						return new JWTAuthentication(null).withJWT(jwt).withApp(app);
					}
				}
			} catch (ParseException e) {
				logger.debug("Unable to parse JWT.", e);
			}
		}
		return null;
	}

	private UserAuthentication getOrCreateUser(String appid, String identityProvider, String accessToken)
			throws IOException {
		if ("facebook".equalsIgnoreCase(identityProvider)) {
			return facebookAuth.getOrCreateUser(appid, accessToken);
		} else if ("google".equalsIgnoreCase(identityProvider)) {
			return googleAuth.getOrCreateUser(appid, accessToken);
		} else if ("github".equalsIgnoreCase(identityProvider)) {
			return githubAuth.getOrCreateUser(appid, accessToken);
		} else if ("linkedin".equalsIgnoreCase(identityProvider)) {
			return linkedinAuth.getOrCreateUser(appid, accessToken);
		} else if ("twitter".equalsIgnoreCase(identityProvider)) {
			return twitterAuth.getOrCreateUser(appid, accessToken);
		} else if ("microsoft".equalsIgnoreCase(identityProvider)) {
			return microsoftAuth.getOrCreateUser(appid, accessToken);
		} else if ("password".equalsIgnoreCase(identityProvider)) {
			return passwordAuth.getOrCreateUser(appid, accessToken);
		}
		return null;
	}
}
