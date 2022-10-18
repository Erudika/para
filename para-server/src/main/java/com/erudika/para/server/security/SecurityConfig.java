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
import static com.erudika.para.server.ParaServer.getInstance;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import java.util.HashSet;
import java.util.LinkedList;
import javax.annotation.security.DeclareRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.firewall.DefaultHttpFirewall;

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

	private final CachedCsrfTokenRepository csrfTokenRepository;

	/**
	 * No-args constructor.
	 */
	public SecurityConfig() {
		csrfTokenRepository = getInstance(CachedCsrfTokenRepository.class);
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

		http.authorizeRequests().requestMatchers(IgnoredRequestMatcher.INSTANCE).permitAll();
		http.authorizeRequests().requestMatchers(RestRequestMatcher.INSTANCE).authenticated();

		parseProtectedResources(http, protectedResources);

		if (Para.getConfig().csrfProtectionEnabled()) {
			http.csrf().requireCsrfProtectionMatcher(CsrfProtectionRequestMatcher.INSTANCE).
					csrfTokenRepository(csrfTokenRepository);
		} else {
			http.csrf().disable();
		}

		http.sessionManagement().enableSessionUrlRewriting(false);
		http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.NEVER);
		http.sessionManagement().sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy());
		http.exceptionHandling().authenticationEntryPoint(new SimpleAuthenticationEntryPoint(signinPath));
		http.exceptionHandling().accessDeniedHandler(new SimpleAccessDeniedHandler(accessDeniedPath));
		http.requestCache().requestCache(new SimpleRequestCache());
		http.logout().deleteCookies(Para.getConfig().authCookieName()).invalidateHttpSession(true).
				logoutUrl(signoutPath).logoutSuccessUrl(signoutSuccessPath);
		http.rememberMe().disable();

		http.authenticationProvider(new JWTAuthenticationProvider());
		http.authenticationProvider(new LDAPAuthenticationProvider());

		http.apply(new JwtConfigurer());

		return http.build();
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
							HttpMethod m = HttpMethod.resolve(r);
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
			String[] rolz = (roles.isEmpty()) ? DEFAULT_ROLES : roles.toArray(new String[0]);
			String[] patternz = patterns.toArray(new String[0]);
			if (methods.isEmpty()) {
				http.authorizeRequests().antMatchers(patternz).hasAnyRole(rolz);
			} else {
				for (HttpMethod method : methods) {
					http.authorizeRequests().antMatchers(method, patternz).hasAnyRole(rolz);
				}
			}
		}
	}
}
