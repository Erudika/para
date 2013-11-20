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
package com.erudika.para.security;

import com.erudika.para.Para;
import com.erudika.para.utils.Config;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.RememberMeAuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.AbstractConfiguredSecurityBuilder;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserCache;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.openid.OpenID4JavaConsumer;
import org.springframework.security.openid.OpenIDAuthenticationProvider;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
	
//	@Autowired
//	public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
//		auth.inMemoryAuthentication().
//				.withUser("user").password("password").roles("USER").and()
//				.withUser("admin").password("password").roles("USER", "ADMIN");
//	}
	
	@Configuration
	@Order(1)
	public static class ApiWebSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {
		protected void configure(HttpSecurity http) throws Exception {
			http.antMatcher("/api/**")
					.authorizeRequests()
					.anyRequest().hasAnyRole("USER","MOD","ADMIN")
					.and()
					.httpBasic()
					.and().csrf().disable();
			http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
		}
	}
	
	@Configuration
	public static class FormLoginWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

		@Override
		protected void configure(AuthenticationManagerBuilder auth) throws Exception {
			OpenIDAuthenticationProvider openidProvider = new OpenIDAuthenticationProvider();
			openidProvider.setAuthenticationUserDetailsService(new StandardUserService());
			auth.authenticationProvider(openidProvider);
			
			RememberMeAuthenticationProvider rmeProvider = new RememberMeAuthenticationProvider(Config.APP_SECRET_KEY);
			auth.authenticationProvider(rmeProvider);
		}
				
//		@Override
//		public void configure(WebSecurity web) throws Exception {
//			web.ignoring().antMatchers("/images/**");
//			web.ignoring().antMatchers("/styles/**");
//			web.ignoring().antMatchers("/scripts/**");
//////			web.ignoring().antMatchers("/signin/**");
////			web.debug(true);
//		}
		
		@Override
		protected void configure(HttpSecurity http) throws Exception {
//			http.authorizeRequests().anyRequest().authenticated().and().formLogin().loginPage("/signin").permitAll();
			http.csrf().disable();
	//		Map<String, String
			http.authorizeRequests()
				.antMatchers("/admin", "/admin/**").hasAnyRole("ROLE_ADMIN")
				.antMatchers("/p/", "/p/**").hasAnyRole("USER","MOD","ADMIN")
				.antMatchers("/votedown/**").hasAnyRole("USER","MOD","ADMIN")
				.antMatchers("/voteup/**").hasAnyRole("USER","MOD","ADMIN")
				.antMatchers("/settings", "/settings/**").hasAnyRole("USER","MOD","ADMIN");
//				.anyRequest().authenticated().and().formLogin();

			CachedSecurityContextRepository cscr = new CachedSecurityContextRepository();
			Para.injectInto(cscr);

			http.sessionManagement().enableSessionUrlRewriting(false);
			http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
			http.sessionManagement().sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy());
			http.securityContext().securityContextRepository(cscr);
			http.exceptionHandling().authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/signin"));
			http.exceptionHandling().accessDeniedPage("/403");

			SimpleUrlAuthenticationSuccessHandler successHandler = new SimpleUrlAuthenticationSuccessHandler();
			successHandler.setDefaultTargetUrl("/");
			successHandler.setTargetUrlParameter("returnto");

			SimpleUrlAuthenticationFailureHandler failureHandler = new SimpleUrlAuthenticationFailureHandler();
			failureHandler.setDefaultFailureUrl("/signin?code=3&error=true");

			TokenBasedRememberMeServices tbrms = new TokenBasedRememberMeServices(Config.APP_SECRET_KEY, new StandardUserService());
			tbrms.setAlwaysRemember(true);
			tbrms.setCookieName(Config.AUTH_COOKIE);
			tbrms.setParameter(Config.AUTH_COOKIE.concat("-remember-me"));
			http.rememberMe().rememberMeServices(tbrms);
			
			PasswordAuthFilter passwordFilter = new PasswordAuthFilter("/"+PasswordAuthFilter.PASSWORD_ACTION);
			passwordFilter.setAuthenticationManager(authenticationManager());
			passwordFilter.setAuthenticationSuccessHandler(successHandler);
			passwordFilter.setAuthenticationFailureHandler(failureHandler);
			passwordFilter.setRememberMeServices(tbrms);
			
			OpenIDAuthFilter openidFilter = new OpenIDAuthFilter("/"+OpenIDAuthFilter.OPENID_ACTION);
			openidFilter.setAuthenticationManager(authenticationManager());
			openidFilter.setConsumer(new OpenID4JavaConsumer(new DefaultAxFetchListFactory()));
			openidFilter.setReturnToUrlParameters(Collections.singleton("returnto"));
			openidFilter.setAuthenticationSuccessHandler(successHandler);
			openidFilter.setAuthenticationFailureHandler(failureHandler);
			openidFilter.setRememberMeServices(tbrms);
			
			FacebookAuthFilter facebookFilter = new FacebookAuthFilter("/"+FacebookAuthFilter.FACEBOOK_ACTION);
			facebookFilter.setAuthenticationManager(authenticationManager());
			facebookFilter.setAuthenticationSuccessHandler(successHandler);
			facebookFilter.setAuthenticationFailureHandler(failureHandler);
			facebookFilter.setRememberMeServices(tbrms);
			
			http.addFilterAfter(passwordFilter, BasicAuthenticationFilter.class);
			http.addFilterAfter(openidFilter, BasicAuthenticationFilter.class);
			http.addFilterAfter(facebookFilter, BasicAuthenticationFilter.class);
			
	//		http.authorizeRequests()
	//				.antMatchers("/signup","/about").permitAll()
	//				.anyRequest().hasRole("USER")
	//				.and()
	//			.formLogin()
	//				// You must render the login page now
	//				.loginPage("/login")
	//				// set permitAll for all URLs associated with Form Login
	//				.permitAll();
		}
		
	}
		
}
