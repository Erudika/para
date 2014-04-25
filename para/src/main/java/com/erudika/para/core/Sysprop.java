/*
 * Copyright 2013-2014 Erudika. http://erudika.com
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
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * A system property object. Generic object for storing data.
 * It is essentially a map of keys and values.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class Sysprop extends PObject {
	private static final long serialVersionUID = 1L;

	@Stored private Map<String, Object> properties;

	/**
	 * No-args constructor
	 */
	public Sysprop() {
		this(null);
	}

	/**
	 * The default constructor
	 * @param id the object id
	 */
	public Sysprop(String id) {
		setId(id);
		setName(getName());
	}

	/**
	 * Adds a new key/value pair to the map.
	 * @param name a key
	 * @param value a value
	 * @return this
	 */
	public Sysprop addProperty(String name, Object value) {
		if (!StringUtils.isBlank(name) && value != null) {
			getProperties().put(name, value);
		}
		return this;
	}

	/**
	 * Returns the value of a property for a given key
	 * @param name the key
	 * @return the value
	 */
	public Object getProperty(String name) {
		if (!StringUtils.isBlank(name)) {
			return getProperties().get(name);
		}
		return null;
	}

	/**
	 * Removes a property from the map
	 * @param name the key
	 * @return this
	 */
	public Sysprop removeProperty(String name) {
		if (!StringUtils.isBlank(name)) {
			getProperties().remove(name);
		}
		return this;
	}

	/**
	 * Checks for the existence of a property
	 * @param name the key
	 * @return true if a property with this key exists
	 */
	public boolean hasProperty(String name) {
		if (StringUtils.isBlank(name)) {
			return false;
		}
		return getProperties().containsKey(name);
	}

	/**
	 * A map of all properties (key/values)
	 * @return a map
	 */
	public Map<String, Object> getProperties() {
		if (properties == null) {
			properties = new HashMap<String, Object>();
		}
		return properties;
	}

	/**
	 * Overwrites the map.
	 * @param properties a new map
	 */
	public void setProperties(Map<String, Object> properties) {
		this.properties = properties;
	}
}
