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

import com.erudika.para.utils.GZipResponseUtil;
import com.erudika.para.utils.GZipServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Provides GZIP compression of responses.
 * See the filter-mappings.xml entry for the gzip filter for the URL patterns which will be gzipped. At present this
 * includes .jsp, .js and .css.
 * @author <a href="mailto:gluck@thoughtworks.com">Greg Luck</a>
 * @author <a href="mailto:amurdoch@thoughtworks.com">Adam Murdoch</a>
 * @version $Id: GzipFilter.java 744 2008-08-16 20:10:49Z gregluck $
 */
public class GZipServletFilter implements Filter {

	private final Logger log = LoggerFactory.getLogger(GZipServletFilter.class);

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// Nothing to initialize
	}

	@Override
	public void destroy() {
		// Nothing to destroy
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		if (!isIncluded(httpRequest) && acceptsGZipEncoding(httpRequest) && !response.isCommitted()) {
			// Client accepts zipped content
			if (log.isTraceEnabled()) {
				log.trace("{} Written with gzip compression", httpRequest.getRequestURL());
			}

			// Create a gzip stream
			final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
			final GZIPOutputStream gzout = new GZIPOutputStream(compressed);

			// Handle the request
			final GZipServletResponseWrapper wrapper = new GZipServletResponseWrapper(httpResponse, gzout);
			wrapper.setDisableFlushBuffer(true);
			chain.doFilter(request, wrapper);
			wrapper.flush();

			gzout.close();

            // double check one more time before writing out
			// repsonse might have been committed due to error
			if (response.isCommitted()) {
				return;
			}

			// return on these special cases when content is empty or unchanged
			switch (wrapper.getStatus()) {
				case HttpServletResponse.SC_NO_CONTENT:
				case HttpServletResponse.SC_RESET_CONTENT:
				case HttpServletResponse.SC_NOT_MODIFIED:
					return;
				default:
			}

			// Saneness checks
			byte[] compressedBytes = compressed.toByteArray();
			boolean shouldGzippedBodyBeZero = GZipResponseUtil.shouldGzippedBodyBeZero(compressedBytes, httpRequest);
			boolean shouldBodyBeZero = GZipResponseUtil.shouldBodyBeZero(httpRequest, wrapper.getStatus());
			if (shouldGzippedBodyBeZero || shouldBodyBeZero) {
                // No reason to add GZIP headers or write body if no content was written or status code specifies no
				// content
				response.setContentLength(0);
				return;
			}

			// Write the zipped body
			GZipResponseUtil.addGzipHeader(httpResponse);

			// Only write out header Vary as needed
			GZipResponseUtil.addVaryAcceptEncoding(wrapper);

			response.setContentLength(compressedBytes.length);

			response.getOutputStream().write(compressedBytes);
		} else {
			// Client does not accept zipped content - don't bother zipping
			if (log.isTraceEnabled()) {
				log.trace("{} Written without gzip compression because the request does not accept gzip",
						httpRequest.getRequestURL());
			}
			chain.doFilter(request, response);
		}
	}

	/**
	 * Checks if the request uri is an include. These cannot be gzipped.
	 */
	private boolean isIncluded(final HttpServletRequest request) {
		final String uri = (String) request.getAttribute("javax.servlet.include.request_uri");
		final boolean includeRequest = !(uri == null);

		if (includeRequest && log.isDebugEnabled()) {
			log.debug("{} resulted in an include request. This is unusable, because"
					+ "the response will be assembled into the overrall response. Not gzipping.",
					request.getRequestURL());
		}
		return includeRequest;
	}

	private boolean acceptsGZipEncoding(HttpServletRequest httpRequest) {
		String acceptEncoding = httpRequest.getHeader("Accept-Encoding");
		return acceptEncoding != null && acceptEncoding.contains("gzip");
	}
}
