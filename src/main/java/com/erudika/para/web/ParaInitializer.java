/*
 * Copyright 2013 Alex Bogdanovski <alex@erudika.com>.
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
 * You can reach the author at: https://github.com/albogdano
 */
package com.erudika.para.web;

import static com.erudika.para.core.User.Roles.*;
import com.erudika.para.security.SecurityConfig;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import org.glassfish.jersey.servlet.ServletContainer;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.tuckey.web.filters.urlrewrite.UrlRewriteFilter;

/**
 * The default web application lifecycle listener.
 * @author Alex Bogdanovski <alex@erudika.com>
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
		// Para init/destroy
		sc.addListener(ParaContextListener.class);
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
//		urf.setInitParameter("confPath", "/WEB-INF/urlrewrite.xml");
		urf.setInitParameter("statusEnabled", "false");
		urf.setInitParameter("logLevel", "slf4j");
		// Jersey REST service (XML, JSON APIs)
		ServletRegistration.Dynamic jersey = sc.addServlet("servletAdaptor", ServletContainer.class);
		jersey.setLoadOnStartup(1);
		jersey.addMapping("/api/*");
		// roles, rename JSESSIONID amd make it dissappear quickly (not used)
		sc.declareRoles(USER.toString(), MOD.toString(), ADMIN.toString());
		sc.getSessionCookieConfig().setName("sess");
		sc.getSessionCookieConfig().setMaxAge(1);
		sc.getSessionCookieConfig().setHttpOnly(true);
	}

}
