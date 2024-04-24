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
package com.erudika.para.server;

import com.erudika.para.core.listeners.WebhookIOListener;
import com.erudika.para.core.rest.CustomResourceHandler;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.Para;
import com.erudika.para.server.aop.AOPModule;
import com.erudika.para.server.cache.CacheModule;
import com.erudika.para.server.email.EmailModule;
import com.erudika.para.server.metrics.MetricsUtils;
import com.erudika.para.server.persistence.PersistenceModule;
import com.erudika.para.server.queue.QueueModule;
import com.erudika.para.server.rest.Api1;
import com.erudika.para.server.search.SearchModule;
import com.erudika.para.server.security.JWTRestfulAuthFilter;
import com.erudika.para.server.security.SecurityModule;
import com.erudika.para.server.storage.StorageModule;
import com.erudika.para.server.utils.HealthUtils;
import com.erudika.para.server.utils.filters.CORSFilter;
import com.erudika.para.server.utils.filters.ErrorFilter;
import com.erudika.para.server.utils.filters.GZipServletFilter;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.util.Modules;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.boot.web.embedded.jetty.JettyServerCustomizer;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Para modules are initialized and destroyed from here.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@SpringBootApplication
public class ParaServer extends SpringBootServletInitializer implements Ordered {

	private static final Logger LOG = LoggerFactory.getLogger(ParaServer.class);
	private static LinkedList<CustomResourceHandler> customResourceHandlers;
	private static Injector injector;

	@Value("${server.ssl.enabled:false}")
	private boolean sslEnabled;

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
			new EmailModule(),
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
		Stage stage = Para.getConfig().inProduction() ? Stage.PRODUCTION : Stage.DEVELOPMENT;

		List<Module> coreModules = Arrays.asList(modules);
		List<Module> externalModules = getExternalModules();

