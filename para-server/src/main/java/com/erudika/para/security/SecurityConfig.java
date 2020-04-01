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
package com.erudika.para.security;

import com.erudika.para.security.filters.OpenIDAuthFilter;
import com.erudika.para.security.filters.GoogleAuthFilter;
import com.erudika.para.security.filters.PasswordAuthFilter;
import com.erudika.para.security.filters.TwitterAuthFilter;
import com.erudika.para.security.filters.MicrosoftAuthFilter;
import com.erudika.para.security.filters.GitHubAuthFilter;
import com.erudika.para.security.filters.LinkedInAuthFilter;
import com.erudika.para.security.filters.GenericOAuth2Filter;
import com.erudika.para.security.filters.FacebookAuthFilter;
import static com.erudika.para.ParaServer.getInstance;
import com.erudika.para.security.filters.AmazonAuthFilter;
import com.erudika.para.security.filters.LdapAuthFilter;
import com.erudika.para.security.filters.PasswordlessAuthFilter;
import com.erudika.para.security.filters.SAMLAuthFilter;
import com.erudika.para.security.filters.SAMLMetadataFilter;
import com.erudika.para.security.filters.SlackAuthFilter;
import com.erudika.para.utils.Config;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import java.util.HashSet;
import java.util.LinkedList;
import javax.annotation.security.DeclareRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.RememberMeAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.openid.OpenIDAuthenticationProvider;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.firewall.DefaultHttpFirewall;

