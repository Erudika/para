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
package com.erudika.para.utils;

import com.erudika.para.core.Sysprop;
import com.erudika.para.core.utils.CoreUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * A utility for creating, reading, and updating registries. The Registry allows for efficient sharing of information
 * between multiple nodes of a Para cluster. Registry objects are saved in the DAO resource for the root application.
 * @author Jeremy Wiesner [jswiesner@gmail.com]
 */
public final class RegistryUtils {

	private static final String REGISTRY_PREFIX = "Registry:";
	private static final String REGISTRY_APP_ID = Config.getRootAppIdentifier();

	private RegistryUtils() { }

	/**
	 * Add a specific value to a registry. If the registry doesn't exist yet, create it.
	 * @param registryName the name of the registry.
	 * @param key the unique key corresponding to the value to insert (typically an appid).
	 * @param value the value to add to the registry.
	 */
	public static void putValue(String registryName, String key, Object value) {
		if (StringUtils.isBlank(registryName) || StringUtils.isBlank(key) || value == null) {
			return;
		}
		Sysprop registryObject = readRegistryObject(registryName);
		if (registryObject == null) {
			registryObject = new Sysprop(getRegistryID(registryName));
			registryObject.addProperty(key, value);
			CoreUtils.getInstance().getDao().create(REGISTRY_APP_ID, registryObject);
		} else {
			registryObject.addProperty(key, value);
			CoreUtils.getInstance().getDao().update(REGISTRY_APP_ID, registryObject);
		}
	}

	/**
	 * Retrieve one specific value from a registry.
	 * @param registryName the name of the registry.
	 * @param key the unique key corresponding to the value to retrieve (typically an appid).
	 * @return the value corresponding to the registry key, null if no value is found for the specified key.
	 */
	public static Object getValue(String registryName, String key) {
		Map<String, Object> registry = getRegistry(registryName);
		if (registry == null || StringUtils.isBlank(key)) {
			return null;
		}
		return registry.get(key);
	}

	/**
	 * Remove a specific value from a registry.
	 * @param registryName the name of the registry.
	 * @param key the unique key corresponding to the value to remove (typically an appid).
	 */
	public static void removeValue(String registryName, String key) {
		Sysprop registryObject = readRegistryObject(registryName);
		if (registryObject == null || StringUtils.isBlank(key)) {
			return;
		}
		if (registryObject.hasProperty(key)) {
			registryObject.removeProperty(key);
			CoreUtils.getInstance().getDao().update(REGISTRY_APP_ID, registryObject);
		}
	}

	/**
	 * Retrieve an entire registry.
	 * @param registryName the name of the registry.
	 * @return the map of all registry values, null if no registry is found by the specified name.
	 */
	public static Map<String, Object> getRegistry(String registryName) {
		if (StringUtils.isBlank(registryName)) {
			return null;
		}
		Sysprop registryObject = readRegistryObject(registryName);
		return registryObject == null ? null : registryObject.getProperties();
	}

	private static Sysprop readRegistryObject(String registryName) {
		return CoreUtils.getInstance().getDao().read(REGISTRY_APP_ID, getRegistryID(registryName));
	}

	private static String getRegistryID(String registryName) {
		return REGISTRY_PREFIX + registryName;
	}
}
