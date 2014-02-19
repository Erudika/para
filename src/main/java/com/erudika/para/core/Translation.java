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

import com.erudika.para.i18n.LanguageUtils;
import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.utils.Config;
import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;

/**
 * A translation is a key/value pair which holds a single translated string.
 * For example: hello = "Hola"
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class Translation extends PObject {
	private static final long serialVersionUID = 1L;

	@Stored @Locked private String locale;
	@Stored @Locked private String thekey;
	@Stored private String value;
	@Stored private Boolean approved;

	private LanguageUtils langutils;

	/**
	 * No-args constructor
	 */
	public Translation() {
		this(null, null, null);
	}

	/**
	 * Default constructor
	 * @param id the id
	 */
	public Translation(String id) {
		this();
	}

	/**
	 * Full constructor.
	 * @param locale a locale
	 * @param thekey the key
	 * @param value the value
	 */
	@Inject
	public Translation(String locale, String thekey, String value) {
		this.locale = locale;
		this.thekey = thekey;
		this.value = value;
		this.approved = false;
		setName(value);
		setName(getName());
	}

	/**
	 * An instance of LanguageUtils
	 * @return instance of {@link com.erudika.para.i18n.LanguageUtils}
	 */
	@JsonIgnore
	public LanguageUtils getLangutils() {
		if (langutils == null) {
			langutils = new LanguageUtils(getSearch(), getDao());
		}
		return langutils;
	}

	/**
	 * Is this an approved translation?
	 * @return true if approved by admin
	 */
	public Boolean getApproved() {
		return approved;
	}

	/**
	 * Sets approved.
	 * @param approved true if approved
	 */
	public void setApproved(Boolean approved) {
		this.approved = approved;
	}

	/**
	 * The translated string.
	 * @return the translation
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Sets the translated string
	 * @param value the translation
	 */
	public void setValue(String value) {
		this.value = value;
	}

	/**
	 * The locale
	 * @return the locale
	 */
	public String getLocale() {
		return locale;
	}

	/**
	 * Sets the locale
	 * @param locale the locale
	 */
	public void setLocale(String locale) {
		this.locale = locale;
	}

	/**
	 * The translation key
	 * @return the key
	 */
	public String getThekey() {
		return thekey;
	}

	/**
	 * Sets the key
	 * @param thekey the key
	 */
	public void setThekey(String thekey) {
		this.thekey = thekey;
	}

	/**
	 * Approves the translation.
	 */
	public void approve() {
		this.approved = true;
		getLangutils().approveTranslation(getAppname(), locale, thekey, value);
		update();
	}

	/**
	 * Disapproves the translation.
	 */
	public void disapprove() {
		this.approved = false;
		getLangutils().disapproveTranslation(getAppname(), locale, thekey);
		update();
	}

	/**
	 * Approved check
	 * @return true if approved
	 */
	public boolean isApproved() {
		return (approved != null) ? approved : false;
	}

	@Override
	public String getParentid() {
		if (StringUtils.isBlank(locale) && StringUtils.isBlank(thekey)) {
			return null;
		}
		return locale.concat(Config.SEPARATOR).concat(thekey);
	}

}
