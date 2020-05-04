/*
 * Copyright 2013-2020 Erudika. https://erudika.com
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

import com.erudika.para.security.filters.PasswordAuthFilter;
import com.erudika.para.security.filters.GoogleAuthFilter;
import com.erudika.para.security.filters.TwitterAuthFilter;
import com.erudika.para.security.filters.MicrosoftAuthFilter;
import com.erudika.para.security.filters.GitHubAuthFilter;
import com.erudika.para.security.filters.LinkedInAuthFilter;
import com.erudika.para.security.filters.GenericOAuth2Filter;
import com.erudika.para.security.filters.FacebookAuthFilter;
import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.User;
import com.erudika.para.rest.RestUtils;
import com.erudika.para.security.filters.AmazonAuthFilter;
import com.erudika.para.security.filters.LdapAuthFilter;
import com.erudika.para.security.filters.PasswordlessAuthFilter;
import com.erudika.para.security.filters.SlackAuthFilter;
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
import org.springframework.security.authentication.AuthenticationServiceException;
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
	private SlackAuthFilter slackAuth;
	private AmazonAuthFilter amazonAuth;
	private GenericOAuth2Filter oauth2Auth;
	private LdapAuthFilter ldapAuth;
	private PasswordAuthFilter passwordAuth;
	private PasswordlessAuthFilter passwordlessAuth;

	/**
	 * The default filter mapping.
	 */
	public static final String JWT_ACTION = "jwt_auth";

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl filter URL
	 */
	public JWTRestfulAuthFilter(String defaultFilterProcessesUrl) {
		setFilterProcessesUrl(defaultFilterProcessesUrl);
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
		Response res = RestUtils.getEntity(request.getInputStream(), Map.class);
		if (res.getStatusInfo() != Response.Status.OK) {
			RestUtils.returnStatusResponse(response, res.getStatus(), res.getEntity().toString());
			return false;
		}
		Map<String, Object> entity = (Map<String, Object>) res.getEntity();
		String provider = (String) entity.get("provider");
		String appid = (String) entity.get(Config._APPID);
		String token = (String) entity.get("token");

		if (provider != null && appid != null && token != null) {
			// don't allow clients to create users on root app unless this is explicitly configured
			if (!App.isRoot(appid) || Config.getConfigBoolean("clients_can_access_root_app", false)) {
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
							"User belongs to an app that does not exist.");
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
		} else if ("password".equalsIgnoreCase(identityProvider) || "generic".equalsIgnoreCase(identityProvider)) {
			return passwordAuth.getOrCreateUser(app, accessToken);
		}
		return null;
	}

	private void setFilterProcessesUrl(String filterProcessesUrl) {
		this.authenticationRequestMatcher = new AntPathRequestMatcher(filterProcessesUrl);
	}

	/**
	 * @return auth manager
	 */
	protected AuthenticationManager getAuthenticationManager() {
		return authenticationManager;
	}

	/**
	 * @param authenticationManager auth manager
	 */
	public void setAuthenticationManager(AuthenticationManager authenticationManager) {
		this.authenticationManager = authenticationManager;
	}

	@Override
	public void afterPropertiesSet() {
		Assert.notNull(authenticationManager, "authenticationManager cannot be null");
	}

	/**
	 * @return auth filter
	 */
	public FacebookAuthFilter getFacebookAuth() {
		return facebookAuth;
	}

	/**
	 * @param facebookAuth auth filter
	 */
	@Inject
	public void setFacebookAuth(FacebookAuthFilter facebookAuth) {
		this.facebookAuth = facebookAuth;
	}

	/**
	 * @return auth filter
	 */
	public GoogleAuthFilter getGoogleAuth() {
		return googleAuth;
	}

	/**
	 * @param googleAuth auth filter
	 */
	@Inject
	public void setGoogleAuth(GoogleAuthFilter googleAuth) {
		this.googleAuth = googleAuth;
	}

	/**
	 * @return auth filter
	 */
	public GitHubAuthFilter getGithubAuth() {
		return githubAuth;
	}

	/**
	 * @param githubAuth auth filter
	 */
	@Inject
	public void setGithubAuth(GitHubAuthFilter githubAuth) {
		this.githubAuth = githubAuth;
	}

	/**
	 * @return auth filter
	 */
	public LinkedInAuthFilter getLinkedinAuth() {
		return linkedinAuth;
	}

	/**
	 * @param linkedinAuth auth filter
	 */
	@Inject
	public void setLinkedinAuth(LinkedInAuthFilter linkedinAuth) {
		this.linkedinAuth = linkedinAuth;
	}

	/**
	 * @return auth filter
	 */
	public TwitterAuthFilter getTwitterAuth() {
		return twitterAuth;
	}

	/**
	 * @param twitterAuth auth filter
	 */
	@Inject
	public void setTwitterAuth(TwitterAuthFilter twitterAuth) {
		this.twitterAuth = twitterAuth;
	}

	/**
	 * @return auth filter
	 */
	public MicrosoftAuthFilter getMicrosoftAuth() {
		return microsoftAuth;
	}

	/**
	 * @param microsoftAuth auth filter
	 */
	@Inject
	public void setMicrosoftAuth(MicrosoftAuthFilter microsoftAuth) {
		this.microsoftAuth = microsoftAuth;
	}

	/**
	 * @return auth filter
	 */
	public SlackAuthFilter getSlackAuth() {
		return slackAuth;
	}

	/**
	 * @param slackAuth auth filter
	 */
	@Inject
	public void setSlackAuth(SlackAuthFilter slackAuth) {
		this.slackAuth = slackAuth;
	}

	/**
	 * @return auth filter
	 */
	public AmazonAuthFilter getAmazonAuth() {
		return amazonAuth;
	}

	/**
	 * @param amazonAuth auth filter
	 */
	@Inject
	public void setAmazonAuth(AmazonAuthFilter amazonAuth) {
		this.amazonAuth = amazonAuth;
	}

	/**
	 * @return auth filter
	 */
	public GenericOAuth2Filter getGenericOAuth2Auth() {
		return oauth2Auth;
	}

	/**
	 * @param oauthAuth auth filter
	 */
	@Inject
	public void setGenericOAuth2Auth(GenericOAuth2Filter oauthAuth) {
		this.oauth2Auth = oauthAuth;
	}

	/**
	 * @return auth filter
	 */
	public LdapAuthFilter getLdapAuth() {
		return ldapAuth;
	}

	/**
	 * @param ldapAuth auth filter
	 */
	public void setLdapAuth(LdapAuthFilter ldapAuth) {
		this.ldapAuth = ldapAuth;
	}

	/**
	 * @return auth filter
	 */
	public PasswordAuthFilter getPasswordAuth() {
		return passwordAuth;
	}

	/**
	 * @param passwordAuth auth filter
	 */
	@Inject
	public void setPasswordAuth(PasswordAuthFilter passwordAuth) {
		this.passwordAuth = passwordAuth;
	}

	/**
	 * @return auth filter
	 */
	public PasswordlessAuthFilter getPasswordlessAuth() {
		return passwordlessAuth;
	}

	/**
	 * @param passwordlessAuth auth filter
	 */
	@Inject
	public void setPasswordlessAuth(PasswordlessAuthFilter passwordlessAuth) {
		this.passwordlessAuth = passwordlessAuth;
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
