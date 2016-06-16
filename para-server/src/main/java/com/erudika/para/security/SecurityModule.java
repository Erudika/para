/*
 * Copyright 2013-2016 Erudika. http://erudika.com
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

import com.erudika.para.cache.Cache;
import com.erudika.para.utils.Config;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.util.Collections;
import java.util.Map;
import org.openid4java.consumer.ConsumerException;
import org.slf4j.LoggerFactory;
import org.springframework.security.openid.OpenID4JavaConsumer;

/**
 * The default security module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SecurityModule extends AbstractModule {

	private final Map<String, String> confMap = Config.getConfigMap();
	private CachedCsrfTokenRepository csrfTokenRepository;
	private SimpleAuthenticationSuccessHandler successHandler;
	private SimpleAuthenticationFailureHandler failureHandler;
	private SimpleRememberMeServices rememberMeServices;
	private PasswordAuthFilter passwordFilter;
	private OpenIDAuthFilter openidFilter;
	private FacebookAuthFilter facebookFilter;
	private GoogleAuthFilter googleFilter;
	private LinkedInAuthFilter linkedinFilter;
	private TwitterAuthFilter twitterFilter;
	private GitHubAuthFilter githubFilter;
	private MicrosoftAuthFilter microsoftFilter;
	private JWTRestfulAuthFilter jwtFilter;

	protected void configure() {
	}

	@Provides
	public CachedCsrfTokenRepository getCsrfTokenRepository(Cache cache) {
		if (csrfTokenRepository == null) {
			csrfTokenRepository = new CachedCsrfTokenRepository();
			csrfTokenRepository.setCache(cache);
		}
		return csrfTokenRepository;
	}

	public void setCsrfTokenRepository(CachedCsrfTokenRepository csrfTokenRepository) {
		this.csrfTokenRepository = csrfTokenRepository;
	}

	@Provides
	public SimpleAuthenticationSuccessHandler getSuccessHandler() {
		if (successHandler == null) {
			successHandler = new SimpleAuthenticationSuccessHandler();
			successHandler.setDefaultTargetUrl(confMap.get("security.signin_success"));
			successHandler.setTargetUrlParameter(confMap.get("security.returnto"));
			successHandler.setUseReferer(false);
		}
		return successHandler;
	}

	public void setSuccessHandler(SimpleAuthenticationSuccessHandler successHandler) {
		this.successHandler = successHandler;
	}

	@Provides
	public SimpleAuthenticationFailureHandler getFailureHandler() {
		if (failureHandler == null) {
			failureHandler = new SimpleAuthenticationFailureHandler();
			failureHandler.setDefaultFailureUrl(confMap.get("security.signin_failure"));
		}
		return failureHandler;
	}

	public void setFailureHandler(SimpleAuthenticationFailureHandler failureHandler) {
		this.failureHandler = failureHandler;
	}

	@Provides
	public SimpleRememberMeServices getRemembeMeServices() {
		if (rememberMeServices == null) {
			rememberMeServices = new SimpleRememberMeServices(Config.APP_SECRET_KEY, new SimpleUserService());
			rememberMeServices.setAlwaysRemember(true);
			rememberMeServices.setTokenValiditySeconds(Config.SESSION_TIMEOUT_SEC.intValue());
			rememberMeServices.setCookieName(Config.AUTH_COOKIE);
			rememberMeServices.setParameter(Config.AUTH_COOKIE.concat("-remember-me"));

		}
		return rememberMeServices;
	}

	public void setRememberMeServices(SimpleRememberMeServices rememberMeServices) {
		this.rememberMeServices = rememberMeServices;
	}

	@Provides
	public PasswordAuthFilter getPasswordFilter() {
		if (passwordFilter == null) {
			passwordFilter = new PasswordAuthFilter("/" + PasswordAuthFilter.PASSWORD_ACTION);
			passwordFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			passwordFilter.setAuthenticationFailureHandler(getFailureHandler());
			passwordFilter.setRememberMeServices(getRemembeMeServices());
		}
		return passwordFilter;
	}

	public void setPasswordFilter(PasswordAuthFilter passwordFilter) {
		this.passwordFilter = passwordFilter;
	}

	@Provides
	public OpenIDAuthFilter getOpenIDFilter() {
		if (openidFilter == null) {
			openidFilter = new OpenIDAuthFilter("/" + OpenIDAuthFilter.OPENID_ACTION);
			try {
				openidFilter.setConsumer(new OpenID4JavaConsumer(new SimpleAxFetchListFactory()));
			} catch (ConsumerException ex) {
				LoggerFactory.getLogger(SecurityModule.class).error(null, ex);
			}
			openidFilter.setReturnToUrlParameters(Collections.singleton(confMap.get("security.returnto")));
			openidFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			openidFilter.setAuthenticationFailureHandler(getFailureHandler());
			openidFilter.setRememberMeServices(getRemembeMeServices());
		}
		return openidFilter;
	}

	public void setOpenidFilter(OpenIDAuthFilter openidFilter) {
		this.openidFilter = openidFilter;
	}

	@Provides
	public FacebookAuthFilter getFacebookFilter() {
		if (facebookFilter == null) {
			facebookFilter = new FacebookAuthFilter("/" + FacebookAuthFilter.FACEBOOK_ACTION);
			facebookFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			facebookFilter.setAuthenticationFailureHandler(getFailureHandler());
			facebookFilter.setRememberMeServices(getRemembeMeServices());
		}
		return facebookFilter;
	}

	public void setFacebookFilter(FacebookAuthFilter facebookFilter) {
		this.facebookFilter = facebookFilter;
	}

	@Provides
	public GoogleAuthFilter getGoogleFilter() {
		if (googleFilter == null) {
			googleFilter = new GoogleAuthFilter("/" + GoogleAuthFilter.GOOGLE_ACTION);
			googleFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			googleFilter.setAuthenticationFailureHandler(getFailureHandler());
			googleFilter.setRememberMeServices(getRemembeMeServices());
		}
		return googleFilter;
	}

	public void setGoogleFilter(GoogleAuthFilter googleFilter) {
		this.googleFilter = googleFilter;
	}

	@Provides
	public LinkedInAuthFilter getLinkedinFilter() {
		if (linkedinFilter == null) {
			linkedinFilter = new LinkedInAuthFilter("/" + LinkedInAuthFilter.LINKEDIN_ACTION);
			linkedinFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			linkedinFilter.setAuthenticationFailureHandler(getFailureHandler());
			linkedinFilter.setRememberMeServices(getRemembeMeServices());
		}
		return linkedinFilter;
	}

	public void setLinkedinFilter(LinkedInAuthFilter linkedinFilter) {
		this.linkedinFilter = linkedinFilter;
	}

	@Provides
	public TwitterAuthFilter getTwitterFilter() {
		if (twitterFilter == null) {
			twitterFilter = new TwitterAuthFilter("/" + TwitterAuthFilter.TWITTER_ACTION);
			twitterFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			twitterFilter.setAuthenticationFailureHandler(getFailureHandler());
			twitterFilter.setRememberMeServices(getRemembeMeServices());
		}
		return twitterFilter;
	}

	public void setTwitterFilter(TwitterAuthFilter twitterFilter) {
		this.twitterFilter = twitterFilter;
	}

	@Provides
	public GitHubAuthFilter getGithubFilter() {
		if (githubFilter == null) {
			githubFilter = new GitHubAuthFilter("/" + GitHubAuthFilter.GITHUB_ACTION);
			githubFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			githubFilter.setAuthenticationFailureHandler(getFailureHandler());
			githubFilter.setRememberMeServices(getRemembeMeServices());
		}
		return githubFilter;
	}

	@Provides
	public MicrosoftAuthFilter getMicrosoftFilter() {
		if (microsoftFilter == null) {
			microsoftFilter = new MicrosoftAuthFilter("/" + MicrosoftAuthFilter.MICROSOFT_ACTION);
			microsoftFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			microsoftFilter.setAuthenticationFailureHandler(getFailureHandler());
			microsoftFilter.setRememberMeServices(getRemembeMeServices());
		}
		return microsoftFilter;
	}

	public void setGithubFilter(GitHubAuthFilter githubFilter) {
		this.githubFilter = githubFilter;
	}

	@Provides
	public JWTRestfulAuthFilter getJWTAuthFilter(FacebookAuthFilter fbAuth, GoogleAuthFilter gpAuth,
			GitHubAuthFilter ghAuth, LinkedInAuthFilter liAuth, TwitterAuthFilter twAuth, PasswordAuthFilter pwAuth) {
		if (jwtFilter == null) {
			jwtFilter = new JWTRestfulAuthFilter("/" + JWTRestfulAuthFilter.JWT_ACTION);
			jwtFilter.setFacebookAuth(fbAuth);
			jwtFilter.setGoogleAuth(gpAuth);
			jwtFilter.setGithubAuth(ghAuth);
			jwtFilter.setLinkedinAuth(liAuth);
			jwtFilter.setTwitterAuth(twAuth);
			jwtFilter.setPasswordAuth(pwAuth);
		}
		return jwtFilter;
	}

	public void setJwtFilter(JWTRestfulAuthFilter jwtFilter) {
		this.jwtFilter = jwtFilter;
	}
}
