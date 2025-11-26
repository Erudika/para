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
package com.erudika.para.server.security;

import com.erudika.para.core.utils.Para;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import jakarta.annotation.security.DeclareRoles;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Programmatic configuration for Spring Security.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Configuration
@EnableWebSecurity
@DeclareRoles({ "ROLE_USER", "ROLE_MOD", "ROLE_ADMIN", "ROLE_APP" })
public class SecurityConfig {

	private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
	private static final String[] DEFAULT_ROLES = {"USER", "MOD", "ADMIN", "APP"};


	/**
	 * No-args constructor.
	 */
	public SecurityConfig() {
	}

	/**
	 * Configures the unsecured public resources.
	 * @return web
	 */
	@Bean
	public WebSecurityCustomizer webSecurityCustomizer() {
		return (web) -> {
			DefaultHttpFirewall firewall = new DefaultHttpFirewall();
			firewall.setAllowUrlEncodedSlash(true);
			web.httpFirewall(firewall);
			//web.debug(true);
		};
	}

	/**
	 * Configures the protected private resources.
	 *
	 * @param http HTTP sec object
	 * @throws Exception ex
	 * @return http
	 */
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		String signinPath = Para.getConfig().signinPath();
		String signoutPath = Para.getConfig().signoutPath();
		String accessDeniedPath = Para.getConfig().accessDeniedPath();
		String signoutSuccessPath = Para.getConfig().signoutSuccessPath();
		ConfigObject protectedResources = Para.getConfig().protectedPaths();

		http.authorizeHttpRequests((authorize) -> authorize.requestMatchers("/**").permitAll());
		http.authorizeHttpRequests((authorize) -> authorize.requestMatchers(IgnoredRequestMatcher.INSTANCE).permitAll());
		http.authorizeHttpRequests((authorize) -> authorize.requestMatchers(RestRequestMatcher.INSTANCE).authenticated());

		parseProtectedResources(http, protectedResources);

		if (Para.getConfig().csrfProtectionEnabled()) {
			CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
			XorCsrfTokenRequestAttributeHandler delegate = new XorCsrfTokenRequestAttributeHandler();
			// set the name of the attribute the CsrfToken will be populated on
			delegate.setCsrfRequestAttributeName("_csrf");
			// Use only the handle() method of XorCsrfTokenRequestAttributeHandler and the
			// default implementation of resolveCsrfTokenValue() from CsrfTokenRequestHandler
			CsrfTokenRequestHandler requestHandler = delegate::handle;
			http.csrf((csrf) -> csrf.requireCsrfProtectionMatcher(CsrfProtectionRequestMatcher.INSTANCE).
					csrfTokenRepository(tokenRepository).csrfTokenRequestHandler(requestHandler))
					.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
//			http.csrf((csrf) -> csrf.requireCsrfProtectionMatcher(CsrfProtectionRequestMatcher.INSTANCE).
//					csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
		} else {
			http.csrf((csrf) -> csrf.disable());
		}

		http.sessionManagement((session) -> session.enableSessionUrlRewriting(false));
		http.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.NEVER));
		http.sessionManagement((session) -> session.sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()));
		http.exceptionHandling((session) -> session.authenticationEntryPoint(new SimpleAuthenticationEntryPoint(signinPath)));
		http.exceptionHandling((session) -> session.accessDeniedHandler(new SimpleAccessDeniedHandler(accessDeniedPath)));
		http.requestCache((cache) -> cache.requestCache(new SimpleRequestCache()));
		http.logout((logout) -> logout.deleteCookies(Para.getConfig().authCookieName()).invalidateHttpSession(true).
				logoutUrl(signoutPath).logoutSuccessUrl(signoutSuccessPath));
		http.rememberMe((rme) -> rme.disable());

		http.authenticationProvider(new JWTAuthenticationProvider());
		http.authenticationProvider(new LDAPAuthenticationProvider());

		http.with(new JwtConfigurer(), (c) -> { });

		return http.build();
	}

	/**
	 * Supplies the simple in-memory {@link UserDetailsService} used for admin authentication.
	 * @return service backed by {@link SimpleUserService}
	 */
	@Bean
	public UserDetailsService simpleUserDetailsService() {
		return new SimpleUserService();
	}

	private void parseProtectedResources(HttpSecurity http, ConfigObject protectedResources) throws Exception {
		if (protectedResources == null || protectedResources.isEmpty()) {
			return;
		}
		for (ConfigValue cv : protectedResources.values()) {
			LinkedList<String> patterns = new LinkedList<>();
			LinkedList<String> roles = new LinkedList<>();
			HashSet<HttpMethod> methods = new HashSet<>();

			for (ConfigValue configValue : (ConfigList) cv) {
				try {
					if (configValue instanceof ConfigList) {
						for (ConfigValue role : (ConfigList) configValue) {
							String r = ((String) role.unwrapped()).toUpperCase().trim();
							// check if any HTTP methods appear here
							HttpMethod m = HttpMethod.valueOf(r);
							if (m != null) {
								methods.add(m);
							} else {
								roles.add(r);
							}
						}
					} else {
						patterns.add((String) configValue.unwrapped());
					}
				} catch (Exception e) {
					logger.error("Invalid config syntax for protected resource: {}.", configValue.render(), e);
				}
			}
			String[] rolz = (roles.isEmpty()) ? DEFAULT_ROLES : roles.toArray(String[]::new);
			String[] patternz = patterns.toArray(String[]::new);
			if (methods.isEmpty()) {
				http.authorizeHttpRequests((authorize) -> authorize.requestMatchers(patternz).hasAnyRole(rolz));
			} else {
				for (HttpMethod method : methods) {
					http.authorizeHttpRequests((authorize) -> authorize.requestMatchers(method, patternz).hasAnyRole(rolz));
				}
			}
		}
	}

	private static final class CsrfCookieFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
				throws ServletException, IOException {
			CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
			// Render the token value to a cookie by causing the deferred token to be loaded
			csrfToken.getToken();
			filterChain.doFilter(request, response);
		}
	}
}
