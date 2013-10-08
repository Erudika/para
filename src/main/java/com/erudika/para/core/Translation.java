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

import com.erudika.para.utils.LanguageUtils;
import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.api.Search;
import com.erudika.para.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class Translation extends PObject{
	private static final long serialVersionUID = 1L;
	
	@Stored @Locked private String locale;
	@Stored @Locked private String thekey;
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

	public Translation(String locale, String thekey, String value) {
		this.locale = locale;
		this.thekey = thekey;
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

	public String getThekey() {
		return thekey;
	}

	public void setThekey(String thekey) {
		this.thekey = thekey;
	}

	public void approve(){
		this.approved = true;
		LanguageUtils.onApprove(locale, thekey, value, Utils.getInstanceOf(Search.class));
		update();
	}

	public void disapprove(){
		this.approved = false;
		LanguageUtils.onDisapprove(locale, thekey, Utils.getInstanceOf(Search.class));
		update();
	}
	
	public boolean isApproved(){
		return (approved != null) ? approved : false;
	}

	public String getName() {
		return this.value;
	}

	public String getParentid() {
		if(StringUtils.isBlank(locale) && StringUtils.isBlank(thekey)) return null;
		return locale.concat(Utils.SEPARATOR).concat(thekey);
	}

	
}
