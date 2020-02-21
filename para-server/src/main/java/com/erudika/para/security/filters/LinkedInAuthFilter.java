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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.util.List;
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
 * A filter that handles authentication requests to LinkedIn.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class LinkedInAuthFilter extends AbstractAuthenticationProcessingFilter {

	private final CloseableHttpClient httpclient;
	private final ObjectReader jreader;
	private static final String PROFILE_URL = "https://api.linkedin.com/v2/me"
			+ "?projection=(id,firstName,lastName,profilePicture(displayImage~:playableStreams))";
	private static final String EMAIL_URL = "https://api.linkedin.com/v2/emailAddress"
			+ "?projection=(elements*(handle~))&q=members";
	private static final String TOKEN_URL = "https://www.linkedin.com/oauth/v2/accessToken";
	private static final String PAYLOAD = "code={0}&redirect_uri={1}&client_id={2}&client_secret={3}"
			+ "&grant_type=authorization_code";

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

		if (requestURI.endsWith(LINKEDIN_ACTION)) {
			String authCode = request.getParameter("code");
			if (!StringUtils.isBlank(authCode)) {
				String appid = SecurityUtils.getAppidFromAuthRequest(request);
				String redirectURI = SecurityUtils.getRedirectUrl(request);
				App app = Para.getDAO().read(App.id(appid == null ? Config.getRootAppIdentifier() : appid));
				String[] keys = SecurityUtils.getOAuthKeysForApp(app, Config.LINKEDIN_PREFIX);
				String entity = Utils.formatMessage(PAYLOAD, authCode, Utils.urlEncode(redirectURI), keys[0], keys[1]);

				HttpPost tokenPost = new HttpPost(TOKEN_URL);
				tokenPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
				tokenPost.setEntity(new StringEntity(entity, "UTF-8"));
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
	 * Calls the LinkedIn API to get the user profile using a given access token.
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
			JsonNode profileNode = null;

			try (CloseableHttpResponse resp2 = httpclient.execute(profileGet)) {
				HttpEntity respEntity = resp2.getEntity();
				if (respEntity != null) {
					profileNode = jreader.readTree(respEntity.getContent());
					EntityUtils.consumeQuietly(respEntity);
				}
			}

			// GET email
			HttpGet emailGet = new HttpGet(EMAIL_URL);
			emailGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
			emailGet.setHeader(HttpHeaders.ACCEPT, "application/json");
			String email = "";

			try (CloseableHttpResponse resp3 = httpclient.execute(emailGet)) {
				HttpEntity respEntity = resp3.getEntity();
				if (respEntity != null) {
					JsonNode handle = jreader.readTree(respEntity.getContent());
					EntityUtils.consumeQuietly(respEntity);
					JsonNode emailNode = handle.at("/elements/0");
					JsonNode emailNode2 = emailNode.at("/handle~");
					if (!emailNode2.isMissingNode()) {
						email = emailNode2.at("/emailAddress").asText();
					}
				}
			}

			if (profileNode != null && profileNode.hasNonNull("id")) {
				String linkedInID = profileNode.get("id").asText();
				String pic = getProfilePicture(profileNode);
				String name = getFullName(profileNode);

				user.setAppid(getAppid(app));
				user.setIdentifier(Config.LINKEDIN_PREFIX.concat(linkedInID));
				user.setEmail(email);
				user = User.readUserForIdentifier(user);
				if (user == null) {
					//user is new
					user = new User();
					user.setActive(true);
					user.setAppid(getAppid(app));
					user.setEmail(StringUtils.isBlank(email) ? Utils.getNewId() + "@linkedin.com" : email);
					user.setName(StringUtils.isBlank(name) ? "No Name" : name);
					user.setPassword(Utils.generateSecurityToken());
					user.setPicture(pic);
					user.setIdentifier(Config.LINKEDIN_PREFIX.concat(linkedInID));
					String id = user.create();
					if (id == null) {
						throw new AuthenticationServiceException("Authentication failed: cannot create new user.");
					}
				} else {
					if (updateUserInfo(user, pic, email, name)) {
						user.update();
					}
				}
				userAuth = new UserAuthentication(new AuthenticatedUserDetails(user));
			}
		}
		return SecurityUtils.checkIfActive(userAuth, user, false);
	}

	private boolean updateUserInfo(User user, String pic, String email, String name) {
		boolean update = false;
		if (!StringUtils.equals(user.getPicture(), pic)) {
			user.setPicture(pic);
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

	private String getProfilePicture(JsonNode profileNode) {
		String url = "";
		JsonNode picNode = profileNode.at("/profilePicture/displayImage~");
		if (!picNode.isMissingNode()) {
			List<String> elements = picNode.findValuesAsText("identifier");
			for (String picUrl : elements) {
				url = picUrl;
				if (picUrl.contains("400_400")) {
					break;
				}
			}
		}
		return url;
	}

	private String getFullName(JsonNode profileNode) {
		JsonNode fNameNode = profileNode.at("/firstName/localized");
		JsonNode lNameNode = profileNode.at("/lastName/localized");
		String fName = fNameNode.elements().hasNext() ? fNameNode.elements().next().textValue() : "";
		String lName = lNameNode.elements().hasNext() ? lNameNode.elements().next().textValue() : "";
		return (fName + " " + lName).trim();
	}

	private String getAppid(App app) {
		return (app == null) ? null : app.getAppIdentifier();
	}
}
