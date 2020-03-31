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
import com.erudika.para.cache.Cache;
import com.erudika.para.security.filters.AmazonAuthFilter;
import com.erudika.para.security.filters.LdapAuthFilter;
import com.erudika.para.security.filters.PasswordlessAuthFilter;
import com.erudika.para.security.filters.SAMLAuthFilter;
import com.erudika.para.security.filters.SAMLMetadataFilter;
import com.erudika.para.security.filters.SlackAuthFilter;
import com.erudika.para.utils.Config;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.util.Collections;
import org.openid4java.consumer.ConsumerException;
import org.slf4j.LoggerFactory;
import org.springframework.security.openid.OpenID4JavaConsumer;

/**
 * The default security module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SecurityModule extends AbstractModule {

	private CachedCsrfTokenRepository csrfTokenRepository;
	private SimpleAuthenticationSuccessHandler successHandler;
	private SimpleAuthenticationFailureHandler failureHandler;
	private SimpleRememberMeServices rememberMeServices;
	private PasswordAuthFilter passwordFilter;
	private PasswordlessAuthFilter passwordlessFilter;
	private OpenIDAuthFilter openidFilter;
	private FacebookAuthFilter facebookFilter;
	private GoogleAuthFilter googleFilter;
	private LinkedInAuthFilter linkedinFilter;
	private TwitterAuthFilter twitterFilter;
	private GitHubAuthFilter githubFilter;
	private MicrosoftAuthFilter microsoftFilter;
	private SlackAuthFilter slackFilter;
	private AmazonAuthFilter amazonFilter;
	private GenericOAuth2Filter oauth2Filter;
	private LdapAuthFilter ldapFilter;
	private SAMLAuthFilter samlFilter;
	private SAMLMetadataFilter samlMetaFilter;
	private JWTRestfulAuthFilter jwtFilter;

	protected void configure() {
	}

	/**
	 * @param cache {@link Cache}
	 * @return token repository
	 */
	@Provides
	public CachedCsrfTokenRepository getCsrfTokenRepository(Cache cache) {
		if (csrfTokenRepository == null) {
			csrfTokenRepository = new CachedCsrfTokenRepository();
			csrfTokenRepository.setCache(cache);
		}
		return csrfTokenRepository;
	}

	/**
	 * @param csrfTokenRepository token repository
	 */
	public void setCsrfTokenRepository(CachedCsrfTokenRepository csrfTokenRepository) {
		this.csrfTokenRepository = csrfTokenRepository;
	}

	/**
	 * @return handler
	 */
	@Provides
	public SimpleAuthenticationSuccessHandler getSuccessHandler() {
		if (successHandler == null) {
			successHandler = new SimpleAuthenticationSuccessHandler();
			successHandler.setDefaultTargetUrl(Config.getConfigParam("security.signin_success", "/"));
			successHandler.setTargetUrlParameter(Config.getConfigParam("security.returnto", "returnto"));
			successHandler.setUseReferer(false);
		}
		return successHandler;
	}

	/**
	 * @param successHandler handler
	 */
	public void setSuccessHandler(SimpleAuthenticationSuccessHandler successHandler) {
		this.successHandler = successHandler;
	}

	/**
	 * @return handler
	 */
	@Provides
	public SimpleAuthenticationFailureHandler getFailureHandler() {
		if (failureHandler == null) {
			failureHandler = new SimpleAuthenticationFailureHandler();
			failureHandler.setDefaultFailureUrl(Config.getConfigParam("security.signin_failure", "/signin?error"));
		}
		return failureHandler;
	}

	/**
	 * @param failureHandler handler
	 */
	public void setFailureHandler(SimpleAuthenticationFailureHandler failureHandler) {
		this.failureHandler = failureHandler;
	}

	/**
	 * @return service
	 */
	@Provides
	public SimpleRememberMeServices getRemembeMeServices() {
		if (rememberMeServices == null) {
			String authCookie = Config.getConfigParam("auth_cookie", Config.PARA.concat("-auth"));
			rememberMeServices = new SimpleRememberMeServices(Config.APP_SECRET_KEY, new SimpleUserService());
			rememberMeServices.setAlwaysRemember(Config.getConfigBoolean("security.remember_me", true));
			rememberMeServices.setTokenValiditySeconds(Config.SESSION_TIMEOUT_SEC);
			rememberMeServices.setCookieName(authCookie);
			rememberMeServices.setParameter(authCookie.concat("-remember-me"));

		}
		return rememberMeServices;
	}

	/**
	 * @param rememberMeServices service
	 */
	public void setRememberMeServices(SimpleRememberMeServices rememberMeServices) {
		this.rememberMeServices = rememberMeServices;
	}

	/**
	 * @return filter
	 */
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

	/**
	 * @param passwordFilter filter
	 */
	public void setPasswordFilter(PasswordAuthFilter passwordFilter) {
		this.passwordFilter = passwordFilter;
	}

	/**
	 * @return filter
	 */
	@Provides
	public PasswordlessAuthFilter getPasswordlessFilter() {
		if (passwordlessFilter == null) {
			passwordlessFilter = new PasswordlessAuthFilter("/" + PasswordlessAuthFilter.PASSWORDLESS_ACTION);
			passwordlessFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			passwordlessFilter.setAuthenticationFailureHandler(getFailureHandler());
			passwordlessFilter.setRememberMeServices(getRemembeMeServices());
		}
		return passwordlessFilter;
	}

	/**
	 * @param passwordlessFilter filter
	 */
	public void setPasswordlessFilter(PasswordlessAuthFilter passwordlessFilter) {
		this.passwordlessFilter = passwordlessFilter;
	}

	/**
	 * @return filter
	 */
	@Provides
	public OpenIDAuthFilter getOpenIDFilter() {
		if (openidFilter == null) {
			openidFilter = new OpenIDAuthFilter("/" + OpenIDAuthFilter.OPENID_ACTION);
			try {
				openidFilter.setConsumer(new OpenID4JavaConsumer(new SimpleAxFetchListFactory()));
			} catch (ConsumerException ex) {
				LoggerFactory.getLogger(SecurityModule.class).error(null, ex);
			}
			openidFilter.setReturnToUrlParameters(Collections.singleton(Config.getConfigParam("security.returnto", "/")));
			openidFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			openidFilter.setAuthenticationFailureHandler(getFailureHandler());
			openidFilter.setRememberMeServices(getRemembeMeServices());
		}
		return openidFilter;
	}

	/**
	 * @param openidFilter filter
	 */
	public void setOpenidFilter(OpenIDAuthFilter openidFilter) {
		this.openidFilter = openidFilter;
	}

	/**
	 * @return filter
	 */
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

	/**
	 * @param facebookFilter filter
	 */
	public void setFacebookFilter(FacebookAuthFilter facebookFilter) {
		this.facebookFilter = facebookFilter;
	}

	/**
	 * @return filter
	 */
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

	/**
	 * @param googleFilter filter
	 */
	public void setGoogleFilter(GoogleAuthFilter googleFilter) {
		this.googleFilter = googleFilter;
	}

	/**
	 * @return filter
	 */
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

	/**
	 * @param linkedinFilter filter
	 */
	public void setLinkedinFilter(LinkedInAuthFilter linkedinFilter) {
		this.linkedinFilter = linkedinFilter;
	}

	/**
	 * @return filter
	 */
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

	/**
	 * @param twitterFilter filter
	 */
	public void setTwitterFilter(TwitterAuthFilter twitterFilter) {
		this.twitterFilter = twitterFilter;
	}

	/**
	 * @return filter
	 */
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

	/**
	 * @param githubFilter filter
	 */
	public void setGithubFilter(GitHubAuthFilter githubFilter) {
		this.githubFilter = githubFilter;
	}

	/**
	 * @return filter
	 */
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

	/**
	 * @param microsoftFilter filter
	 */
	public void setMicrosoftFilter(MicrosoftAuthFilter microsoftFilter) {
		this.microsoftFilter = microsoftFilter;
	}

	/**
	 * @return filter
	 */
	@Provides
	public SlackAuthFilter getSlackFilter() {
		if (slackFilter == null) {
			slackFilter = new SlackAuthFilter("/" + SlackAuthFilter.SLACK_ACTION);
			slackFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			slackFilter.setAuthenticationFailureHandler(getFailureHandler());
			slackFilter.setRememberMeServices(getRemembeMeServices());
		}
		return slackFilter;
	}

	/**
	 * @param slackFilter filter
	 */
	public void setSlackFilter(SlackAuthFilter slackFilter) {
		this.slackFilter = slackFilter;
	}

	/**
	 * @return filter
	 */
	@Provides
	public AmazonAuthFilter getAmazonFilter() {
		if (amazonFilter == null) {
			amazonFilter = new AmazonAuthFilter("/" + AmazonAuthFilter.AMAZON_ACTION);
			amazonFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			amazonFilter.setAuthenticationFailureHandler(getFailureHandler());
			amazonFilter.setRememberMeServices(getRemembeMeServices());
		}
		return amazonFilter;
	}

	/**
	 * @param amazonFilter filter
	 */
	public void setAmazonFilter(AmazonAuthFilter amazonFilter) {
		this.amazonFilter = amazonFilter;
	}

	/**
	 * @return filter
	 */
	@Provides
	public GenericOAuth2Filter getGenericOAuth2Filter() {
		if (oauth2Filter == null) {
			oauth2Filter = new GenericOAuth2Filter("/" + GenericOAuth2Filter.OAUTH2_ACTION);
			oauth2Filter.setAuthenticationSuccessHandler(getSuccessHandler());
			oauth2Filter.setAuthenticationFailureHandler(getFailureHandler());
			oauth2Filter.setRememberMeServices(getRemembeMeServices());
		}
		return oauth2Filter;
	}

	/**
	 * @param oauth2Filter filter
	 */
	public void setGenericOAuth2Filter(GenericOAuth2Filter oauth2Filter) {
		this.oauth2Filter = oauth2Filter;
	}

	/**
	 * @return filter
	 */
	@Provides
	public LdapAuthFilter getLdapAuthFilter() {
		if (ldapFilter == null) {
			ldapFilter = new LdapAuthFilter("/" + LdapAuthFilter.LDAP_ACTION);
			ldapFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			ldapFilter.setAuthenticationFailureHandler(getFailureHandler());
			ldapFilter.setRememberMeServices(getRemembeMeServices());
		}
		return ldapFilter;
	}

	/**
	 * @param ldapFilter filter
	 */
	public void setLdapAuthFilter(LdapAuthFilter ldapFilter) {
		this.ldapFilter = ldapFilter;
	}

	/**
	 * @return filter
	 */
	@Provides
	public SAMLAuthFilter getSamlAuthFilter() {
		if (samlFilter == null) {
			samlFilter = new SAMLAuthFilter(SAMLAuthFilter.SAML_ACTION + "/*");
			samlFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			samlFilter.setAuthenticationFailureHandler(getFailureHandler());
			samlFilter.setRememberMeServices(getRemembeMeServices());
		}
		return samlFilter;
	}

	/**
	 * @param samlFilter filter
	 */
	public void setSamlAuthFilter(SAMLAuthFilter samlFilter) {
		this.samlFilter = samlFilter;
	}

	/**
	 * @return filter
	 */
	@Provides
	public SAMLMetadataFilter getSamlMetadataFilter() {
		if (samlMetaFilter == null) {
			samlMetaFilter = new SAMLMetadataFilter();
		}
		return samlMetaFilter;
	}

	/**
	 * @param samleMetaFilter filter
	 */
	public void setSamlMetadataFilter(SAMLMetadataFilter samleMetaFilter) {
		this.samlMetaFilter = samleMetaFilter;
	}

	/**
	 * @param fbAuth filter
	 * @param gpAuth filter
	 * @param ghAuth filter
	 * @param liAuth filter
	 * @param twAuth filter
	 * @param msAuth filter
	 * @param slAuth filter
	 * @param azAuth filter
	 * @param oAuth2 filter
	 * @param ldAuth filter
	 * @param pwAuth filter
	 * @param plAuth filter
	 * @return filter
	 */
	@Provides
	public JWTRestfulAuthFilter getJWTAuthFilter(FacebookAuthFilter fbAuth, GoogleAuthFilter gpAuth,
			GitHubAuthFilter ghAuth, LinkedInAuthFilter liAuth, TwitterAuthFilter twAuth,
			MicrosoftAuthFilter msAuth, SlackAuthFilter slAuth, AmazonAuthFilter azAuth,
			GenericOAuth2Filter oAuth2, LdapAuthFilter ldAuth,
			PasswordAuthFilter pwAuth, PasswordlessAuthFilter plAuth) {
		if (jwtFilter == null) {
			jwtFilter = new JWTRestfulAuthFilter("/" + JWTRestfulAuthFilter.JWT_ACTION);
			jwtFilter.setFacebookAuth(fbAuth);
			jwtFilter.setGoogleAuth(gpAuth);
			jwtFilter.setGithubAuth(ghAuth);
			jwtFilter.setLinkedinAuth(liAuth);
			jwtFilter.setTwitterAuth(twAuth);
			jwtFilter.setMicrosoftAuth(msAuth);
			jwtFilter.setSlackAuth(slAuth);
			jwtFilter.setAmazonAuth(azAuth);
			jwtFilter.setGenericOAuth2Auth(oAuth2);
			jwtFilter.setLdapAuth(ldAuth);
			jwtFilter.setPasswordAuth(pwAuth);
			jwtFilter.setPasswordlessAuth(plAuth);
		}
		return jwtFilter;
	}

	/**
	 * @param jwtFilter filter
	 */
	public void setJwtFilter(JWTRestfulAuthFilter jwtFilter) {
		this.jwtFilter = jwtFilter;
	}
}
