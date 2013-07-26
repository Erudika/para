/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.erudika.para.i18n;

import com.erudika.para.core.PObject;
import com.erudika.para.utils.DAO;
import com.erudika.para.utils.Locked;
import com.erudika.para.utils.Stored;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/**
 *
 * @author alexb
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class Translation extends PObject{
	private static final long serialVersionUID = 1L;
	
	@Stored @Locked private String locale;
	@Stored @Locked private String key;
	@Stored private String value;
	@Stored private Boolean approved;
	@Stored private Integer votes;
	@Stored private Integer oldvotes;
	
	public Translation() {
		this(null, null, null);
	}

	public Translation(String id) {
		this();
		setId(id);
	}

	public Translation(String locale, String key, String value) {
		this.locale = locale;
		this.key = key;
		this.value = value;
		this.votes = 0;
		this.approved = false;
	}

	public Boolean getApproved() {
		return approved;
	}

	public void setApproved(Boolean approved) {
		this.approved = approved;
	}
	
	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public Integer getVotes() {
		return votes;
	}

	public void setVotes(Integer votes) {
		setOldvotes(this.votes);
		this.votes = votes;
	}

	public Integer getOldvotes() {
		return oldvotes;
	}

	public void setOldvotes(Integer oldvotes) {
		this.oldvotes = oldvotes;
	}

	public void approve(){
		this.approved = true;
		LanguageUtils.onApprove(locale, key, value);
		update();
	}

	public void disapprove(){
		this.approved = false;
		LanguageUtils.onDisapprove(locale, key);
		update();
	}
	
	public boolean isApproved(){
		return (approved != null) ? approved : false;
	}

	public boolean voteUp(String userid) {
		return DAO.getInstance().voteUp(userid, this);
	}

	public boolean voteDown(String userid) {
		return DAO.getInstance().voteDown(userid, this);
	}

	public String getName() {
		return this.value;
	}

}
