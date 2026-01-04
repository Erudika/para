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
import com.erudika.para.server.security.filters.AmazonAuthFilter;
import com.erudika.para.server.security.filters.FacebookAuthFilter;
import com.erudika.para.server.security.filters.GenericOAuth2Filter;
import com.erudika.para.server.security.filters.GitHubAuthFilter;
import com.erudika.para.server.security.filters.GoogleAuthFilter;
import com.erudika.para.server.security.filters.LdapAuthFilter;
import com.erudika.para.server.security.filters.LinkedInAuthFilter;
import com.erudika.para.server.security.filters.MicrosoftAuthFilter;
import com.erudika.para.server.security.filters.PasswordAuthFilter;
import com.erudika.para.server.security.filters.PasswordlessAuthFilter;
import com.erudika.para.server.security.filters.SAMLAuthFilter;
import com.erudika.para.server.security.filters.SAMLMetadataFilter;
import com.erudika.para.server.security.filters.SlackAuthFilter;
import com.erudika.para.server.security.filters.TwitterAuthFilter;
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
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
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
	private static FacebookAuthFilter facebookAuthFilter;

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
	public SecurityFilterChain filterChain(HttpSecurity http,
			AuthenticationManager authManager,
			FacebookAuthFilter facebookAuth,
			GoogleAuthFilter googleAuth,
			GitHubAuthFilter githubAuth,
			LinkedInAuthFilter linkedinAuth,
			TwitterAuthFilter twitterAuth,
			MicrosoftAuthFilter microsoftAuth,
			SlackAuthFilter slackAuth,
			AmazonAuthFilter amazonAuth,
			GenericOAuth2Filter oauth2Auth,
			LdapAuthFilter ldapAuth,
			PasswordAuthFilter passwordAuth,
			PasswordlessAuthFilter passwordlessAuth) throws Exception {
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
		addFilterAfter(getPasswordFilter(authManager), BasicAuthenticationFilter.class).
		addFilterAfter(getPasswordlessFilter(authManager), BasicAuthenticationFilter.class).
		addFilterAfter(getFacebookFilter(authManager), BasicAuthenticationFilter.class).
		addFilterAfter(getGoogleFilter(authManager), BasicAuthenticationFilter.class).
		addFilterAfter(getLinkedinFilter(authManager), BasicAuthenticationFilter.class).
		addFilterAfter(getTwitterFilter(authManager), BasicAuthenticationFilter.class).
		addFilterAfter(getGithubFilter(authManager), BasicAuthenticationFilter.class).
		addFilterAfter(getMicrosoftFilter(authManager), BasicAuthenticationFilter.class).
		addFilterAfter(getSlackFilter(authManager), BasicAuthenticationFilter.class).
		addFilterAfter(getAmazonFilter(authManager), BasicAuthenticationFilter.class).
		addFilterAfter(getGenericOAuth2Filter(authManager), BasicAuthenticationFilter.class).
		addFilterAfter(getLdapAuthFilter(authManager), BasicAuthenticationFilter.class).
		addFilterAfter(getSamlAuthFilter(authManager), BasicAuthenticationFilter.class).
		addFilterAfter(getSamlMetadataFilter(), BasicAuthenticationFilter.class).
		addFilterBefore(getJWTAuthFilter(authManager, facebookAuth, googleAuth, githubAuth,
				linkedinAuth, twitterAuth, microsoftAuth, slackAuth, amazonAuth, oauth2Auth,
				ldapAuth, passwordAuth, passwordlessAuth), RememberMeAuthenticationFilter.class).
		addFilterBefore(new RestAuthFilter(), RememberMeAuthenticationFilter.class).
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

	/**
	 * getter/setter.
	 *
	 * @return handler
	 */
	@Bean
	public SimpleAuthenticationSuccessHandler getSuccessHandler() {
		SimpleAuthenticationSuccessHandler successHandler = new SimpleAuthenticationSuccessHandler();
		successHandler.setDefaultTargetUrl(Para.getConfig().signinSuccessPath());
		successHandler.setTargetUrlParameter(Para.getConfig().returnToPath());
		successHandler.setUseReferer(false);
		return successHandler;
	}

	/**
	 * getter/setter.
	 * @return handler
	 */
	@Bean
	public SimpleAuthenticationFailureHandler getFailureHandler() {
		SimpleAuthenticationFailureHandler failureHandler = new SimpleAuthenticationFailureHandler();
		failureHandler.setDefaultFailureUrl(Para.getConfig().signinFailurePath());
		return failureHandler;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public JWTRestfulAuthFilter getJWTAuthFilter(AuthenticationManager authenticationManager,
			FacebookAuthFilter facebookAuth,
			GoogleAuthFilter googleAuth,
			GitHubAuthFilter githubAuth,
			LinkedInAuthFilter linkedinAuth,
			TwitterAuthFilter twitterAuth,
			MicrosoftAuthFilter microsoftAuth,
			SlackAuthFilter slackAuth,
			AmazonAuthFilter amazonAuth,
			GenericOAuth2Filter oauth2Auth,
			LdapAuthFilter ldapAuth,
			PasswordAuthFilter passwordAuth,
			PasswordlessAuthFilter passwordlessAuth) {
		return new JWTRestfulAuthFilter(authenticationManager, facebookAuth, googleAuth, githubAuth,
				linkedinAuth, twitterAuth, microsoftAuth, slackAuth, amazonAuth, oauth2Auth, ldapAuth,
				passwordAuth, passwordlessAuth);
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public PasswordAuthFilter getPasswordFilter(AuthenticationManager authenticationManager) {
		PasswordAuthFilter passwordFilter = new PasswordAuthFilter("/" + PasswordAuthFilter.PASSWORD_ACTION);
		passwordFilter.setAuthenticationSuccessHandler(getSuccessHandler());
		passwordFilter.setAuthenticationFailureHandler(getFailureHandler());
		passwordFilter.setAuthenticationManager(authenticationManager);
		return passwordFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public PasswordlessAuthFilter getPasswordlessFilter(AuthenticationManager authenticationManager) {
		PasswordlessAuthFilter passwordlessFilter = new PasswordlessAuthFilter("/" + PasswordlessAuthFilter.PASSWORDLESS_ACTION);
		passwordlessFilter.setAuthenticationSuccessHandler(getSuccessHandler());
		passwordlessFilter.setAuthenticationFailureHandler(getFailureHandler());
		passwordlessFilter.setAuthenticationManager(authenticationManager);
		return passwordlessFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public FacebookAuthFilter getFacebookFilter(AuthenticationManager authenticationManager) {
		if (facebookAuthFilter != null) {
			return facebookAuthFilter;
		}
		FacebookAuthFilter facebookFilter = new FacebookAuthFilter("/" + FacebookAuthFilter.FACEBOOK_ACTION);
		facebookFilter.setAuthenticationSuccessHandler(getSuccessHandler());
		facebookFilter.setAuthenticationFailureHandler(getFailureHandler());
		facebookFilter.setAuthenticationManager(authenticationManager);
		return facebookFilter;
	}

	/**
	 * For testing only.
	 * @param facebookAuthFilter used for testing
	 */
	public static void setFacebookAuthFilter(FacebookAuthFilter facebookAuthFilter) {
		SecurityConfig.facebookAuthFilter = facebookAuthFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public GoogleAuthFilter getGoogleFilter(AuthenticationManager authenticationManager) {
		GoogleAuthFilter googleFilter = new GoogleAuthFilter("/" + GoogleAuthFilter.GOOGLE_ACTION);
		googleFilter.setAuthenticationSuccessHandler(getSuccessHandler());
		googleFilter.setAuthenticationFailureHandler(getFailureHandler());
		googleFilter.setAuthenticationManager(authenticationManager);
		return googleFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public LinkedInAuthFilter getLinkedinFilter(AuthenticationManager authenticationManager) {
		LinkedInAuthFilter linkedinFilter = new LinkedInAuthFilter("/" + LinkedInAuthFilter.LINKEDIN_ACTION);
		linkedinFilter.setAuthenticationSuccessHandler(getSuccessHandler());
		linkedinFilter.setAuthenticationFailureHandler(getFailureHandler());
		linkedinFilter.setAuthenticationManager(authenticationManager);
		return linkedinFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public TwitterAuthFilter getTwitterFilter(AuthenticationManager authenticationManager) {
		TwitterAuthFilter twitterFilter = new TwitterAuthFilter("/" + TwitterAuthFilter.TWITTER_ACTION);
		twitterFilter.setAuthenticationSuccessHandler(getSuccessHandler());
		twitterFilter.setAuthenticationFailureHandler(getFailureHandler());
		twitterFilter.setAuthenticationManager(authenticationManager);
		return twitterFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public GitHubAuthFilter getGithubFilter(AuthenticationManager authenticationManager) {
		GitHubAuthFilter githubFilter = new GitHubAuthFilter("/" + GitHubAuthFilter.GITHUB_ACTION);
		githubFilter.setAuthenticationSuccessHandler(getSuccessHandler());
		githubFilter.setAuthenticationFailureHandler(getFailureHandler());
		githubFilter.setAuthenticationManager(authenticationManager);
		return githubFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public MicrosoftAuthFilter getMicrosoftFilter(AuthenticationManager authenticationManager) {
		MicrosoftAuthFilter microsoftFilter = new MicrosoftAuthFilter("/" + MicrosoftAuthFilter.MICROSOFT_ACTION);
		microsoftFilter.setAuthenticationSuccessHandler(getSuccessHandler());
		microsoftFilter.setAuthenticationFailureHandler(getFailureHandler());
		microsoftFilter.setAuthenticationManager(authenticationManager);
		return microsoftFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public SlackAuthFilter getSlackFilter(AuthenticationManager authenticationManager) {
		SlackAuthFilter slackFilter = new SlackAuthFilter("/" + SlackAuthFilter.SLACK_ACTION);
		slackFilter.setAuthenticationSuccessHandler(getSuccessHandler());
		slackFilter.setAuthenticationFailureHandler(getFailureHandler());
		slackFilter.setAuthenticationManager(authenticationManager);
		return slackFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public AmazonAuthFilter getAmazonFilter(AuthenticationManager authenticationManager) {
		AmazonAuthFilter amazonFilter = new AmazonAuthFilter("/" + AmazonAuthFilter.AMAZON_ACTION);
		amazonFilter.setAuthenticationSuccessHandler(getSuccessHandler());
		amazonFilter.setAuthenticationFailureHandler(getFailureHandler());
		amazonFilter.setAuthenticationManager(authenticationManager);
		return amazonFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public GenericOAuth2Filter getGenericOAuth2Filter(AuthenticationManager authenticationManager) {
		GenericOAuth2Filter oauth2Filter = new GenericOAuth2Filter("/" + GenericOAuth2Filter.OAUTH2_ACTION);
		oauth2Filter.setAuthenticationSuccessHandler(getSuccessHandler());
		oauth2Filter.setAuthenticationFailureHandler(getFailureHandler());
		oauth2Filter.setAuthenticationManager(authenticationManager);
		return oauth2Filter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public LdapAuthFilter getLdapAuthFilter(AuthenticationManager authenticationManager) {
		LdapAuthFilter ldapFilter = new LdapAuthFilter("/" + LdapAuthFilter.LDAP_ACTION);
		ldapFilter.setAuthenticationSuccessHandler(getSuccessHandler());
		ldapFilter.setAuthenticationFailureHandler(getFailureHandler());
		ldapFilter.setAuthenticationManager(authenticationManager);
		return ldapFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public SAMLAuthFilter getSamlAuthFilter(AuthenticationManager authenticationManager) {
		SAMLAuthFilter samlFilter = new SAMLAuthFilter(SAMLAuthFilter.SAML_ACTION + "/*");
		samlFilter.setAuthenticationSuccessHandler(getSuccessHandler());
		samlFilter.setAuthenticationFailureHandler(getFailureHandler());
		samlFilter.setAuthenticationManager(authenticationManager);
		return samlFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Bean
	public SAMLMetadataFilter getSamlMetadataFilter() {
		return new SAMLMetadataFilter();
	}

}
