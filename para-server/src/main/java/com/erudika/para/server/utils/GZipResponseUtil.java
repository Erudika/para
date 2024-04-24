/*
 * Copyright 2013-2022 Erudika. https://erudika.com
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
package com.erudika.para.server.utils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A collection of response processing utilities, which are shared between 2 or more filters.
 * @author Greg Luck
 * @version $Id: ResponseUtil.java 744 2008-08-16 20:10:49Z gregluck $
 */
public final class GZipResponseUtil {

	private static final Logger log = LoggerFactory.getLogger(GZipResponseUtil.class);

	/**
	 * Gzipping an empty file or stream always results in a 20 byte output This is in java or elsewhere.
	 * On a unix system to reproduce do <code>gzip -n empty_file</code>. -n tells gzip to not include the file name. The
	 * resulting file size is 20 bytes.
	 * Therefore 20 bytes can be used indicate that the gzip byte[] will be empty when ungzipped.
	 */
	private static final int EMPTY_GZIPPED_CONTENT_SIZE = 20;

	/**
	 * Utility class. No public constructor.
	 */
	private GZipResponseUtil() { }

	/**
	 * Checks whether a gzipped body is actually empty and should just be zero. When the compressedBytes is
	 * {@link #EMPTY_GZIPPED_CONTENT_SIZE} it should be zero.
	 * @param compressedBytes the gzipped response body
	 * @param request the client HTTP request
	 * @return true if the response should be 0, even if it is isn't.
	 */
	public static boolean shouldGzippedBodyBeZero(byte[] compressedBytes, HttpServletRequest request) {

		//Check for 0 length body
		if (compressedBytes.length == EMPTY_GZIPPED_CONTENT_SIZE) {
			if (log.isTraceEnabled()) {
				log.trace("{} resulted in an empty response.", request.getRequestURL());
			}
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Performs a number of checks to ensure response saneness according to the rules of RFC2616:
	 * <ol>
	 * <li>If the response code is {@link jakarta.servlet.http.HttpServletResponse#SC_NO_CONTENT} then it is illegal for
	 * the body to contain anything. See http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.5
	 * <li>If the response code is {@link jakarta.servlet.http.HttpServletResponse#SC_NOT_MODIFIED} then it is illegal for
	 * the body to contain anything. See http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.5
	 * </ol>
	 * @param request the client HTTP request
	 * @param responseStatus the responseStatus
	 * @return true if the response should be 0, even if it is isn't.
	 */
	public static boolean shouldBodyBeZero(HttpServletRequest request, int responseStatus) {

		//Check for NO_CONTENT
		if (responseStatus == HttpServletResponse.SC_NO_CONTENT) {
			if (log.isDebugEnabled()) {
				log.debug("{} resulted in a {} response. Removing message body in accordance with RFC2616.",
						request.getRequestURL(), HttpServletResponse.SC_NO_CONTENT);
			}
			return true;
		}

		//Check for NOT_MODIFIED
		if (responseStatus == HttpServletResponse.SC_NOT_MODIFIED) {
			if (log.isDebugEnabled()) {
				log.debug("{} resulted in a {} response. Removing message body in accordance with RFC2616.",
						request.getRequestURL(), HttpServletResponse.SC_NOT_MODIFIED);
			}
			return true;
		}
		return false;
	}

	/**
	 * Adds the gzip HTTP header to the response. This is needed when a gzipped body is returned so that browsers can
	 * properly decompress it.
	 * @param response the response which will have a header added to it. I.e this method changes its parameter
	 * @throws ServletException Either the response is committed or we were called using the
	 * include method from a
	 * {@link jakarta.servlet.RequestDispatcher#include(javax.servlet.ServletRequest, jakarta.servlet.ServletResponse)}
	 * method and the set header is ignored.
	 */
	public static void addGzipHeader(final HttpServletResponse response) throws ServletException {
		response.setHeader("Content-Encoding", "gzip");
		boolean containsEncoding = response.containsHeader("Content-Encoding");
		if (!containsEncoding) {
			throw new ServletException("Failure when attempting to set Content-Encoding: gzip");
		}
	}

	/**
	 * Adds the Vary: Accept-Encoding header to the response if needed.
	 * @param wrapper response
	 */
	public static void addVaryAcceptEncoding(final GZipServletResponseWrapper wrapper) {
		Collection<String> headers = wrapper.getHeaderNames();

		String varyHeader = null;
		for (String header : headers) {
			if (header.equals("Vary")) {
				varyHeader = wrapper.getHeader(header);
				break;
			}
		}

		if (varyHeader == null) {
			wrapper.setHeader("Vary", "Accept-Encoding");
		} else {
			if (!varyHeader.equals("*") && !varyHeader.contains("Accept-Encoding")) {
				wrapper.setHeader("Vary", varyHeader + ",Accept-Encoding");
			}
		}
	}

}
