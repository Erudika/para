/*
 * Copyright 2013-2021 Erudika. https://erudika.com
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

/**
 * A filter that handles authentication requests to Facebook.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class FacebookAuthFilter extends AbstractAuthenticationProcessingFilter {

	private final CloseableHttpClient httpclient;
	private final ObjectReader jreader;
	private static final String PROFILE_URL = "https://graph.facebook.com/me?"
			+ "fields=name,email,picture.width(400).type(square).height(400)&access_token=";
	private static final String TOKEN_URL = "https://graph.facebook.com/oauth/access_token?"
			+ "code={0}&redirect_uri={1}&client_id={2}&client_secret={3}";
	/**
	 * The default filter mapping.
	 */
	public static final String FACEBOOK_ACTION = "facebook_auth";

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public FacebookAuthFilter(String defaultFilterProcessesUrl) {
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

		if (requestURI.endsWith(FACEBOOK_ACTION)) {
			String authCode = request.getParameter("code");
			if (!StringUtils.isBlank(authCode)) {
				String appid = SecurityUtils.getAppidFromAuthRequest(request);
				String redirectURI = SecurityUtils.getRedirectUrl(request);
				App app = Para.getDAO().read(App.id(appid == null ? Para.getConfig().getRootAppIdentifier() : appid));
				String[] keys = SecurityUtils.getOAuthKeysForApp(app, Config.FB_PREFIX);
				String url = Utils.formatMessage(TOKEN_URL, authCode, redirectURI, keys[0], keys[1]);
				HttpGet tokenPost = new HttpGet(url);
				try (CloseableHttpResponse resp1 = httpclient.execute(tokenPost)) {
					// Facebook keep changing their API so we try to read the access_token by the old and new ways
					String token = EntityUtils.toString(resp1.getEntity(), Para.getConfig().defaultEncoding());
					String accessToken = parseAccessToken(token);
					if (accessToken != null) {
						userAuth = getOrCreateUser(app, accessToken);
					} else {
						logger.info("Authentication request failed with status '"
								+ resp1.getReasonPhrase() + "' - " + token);
					}
				} catch (Exception e) {
					logger.warn("Facebook auth request failed: GET " + url, e);
				}
			}
		}

		return SecurityUtils.checkIfActive(userAuth, SecurityUtils.getAuthenticatedUser(userAuth), true);
	}

	/**
	 * Calls the Facebook API to get the user profile using a given access token.
	 * @param app the app where the user will be created, use null for root app
	 * @param accessToken access token
	 * @return {@link UserAuthentication} object or null if something went wrong
	 * @throws IOException ex
	 */
	@SuppressWarnings("unchecked")
	public UserAuthentication getOrCreateUser(App app, String accessToken) throws IOException {
		UserAuthentication userAuth = null;
		User user = new User();
		if (accessToken != null) {
			HttpGet profileGet = new HttpGet(PROFILE_URL + accessToken);
			try (CloseableHttpResponse resp2 = httpclient.execute(profileGet)) {
				HttpEntity respEntity = resp2.getEntity();
				String ctype = resp2.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue();

				if (respEntity != null && Utils.isJsonType(ctype)) {
					Map<String, Object> profile = jreader.readValue(respEntity.getContent());

					if (profile != null && profile.containsKey("id")) {
						String fbId = (String) profile.get("id");
						String email = (String) profile.get("email");
						String name = (String) profile.get("name");

						user.setAppid(getAppid(app));
						user.setIdentifier(Config.FB_PREFIX.concat(fbId));
						user.setEmail(email);
						user = User.readUserForIdentifier(user);
						if (user == null) {
							//user is new
							user = new User();
							user.setActive(true);
							user.setAppid(getAppid(app));
							user.setEmail(StringUtils.isBlank(email) ? Utils.getNewId() + "@facebook.com" : email);
							user.setName(StringUtils.isBlank(name) ? "No Name" : name);
							user.setPassword(Utils.generateSecurityToken());
							user.setPicture(getPicture(fbId));
							user.setIdentifier(Config.FB_PREFIX.concat(fbId));
							String id = user.create();
							if (id == null) {
								throw new AuthenticationServiceException("Authentication failed: cannot create new user.");
							}
						} else {
							if (updateUserInfo(user, fbId, email, name)) {
								user.update();
							}
						}
						userAuth = new UserAuthentication(new AuthenticatedUserDetails(user));
					} else {
						logger.info("Authentication request failed because user profile doesn't contain the expected attributes");
					}
				} else {
					logger.info("Authentication request failed because response was missing or contained invalid JSON.");
				}
			} catch (Exception e) {
				logger.warn("Facebook auth request failed: GET " + PROFILE_URL + accessToken, e);
			}
		}
		return SecurityUtils.checkIfActive(userAuth, user, false);
	}

	private boolean updateUserInfo(User user, String fbId, String email, String name) {
		String picture = getPicture(fbId);
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

	@SuppressWarnings("unchecked")
	private static String getPicture(String fbId) {
		if (fbId != null) {
			return "https://graph.facebook.com/" + fbId + "/picture?width=700&height=700&type=square";
		}
		return null;
	}

	private String parseAccessToken(String token) throws JsonProcessingException {
		if (token != null) {
			if (token.startsWith("access_token")) {
				return token.substring(token.indexOf('=') + 1, token.indexOf('&'));
			} else {
				Map<String, Object> tokenObject = jreader.readValue(token);
				if (tokenObject != null && tokenObject.containsKey("access_token")) {
					return (String) tokenObject.get("access_token");
				}
			}
		}
		return null;
	}

	private String getAppid(App app) {
		return (app == null) ? null : app.getAppIdentifier();
	}
}
