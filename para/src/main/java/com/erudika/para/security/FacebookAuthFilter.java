/*
 * Copyright 2013-2014 Erudika. http://erudika.com
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
package com.erudika.para.security;

import com.eaio.uuid.UUID;
import com.erudika.para.core.User;
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

	private static final String PROFILE_URL = "https://graph.facebook.com/me?"
			+ "fields=name,email,picture.width(400).type(square).height(400)&access_token=";
	private static final String TOKEN_URL = "https://graph.facebook.com/oauth/access_token?"
			+ "code={0}&redirect_uri={1}&client_id={2}&client_secret={3}";
	/**
	 * The default filter mapping
	 */
	public static final String FACEBOOK_ACTION = "facebook_auth";

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public FacebookAuthFilter(String defaultFilterProcessesUrl) {
		super(defaultFilterProcessesUrl);
	}

	/**
	 * Handles an authentication request.
	 * @param request HTTP request
	 * @param response HTTP response
	 * @return an authentication object that contains the principal object if successful.
	 * @throws IOException ex
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		final String requestURI = request.getRequestURI();
		Authentication userAuth = null;
		User user = new User();

		if (requestURI.endsWith(FACEBOOK_ACTION)) {
			String authCode = request.getParameter("code");
			if (!StringUtils.isBlank(authCode)) {
				String url = Utils.formatMessage(TOKEN_URL, authCode,
						request.getRequestURL().toString(), Config.FB_APP_ID, Config.FB_SECRET);

				CloseableHttpClient httpclient = HttpClients.createDefault();
				HttpGet tokenPost = new HttpGet(url);
				CloseableHttpResponse resp1 = httpclient.execute(tokenPost);
				ObjectReader jreader = Utils.getJsonReader(Map.class);

				if (resp1 != null && resp1.getEntity() != null) {
					String token = EntityUtils.toString(resp1.getEntity(), Config.DEFAULT_ENCODING);
					if (token != null && token.startsWith("access_token")) {
						// got valid token
						String accessToken = token.substring(token.indexOf("=") + 1, token.indexOf("&"));
						HttpGet profileGet = new HttpGet(PROFILE_URL + accessToken);
						CloseableHttpResponse resp2 = httpclient.execute(profileGet);
						HttpEntity respEntity = resp2.getEntity();
						String ctype = resp2.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue();

						if (respEntity != null && Utils.isJsonType(ctype)) {
							Map<String, Object> profile = jreader.readValue(resp2.getEntity().getContent());

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
									user.setEmail(StringUtils.isBlank(email) ? "email@domain.com" : email);
									user.setName(StringUtils.isBlank(name) ? "No Name" : name);
									user.setPassword(new UUID().toString());
									user.setIdentifier(Config.FB_PREFIX.concat(fbId));
									if (user.getPicture() == null && pic != null) {
										Map<String, Object> data = (Map<String, Object>) pic.get("data");
										// try to get the direct url to the profile pic
										if (data != null && data.containsKey("url")) {
											user.setPicture((String) data.get("url"));
										} else {
											user.setPicture("http://graph.facebook.com/" + fbId +
													"/picture?width=400&height=400&type=square");
										}
									}

									String id = user.create();
									if (id == null) {
										throw new AuthenticationServiceException("Authentication failed: cannot create new user.");
									}
								}
								userAuth = new UserAuthentication(user);
							}
							EntityUtils.consumeQuietly(resp2.getEntity());
						}
						EntityUtils.consumeQuietly(resp1.getEntity());
					}
				}
			}
		}

		if (userAuth == null || user == null || user.getIdentifier() == null) {
			throw new BadCredentialsException("Bad credentials.");
		} else if (!user.isEnabled()) {
			throw new LockedException("Account is locked.");
		}
		return userAuth;
	}
}
