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
package com.erudika.para.server.security;

import com.erudika.para.core.utils.Para;
import jakarta.annotation.security.DeclareRoles;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Programmatic configuration for Spring Security.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Configuration
@EnableWebSecurity
@DeclareRoles({ "ROLE_USER", "ROLE_MOD", "ROLE_ADMIN", "ROLE_APP" })
public class SecurityConfig {

	private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
	/**
	 * Default roles.
	 */
	public static final String[] DEFAULT_ROLES = {"USER", "MOD", "ADMIN", "APP"};

	/**
	 * No-args constructor.
	 */
	public SecurityConfig() {
	}

	@Bean
	public AuthenticationManager authenticationManager() {
		return new ProviderManager(new JWTAuthenticationProvider(),
				new LDAPAuthenticationProvider(), new OpenSaml5AuthenticationProvider());
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

		return http.authorizeHttpRequests((authorize) -> {
			parseProtectedResources(http);
			authorize.requestMatchers(RestRequestMatcher.INSTANCE).authenticated();
			authorize.requestMatchers(IgnoredRequestMatcher.INSTANCE).permitAll();
			authorize.anyRequest().permitAll(); // allow static files
		}).
		exceptionHandling((exc) -> {
			exc.authenticationEntryPoint(new SimpleAuthenticationEntryPoint(signinPath));
			exc.accessDeniedHandler(new SimpleAccessDeniedHandler(accessDeniedPath));
		}).
		cors((cors) -> {
			if (Para.getConfig().corsEnabled()) {
				cors.configurationSource(apiCorsConfiguration());
			}
		}).
		logout((logout) -> {
			logout.deleteCookies(Para.getConfig().authCookieName()).invalidateHttpSession(true).
					logoutUrl(signoutPath).logoutSuccessUrl(signoutSuccessPath);
		}).
		rememberMe((rme) -> rme.disable()).
		csrf((csrf) -> {
			if (Para.getConfig().csrfProtectionEnabled() || Para.getConfig().csrfProtectionWithSpaEnabled()) {
				csrf.requireCsrfProtectionMatcher(CsrfProtectionRequestMatcher.INSTANCE).
						csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
				if (Para.getConfig().csrfProtectionWithSpaEnabled()) {
					csrf.spa();
				}
			} else {
				csrf.disable();
			}
		}).
		sessionManagement((session) -> {
			session.enableSessionUrlRewriting(false);
			session.sessionCreationPolicy(SessionCreationPolicy.NEVER);
			session.sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy());
		}).
		requestCache((cache) -> cache.requestCache(new SimpleRequestCache())).
		with(new JwtConfigurer(authenticationManager()), (c) -> { }).
		//securityMatcher(API_PATH + "/**"). // DO NOT USE! It restricts which paths are protected by Spring Security
		build();
	}

	/**
	 * Supplies the simple in-memory {@link UserDetailsService} used for admin authentication.
	 * @return service backed by {@link SimpleUserService}
	 */
	@Bean
	public UserDetailsService simpleUserDetailsService() {
		return new SimpleUserService();
	}

	// Tu Du: Make this configurable
	private UrlBasedCorsConfigurationSource apiCorsConfiguration() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(Arrays.asList("*"));
		configuration.setAllowedMethods(Arrays.asList("*"));
		configuration.setAllowedHeaders(Arrays.asList("*"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	private void parseProtectedResources(HttpSecurity http) {
		RestRequestMatcher.getCustomProtectedPaths().stream().filter(p -> !p.isRest()).forEach(path -> {
			path.getPatterns().forEach(pattern -> path.getMethods().forEach(method -> {
				try {
					http.authorizeHttpRequests((authorize) -> authorize.requestMatchers(method, pattern).
							hasAnyRole(path.getRoles().toArray(String[]::new)));
				} catch (Exception e) {
					logger.error("Invalid config syntax for protected resource: {}.", e.getMessage());
				}
			}));
		});
	}
}
