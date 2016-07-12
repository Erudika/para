/*
 * Copyright 2013-2016 Erudika. http://erudika.com
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

import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.iot.IoTService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;

/**
 * A "thing" in the Internet of Things.
 * Generic IoT object for describing a device in the cloud.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class Thing extends Sysprop {
	private static final long serialVersionUID = 1L;

	@Stored @Locked @NotBlank private String serviceBroker;
	@Stored private Map<String, Object> state;
	@Stored @Locked private Map<String, Object> metadata;

	/**
	 * No-args constructor
	 */
	public Thing() {
		this(null);
	}

	/**
	 * The default constructor
	 * @param id the object id
	 */
	public Thing(String id) {
		super(id);
	}

	/**
	 * Returns the service broker, e.g. AWS.
	 * Used for switching cloud providers.
	 * @return a string
	 */
	public String getServiceBroker() {
		return serviceBroker;
	}

	/**
	 * Sets the service broker which will handle the messages in the cloud.
	 * @param serviceBroker a name
	 */
	public void setServiceBroker(String serviceBroker) {
		this.serviceBroker = serviceBroker;
	}

	/**
	 * Returns the device's metadata.
	 * @return a map
	 */
	@JsonIgnore
	public Map<String, Object> getMetadata() {
		return metadata;
	}

	/**
	 * Sets the device's metadata.
	 * @param metadata a map
	 */
	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	/**
	 * The state of the device.
	 * @return a map of keys/values
	 */
	public Map<String, Object> getState() {
		if (state == null) {
			state = new HashMap<String, Object>();
		}
		return state;
	}

	/**
	 * Sets the device's state.
	 * @param state a map of keys/values
	 */
	public void setState(Map<String, Object> state) {
		this.state = state;
	}

	/**
	 * Adds a new key/value pair to the map.
	 * @param key a key
	 * @param value a value
	 * @return this
	 */
	public Thing addStateProperty(String key, Object value) {
		if (!StringUtils.isBlank(key) && value != null) {
			getState().put(key, value);
		}
		return this;
	}

	/**
	 * Returns the value of a property for a given key
	 * @param key the key
	 * @return the value
	 */
	public Object getStateProperty(String key) {
		if (!StringUtils.isBlank(key)) {
			return getState().get(key);
		}
		return null;
	}

	/**
	 * Removes a property from the map
	 * @param key the key
	 * @return this
	 */
	public Thing removeStateProperty(String key) {
		if (!StringUtils.isBlank(key)) {
			getState().remove(key);
		}
		return this;
	}

	/**
	 * Checks for the existence of a property
	 * @param key the key
	 * @return true if a property with this key exists
	 */
	public boolean hasStateProperty(String key) {
		if (StringUtils.isBlank(key)) {
			return false;
		}
		return getState().containsKey(key);
	}

	@Override
	public boolean exists() {
		IoTService iot = CoreUtils.getInstance().getIotFactory().getIoTService(serviceBroker);
		if (iot != null && !StringUtils.isBlank(getName())) {
			return iot.existsThing(getName());
		}
		return super.exists();
	}

	@Override
	public void delete() {
		IoTService iot = CoreUtils.getInstance().getIotFactory().getIoTService(serviceBroker);
		if (iot != null && !StringUtils.isBlank(getName())) {
			iot.deleteThing(this);
		}
		super.delete();
	}

	@Override
	public void update() {
		IoTService iot = CoreUtils.getInstance().getIotFactory().getIoTService(serviceBroker);
		if (iot != null && !StringUtils.isBlank(getName())) {
			iot.updateThing(this);
		}
		super.update();
	}

	@Override
	public String create() {
		IoTService iot = CoreUtils.getInstance().getIotFactory().getIoTService(serviceBroker);
		if (iot != null && !StringUtils.isBlank(getName())) {
			iot.createThing(this);
		}
		return super.create();
	}

}
