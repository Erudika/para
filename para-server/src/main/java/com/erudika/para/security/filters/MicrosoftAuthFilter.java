/*
 * Copyright 2013-2017 Erudika. https://erudika.com
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

import com.eaio.uuid.UUID;
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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

/**
 * A filter that handles authentication requests to Microsoft.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class MicrosoftAuthFilter extends AbstractAuthenticationProcessingFilter {

	private final CloseableHttpClient httpclient;
	private final ObjectReader jreader;
	private static final String PROFILE_URL = "https://graph.microsoft.com/v1.0/me";
	private static final String PHOTO_URL = "https://apis.live.net/v5.0/{0}/picture?type=large";
	private static final String TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
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
		this.httpclient = HttpClients.createDefault();
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
				String appid = request.getParameter("appid");
				String redirectURI = request.getRequestURL().toString() + (appid == null ? "" : "?appid=" + appid);
				String[] keys = SecurityUtils.getCustomAuthSettings(appid, Config.MICROSOFT_PREFIX, request);
				String entity = Utils.formatMessage(PAYLOAD,
						URLEncoder.encode(authCode, "UTF-8"),
						URLEncoder.encode(redirectURI, "UTF-8"), keys[0], keys[1]);

				HttpPost tokenPost = new HttpPost(TOKEN_URL);
				tokenPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
				tokenPost.setHeader(HttpHeaders.ACCEPT, "application/json");
				tokenPost.setEntity(new StringEntity(entity, "UTF-8"));
				CloseableHttpResponse resp1 = httpclient.execute(tokenPost);

				if (resp1 != null && resp1.getEntity() != null) {
					Map<String, Object> token = jreader.readValue(resp1.getEntity().getContent());
					if (token != null && token.containsKey("access_token")) {
						userAuth = getOrCreateUser(appid, (String) token.get("access_token"));
					}
					EntityUtils.consumeQuietly(resp1.getEntity());
				}
			}
		}

		User user = SecurityUtils.getAuthenticatedUser(userAuth);

		if (userAuth == null || user == null || user.getIdentifier() == null) {
			throw new BadCredentialsException("Bad credentials.");
		} else if (!user.getActive()) {
			throw new LockedException("Account is locked.");
		}
		return userAuth;
	}

	/**
	 * Calls the Microsoft Graph API to get the user profile using a given access token.
	 * @param appid app identifier of the parent app, use null for root app
	 * @param accessToken access token
	 * @return {@link UserAuthentication} object or null if something went wrong
	 * @throws IOException ex
	 */
	public UserAuthentication getOrCreateUser(String appid, String accessToken) throws IOException {
		UserAuthentication userAuth = null;
		if (accessToken != null) {
			User user = new User();
			user.setAppid(appid);
			HttpGet profileGet = new HttpGet(PROFILE_URL);
			profileGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
			profileGet.setHeader(HttpHeaders.ACCEPT, "application/json");
			CloseableHttpResponse resp2 = httpclient.execute(profileGet);
			HttpEntity respEntity = resp2.getEntity();
			String ctype = resp2.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue();

			if (respEntity != null && Utils.isJsonType(ctype)) {
				Map<String, Object> profile = jreader.readValue(respEntity.getContent());

				if (profile != null && profile.containsKey("id")) {
					String microsoftId = (String) profile.get("id");
					String email = (String) profile.get("userPrincipalName");
					String name = (String) profile.get("displayName");
					if (StringUtils.isBlank(email) || !StringUtils.contains(email, "@")) {
						email = (String) profile.get("mail");
					}

					user.setIdentifier(Config.MICROSOFT_PREFIX + microsoftId);
					user = User.readUserForIdentifier(user);
					if (user == null) {
						//user is new
						user = new User();
						user.setActive(true);
						user.setAppid(appid);
						user.setEmail(StringUtils.isBlank(email) ? microsoftId + "@windowslive.com" : email);
						user.setName(StringUtils.isBlank(name) ? "No Name" : name);
						user.setPassword(new UUID().toString());
						user.setPicture(getPicture(microsoftId));
						user.setIdentifier(Config.MICROSOFT_PREFIX + microsoftId);
						String id = user.create();
						if (id == null) {
							throw new AuthenticationServiceException("Authentication failed: cannot create new user.");
						}
					} else {
						if (!StringUtils.isBlank(email) && !StringUtils.equals(user.getEmail(), email)) {
							user.setEmail(email);
							user.update();
						}
					}
					userAuth = new UserAuthentication(new AuthenticatedUserDetails(user));
				}
				EntityUtils.consumeQuietly(respEntity);
			}
		}
		return userAuth;
	}

	private String getPicture(String cid) {
		if (cid != null) {
			return Utils.formatMessage(PHOTO_URL, cid);
		}
		return null;
	}
}