		if (coreModules.isEmpty() && externalModules.isEmpty()) {
			LOG.warn("No implementing modules found. Aborting...");
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

		if (Para.getConfig().webhooksEnabled()) {
			Para.addIOListener(new WebhookIOListener());
		}

		Para.initialize();

		// this enables the "river" feature - polls the default queue for objects and imports them into Para
		// additionally, the polling feature is used for implementing a webhooks worker node
		if ((Para.getConfig().queuePollingEnabled() || Para.getConfig().webhooksEnabled())) {
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
	 * Try loading external {@link com.erudika.para.core.rest.CustomResourceHandler} classes. These will handle custom API
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
		LOG.debug("Initializing Para API v1 [{}]...", path);
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
		LOG.debug("Initializing GZip filter [{}]...", path);
		frb.addUrlPatterns(path);
		frb.setAsyncSupported(true);
		frb.setEnabled(Para.getConfig().gzipEnabled());
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
		LOG.debug("Initializing CORS filter [{}]...", path);
		FilterRegistrationBean<?> frb = new FilterRegistrationBean<>(new CORSFilter());
		frb.addInitParameter("cors.support.credentials", "true");
		frb.addInitParameter("cors.allowed.methods", "GET,POST,PATCH,PUT,DELETE,HEAD,OPTIONS");
		frb.addInitParameter("cors.exposed.headers", "Cache-Control,Content-Length,Content-Type,Date,ETag,Expires");
		frb.addInitParameter("cors.allowed.headers", "Origin,Accept,X-Requested-With,Content-Type,"
				+ "Access-Control-Request-Method,Access-Control-Request-Headers,X-Amz-Credential,"
				+ "X-Amz-Date,Authorization");
		frb.addUrlPatterns(path, "/" + JWTRestfulAuthFilter.JWT_ACTION);
		frb.setAsyncSupported(true);
		frb.setEnabled(Para.getConfig().corsEnabled());
		frb.setMatchAfter(false);
		frb.setOrder(HIGHEST_PRECEDENCE);
		return frb;
	}

	/**
	 * @return Jetty config bean
	 */
	@Bean
	public ServletWebServerFactory jettyConfigBean() {
		if (Para.getConfig().accessLogEnabled()) {
			System.setProperty("server.jetty.accesslog.append", "true");
			System.setProperty("server.jetty.accesslog.enabled", "true");
			if (!System.getProperty("para.file_logger_level", "INFO").equalsIgnoreCase("OFF")) {
				System.setProperty("server.jetty.accesslog.filename", System.getProperty("para.logs_dir", ".")
						+ File.separator + Para.getConfig().getConfigRootPrefix() + "-access.log");
			}
		}
		JettyServletWebServerFactory jef = new JettyServletWebServerFactory();
		jef.setRegisterDefaultServlet(true);
		jef.addServerCustomizers((JettyServerCustomizer) (Server server) -> {
			for (Connector y : server.getConnectors()) {
				for (ConnectionFactory cf : y.getConnectionFactories()) {
					if (cf instanceof HttpConnectionFactory) {
						HttpConnectionFactory dcf = (HttpConnectionFactory) cf;
						// support for X-Forwarded-Proto
						// redirect back to https if original request uses it
						if (Para.getConfig().inProduction()) {
							ForwardedRequestCustomizer frc = new ForwardedRequestCustomizer() {
								public void customize(Connector connector, HttpConfiguration config, Request request) {
									//super.customize(connector, config, request);
									String cfProto = request.getHeaders().get("CloudFront-Forwarded-Proto");
									if (StringUtils.isBlank(cfProto)) {
										cfProto = request.getHeaders().get("X-Forwarded-Proto");
									}
									if (StringUtils.equalsIgnoreCase(cfProto, config.getSecureScheme())) {
										setForwardedProtoHeader(cfProto);
										setSslIsSecure(true);
										//request.setScheme(cfProto);
										//request.setSecure(true);
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
		String contextPath = Para.getConfig().serverContextPath();
		if (StringUtils.length(contextPath) > 1 && contextPath.charAt(0) == '/') {
			jef.setContextPath(contextPath);
		}
		Map<String, String> params = new HashMap<>(jef.getInitParameters());
		params.put("org.eclipse.jetty.servlet.Default.dirAllowed", "false");
		jef.setInitParameters(params);
		jef.getSession().getCookie().setName("sess");
		jef.getSession().getCookie().setMaxAge(Duration.ofSeconds(1));
		jef.getSession().getCookie().setHttpOnly(true);
		jef.setPort(Para.getConfig().serverPort());
		LOG.info("Instance #{} initialized and listening on http{}://localhost:{}{}",
				Para.getConfig().workerId(), (sslEnabled ? "s" : ""), jef.getPort(),
				Para.getConfig().serverContextPath());
		return jef;
	}

	/**
	 * Called before shutdown.
	 */
	@PreDestroy
	public void preDestroy() {
		destroy();
	}

	/**
	 * This is the initializing method when running ParaServer as WAR,
	 * deployed to a servlet container.
	 * @param app the Spring app builder instance
	 * @return the application builder
	 */
	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder app) {
		// entry point (WAR)
		return builder(app, true);
	}

	/**
	 * This is the initializing method when running ParaServer as executable JAR (or WAR),
	 * from the command line: java -jar para.jar.
	 * @param b Spring app builder
	 * @param isWar is it started from a WAR file
	 * @param sources the application classes that will be scanned
	 * @return the application builder
	 */
	public static SpringApplicationBuilder builder(SpringApplicationBuilder b, boolean isWar, Class<?>... sources) {
		b.profiles(Para.getConfig().environment());
		b.sources(ParaServer.class);
		b.sources(sources);
		b.web(WebApplicationType.SERVLET);
		b.bannerMode(Banner.Mode.OFF);
		if (Para.getConfig().pidFileEnabled()) {
			b.listeners(new ApplicationPidFileWriter(Config.PARA + "_" + Para.getConfig().serverPort() + ".pid"));
		}
		if (isWar) {
			b.sources(ErrorFilter.class);
		}
		initialize(getCoreModules());
		return b;
	}

	/**
	 * Para starts from here.
	 * @param args args
	 */
	public static void main(String[] args) {
		// entry point (JAR)
		builder(new SpringApplicationBuilder(), false).run(args);
	}
}
