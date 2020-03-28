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
package com.erudika.para;

import ch.qos.logback.access.jetty.RequestLogImpl;
import com.erudika.para.aop.AOPModule;
import com.erudika.para.cache.CacheModule;
import com.erudika.para.email.EmailModule;
import com.erudika.para.i18n.I18nModule;
import com.erudika.para.iot.IoTModule;
import com.erudika.para.metrics.MetricsUtils;
import com.erudika.para.persistence.PersistenceModule;
import com.erudika.para.queue.QueueModule;
import com.erudika.para.rest.Api1;
import com.erudika.para.rest.CustomResourceHandler;
import com.erudika.para.search.SearchModule;
import com.erudika.para.security.JWTRestfulAuthFilter;
import com.erudika.para.security.SecurityModule;
import com.erudika.para.storage.StorageModule;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.HealthUtils;
import com.erudika.para.utils.filters.CORSFilter;
import com.erudika.para.utils.filters.ErrorFilter;
import com.erudika.para.utils.filters.GZipServletFilter;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.util.Modules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.ParentContextApplicationContextInitializer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.boot.web.embedded.jetty.JettyServerCustomizer;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.boot.web.servlet.support.ServletContextApplicationContextInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

/**
 * Para modules are initialized and destroyed from here.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Configuration
@ComponentScan
public class ParaServer implements WebApplicationInitializer, Ordered {

	private static final Logger logger = LoggerFactory.getLogger(ParaServer.class);
	private static LinkedList<CustomResourceHandler> customResourceHandlers;
	private static Injector injector;

	/**
	 * Returns the list of core modules.
	 * @return the core modules
	 */
	public static Module[] getCoreModules() {
		return new Module[] {
			new PersistenceModule(),
			new SearchModule(),
			new CacheModule(),
			new QueueModule(),
			new AOPModule(),
			new IoTModule(),
			new EmailModule(),
			new I18nModule(),
			new StorageModule(),
			new SecurityModule()
		};
	}

	/**
	 * Initializes the Para core modules and allows the user to override them. Call this method first.
	 *	This method calls {@code Para.initialize()}.
	 * @param modules a list of modules that override the main modules
	 */
	public static void initialize(Module... modules) {
		Stage stage = Config.IN_PRODUCTION ? Stage.PRODUCTION : Stage.DEVELOPMENT;

		List<Module> coreModules = Arrays.asList(modules);
		List<Module> externalModules = getExternalModules();

		if (coreModules.isEmpty() && externalModules.isEmpty()) {
			logger.warn("No implementing modules found. Aborting...");
			destroy();
			return;
		}

		if (!externalModules.isEmpty()) {
			injector = Guice.createInjector(stage, Modules.override(coreModules).with(externalModules));
		} else {
			injector = Guice.createInjector(stage, coreModules);
		}

		Para.addInitListener(HealthUtils.getInstance());
		Para.addInitListener(MetricsUtils.getInstance());

		Para.getInitListeners().forEach((initListener) -> {
			injectInto(initListener);
		});

		if (Config.WEBHOOKS_ENABLED) {
			Para.addIOListener(new WebhookIOListener());
		}

		Para.initialize();

		// this enables the "river" feature - polls the default queue for objects and imports them into Para
		// additionally, the polling feature is used for implementing a webhooks worker node
		if ((Config.getConfigBoolean("queue_link_enabled", false) || Config.WEBHOOKS_ENABLED) &&
				HealthUtils.getInstance().isHealthy()) {
			Para.getQueue().startPolling();
		}
	}

	/**
	 * Calls all registered listeners on exit. Call this method last.
	 * This method calls {@code Para.destroy()}.
	 */
	public static void destroy() {
		Para.getDestroyListeners().forEach((destroyListener) -> {
			injectInto(destroyListener);
		});
		Para.destroy();
	}

	/**
	 * Inject dependencies into a given object.
	 *
	 * @param obj the object we inject into
	 */
	public static void injectInto(Object obj) {
		if (obj == null) {
			return;
		}
		if (injector == null) {
			handleNotInitializedError();
		}
		injector.injectMembers(obj);
	}

	/**
	 * Return an instance of some class if it has been wired through DI.
	 *
	 * @param <T> any type
	 * @param type any type
	 * @return an object
	 */
	public static <T> T getInstance(Class<T> type) {
		if (injector == null) {
			handleNotInitializedError();
		}
		return injector.getInstance(type);
	}

	/**
	 * Try loading external {@link com.erudika.para.rest.CustomResourceHandler} classes. These will handle custom API
	 * requests. via {@link java.util.ServiceLoader#load(java.lang.Class)}.
	 *
	 * @return a loaded list of ServletContextListener class.
	 */
	public static List<CustomResourceHandler> getCustomResourceHandlers() {
		if (customResourceHandlers == null) {
			customResourceHandlers = new LinkedList<>();
			ServiceLoader<CustomResourceHandler> loader = ServiceLoader.
					load(CustomResourceHandler.class, Para.getParaClassLoader());
			for (CustomResourceHandler handler : loader) {
				if (handler != null) {
					injectInto(handler);
					customResourceHandlers.add(handler);
				}
			}
		}
		return Collections.unmodifiableList(customResourceHandlers);
	}

	private static List<Module> getExternalModules() {
		ServiceLoader<Module> moduleLoader = ServiceLoader.load(Module.class, Para.getParaClassLoader());
		List<Module> externalModules = new ArrayList<>();
		for (Module module : moduleLoader) {
			externalModules.add(module);
		}
		return externalModules;
	}

	private static void handleNotInitializedError() {
		throw new IllegalStateException("Call ParaServer.initialize() first!");
	}

	@Override
	public int getOrder() {
		return 1;
	}

	/**
	 * @return API servlet bean
	 */
	@Bean
	public ServletRegistrationBean<?> apiV1RegistrationBean() {
		String path = Api1.PATH + "*";
		ServletRegistrationBean<?> reg = new ServletRegistrationBean<>(new ServletContainer(new Api1()), path);
		logger.debug("Initializing Para API v1 [{}]...", path);
		reg.setName(Api1.class.getSimpleName());
		reg.setAsyncSupported(true);
		reg.setEnabled(true);
		reg.setOrder(3);
		return reg;
	}

	/**
	 * @return GZIP filter bean
	 */
	@Bean
	public FilterRegistrationBean<?> gzipFilterRegistrationBean() {
		String path = Api1.PATH + "*";
		FilterRegistrationBean<?> frb = new FilterRegistrationBean<>(new GZipServletFilter());
		logger.debug("Initializing GZip filter [{}]...", path);
		frb.addUrlPatterns(path);
		frb.setAsyncSupported(true);
		frb.setEnabled(Config.GZIP_ENABLED);
		frb.setMatchAfter(true);
		frb.setOrder(20);
		return frb;
	}

	/**
	 * @return CORS filter bean
	 */
	@Bean
	public FilterRegistrationBean<?> corsFilterRegistrationBean() {
		String path = Api1.PATH + "*";
		logger.debug("Initializing CORS filter [{}]...", path);
		FilterRegistrationBean<?> frb = new FilterRegistrationBean<>(new CORSFilter());
		frb.addInitParameter("cors.support.credentials", "true");
		frb.addInitParameter("cors.allowed.methods", "GET,POST,PATCH,PUT,DELETE,HEAD,OPTIONS");
		frb.addInitParameter("cors.exposed.headers", "Cache-Control,Content-Length,Content-Type,Date,ETag,Expires");
		frb.addInitParameter("cors.allowed.headers", "Origin,Accept,X-Requested-With,Content-Type,"
				+ "Access-Control-Request-Method,Access-Control-Request-Headers,X-Amz-Credential,"
				+ "X-Amz-Date,Authorization");
		frb.addUrlPatterns(path, "/" + JWTRestfulAuthFilter.JWT_ACTION);
		frb.setAsyncSupported(true);
		frb.setEnabled(Config.CORS_ENABLED);
		frb.setMatchAfter(false);
		frb.setOrder(2);
		return frb;
	}

	/**
	 * @return Jetty config bean
	 */
	@Bean
	public ServletWebServerFactory jettyConfigBean() {
		JettyServletWebServerFactory jef = new JettyServletWebServerFactory();
		jef.addServerCustomizers((JettyServerCustomizer) (Server server) -> {
			if (Config.getConfigBoolean("access_log_enabled", true)) {
				// enable access log via Logback
				HandlerCollection handlers = new HandlerCollection();
				for (Handler handler : server.getHandlers()) {
					handlers.addHandler(handler);
				}
				RequestLogHandler reqLogs = new RequestLogHandler();
				reqLogs.setServer(server);
				RequestLogImpl rli = new RequestLogImpl();
				rli.setResource("/logback-access.xml");
				rli.setQuiet(true);
				rli.start();
				reqLogs.setRequestLog(rli);
				handlers.addHandler(reqLogs);
				server.setHandler(handlers);
			}

			for (Connector y : server.getConnectors()) {
				for (ConnectionFactory cf : y.getConnectionFactories()) {
					if (cf instanceof HttpConnectionFactory) {
						HttpConnectionFactory dcf = (HttpConnectionFactory) cf;
						// support for X-Forwarded-Proto
						// redirect back to https if original request uses it
						if (Config.IN_PRODUCTION) {
							ForwardedRequestCustomizer frc = new ForwardedRequestCustomizer() {
								public void customize(Connector connector, HttpConfiguration config, Request request) {
									super.customize(connector, config, request);
									String cfProto = request.getHeader("CloudFront-Forwarded-Proto");
									if (StringUtils.equalsIgnoreCase(cfProto, config.getSecureScheme())) {
										request.setScheme(cfProto);
										request.setSecure(true);
									}
								}
							};
							HttpConfiguration httpConfiguration = dcf.getHttpConfiguration();
							httpConfiguration.addCustomizer(frc);
						}
						// Disable Jetty version header
						dcf.getHttpConfiguration().setSendServerVersion(false);
						// Increase idle timeout
						dcf.getHttpConfiguration().setIdleTimeout(TimeUnit.MINUTES.toMillis(5));
					}
				}
			}
		});
		String contextPath = Config.getConfigParam("context_path", "");
		if (StringUtils.length(contextPath) > 1 && contextPath.charAt(0) == '/') {
			jef.setContextPath(contextPath);
		}
		jef.setPort(getServerPort());
		logger.info("Listening on port {}...", jef.getPort());
		return jef;
	}

	/**
	 * @return the server port
	 */
	public static int getServerPort() {
		int defaultPort = NumberUtils.toInt(System.getProperty("jetty.http.port"), Config.getConfigInt("port", 8080));
		return NumberUtils.toInt(System.getProperty("server.port"), defaultPort);
	}

	/**
	 * Called before shutdown.
	 */
	@PreDestroy
	public void preDestroy() {
		destroy();
	}

	@Override
	public void onStartup(ServletContext sc) throws ServletException {
		runAsWAR(sc, ParaServer.class);
	}

	/**
	 * This is the initializing method when running ParaServer as WAR,
	 * deployed to a servlet container.
	 * @param sc the ServletContext instance
	 * @param sources the application classes that will be scanned
	 * @return the application context
	 */
	public static WebApplicationContext runAsWAR(ServletContext sc, Class<?>... sources) {
		ApplicationContext parent = null;
		Object object = sc.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		if (object instanceof ApplicationContext) {
			logger.info("Root context already created (using as parent).");
			parent = (ApplicationContext) object;
			sc.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, null);
		}
		SpringApplicationBuilder application = new SpringApplicationBuilder(sources);
		if (parent != null) {
			application.initializers(new ParentContextApplicationContextInitializer(parent));
		}
		application.initializers(new ServletContextApplicationContextInitializer(sc));
		application.contextClass(AnnotationConfigServletWebServerApplicationContext.class);

		// entry point (WAR)
		application.profiles(Config.ENVIRONMENT);
		application.web(WebApplicationType.SERVLET);
		application.bannerMode(Banner.Mode.OFF);
		initialize(getCoreModules());
		// Ensure error pages are registered
		application.sources(ErrorFilter.class);

		WebApplicationContext rootAppContext = (WebApplicationContext) application.run();

		if (rootAppContext != null) {
			sc.addListener(new ContextLoaderListener(rootAppContext) {
				@Override
				public void contextInitialized(ServletContextEvent event) {
					// no-op because the application context is already initialized
				}
			});
			sc.getSessionCookieConfig().setName("sess");
			sc.getSessionCookieConfig().setMaxAge(1);
			sc.getSessionCookieConfig().setHttpOnly(true);
			sc.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");
		}
		return rootAppContext;
	}

	/**
	 * This is the initializing method when running ParaServer as executable JAR (or WAR),
	 * from the command line: java -jar para.jar.
	 * @param args command line arguments array (same as those in {@code void main(String[] args)} )
	 * @param sources the application classes that will be scanned
	 */
	public static void runAsJAR(String[] args, Class<?>... sources) {
		// entry point (JAR)
		SpringApplication app = new SpringApplication(sources);
		app.setAdditionalProfiles(Config.ENVIRONMENT);
		app.setWebApplicationType(WebApplicationType.SERVLET);
		app.setBannerMode(Banner.Mode.OFF);
		if (Config.getConfigBoolean("pidfile_enabled", true)) {
			app.addListeners(new ApplicationPidFileWriter(Config.PARA + "_" + getServerPort() + ".pid"));
		}
		initialize(getCoreModules());
		app.run(args);
	}

	/**
	 * Para starts from here.
	 * @param args args
	 */
	public static void main(String[] args) {
		runAsJAR(args, ParaServer.class);
	}
}
