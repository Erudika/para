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
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

/**
 * A filter that handles authentication requests to LinkedIn.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class LinkedInAuthFilter extends AbstractAuthenticationProcessingFilter {

	private final CloseableHttpClient httpclient;
	private final ObjectReader jreader;
	private static final String PROFILE_URL = "https://api.linkedin.com/v1/people/~"
			+ ":(id,firstName,lastName,email-address,picture-url)?format=json&oauth2_access_token=";
	private static final String TOKEN_URL = "https://www.linkedin.com/uas/oauth2/accessToken?"
			+ "grant_type=authorization_code&code={0}&redirect_uri={1}&client_id={2}&client_secret={3}";

	/**
	 * The default filter mapping.
	 */
	public static final String LINKEDIN_ACTION = "linkedin_auth";

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public LinkedInAuthFilter(final String defaultFilterProcessesUrl) {
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

		if (requestURI.endsWith(LINKEDIN_ACTION)) {
			String authCode = request.getParameter("code");
			if (!StringUtils.isBlank(authCode)) {
				String appid = request.getParameter(Config._APPID);
				String redirectURI = request.getRequestURL().toString() + (appid == null ? "" : "?appid=" + appid);
				App app = Para.getDAO().read(App.id(appid == null ? Config.APP_NAME_NS : appid));
				String[] keys = SecurityUtils.getOAuthKeysForApp(app, Config.LINKEDIN_PREFIX);
				CloseableHttpResponse resp1 = null;
				String url = Utils.formatMessage(TOKEN_URL, authCode, redirectURI, keys[0], keys[1]);
				try {
					HttpPost tokenPost = new HttpPost(url);
					resp1 = httpclient.execute(tokenPost);
				} catch (Exception e) {
					logger.warn("LinkedIn auth request failed: GET " + url, e);
				}

				if (resp1 != null && resp1.getEntity() != null) {
					Map<String, Object> token = jreader.readValue(resp1.getEntity().getContent());
					if (token != null && token.containsKey("access_token")) {
						userAuth = getOrCreateUser(app, (String) token.get("access_token"));
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
	 * Calls the LinkedIn API to get the user profile using a given access token.
	 * @param app the app where the user will be created, use null for root app
	 * @param accessToken access token
	 * @return {@link UserAuthentication} object or null if something went wrong
	 * @throws IOException ex
	 */
	public UserAuthentication getOrCreateUser(App app, String accessToken) throws IOException {
		UserAuthentication userAuth = null;
		if (accessToken != null) {
			String ctype = null;
			HttpEntity respEntity = null;
			CloseableHttpResponse resp2 = null;
			try {
				HttpGet profileGet = new HttpGet(PROFILE_URL + accessToken);
				resp2 = httpclient.execute(profileGet);
				respEntity = resp2.getEntity();
				ctype = resp2.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue();
			} catch (Exception e) {
				logger.warn("LinkedIn auth request failed: GET " + PROFILE_URL + accessToken, e);
			}

			if (respEntity != null && Utils.isJsonType(ctype)) {
				Map<String, Object> profile = jreader.readValue(respEntity.getContent());

				if (profile != null && profile.containsKey("id")) {
					String linkedInID = (String) profile.get("id");
					String email = (String) profile.get("emailAddress");
					String pic = (String) profile.get("pictureUrl");
					String fName = (String) profile.get("firstName");
					String lName = (String) profile.get("lastName");
					String name = fName + " " + lName;

					User user = new User();
					user.setAppid(getAppid(app));
					user.setIdentifier(Config.LINKEDIN_PREFIX.concat(linkedInID));
					user.setEmail(email);
					user = User.readUserForIdentifier(user);
					if (user == null) {
						//user is new
						user = new User();
						user.setActive(true);
						user.setAppid(getAppid(app));
						user.setEmail(StringUtils.isBlank(email) ? linkedInID + "@linkedin.com" : email);
						user.setName(StringUtils.isBlank(name) ? "No Name" : name);
						user.setPassword(new UUID().toString());
						user.setPicture(pic);
						user.setIdentifier(Config.LINKEDIN_PREFIX.concat(linkedInID));
						String id = user.create();
						if (id == null) {
							throw new AuthenticationServiceException("Authentication failed: cannot create new user.");
						}
					} else {
						boolean update = false;
						if (!StringUtils.equals(user.getPicture(), pic)) {
							user.setPicture(pic);
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
				EntityUtils.consumeQuietly(respEntity);
			}
		}
		return userAuth;
	}

	private String getAppid(App app) {
		return (app == null) ? null : app.getAppIdentifier();
	}
}
