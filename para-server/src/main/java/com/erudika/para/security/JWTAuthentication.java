/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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

	public JWTAuthentication(AuthenticatedUserDetails principal) {
		super(principal);
	}

	public JWTAuthentication withJWT(SignedJWT jwt) {
		this.jwt = jwt;
		return this;
	}

	public JWTAuthentication withApp(App app) {
		this.app = app;
		return this;
	}

	public SignedJWT getJwt() {
		return jwt;
	}

	public App getApp() {
		return app;
	}

	public String getAppid() {
		if (app != null) {
			return app.getId();
		} else if (claims != null && claims.getClaims() != null) {
			return (String) claims.getClaims().get("appid");
		}
		return null;
	}

	public String getUserid() {
		if (claims != null) {
			return claims.getSubject();
		}
		return null;
	}

	public JWTClaimsSet getClaims() {
		return claims;
	}
}
