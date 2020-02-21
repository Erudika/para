/*
 * Copyright 2013-2020 Erudika. https://erudika.com
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import javax.validation.constraints.NotBlank;

/**
 * A "thing" in the Internet of Things.
 * Generic IoT object for describing a device in the cloud.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class Thing extends Sysprop {
	private static final long serialVersionUID = 1L;

	@Stored @Locked @NotBlank private String serviceBroker;
	@Stored private Map<String, Object> deviceState;
	@Stored @Locked private Map<String, Object> deviceMetadata;

	private transient Map<String, Object> deviceDetails;

	/**
	 * No-args constructor.
	 */
	public Thing() {
		this(null);
	}

	/**
	 * The default constructor.
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
	public Map<String, Object> getDeviceMetadata() {
		if (deviceMetadata == null) {
			deviceMetadata = new LinkedHashMap<>(20);
		}
		return deviceMetadata;
	}

	/**
	 * Sets the device's metadata.
	 * @param deviceMetadata a map
	 */
	public void setDeviceMetadata(Map<String, Object> deviceMetadata) {
		this.deviceMetadata = deviceMetadata;
	}

	/**
	 * The deviceState of the device.
	 * @return a map of keys/values
	 */
	public Map<String, Object> getDeviceState() {
		if (deviceState == null) {
			deviceState = new HashMap<>();
		}
		return deviceState;
	}

	/**
	 * Sets the device's deviceState.
	 * @param state a map of keys/values
	 */
	public void setDeviceState(Map<String, Object> state) {
		this.deviceState = state;
	}

	/**
	 * Adds a new key/value pair to the map.
	 * @param key a key
	 * @param value a value
	 * @return this
	 */
	public Thing addStateProperty(String key, Object value) {
		if (!StringUtils.isBlank(key) && value != null) {
			getDeviceState().put(key, value);
		}
		return this;
	}

	/**
	 * Returns the value of a property for a given key.
	 * @param key the key
	 * @return the value
	 */
	public Object getStateProperty(String key) {
		if (!StringUtils.isBlank(key)) {
			return getDeviceState().get(key);
		}
		return null;
	}

	/**
	 * Removes a property from the map.
	 * @param key the key
	 * @return this
	 */
	public Thing removeStateProperty(String key) {
		if (!StringUtils.isBlank(key)) {
			getDeviceState().remove(key);
		}
		return this;
	}

	/**
	 * Checks for the existence of a property.
	 * @param key the key
	 * @return true if a property with this key exists
	 */
	public boolean hasStateProperty(String key) {
		if (StringUtils.isBlank(key)) {
			return false;
		}
		return getDeviceState().containsKey(key);
	}

	/**
	 * Temporarily set when a Thing is created.
	 * Contains sensitive info shown only on {@link #create()}
	 * @return device informations (same as metadata, but shown only once)
	 */
	public Map<String, Object> getDeviceDetails() {
		return deviceDetails;
	}

	@Override
	public void delete() {
		Thing t = this;
		if (StringUtils.isBlank(serviceBroker)) {
			t = CoreUtils.getInstance().getDao().read(getAppid(), getId());
		}
		if (t != null) {
			IoTService iot = CoreUtils.getInstance().getIotFactory().getIoTService(t.getServiceBroker());
			if (iot != null && !StringUtils.isBlank(t.getName())) {
				iot.deleteThing(t);
			}
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
			if (!getDeviceMetadata().isEmpty()) {
				this.deviceDetails = getDeviceMetadata();
				return super.create();
			}
		}
		return null;
	}

	@Override
	public boolean equals(Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		return Objects.equals(this.serviceBroker, ((Thing) obj).getServiceBroker());
	}

	@Override
	public int hashCode() {
		int hash = super.hashCode();
		hash = 89 * hash + Objects.hashCode(this.serviceBroker);
		return hash;
	}

}
