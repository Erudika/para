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

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * This filter is used in production, to put HTTP cache headers with a long (1 month) expiration time.
 *
 * Adapted from JHipster: https://github.com/jhipster/generator-jhipster
 *
 * @author Julien Dubois
 * @author Jérôme Mirc
 */
public class CachingHttpHeadersFilter implements Filter {

	// Cache period is 1 month (in ms)
	private static final long CACHE_PERIOD = TimeUnit.DAYS.toMillis(31L);

	// We consider the last modified date is the start up time of the server
	private static final long LAST_MODIFIED = System.currentTimeMillis();

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// Nothing to initialize
	}

	@Override
	public void destroy() {
		// Nothing to destroy
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		httpResponse.setHeader("Cache-Control", "max-age=2678400000, public");
		httpResponse.setHeader("Pragma", "cache");

		// Setting Expires header, for proxy caching
		httpResponse.setDateHeader("Expires", CACHE_PERIOD + System.currentTimeMillis());

		// Setting the Last-Modified header, for browser caching
		httpResponse.setDateHeader("Last-Modified", LAST_MODIFIED);

		chain.doFilter(request, response);
	}
}
