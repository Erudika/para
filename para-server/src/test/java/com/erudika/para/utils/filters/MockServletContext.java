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

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;

@SuppressWarnings("unchecked")
public class MockServletContext implements ServletContext {

    public String getContextPath() {
        throw new RuntimeException("Not implemented");
    }

    public ServletContext getContext(String uripath) {
        throw new RuntimeException("Not implemented");
    }

    public int getMajorVersion() {
        throw new RuntimeException("Not implemented");
    }

    public int getMinorVersion() {
        throw new RuntimeException("Not implemented");
    }

    public String getMimeType(String file) {
        throw new RuntimeException("Not implemented");
    }

    public Set getResourcePaths(String path) {
        throw new RuntimeException("Not implemented");
    }

    public URL getResource(String path) throws MalformedURLException {
        throw new RuntimeException("Not implemented");
    }

    public InputStream getResourceAsStream(String path) {
        throw new RuntimeException("Not implemented");
    }

    public RequestDispatcher getRequestDispatcher(String path) {

        throw new RuntimeException("Not implemented");
    }

    public RequestDispatcher getNamedDispatcher(String name) {

        throw new RuntimeException("Not implemented");
    }

	@Deprecated
    public Servlet getServlet(String name) throws ServletException {

        throw new RuntimeException("Not implemented");
    }

	@Deprecated
    public Enumeration getServlets() {

        throw new RuntimeException("Not implemented");
    }

	@Deprecated
    public Enumeration getServletNames() {

        throw new RuntimeException("Not implemented");
    }

    public void log(String msg) {
        // NOOP
    }

	@Deprecated
    public void log(Exception exception, String msg) {
        // NOOP
    }

    public void log(String message, Throwable throwable) {
        // NOOP
    }

    public String getRealPath(String path) {

        throw new RuntimeException("Not implemented");
    }

    public String getServerInfo() {

        throw new RuntimeException("Not implemented");
    }

    public String getInitParameter(String name) {

        throw new RuntimeException("Not implemented");
    }

    public Enumeration getInitParameterNames() {

        throw new RuntimeException("Not implemented");
    }

    public Object getAttribute(String name) {

        throw new RuntimeException("Not implemented");
    }

    public Enumeration getAttributeNames() {

        throw new RuntimeException("Not implemented");
    }

    public void setAttribute(String name, Object object) {
        throw new RuntimeException("Not implemented");
    }

    public void removeAttribute(String name) {
        throw new RuntimeException("Not implemented");
    }

    public String getServletContextName() {

        throw new RuntimeException("Not implemented");
    }

	@Override
	public int getEffectiveMajorVersion() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public int getEffectiveMinorVersion() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public boolean setInitParameter(String name, String value) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public ServletRegistration.Dynamic addServlet(String servletName, String className) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public ServletRegistration getServletRegistration(String servletName) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Map<String, ? extends ServletRegistration> getServletRegistrations() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, String className) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public FilterRegistration getFilterRegistration(String filterName) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public SessionCookieConfig getSessionCookieConfig() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void addListener(String className) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public <T extends EventListener> void addListener(T t) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void addListener(Class<? extends EventListener> listenerClass) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public JspConfigDescriptor getJspConfigDescriptor() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public ClassLoader getClassLoader() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void declareRoles(String... roleNames) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public String getVirtualServerName() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public ServletRegistration.Dynamic addJspFile(String string, String string1) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public int getSessionTimeout() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void setSessionTimeout(int i) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public String getRequestCharacterEncoding() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void setRequestCharacterEncoding(String string) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public String getResponseCharacterEncoding() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void setResponseCharacterEncoding(String string) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

}
