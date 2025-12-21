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
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

/**
 * The default security module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SecurityModule extends AbstractModule {

	private SimpleAuthenticationSuccessHandler successHandler;
	private SimpleAuthenticationFailureHandler failureHandler;
	private PasswordAuthFilter passwordFilter;
	private PasswordlessAuthFilter passwordlessFilter;
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

	/**
	 * Creates the security module that wires authentication filters and handlers.
	 */
	public SecurityModule() {
		// default constructor
	}

	protected void configure() {
	}

	/**
	 * getter/setter.
	 * @return handler
	 */
	@Provides
	public SimpleAuthenticationSuccessHandler getSuccessHandler() {
		if (successHandler == null) {
			successHandler = new SimpleAuthenticationSuccessHandler();
			successHandler.setDefaultTargetUrl(Para.getConfig().signinSuccessPath());
			successHandler.setTargetUrlParameter(Para.getConfig().returnToPath());
			successHandler.setUseReferer(false);
		}
		return successHandler;
	}

	/**
	 * getter/setter.
	 * @param successHandler handler
	 */
	public void setSuccessHandler(SimpleAuthenticationSuccessHandler successHandler) {
		this.successHandler = successHandler;
	}

	/**
	 * getter/setter.
	 * @return handler
	 */
	@Provides
	public SimpleAuthenticationFailureHandler getFailureHandler() {
		if (failureHandler == null) {
			failureHandler = new SimpleAuthenticationFailureHandler();
			failureHandler.setDefaultFailureUrl(Para.getConfig().signinFailurePath());
		}
		return failureHandler;
	}

	/**
	 * getter/setter.
	 * @param failureHandler handler
	 */
	public void setFailureHandler(SimpleAuthenticationFailureHandler failureHandler) {
		this.failureHandler = failureHandler;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Provides
	public PasswordAuthFilter getPasswordFilter() {
		if (passwordFilter == null) {
			passwordFilter = new PasswordAuthFilter("/" + PasswordAuthFilter.PASSWORD_ACTION);
			passwordFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			passwordFilter.setAuthenticationFailureHandler(getFailureHandler());
		}
		return passwordFilter;
	}

	/**
	 * getter/setter.
	 * @param passwordFilter filter
	 */
	public void setPasswordFilter(PasswordAuthFilter passwordFilter) {
		this.passwordFilter = passwordFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Provides
	public PasswordlessAuthFilter getPasswordlessFilter() {
		if (passwordlessFilter == null) {
			passwordlessFilter = new PasswordlessAuthFilter("/" + PasswordlessAuthFilter.PASSWORDLESS_ACTION);
			passwordlessFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			passwordlessFilter.setAuthenticationFailureHandler(getFailureHandler());
		}
		return passwordlessFilter;
	}

	/**
	 * getter/setter.
	 * @param passwordlessFilter filter
	 */
	public void setPasswordlessFilter(PasswordlessAuthFilter passwordlessFilter) {
		this.passwordlessFilter = passwordlessFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Provides
	public FacebookAuthFilter getFacebookFilter() {
		if (facebookFilter == null) {
			facebookFilter = new FacebookAuthFilter("/" + FacebookAuthFilter.FACEBOOK_ACTION);
			facebookFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			facebookFilter.setAuthenticationFailureHandler(getFailureHandler());
		}
		return facebookFilter;
	}

	/**
	 * getter/setter.
	 * @param facebookFilter filter
	 */
	public void setFacebookFilter(FacebookAuthFilter facebookFilter) {
		this.facebookFilter = facebookFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Provides
	public GoogleAuthFilter getGoogleFilter() {
		if (googleFilter == null) {
			googleFilter = new GoogleAuthFilter("/" + GoogleAuthFilter.GOOGLE_ACTION);
			googleFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			googleFilter.setAuthenticationFailureHandler(getFailureHandler());
		}
		return googleFilter;
	}

	/**
	 * getter/setter.
	 * @param googleFilter filter
	 */
	public void setGoogleFilter(GoogleAuthFilter googleFilter) {
		this.googleFilter = googleFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Provides
	public LinkedInAuthFilter getLinkedinFilter() {
		if (linkedinFilter == null) {
			linkedinFilter = new LinkedInAuthFilter("/" + LinkedInAuthFilter.LINKEDIN_ACTION);
			linkedinFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			linkedinFilter.setAuthenticationFailureHandler(getFailureHandler());
		}
		return linkedinFilter;
	}

	/**
	 * getter/setter.
	 * @param linkedinFilter filter
	 */
	public void setLinkedinFilter(LinkedInAuthFilter linkedinFilter) {
		this.linkedinFilter = linkedinFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Provides
	public TwitterAuthFilter getTwitterFilter() {
		if (twitterFilter == null) {
			twitterFilter = new TwitterAuthFilter("/" + TwitterAuthFilter.TWITTER_ACTION);
			twitterFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			twitterFilter.setAuthenticationFailureHandler(getFailureHandler());
		}
		return twitterFilter;
	}

	/**
	 * getter/setter.
	 * @param twitterFilter filter
	 */
	public void setTwitterFilter(TwitterAuthFilter twitterFilter) {
		this.twitterFilter = twitterFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Provides
	public GitHubAuthFilter getGithubFilter() {
		if (githubFilter == null) {
			githubFilter = new GitHubAuthFilter("/" + GitHubAuthFilter.GITHUB_ACTION);
			githubFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			githubFilter.setAuthenticationFailureHandler(getFailureHandler());
		}
		return githubFilter;
	}

	/**
	 * getter/setter.
	 * @param githubFilter filter
	 */
	public void setGithubFilter(GitHubAuthFilter githubFilter) {
		this.githubFilter = githubFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Provides
	public MicrosoftAuthFilter getMicrosoftFilter() {
		if (microsoftFilter == null) {
			microsoftFilter = new MicrosoftAuthFilter("/" + MicrosoftAuthFilter.MICROSOFT_ACTION);
			microsoftFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			microsoftFilter.setAuthenticationFailureHandler(getFailureHandler());
		}
		return microsoftFilter;
	}

	/**
	 * getter/setter.
	 * @param microsoftFilter filter
	 */
	public void setMicrosoftFilter(MicrosoftAuthFilter microsoftFilter) {
		this.microsoftFilter = microsoftFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Provides
	public SlackAuthFilter getSlackFilter() {
		if (slackFilter == null) {
			slackFilter = new SlackAuthFilter("/" + SlackAuthFilter.SLACK_ACTION);
			slackFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			slackFilter.setAuthenticationFailureHandler(getFailureHandler());
		}
		return slackFilter;
	}

	/**
	 * getter/setter.
	 * @param slackFilter filter
	 */
	public void setSlackFilter(SlackAuthFilter slackFilter) {
		this.slackFilter = slackFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Provides
	public AmazonAuthFilter getAmazonFilter() {
		if (amazonFilter == null) {
			amazonFilter = new AmazonAuthFilter("/" + AmazonAuthFilter.AMAZON_ACTION);
			amazonFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			amazonFilter.setAuthenticationFailureHandler(getFailureHandler());
		}
		return amazonFilter;
	}

	/**
	 * getter/setter.
	 * @param amazonFilter filter
	 */
	public void setAmazonFilter(AmazonAuthFilter amazonFilter) {
		this.amazonFilter = amazonFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Provides
	public GenericOAuth2Filter getGenericOAuth2Filter() {
		if (oauth2Filter == null) {
			oauth2Filter = new GenericOAuth2Filter("/" + GenericOAuth2Filter.OAUTH2_ACTION);
			oauth2Filter.setAuthenticationSuccessHandler(getSuccessHandler());
			oauth2Filter.setAuthenticationFailureHandler(getFailureHandler());
		}
		return oauth2Filter;
	}

	/**
	 * getter/setter.
	 * @param oauth2Filter filter
	 */
	public void setGenericOAuth2Filter(GenericOAuth2Filter oauth2Filter) {
		this.oauth2Filter = oauth2Filter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Provides
	public LdapAuthFilter getLdapAuthFilter() {
		if (ldapFilter == null) {
			ldapFilter = new LdapAuthFilter("/" + LdapAuthFilter.LDAP_ACTION);
			ldapFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			ldapFilter.setAuthenticationFailureHandler(getFailureHandler());
		}
		return ldapFilter;
	}

	/**
	 * getter/setter.
	 * @param ldapFilter filter
	 */
	public void setLdapAuthFilter(LdapAuthFilter ldapFilter) {
		this.ldapFilter = ldapFilter;
	}

	/**
	 * getter/setter.
	 * @return filter
	 */
	@Provides
	public SAMLAuthFilter getSamlAuthFilter() {
		if (samlFilter == null) {
			samlFilter = new SAMLAuthFilter(SAMLAuthFilter.SAML_ACTION + "/*");
			samlFilter.setAuthenticationSuccessHandler(getSuccessHandler());
			samlFilter.setAuthenticationFailureHandler(getFailureHandler());
		}
		return samlFilter;
	}

	/**
	 * getter/setter.
	 * @param samlFilter filter
	 */
	public void setSamlAuthFilter(SAMLAuthFilter samlFilter) {
		this.samlFilter = samlFilter;
	}

	/**
	 * getter/setter.
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
	 * getter/setter.
	 * @param samleMetaFilter filter
	 */
	public void setSamlMetadataFilter(SAMLMetadataFilter samleMetaFilter) {
		this.samlMetaFilter = samleMetaFilter;
	}

}
