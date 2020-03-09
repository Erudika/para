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
package com.erudika.para.security.filters;

import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.User;
import com.erudika.para.security.AuthenticatedUserDetails;
import com.erudika.para.security.SecurityUtils;
import com.erudika.para.security.UserAuthentication;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

/**
 * A filter that handles authentication requests to a generic OAuth 2.0 identity server.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class GenericOAuth2Filter extends AbstractAuthenticationProcessingFilter {

	private static final Logger LOG = LoggerFactory.getLogger(GenericOAuth2Filter.class);
	private final CloseableHttpClient httpclient;
	private final ObjectReader jreader;
	private static final String PAYLOAD = "code={0}&redirect_uri={1}"
			+ "&scope={2}&client_id={3}&client_secret={4}&grant_type=authorization_code";
	private static final String REFRESH_PAYLOAD = "refresh_token={0}"
			+ "&scope={1}&client_id={2}&client_secret={3}&grant_type=refresh_token";

	/**
	 * The default filter mapping.
	 */
	public static final String OAUTH2_ACTION = "oauth2_auth";
	/**
	 * Second filter mapping.
	 */
	public static final String OAUTH2_SECOND_ACTION = "oauth2second_auth";
	/**
	 * Third filter mapping.
	 */
	public static final String OAUTH2_THIRD_ACTION = "oauth2third_auth";

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public GenericOAuth2Filter(final String defaultFilterProcessesUrl) {
		super(defaultFilterProcessesUrl);
		this.jreader = ParaObjectUtils.getJsonReader(Map.class);
		int timeout = 30 * 1000;
		this.httpclient = HttpClientBuilder.create().
				setConnectionReuseStrategy(new NoConnectionReuseStrategy()).
				setDefaultRequestConfig(RequestConfig.custom().
						setConnectTimeout(timeout).
						setConnectionRequestTimeout(timeout).
						setCookieSpec(CookieSpecs.STANDARD).
						setSocketTimeout(timeout).
						build()).
				build();
	}

	/**
	 * Handles an authentication request.
	 * @param request HTTP request
	 * @param response HTTP response
	 * @return an authentication object that contains the principal object if successful.
	 * @throws IOException ex
	 */
	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		final String requestURI = request.getRequestURI();
		UserAuthentication userAuth = null;
		boolean isSecond = requestURI.endsWith(OAUTH2_SECOND_ACTION);
		boolean isThird = requestURI.endsWith(OAUTH2_THIRD_ACTION);

		if (requestURI.endsWith(OAUTH2_ACTION) || isSecond || isThird) {
			String alias = isThird ? "third" : (isSecond ? "second" : "");
			String authCode = request.getParameter("code");
			if (!StringUtils.isBlank(authCode)) {
				String appid = SecurityUtils.getAppidFromAuthRequest(request);
				App app = Para.getDAO().read(App.id(appid == null ? Config.getRootAppIdentifier() : appid));

				Map<String, Object> token = tokenRequest(app, authCode, SecurityUtils.getRedirectUrl(request), alias);
				if (token != null) {
					if (token.containsKey("access_token")) {
						userAuth = getOrCreateUser(app, token.get("access_token") +
								Config.SEPARATOR + token.get("refresh_token"));
					} else {
						LOG.error("OAuth 2.0 token request failed with response " + token);
					}
				}
			}
		}

		return SecurityUtils.checkIfActive(userAuth, SecurityUtils.getAuthenticatedUser(userAuth), true);
	}

	/**
	 * Calls an external API to get the user profile using a given access token.
	 * @param app the app where the user will be created, use null for root app
	 * @param accessToken access token
	 * @return {@link UserAuthentication} object or null if something went wrong
	 * @throws IOException ex if connection fails
	 */
	public UserAuthentication getOrCreateUser(App app, String accessToken) throws IOException {
		return getOrCreateUser(app, accessToken, null);
	}

	/**
	 * Calls an external API to get the user profile using a given access token.
	 * @param app the app where the user will be created, use null for root app
	 * @param accessToken access token
	 * @param alias alias
	 * @return {@link UserAuthentication} object or null if something went wrong
	 * @throws IOException ex if connection fails
	 */
	public UserAuthentication getOrCreateUser(App app, String accessToken, String alias) throws IOException {
		UserAuthentication userAuth = null;
		User user = new User();
		if (accessToken != null) {
			String[] tokens = accessToken.split(Config.SEPARATOR);
			String refreshToken = null;
			if (tokens.length > 1) {
				accessToken = tokens[0];
				refreshToken = tokens[1];
			}

			boolean tokenDelegationEnabled = isAccessTokenDelegationEnabled(app, alias);
			Map<String, Object> profile = fetchProfileFromIDP(app, accessToken, alias);

			String accountIdParam = SecurityUtils.getSettingForApp(app, configKey("parameters.id", alias), "sub");
			String pictureParam = SecurityUtils.getSettingForApp(app, configKey("parameters.picture", alias), "picture");
			String emailDomain = SecurityUtils.getSettingForApp(app, configKey("domain", alias), "paraio.com");
			String emailParam = SecurityUtils.getSettingForApp(app, configKey("parameters.email", alias), "email");
			String nameParam = SecurityUtils.getSettingForApp(app, configKey("parameters.name", alias), "name");

			if (profile != null && profile.containsKey(accountIdParam)) {
				String oauthAccountId = (String) profile.get(accountIdParam);
				String pic = (String) profile.get(pictureParam);
				String email = (String) profile.get(emailParam);
				String name = (String) profile.get(nameParam);

				if (StringUtils.isBlank(email)) {
					if (!StringUtils.isBlank(emailDomain)) {
						email = oauthAccountId.concat("@").concat(emailDomain);
					} else {
						LOG.warn("Blank email attribute for OAuth2 user '{}'.", oauthAccountId);
						email = oauthAccountId + "@scoold.com";
					}
				}

				user.setAppid(getAppid(app));
				user.setIdentifier(oauthPrefix(alias).concat(oauthAccountId));
				user.setEmail(email);
				user = User.readUserForIdentifier(user);
				if (user == null) {
					//user is new
					user = new User();
					user.setActive(true);
					user.setAppid(getAppid(app));
					user.setEmail(email);
					user.setName(StringUtils.isBlank(name) ? "No Name" : name);
					user.setPassword(Utils.generateSecurityToken());
					if (tokenDelegationEnabled) {
						user.setIdpAccessToken(accessToken);
						user.setIdpRefreshToken(refreshToken);
					}
					user.setPicture(getPicture(pic));
					user.setIdentifier(oauthPrefix(alias).concat(oauthAccountId));
					String id = user.create();
					if (id == null) {
						throw new AuthenticationServiceException("Authentication failed: cannot create new user.");
					}
				} else {
					if (updateUserInfo(user, pic, email, name, accessToken, refreshToken, tokenDelegationEnabled)) {
						user.update();
					}
				}
				userAuth = new UserAuthentication(new AuthenticatedUserDetails(user));
			} else {
				LOG.error("Authentication was successful but OAuth 2 parameter names not configured properly - "
						+ "'id' property not found in user data (data." + accountIdParam + " = null). "
						+ "The names available are: " + (profile != null ? profile.keySet() : null));
			}
		}
		return SecurityUtils.checkIfActive(userAuth, user, false);
	}

	private boolean updateUserInfo(User user, String pic, String email, String name,
			String accessToken, String refreshToken, boolean tokenDelegationEnabled) {
		String picture = getPicture(pic);
		boolean update = false;
		if (!StringUtils.equals(user.getPicture(), picture)) {
			user.setPicture(picture);
			update = true;
		}
		if (!StringUtils.isBlank(email) && !StringUtils.equals(user.getEmail(), email)) {
			user.setEmail(email);
			update = true;
		}
		if (!StringUtils.isBlank(name) && !StringUtils.equals(user.getName(), name)) {
			user.setName(name);
			update = true;
		}
		if (tokenDelegationEnabled) {
			user.setIdpAccessToken(accessToken);
			user.setIdpRefreshToken(refreshToken);
			update = true;
		}
		return update;
	}

	/**
	 * If true, access tokens from the IDP are stored inside the user object and sent back to IDP for validation.
	 * @param app an app object
	 * @param user user
	 * @return false by default
	 */
	public boolean isAccessTokenDelegationEnabled(App app, User user) {
		return isAccessTokenDelegationEnabled(app, oauthAlias(user.getIdentifier()));
	}

	private boolean isAccessTokenDelegationEnabled(App app, String alias) {
		return Boolean.parseBoolean(SecurityUtils.getSettingForApp(app,
				configKey("token_delegation_enabled", alias), "false"));
	}

	/**
	 * Validates the access token against the IDP server.
	 * @param app an app object
	 * @param user the user object holding the tokens
	 * @return true if access token is valid
	 */
	public boolean isValidAccessToken(App app, User user) {
		try {
			String alias = oauthAlias(user.getIdentifier());
			Map<String, Object> profile = fetchProfileFromIDP(app, user.getIdpAccessToken(), alias);
			if (profile == null && user.getIdpRefreshToken() != null) {
				refreshTokens(app, user);
				profile = fetchProfileFromIDP(app, user.getIdpAccessToken(), alias);
			}
			return profile != null && profile.containsKey(SecurityUtils.getSettingForApp(app,
					configKey("parameters.id", alias), "sub"));
		} catch (Exception e) {
			LOG.error(null, e);
			return false;
		}
	}

	/**
	 * Sends a profile request to the IDP server with a given access token.
	 * @param app an app object
	 * @param accessToken access token
	 * @return null if the token was invalid or a map containing user information
	 * @throws IOException if connection fails
	 */
	private Map<String, Object> fetchProfileFromIDP(App app, String accessToken, String alias) throws IOException {
		Map<String, Object> profile = null;
		String acceptHeader = SecurityUtils.getSettingForApp(app, configKey("accept_header", alias), "");
		HttpGet profileGet = new HttpGet(SecurityUtils.getSettingForApp(app, configKey("profile_url", alias), ""));
		profileGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

		if (!StringUtils.isBlank(acceptHeader)) {
			profileGet.setHeader(HttpHeaders.ACCEPT, acceptHeader);
		}

		try (CloseableHttpResponse resp2 = httpclient.execute(profileGet)) {
			HttpEntity respEntity = resp2.getEntity();
			if (respEntity != null && resp2.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
				profile = jreader.readValue(respEntity.getContent());
			}
			if (profile == null || profile.isEmpty()) {
				LOG.error("OAuth 2 provider did not return any valid user information - response code {} {}",
						resp2.getStatusLine().getStatusCode(), resp2.getStatusLine().getReasonPhrase());
			}
			EntityUtils.consumeQuietly(respEntity);
		}
		return profile;
	}

	private void refreshTokens(App app, User user) throws IOException {
		Map<String, Object> token = tokenRequest(app, user.getIdpRefreshToken(), null, oauthAlias(user.getIdentifier()));
		if (token != null && token.containsKey("access_token")) {
			user.setIdpAccessToken((String) token.get("access_token"));
			String newRefresh = (String) token.get("refresh_token");
			if (!StringUtils.equals(newRefresh, user.getIdpRefreshToken())) {
				user.setIdpRefreshToken(newRefresh);
			}
			user.update();
		}
	}

	private Map<String, Object> tokenRequest(App app, String authCodeOrRefreshToken, String redirectURI, String alias)
			throws IOException {
		String[] keys = SecurityUtils.getOAuthKeysForApp(app, oauthPrefix(alias));

		String entity;
		String scope = SecurityUtils.getSettingForApp(app, configKey("scope", alias), "");
		if (redirectURI == null) {
			entity = Utils.formatMessage(REFRESH_PAYLOAD, authCodeOrRefreshToken,
					URLEncoder.encode(scope, "UTF-8"), keys[0], keys[1]);
		} else {
			entity = Utils.formatMessage(PAYLOAD, authCodeOrRefreshToken, Utils.urlEncode(redirectURI),
					URLEncoder.encode(scope, "UTF-8"), keys[0], keys[1]);
		}

		String acceptHeader = SecurityUtils.getSettingForApp(app, configKey("accept_header", alias), "");
		HttpPost tokenPost = new HttpPost(SecurityUtils.getSettingForApp(app, configKey("token_url", alias), ""));
		tokenPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
		tokenPost.setEntity(new StringEntity(entity, "UTF-8"));
		if (!StringUtils.isBlank(acceptHeader)) {
			tokenPost.setHeader(HttpHeaders.ACCEPT, acceptHeader);
		}

		Map<String, Object> tokens = null;
		try (CloseableHttpResponse resp1 = httpclient.execute(tokenPost)) {
			if (resp1 != null && resp1.getEntity() != null) {
				tokens = jreader.readValue(resp1.getEntity().getContent());
				EntityUtils.consumeQuietly(resp1.getEntity());
			}
		}
		return tokens;
	}

	private String oauthPrefix(String alias) {
		if ("third".equalsIgnoreCase(alias)) {
			return Config.OAUTH2_THIRD_PREFIX;
		} else if ("second".equalsIgnoreCase(alias)) {
			return Config.OAUTH2_SECOND_PREFIX;
		} else {
			return Config.OAUTH2_PREFIX;
		}
	}

	private String oauthAlias(String identifier) {
		if (identifier.startsWith(Config.OAUTH2_THIRD_PREFIX)) {
			return "third";
		} else if (identifier.startsWith(Config.OAUTH2_SECOND_PREFIX)) {
			return "second";
		} else {
			return "";
		}
	}

	private String configKey(String key, String alias) {
		if (StringUtils.isBlank(alias)) {
			return "security.oauth." + key;
		}
		return "security.oauth" + alias + "." + key;
	}

	private static String getPicture(String pic) {
		if (pic != null) {
			if (pic.contains("?")) {
				// user picture migth contain size parameters - remove them
				return pic.substring(0, pic.indexOf('?'));
			} else {
				return pic;
			}
		}
		return null;
	}

	private String getAppid(App app) {
		return (app == null) ? null : app.getAppIdentifier();
	}
}
