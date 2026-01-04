/*
 * Copyright 2013-2026 Erudika. https://erudika.com
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
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.server.metrics.MetricsUtils;
import com.erudika.para.server.utils.HealthUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.error.ErrorPage;
import org.springframework.boot.web.error.ErrorPageRegistrar;
import org.springframework.boot.web.error.ErrorPageRegistry;
import org.springframework.boot.web.server.servlet.ServletWebServerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Para modules are initialized and destroyed from here.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@SpringBootApplication
public class ParaServer implements Ordered {

	private static final Logger LOG = LoggerFactory.getLogger(ParaServer.class);
	private static final Map<String, String> FILE_CACHE = new ConcurrentHashMap<String, String>();
	private static ConfigurableApplicationContext appContext;

	/**
	 * The path of the API controller.
	 */
	public static final String API_PATH = "/v1";

	@Value("${server.ssl.enabled:false}")
	private boolean sslEnabled;

	/**
	 * Creates the main Para server bootstrapper.
	 */
	public ParaServer() {
		// default constructor
	}

	static {
		System.setProperty("server.port", String.valueOf(Para.getConfig().serverPort()));
		System.setProperty("server.port", String.valueOf(Para.getConfig().serverPort()));
		System.setProperty("server.servlet.context-path", Para.getConfig().serverContextPath());
		System.setProperty("server.use-forward-headers", String.valueOf(Para.getConfig().inProduction()));
		System.setProperty("para.logs_name", Para.getConfig().getConfigRootPrefix());
		if (Para.getConfig().accessLogEnabled()) {
			System.setProperty("server.jetty.accesslog.append", "true");
			System.setProperty("server.jetty.accesslog.enabled", "true");
			if (!System.getProperty("para.file_logger_level", "INFO").equalsIgnoreCase("OFF")) {
				System.setProperty("server.jetty.accesslog.filename", System.getProperty("para.logs_dir", ".")
						+ File.separator + Para.getConfig().getConfigRootPrefix() + "-access_yyyy_MM_dd.log");
				System.setProperty("server.jetty.accesslog.file-date-format", "yyyy_MM_dd");
			}
		}
		System.setProperty("para.landing_page_enabled", String.valueOf(Para.getConfig().landingPageEnabled()));
		System.setProperty("para.api_enabled", String.valueOf(Para.getConfig().apiEnabled()));
	}

	/**
	 * * Initializes the Para core modules and allows the user to override them. Call this method first.
	 *	This method calls {@code Para.initialize()}.
	 * @return ctx
	 */
	@EventListener({ContextRefreshedEvent.class})
	protected static void initialize() {
		Para.addInitListener(HealthUtils.getInstance());
		Para.addInitListener(MetricsUtils.getInstance());

		if (Para.getConfig().webhooksEnabled()) {
			Para.addIOListener(new WebhookIOListener());
		}

		Para.initialize();

		// this enables the "river" feature - polls the default queue for objects and imports them into Para
		// additionally, the polling feature is used for implementing a webhooks worker node
		if ((Para.getConfig().queuePollingEnabled() || Para.getConfig().webhooksEnabled())) {
			Para.getQueue().startPolling();
		}

		Para.getCustomResourceHandlers().forEach(crh -> {
			if (CustomResourceHandler.class.isAssignableFrom(crh.getClass())) {
				RequestMapping[] anno = crh.getClass().getAnnotationsByType(RequestMapping.class);
				String paths = "";
				if (anno != null && anno.length > 0 && anno[0] != null) {
					RequestMapping ann = anno[0];
					paths = String.join(",", (ann.path().length == 0) ? ann.value() : ann.path());
				}
				LOG.info("Registered custom resource handler {} at path(s) '{}'.", crh.getClass().getSimpleName(), paths);
			}
		});
	}

	/**
	 * Jetty config.
	 * @return Jetty config bean
	 */
	@Bean
	public ServletWebServerFactory jettyConfigBean() {
		TomcatServletWebServerFactory jef = new TomcatServletWebServerFactory();
		//JettyServletWebServerFactory jef = new JettyServletWebServerFactory();
		jef.setRegisterDefaultServlet(true);
		jef.setPort(Para.getConfig().serverPort());
		String contextPath = Para.getConfig().serverContextPath();
		if (StringUtils.length(contextPath) > 1 && contextPath.charAt(0) == '/') {
			jef.setContextPath(contextPath);
		}
		Map<String, String> params = new HashMap<>(jef.getSettings().getInitParameters());
		params.put("org.eclipse.jetty.servlet.Default.dirAllowed", "false");
		jef.setInitParameters(params);
		jef.setPort(Para.getConfig().serverPort());
		LOG.info("Instance #{} initialized and listening on http{}://localhost:{}{}",
				Para.getConfig().workerId(), (sslEnabled ? "s" : ""), jef.getPort(),
				Para.getConfig().serverContextPath());
		return jef;
	}

	/**
	 * Configures a custom Jackson object mapper.
	 * @return the {@link ParaObjectUtils#getJsonMapper()}
	 */
	@Bean
	public ObjectMapper jacksonObjectMapper() {
		return ParaObjectUtils.getJsonMapper();
	}

	/**
	 * @return Error page registry bean
	 */
	@Bean
	public ErrorPageRegistrar errorPageRegistrar() {
		return (ErrorPageRegistry epr) -> {
			epr.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/not-found"));
			epr.addErrorPages(new ErrorPage(HttpStatus.FORBIDDEN, "/error/403"));
			epr.addErrorPages(new ErrorPage(HttpStatus.UNAUTHORIZED, "/error/401"));
			epr.addErrorPages(new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/error/500"));
			epr.addErrorPages(new ErrorPage(HttpStatus.SERVICE_UNAVAILABLE, "/error/503"));
			epr.addErrorPages(new ErrorPage(HttpStatus.BAD_REQUEST, "/error/400"));
			epr.addErrorPages(new ErrorPage(HttpStatus.METHOD_NOT_ALLOWED, "/error/405"));
			epr.addErrorPages(new ErrorPage(Exception.class, "/error/500"));
		};
	}

	/**
	 * Loads a file from classpath.
	 * @param filePath a file path.
	 * @return file contents as a string
	 */
	public static String loadResource(String filePath) {
		if (filePath == null) {
			return "";
		}
		if (FILE_CACHE.containsKey(filePath) && Para.getConfig().inProduction()) {
			return FILE_CACHE.get(filePath);
		}
		String template = "";
		try (InputStream in = ParaServer.class.getClassLoader().getResourceAsStream(filePath)) {
			try (Scanner s = new Scanner(in).useDelimiter("\\A")) {
				template = s.hasNext() ? s.next() : "";
				if (!StringUtils.isBlank(template)) {
					FILE_CACHE.put(filePath, template);
				}
			}
		} catch (Exception ex) {
			LOG.info("Couldn't load resource '{}'.", filePath);
		}
		return template;
	}

	/**
	 * Calls all registered listeners on exit. Call this method last. This method calls {@code Para.destroy()}.
	 */
	public static void destroy() {
		Para.destroy();
		if (appContext != null) {
			appContext.close();
		}
	}

	/**
	 * Called before shutdown.
	 */
	@PreDestroy
	public void preDestroy() {
		destroy();
	}

	@Override
	public int getOrder() {
		return 1;
	}

	/**
	 * This is the initializing method when running ParaServer as executable JAR (or WAR),
	 * from the command line: java -jar para.jar.
	 * @param b Spring app builder
	 * @param sources the application classes that will be scanned
	 * @return the application builder
	 */
	public static SpringApplicationBuilder builder(SpringApplicationBuilder b, Class<?>... sources) {
		b.profiles(Para.getConfig().environment());
		b.sources(ParaServer.class);
		b.sources(sources);
		b.web(WebApplicationType.SERVLET);
		b.bannerMode(Para.getConfig().logoBannerEnabled() ? Banner.Mode.CONSOLE : Banner.Mode.OFF);
		if (Para.getConfig().pidFileEnabled()) {
			b.listeners(new ApplicationPidFileWriter(Config.PARA + "_" + Para.getConfig().serverPort() + ".pid"));
		}
		if (!Para.getCustomResourceHandlers().isEmpty()) {
			b.sources(Para.getCustomResourceHandlers().stream().map(c -> c.getClass()).toArray(Class[]::new));
		}
		return b;
	}

	/**
	 * Para starts from here.
	 * @param args args
	 */
	public static void main(String[] args) {
		// entry point (JAR)
		appContext = builder(new SpringApplicationBuilder()).run(args);
	}
}
