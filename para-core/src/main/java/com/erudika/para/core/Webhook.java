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

import com.erudika.para.Para;
import com.erudika.para.annotations.Stored;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.URL;
import org.slf4j.LoggerFactory;

/**
 * Represents a webhook registration.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class Webhook extends Sysprop {
	private static final long serialVersionUID = 1L;

	@Stored @NotBlank @URL private String targetUrl;
	@Stored private String secret;
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
	@Stored private List<String> customEvents;
	@Stored private String triggeredEvent;
	@Stored private Object customPayload;

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
	 * @return the name of the custom event
	 */
	public List<String> getCustomEvents() {
		if (customEvents == null) {
			customEvents = new LinkedList<>();
		}
		return customEvents;
	}

	/**
	 * @param customEvents set the name of the custom event
	 */
	public void setCustomEvents(List<String> customEvents) {
		this.customEvents = customEvents;
	}

	/**
	 * @return the name of the custom event to be triggered
	 */
	public String getTriggeredEvent() {
		return triggeredEvent;
	}

	/**
	 * @param triggeredEvent custom event name
	 */
	public void setTriggeredEvent(String triggeredEvent) {
		this.triggeredEvent = triggeredEvent;
		if (!StringUtils.isBlank(triggeredEvent)) {
			// get around the validation
			setTargetUrl("https://para");
		}
	}

	/**
	 * @return the custom payload object
	 */
	public Object getCustomPayload() {
		return customPayload;
	}

	/**
	 * @param customPayload set the custom payload object which will be sent when a custom event is triggered
	 */
	public void setCustomPayload(Object customPayload) {
		this.customPayload = customPayload;
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
		triggeredEvent = null; // not used
		customPayload = null; // not used
		super.update();
	}

	@Override
	public String create() {
		// check if this is a trigger request for a custom event using PUT /webhooks/trigger:custom.event
		if (!StringUtils.isBlank(triggeredEvent) && customPayload != null) {
			sendEventPayloadToQueue(getAppid(), "customEvents", triggeredEvent, customPayload);
			setId("triggered" + Config.SEPARATOR + triggeredEvent);
			setName("This webhook object is not persisted and should be discarded.");
			setStored(false);
			setIndexed(false);
			setCached(false);
			return getId();
		}

		if (StringUtils.isBlank(secret)) {
			resetSecret();
		}
		if (create || update || delete || createAll || updateAll || deleteAll || !getCustomEvents().isEmpty()) {
			active = true;
		}
		triggeredEvent = null; // not used
		customPayload = null; // not used
		return super.create();
	}

	/**
	 * Builds the JSON payload object.
	 * @param event Para.DAO method name or custom event name
	 * @param payload payload object to convert to JSON
	 * @return the payload + metadata object as JSON string
	 */
	public String buildPayloadAsJSON(String event, Object payload) {
		Map<String, Object> data = new HashMap<>();
		data.put(Config._ID, getId());
		data.put(Config._APPID, getAppid());
		data.put(Config._TYPE, "webhookpayload");
		data.put("targetUrl", getTargetUrl());
		data.put("urlEncoded", getUrlEncoded());
		data.put("event", event);

		Map<String, Object> payloadObject = new HashMap<>();
		payloadObject.put(Config._TIMESTAMP, System.currentTimeMillis());
		payloadObject.put(Config._APPID, getAppid());
		payloadObject.put("event", event);
		if (payload instanceof List) {
			payloadObject.put("items", payload);
		} else {
			payloadObject.put("items", Collections.singletonList(payload));
		}
		try {
			String payloadString = ParaObjectUtils.getJsonWriterNoIdent().writeValueAsString(payloadObject);
			data.put("payload", payloadString);
			data.put("signature", Utils.hmacSHA256(payloadString, getSecret()));
			return ParaObjectUtils.getJsonWriterNoIdent().writeValueAsString(data);
		} catch (Exception e) {
			LoggerFactory.getLogger(Webhook.class).error(null, e);
		}
		return "";
	}

	/**
	 * Sends out the payload object for an event to the queue for processing.
	 * @param appid appid
	 * @param eventName event name like "create", "delete" or "customEvents"
	 * @param eventValue event value - for custom events this is the name of the custom event
	 * @param payload the payload
	 */
	public static void sendEventPayloadToQueue(String appid, String eventName, Object eventValue, Object payload) {
		if (StringUtils.isBlank(appid)) {
			return;
		}
		Pager p = new Pager(10);
		p.setSortby("_docid");
		List<Webhook> webhooks;
		do {
			Map<String, Object> terms = new HashMap<>();
			terms.put(eventName, eventValue);
			terms.put("active", true);
			webhooks = Para.getSearch().findTerms(appid, Utils.type(Webhook.class), terms, true, p);
				webhooks.stream().filter(webhook -> typeFilterMatches(webhook, payload)).
					forEach(webhook -> Para.getQueue().push(webhook.buildPayloadAsJSON(
							(eventValue instanceof String) ? (String) eventValue : eventName, payload)));
		} while (!webhooks.isEmpty());
	}

	private static boolean typeFilterMatches(Webhook webhook, Object paraObjects) {
		if (StringUtils.isBlank(webhook.getTypeFilter()) || App.ALLOW_ALL.equals(webhook.getTypeFilter())) {
			return true;
		}
		if (paraObjects instanceof ParaObject) {
			return webhook.getTypeFilter().equalsIgnoreCase(((ParaObject) paraObjects).getType());
		} else if (paraObjects instanceof List) {
			List<?> list = (List) paraObjects;
			if (!list.isEmpty() && list.get(0) instanceof ParaObject) {
				return webhook.getTypeFilter().equalsIgnoreCase(((ParaObject) list.get(0)).getType());
			}
		}
		return false;
	}
}
