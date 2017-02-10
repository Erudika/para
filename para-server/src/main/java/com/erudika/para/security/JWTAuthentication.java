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
package com.erudika.para.security;

import com.erudika.para.core.App;
import com.erudika.para.utils.Config;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.LoggerFactory;

/**
 * Represents a user authentication with JWT.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class JWTAuthentication extends UserAuthentication {

	private static final long serialVersionUID = 1L;
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(JWTAuthentication.class);

	private App app;
	private SignedJWT jwt;
	private JWTClaimsSet claims;

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

	/**
	 * @return appid
	 */
	public String getAppid() {
		if (app != null) {
			return app.getId();
		} else if (claims != null && claims.getClaims() != null) {
			return (String) claims.getClaims().get(Config._APPID);
		}
		return null;
	}

	/**
	 * @return userid
	 */
	public String getUserid() {
		if (claims != null) {
			return claims.getSubject();
		}
		return null;
	}

	/**
	 * @return claims set
	 */
	public JWTClaimsSet getClaims() {
		return claims;
	}
}
