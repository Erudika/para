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
package com.erudika.para.core;

import com.erudika.para.Para;
import com.erudika.para.annotations.Email;
import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.i18n.CurrencyUtils;
import com.erudika.para.persistence.DAO;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.Size;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The core user object. Stores information about users.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class User implements ParaObject, UserDetails {
	private static final long serialVersionUID = 1L;

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
	@Stored @Locked private String plural;
	@Stored @Locked private String objectURI;

	@Stored @NotBlank private String identifier;
	@Stored @Locked @NotBlank private String groups;
	@Stored @Locked private Boolean active;
	@Stored @NotBlank @Email private String email;
	@Stored private String currency;
	@Stored private Boolean pro;
	@Stored private Integer plan;
	@Stored private String picture;

	@NotBlank @Size(min = Config.MIN_PASS_LENGTH, max = 255)
	private transient String password;	// for validation purposes only
	private transient String shardKey;
	private transient DAO dao;
	private transient Search search;

	/**
	 * No-args constructor
	 */
	public User() {
		this(null);
	}

	/**
	 * Default constructor
	 * @param id the id
	 */
	public User(String id) {
		setId(id);
		setName(getName());
	}

	@Override
	public ParaObject getParent() {
		return this;
	}

	/**
	 * The profile picture URL.
	 * @return a URL or null
	 */
	public String getPicture() {
		return picture;
	}

	/**
	 * Sets the profile picture URL.
	 * @param picture the picture URL.
	 */
	public void setPicture(String picture) {
		this.picture = picture;
	}

	/**
	 * Paid plan. Reserved for future use.
	 * @return a paid plan
	 */
	public Integer getPlan() {
		return plan;
	}

	/**
	 * Sets a paid plan. Reserved for future use.
	 * @param plan a paid plan
	 */
	public void setPlan(Integer plan) {
		this.plan = plan;
	}

	/**
	 * Premium user flag.
	 * @return true if user is pro
	 */
	public Boolean getPro() {
		return pro != null && pro;
	}

	/**
	 * Sets the premium user flag.
	 * @param pro pro plan flag
	 */
	public void setPro(Boolean pro) {
		this.pro = pro;
	}

	/**
	 * Is this account active?
	 * @return true if active
	 */
	public Boolean getActive() {
		if (active == null) {
			active = false;
		}
		return active;
	}

	/**
	 * Sets the account active
	 * @param active true if active
	 */
	public void setActive(Boolean active) {
		this.active = active;
	}

	/**
	 * Returns the security groups for this user
	 * @return the groups string
	 */
	public String getGroups() {
		return groups;
	}

	/**
	 * Sets the security groups for this user
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
	 * The user's email
	 * @return email
	 */
	public String getEmail() {
		return email;
	}

	/**
	 * Sets the email
	 * @param email email
	 */
	public void setEmail(String email) {
		this.email = email;
	}

	/**
	 * The user's currency preference
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
		} else {
			setGroups(User.Groups.USERS.toString());
		}

		setActive(true);

		if (getDao().create(getAppid(), this) != null) {
			createIdentifier(getId(), getIdentifier(), getPassword());
		}

		return getId();
	}

	@Override
	public void delete() {
		if (getId() != null) {
			getDao().delete(getAppid(), this);
			for (String ident1 : getIdentifiers()) {
				deleteIdentifier(ident1);
			}
		}
	}

	/**
	 * Returns a list of identifiers for this user (can have many).
	 * @return a list of identifiers
	 */
	@JsonIgnore
	public List<String> getIdentifiers() {
		List<Sysprop> list = getSearch().findTerms(getAppid(), Utils.type(Sysprop.class),
				Collections.singletonMap(Config._CREATORID, getId()), true);
		ArrayList<String> idents = new ArrayList<String>();
		for (Sysprop s : list) {
			idents.add(s.getId());
		}
		return idents;
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
	 * Detaches an identifier from this user.
	 * @param identifier an attached identifier
	 */
	public void detachIdentifier(String identifier) {
		if (!StringUtils.equals(identifier, getIdentifier())) {
			Sysprop s = getDao().read(getAppid(), identifier);
			if (s != null && StringUtils.equals(getId(), s.getCreatorid())) {
				deleteIdentifier(identifier);
			}
		}
	}

	/**
	 * Is the main identifier a Facebook id
	 * @return true if user is signed in with Facebook
	 */
	public boolean isFacebookUser() {
		return StringUtils.startsWithIgnoreCase(identifier, Config.FB_PREFIX);
	}

	/**
	 * Is the main identifier a Google+ id
	 * @return true if user is signed in with Google+
	 */
	public boolean isGooglePlusUser() {
		return StringUtils.startsWithIgnoreCase(identifier, Config.GPLUS_PREFIX);
	}

	/**
	 * Is the main identifier a LinkedIn id
	 * @return true if user is signed in with LinkedIn
	 */
	public boolean isLinkedInUser() {
		return StringUtils.startsWithIgnoreCase(identifier, Config.LINKEDIN_PREFIX);
	}

	/**
	 * Is the main identifier a Twitter id
	 * @return true if user is signed in with Twitter
	 */
	public boolean isTwitterUser() {
		return StringUtils.startsWithIgnoreCase(identifier, Config.TWITTER_PREFIX);
	}

	/**
	 * Checks for admin rights
	 * @return true if user has admin rights
	 */
	public boolean isAdmin() {
		return StringUtils.equalsIgnoreCase(this.groups, Groups.ADMINS.toString());
	}

	/**
	 * Checks for moderator rights
	 * @return true if user has mod rights
	 */
	public boolean isModerator() {
		return isAdmin() ? true : StringUtils.equalsIgnoreCase(this.groups, Groups.MODS.toString());
	}

	/**
	 * A list of roles for this user
	 * @return a list of roles
	 */
	@JsonIgnore
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if (isAdmin()) {
			return Collections.singleton(new SimpleGrantedAuthority(Roles.ADMIN.toString()));
		} else if (isModerator()) {
			return Collections.singleton(new SimpleGrantedAuthority(Roles.MOD.toString()));
		} else {
			return Collections.singleton(new SimpleGrantedAuthority(Roles.USER.toString()));
		}
	}

	/**
	 * The username. Same as {@link #getIdentifier()}
	 * @return {@link #getIdentifier()}
	 * @see #getIdentifier()
	 */
	@JsonIgnore
	public String getUsername() {
		return getIdentifier();
	}

	/**
	 * Same as {@link #getActive() }
	 * @return true if active
	 * @see #getActive()
	 */
	@JsonIgnore
	public boolean isAccountNonExpired() {
		return getActive();
	}

	/**
	 * Same as {@link #getActive() }
	 * @return true if active
	 * @see #getActive()
	 */
	@JsonIgnore
	public boolean isAccountNonLocked() {
		return getActive();
	}

	/**
	 * Same as {@link #getActive() }
	 * @return true if active
	 * @see #getActive()
	 */
	@JsonIgnore
	public boolean isCredentialsNonExpired() {
		return getActive();
	}

	/**
	 * Same as {@link #getActive() }
	 * @return true if active
	 * @see #getActive()
	 */
	@JsonIgnore
	public boolean isEnabled() {
		return getActive();
	}

	/**
	 * The password. A transient field used for validation.
	 * @return the password.
	 */
	@JsonIgnore
	public String getPassword() {
		if (StringUtils.isBlank(password)) {
			password = Utils.generateSecurityToken();
		}
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
	public static User readUserForIdentifier(User u) {
		if (u == null || StringUtils.isBlank(u.getIdentifier())) {
			return null;
		}
		String identifier = u.getIdentifier();
		Sysprop s = u.getDao().read(u.getAppid(), identifier);
		if (s != null && s.getCreatorid() != null) {
			User user = u.getDao().read(u.getAppid(), s.getCreatorid());
			if (user != null) {
				if (!identifier.equals(user.getIdentifier())) {
					user.setIdentifier(identifier);
					u.getDao().update(user);
				}
				return user;
			}
		}
		return null;
	}

	/**
	 * Checks if a user has entered the correct password.
	 * Compares password hashes.
	 * @param u a user with a set password
	 * @return true if password matches the one in the data store
	 */
	public static boolean passwordMatches(User u) {
		if (u == null) {
			return false;
		}
		String password = u.getPassword();
		String identifier = u.getIdentifier();
		if (StringUtils.isBlank(password) || StringUtils.isBlank(identifier)) {
			return false;
		}
		Sysprop s = u.getDao().read(u.getAppid(), identifier);
		if (s != null) {
			String storedHash = (String) s.getProperty(Config._PASSWORD);
			return Utils.bcryptMatches(password, storedHash);
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
		Sysprop s = getDao().read(getAppid(), identifier);
		if (s != null) {
			String token = Utils.MD5(Utils.getNewId() + Config.SEPARATOR + Utils.generateSecurityToken());
			s.addProperty(Config._RESET_TOKEN, token);
			getDao().update(getAppid(), s);
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
		if (StringUtils.isBlank(newpass) || StringUtils.isBlank(token)) {
			return false;
		}
		if (newpass.length() < Config.MIN_PASS_LENGTH) {
			return false;
		}
		Sysprop s = getDao().read(getAppid(), identifier);
		if (s != null && s.hasProperty(Config._RESET_TOKEN)) {
			String storedToken = (String) s.getProperty(Config._RESET_TOKEN);
			long now = Utils.timestamp();
			long timeout = Config.PASSRESET_TIMEOUT_SEC * 1000;
			if (StringUtils.equals(storedToken, token) && (s.getUpdated() + timeout) > now) {
				s.removeProperty(Config._RESET_TOKEN);
				s.addProperty(Config._PASSWORD, Utils.bcrypt(newpass));
				getDao().update(getAppid(), s);
				return true;
			}
		}
		return false;
	}

	private boolean createIdentifier(String userid, String newIdent) {
		return createIdentifier(userid, newIdent, null);
	}

	/**
	 * Creates a new identifier object using {@link Sysprop}.
	 * Used for identifying a user when signing in.
	 * @param userid a user id
	 * @param newIdent a new identifier
	 * @param password a password for the user (optional)
	 * @return true if successful
	 */
	private boolean createIdentifier(String userid, String newIdent, String password) {
		if (StringUtils.isBlank(userid) || StringUtils.isBlank(newIdent)) {
			return false;
		}
		Sysprop s = new Sysprop();
		s.setId(newIdent);
		s.setName(Config._IDENTIFIER);
		s.setCreatorid(userid);
		if (!StringUtils.isBlank(password)) {
			s.addProperty(Config._PASSWORD, Utils.bcrypt(password));
		}
		return getDao().create(getAppid(), s) != null;
	}

	/**
	 * Deletes the identifier and the user can no longer sign in with it.
	 * @param ident the attached identifier
	 */
	private void deleteIdentifier(String ident) {
		if (!StringUtils.isBlank(ident)) {
			getDao().delete(getAppid(), new Sysprop(ident));
		}
	}

	/**
	 * Simple groups enum
	 */
	public static enum Groups {
		/**
		 * The standard user group
		 */
		USERS,
		/**
		 * Moderators group
		 */
		MODS,
		/**
		 * Administrators group
		 */
		ADMINS;

		public String toString() {
			return this.name().toLowerCase();
		}
	}

	/**
	 * Simple user roles enum
	 */
	public static enum Roles {
		/**
		 * The standard role
		 */
		USER,
		/**
		 * The moderator role
		 */
		MOD,
		/**
		 * The administrator role
		 */
		ADMIN;

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
		return CoreUtils.getObjectURI(this);
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
		return CoreUtils.getName(name, id);
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
	public ParaObject getCreator() {
		return getDao().read(getAppid(), creatorid);
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
		getDao().update(getAppid(), this);
	}

	@Override
	public boolean exists() {
		return getDao().read(id) != null;
	}

	@Override
	public DAO getDao() {
		if (dao == null) {
			dao = Para.getDAO();
		}
		return dao;
	}

	@Override
	public void setDao(DAO dao) {
		this.dao = dao;
	}

	@Override
	public Search getSearch() {
		if (search == null) {
			search = Para.getSearch();
		}
		return search;
	}

	@Override
	public void setSearch(Search search) {
		this.search = search;
	}

	@Override
	public String getShardKey() {
		return StringUtils.isBlank(shardKey) ? getId() : shardKey;
	}

	@Override
	public void setShardKey(String shardKey) {
		this.shardKey = shardKey;
	}

	@Override
	public boolean voteUp(String userid) {
		return CoreUtils.vote(this, userid, VoteValue.UP);
	}

	@Override
	public boolean voteDown(String userid) {
		return CoreUtils.vote(this, userid, VoteValue.DOWN);
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
		return CoreUtils.countLinks(this, type2);
	}

	@Override
	public List<Linker> getLinks(String type2, Pager... pager) {
		return CoreUtils.getLinks(this, type2, pager);
	}

	@Override
	public <P extends ParaObject> List<P> getLinkedObjects(String type, Pager... pager) {
		return CoreUtils.getLinkedObjects(this, type, pager);
	}

	@Override
	public boolean isLinked(String type2, String id2) {
		return CoreUtils.isLinked(this, type2, id2);
	}

	@Override
	public boolean isLinked(ParaObject toObj) {
		return CoreUtils.isLinked(this, toObj);
	}

	@Override
	public String link(String id2) {
		return CoreUtils.link(this, id2);
	}

	@Override
	public void unlink(String type, String id2) {
		CoreUtils.unlink(this, type, id2);
	}

	@Override
	public void unlinkAll() {
		CoreUtils.unlinkAll(this);
	}

	@Override
	public Long countChildren(String type) {
		return CoreUtils.countChildren(this, type);
	}

	@Override
	public <P extends ParaObject> List<P> getChildren(String type, Pager... pager) {
		return CoreUtils.getChildren(this, type, pager);
	}

	@Override
	public <P extends ParaObject> List<P> getChildren(String type, String field, String term, Pager... pager) {
		return CoreUtils.getChildren(this, type, field, term, pager);
	}

	@Override
	public void deleteChildren(String type) {
		CoreUtils.deleteChildren(this, type);
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
		return Utils.toJSON(this);
	}
}
