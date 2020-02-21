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
import com.erudika.para.security.OAuth1HmacSigner;
import com.erudika.para.security.SecurityUtils;
import com.erudika.para.security.UserAuthentication;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.ParseException;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class TwitterAuthFilter extends AbstractAuthenticationProcessingFilter {

	private final CloseableHttpClient httpclient;
	private final ObjectReader jreader;
	private static final String FLOW_URL1 = "https://api.twitter.com/oauth/request_token";
	private static final String FLOW_URL2 = "https://api.twitter.com/oauth/authenticate?";
	private static final String FLOW_URL3 = "https://api.twitter.com/oauth/access_token";
	private static final String PROFILE_URL = "https://api.twitter.com/1.1/account/verify_credentials.json";

	/**
	 * The default filter mapping.
	 */
	public static final String TWITTER_ACTION = "twitter_auth";

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public TwitterAuthFilter(final String defaultFilterProcessesUrl) {
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

		if (requestURI.endsWith(TWITTER_ACTION)) {
			String verifier = request.getParameter("oauth_verifier");
			String appid = SecurityUtils.getAppidFromAuthRequest(request);
			String denied = request.getParameter("denied");
			String redirectURI = SecurityUtils.getRedirectUrl(request) + (appid == null ? "" : "?appid=" + appid);
			App app = Para.getDAO().read(App.id(appid == null ? Config.getRootAppIdentifier() : appid));
			String[] keys = SecurityUtils.getOAuthKeysForApp(app, Config.TWITTER_PREFIX);
			if (denied != null) {
				throw new BadCredentialsException("Cancelled.");
			}

			if (verifier == null && stepOne(response, redirectURI, keys)) {
				return null;
			} else {
				userAuth = stepTwo(request, verifier, keys, app);
			}
		}

		return SecurityUtils.checkIfActive(userAuth, SecurityUtils.getAuthenticatedUser(userAuth), true);
	}

	private boolean stepOne(HttpServletResponse response, String redirectURI, String[] keys)
			throws ParseException, IOException {
		String callback = Utils.urlEncode(redirectURI);
		Map<String, String[]> params = new HashMap<>();
		params.put("oauth_callback", new String[]{callback});
		HttpPost tokenPost = new HttpPost(FLOW_URL1);
		tokenPost.setHeader(HttpHeaders.AUTHORIZATION, OAuth1HmacSigner.sign("POST", FLOW_URL1,
				params, keys[0], keys[1], null, null));
		tokenPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");

		try (CloseableHttpResponse resp1 = httpclient.execute(tokenPost)) {
			if (resp1.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
				String decoded = EntityUtils.toString(resp1.getEntity());
				EntityUtils.consumeQuietly(resp1.getEntity());
				for (String pair : decoded.split("&")) {
					if (pair.startsWith("oauth_token")) {
						response.sendRedirect(FLOW_URL2 + pair);
						return true;
					}
				}
			}
		}
		return false;
	}

	private UserAuthentication stepTwo(HttpServletRequest request, String verifier, String[] keys, App app)
			throws ParseException, UnsupportedEncodingException, IOException {
		String token = request.getParameter("oauth_token");
		Map<String, String[]> params = new HashMap<>();
		params.put("oauth_verifier", new String[]{verifier});
		HttpPost tokenPost = new HttpPost(FLOW_URL3);
		tokenPost.setEntity(new StringEntity("oauth_verifier=" + verifier));
		tokenPost.setHeader(HttpHeaders.AUTHORIZATION, OAuth1HmacSigner.sign("POST", FLOW_URL3,
				params, keys[0], keys[1], token, null));
		tokenPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");

		try (CloseableHttpResponse resp2 = httpclient.execute(tokenPost)) {
			if (resp2.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
				String decoded = EntityUtils.toString(resp2.getEntity());
				EntityUtils.consumeQuietly(resp2.getEntity());
				String oauthToken = null;
				String oauthSecret = null;
				for (String pair : decoded.split("&")) {
					if (pair.startsWith("oauth_token_secret")) {
						oauthSecret = pair.substring(19);
					} else if (pair.startsWith("oauth_token")) {
						oauthToken = pair.substring(12);
					}
				}
				return getOrCreateUser(app, oauthToken + Config.SEPARATOR + oauthSecret);
			}
		}
		return null;
	}

	/**
	 * Calls the Twitter API to get the user profile using a given access token.
	 * @param app the app where the user will be created, use null for root app
	 * @param accessToken token in the format "oauth_token:oauth_secret"
	 * @return {@link UserAuthentication} object or null if something went wrong
	 * @throws IOException ex
	 */
	public UserAuthentication getOrCreateUser(App app, String accessToken) throws IOException {
		UserAuthentication userAuth = null;
		User user = new User();
		if (accessToken != null && accessToken.contains(Config.SEPARATOR)) {
			String[] tokens = accessToken.split(Config.SEPARATOR);
			String[] keys = SecurityUtils.getOAuthKeysForApp(app, Config.TWITTER_PREFIX);

			Map<String, String[]> params2 = new HashMap<>();
			HttpGet profileGet = new HttpGet(PROFILE_URL + "?include_email=true");
			params2.put("include_email", new String[]{"true"});
			profileGet.setHeader(HttpHeaders.AUTHORIZATION, OAuth1HmacSigner.sign("GET", PROFILE_URL,
					params2, keys[0], keys[1], tokens[0], tokens[1]));

			Map<String, Object> profile = null;

			try (CloseableHttpResponse resp3 = httpclient.execute(profileGet)) {
				if (resp3.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
					profile = jreader.readValue(resp3.getEntity().getContent());
					EntityUtils.consumeQuietly(resp3.getEntity());
				}
			}

			if (profile != null && profile.containsKey("id_str")) {
				String twitterId = (String) profile.get("id_str");
				String pic = (String) profile.get("profile_image_url_https");
				String alias = (String) profile.get("screen_name");
				String name = (String) profile.get("name");
				String email = (String) profile.get("email");

				user.setAppid(getAppid(app));
				user.setIdentifier(Config.TWITTER_PREFIX + twitterId);
				user.setEmail(email);
				user = User.readUserForIdentifier(user);
				if (user == null) {
					//user is new
					user = new User();
					user.setActive(true);
					user.setAppid(getAppid(app));
					user.setEmail(StringUtils.isBlank(email) ? Utils.getNewId() + "@twitter.com" : email);
					user.setName(StringUtils.isBlank(name) ? alias : name);
					user.setPassword(Utils.generateSecurityToken());
					user.setPicture(getPicture(pic));
					user.setIdentifier(Config.TWITTER_PREFIX + twitterId);
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
			pic = pic.replace("_normal", "");
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
