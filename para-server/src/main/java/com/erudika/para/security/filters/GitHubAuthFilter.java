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
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
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
 * A filter that handles authentication requests to GitHub.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class GitHubAuthFilter extends AbstractAuthenticationProcessingFilter {

	private final CloseableHttpClient httpclient;
	private final ObjectReader jreader;
	private static final String PROFILE_URL = "https://api.github.com/user";
	private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
	private static final String PAYLOAD = "code={0}&redirect_uri={1}&client_id={2}&client_secret={3}";

	/**
	 * The default filter mapping.
	 */
	public static final String GITHUB_ACTION = "github_auth";

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public GitHubAuthFilter(final String defaultFilterProcessesUrl) {
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

		if (requestURI.endsWith(GITHUB_ACTION)) {
			String authCode = request.getParameter("code");
			if (!StringUtils.isBlank(authCode)) {
				String appid = SecurityUtils.getAppidFromAuthRequest(request);
				String redirectURI = SecurityUtils.getRedirectUrl(request);
				App app = Para.getDAO().read(App.id(appid == null ? Config.getRootAppIdentifier() : appid));
				String[] keys = SecurityUtils.getOAuthKeysForApp(app, Config.GITHUB_PREFIX);
				String entity = Utils.formatMessage(PAYLOAD, authCode, Utils.urlEncode(redirectURI), keys[0], keys[1]);

				HttpPost tokenPost = new HttpPost(TOKEN_URL);
				tokenPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
				tokenPost.setHeader(HttpHeaders.ACCEPT, "application/json");
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
	 * Calls the GitHub API to get the user profile using a given access token.
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
			profileGet.setHeader(HttpHeaders.AUTHORIZATION, "token " + accessToken);
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
				Integer githubId = (Integer) profile.get("id");
				String pic = (String) profile.get("avatar_url");
				String email = (String) profile.get("email");
				String name = (String) profile.get("name");
				if (StringUtils.isBlank(email)) {
					email = fetchUserEmail(githubId, accessToken);
				}

				user.setAppid(getAppid(app));
				user.setIdentifier(Config.GITHUB_PREFIX + githubId);
				user.setEmail(email);
				user = User.readUserForIdentifier(user);
				if (user == null) {
					//user is new
					user = new User();
					user.setActive(true);
					user.setAppid(getAppid(app));
					user.setEmail(StringUtils.isBlank(email) ? Utils.getNewId() + "@github.com" : email);
					user.setName(StringUtils.isBlank(name) ? "No Name" : name);
					user.setPassword(Utils.generateSecurityToken());
					user.setPicture(getPicture(pic));
					user.setIdentifier(Config.GITHUB_PREFIX + githubId);
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
		return update;
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

	private String fetchUserEmail(Integer githubId, String accessToken) {
		HttpGet emailsGet = new HttpGet(PROFILE_URL + "/emails");
		emailsGet.setHeader(HttpHeaders.AUTHORIZATION, "token " + accessToken);
		emailsGet.setHeader(HttpHeaders.ACCEPT, "application/json");
		String defaultEmail = githubId + "@github.com";
		try (CloseableHttpResponse resp = httpclient.execute(emailsGet)) {
			HttpEntity respEntity = resp.getEntity();
			if (respEntity != null) {
				MappingIterator<Map<String, Object>> emails = jreader.readValues(respEntity.getContent());
				EntityUtils.consumeQuietly(respEntity);
				if (emails != null) {
					String email = null;
					while (emails.hasNext()) {
						Map<String, Object> next = emails.next();
						email = (String) next.get("email");
						if (next.containsKey("primary") && (Boolean) next.get("primary")) {
							break;
						}
					}
					return email;
				}
			}
		} catch (IOException e) {
			logger.warn("Failed to fetch user email from GitHub, using default: " + defaultEmail);
		}
		return defaultEmail;
	}

	private String getAppid(App app) {
		return (app == null) ? null : app.getAppIdentifier();
	}
}
