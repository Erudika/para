/*
 * Copyright 2013-2026 Erudika. http://erudika.com
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

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * HttpServletRequest wrapper.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class BufferedRequestWrapper extends HttpServletRequestWrapper {

	private final byte[] body;

	/**
	 * Default constructor.
	 * @param request the request to wrap
	 * @throws IOException if error
	 */
	public BufferedRequestWrapper(HttpServletRequest request) throws IOException {
		super(request);
		this.body = request.getInputStream().readAllBytes();
	}

	@Override
	public ServletInputStream getInputStream() {
		return new BufferedServletInputStream(new ByteArrayInputStream(body));
	}

	@Override
	public BufferedReader getReader() throws IOException {
		String enc = getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.UTF_8.name();
		return new BufferedReader(new InputStreamReader(getInputStream(), enc));
	}

	/**
	 * Returns the cached body as byte array.
	 * @return the body
	 */
	public byte[] getCachedBody() {
		return body.clone();
	}
}
