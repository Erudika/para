/**
 * Copyright 2012-2013 eBay Software Foundation, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.erudika.para.utils.filters;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("unchecked")
public class MockHttpServletResponse implements HttpServletResponse {
    List headerNames = new ArrayList();
    List headerValues = new ArrayList();
    PrintWriter pw;
    int status;

    public String getCharacterEncoding() {

        throw new RuntimeException("Not implemented");
    }

    public String getContentType() {

        throw new RuntimeException("Not implemented");
    }

    public ServletOutputStream getOutputStream() throws IOException {

        throw new RuntimeException("Not implemented");
    }

    public PrintWriter getWriter() throws IOException {
        if (pw == null) {
            pw = new PrintWriter(new StringWriter());
        }
        return pw;
    }

    public void setCharacterEncoding(String charset) {
        throw new RuntimeException("Not implemented");
    }

    public void setContentLength(int len) {
        throw new RuntimeException("Not implemented");
    }

    public void setContentType(String type) {

    }

    public void setBufferSize(int size) {
        throw new RuntimeException("Not implemented");
    }

    public int getBufferSize() {
        throw new RuntimeException("Not implemented");
    }

    public void flushBuffer() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    public void resetBuffer() {
    }

    public boolean isCommitted() {
        throw new RuntimeException("Not implemented");
    }

    public void reset() {

    }

    public void setLocale(Locale loc) {
        throw new RuntimeException("Not implemented");
    }

    public Locale getLocale() {

        throw new RuntimeException("Not implemented");
    }

    public void addCookie(Cookie cookie) {
        throw new RuntimeException("Not implemented");
    }

    public boolean containsHeader(String name) {
        throw new RuntimeException("Not implemented");
    }

    public String encodeURL(String url) {

        throw new RuntimeException("Not implemented");
    }

    public String encodeRedirectURL(String url) {

        throw new RuntimeException("Not implemented");
    }

	@Deprecated
    public String encodeUrl(String url) {

        throw new RuntimeException("Not implemented");
    }

	@Deprecated
    public String encodeRedirectUrl(String url) {

        throw new RuntimeException("Not implemented");
    }

    public void sendError(int sc, String msg) throws IOException {

    }

    public void sendError(int sc) throws IOException {

    }

    public void sendRedirect(String location) throws IOException {
        throw new RuntimeException("Not implemented");
    }

    public void setDateHeader(String name, long date) {
        throw new RuntimeException("Not implemented");
    }

    public void addDateHeader(String name, long date) {
        throw new RuntimeException("Not implemented");
    }

    public String getHeader(String name) {
        int index = headerNames.indexOf(name);
        if (index != -1) {
            return (String) headerValues.get(index);
        }
        return null;
    }

    public void setHeader(String name, String value) {
        int index = headerNames.indexOf(name);
        if (index != -1) {
            headerValues.set(index, value);
        } else {
            headerNames.add(name);
            headerValues.add(value);
        }
    }

    public void addHeader(String name, String value) {
        headerNames.add(name);
        headerValues.add(value);
    }

    public void setIntHeader(String name, int value) {
        throw new RuntimeException("Not implemented");
    }

    public void addIntHeader(String name, int value) {
        throw new RuntimeException("Not implemented");
    }

    public void setStatus(int sc) {
        this.status = sc;
    }

    public int getStatus() {
        return this.status;
    }

	@Deprecated
    public void setStatus(int sc, String sm) {

    }

	@Override
	public Collection<String> getHeaders(String name) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Collection<String> getHeaderNames() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void setContentLengthLong(long len) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

}
