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
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
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

		if (requestURI.endsWith(FACEBOOK_ACTION)) {
			String authCode = request.getParameter("code");
			if (!StringUtils.isBlank(authCode)) {
				String appid = request.getParameter(Config._APPID);
				String redirectURI = request.getRequestURL().toString() + (appid == null ? "" : "?appid=" + appid);
				String[] keys = SecurityUtils.getCustomAuthSettings(appid, Config.FB_PREFIX, request);
				CloseableHttpResponse resp1 = null;
				String url = Utils.formatMessage(TOKEN_URL, authCode, redirectURI, keys[0], keys[1]);
				try {
					HttpGet tokenPost = new HttpGet(url);
					resp1 = httpclient.execute(tokenPost);
				} catch (Exception e) {
					logger.warn("Facebook auth request failed: GET " + url, e);
				}

				if (resp1 != null && resp1.getEntity() != null) {
					String token = EntityUtils.toString(resp1.getEntity(), Config.DEFAULT_ENCODING);
					if (token != null && token.startsWith("access_token")) {
						String accessToken = token.substring(token.indexOf("=") + 1, token.indexOf("&"));
						userAuth = getOrCreateUser(appid, accessToken);
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
	 * Calls the Facebook API to get the user profile using a given access token.
	 * @param appid app identifier of the parent app, use null for root app
	 * @param accessToken access token
	 * @return {@link UserAuthentication} object or null if something went wrong
	 * @throws IOException ex
	 */
	@SuppressWarnings("unchecked")
	public UserAuthentication getOrCreateUser(String appid, String accessToken) throws IOException {
		UserAuthentication userAuth = null;
		if (accessToken != null) {
			User user = new User();
			user.setAppid(appid);

			String ctype = null;
			HttpEntity respEntity = null;
			CloseableHttpResponse resp2 = null;
			try {
				HttpGet profileGet = new HttpGet(PROFILE_URL + accessToken);
				resp2 = httpclient.execute(profileGet);
				respEntity = resp2.getEntity();
				ctype = resp2.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue();
			} catch (Exception e) {
				logger.warn("Facebook auth request failed: GET " + PROFILE_URL + accessToken, e);
			}

			if (respEntity != null && Utils.isJsonType(ctype)) {
				Map<String, Object> profile = jreader.readValue(respEntity.getContent());

				if (profile != null && profile.containsKey("id")) {
					String fbId = (String) profile.get("id");
					Map<String, Object> pic = (Map<String, Object>) profile.get("picture");
					String email = (String) profile.get("email");
					String name = (String) profile.get("name");

					user.setIdentifier(Config.FB_PREFIX.concat(fbId));
					user = User.readUserForIdentifier(user);
					if (user == null) {
						//user is new
						user = new User();
						user.setActive(true);
						user.setAppid(appid);
						user.setEmail(StringUtils.isBlank(email) ? fbId + "@facebook.com" : email);
						user.setName(StringUtils.isBlank(name) ? "No Name" : name);
						user.setPassword(new UUID().toString());
						user.setPicture(getPicture(fbId, pic));
						user.setIdentifier(Config.FB_PREFIX.concat(fbId));
						String id = user.create();
						if (id == null) {
							throw new AuthenticationServiceException("Authentication failed: cannot create new user.");
						}
					} else {
						String picture = getPicture(fbId, pic);
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
				EntityUtils.consumeQuietly(respEntity);
			}
		}
		return userAuth;
	}

	@SuppressWarnings("unchecked")
	private static String getPicture(String fbId, Map<String, Object> pic) {
		if (pic != null) {
			Map<String, Object> data = (Map<String, Object>) pic.get("data");
			// try to get the direct url to the profile pic
			if (data != null && data.containsKey("url")) {
				return (String) data.get("url");
			} else {
				return "http://graph.facebook.com/" + fbId + "/picture?width=400&height=400&type=square";
			}
		}
		return null;
	}
}
