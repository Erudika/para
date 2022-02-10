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
package com.erudika.para.server.security.filters;

import com.erudika.para.core.utils.Para;
import com.erudika.para.core.App;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.User;
import com.erudika.para.server.security.AuthenticatedUserDetails;
import com.erudika.para.server.security.SecurityUtils;
import com.erudika.para.server.security.UserAuthentication;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
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
		int timeout = 30;
		this.httpclient = HttpClientBuilder.create().
				setDefaultRequestConfig(RequestConfig.custom().
						setConnectTimeout(timeout, TimeUnit.SECONDS).
						setConnectionRequestTimeout(timeout, TimeUnit.SECONDS).
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
				App app = Para.getDAO().read(App.id(appid == null ? Para.getConfig().getRootAppIdentifier() : appid));

				Map<String, Object> token = tokenRequest(app, authCode, SecurityUtils.getRedirectUrl(request), alias);
				if (token != null) {
					if (token.containsKey("access_token")) {
						userAuth = getOrCreateUser(app, token.get("access_token") +
								Para.getConfig().separator() + token.get("refresh_token") +
								Para.getConfig().separator() + token.get("id_token"));
					} else {
						LOG.info("OAuth 2.0 token request failed with response " + token);
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
			String[] tokens = accessToken.split(Para.getConfig().separator());
			String refreshToken = null;
			String idToken = null;
			if (tokens.length > 0) {
				accessToken = tokens[0];
			}
			if (tokens.length > 1) {
				refreshToken = tokens[1];
			}
			if (tokens.length > 2) {
				idToken = tokens[2];
			}

			boolean tokenDelegationEnabled = isAccessTokenDelegationEnabled(app, alias);
			Map<String, Object> profile = fetchProfileFromIDP(app, accessToken, idToken, alias);

			String accountIdParam = SecurityUtils.getSettingForApp(app, configKey("parameters.id", alias), "sub");
			String pictureParam = SecurityUtils.getSettingForApp(app, configKey("parameters.picture", alias), "picture");
			String emailDomain = SecurityUtils.getSettingForApp(app, configKey("domain", alias), "paraio.com");
			String emailParam = SecurityUtils.getSettingForApp(app, configKey("parameters.email", alias), "email");
			String nameParam = SecurityUtils.getSettingForApp(app, configKey("parameters.name", alias), "name");
			String gnParam = SecurityUtils.getSettingForApp(app, configKey("parameters.given_name", alias), "given_name");
			String fnParam = SecurityUtils.getSettingForApp(app, configKey("parameters.family_name", alias), "family_name");

			if (profile != null && profile.containsKey(accountIdParam)) {
				Object accid = profile.get(accountIdParam);
				String oauthAccountId = accid instanceof String ? (String) accid : String.valueOf(accid);
				String email = getEmailFromProfile(profile, emailParam, oauthAccountId, emailDomain);
				String pic = getPictureFromProfile(profile, pictureParam);
				String name = getNameFromProfile(profile, nameParam);
				String gname = getGivenNameFromProfile(profile, gnParam);
				String fname = getFirstNameFromProfile(profile, fnParam);

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
					user.setName(StringUtils.isBlank(name) ? getFullName(gname, fname) : name);
					user.setPassword(Utils.generateSecurityToken());
					if (tokenDelegationEnabled) {
						user.setIdpAccessToken(accessToken);
						user.setIdpRefreshToken(refreshToken);
						user.setIdpIdToken(idToken);
						printTokenDebugInfo(user);
					}
					user.setPicture(getPicture(app, user, accessToken, alias, pic));
					user.setIdentifier(oauthPrefix(alias).concat(oauthAccountId));
					String id = user.create();
					if (id == null) {
						throw new AuthenticationServiceException("Authentication failed: cannot create new user.");
					}
				} else {
					if (updateUserInfo(app, user, pic, email, name, accessToken,
							refreshToken, idToken, alias, tokenDelegationEnabled)) {
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

	private boolean updateUserInfo(App app, User user, String pic, String email, String name,
			String accessToken, String refreshToken, String idToken, String alias, boolean tokenDelegationEnabled) {
		String picture = getPicture(app, user, accessToken, alias, pic);
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
			user.setIdpIdToken(idToken);
			printTokenDebugInfo(user);
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
			Map<String, Object> profile = fetchProfileFromIDP(app, user.getIdpAccessToken(), null, alias);
			if (profile == null && user.getIdpRefreshToken() != null) {
				refreshTokens(app, user);
				profile = fetchProfileFromIDP(app, user.getIdpAccessToken(), null, alias);
			}
			return profile != null && profile.containsKey(SecurityUtils.getSettingForApp(app,
					configKey("parameters.id", alias), "sub"));
		} catch (Exception e) {
			LOG.debug("Invalid access token {}", e);
			return false;
		}
	}

	/**
	 * Sends a profile request to the IDP server with a given access token.
	 * @param app an app object
	 * @param accessToken access token
	 * @param idToken ID token
	 * @return null if the token was invalid or a map containing user information
	 * @throws IOException if connection fails
	 */
	private Map<String, Object> fetchProfileFromIDP(App app, String accessToken, String idToken, String alias) throws IOException {
		Map<String, Object> profile = new HashMap<>();
		if (StringUtils.contains(idToken, ".")) {
			String idTokenDecoded = Utils.base64dec(StringUtils.substringBetween(idToken, "."));
			profile.putAll(jreader.readValue(idTokenDecoded));
		}
		String acceptHeader = SecurityUtils.getSettingForApp(app, configKey("accept_header", alias), "");
		HttpGet profileGet = new HttpGet(SecurityUtils.getSettingForApp(app, configKey("profile_url", alias), ""));
		profileGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

		if (!StringUtils.isBlank(acceptHeader)) {
			profileGet.setHeader(HttpHeaders.ACCEPT, acceptHeader);
		}

		try (CloseableHttpResponse resp2 = httpclient.execute(profileGet)) {
			HttpEntity respEntity = resp2.getEntity();
			String error = null;
			if (respEntity != null) {
				if (resp2.getCode() == HttpServletResponse.SC_OK) {
					profile.putAll(jreader.readValue(respEntity.getContent()));
				} else {
					error = IOUtils.toString(respEntity.getContent(), Para.getConfig().defaultEncoding());
				}
			}
			if (profile.isEmpty() || error != null) {
				LOG.error("OAuth 2 provider did not return any valid user information - "
						+ "response code {} {}, app '{}', payload {}",
						resp2.getCode(), resp2.getReasonPhrase(), app.getId(),
						Utils.abbreviate(error, 1000));
			}
			EntityUtils.consumeQuietly(respEntity);
		} catch (Exception e) {
			LOG.error("Failed to fetch profile form IDP for app {} - {}", app.getId(), e.getMessage());
		}
		return profile;
	}

	private void refreshTokens(App app, User user) throws IOException {
		Map<String, Object> token = tokenRequest(app, user.getIdpRefreshToken(), null, oauthAlias(user.getIdentifier()));
		if (token != null && token.containsKey("access_token")) {
			user.setIdpAccessToken((String) token.get("access_token"));
			user.setIdpIdToken((String) token.get("id_token"));
			String newRefresh = (String) token.get("refresh_token");
			if (!StringUtils.equals(newRefresh, user.getIdpRefreshToken())) {
				user.setIdpRefreshToken(newRefresh);
			}
			printTokenDebugInfo(user);
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
		tokenPost.setEntity(new StringEntity(entity));
		if (!StringUtils.isBlank(acceptHeader)) {
			tokenPost.setHeader(HttpHeaders.ACCEPT, acceptHeader);
		}

		Map<String, Object> tokens = null;
		try (CloseableHttpResponse resp1 = httpclient.execute(tokenPost)) {
			if (resp1 != null && resp1.getEntity() != null) {
				tokens = jreader.readValue(resp1.getEntity().getContent());
				EntityUtils.consumeQuietly(resp1.getEntity());
			} else {
				LOG.info("Authentication request failed with status '"
						+ (resp1 != null ? resp1.getReasonPhrase() : "null")
						+ "' and empty response body.");
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

	private static String configKey(String key, String alias) {
		if (StringUtils.isBlank(alias)) {
			return "security.oauth." + key;
		}
		return "security.oauth" + alias + "." + key;
	}

	private static String getPicture(App app, User user, String accessToken, String alias, String pic) {
		if (pic != null) {
			String avatar = pic;
			if ("true".equals(SecurityUtils.getSettingForApp(app, configKey("download_avatars", alias), "false"))) {
				avatar = fetchAvatar(app.getAppIdentifier().trim(), user.getId(), accessToken, pic);
			} else if (pic.contains("?")) {
				// user picture migth contain size parameters - remove them
				avatar = pic.substring(0, pic.indexOf('?'));
			}
			return avatar;
		}
		return null;
	}

	private static String fetchAvatar(String appid, String userid, String accessToken, String avatarUrl) {
		if (accessToken != null) {
			HttpGet avatarGet = new HttpGet(avatarUrl);
			avatarGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
			try (CloseableHttpResponse resp = HttpClientBuilder.create().build().execute(avatarGet)) {
				HttpEntity respEntity = resp.getEntity();
				if (respEntity != null && respEntity.getContentType().startsWith("image")) {
					String ctype = respEntity.getContentType();
					return Para.getFileStore().store(Optional.ofNullable(appid).orElse(Config.PARA) + "/" + userid + "."
							+ StringUtils.substringAfter(ctype, "/"), respEntity.getContent());
				}
			} catch (Exception e) {
				LOG.error(null, e);
			}
		}
		return avatarUrl;
	}

	private String getAppid(App app) {
		return (app == null) ? null : app.getAppIdentifier();
	}

	private String getFullName(String gname, String fname) {
		if (StringUtils.isBlank(fname)) {
			return StringUtils.isBlank(gname) ? "No Name" : gname;
		} else if (StringUtils.isBlank(gname)) {
			return StringUtils.isBlank(fname) ? "No Name" : fname;
		}
		return gname + " " + fname;
	}

	private String getEmailFromProfile(Map<String, Object> profile, String emailParam, String oauthAccountId, String emailDomain) {
		String email = (String) profile.get(emailParam);
		if (StringUtils.isBlank(email)) {
			if (emailParam.startsWith("/")) {
				// support for JSON pointers to get data from sub fields like {"attributes": { data }}
				JsonNode profileTree = ParaObjectUtils.getJsonMapper().valueToTree(profile);
				JsonNode nodeAtPath = profileTree.at(emailParam);
				if (!nodeAtPath.isMissingNode()) {
					email = nodeAtPath.asText(email);
				}
			}
			if (StringUtils.isBlank(email)) {
				if (Utils.isValidEmail(oauthAccountId)) {
					email = oauthAccountId;
				} else if (!StringUtils.isBlank(emailDomain)) {
					email = oauthAccountId.concat("@").concat(emailDomain);
				} else {
					LOG.warn("Blank email attribute for OAuth2 user '{}'.", oauthAccountId);
					email = oauthAccountId + "@scoold.com";
				}
			}
		}
		return email;
	}

	private String getPictureFromProfile(Map<String, Object> profile, String pictureParam) {
		String pic = (String) profile.get(pictureParam);
		if (StringUtils.isBlank(pic) && pictureParam.startsWith("/")) {
			// support for JSON pointers to get data from sub fields like {"attributes": { data }}
			JsonNode profileTree = ParaObjectUtils.getJsonMapper().valueToTree(profile);
			JsonNode nodeAtPath = profileTree.at(pictureParam);
			if (!nodeAtPath.isMissingNode()) {
				pic = nodeAtPath.asText(pic);
			}
		}
		return pic;
	}

	private String getNameFromProfile(Map<String, Object> profile, String nameParam) {
		String name = (String) profile.get(nameParam);
		if (StringUtils.isBlank(name) && nameParam.startsWith("/")) {
			// support for JSON pointers to get data from sub fields like {"attributes": { data }}
			JsonNode profileTree = ParaObjectUtils.getJsonMapper().valueToTree(profile);
			JsonNode nodeAtPath = profileTree.at(nameParam);
			if (!nodeAtPath.isMissingNode()) {
				name = nodeAtPath.asText(name);
			}
		}
		return name;
	}

	private String getGivenNameFromProfile(Map<String, Object> profile, String gnParam) {
		String gname = (String) profile.get(gnParam);
		if (StringUtils.isBlank(gname) && gnParam.startsWith("/")) {
			// support for JSON pointers to get data from sub fields like {"attributes": { data }}
			JsonNode profileTree = ParaObjectUtils.getJsonMapper().valueToTree(profile);
			JsonNode nodeAtPath = profileTree.at(gnParam);
			if (!nodeAtPath.isMissingNode()) {
				gname = nodeAtPath.asText(gname);
			}
		}
		return gname;
	}

	private String getFirstNameFromProfile(Map<String, Object> profile, String fnParam) {
		String fname = (String) profile.get(fnParam);
		if (StringUtils.isBlank(fname) && fnParam.startsWith("/")) {
			// support for JSON pointers to get data from sub fields like {"attributes": { data }}
			JsonNode profileTree = ParaObjectUtils.getJsonMapper().valueToTree(profile);
			JsonNode nodeAtPath = profileTree.at(fnParam);
			if (!nodeAtPath.isMissingNode()) {
				fname = nodeAtPath.asText(fname);
			}
		}
		return fname;
	}

	private void printTokenDebugInfo(User user) {
		try {
			LOG.debug("Updated OAuth2 tokens for user " + user.getId() + ":" +
					"\nidpAccessTokenPayload: " + Utils.base64dec(user.getIdpAccessTokenPayload()) +
					"\nidpIdTokenPayload: " + Utils.base64dec(user.getIdpIdTokenPayload()) +
					"\nidpRefreshToken: " + user.getIdpRefreshToken());
		} catch (Exception e) {
			LOG.debug(null, e);
		}
	}
}
