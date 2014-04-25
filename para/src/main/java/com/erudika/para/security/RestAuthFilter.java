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

import com.erudika.para.core.App;
import com.erudika.para.rest.RestUtils;
import com.erudika.para.rest.Signer;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.io.IOException;
import java.util.Date;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

/**
 * Authenticates an {@link com.erudika.para.core.App} by verifying its credentials.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class RestAuthFilter extends GenericFilterBean implements InitializingBean {

	private final Signer signer;

	/**
	 * Default constructor
	 * @param signer a request signer instance for request signature verification
	 */
	public RestAuthFilter(Signer signer) {
		this.signer = signer;
	}

	/**
	 * Authenticates an application.
	 * @param req a request
	 * @param res a response
	 * @param chain filter chain
	 * @throws IOException ex
	 * @throws ServletException ex
	 */
	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;

		String key = RestUtils.extractAccessKey(request);
		String date = RestUtils.extractDate(request);
		Date d = RestUtils.parseAWSDate(date);
		boolean requestExpired = (d != null) && (System.currentTimeMillis() >
				(d.getTime() + (Config.REQUEST_EXPIRES_AFTER_SEC * 1000)));

		if (req.isSecure()) {
			if (!StringUtils.isBlank(key)) {
				String id = Utils.base64dec(key);

				if (StringUtils.isBlank(date)) {
					RestUtils.returnStatusResponse(response, HttpServletResponse.SC_BAD_REQUEST,
							"'X-Amz-Date' header/parameter is not set!");
				} else {
					if (requestExpired) {
						RestUtils.returnStatusResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Request has expired.");
					} else if (!StringUtils.isBlank(id)) {
						App app = new App();
						app.setId(id);
						app = App.readApp(app);

						if (app != null) {
							if (signer.isValidSignature(request, app.getSecret())) {
								SecurityContextHolder.getContext().setAuthentication(new AppAuthentication(app));
							} else {
								RestUtils.returnStatusResponse(response, HttpServletResponse.SC_FORBIDDEN,
										"Signature is invalid.");
							}
						} else {
							RestUtils.returnStatusResponse(response, HttpServletResponse.SC_NOT_FOUND, "App not found.");
						}
					} else {
						RestUtils.returnStatusResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Bad request.");
					}
				}
			} else {
				RestUtils.returnStatusResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Credentials are missing.");
			}
		}

		chain.doFilter(req, res);
	}

}
