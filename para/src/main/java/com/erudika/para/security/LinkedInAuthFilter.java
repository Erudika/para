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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.security.authentication.AbstractAuthenticationToken;
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

	private static final String PROFILE_URL = "https://api.linkedin.com/v1/people/~"
			+ ":(id,firstName,lastName,email-address,picture-url)?format=json&oauth2_access_token=";
	private static final String TOKEN_URL = "https://www.linkedin.com/uas/oauth2/accessToken?"
			+ "grant_type=authorization_code&code={0}&redirect_uri={1}&client_id={2}&client_secret={3}";

	/**
	 * The default filter mapping
	 */
	public static final String LINKEDIN_ACTION = "/member/register/linkedin";

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public LinkedInAuthFilter(final String defaultFilterProcessesUrl) {
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
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		final String requestURI = request.getRequestURI();
		Authentication userAuth = null;
		User user = new User();

		if (requestURI.endsWith(LINKEDIN_ACTION)) {
			String authCode = request.getParameter("code");
			if (!StringUtils.isBlank(authCode)) {
				String url = Utils.formatMessage(TOKEN_URL, authCode,
						request.getRequestURL().toString(), Config.LINKEDIN_APP_ID, Config.LINKEDIN_SECRET);

				CloseableHttpClient httpclient = HttpClients.createDefault();
				HttpPost tokenPost = new HttpPost(url);
				CloseableHttpResponse resp1 = httpclient.execute(tokenPost);
				ObjectReader jreader = Utils.getJsonReader(Map.class);

				if (resp1 != null && resp1.getEntity() != null) {
					Map<String, Object> token = jreader.readValue(resp1.getEntity().getContent());
					if (token != null && token.containsKey("access_token")) {
						// got valid token
						HttpGet profileGet = new HttpGet(PROFILE_URL + token.get("access_token"));
						CloseableHttpResponse resp2 = httpclient.execute(profileGet);
						if (resp2 != null && resp2.getEntity() != null) {
							Map<String, Object> profile = jreader.readValue(resp2.getEntity().getContent());

							if (profile != null && profile.containsKey("id")) {
								String linkedInID = (String) profile.get("id");
								String email = (String) profile.get("emailAddress");
								String fName = (String) profile.get("firstName");
								String lName = (String) profile.get("lastName");
								String name = fName + " " + lName;

								user.setIdentifier(Config.LINKEDIN_PREFIX.concat(linkedInID));
								final User user1 = User.readUserForIdentifier(user);
								user1.setEmail(StringUtils.isBlank(email) ? "email@domain.com" : email);
								user1.setName(StringUtils.isBlank(name) ? "No Name" : name);
								user1.setPassword(new UUID().toString());
								user1.setIdentifier(Config.LINKEDIN_PREFIX.concat(linkedInID));
								if (user1.getPicture() == null) {
									String pic = (String) profile.get("pictureUrl");
									if (pic != null) {
										user1.setPicture(pic);
									} else {
										user1.setPicture("http://www.gravatar.com/avatar?d=mm&size=200");
									}
								}

								String id = user.create();
								if (id == null) {
									throw new AuthenticationServiceException("Authentication failed: cannot create new user.");
								} else {
									userAuth = new AbstractAuthenticationToken(user1.getAuthorities()) {
										private static final long serialVersionUID = 1L;
										public Object getCredentials() {
											return user1.getIdentifier();
										}
										public Object getPrincipal() {
											return user1;
										}
									};
									userAuth.setAuthenticated(true);
									user = user1;
								}
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
