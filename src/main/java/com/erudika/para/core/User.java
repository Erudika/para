/*
 * Copyright 2013 Alex Bogdanovski <alex@erudika.com>.
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

import com.erudika.para.annotations.Email;
import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.i18n.CurrencyUtils;
import com.erudika.para.persistence.DAO;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.Size;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class User extends PObject implements UserDetails{
	private static final long serialVersionUID = 1L;
	
	public static enum Groups{
		USERS, MODS, ADMINS;

		public String toString() {
			return this.name().toLowerCase();
		}
	}
	
	public static enum Roles{
		USER, MOD, ADMIN;

		public String toString() {
			return "ROLE_".concat(this.name());
		}
	}
	
	@Stored @NotBlank private String identifier;
	@Stored @Locked @NotBlank private String groups;
	@Stored @Locked private Boolean active;
	@Stored @NotBlank @Email private String email;
	@Stored private String authstamp;
	@Stored private String currency;
	@Stored private Boolean pro;
	@Stored private Integer plan;
	
	@NotBlank @Size(min=Config.MIN_PASS_LENGTH, max=255)	
	private transient String password;	// for validation purposes only? 
	
	private transient String authtoken;
	
	public User() {
		this(null);
	}

	public User(String id) {
		setId(id);
		getName();
	}
	
	public PObject getParent(){
		return this;
	}

	public Integer getPlan() {
		return plan;
	}

	public void setPlan(Integer plan) {
		this.plan = plan;
	}
	
	public Boolean getPro() {
		return pro != null && pro;
	}

	public void setPro(Boolean pro) {
		this.pro = pro;
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

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		currency = StringUtils.upperCase(currency);
		if(!CurrencyUtils.getInstance().isValidCurrency(currency)) currency = "EUR";
		this.currency = currency;
	}
	
	public String create() {
		if(StringUtils.isBlank(getIdentifier())) return null;
		if (!StringUtils.isBlank(getPassword()) && getPassword().length() < Config.MIN_PASS_LENGTH) return null;
		
		// admin detected
		if (!Config.ADMIN_IDENT.isEmpty() && Config.ADMIN_IDENT.equals(getIdentifier()) ){
			setGroups(User.Groups.ADMINS.toString());
		}else{
			setGroups(User.Groups.USERS.toString());
		}
		
		setActive(true);	
		
		if(getDao().create(getAppname(), this) != null){
			createIdentifier(getId(), getIdentifier(), getPassword());
		}
		
		return getId();
	}

	public void delete() {
		if(getId() != null){
			getDao().delete(getAppname(), this);
			for (String ident1 : getIdentifiers()) {
				deleteIdentifier(ident1);
			}
		}
	}
	
	@JsonIgnore
	public List<String> getIdentifiers(){
		List<Sysprop> list = getSearch().findTerm(getAppname(), PObject.classname(Sysprop.class), 
				null, null, DAO.CN_CREATORID, getId());
		ArrayList<String> idents = new ArrayList<String>();
		for (Sysprop s : list) {
			idents.add(s.getId());
		}
		return idents;
	}
	
	public void attachIdentifier(String identifier){
		if(!this.exists()) return;
		createIdentifier(getId(), identifier);
	}

	public void detachIdentifier(String identifier){
		if(StringUtils.equals(identifier, getIdentifier())) return;
		Sysprop s = getDao().read(getAppname(), identifier);
		if(s != null && StringUtils.equals(getId(), s.getCreatorid())){
			deleteIdentifier(identifier);
		}
	}
	
	public boolean isFacebookUser(){
		return StringUtils.startsWithIgnoreCase(identifier, Config.FB_PREFIX);
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
	
	@JsonIgnore
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if(isAdmin()){
			return Collections.singleton(new SimpleGrantedAuthority(Roles.ADMIN.toString()));
		}else if(isModerator()){
			return Collections.singleton(new SimpleGrantedAuthority(Roles.MOD.toString()));
		}else{
			return Collections.singleton(new SimpleGrantedAuthority(Roles.USER.toString()));
		}
	}

	@JsonIgnore
	public String getUsername() {
		return getIdentifier();
	}

	@JsonIgnore
	public boolean isAccountNonExpired() {
		return true;
	}

	@JsonIgnore
	public boolean isAccountNonLocked() {
		return active != null && active == true;
	}

	@JsonIgnore
	public boolean isCredentialsNonExpired() {
		return active != null && active == true;
	}

	@JsonIgnore
	public boolean isEnabled() {
		return active != null && active == true;
	}

	@JsonIgnore
	public String getPassword() {
		return StringUtils.isBlank(password) ? authtoken : password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	
	public static User readUserForIdentifier(User u){
		if(u == null || StringUtils.isBlank(u.getIdentifier())) return null;
		String identifier = u.getIdentifier();
		if(NumberUtils.isDigits(identifier)) identifier = Config.FB_PREFIX + u.getIdentifier();
		Sysprop s = u.getDao().read(u.getAppname(), identifier);
		if(s != null && s.getCreatorid() != null){
			User user = u.getDao().read(u.getAppname(), s.getCreatorid());
			if(user != null ){
				if(!identifier.equals(user.getIdentifier())){
					user.setIdentifier(identifier);
					user.update();
				}
				user.setAuthtoken((String) s.getProperty(DAO.CN_AUTHTOKEN));
				return user;
			}
		}
		return null;
    }
	
	public static boolean passwordMatches(User u){
		if(u == null) return false;
		String password = u.getPassword();
		String identifier = u.getIdentifier();
		if(StringUtils.isBlank(password) || StringUtils.isBlank(identifier)) return false;
		Sysprop s = u.getDao().read(u.getAppname(), identifier);
		if(s != null){
			String salt = (String) s.getProperty(DAO.CN_SALT);
			String storedHash = (String) s.getProperty(DAO.CN_PASSWORD);
			String givenHash = Utils.HMACSHA(password, salt);
			return StringUtils.equals(givenHash, storedHash);
		}
		return false;
	}
	
	public final String generatePasswordResetToken(){
		if(StringUtils.isBlank(identifier)) return "";
		Sysprop s = getDao().read(getAppname(), identifier);
		if(s != null){
			String salt = (String) s.getProperty(DAO.CN_SALT);
			String token = Utils.HMACSHA(Utils.getNewId(), salt);
			s.addProperty(DAO.CN_RESET_TOKEN, token);
			getDao().update(getAppname(), s);
			return token;
		}
		return "";
	}
	
	public final boolean resetPassword(String token, String newpass){
		if(StringUtils.isBlank(newpass) || StringUtils.isBlank(token)) return false;
		if(newpass.length() < Config.MIN_PASS_LENGTH) return false;
		Sysprop s = getDao().read(getAppname(), identifier);
		if(s != null && s.hasProperty(DAO.CN_RESET_TOKEN)){
			String storedToken = (String) s.getProperty(DAO.CN_RESET_TOKEN);
			long now = System.currentTimeMillis();
			long timeout = Config.PASSRESET_TIMEOUT_SEC * 1000;
			if(StringUtils.equals(storedToken, token) && (s.getTimestamp() + timeout) > now){
				s.removeProperty(DAO.CN_RESET_TOKEN);
				String salt = getPassSalt();
				s.addProperty(DAO.CN_SALT, salt);
				s.addProperty(DAO.CN_PASSWORD, getPassHash(newpass, salt));
				getDao().update(getAppname(), s);
				return true;
			}
		}
		return false;
	}
		
//	private void changeIdentifier(String oldIdent, String newIdent){
//		if(StringUtils.isBlank(oldIdent) || StringUtils.isBlank(newIdent) || oldIdent.equalsIgnoreCase(newIdent)) return;
//		Sysprop s = getDao().read(oldIdent);
//		if(s != null){
//			getDao().delete(s);
//			s.setId(newIdent);
//			getDao().create(s);
//		}
//	}
	
	private boolean createIdentifier(String userid, String newIdent){
		return createIdentifier(userid, newIdent, null);
	}
	
	private boolean createIdentifier(String userid, String newIdent, String password){
		if(StringUtils.isBlank(userid) || StringUtils.isBlank(newIdent)) return false;
		if(NumberUtils.isDigits(newIdent)) newIdent = Config.FB_PREFIX + newIdent;
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
		return getDao().create(getAppname(), s) != null;
	}
	
	private void deleteIdentifier(String ident){
		if(StringUtils.isBlank(ident)) return;
		if(NumberUtils.isDigits(ident)) ident = Config.FB_PREFIX + ident;		
		getDao().delete(getAppname(), new Sysprop(ident));
	}
	
	private String getPassSalt(){
		return RandomStringUtils.randomAlphanumeric(20);
	}
	
	private String getPassHash(String pass, String salt){
		return Utils.HMACSHA(pass, salt);
	}
}
