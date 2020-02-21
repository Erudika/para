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

package com.erudika.para.utils.filters;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Alternative to Spring Boot's ErrorPageFilter which was causing problems
 * with other filters, because it was trying to send errors after a response was committed.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErrorFilter implements Filter {

	private static final String ERROR_MESSAGE = "javax.servlet.error.message";
	private static final String ERROR_STATUS_CODE = "javax.servlet.error.status_code";

	@Override
	public void init(FilterConfig fc) throws ServletException {
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
			ErrorWrapperResponse wrapped = new ErrorWrapperResponse((HttpServletResponse) response);
			try {
				chain.doFilter(request, wrapped);
				int status = wrapped.getStatus();
				if (status >= 400) {
					setErrorAttributes(request, status, wrapped.getMessage());
				}
			} catch (Exception ex) {
//				rethrow(ex);
				LoggerFactory.getLogger(getClass()).error(null, ex);
			}
			response.flushBuffer();
		} else {
			chain.doFilter(request, response);
		}
	}

	@Override
	public void destroy() {
	}

	private void setErrorAttributes(ServletRequest request, int status, String message) {
		request.setAttribute(ERROR_STATUS_CODE, status);
		request.setAttribute(ERROR_MESSAGE, message);
	}

	private void rethrow(Throwable ex) throws IOException, ServletException {
		if (ex instanceof RuntimeException) {
			throw (RuntimeException) ex;
		}
		if (ex instanceof Error) {
			throw (Error) ex;
		}
		if (ex instanceof IOException) {
			throw (IOException) ex;
		}
		if (ex instanceof ServletException) {
			throw (ServletException) ex;
		}
		throw new IllegalStateException(ex);
	}

	/**
	 * HttpServletResponse wrapper.
	 */
	private static class ErrorWrapperResponse extends HttpServletResponseWrapper {

		private int status;
		private String message;

		ErrorWrapperResponse(HttpServletResponse response) {
			super(response);
		}

		@Override
		public void sendError(int status) throws IOException {
			sendError(status, null);
		}

		@Override
		public void sendError(int status, String message) throws IOException {
			this.status = status;
			this.message = message;
		}

		@Override
		public int getStatus() {
			return this.status;
		}

		public String getMessage() {
			return this.message;
		}
	}
}
