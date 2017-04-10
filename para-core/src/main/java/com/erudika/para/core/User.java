/*
 * Copyright 2013-2017 Erudika. https://erudika.com
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

import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.eaio.uuid.UUID;
import com.erudika.para.annotations.Email;
import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.i18n.CurrencyUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The core user object. Stores information about users.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class User implements ParaObject {
	private static final long serialVersionUID = 1L;
	private static Logger logger = LoggerFactory.getLogger(User.class);

	@Stored @Locked private String id;
	@Stored @Locked private Long timestamp;
	@Stored @Locked private String type;
	@Stored @Locked private String appid;
	@Stored @Locked private String parentid;
	@Stored @Locked private String creatorid;
	@Stored private Long updated;
	@Stored private String name;
	@Stored private List<String> tags;
	@Stored private Integer votes;
	@Stored private Boolean stored;
	@Stored private Boolean indexed;
	@Stored private Boolean cached;

	@Stored @NotBlank private String identifier;
	@Stored @Locked @NotBlank private String groups;
	@Stored private Boolean active;
	@Stored @NotBlank @Email private String email;
	@Stored private String currency;
	@Stored private String picture;
	@Stored private String lastIp;
	@Stored @Locked private String tokenSecret;

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
	 *	The IP address of the user recorded on last login.
	 * @return the IP or null
	 */
	@JsonIgnore
	public String getLastIp() {
		return lastIp;
	}

	/**
	 * Sets the IP of the user.
	 * @param lastIp last known IP address
	 */
	public void setLastIp(String lastIp) {
		this.lastIp = lastIp;
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
		return email;
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
					(obj.getCreatorid().startsWith(id + Config.SEPARATOR) || id.equals(obj.getCreatorid()));
			boolean mine = isCreatedByMe || id.equals(obj.getId()) || id.equals(obj.getParentid());
			return (mine || isAdmin());
		}
	}

	@Override
	public String create() {
		if (StringUtils.isBlank(getIdentifier())) {
			return null;
		}
		if (!StringUtils.isBlank(getPassword()) && getPassword().length() < Config.MIN_PASS_LENGTH) {
			return null;
		}

		// admin detected
		if (!Config.ADMIN_IDENT.isEmpty() && Config.ADMIN_IDENT.equals(getIdentifier())) {
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
	@JsonIgnore
	public List<Sysprop> getIdentifiers() {
		return CoreUtils.getInstance().getSearch().findTerms(getAppid(), Utils.type(Sysprop.class),
				Collections.singletonMap(Config._CREATORID, getId()), true);
	}

	/**
	 * Attaches a new identifier to this user.
	 * @param identifier a new identifier
	 */
	public void attachIdentifier(String identifier) {
		if (this.exists()) {
			createIdentifier(getId(), identifier);
		}
	}

	/**
	 * Detaches a secondary identifier which is not already used by this user.
	 * @param identifier an attached identifier
	 */
	public void detachIdentifier(String identifier) {
		if (!StringUtils.equals(identifier, getIdentifier())) {
			Sysprop s = CoreUtils.getInstance().getDao().read(getAppid(), identifier);
			if (s != null && StringUtils.equals(getId(), s.getCreatorid())) {
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
		return StringUtils.startsWithIgnoreCase(identifier, Config.FB_PREFIX);
	}

	/**
	 * Is the main identifier a Google+ id.
	 * @return true if user is signed in with Google+
	 */
	@JsonIgnore
	public boolean isGooglePlusUser() {
		return StringUtils.startsWithIgnoreCase(identifier, Config.GPLUS_PREFIX);
	}

	/**
	 * Is the main identifier a LinkedIn id.
	 * @return true if user is signed in with LinkedIn
	 */
	@JsonIgnore
	public boolean isLinkedInUser() {
		return StringUtils.startsWithIgnoreCase(identifier, Config.LINKEDIN_PREFIX);
	}

	/**
	 * Is the main identifier a Twitter id.
	 * @return true if user is signed in with Twitter
	 */
	@JsonIgnore
	public boolean isTwitterUser() {
		return StringUtils.startsWithIgnoreCase(identifier, Config.TWITTER_PREFIX);
	}

	/**
	 * Is the main identifier a GitHub id.
	 * @return true if user is signed in with GitHub
	 */
	@JsonIgnore
	public boolean isGitHubUser() {
		return StringUtils.startsWithIgnoreCase(identifier, Config.GITHUB_PREFIX);
	}

	/**
	 * Is the main identifier a Microsoft/Windows account id.
	 * @return true if user is signed in with a Microsoft account
	 */
	@JsonIgnore
	public boolean isMicrosoftUser() {
		return StringUtils.startsWithIgnoreCase(identifier, Config.MICROSOFT_PREFIX);
	}

	/**
	 * Checks for admin rights.
	 * @return true if user has admin rights
	 */
	@JsonIgnore
	public boolean isAdmin() {
		return StringUtils.equalsIgnoreCase(this.groups, Groups.ADMINS.toString());
	}

	/**
	 * Checks for moderator rights.
	 * @return true if user has mod rights
	 */
	@JsonIgnore
	public boolean isModerator() {
		return isAdmin() ? true : StringUtils.equalsIgnoreCase(this.groups, Groups.MODS.toString());
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
		} else {
			return "generic";
		}
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
			List<User> users = CoreUtils.getInstance().getSearch().findTerms(u.getAppid(), u.getType(),
					new HashMap<String, Object>(){{
						put(Config._EMAIL, u.getEmail());
						put(Config._APPID, u.getAppid());
					}}, true, new Pager(1));
			if (!users.isEmpty()) {
				user = users.get(0);
				password = new UUID().toString();
				user.createIdentifier(u.getIdentifier(), password);
			}
		}
		if (user != null) {
			if (password != null) {
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
		logger.info("User not found for identifier {}/{}, {}.", u.getAppid(), identifier, u.getId());
		return null;
	}

	/**
	 * Checks if a user has entered the correct password.
	 * Compares password hashes.
	 * @param u a user with a set password
	 * @return true if password matches the one in the data store
	 */
	public static final boolean passwordMatches(User u) {
		if (u == null) {
			return false;
		}
		String password = u.getPassword();
		String identifier = u.getIdentifier();
		if (StringUtils.isBlank(password) || StringUtils.isBlank(identifier)) {
			return false;
		}
		ParaObject s = CoreUtils.getInstance().getDao().read(u.getAppid(), identifier);
		if (s != null) {
			if (s instanceof Sysprop) {
				String storedHash = (String) ((Sysprop) s).getProperty(Config._PASSWORD);
				return Utils.bcryptMatches(password, storedHash);
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
		if (StringUtils.isBlank(newpass) || StringUtils.isBlank(token) || newpass.length() < Config.MIN_PASS_LENGTH) {
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
			String token = Utils.base64encURL(new UUID().toString().getBytes());
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
			long timeout = Config.PASSRESET_TIMEOUT_SEC * 1000;
			if (StringUtils.equals(storedToken, token) && (s.getUpdated() + timeout) > Utils.timestamp()) {
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
				String emailHash = Utils.md5(email);
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

	@Override
	public final String getId() {
		return id;
	}

	@Override
	public final void setId(String id) {
		this.id = id;
	}

	@Override
	public final String getType() {
		type = (type == null) ? Utils.type(this.getClass()) : type;
		return type;
	}

	@Override
	public final void setType(String type) {
		this.type = type;
	}

	@Override
	public String getAppid() {
		appid = (appid == null) ? Config.APP_NAME_NS : appid;
		return appid;
	}

	@Override
	public void setAppid(String appid) {
		this.appid = appid;
	}

	@Override
	public String getObjectURI() {
		return CoreUtils.getInstance().getObjectURI(this);
	}

	@Override
	public List<String> getTags() {
		return tags;
	}

	@Override
	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	@Override
	public Boolean getStored() {
		if (stored == null) {
			stored = true;
		}
		return stored;
	}

	@Override
	public void setStored(Boolean stored) {
		this.stored = stored;
	}

	@Override
	public Boolean getIndexed() {
		if (indexed == null) {
			indexed = true;
		}
		return indexed;
	}

	@Override
	public void setIndexed(Boolean indexed) {
		this.indexed = indexed;
	}

	@Override
	public Boolean getCached() {
		if (cached == null) {
			cached = true;
		}
		return cached;
	}

	@Override
	public void setCached(Boolean cached) {
		this.cached = cached;
	}

	@Override
	public Long getTimestamp() {
		return (timestamp != null && timestamp != 0) ? timestamp : null;
	}

	@Override
	public void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}

	@Override
	public String getCreatorid() {
		return creatorid;
	}

	@Override
	public void setCreatorid(String creatorid) {
		this.creatorid = creatorid;
	}

	@Override
	public final String getName() {
		return CoreUtils.getInstance().getName(name, id);
	}

	@Override
	public final void setName(String name) {
		this.name = (name == null || !name.isEmpty()) ? name : this.name;
	}

	@Override
	public String getPlural() {
		return Utils.singularToPlural(getType());
	}

	@Override
	public String getParentid() {
		return parentid;
	}

	@Override
	public void setParentid(String parentid) {
		this.parentid = parentid;
	}

	@Override
	public Long getUpdated() {
		return (updated != null && updated != 0) ? updated : null;
	}

	@Override
	public void setUpdated(Long updated) {
		this.updated = updated;
	}

	@Override
	public void update() {
		CoreUtils.getInstance().getDao().update(getAppid(), this);
	}

	@Override
	public boolean exists() {
		return CoreUtils.getInstance().getDao().read(getAppid(), getId()) != null;
	}

	@Override
	public boolean voteUp(String userid) {
		return CoreUtils.getInstance().vote(this, userid, VoteValue.UP);
	}

	@Override
	public boolean voteDown(String userid) {
		return CoreUtils.getInstance().vote(this, userid, VoteValue.DOWN);
	}

	@Override
	public Integer getVotes() {
		return (votes == null) ? 0 : votes;
	}

	@Override
	public void setVotes(Integer votes) {
		this.votes = votes;
	}

	@Override
	public Long countLinks(String type2) {
		return CoreUtils.getInstance().countLinks(this, type2);
	}

	@Override
	public List<Linker> getLinks(String type2, Pager... pager) {
		return CoreUtils.getInstance().getLinks(this, type2, pager);
	}

	@Override
	public <P extends ParaObject> List<P> getLinkedObjects(String type, Pager... pager) {
		return CoreUtils.getInstance().getLinkedObjects(this, type, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findLinkedObjects(String type, String field, String query, Pager... pager) {
		return CoreUtils.getInstance().findLinkedObjects(this, type, field, query, pager);
	}

	@Override
	public boolean isLinked(String type2, String id2) {
		return CoreUtils.getInstance().isLinked(this, type2, id2);
	}

	@Override
	public boolean isLinked(ParaObject toObj) {
		return CoreUtils.getInstance().isLinked(this, toObj);
	}

	@Override
	public String link(String id2) {
		return CoreUtils.getInstance().link(this, id2);
	}

	@Override
	public void unlink(String type, String id2) {
		CoreUtils.getInstance().unlink(this, type, id2);
	}

	@Override
	public void unlinkAll() {
		CoreUtils.getInstance().unlinkAll(this);
	}

	@Override
	public Long countChildren(String type) {
		return CoreUtils.getInstance().countChildren(this, type);
	}

	@Override
	public <P extends ParaObject> List<P> getChildren(String type, Pager... pager) {
		return CoreUtils.getInstance().getChildren(this, type, pager);
	}

	@Override
	public <P extends ParaObject> List<P> getChildren(String type, String field, String term, Pager... pager) {
		return CoreUtils.getInstance().getChildren(this, type, field, term, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findChildren(String type, String query, Pager... pager) {
		return CoreUtils.getInstance().findChildren(this, type, query, pager);
	}

	@Override
	public void deleteChildren(String type) {
		CoreUtils.getInstance().deleteChildren(this, type);
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 67 * hash + Objects.hashCode(this.id) + Objects.hashCode(this.name);
		return hash;
	}

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

	@Override
	public String toString() {
		return ParaObjectUtils.toJSON(this);
	}
}
