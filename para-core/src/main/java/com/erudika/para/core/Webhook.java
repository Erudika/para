/*
 * Copyright 2013-2020 Erudika. http://erudika.com
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

import com.erudika.para.annotations.Stored;
import com.erudika.para.utils.Utils;
import javax.validation.constraints.NotBlank;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.URL;

/**
 * Represents a webhook registration.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class Webhook extends Sysprop {
	private static final long serialVersionUID = 1L;

	@Stored @NotBlank @URL private String targetUrl;
	@Stored @NotBlank private String secret;
	@Stored private String typeFilter;
	@Stored private Boolean urlEncoded;
	@Stored private Boolean active;
	@Stored private Boolean tooManyFailures;

	@Stored private Boolean create;
	@Stored private Boolean update;
	@Stored private Boolean delete;
	@Stored private Boolean createAll;
	@Stored private Boolean updateAll;
	@Stored private Boolean deleteAll;

	/**
	 * No-args constructor.
	 */
	public Webhook() {
		this(null);
	}

	/**
	 * Default constructor.
	 * @param targetUrl the URL where the payload will be sent
	 */
	public Webhook(String targetUrl) {
		this.targetUrl = targetUrl;
		this.urlEncoded = true;
		this.create = false;
		this.update = false;
		this.delete = false;
		this.createAll = false;
		this.updateAll = false;
		this.deleteAll = false;
		this.active = false;
		this.tooManyFailures = false;
	}

	/**
	 * @return the target URL
	 */
	public String getTargetUrl() {
		return targetUrl;
	}

	/**
	 * @param targetUrl target URL value
	 */
	public void setTargetUrl(String targetUrl) {
		this.targetUrl = targetUrl;
	}

	/**
	 * @return the webhook secret key
	 */
	public String getSecret() {
		return secret;
	}

	/**
	 * @param secret webhook secret key
	 */
	public void setSecret(String secret) {
		this.secret = secret;
	}

	/**
	 * @return type filter
	 */
	public String getTypeFilter() {
		return typeFilter;
	}

	/**
	 * @param typeFilter type filter
	 */
	public void setTypeFilter(String typeFilter) {
		this.typeFilter = typeFilter;
	}

	/**
	 * @return if false, JSON is returned, otherwise x-www-form-urlencoded
	 */
	public Boolean getUrlEncoded() {
		return urlEncoded;
	}

	/**
	 * @param urlEncoded false for JSON payloads
	 */
	public void setUrlEncoded(Boolean urlEncoded) {
		this.urlEncoded = urlEncoded;
	}

	/**
	 * @return if false, nothing is sent to {@code targetUrl}.
	 */
	public Boolean getActive() {
		return active;
	}

	/**
	 * @param active if false, nothing is sent to {@code targetUrl}.
	 */
	public void setActive(Boolean active) {
		this.active = active;
	}

	/**
	 * @return true if this was disabled by the system
	 */
	public Boolean getTooManyFailures() {
		return tooManyFailures;
	}

	/**
	 * @param tooManyFailures don't set this manually
	 */
	public void setTooManyFailures(Boolean tooManyFailures) {
		this.tooManyFailures = tooManyFailures;
	}

	/**
	 * @return true if subscribed to DAO.create() methods
	 */
	public Boolean getCreate() {
		return create;
	}

	/**
	 * @param create set to true to subscribe to create methods
	 */
	public void setCreate(Boolean create) {
		this.create = create;
	}

	/**
	 * @return true if subscribed to DAO.update() methods
	 */
	public Boolean getUpdate() {
		return update;
	}

	/**
	 * @param update set to true to subscribe to update methods
	 */
	public void setUpdate(Boolean update) {
		this.update = update;
	}

	/**
	 * @return true if subscribed to DAO.delete() methods
	 */
	public Boolean getDelete() {
		return delete;
	}

	/**
	 * @param delete set to true to subscribe to delete methods
	 */
	public void setDelete(Boolean delete) {
		this.delete = delete;
	}

	/**
	 * @return true if subscribed to DAO.createAll() methods
	 */
	public Boolean getCreateAll() {
		return createAll;
	}

	/**
	 * @param createAll set to true to subscribe to createAll methods
	 */
	public void setCreateAll(Boolean createAll) {
		this.createAll = createAll;
	}

	/**
	 * @return true if subscribed to DAO.updateAll() methods
	 */
	public Boolean getUpdateAll() {
		return updateAll;
	}

	/**
	 * @param updateAll set to true to subscribe to updateAll methods
	 */
	public void setUpdateAll(Boolean updateAll) {
		this.updateAll = updateAll;
	}

	/**
	 * @return true if subscribed to DAO.deleteAll() methods
	 */
	public Boolean getDeleteAll() {
		return deleteAll;
	}

	/**
	 * @param deleteAll set to true to subscribe to deleteAll methods
	 */
	public void setDeleteAll(Boolean deleteAll) {
		this.deleteAll = deleteAll;
	}

	/**
	 * Resets the secret key by generating a new one.
	 */
	public void resetSecret() {
		this.secret = Utils.generateSecurityToken();
	}

	@Override
	public void update() {
		if (active) {
			this.tooManyFailures = false; // clear notification flag
		}
		super.update();
	}

	@Override
	public String create() {
		if (StringUtils.isBlank(secret)) {
			resetSecret();
		}
		if (create || update || delete || createAll || updateAll || deleteAll) {
			this.active = true;
		}
		return super.create();
	}


}
