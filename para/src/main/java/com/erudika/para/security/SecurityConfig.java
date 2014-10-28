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
package com.erudika.para.security;

import com.erudika.para.Para;
import com.erudika.para.rest.Signer;
import com.erudika.para.utils.Config;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.security.DeclareRoles;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.RememberMeAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.openid.OpenID4JavaConsumer;
import org.springframework.security.openid.OpenIDAuthenticationProvider;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Programmatic configuration for Spring Security.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Configuration
@EnableWebSecurity
@DeclareRoles({ "ROLE_USER", "ROLE_MOD", "ROLE_ADMIN", "ROLE_APP" })
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

	/**
	 * Configuers the authentication providers
	 *
	 * @param auth a builder
	 * @throws Exception ex
	 */
	@Override
	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
		OpenIDAuthenticationProvider openidProvider = new OpenIDAuthenticationProvider();
		openidProvider.setAuthenticationUserDetailsService(new SimpleUserService());
		auth.authenticationProvider(openidProvider);

		RememberMeAuthenticationProvider rmeProvider = new RememberMeAuthenticationProvider(Config.APP_SECRET_KEY);
		auth.authenticationProvider(rmeProvider);
	}

	/**
	 * Configures the unsecured public resources
	 *
	 * @param web web sec object
	 * @throws Exception ex
	 */
	@Override
	public void configure(WebSecurity web) throws Exception {
		ConfigList c = Config.getConfig().getList("security.ignored");
		for (ConfigValue configValue : c) {
			web.ignoring().antMatchers((String) configValue.unwrapped());
		}
		//web.debug(true);
	}

	/**
	 * Configures the protected private resources
	 *
	 * @param http HTTP sec object
	 * @throws Exception ex
	 */
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		String[] defRoles = {"USER", "MOD", "ADMIN"};
		Map<String, String> confMap = Config.getConfigMap();
		ConfigObject c = Config.getConfig().getObject("security.protected");
		ConfigValue apiSec = Config.getConfig().getValue("security.api_security");
		boolean enableRestFilter = apiSec != null && Boolean.TRUE.equals(apiSec.unwrapped());

		for (String key : c.keySet()) {
			ConfigValue cv = c.get(key);
			ArrayList<String> patterns = new ArrayList<String>();
			ArrayList<String> roles = new ArrayList<String>();

			// if API security is disabled don't add any API related patterns
			// to the list of protected resources
			if (!"api".equals(key) || enableRestFilter) {
				for (ConfigValue configValue : (ConfigList) cv) {
					if (configValue instanceof List) {
						for (ConfigValue role : (ConfigList) configValue) {
							roles.add(((String) role.unwrapped()).toUpperCase());
						}
					} else {
						patterns.add((String) configValue.unwrapped());
					}
				}
				String[] rolz = (roles.isEmpty()) ? defRoles : roles.toArray(new String[0]);
				http.authorizeRequests().antMatchers(patterns.toArray(new String[0])).hasAnyRole(rolz);
			}
		}

		if (Config.getConfigParamUnwrapped("security.csrf_protection", true)) {
			CachedCsrfTokenRepository str = new CachedCsrfTokenRepository();
			Para.injectInto(str);

			http.csrf().requireCsrfProtectionMatcher(new RequestMatcher() {
				private Pattern allowedMethods = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");

				public boolean matches(HttpServletRequest request) {
					return !RestRequestMatcher.INSTANCE.matches(request)
							&& !allowedMethods.matcher(request.getMethod()).matches();
				}
			}).csrfTokenRepository(str);
		} else {
			http.csrf().disable();
		}

		http.sessionManagement().enableSessionUrlRewriting(false);
		http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.NEVER);
		http.sessionManagement().sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy());
		http.securityContext().securityContextRepository(new NullSecurityContextRepository());
		http.exceptionHandling().authenticationEntryPoint(new SimpleAuthenticationEntryPoint(confMap.get("security.signin")));
		http.exceptionHandling().accessDeniedHandler(new SimpleAccessDeniedHandler(confMap.get("security.access_denied")));
		http.requestCache().requestCache(new SimpleRequestCache());
		http.logout().logoutUrl(confMap.get("security.signout")).
				logoutSuccessUrl(confMap.get("security.signout_success"));

		SimpleAuthenticationSuccessHandler successHandler = new SimpleAuthenticationSuccessHandler();
		successHandler.setDefaultTargetUrl(confMap.get("security.signin_success"));
		successHandler.setTargetUrlParameter(confMap.get("security.returnto"));
		successHandler.setUseReferer(true);

		SimpleAuthenticationFailureHandler failureHandler = new SimpleAuthenticationFailureHandler();
		failureHandler.setDefaultFailureUrl(confMap.get("security.signin_failure"));

		SimpleRememberMeServices tbrms = new SimpleRememberMeServices(Config.APP_SECRET_KEY, new SimpleUserService());
		tbrms.setAlwaysRemember(true);
		tbrms.setTokenValiditySeconds(Config.SESSION_TIMEOUT_SEC.intValue());
		tbrms.setCookieName(Config.AUTH_COOKIE);
		tbrms.setParameter(Config.AUTH_COOKIE.concat("-remember-me"));
		http.rememberMe().rememberMeServices(tbrms);

		PasswordAuthFilter passwordFilter = new PasswordAuthFilter("/" + PasswordAuthFilter.PASSWORD_ACTION);
		passwordFilter.setAuthenticationManager(authenticationManager());
		passwordFilter.setAuthenticationSuccessHandler(successHandler);
		passwordFilter.setAuthenticationFailureHandler(failureHandler);
		passwordFilter.setRememberMeServices(tbrms);

		OpenIDAuthFilter openidFilter = new OpenIDAuthFilter("/" + OpenIDAuthFilter.OPENID_ACTION);
		openidFilter.setAuthenticationManager(authenticationManager());
		openidFilter.setConsumer(new OpenID4JavaConsumer(new SimpleAxFetchListFactory()));
		openidFilter.setReturnToUrlParameters(Collections.singleton(confMap.get("security.returnto")));
		openidFilter.setAuthenticationSuccessHandler(successHandler);
		openidFilter.setAuthenticationFailureHandler(failureHandler);
		openidFilter.setRememberMeServices(tbrms);

		FacebookAuthFilter facebookFilter = new FacebookAuthFilter("/" + FacebookAuthFilter.FACEBOOK_ACTION);
		facebookFilter.setAuthenticationManager(authenticationManager());
		facebookFilter.setAuthenticationSuccessHandler(successHandler);
		facebookFilter.setAuthenticationFailureHandler(failureHandler);
		facebookFilter.setRememberMeServices(tbrms);

		GoogleAuthFilter googleFilter = new GoogleAuthFilter("/" + GoogleAuthFilter.GOOGLE_ACTION);
		googleFilter.setAuthenticationManager(authenticationManager());
		googleFilter.setAuthenticationSuccessHandler(successHandler);
		googleFilter.setAuthenticationFailureHandler(failureHandler);
		googleFilter.setRememberMeServices(tbrms);

		LinkedInAuthFilter linkedinFilter = new LinkedInAuthFilter("/" + LinkedInAuthFilter.LINKEDIN_ACTION);
		linkedinFilter.setAuthenticationManager(authenticationManager());
		linkedinFilter.setAuthenticationSuccessHandler(successHandler);
		linkedinFilter.setAuthenticationFailureHandler(failureHandler);
		linkedinFilter.setRememberMeServices(tbrms);

		http.addFilterAfter(passwordFilter, BasicAuthenticationFilter.class);
		http.addFilterAfter(openidFilter, BasicAuthenticationFilter.class);
		http.addFilterAfter(facebookFilter, BasicAuthenticationFilter.class);
		http.addFilterAfter(googleFilter, BasicAuthenticationFilter.class);
		http.addFilterAfter(linkedinFilter, BasicAuthenticationFilter.class);

		if (enableRestFilter) {
			RestAuthFilter restFilter = new RestAuthFilter(new Signer());
			http.addFilterAfter(restFilter, RememberMeAuthenticationFilter.class);
		}
	}

}
