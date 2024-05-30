/*
 * Copyright 2013-2022 Erudika. http://erudika.com
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

import com.erudika.para.core.annotations.Stored;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.Pager;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.utils.Utils;
import jakarta.validation.constraints.NotBlank;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	@Stored private String propertyFilter;
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
	@Stored private Integer repeatedDeliveryAttempts; // send the same payload X times

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
		this.urlEncoded = false;
		this.create = false;
		this.update = false;
		this.delete = false;
		this.createAll = false;
		this.updateAll = false;
		this.deleteAll = false;
		this.active = false;
		this.tooManyFailures = false;
		this.repeatedDeliveryAttempts = 1;
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
	 * @return property filter
	 */
	public String getPropertyFilter() {
		return propertyFilter;
	}

	/**
	 * @param propertyFilter property filter
	 */
	public void setPropertyFilter(String propertyFilter) {
		this.propertyFilter = propertyFilter;
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
		if (!StringUtils.isBlank(triggeredEvent) && StringUtils.isBlank(targetUrl)) {
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
	 * @return the number of times to deliver the same payload to target.
	 */
	public Integer getRepeatedDeliveryAttempts() {
		if (repeatedDeliveryAttempts == null) {
			return 1;
		}
		return Math.abs(repeatedDeliveryAttempts);
	}

	/**
	 * @param repeatedDeliveryAttempts the number of times to deliver the same payload to target.
	 */
	public void setRepeatedDeliveryAttempts(Integer repeatedDeliveryAttempts) {
		this.repeatedDeliveryAttempts = repeatedDeliveryAttempts;
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
		// check if this is a trigger request for a custom event using POST /webhooks
		if (!StringUtils.isBlank(triggeredEvent) && customPayload != null) {
			sendEventPayloadToQueue(getAppid(), "customEvents", triggeredEvent, customPayload);
			if (!StringUtils.isBlank(secret) && Utils.isValidURL(targetUrl) && !"https://para".equals(targetUrl)) {
				// support for triggering and delivering the custom event directly without having to register a webhook
				Para.getQueue().push(buildPayloadAsJSON(triggeredEvent, customPayload));
			}
			setId("triggered" + Para.getConfig().separator() + triggeredEvent);
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
		data.put("repeatedDeliveryAttempts", getRepeatedDeliveryAttempts());
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
			data.put("signature", Utils.hmacSHA256(payloadString, secret()));
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
			terms.put(Config._APPID, appid);
			terms.put("active", true);
			webhooks = Para.getSearch().findTerms(appid, Utils.type(Webhook.class), terms, true, p);
			webhooks.stream().
					filter(webhook -> typeFilterMatches(webhook, payload) && propertyFilterMatches(webhook, payload)).
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

	public static boolean propertyFilterMatches(Webhook webhook, Object paraObjects) {
		if (StringUtils.isBlank(webhook.getPropertyFilter())) {
			return true;
		}
		if (webhook.getPropertyFilter().contains(":")) {
			if (paraObjects instanceof ParaObject) {
				return matchesPropFilter(webhook, (ParaObject) paraObjects);
			} else if (paraObjects instanceof List) {
				List<?> list = (List) paraObjects;
				return !list.isEmpty() && list.stream().anyMatch((pobj) -> matchesPropFilter(webhook, (ParaObject) pobj));
			}
		}
		return false;
	}

	private static boolean matchesPropFilter(Webhook webhook, ParaObject paraObject) {
		String propName = StringUtils.substringBefore(webhook.getPropertyFilter(), ":");
		String propValue = StringUtils.substringAfter(webhook.getPropertyFilter(), ":");
		Set<String> vals = new LinkedHashSet<>(List.of(StringUtils.split(propValue, ",|", 50)));
		boolean matchAll = StringUtils.contains(propValue, ",");
//		boolean multiple = StringUtils.containsAny(propValue, ",", "|");
		Map<String, Object> props = ParaObjectUtils.getAnnotatedFields(paraObject, null, false);
		if (props.containsKey(propName)) {
			Object v = props.get(propName);
			if ("-".equals(propValue) && (v == null || StringUtils.isBlank(v.toString()))) {
				return true;
			}
			if (v instanceof Collection) {
				if (matchAll) {
					try {
						return ((Collection) v).containsAll(vals);
					} catch (Exception e) {
						return false;
					}
				} else {
					for (String val : vals) {
						if (((Collection) v).contains(val)) {
							return true;
						}
					}
				}

			} else {
				if (vals.size() > 1 && !matchAll) {
					for (String val : vals) {
						if (v != null && v.equals(val)) {
							return true;
						}
					}
				} else {
					return v != null && v.equals(propValue);
				}
			}
		}
		return false;
	}

	private String secret() {
		if ("{{secretKey}}".equals(getSecret())) {
			// fetch the secret key for that app
			App app = Para.getDAO().read(App.id(getAppid()));
			if (app != null) {
				return app.getSecret();
			}
		}
		return getSecret();
	}
}
