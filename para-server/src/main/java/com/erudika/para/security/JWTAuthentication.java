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
package com.erudika.para.security;

import com.erudika.para.core.App;
import com.nimbusds.jwt.SignedJWT;

/**
 * Represents a user authentication with JWT.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class JWTAuthentication extends UserAuthentication {

	private static final long serialVersionUID = 1L;

	private App app;
	private SignedJWT jwt;

	/**
	 * Default constructor.
	 * @param principal a user object
	 */
	public JWTAuthentication(AuthenticatedUserDetails principal) {
		super(principal);
	}

	/**
	 * @param jwt token
	 * @return this
	 */
	public JWTAuthentication withJWT(SignedJWT jwt) {
		this.jwt = jwt;
		return this;
	}

	/**
	 * @param app {@link App}
	 * @return this
	 */
	public JWTAuthentication withApp(App app) {
		this.app = app;
		return this;
	}

	/**
	 * @return the JWT token
	 */
	public SignedJWT getJwt() {
		return jwt;
	}

	/**
	 * @return the {@link App}
	 */
	public App getApp() {
		return app;
	}
}
