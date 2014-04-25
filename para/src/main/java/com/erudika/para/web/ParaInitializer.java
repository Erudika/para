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
package com.erudika.para.web;

import static com.erudika.para.core.User.Roles.*;
import com.erudika.para.security.SecurityConfig;
import java.util.EnumSet;
import java.util.ServiceLoader;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextListener;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.tuckey.web.filters.urlrewrite.UrlRewriteFilter;

/**
 * The default web application lifecycle listener.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ParaInitializer extends AbstractSecurityWebApplicationInitializer {

	/**
	 * No-args constructor
	 */
	public ParaInitializer() {
		super(SecurityConfig.class);
	}

	/**
	 * Executes this before Spring Security.
	 * @param sc context
	 */
	@Override
	protected void beforeSpringSecurityFilterChain(ServletContext sc) {
		// init/destroy
		sc.addListener(getContextListener());
	}

	/**
	 * Executes this after Spring Security.
	 * @param sc context
	 */
	@Override
	protected void afterSpringSecurityFilterChain(ServletContext sc) {
		// UrlRewriteFilter (nice URLs)
		FilterRegistration.Dynamic urf = sc.addFilter("urlRewriteFilter", UrlRewriteFilter.class);
		urf.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD), false, "/*");
		urf.setInitParameter("statusEnabled", "false");
		urf.setInitParameter("logLevel", "slf4j");
		// roles, rename JSESSIONID amd make it dissappear quickly (not used)
		sc.declareRoles(USER.toString(), MOD.toString(), ADMIN.toString());
		sc.getSessionCookieConfig().setName("sess");
		sc.getSessionCookieConfig().setMaxAge(1);
		sc.getSessionCookieConfig().setHttpOnly(true);
	}

	/**
	 * Try loading an external {@link javax.servlet.ServletContextListener} class
	 * via {@link java.util.ServiceLoader#load(java.lang.Class)}.
	 * @return a loaded ServletContextListener class.
	 * Defaults to {@link com.erudika.para.web.ParaContextListener}.
	 */
	private static Class<? extends ServletContextListener> getContextListener() {
		Class<? extends ServletContextListener> scl = ParaContextListener.class;
		ServiceLoader<ServletContextListener> loader = ServiceLoader.load(ServletContextListener.class);
		for (ServletContextListener module : loader) {
			if (module != null) {
				scl = module.getClass();
				break;
			}
		}
		return scl;
	}
}
