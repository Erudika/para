/*
 * Copyright 2013-2022 Erudika. http://erudika.com
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
package com.erudika.para.server.security;

import com.erudika.para.server.ParaServer;
import com.erudika.para.server.security.filters.SAMLMetadataFilter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * JwtConfigurer.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class JwtConfigurer extends AbstractHttpConfigurer<JwtConfigurer, HttpSecurity> {

	private final AuthenticationManager authenticationManager;

	/**
	 * Constructor.
	 */
	public JwtConfigurer(AuthenticationManager manager) {
		this.authenticationManager = manager;
	}

	@Override
	public void configure(HttpSecurity builder) throws Exception {
		JWTRestfulAuthFilter jwtAuthFilter = new JWTRestfulAuthFilter(authenticationManager);
		RestAuthFilter restAuthFilter = new RestAuthFilter();
		SAMLMetadataFilter samlMetadataFilter = new SAMLMetadataFilter();
		ParaServer.injectInto(jwtAuthFilter);

		jwtAuthFilter.getPasswordAuth().setAuthenticationManager(authenticationManager);
		builder.addFilterAfter(jwtAuthFilter.getPasswordAuth(), BasicAuthenticationFilter.class);

		jwtAuthFilter.getPasswordlessAuth().setAuthenticationManager(authenticationManager);
		builder.addFilterAfter(jwtAuthFilter.getPasswordlessAuth(), BasicAuthenticationFilter.class);

		jwtAuthFilter.getFacebookAuth().setAuthenticationManager(authenticationManager);
		builder.addFilterAfter(jwtAuthFilter.getFacebookAuth(), BasicAuthenticationFilter.class);

		jwtAuthFilter.getGoogleAuth().setAuthenticationManager(authenticationManager);
		builder.addFilterAfter(jwtAuthFilter.getGoogleAuth(), BasicAuthenticationFilter.class);

		jwtAuthFilter.getLinkedinAuth().setAuthenticationManager(authenticationManager);
		builder.addFilterAfter(jwtAuthFilter.getLinkedinAuth(), BasicAuthenticationFilter.class);

		jwtAuthFilter.getTwitterAuth().setAuthenticationManager(authenticationManager);
		builder.addFilterAfter(jwtAuthFilter.getTwitterAuth(), BasicAuthenticationFilter.class);

		jwtAuthFilter.getGithubAuth().setAuthenticationManager(authenticationManager);
		builder.addFilterAfter(jwtAuthFilter.getGithubAuth(), BasicAuthenticationFilter.class);

		jwtAuthFilter.getMicrosoftAuth().setAuthenticationManager(authenticationManager);
		builder.addFilterAfter(jwtAuthFilter.getMicrosoftAuth(), BasicAuthenticationFilter.class);

		jwtAuthFilter.getSlackAuth().setAuthenticationManager(authenticationManager);
		builder.addFilterAfter(jwtAuthFilter.getSlackAuth(), BasicAuthenticationFilter.class);

		jwtAuthFilter.getAmazonAuth().setAuthenticationManager(authenticationManager);
		builder.addFilterAfter(jwtAuthFilter.getAmazonAuth(), BasicAuthenticationFilter.class);

		jwtAuthFilter.getGenericOAuth2Auth().setAuthenticationManager(authenticationManager);
		builder.addFilterAfter(jwtAuthFilter.getGenericOAuth2Auth(), BasicAuthenticationFilter.class);

		jwtAuthFilter.getLdapAuth().setAuthenticationManager(authenticationManager);
		builder.addFilterAfter(jwtAuthFilter.getLdapAuth(), BasicAuthenticationFilter.class);

		jwtAuthFilter.getSamlAuth().setAuthenticationManager(authenticationManager);
		builder.addFilterAfter(jwtAuthFilter.getSamlAuth(), BasicAuthenticationFilter.class);
		builder.addFilterAfter(samlMetadataFilter, BasicAuthenticationFilter.class);

		builder.addFilterBefore(jwtAuthFilter, RememberMeAuthenticationFilter.class);

		builder.addFilterBefore(restAuthFilter, RememberMeAuthenticationFilter.class);
	}
}