/**
 * Programmatic configuration for Spring Security.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Configuration
@EnableWebSecurity
@DeclareRoles({ "ROLE_USER", "ROLE_MOD", "ROLE_ADMIN", "ROLE_APP" })
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
	private static final String[] DEFAULT_ROLES = {"USER", "MOD", "ADMIN", "APP"};

	private final CachedCsrfTokenRepository csrfTokenRepository;
	private final SimpleRememberMeServices rememberMeServices;
	private final PasswordAuthFilter passwordFilter;
	private final PasswordlessAuthFilter passwordlessFilter;
	private final OpenIDAuthFilter openidFilter;
	private final FacebookAuthFilter facebookFilter;
	private final GoogleAuthFilter googleFilter;
	private final LinkedInAuthFilter linkedinFilter;
	private final TwitterAuthFilter twitterFilter;
	private final GitHubAuthFilter githubFilter;
	private final MicrosoftAuthFilter microsoftFilter;
	private final SlackAuthFilter slackFilter;
	private final AmazonAuthFilter amazonFilter;
	private final GenericOAuth2Filter oauth2Filter;
	private final LdapAuthFilter ldapFilter;
	private final SAMLAuthFilter samlFilter;
	private final SAMLMetadataFilter samlMetaFilter;
	private final JWTRestfulAuthFilter jwtFilter;

	/**
	 * No-args constructor.
	 */
	public SecurityConfig() {
		csrfTokenRepository = getInstance(CachedCsrfTokenRepository.class);
		rememberMeServices = getInstance(SimpleRememberMeServices.class);
		passwordFilter = getInstance(PasswordAuthFilter.class);
		passwordlessFilter = getInstance(PasswordlessAuthFilter.class);
		openidFilter = getInstance(OpenIDAuthFilter.class);
		facebookFilter = getInstance(FacebookAuthFilter.class);
		googleFilter = getInstance(GoogleAuthFilter.class);
		linkedinFilter = getInstance(LinkedInAuthFilter.class);
		twitterFilter = getInstance(TwitterAuthFilter.class);
		githubFilter = getInstance(GitHubAuthFilter.class);
		microsoftFilter = getInstance(MicrosoftAuthFilter.class);
		slackFilter = getInstance(SlackAuthFilter.class);
		amazonFilter = getInstance(AmazonAuthFilter.class);
		oauth2Filter = getInstance(GenericOAuth2Filter.class);
		ldapFilter = getInstance(LdapAuthFilter.class);
		samlFilter = getInstance(SAMLAuthFilter.class);
		samlMetaFilter = getInstance(SAMLMetadataFilter.class);
		jwtFilter = getInstance(JWTRestfulAuthFilter.class);
	}

	/**
	 * Configures the authentication providers.
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

		JWTAuthenticationProvider jwtProvider = new JWTAuthenticationProvider();
		auth.authenticationProvider(jwtProvider);

		LDAPAuthenticationProvider ldapProvider = new LDAPAuthenticationProvider();
		auth.authenticationProvider(ldapProvider);
	}

	/**
	 * Configures the unsecured public resources.
	 *
	 * @param web web sec object
	 * @throws Exception ex
	 */
	@Override
	public void configure(WebSecurity web) throws Exception {
		web.ignoring().requestMatchers(IgnoredRequestMatcher.INSTANCE);
		DefaultHttpFirewall firewall = new DefaultHttpFirewall();
		firewall.setAllowUrlEncodedSlash(true);
		web.httpFirewall(firewall);
		//web.debug(true);
	}

	/**
	 * Configures the protected private resources.
	 *
	 * @param http HTTP sec object
	 * @throws Exception ex
	 */
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		ConfigObject protectedResources = Config.getConfig().getObject("security.protected");
		ConfigValue apiSec = Config.getConfig().getValue("security.api_security");
		boolean enableRestFilter = apiSec != null && Boolean.TRUE.equals(apiSec.unwrapped());
		String signinPath = Config.getConfigParam("security.signin", "/signin");
		String signoutPath = Config.getConfigParam("security.signout", "/signout");
		String accessDeniedPath = Config.getConfigParam("security.access_denied", "/403");
		String signoutSuccessPath = Config.getConfigParam("security.signout_success", signinPath);

		// If API security is disabled don't add the API endpoint to the list of protected resources
		if (enableRestFilter) {
			http.authorizeRequests().requestMatchers(RestRequestMatcher.INSTANCE);
		}

		parseProtectedResources(http, protectedResources);

		if (Config.getConfigBoolean("security.csrf_protection", true)) {
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
		http.logout().logoutUrl(signoutPath).logoutSuccessUrl(signoutSuccessPath);
		http.rememberMe().rememberMeServices(rememberMeServices);

		registerAuthFilters(http);

		if (enableRestFilter) {
			if (jwtFilter != null) {
				jwtFilter.setAuthenticationManager(authenticationManager());
				http.addFilterBefore(jwtFilter, RememberMeAuthenticationFilter.class);
			}
			RestAuthFilter restFilter = new RestAuthFilter();
			http.addFilterAfter(restFilter, JWTRestfulAuthFilter.class);
		}
	}

	private void registerAuthFilters(HttpSecurity http) throws Exception {
		if (passwordFilter != null) {
			passwordFilter.setAuthenticationManager(authenticationManager());
			http.addFilterAfter(passwordFilter, BasicAuthenticationFilter.class);
		}

		if (passwordlessFilter != null) {
			passwordlessFilter.setAuthenticationManager(authenticationManager());
			http.addFilterAfter(passwordlessFilter, BasicAuthenticationFilter.class);
		}

		if (openidFilter != null) {
			openidFilter.setAuthenticationManager(authenticationManager());
			http.addFilterAfter(openidFilter, BasicAuthenticationFilter.class);
		}

		if (facebookFilter != null) {
			facebookFilter.setAuthenticationManager(authenticationManager());
			http.addFilterAfter(facebookFilter, BasicAuthenticationFilter.class);
		}

		if (googleFilter != null) {
			googleFilter.setAuthenticationManager(authenticationManager());
			http.addFilterAfter(googleFilter, BasicAuthenticationFilter.class);
		}

		if (linkedinFilter != null) {
			linkedinFilter.setAuthenticationManager(authenticationManager());
			http.addFilterAfter(linkedinFilter, BasicAuthenticationFilter.class);
		}

		if (twitterFilter != null) {
			twitterFilter.setAuthenticationManager(authenticationManager());
			http.addFilterAfter(twitterFilter, BasicAuthenticationFilter.class);
		}

		if (githubFilter != null) {
			githubFilter.setAuthenticationManager(authenticationManager());
			http.addFilterAfter(githubFilter, BasicAuthenticationFilter.class);
		}

		if (microsoftFilter != null) {
			microsoftFilter.setAuthenticationManager(authenticationManager());
			http.addFilterAfter(microsoftFilter, BasicAuthenticationFilter.class);
		}

		if (slackFilter != null) {
			slackFilter.setAuthenticationManager(authenticationManager());
			http.addFilterAfter(slackFilter, BasicAuthenticationFilter.class);
		}

		if (amazonFilter != null) {
			amazonFilter.setAuthenticationManager(authenticationManager());
			http.addFilterAfter(amazonFilter, BasicAuthenticationFilter.class);
		}

		if (oauth2Filter != null) {
			oauth2Filter.setAuthenticationManager(authenticationManager());
			http.addFilterAfter(oauth2Filter, BasicAuthenticationFilter.class);
		}

		if (ldapFilter != null) {
			ldapFilter.setAuthenticationManager(authenticationManager());
			http.addFilterAfter(ldapFilter, BasicAuthenticationFilter.class);
		}

		if (samlFilter != null) {
			samlFilter.setAuthenticationManager(authenticationManager());
			http.addFilterAfter(samlFilter, BasicAuthenticationFilter.class);
		}

		http.addFilterAfter(samlMetaFilter, BasicAuthenticationFilter.class);
	}

	private void parseProtectedResources(HttpSecurity http, ConfigObject protectedResources) throws Exception {
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
