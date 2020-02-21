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
package com.erudika.para.cache;

import java.util.List;
import java.util.Map;

/**
 * This class manages object caching. An object is cached mainly for read performance and database offloading. The cache
 * can also be used to store transient data which is shared among all nodes of the system.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public interface Cache {

	/**
	 * Do we have this object in the cache?
	 * @param id the object's id
	 * @return true if in cache
	 */
	boolean contains(String id);

	/**
	 * Do we have this object in the cache?
	 * @param appid the name of the application
	 * @param id the object's id
	 * @return true if in cache
	 * @see #contains(java.lang.String)
	 */
	boolean contains(String appid, String id);

	/**
	 * Store an object in the cache.
	 * @param <T> the type of object to be cached
	 * @param id the object's id, not null or empty
	 * @param object the object itself, not null
	 */
	<T> void put(String id, T object);

	/**
	 * Store an object in the cache.
	 * @param appid the name of the application
	 * @param <T> the type of object to be cached
	 * @param id the object's id, not null or empty
	 * @param object the object itself, not null
	 * @see #put(java.lang.String, java.lang.Object)
	 */
	<T> void put(String appid, String id, T object);

	/**
	 * Store an object in the cache.
	 * @param appid the name of the application
	 * @param <T> the type of object to be cached
	 * @param id the object's id, not null or empty
	 * @param object the object itself, not null
	 * @param ttlSeconds the time to live for an object before it is evicted from the cache.
	 * @see #put(java.lang.String, java.lang.Object)
	 */
	<T> void put(String appid, String id, T object, Long ttlSeconds);

	/**
	 * Store all objects in cache, except those which are null.
	 * @param <T> any object, not null
	 * @param objects map of id - object
	 */
	<T> void putAll(Map<String, T> objects);

	/**
	 * Store all objects in cache, except those which are null.
	 * @param appid the name of the application
	 * @param <T> any object, not null
	 * @param objects map of id - object
	 * @see #putAll(java.util.Map)
	 */
	<T> void putAll(String appid, Map<String, T> objects);

	/**
	 * Read an object from cache.
	 * @param <T> the type of object to be cached
	 * @param id the object's id, not null or empty
	 * @return the object from cache or null if not found
	 */
	<T> T get(String id);

	/**
	 * Read an object from cache.
	 * @param appid the name of the application
	 * @param <T> the type of object to be cached
	 * @param id the object's id, not null or empty
	 * @return the object from cache or null if not found
	 * @see #get(java.lang.String)
	 */
	<T> T get(String appid, String id);

	/**
	 * Read a number of objects given a list of their ids.
	 * @param <T> the type of object to be cached
	 * @param ids the ids, not null or empty
	 * @return a map of the objects that are contained in cache (may be empty)
	 */
	<T> Map<String, T> getAll(List<String> ids);

	/**
	 * Read a number of objects given a list of their ids.
	 * @param appid the name of the application
	 * @param <T> the type of object to be cached
	 * @param ids the ids, not null or empty
	 * @return a map of the objects that are contained in cache (may be empty)
	 * @see #getAll(java.util.List)
	 */
	<T> Map<String, T> getAll(String appid, List<String> ids);

	/**
	 * Remove an object from cache.
	 * @param id the object's id, not null or empty
	 */
	void remove(String id);

	/**
	 * Remove an object from cache.
	 * @param appid the name of the application
	 * @param id the object's id, not null or empty
	 * @see #remove(java.lang.String)
	 */
	void remove(String appid, String id);

	/**
	 * Clears the cache.
	 */
	void removeAll();

	/**
	 * Clears the cache.
	 * @param appid the name of the application
	 * @see #removeAll()
	 */
	void removeAll(String appid);

	/**
	 * Remove a number of objects from cache given a list of their ids.
	 * @param ids the ids, not null or empty
	 */
	void removeAll(List<String> ids);

	/**
	 * Remove a number of objects from cache given a list of their ids.
	 * @param ids the ids, not null or empty
	 * @param appid the name of the application
	 * @see #removeAll(java.util.List)
	 */
	void removeAll(String appid, List<String> ids);
}
