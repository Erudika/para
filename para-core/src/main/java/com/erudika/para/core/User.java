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
package com.erudika.para.core;

import com.erudika.para.core.annotations.Email;
import com.erudika.para.core.annotations.Locked;
import com.erudika.para.core.annotations.Stored;
import com.erudika.para.core.exceptions.RateLimitException;
import com.erudika.para.core.i18n.CurrencyUtils;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Pager;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.utils.Utils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The core user object. Stores information about users.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class User implements ParaObject {
	private static final long serialVersionUID = 1L;
	private static Logger logger = LoggerFactory.getLogger(User.class);

	/**
	 * Maximum password length.
	 */
	public static final int MAX_PASSWORD_LENGTH = 5000;

	/**
	 * The object ID.
	 */
	@Stored @Locked private String id;
	/**
	 * The timestamp.
	 */
	@Stored @Locked private Long timestamp;
	/**
	 * The object type.
	 */
	@Stored @Locked private String type;
	/**
	 * The appid.
	 */
	@Stored @Locked private String appid;
	/**
	 * The parentid.
	 */
	@Stored @Locked private String parentid;
	/**
	 * The creatorid.
	 */
	@Stored @Locked private String creatorid;
	/**
	 * The updated timestamp.
	 */
	@Stored private Long updated;
	/**
	 * The name.
	 */
	@Stored private String name;
	/**
	 * The tags.
	 */
	@Stored private List<String> tags;
	/**
	 * The votes.
	 */
	@Stored private Integer votes;
	/**
	 * The version.
	 */
	@Stored private Long version;
	/**
	 * The stored flag.
	 */
	@Stored private Boolean stored;
	/**
	 * The indexed flag.
	 */
	@Stored private Boolean indexed;
	/**
	 * The cached flag.
	 */
	@Stored private Boolean cached;

	/**
	 * The unique user identifier.
	 */
	@Stored @NotBlank private String identifier;
	/**
	 * The user groups.
	 */
	@Stored @Locked @NotBlank private String groups;
	/**
	 * The active flag.
	 */
	@Stored private Boolean active;
	/**
	 * The 2FA flag.
	 */
	@Stored private Boolean twoFA;
	/**
	 * The 2FA secret key.
	 */
	@Stored private String twoFAkey;
	/**
	 * The 2FA backup key hash.
	 */
	@Stored private String twoFAbackupKeyHash;
	/**
	 * The user email.
	 */
	@Stored @NotBlank @Email private String email;
	/**
	 * The currency.
	 */
	@Stored private String currency;
	/**
	 * The picture URL.
	 */
	@Stored private String picture;
	/**
	 * The token secret.
	 */
	@Stored @Locked private String tokenSecret;
	/**
	 * The IDP ID token.
	 */
	@Stored private String idpIdToken;
	/**
	 * The IDP access token.
	 */
	@Stored private String idpAccessToken;
	/**
	 * The IDP refresh token.
	 */
	@Stored private String idpRefreshToken;

	private transient String password;

	/**
	 * No-args constructor.
	 */
	public User() {
		this(null);
	}

	/**
	 * Default constructor.
	 * @param id the id
	 */
	public User(String id) {
		setId(id);
		setName(getName());
		this.groups = Groups.USERS.toString();
	}

	/**
	 * Token secret - used for generating JWT tokens.
	 * Changing this secret would invalidate all existing user tokens.
	 * A kind of global "logout".
	 * @return a random string
	 */
	@JsonIgnore
	public String getTokenSecret() {
		if (tokenSecret == null) {
			resetTokenSecret();
		}
		return tokenSecret;
	}

	/**
	 * Sets the token secret.
	 * @param tokenSecret a random string
	 */
	public void setTokenSecret(String tokenSecret) {
		this.tokenSecret = tokenSecret;
	}

	/**
	 * The profile picture URL.
	 * @return a URL or null
	 */
	public String getPicture() {
		return StringUtils.isBlank(picture) ?
				"https://www.gravatar.com/avatar?d=mm&size=400" : picture;
	}

	/**
	 * Sets the profile picture URL.
	 * @param picture the picture URL.
	 */
	public void setPicture(String picture) {
		this.picture = picture;
	}

	/**
	 * Returns true if this account is active.
	 * @return true if active
	 */
	public Boolean getActive() {
		if (active == null) {
			active = false;
		}
		return active;
	}

	/**
	 * Sets the account active.
	 * @param active true if active
	 */
	public void setActive(Boolean active) {
		this.active = active;
	}

	/**
	 * Returns true if 2FA is enabled.
	 * @return true if 2FA is enabled
	 */
	public Boolean getTwoFA() {
		if (twoFA == null) {
			twoFA = false;
		}
		return twoFA;
	}

	/**
	 * Sets 2FA enabled/disabled.
	 * @param twoFA true if 2FA is enabled
	 */
	public void setTwoFA(Boolean twoFA) {
		this.twoFA = twoFA;
	}

	/**
	 * Returns the 2FA secret key. Used for checking the TOTP code.
	 * @return the 2FA secret key. Used for checking the TOTP code.
	 */
	public String getTwoFAkey() {
		return twoFAkey;
	}

	/**
	 * Sets the 2FA secret key.
	 * @param twoFAkey secret key
	 */
	public void setTwoFAkey(String twoFAkey) {
		this.twoFAkey = twoFAkey;
	}

	/**
	 * Returns the hashed backup key (shown only once). Used for disabling 2FA.
	 * @return the hashed backup key (shown only once). Used for disabling 2FA.
	 */
	public String getTwoFAbackupKeyHash() {
		return twoFAbackupKeyHash;
	}

	/**
	 * Sets the 2FA backup key hash.
	 * @param twoFAbackupKeyHash bcrypt hash
	 */
	public void setTwoFAbackupKeyHash(String twoFAbackupKeyHash) {
		this.twoFAbackupKeyHash = twoFAbackupKeyHash;
	}

	/**
	 * Returns the security groups for this user.
	 * @return the groups string
	 */
	public String getGroups() {
		return groups;
	}

	/**
	 * Sets the security groups for this user.
	 * @param groups the groups string
	 */
	public void setGroups(String groups) {
		this.groups = groups;
	}

	/**
	 * Returns the main identifier for this user.
	 * An identifier is basically a unique username that identifies a user.
	 * @return the main identifier
	 */
	public String getIdentifier() {
		return identifier;
	}

	/**
	 * Sets the main identifier.
	 * @param identifier the main identifier
	 */
	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	/**
	 * The user's email.
	 * @return email
	 */
	public String getEmail() {
		return StringUtils.lowerCase(email);
	}

	/**
	 * Sets the email.
	 * @param email email
	 */
	public void setEmail(String email) {
		this.email = email;
	}

	/**
	 * The user's currency preference.
	 * @return a currency 3-letter code in uppercase
	 */
	public String getCurrency() {
		return currency;
	}

	/**
	 * Sets a preferred currency. Default is "EUR".
	 * @param currency a 3-letter currency code
	 */
	public void setCurrency(String currency) {
		currency = StringUtils.upperCase(currency);
		if (!CurrencyUtils.getInstance().isValidCurrency(currency)) {
			currency = "EUR";
		}
		this.currency = currency;
	}

	/**
	 * Generates a new token secret.
	 * This is whould be equivalent to "logout everywhere".
	 */
	public void resetTokenSecret() {
		tokenSecret = Utils.generateSecurityToken();
	}

	/**
	 * Note: this method assumes that child objects can be modified by their parents.
	 * This might not work for special cases where a parent has no rights over a child.
	 * @param obj an object
	 * @return true if the user is the creator or parent of this object or an admin user
	 */
	public boolean canModify(ParaObject obj) {
		if (obj == null || id == null) {
			return false;
		} else {
			boolean isCreatedByMe = obj.getCreatorid() != null &&
					(obj.getCreatorid().startsWith(id + Para.getConfig().separator()) || id.equals(obj.getCreatorid()));
			boolean mine = isCreatedByMe || id.equals(obj.getId()) || id.equals(obj.getParentid());
			return (mine || isAdmin());
		}
	}

	@Override
	public String create() {
		if (StringUtils.isBlank(getIdentifier())) {
			logger.warn("Failed to create user - identifier not set.");
			return null;
		}
		if (!StringUtils.isBlank(getPassword()) && getPassword().length() < Para.getConfig().minPasswordLength()) {
			logger.warn("Failed to create user - password too short.");
			return null;
		}
		if (readUserForIdentifier(this) != null) {
			logger.warn("Failed to create user - user with identifier '{}' already exists.", getIdentifier());
			return null;
		}

		// admin detected
		if (!Para.getConfig().adminIdentifier().isEmpty() && Para.getConfig().adminIdentifier().equals(getIdentifier())) {
			logger.info("Creating new user '{}' ({}) with admin privileges.", getName(), getIdentifier());
			setGroups(User.Groups.ADMINS.toString());
		}

		if (StringUtils.isBlank(getGroups())) {
			setGroups(User.Groups.USERS.toString());
		}

		setGravatarPicture();

		if (StringUtils.isBlank(tokenSecret)) {
			resetTokenSecret();
		}

		if (CoreUtils.getInstance().getDao().create(getAppid(), this) != null) {
			createIdentifier(getIdentifier(), getPassword());
		} else {
			logger.warn("Failed to create user - dao.create() returned null.");
		}

		return getId();
	}

	@Override
	public void delete() {
		if (getId() != null) {
			CoreUtils.getInstance().getDao().deleteAll(getAppid(), getIdentifiers());
			CoreUtils.getInstance().getDao().delete(getAppid(), this);
		}
	}

	/**
	 * Returns a list of identifiers for this user (can have many).
	 * @return a list of {@link Sysprop} objects
	 */
	private List<Sysprop> getIdentifiers() {
		return CoreUtils.getInstance().getSearch().findTerms(getAppid(), Utils.type(Sysprop.class),
				Collections.singletonMap(Config._CREATORID, getId()), true);
	}

	/**
	 * Attaches a new identifier to this user.
	 * @param identifier a new identifier
	 */
	public void attachIdentifier(String identifier) {
		if (this.exists()) {
			createIdentifier(identifier, Utils.generateSecurityToken());
		}
	}

	/**
	 * Detaches a secondary identifier which is not already used by this user.
	 * @param identifier an attached identifier
	 */
	public void detachIdentifier(String identifier) {
		if (!Strings.CS.equals(identifier, getIdentifier())) {
			Sysprop s = CoreUtils.getInstance().getDao().read(getAppid(), identifier);
			if (s != null && Strings.CS.equals(getId(), s.getCreatorid())) {
				deleteIdentifier(identifier);
			}
		}
	}

	/**
	 * Is the main identifier a Facebook id.
	 * @return true if user is signed in with Facebook
	 */
	@JsonIgnore
	public boolean isFacebookUser() {
		return Strings.CI.startsWith(identifier, Config.FB_PREFIX);
	}

	/**
	 * Is the main identifier a Google+ id.
	 * @return true if user is signed in with Google+
	 */
	@JsonIgnore
	public boolean isGooglePlusUser() {
		return Strings.CI.startsWith(identifier, Config.GPLUS_PREFIX);
	}

	/**
	 * Is the main identifier a LinkedIn id.
	 * @return true if user is signed in with LinkedIn
	 */
	@JsonIgnore
	public boolean isLinkedInUser() {
		return Strings.CI.startsWith(identifier, Config.LINKEDIN_PREFIX);
	}

	/**
	 * Is the main identifier a Twitter id.
	 * @return true if user is signed in with Twitter
	 */
	@JsonIgnore
	public boolean isTwitterUser() {
		return Strings.CI.startsWith(identifier, Config.TWITTER_PREFIX);
	}

	/**
	 * Is the main identifier a GitHub id.
	 * @return true if user is signed in with GitHub
	 */
	@JsonIgnore
	public boolean isGitHubUser() {
		return Strings.CI.startsWith(identifier, Config.GITHUB_PREFIX);
	}

	/**
	 * Is the main identifier a Microsoft/Windows account id.
	 * @return true if user is signed in with a Microsoft account
	 */
	@JsonIgnore
	public boolean isMicrosoftUser() {
		return Strings.CI.startsWith(identifier, Config.MICROSOFT_PREFIX);
	}

	/**
	 * Is the main identifier a Slack account id.
	 * @return true if user is signed in with a Slack account
	 */
	@JsonIgnore
	public boolean isSlackUser() {
		return Strings.CI.startsWith(identifier, Config.SLACK_PREFIX);
	}

	/**
	 * Is the main identifier a Mattermost account id.
	 * @return true if user is signed in with a Mattermost account
	 */
	@JsonIgnore
	public boolean isMattermostUser() {
		return Strings.CI.startsWith(identifier, Config.MATTERMOST_PREFIX);
	}

	/**
	 * Is the main identifier a Amazon account id.
	 * @return true if user is signed in with a Amazon account
	 */
	@JsonIgnore
	public boolean isAmazonUser() {
		return Strings.CI.startsWith(identifier, Config.AMAZON_PREFIX);
	}

	/**
	 * Is the main identifier a LDAP account.
	 * @return true if user is signed in with a LDAP account
	 */
	@JsonIgnore
	public boolean isLDAPUser() {
		return Strings.CI.startsWith(identifier, Config.LDAP_PREFIX);
	}

	/**
	 * Is the main identifier a SAML account.
	 * @return true if user is signed in with a SAML account
	 */
	@JsonIgnore
	public boolean isSAMLUser() {
		return Strings.CI.startsWith(identifier, Config.SAML_PREFIX);
	}

	/**
	 * Is the main identifier a custom SSO account.
	 * @return true if user is signed in with a custom SSO JWT token
	 */
	@JsonIgnore
	public boolean isPasswordlessUser() {
		return Strings.CI.startsWith(identifier, Config.PASSWORDLESS_PREFIX);
	}

	/**
	 * Is the main identifier from a generic OAuth 2.0/OpenID Connect provider.
	 * @return true if user is signed in with a generic OAauth 2.0 account
	 */
	@JsonIgnore
	public boolean isOAuth2User() {
		return Strings.CI.startsWith(identifier, Config.OAUTH2_PREFIX) ||
				Strings.CI.startsWith(identifier, Config.OAUTH2_SECOND_PREFIX) ||
				Strings.CI.startsWith(identifier, Config.OAUTH2_THIRD_PREFIX);
	}

	/**
	 * Checks for admin rights.
	 * @return true if user has admin rights
	 */
	@JsonIgnore
	public boolean isAdmin() {
		return Strings.CI.equals(this.groups, Groups.ADMINS.toString());
	}

	/**
	 * Checks for moderator rights.
	 * @return true if user has mod rights
	 */
	@JsonIgnore
	public boolean isModerator() {
		return isAdmin() ? true : Strings.CI.equals(this.groups, Groups.MODS.toString());
	}

	/**
	 * Returns the name of the identity provider.
	 * @return "facebook", "google"... etc.
	 */
	public String getIdentityProvider() {
		if (isFacebookUser()) {
			return "facebook";
		} else if (isGooglePlusUser()) {
			return "google";
		} else if (isGitHubUser()) {
			return "github";
		} else if (isTwitterUser()) {
			return "twitter";
		} else if (isLinkedInUser()) {
			return "linkedin";
		} else if (isMicrosoftUser()) {
			return "microsoft";
		} else if (isSlackUser()) {
			return "slack";
		} else if (isMattermostUser()) {
			return "mattermost";
		} else if (isAmazonUser()) {
			return "amazon";
		} else if (isLDAPUser()) {
			return "ldap";
		} else if (isSAMLUser()) {
			return "saml";
		} else if (isOAuth2User()) {
			return "oauth2";
		} else if (isPasswordlessUser()) {
			return "custom";
		} else if (Utils.isValidEmail(identifier)) {
			return "generic";
		} else {
			return "unknown";
		}
	}

	/**
	 * Validates the identifier property value.
	 * @return true if the user identifier has valid syntax.
	 */
	public boolean hasValidIdentifier() {
		return !getIdentityProvider().equals("unknown");
	}

	/**
	 * The password. A transient field used for validation.
	 * @return the password.
	 */
	@JsonIgnore
	public String getPassword() {
		return password;
	}

	/**
	 * Sets a password.
	 * @param password a password
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * Used for storing the ID token from an OpenID Connect/OAuth 2.0 identity provider.
	 * @return an ID token (JWT is always assumed to be the format)
	 */
	public String getIdpIdToken() {
		return idpIdToken;
	}

	/**
	 * Sets the IDP ID token.
	 * @param idpIdToken a token
	 */
	public void setIdpIdToken(String idpIdToken) {
		this.idpIdToken = idpIdToken;
	}

	/**
	 * Used for storing the access token from an OpenID Connect/OAuth 2.0 identity provider.
	 * @return a JWT access token (JWT is always assumed to be the format)
	 */
	public String getIdpAccessToken() {
		return idpAccessToken;
	}

	/**
	 * Sets the IDP access token.
	 * @param idpAccessToken a token
	 */
	public void setIdpAccessToken(String idpAccessToken) {
		this.idpAccessToken = idpAccessToken;
	}

	/**
	 * Stores the refresh token from the identity provider.
	 * @return a JWT refresh token
	 */
	public String getIdpRefreshToken() {
		return idpRefreshToken;
	}

	/**
	 * Sets the refresh token.
	 * @param idpRefreshToken a refresh token
	 */
	public void setIdpRefreshToken(String idpRefreshToken) {
		this.idpRefreshToken = idpRefreshToken;
	}

	/**
	 * Returns the JWT payload for the ID token coming from the IDP.
	 * Used for delegating user attributes data to clients. This must be a Base64-encoded JSON string.
	 * @return the payload part in Base64
	 */
	@JsonIgnore
	public String getIdpIdTokenPayload() {
		return StringUtils.substringBetween(idpIdToken, ".");
	}

	/**
	 * Returns the JWT payload for the access token coming from the IDP.
	 * Used for delegating user attributes data to clients. This must be a Base64-encoded JSON string.
	 * @return the payload part in Base64
	 */
	@JsonIgnore
	public String getIdpAccessTokenPayload() {
		return StringUtils.substringBetween(idpAccessToken, ".");
	}

	/**
	 * Returns a user object for a given identifier.
	 * @param u a user having a valid identifier set.
	 * @return a user or null if no user is found for this identifier
	 */
	public static final User readUserForIdentifier(final User u) {
		if (u == null || StringUtils.isBlank(u.getIdentifier())) {
			return null;
		}
		User user = null;
		String password = null;
		String identifier = u.getIdentifier();
		// Try to read the identifier object first, then read the user object linked to it.
		Sysprop s = CoreUtils.getInstance().getDao().read(u.getAppid(), identifier);
		if (s != null && s.getCreatorid() != null) {
			user = CoreUtils.getInstance().getDao().read(u.getAppid(), s.getCreatorid());
			password = (String) s.getProperty(Config._PASSWORD);
		}
		// Try to find the user by email if already created, but with a different identifier.
		// This prevents users with identical emails to have separate accounts by signing in through
		// different identity providers.
		if (user == null && !StringUtils.isBlank(u.getEmail())) {
			HashMap<String, Object> terms = new HashMap<>(2);
			terms.put(Config._EMAIL, u.getEmail());
			terms.put(Config._APPID, u.getAppid());
			Pager p = new Pager(1);
			List<User> users = CoreUtils.getInstance().getSearch().findTerms(u.getAppid(), u.getType(), terms, true, p);
			if (!users.isEmpty()) {
				user = users.get(0);
				// keep this random! dangerous to set it to user.getPassword()
				password = Utils.generateSecurityToken();
				user.createIdentifier(u.getIdentifier(), password);
				if (p.getCount() > 1) {
					logger.warn("{} user objects exist with the same email {}", p.getCount(), user.getEmail());
				}
			}
		}
		if (user != null) {
			if (password != null) {
				// used for remember me token signature calculations
				user.setPassword(password);
			}
			if (!identifier.equals(user.getIdentifier())) {
				logger.info("Identifier changed for user '{}', from {} to {}.",
						user.getId(), user.getIdentifier(), identifier);
				// the main identifier was changed - update
				user.setIdentifier(identifier);
				CoreUtils.getInstance().getDao().update(user.getAppid(), user);
			}
			return user;
		}
		logger.debug("User not found for identifier {}/{}, {}.", u.getAppid(), identifier, u.getId());
		return null;
	}

	/**
	 * Checks if a user has entered the correct password.
	 * Compares password hashes.
	 * @param u a user with a set password
	 * @return true if password matches the one in the data store
	 * @throws RateLimitException if rate limit is exceeded when password doesn't match.
	 */
	public static final boolean passwordMatches(User u) throws RateLimitException {
		if (u == null) {
			return false;
		}
		String password = u.getPassword();
		String identifier = u.getIdentifier();
		if (StringUtils.isBlank(password) || StringUtils.isBlank(identifier) || password.length() > MAX_PASSWORD_LENGTH) {
			return false;
		}
		ParaObject s = CoreUtils.getInstance().getDao().read(u.getAppid(), identifier);
		if (s != null) {
			if (s instanceof Sysprop) {
				String storedHash = (String) ((Sysprop) s).getProperty(Config._PASSWORD);
				boolean matches = Utils.bcryptMatches(password, storedHash);
				if (matches) {
					((Sysprop) s).setVotes(0);
					((Sysprop) s).removeProperty("lockedUntil");
					CoreUtils.getInstance().getDao().update(u.getAppid(), s);
				} else {
					if (((Sysprop) s).hasProperty("lockedUntil") &&
							(long) ((Sysprop) s).getProperty("lockedUntil") > System.currentTimeMillis()) {
						logger.warn("Too many login attempts for user {} ({}/{}), account locked.",
								u.getId(), u.getAppid(), identifier);
						throw new RateLimitException("Too many login attempts!");
					}
					((Sysprop) s).setVotes(((Sysprop) s).getVotes() + 1);
					if (((Sysprop) s).getVotes() >= Para.getConfig().maxPasswordMatchingAttempts()) {
						((Sysprop) s).addProperty("lockedUntil", System.currentTimeMillis() +
								TimeUnit.HOURS.toNanos(Para.getConfig().passwordMatchingLockPeriodHours()));
						CoreUtils.getInstance().getDao().update(u.getAppid(), s);
					} else {
						CoreUtils.getInstance().getDao().update(u.getAppid(), s);
					}
				}
				return matches;
			} else {
				LoggerFactory.getLogger(User.class).
						warn(Utils.formatMessage("Failed to read auth object for user '{}' using identifier '{}'.",
								u.getId(), identifier));
			}
		}
		return false;
	}

	/**
	 * Generates a new password reset token. Sent via email for pass reset.
	 * @return the pass reset token
	 */
	public final String generatePasswordResetToken() {
		if (StringUtils.isBlank(identifier)) {
			return "";
		}
		Sysprop s = CoreUtils.getInstance().getDao().read(getAppid(), identifier);
		if (s != null) {
			String token = Utils.generateSecurityToken(42, true);
			s.addProperty(Config._RESET_TOKEN, token);
			CoreUtils.getInstance().getDao().update(getAppid(), s);
			return token;
		}
		return "";
	}

	/**
	 * Changes the user password permanently.
	 * @param token the reset token. see {@link #generatePasswordResetToken()}
	 * @param newpass the new password
	 * @return true if successful
	 */
	public final boolean resetPassword(String token, String newpass) {
		if (StringUtils.isBlank(newpass) || StringUtils.isBlank(token) || newpass.length() < Para.getConfig().minPasswordLength()) {
			return false;
		}
		Sysprop s = CoreUtils.getInstance().getDao().read(getAppid(), identifier);
		if (isValidToken(s, Config._RESET_TOKEN, token)) {
			s.removeProperty(Config._RESET_TOKEN);
			String hashed = Utils.bcrypt(newpass);
			s.addProperty(Config._PASSWORD, hashed);
			setPassword(hashed);
			CoreUtils.getInstance().getDao().update(getAppid(), s);
			return true;
		}
		return false;
	}

	/**
	 * Creates a new identifier object using {@link Sysprop}.
	 * Used for identifying a user when signing in.
	 * @param newIdent a new identifier
	 * @param password a password for the user (optional)
	 * @return true if successful
	 */
	private boolean createIdentifier(String newIdent, String password) {
		if (StringUtils.isBlank(getId()) || StringUtils.isBlank(newIdent)) {
			return false;
		}
		Sysprop s = new Sysprop();
		s.setId(newIdent);
		s.setName(Config._IDENTIFIER);
		s.setCreatorid(getId());
		if (!StringUtils.isBlank(password)) {
			String hashed = Utils.bcrypt(password);
			s.addProperty(Config._PASSWORD, hashed);
			setPassword(hashed);
		}
		return CoreUtils.getInstance().getDao().create(getAppid(), s) != null;
	}

	/**
	 * Deletes the identifier and the user can no longer sign in with it.
	 * @param ident the attached identifier
	 */
	private void deleteIdentifier(String ident) {
		if (!StringUtils.isBlank(ident)) {
			CoreUtils.getInstance().getDao().delete(getAppid(), new Sysprop(ident));
		}
	}

	/**
	 * Generates a new email confirmation token. Sent via email for user activation.
	 * @return a Base64 encoded UUID
	 */
	public String generateEmailConfirmationToken() {
		if (StringUtils.isBlank(identifier)) {
			return "";
		}
		Sysprop s = CoreUtils.getInstance().getDao().read(getAppid(), identifier);
		if (s != null) {
			String token = Utils.base64encURL(Utils.generateSecurityToken().getBytes());
			s.addProperty(Config._EMAIL_TOKEN, token);
			CoreUtils.getInstance().getDao().update(getAppid(), s);
			return token;
		}
		return "";
	}

	/**
	 * Activates a user if a given token matches the one stored.
	 * @param token the email confirmation token. see {@link #generateEmailConfirmationToken() }
	 * @return true if successful
	 */
	public final boolean activateWithEmailToken(String token) {
		Sysprop s = CoreUtils.getInstance().getDao().read(getAppid(), identifier);
		if (isValidToken(s, Config._EMAIL_TOKEN, token)) {
			s.removeProperty(Config._EMAIL_TOKEN);
			CoreUtils.getInstance().getDao().update(getAppid(), s);
			setActive(true);
			update();
			return true;
		}
		return false;
	}

	/**
	 * Validates a token sent via email for password reset.
	 * @param token a token
	 * @return true if valid
	 */
	public final boolean isValidPasswordResetToken(String token) {
		Sysprop s = CoreUtils.getInstance().getDao().read(getAppid(), identifier);
		return isValidToken(s, Config._RESET_TOKEN, token);
	}

	/**
	 * Validates a token sent for email confirmation.
	 * @param token a token
	 * @return true if valid
	 */
	public final boolean isValidEmailConfirmationToken(String token) {
		Sysprop s = CoreUtils.getInstance().getDao().read(getAppid(), identifier);
		return isValidToken(s, Config._EMAIL_TOKEN, token);
	}

	private boolean isValidToken(Sysprop s, String key, String token) {
		if (StringUtils.isBlank(token)) {
			return false;
		}
		if (s != null && s.hasProperty(key)) {
			String storedToken = (String) s.getProperty(key);
			// tokens expire afer a reasonably short period ~ 30 mins
			long timeout = (long) Para.getConfig().passwordResetTimeoutSec() * 1000L;
			if (Strings.CS.equals(storedToken, token) && (s.getUpdated() + timeout) > Utils.timestamp()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Sets the profile picture using the Gravatar service.
	 */
	private void setGravatarPicture() {
		if (StringUtils.isBlank(picture)) {
			if (email != null) {
				String emailHash = Utils.md5(email.toLowerCase());
				setPicture("https://www.gravatar.com/avatar/" + emailHash + "?size=400&d=mm&r=pg");
			} else {
				setPicture("https://www.gravatar.com/avatar?d=mm&size=400");
			}
		}
	}

	/**
	 * Simple groups enum.
	 */
	public enum Groups {
		/**
		 * The standard user group.
		 */
		USERS,
		/**
		 * Moderators group.
		 */
		MODS,
		/**
		 * Administrators group.
		 */
		ADMINS;

		@Override
		public String toString() {
			return this.name().toLowerCase();
		}
	}

	/**
	 * Simple user roles enum.
	 */
	public enum Roles {
		/**
		 * The standard role.
		 */
		USER,
		/**
		 * The moderator role.
		 */
		MOD,
		/**
		 * The administrator role.
		 */
		ADMIN;

		@Override
		public String toString() {
			return "ROLE_".concat(this.name());
		}
	}

	////////////////////////////////////////////////////////

	/**
	 * Returns the object ID.
	 * @return the id
	 */
	@Override
	public final String getId() {
		return id;
	}

	/**
	 * Sets the object ID.
	 * @param id the id
	 */
	@Override
	public final void setId(String id) {
		this.id = id;
	}

	/**
	 * Returns the object type.
	 * @return the type
	 */
	@Override
	public final String getType() {
		type = (type == null) ? Utils.type(this.getClass()) : type;
		return type;
	}

	/**
	 * Sets the object type.
	 * @param type the type
	 */
	@Override
	public final void setType(String type) {
		this.type = type;
	}

	/**
	 * Returns the app identifier.
	 * @return the appid
	 */
	@Override
	public String getAppid() {
		appid = (appid == null) ? Para.getConfig().getRootAppIdentifier() : appid;
		return appid;
	}

	/**
	 * Sets the app identifier.
	 * @param appid the appid
	 */
	@Override
	public void setAppid(String appid) {
		this.appid = appid;
	}

	/**
	 * Returns the object URI.
	 * @return the URI
	 */
	@Override
	public String getObjectURI() {
		return CoreUtils.getInstance().getObjectURI(this);
	}

	/**
	 * Returns the tags.
	 * @return the tags
	 */
	@Override
	public List<String> getTags() {
		return tags;
	}

	/**
	 * Sets the tags.
	 * @param tags the tags
	 */
	@Override
	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	/**
	 * Returns true if the object is stored in the database.
	 * @return true if stored
	 */
	@Override
	public Boolean getStored() {
		if (stored == null) {
			stored = true;
		}
		return stored;
	}

	/**
	 * Sets the stored flag.
	 * @param stored the stored flag
	 */
	@Override
	public void setStored(Boolean stored) {
		this.stored = stored;
	}

	/**
	 * Returns true if the object is indexed.
	 * @return true if indexed
	 */
	@Override
	public Boolean getIndexed() {
		if (indexed == null) {
			indexed = true;
		}
		return indexed;
	}

	/**
	 * Sets the indexed flag.
	 * @param indexed the indexed flag
	 */
	@Override
	public void setIndexed(Boolean indexed) {
		this.indexed = indexed;
	}

	/**
	 * Returns true if the object is cached.
	 * @return true if cached
	 */
	@Override
	public Boolean getCached() {
		if (cached == null) {
			cached = true;
		}
		return cached;
	}

	/**
	 * Sets the cached flag.
	 * @param cached the cached flag
	 */
	@Override
	public void setCached(Boolean cached) {
		this.cached = cached;
	}

	/**
	 * Returns the timestamp.
	 * @return the timestamp
	 */
	@Override
	public Long getTimestamp() {
		return (timestamp != null && timestamp != 0) ? timestamp : null;
	}

	/**
	 * Sets the timestamp.
	 * @param timestamp the timestamp
	 */
	@Override
	public void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Returns the creator ID.
	 * @return the creatorid
	 */
	@Override
	public String getCreatorid() {
		return creatorid;
	}

	/**
	 * Sets the creator ID.
	 * @param creatorid the creatorid
	 */
	@Override
	public void setCreatorid(String creatorid) {
		this.creatorid = creatorid;
	}

	/**
	 * Returns the object name.
	 * @return the name
	 */
	@Override
	public final String getName() {
			return CoreUtils.getInstance().getName(name, id).replaceAll("[\\p{S}\\p{P}\\p{C}&&[^'\\-,\\.]]", "").
					replaceAll("\\p{Z}+", " ").trim();
	}

	/**
	 * Sets the object name.
	 * @param name the name
	 */
	@Override
	public final void setName(String name) {
		this.name = (name == null || !name.isEmpty()) ? name : this.name;
	}

	/**
	 * Returns the plural form of the object type.
	 * @return the plural name
	 */
	@Override
	public String getPlural() {
		return Utils.singularToPlural(getType());
	}

	/**
	 * Returns the parent ID.
	 * @return the parentid
	 */
	@Override
	public String getParentid() {
		return parentid;
	}

	/**
	 * Sets the parent ID.
	 * @param parentid the parentid
	 */
	@Override
	public void setParentid(String parentid) {
		this.parentid = parentid;
	}

	/**
	 * Returns the updated timestamp.
	 * @return the updated timestamp
	 */
	@Override
	public Long getUpdated() {
		return (updated != null && updated != 0) ? updated : null;
	}

	/**
	 * Sets the updated timestamp.
	 * @param updated the updated timestamp
	 */
	@Override
	public void setUpdated(Long updated) {
		this.updated = updated;
	}

	/**
	 * Updates the object in the database.
	 */
	@Override
	public void update() {
		CoreUtils.getInstance().getDao().update(getAppid(), this);
	}

	/**
	 * Returns true if the object exists in the database.
	 * @return true if exists
	 */
	@Override
	public boolean exists() {
		return CoreUtils.getInstance().getDao().read(getAppid(), getId()) != null;
	}

	/**
	 * Votes up for the object.
	 * @param userid the voter ID
	 * @return true if successful
	 */
	@Override
	public boolean voteUp(String userid) {
		return CoreUtils.getInstance().vote(this, userid, VoteValue.UP);
	}

	/**
	 * Votes down for the object.
	 * @param userid the voter ID
	 * @return true if successful
	 */
	@Override
	public boolean voteDown(String userid) {
		return CoreUtils.getInstance().vote(this, userid, VoteValue.DOWN);
	}

	/**
	 * Returns the number of votes.
	 * @return the votes
	 */
	@Override
	public Integer getVotes() {
		return (votes == null) ? 0 : votes;
	}

	/**
	 * Sets the number of votes.
	 * @param votes the votes
	 */
	@Override
	public void setVotes(Integer votes) {
		this.votes = votes;
	}

	/**
	 * Returns the version of the object.
	 * @return the version
	 */
	@Override
	public Long getVersion() {
		return (version == null) ? 0 : version;
	}

	/**
	 * Sets the version of the object.
	 * @param version the version
	 */
	@Override
	public void setVersion(Long version) {
		this.version = version;
	}

	/**
	 * Counts the number of links to other objects of a certain type.
	 * @param type2 the other type
	 * @return the number of links
	 */
	@Override
	public Long countLinks(String type2) {
		return CoreUtils.getInstance().countLinks(this, type2);
	}

	/**
	 * Returns a list of links to other objects of a certain type.
	 * @param type2 the other type
	 * @param pager a pager
	 * @return a list of links
	 */
	@Override
	public List<Linker> getLinks(String type2, Pager... pager) {
		return CoreUtils.getInstance().getLinks(this, type2, pager);
	}

	/**
	 * Returns a list of linked objects of a certain type.
	 * @param <P> the type of linked objects
	 * @param type the other type
	 * @param pager a pager
	 * @return a list of linked objects
	 */
	@Override
	public <P extends ParaObject> List<P> getLinkedObjects(String type, Pager... pager) {
		return CoreUtils.getInstance().getLinkedObjects(this, type, pager);
	}

	/**
	 * Finds linked objects of a certain type that match a specific field and query.
	 * @param <P> the type of linked objects
	 * @param type the other type
	 * @param field the field to search in
	 * @param query the search query
	 * @param pager a pager
	 * @return a list of linked objects
	 */
	@Override
	public <P extends ParaObject> List<P> findLinkedObjects(String type, String field, String query, Pager... pager) {
		return CoreUtils.getInstance().findLinkedObjects(this, type, field, query, pager);
	}

	/**
	 * Returns true if the object is linked to another object of a certain type and ID.
	 * @param type2 the other type
	 * @param id2 the other ID
	 * @return true if linked
	 */
	@Override
	public boolean isLinked(String type2, String id2) {
		return CoreUtils.getInstance().isLinked(this, type2, id2);
	}

	/**
	 * Returns true if the object is linked to another object.
	 * @param toObj the other object
	 * @return true if linked
	 */
	@Override
	public boolean isLinked(ParaObject toObj) {
		return CoreUtils.getInstance().isLinked(this, toObj);
	}

	/**
	 * Links the object to another object of a certain type and ID.
	 * @param id2 the other ID
	 * @return the link ID
	 */
	@Override
	public String link(String id2) {
		return CoreUtils.getInstance().link(this, id2);
	}

	/**
	 * Unlinks the object from another object of a certain type and ID.
	 * @param type the other type
	 * @param id2 the other ID
	 */
	@Override
	public void unlink(String type, String id2) {
		CoreUtils.getInstance().unlink(this, type, id2);
	}

	/**
	 * Unlinks the object from all other objects.
	 */
	@Override
	public void unlinkAll() {
		CoreUtils.getInstance().unlinkAll(this);
	}

	/**
	 * Counts the number of children of a certain type.
	 * @param type the child type
	 * @return the number of children
	 */
	@Override
	public Long countChildren(String type) {
		return CoreUtils.getInstance().countChildren(this, type);
	}

	/**
	 * Returns a list of children of a certain type.
	 * @param <P> the type of children
	 * @param type the child type
	 * @param pager a pager
	 * @return a list of children
	 */
	@Override
	public <P extends ParaObject> List<P> getChildren(String type, Pager... pager) {
		return CoreUtils.getInstance().getChildren(this, type, pager);
	}

	/**
	 * Returns a list of children of a certain type that match a specific field and term.
	 * @param <P> the type of children
	 * @param type the child type
	 * @param field the field to search in
	 * @param term the search term
	 * @param pager a pager
	 * @return a list of children
	 */
	@Override
	public <P extends ParaObject> List<P> getChildren(String type, String field, String term, Pager... pager) {
		return CoreUtils.getInstance().getChildren(this, type, field, term, pager);
	}

	/**
	 * Finds children of a certain type that match a specific query.
	 * @param <P> the type of children
	 * @param type the child type
	 * @param query the search query
	 * @param pager a pager
	 * @return a list of children
	 */
	@Override
	public <P extends ParaObject> List<P> findChildren(String type, String query, Pager... pager) {
		return CoreUtils.getInstance().findChildren(this, type, query, pager);
	}

	/**
	 * Deletes all children of a certain type.
	 * @param type the child type
	 */
	@Override
	public void deleteChildren(String type) {
		CoreUtils.getInstance().deleteChildren(this, type);
	}

	/**
	 * Returns the hash code for this object.
	 * @return hash code
	 */
	@Override
	public int hashCode() {
		int hash = 7;
		hash = 67 * hash + Objects.hashCode(this.id) + Objects.hashCode(this.name);
		return hash;
	}

	/**
	 * Returns true if this object is equal to another.
	 * @param obj the other object
	 * @return true if equal
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final ParaObject other = (ParaObject) obj;
		if (!Objects.equals(this.id, other.getId())) {
			return false;
		}
		return true;
	}

	/**
	 * Returns a JSON string representation of this object.
	 * @return JSON string
	 */
	@Override
	public String toString() {
		return ParaObjectUtils.toJSON(this);
	}
}
