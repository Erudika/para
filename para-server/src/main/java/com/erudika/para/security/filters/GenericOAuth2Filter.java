/*
 * Copyright 2013-2018 Erudika. https://erudika.com
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
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

/**
 * A filter that handles authentication requests to a generic OAuth 2.0 identity server.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class GenericOAuth2Filter extends AbstractAuthenticationProcessingFilter {

	private final CloseableHttpClient httpclient;
	private final ObjectReader jreader;
	private static final String PAYLOAD = "code={0}&redirect_uri={1}"
			+ "&scope={2}&client_id={3}&client_secret={4}&grant_type=authorization_code";

	/**
	 * The default filter mapping.
	 */
	public static final String OAUTH2_ACTION = "oauth2_auth";

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

		if (requestURI.endsWith(OAUTH2_ACTION)) {
			String authCode = request.getParameter("code");
			if (!StringUtils.isBlank(authCode)) {
				String appid = SecurityUtils.getAppidFromAuthRequest(request);
				String redirectURI = SecurityUtils.getRedirectUrl(request);
				App app = Para.getDAO().read(App.id(appid == null ? Config.getRootAppIdentifier() : appid));
				String[] keys = SecurityUtils.getOAuthKeysForApp(app, Config.OAUTH2_PREFIX);
				String entity = Utils.formatMessage(PAYLOAD,
						authCode, Utils.urlEncode(redirectURI),
						URLEncoder.encode(SecurityUtils.getSettingForApp(app, "security.oauth.scope", ""), "UTF-8"),
						keys[0], keys[1]);

				String acceptHeader = SecurityUtils.getSettingForApp(app, "security.oauth.accept_header", "");
				HttpPost tokenPost = new HttpPost(SecurityUtils.getSettingForApp(app, "security.oauth.token_url", ""));
				tokenPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
				tokenPost.setEntity(new StringEntity(entity, "UTF-8"));
				if (!StringUtils.isBlank(acceptHeader)) {
					tokenPost.setHeader(HttpHeaders.ACCEPT, acceptHeader);
				}

				try (CloseableHttpResponse resp1 = httpclient.execute(tokenPost)) {
					if (resp1 != null && resp1.getEntity() != null) {
						Map<String, Object> token = jreader.readValue(resp1.getEntity().getContent());
						if (token != null && token.containsKey("access_token")) {
							userAuth = getOrCreateUser(app, (String) token.get("access_token"));
						}
						EntityUtils.consumeQuietly(resp1.getEntity());
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
	 * @throws IOException ex
	 */
	public UserAuthentication getOrCreateUser(App app, String accessToken) throws IOException {
		UserAuthentication userAuth = null;
		User user = new User();
		if (accessToken != null) {
			String acceptHeader = SecurityUtils.getSettingForApp(app, "security.oauth.accept_header", "");
			HttpGet profileGet = new HttpGet(SecurityUtils.getSettingForApp(app, "security.oauth.profile_url", ""));
			profileGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
			Map<String, Object> profile = null;

			if (!StringUtils.isBlank(acceptHeader)) {
				profileGet.setHeader(HttpHeaders.ACCEPT, acceptHeader);
			}

			try (CloseableHttpResponse resp2 = httpclient.execute(profileGet)) {
				HttpEntity respEntity = resp2.getEntity();
				if (respEntity != null) {
					profile = jreader.readValue(respEntity.getContent());
					EntityUtils.consumeQuietly(respEntity);
				}
			}

			String accountIdParam = SecurityUtils.getSettingForApp(app, "security.oauth.parameters.id", "id");
			String pictureParam = SecurityUtils.getSettingForApp(app, "security.oauth.parameters.picture", "picture");
			String emailDomain = SecurityUtils.getSettingForApp(app, "security.oauth.domain", "paraio.com");
			String emailParam = SecurityUtils.getSettingForApp(app, "security.oauth.parameters.email", "email");
			String nameParam = SecurityUtils.getSettingForApp(app, "security.oauth.parameters.name", "name");

			if (profile != null && profile.containsKey(accountIdParam)) {
				String oauthAccountId = (String) profile.get(accountIdParam);
				String pic = (String) profile.get(pictureParam);
				String email = (String) profile.get(emailParam);
				String name = (String) profile.get(nameParam);

				user.setAppid(getAppid(app));
				user.setIdentifier(Config.OAUTH2_PREFIX.concat(oauthAccountId));
				user.setEmail(email);
				user = User.readUserForIdentifier(user);
				if (user == null) {
					//user is new
					user = new User();
					user.setActive(true);
					user.setAppid(getAppid(app));
					user.setEmail(StringUtils.isBlank(email) ? oauthAccountId + "@" + emailDomain : email);
					user.setName(StringUtils.isBlank(name) ? "No Name" : name);
					user.setPassword(Utils.generateSecurityToken());
					user.setPicture(getPicture(pic));
					user.setIdentifier(Config.OAUTH2_PREFIX.concat(oauthAccountId));
					String id = user.create();
					if (id == null) {
						throw new AuthenticationServiceException("Authentication failed: cannot create new user.");
					}
				} else {
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
					if (update) {
						user.update();
					}
				}
				userAuth = new UserAuthentication(new AuthenticatedUserDetails(user));
			}
		}
		return SecurityUtils.checkIfActive(userAuth, user, false);
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
