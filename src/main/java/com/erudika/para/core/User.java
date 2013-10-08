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
package com.erudika.para.core;

import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.api.DAO;
import com.erudika.para.api.Search;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.Size;
import org.apache.commons.lang3.RandomStringUtils;
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
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class User extends PObject implements UserDetails{
	private static final long serialVersionUID = 1L;
	
	public static int TOKEN_EXPIRES_AFTER = 20 * 60 * 1000; //20 minutes in milliseconds
	
	public static enum Groups{
		USERS, MODS, ADMINS;

		public String toString() {
			return this.name().toLowerCase();
		}
	}
	
	@Stored @NotBlank private String identifier;
	@Stored @Locked @NotBlank private String groups;
	@Stored @Locked private Boolean active;
	@Stored @NotBlank @Email private String email;
	@Stored private String authstamp;
	@NotBlank @Size(min=Utils.MIN_PASS_LENGTH, max=255) private transient String password;	// for validation purposes only
	
	private transient String currentIdentifier;
	private transient String authtoken;
	
	public User() {
	}

	public User(String id) {
		setId(id);
	}
	
	public PObject getParent(){
		return this;
	}
	
	public String getAuthstamp(){
		return authstamp;
	}
	
	public void setAuthstamp(String authstamp){
		this.authstamp = authstamp;
	}

	public String getAuthtoken(){
		return authtoken; 
	}

	public void setAuthtoken(String authtoken) {
		this.authtoken = authtoken;
	}
	
	public String getCurrentIdentifier() {
		return currentIdentifier;
	}

	public void setCurrentIdentifier(String currentIdentifier) {
		this.currentIdentifier = currentIdentifier;
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
		if(!StringUtils.isBlank(this.email) && !StringUtils.isBlank(email) && !this.email.equals(email)){
			changeIdentifier(this.email, email);
		}
		this.email = email;
	}
	
	public String create() {
		if(StringUtils.isBlank(getIdentifier())) return null;
		if (!StringUtils.isBlank(getPassword()) && getPassword().length() < Utils.MIN_PASS_LENGTH) return null;
		
		// admin detected
		if (!Utils.ADMIN_IDENT.isEmpty() && Utils.ADMIN_IDENT.equals(getIdentifier()) ){
			setGroups(User.Groups.ADMINS.toString());
		}else{
			setGroups(User.Groups.USERS.toString());
		}
		
		setActive(true);	
		
		if(super.create() != null){
			createIdentifier(getId(), getIdentifier(), getPassword());
		}
		
		return getId();
	}

	public void update() {
		super.update();
	}

	public void delete() {
		if(getId() != null){
			super.delete();
			for (String ident1 : getIdentifiers()) {
				deleteIdentifier(ident1);
			}
		}
	}
	
	public List<String> getIdentifiers(){
		List<Sysprop> list = Utils.getInstanceOf(Search.class).findTerm(PObject.classname(Sysprop.class), 
				null, null, DAO.CN_CREATORID, getId());
		ArrayList<String> idents = new ArrayList<String>();
		for (Sysprop s : list) {
			idents.add(s.getId());
		}
		return idents;
	}
	
	public void attachIdentifier(String identifier){
		createIdentifier(getId(), identifier);
	}

	public void detachIdentifier(String identifier){
		deleteIdentifier(identifier);
	}
	
	public boolean isFacebookUser(){
		return StringUtils.startsWithIgnoreCase(identifier, Utils.FB_PREFIX);
	}
	
	public String getCreatorid(){
		return getId();
	}

	public boolean isAdmin() {
		return StringUtils.equalsIgnoreCase(this.groups, Groups.ADMINS.toString());
	}
	
	public boolean isModerator(){
		if(isAdmin()) return true;
		return StringUtils.equalsIgnoreCase(this.groups, Groups.MODS.toString());
	}
	
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if(isAdmin()){
			return Collections.singleton(new SimpleGrantedAuthority("ROLE_ADMIN"));
		}else if(isModerator()){
			return Collections.singleton(new SimpleGrantedAuthority("ROLE_MOD"));
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
		return active != null && active == true;
	}

	public boolean isCredentialsNonExpired() {
		return active != null && active == true;
	}

	public boolean isEnabled() {
		return active != null && active == true;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	
	public static User readUserForIdentifier (String identifier, DAO dao){
		if(StringUtils.isBlank(identifier)) return null;
		if(NumberUtils.isDigits(identifier)) identifier = Utils.FB_PREFIX + identifier;
		Sysprop s = dao.read(identifier);
		if(s == null || s.getCreatorid() == null) return null;
		User user = dao.read(s.getCreatorid());
		if(user != null ){
			user.setCurrentIdentifier(identifier);
			user.setAuthtoken((String) s.getProperty(DAO.CN_AUTHTOKEN));
		}
		return user;
    }
	
	public static boolean passwordMatches(String password, String identifier, DAO dao){
		if(StringUtils.isBlank(password)) return false;
		Sysprop s = dao.read(identifier);
		if(s == null) return false;
		String salt = (String) s.getProperty(DAO.CN_SALT);
		String storedHash = (String) s.getProperty(DAO.CN_PASSWORD);
		String givenHash = Utils.HMACSHA(password, salt);
		return StringUtils.equals(givenHash, storedHash);
	}
	
	public final String generatePasswordResetToken(){
		if(StringUtils.isBlank(identifier)) return "";
		Sysprop s = Utils.getInstanceOf(DAO.class).read(identifier);
		if(s == null) return "";
		String salt = (String) s.getProperty(DAO.CN_SALT);
		String token = Utils.HMACSHA(Long.toString(System.currentTimeMillis()), salt);
		s.addProperty(DAO.CN_RESET_TOKEN, token);
		s.update();
		return token;
	}
	
	public final boolean resetPassword(String identifier, String token, String newpass){
		if(StringUtils.isBlank(newpass) || StringUtils.isBlank(token)) return false;
		Sysprop s = Utils.getInstanceOf(DAO.class).read(identifier);
		if(s != null && s.hasProperty(DAO.CN_RESET_TOKEN)){
			String storedToken = (String) s.getProperty(DAO.CN_RESET_TOKEN);
			long now = System.currentTimeMillis();
			if(StringUtils.equals(storedToken, token) && (s.getTimestamp() + TOKEN_EXPIRES_AFTER) > now){
				s.removeProperty(DAO.CN_RESET_TOKEN);
				String salt = getPassSalt();
				s.addProperty(DAO.CN_SALT, salt);
				s.addProperty(DAO.CN_PASSWORD, getPassHash(newpass, salt));
				s.update();
				return true;
			}
		}
		return false;
	}
		
	private void changeIdentifier(String oldIdent, String newIdent){
		if(StringUtils.isBlank(oldIdent) || StringUtils.isBlank(newIdent) || oldIdent.equalsIgnoreCase(newIdent)) return;
		Sysprop s = Utils.getInstanceOf(DAO.class).read(oldIdent);
		if(s != null){
			s.delete();
			s.setId(newIdent);
			s.create();
		}
	}
	
	private boolean createIdentifier(String userid, String newIdent){
		return createIdentifier(userid, newIdent, null);
	}
	
	private boolean createIdentifier(String userid, String newIdent, String password){
		if(StringUtils.isBlank(userid) || StringUtils.isBlank(newIdent)) return false;
		if(NumberUtils.isDigits(newIdent)) newIdent = Utils.FB_PREFIX + newIdent;
		Sysprop s = new Sysprop();
		s.setId(newIdent);
		s.setName(DAO.CN_IDENTIFIER);
		s.setCreatorid(userid);
		s.addProperty(DAO.CN_AUTHTOKEN, Utils.generateAuthToken());
		if(!StringUtils.isBlank(password)){
			String salt = getPassSalt();
			s.addProperty(DAO.CN_SALT, salt);
			s.addProperty(DAO.CN_PASSWORD, getPassHash(password, salt));
		}
		return s.create() != null;
	}
	
	private void deleteIdentifier(String ident){
		if(StringUtils.isBlank(ident)) return;
		if(NumberUtils.isDigits(ident)) ident = Utils.FB_PREFIX + ident;
		new Sysprop(ident).delete();
	}
	
	private String getPassSalt(){
		return RandomStringUtils.randomAlphanumeric(20);
	}
	
	private String getPassHash(String pass, String salt){
		return Utils.HMACSHA(pass, salt);
	}
}
