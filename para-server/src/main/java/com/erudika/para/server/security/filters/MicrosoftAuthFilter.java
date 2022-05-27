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
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
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
 * A filter that handles authentication requests to Microsoft.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class MicrosoftAuthFilter extends AbstractAuthenticationProcessingFilter {

	private static final Logger logger = LoggerFactory.getLogger(MicrosoftAuthFilter.class);

	private final CloseableHttpClient httpclient;
	private final ObjectReader jreader;
	private static final String PROFILE_URL = "https://graph.microsoft.com/v1.0/me";
	private static final String PHOTO_URL = "https://graph.microsoft.com/v1.0/me/photo/$value";
	private static final String TOKEN_URL = "https://login.microsoftonline.com/{0}/oauth2/v2.0/token";
	private static final String PAYLOAD = "code={0}&redirect_uri={1}"
			+ "&scope=https%3A%2F%2Fgraph.microsoft.com%2Fuser.read&client_id={2}"
			+ "&client_secret={3}&grant_type=authorization_code";
	/**
	 * The default filter mapping.
	 */
	public static final String MICROSOFT_ACTION = "microsoft_auth";

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public MicrosoftAuthFilter(final String defaultFilterProcessesUrl) {
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

		if (requestURI.endsWith(MICROSOFT_ACTION)) {
			String authCode = request.getParameter("code");
			if (!StringUtils.isBlank(authCode)) {
				String appid = SecurityUtils.getAppidFromAuthRequest(request);
				String redirectURI = SecurityUtils.getRedirectUrl(request);
				App app = Para.getDAO().read(App.id(appid == null ? Para.getConfig().getRootAppIdentifier() : appid));
				String[] keys = Para.getConfig().getOAuthKeysForApp(app, Config.MICROSOFT_PREFIX);
				String entity = Utils.formatMessage(PAYLOAD, authCode, Utils.urlEncode(redirectURI), keys[0], keys[1]);

				HttpPost tokenPost = new HttpPost(Utils.formatMessage(TOKEN_URL,
						Para.getConfig().getSettingForApp(app, "ms_tenant_id", "common")));
				tokenPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
				tokenPost.setEntity(new StringEntity(entity));
				try (CloseableHttpResponse resp1 = httpclient.execute(tokenPost)) {
					if (resp1 != null && resp1.getEntity() != null) {
						Map<String, Object> token = jreader.readValue(resp1.getEntity().getContent());
						if (token != null && token.containsKey("access_token")) {
							userAuth = getOrCreateUser(app, (String) token.get("access_token"));
						} else {
							logger.info("Authentication request failed with status '" +
									resp1.getReasonPhrase() + "' - " + token);
						}
						EntityUtils.consumeQuietly(resp1.getEntity());
					} else {
						logger.info("Authentication request failed with status '" +
								(resp1 != null ? resp1.getReasonPhrase() : "null") +
								"' and empty response body.");
					}
				}
			}
		}

		return SecurityUtils.checkIfActive(userAuth, SecurityUtils.getAuthenticatedUser(userAuth), true);
	}

	/**
	 * Calls the Microsoft Graph API to get the user profile using a given access token.
	 * @param app the app where the user will be created, use null for root app
	 * @param accessToken access token
	 * @return {@link UserAuthentication} object or null if something went wrong
	 * @throws IOException ex
	 */
	public UserAuthentication getOrCreateUser(App app, String accessToken) throws IOException {
		UserAuthentication userAuth = null;
		User user = new User();
		if (accessToken != null) {
			HttpGet profileGet = new HttpGet(PROFILE_URL);
			profileGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
			profileGet.setHeader(HttpHeaders.ACCEPT, "application/json");
			Map<String, Object> profile = null;

			try (CloseableHttpResponse resp2 = httpclient.execute(profileGet)) {
				HttpEntity respEntity = resp2.getEntity();
				if (respEntity != null) {
					profile = jreader.readValue(respEntity.getContent());
					EntityUtils.consumeQuietly(respEntity);
				}
			}

			if (profile != null && profile.containsKey("id")) {
				String microsoftId = (String) profile.get("id");
				String email = getEmail(profile);
				String name = (String) profile.get("displayName");

				user.setAppid(getAppid(app));
				user.setIdentifier(Config.MICROSOFT_PREFIX + microsoftId);
				user.setEmail(email);
				user = User.readUserForIdentifier(user);
				if (user == null) {
					//user is new
					user = new User(Utils.getNewId());
					user.setActive(true);
					user.setAppid(getAppid(app));
					user.setEmail(StringUtils.isBlank(email) ? Utils.getNewId() + "@windowslive.com" : email);
					user.setName(StringUtils.isBlank(name) ? "No Name" : name);
					user.setPassword(Utils.generateSecurityToken());
					user.setPicture(getPicture(getAppid(app), user.getId(), accessToken, email));
					user.setIdentifier(Config.MICROSOFT_PREFIX + microsoftId);
					String id = user.create();
					if (id == null) {
						throw new AuthenticationServiceException("Authentication failed: cannot create new user.");
					}
				} else {
					if (updateUserInfo(user, email, name, accessToken, getAppid(app))) {
						user.update();
					}
				}
				userAuth = new UserAuthentication(new AuthenticatedUserDetails(user));
			} else {
				logger.info("Authentication request failed because user profile doesn't contain the expected attributes");
			}
		}
		return SecurityUtils.checkIfActive(userAuth, user, false);
	}

	private boolean updateUserInfo(User user, String email, String name, String accessToken, String appid) throws IOException {
		String picture = getPicture(appid, user.getId(), accessToken, email);
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
		return update;
	}

	private String getPicture(String appid, String userid, String accessToken, String email) throws IOException {
		String pic = getGravatar(email);
		if (accessToken != null) {
			HttpGet profileGet = new HttpGet(PHOTO_URL);
			profileGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
			profileGet.setHeader(HttpHeaders.ACCEPT, "application/json");
			try (CloseableHttpResponse resp = httpclient.execute(profileGet)) {
				HttpEntity respEntity = resp.getEntity();
				if (respEntity != null && respEntity.getContentType().startsWith("image")) {
					String ctype = respEntity.getContentType();
					pic = Para.getFileStore().store(Optional.
							ofNullable(appid).orElse(Config.PARA) + "/" + userid + "." +
									StringUtils.substringAfter(ctype, "/"), respEntity.getContent());
				}
				EntityUtils.consumeQuietly(respEntity);
			}
		}
		return pic;
	}

	private String getGravatar(String email) {
		return "https://www.gravatar.com/avatar/" + Utils.md5(email.toLowerCase()) + "?size=400&d=mm&r=pg";
	}

	private String getEmail(Map<String, Object> profile) {
		String email = (String) profile.get("mail");
		if (StringUtils.isBlank(email) || !StringUtils.contains(email, "@")) {
			email = (String) profile.get("userPrincipalName");
		}
		return email;
	}

	private String getAppid(App app) {
		return (app == null) ? null : app.getAppIdentifier();
	}
}
