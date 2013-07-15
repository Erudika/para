/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erudika.para.core;

import com.erudika.para.utils.DAO;
import com.erudika.para.utils.Locked;
import com.erudika.para.utils.Stored;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.validation.constraints.Size;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 *
 * @author alexb
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class User extends PObject implements UserDetails{
	private static final long serialVersionUID = 1L;

	public static enum Groups{
		USERS, ADMINS;

		public String toString() {
			return this.name().toLowerCase();
		}
	}

	
	@Stored @Locked @NotBlank private String identifier;
	@Stored @Locked @NotBlank private String groups;
	@Stored @Locked private Boolean active;
	@Stored @NotBlank @Email private String email;
	@NotBlank @Size(min=DAO.MIN_PASS_LENGTH, max=255) private transient String password;	// for validation purposes only
	
	private transient Map<String, String> authmap;
	private transient Long authstamp;	
	private transient String authtoken;
	
	public User() {
	}

	public User(String id) {
		setId(id);
	}
	
	public PObject getParent(){
		return this;
	}
	
	public Long getAuthstamp(){
		if(authstamp == null)
			authstamp = NumberUtils.toLong(getAuthMap().get(DAO.CN_AUTHSTAMP), 0L);
		return authstamp;
	}
	
	public String getAuthtoken(){
		if(authtoken == null)
			authtoken = getAuthMap().get(DAO.CN_AUTHTOKEN);
		return authtoken; 
	}
	
	private Map<String, String> getAuthMap(){
		if(authmap == null)
			authmap = DAO.getInstance().loadAuthMap(this.identifier);
		return authmap;
	}
	
	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public String getGroups() {
		return groups;
	}

	public void setGroups(String groups) {
		this.groups = groups;
	}
	
	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}
	
	public String create() {
		return DAO.getInstance().createUser(this);
	}

	public void update() {
		DAO.getInstance().updateUser(this);
	}

	public void delete() {
		DAO.getInstance().deleteUser(this);
	}
	
	public String getCreatorid(){
		return getId();
	}

	public boolean isAdmin() {
		return StringUtils.equalsIgnoreCase(this.groups, "admins");
	}
	
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if(isAdmin()){
			return Collections.singleton(new SimpleGrantedAuthority("ROLE_ADMIN"));
		}else{
			return Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"));
		}
	}

	public String getUsername() {
		return getIdentifier();
	}

	public boolean isAccountNonExpired() {
		return true;
	}

	public boolean isAccountNonLocked() {
		return active;
	}

	public boolean isCredentialsNonExpired() {
		return active;
	}

	public boolean isEnabled() {
		return active;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
