/*
 * Copyright 2013 Alex Bogdanovski <albogdano@me.com>.
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

import com.erudika.para.security.SecurityFilter;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletRegistration;
import org.glassfish.jersey.servlet.ServletContainer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.tuckey.web.filters.urlrewrite.UrlRewriteFilter;

/**
 * Web application lifecycle listener.
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public abstract class ParaContextListener extends GuiceServletContextListener {
	
//	protected abstract Injector getInjector();
//	@Override
//	protected Injector getInjector() {
////		return Utils.initDI(new ServletModule(){
////			@Override
////			protected void configureServlets() {
////				super.configureServlets();
////			}		
////		});
//		return null;
//	}
	
	public void contextInitialized(ServletContextEvent sce) {
		super.contextInitialized(sce);
		
		ServletContext sc = sce.getServletContext();
		// Guice (Dependency Injection & AOP)
		sc.addFilter("guiceFilter", GuiceFilter.class).addMappingForServletNames(null, false, "/*");
		// Spring Security (Authetication - Facebook, OpenID, cookie verification etc.)
		sc.addListener(ContextLoaderListener.class);
		sc.setInitParameter("contextConfigLocation", "/WEB-INF/applicationContext-security.xml");
		sc.addFilter("springSecurityFilterChain", DelegatingFilterProxy.class).addMappingForUrlPatterns(null, false, "/*");
		// Security Filter (Anti-CSRF, REST JSON checks, etc.)
		sc.addFilter("securityFilter", SecurityFilter.class).addMappingForUrlPatterns(null, false, "/*");
		// UrlRewriteFilter (nice URLs)
		FilterRegistration.Dynamic urf = sc.addFilter("urlRewriteFilter", UrlRewriteFilter.class);
		urf.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD), false, "/*");
		urf.setInitParameter("confPath", "/WEB-INF/urlrewrite.xml");
		urf.setInitParameter("logLevel", "WARN");
		urf.setInitParameter("statusEnabled", "false");
		// Jersey REST service (XML, JSON APIs)
		ServletRegistration.Dynamic jersey = sc.addServlet("servletAdaptor", ServletContainer.class);
		jersey.setLoadOnStartup(1);
		jersey.addMapping("/api/*");
	}

	public void contextDestroyed(ServletContextEvent sce) {
		super.contextDestroyed(sce);
	}
}