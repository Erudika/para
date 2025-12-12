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

import com.erudika.para.core.App;
import com.erudika.para.core.User;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.utils.Utils;
import com.erudika.para.server.security.AuthenticatedUserDetails;
import com.erudika.para.server.security.SecurityUtils;
import com.erudika.para.server.security.UserAuthentication;
import com.fasterxml.jackson.databind.ObjectReader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

/**
 * A filter that handles authentication requests to Amazon.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AmazonAuthFilter extends AbstractAuthenticationProcessingFilter {

	private static final Logger logger = LoggerFactory.getLogger(AmazonAuthFilter.class);

	private final CloseableHttpClient httpclient;
	private final ObjectReader jreader;
	private static final String PROFILE_URL = "https://api.amazon.com/user/profile";
	private static final String TOKEN_URL = "https://api.amazon.com/auth/o2/token";
	private static final String PAYLOAD = "code={0}&redirect_uri={1}&client_id={2}&client_secret={3}"
			+ "&grant_type=authorization_code";

	/**
	 * The default filter mapping.
	 */
	public static final String AMAZON_ACTION = "amazon_auth";

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public AmazonAuthFilter(final String defaultFilterProcessesUrl) {
		super(defaultFilterProcessesUrl);
		this.jreader = ParaObjectUtils.getJsonReader(Map.class);
		int timeout = 30;
		this.httpclient = HttpClientBuilder.create().
				setDefaultRequestConfig(RequestConfig.custom().
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
		final String requestURI = request.getServletPath();
		UserAuthentication userAuth = null;

		if (requestURI.endsWith(AMAZON_ACTION)) {
			String authCode = request.getParameter("code");
			if (!StringUtils.isBlank(authCode)) {
				String appid = SecurityUtils.getAppidFromAuthRequest(request);
				String redirectURI = SecurityUtils.getRedirectUrl(request);
				App app = Para.getDAO().read(App.id(appid == null ? Para.getConfig().getRootAppIdentifier() : appid));
				String[] keys = Para.getConfig().getOAuthKeysForApp(app, Config.AMAZON_PREFIX);
				String entity = Utils.formatMessage(PAYLOAD, authCode, Utils.urlEncode(redirectURI), keys[0], keys[1]);

				HttpPost tokenPost = new HttpPost(TOKEN_URL);
				tokenPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
				tokenPost.setHeader(HttpHeaders.ACCEPT, "application/json");
				tokenPost.setEntity(new StringEntity(entity));
				userAuth = httpclient.execute(tokenPost, (resp1) -> {
					if (resp1 != null && resp1.getEntity() != null) {
						Map<String, Object> token = jreader.readValue(resp1.getEntity().getContent());
						if (token != null && token.containsKey("access_token")) {
							return getOrCreateUser(app, (String) token.get("access_token"));
						} else {
							logger.info("Authentication request failed with status '" +
									resp1.getReasonPhrase() + "' - " + token);
						}
						EntityUtils.consumeQuietly(resp1.getEntity());
					} else {
						logger.info("Authentication request failed with status '"
								+ (resp1 != null ? resp1.getReasonPhrase() : "null")
								+ "' and empty response body.");
					}
					return null;
				});
			}
		}

		return SecurityUtils.checkIfActive(userAuth, SecurityUtils.getAuthenticatedUser(userAuth), true);
	}

	/**
	 * Calls the Amazon API to get the user profile using a given access token.
	 * @param app the app where the user will be created, use null for root app
	 * @param accessToken access token
	 * @return {@link UserAuthentication} object or null if something went wrong
	 * @throws IOException ex
	 */
	@SuppressWarnings("unchecked")
	public UserAuthentication getOrCreateUser(App app, String accessToken) throws IOException {
		if (accessToken == null) {
			return SecurityUtils.checkIfActive(null, null, false);
		}
		HttpGet profileGet = new HttpGet(PROFILE_URL);
		profileGet.setHeader(HttpHeaders.ACCEPT, "application/json");
		profileGet.setHeader("x-amz-access-token", accessToken);
		return httpclient.execute(profileGet, (resp2) -> {
			UserAuthentication userAuth = null;
			User user = new User();
			Map<String, Object> profile = null;

			HttpEntity respEntity = resp2.getEntity();
			if (respEntity != null) {
				profile = jreader.readValue(respEntity.getContent());
				EntityUtils.consumeQuietly(respEntity);
			}

			if (profile != null && profile.containsKey("user_id")) {
				String amazonId = (String) profile.get("user_id");
				String email = (String) profile.get("email");
				String name = (String) profile.get("name");

				user.setAppid(getAppid(app));
				user.setIdentifier(Config.AMAZON_PREFIX + amazonId);
				user.setEmail(email);
				user = User.readUserForIdentifier(user);
				if (user == null) {
					//user is new
					user = new User();
					user.setActive(true);
					user.setAppid(getAppid(app));
					user.setEmail(StringUtils.isBlank(email) ? Utils.getNewId() + "@amazon.com" : email);
					user.setName(StringUtils.isBlank(name) ? "No Name" : name);
					user.setPassword(Utils.generateSecurityToken());
					user.setPicture("https://www.gravatar.com/avatar/" + Utils.md5(email) + "?d=mm&size=400");
					user.setIdentifier(Config.AMAZON_PREFIX + amazonId);
					updateIdpAccessToken(user, accessToken);
					String id = user.create();
					if (id == null) {
						throw new AuthenticationServiceException("Authentication failed: cannot create new user.");
					}
				} else {
					if (updateUserInfo(user, email, name, accessToken)) {
						user.update();
					}
				}
				userAuth = new UserAuthentication(new AuthenticatedUserDetails(user));
			} else {
				logger.info("Authentication request failed because user profile doesn't contain the expected attributes");
			}
			return SecurityUtils.checkIfActive(userAuth, user, false);
		});
	}

	private boolean updateUserInfo(User user, String email, String name, String accessToken)
			throws UnsupportedEncodingException {
		boolean update = false;
		if (!StringUtils.isBlank(email) && !Strings.CS.equals(user.getEmail(), email)) {
			user.setEmail(email);
			update = true;
		}
		if (!StringUtils.isBlank(name) && !Strings.CS.equals(user.getName(), name)) {
			user.setName(name);
			update = true;
		}
		return updateIdpAccessToken(user, accessToken) || update;
	}

	private boolean updateIdpAccessToken(User user, String accessToken) throws UnsupportedEncodingException {
		String payload = "{\"access_token\":\"" + accessToken + "\"}";
		if (!payload.equals(Utils.base64dec(user.getIdpAccessTokenPayload()))) {
			user.setIdpAccessToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
					+ Utils.base64enc(payload.getBytes(Para.getConfig().defaultEncoding())) + ".Ss");
			return true;
		}
		return false;
	}

	private String getAppid(App app) {
		return (app == null) ? null : app.getAppIdentifier();
	}
}
