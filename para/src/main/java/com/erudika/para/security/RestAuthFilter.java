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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
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
		// We must wrap the request in order to read the InputStream twice:
		// - first read - used to calculate the signature
		// - second read - used as request payload
		BufferedRequestWrapper request = new BufferedRequestWrapper((HttpServletRequest) req);
		HttpServletResponse response = (HttpServletResponse) res;

		String appid = RestUtils.extractAccessKey(request);
		String date = RestUtils.extractDate(request);
		Date d = RestUtils.parseAWSDate(date);
		boolean requestExpired = (d != null) && (System.currentTimeMillis() >
				(d.getTime() + (Config.REQUEST_EXPIRES_AFTER_SEC * 1000)));

		if (!StringUtils.isBlank(appid)) {
			if (!StringUtils.isBlank(date)) {
				if (!requestExpired) {
					App app = new App();
					app.setId(appid);
					app = app.getDao().read(appid);

					if (app != null) {
						if (signer.isValidSignature(request, app.getSecret())) {
							SecurityContextHolder.getContext().setAuthentication(new AppAuthentication(app));
						} else {
							RestUtils.returnStatusResponse(response, HttpServletResponse.SC_FORBIDDEN,
									"Request signature is invalid.");
						}
					} else {
						RestUtils.returnStatusResponse(response, HttpServletResponse.SC_NOT_FOUND, "App not found.");
					}
				} else {
					RestUtils.returnStatusResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Request has expired.");
				}
			} else {
				RestUtils.returnStatusResponse(response, HttpServletResponse.SC_BAD_REQUEST,
						"'X-Amz-Date' header/parameter is not set!");
			}
		} else {
			RestUtils.returnStatusResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Credentials are missing.");
		}

		chain.doFilter(request, res);
	}


	private class BufferedRequestWrapper extends HttpServletRequestWrapper {

		ByteArrayInputStream bais;
		ByteArrayOutputStream baos;
		BufferedServletInputStream bsis;
		byte[] buffer;

		public BufferedRequestWrapper(HttpServletRequest req) throws IOException {
			super(req);
			InputStream is = req.getInputStream();
			baos = new ByteArrayOutputStream();
			byte buf[] = new byte[1024];
			int length;
			while ((length = is.read(buf)) > 0) {
				baos.write(buf, 0, length);
			}
			buffer = baos.toByteArray();
		}

		public ServletInputStream getInputStream() {
			try {
				bais = new ByteArrayInputStream(buffer);
				bsis = new BufferedServletInputStream(bais);
			} catch (Exception ex) {
				logger.error(ex);
			}
			return bsis;
		}

	}

	private class BufferedServletInputStream extends ServletInputStream {
		ByteArrayInputStream bais;

		public BufferedServletInputStream(ByteArrayInputStream bais) {
			this.bais = bais;
		}

		@Override
		public int available() {
			return bais.available();
		}

		@Override
		public int read() {
			return bais.read();
		}

		@Override
		public int read(byte[] buf, int off, int len) {
			return bais.read(buf, off, len);
		}

		@Override
		public boolean isFinished() {
			return bais.available() <= 0;
		}

		@Override
		public boolean isReady() {
			return !isFinished();
		}

		@Override
		public void setReadListener(ReadListener rl) {
		}
	}

}
