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

import com.erudika.para.core.App;
import com.erudika.para.core.User;
import com.erudika.para.core.User.Roles;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.Collections;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Stores information about authenticated users.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AuthenticatedUserDetails implements UserDetails {
	private static final long serialVersionUID = 1L;

	private User user;

	/**
	 * Default constructor.
	 * @param user an authenticated user object (principal)
	 */
	public AuthenticatedUserDetails(User user) {
		assert user != null;
		this.user = user;
	}

	/**
	 * Returns the underlying principal.
	 * @return a {@link User} object
	 */
	public User getUser() {
		return user;
	}

	/**
	 * Sets the principal object.
	 * @param user a user
	 */
	public void setUser(User user) {
		this.user = user;
	}

	/**
	 * A list of roles for this user.
	 * @return a list of roles
	 */
	@JsonIgnore
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if (user.isAdmin()) {
			return Collections.singleton(new SimpleGrantedAuthority(Roles.ADMIN.toString()));
		} else if (user.isModerator()) {
			return Collections.singleton(new SimpleGrantedAuthority(Roles.MOD.toString()));
		} else {
			return Collections.singleton(new SimpleGrantedAuthority(Roles.USER.toString()));
		}
	}

	/**
	 * The username. Same as {@link User#getIdentifier()}
	 * @return {@link User#getIdentifier()} if user.appid == root appid, or "appid/user.identifier" if child app,
	 * @see User#getIdentifier()
	 */
	@JsonIgnore
	public String getUsername() {
		if (App.isRoot(user.getAppid())) {
			return user.getIdentifier();
		} else {
			return user.getAppid() + "/" + user.getIdentifier();
		}
	}

	/**
	 * Same as {@link User#getActive() }.
	 * @return true if active
	 * @see User#getActive()
	 */
	@JsonIgnore
	public boolean isAccountNonExpired() {
		return user.getActive();
	}

	/**
	 * Same as {@link User#getActive() }.
	 * @return true if active
	 * @see User#getActive()
	 */
	@JsonIgnore
	public boolean isAccountNonLocked() {
		return user.getActive();
	}

	/**
	 * Same as {@link User#getActive() }.
	 * @return true if active
	 * @see User#getActive()
	 */
	@JsonIgnore
	public boolean isCredentialsNonExpired() {
		return user.getActive();
	}

	/**
	 * Same as {@link User#getActive() }.
	 * @return true if active
	 * @see User#getActive()
	 */
	@JsonIgnore
	public boolean isEnabled() {
		return user.getActive();
	}

	/**
	 * The password. A transient field used for validation.
	 * @return the password.
	 */
	@JsonIgnore
	public String getPassword() {
		return user.getPassword();
	}

	/**
	 * Returns the main identifier for this user.
	 * An identifier is basically a unique username that identifies a user.
	 * @return the main identifier
	 */
	public String getIdentifier() {
		return user.getIdentifier();
	}

}
