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
package com.erudika.para.persistence;

import com.erudika.para.annotations.Cached;
import com.erudika.para.annotations.Indexed;
import com.erudika.para.core.ParaObject;
import com.erudika.para.utils.Pager;
import java.util.List;
import java.util.Map;

/**
 * The core persistence interface. Stores and retrieves domain objects to/from a data store.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public interface DAO {

	/////////////////////////////////////////////
	//				CRUD METHODS
	/////////////////////////////////////////////

	/**
	 * Persists an object to the data store.
	 * @param <P> the type of object
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param object the domain object
	 * @return the object's id or null if not created.
	 */
	@Indexed(action = Indexed.Action.ADD)
	@Cached(action = Cached.Action.PUT)
	<P extends ParaObject> String create(String appid, P object);

	/**
	 * Persists an object to the data store.
	 * @param <P> the type of object
	 * @param object the domain object
	 * @return the object's id or null if not created.
	 */
	<P extends ParaObject> String create(P object);

	/**
	 * Retrieves an object from the data store.
	 * @param <P> the type of object
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param key an object id
	 * @return the object or null if not found
	 */
	@Cached(action = Cached.Action.GET)
	<P extends ParaObject> P read(String appid, String key);

	/**
	 * Retrieves an object from the data store.
	 * @param <P> the type of object
	 * @param key an object id
	 * @return the object or null if not found
	 */
	<P extends ParaObject> P read(String key);

	/**
	 * Updates an object permanently.
	 * @param <P> the type of object
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param object the domain object
	 */
	@Indexed(action = Indexed.Action.ADD)
	@Cached(action = Cached.Action.PUT)
	<P extends ParaObject> void update(String appid, P object);

	/**
	 * Updates an object permanently.
	 * @param <P> the type of object
	 * @param object the domain object
	 */
	<P extends ParaObject> void update(P object);

	/**
	 * Deletes an object permanently.
	 * @param <P> the type of object
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param object the domain object
	 */
	@Indexed(action = Indexed.Action.REMOVE)
	@Cached(action = Cached.Action.DELETE)
	<P extends ParaObject> void delete(String appid, P object);

	/**
	 * Deletes an object permanently.
	 * @param <P> the type of object
	 * @param object the domain object
	 */
	<P extends ParaObject> void delete(P object);

	/////////////////////////////////////////////
	//           BATCH CRUD METHODS
	/////////////////////////////////////////////

	/**
	 * Saves multiple objects to the data store.
	 * @param <P> the type of object
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param objects the list of objects to save
	 */
	@Indexed(action = Indexed.Action.ADD_ALL)
	@Cached(action = Cached.Action.PUT_ALL)
	<P extends ParaObject> void createAll(String appid, List<P> objects);

	/**
	 * Saves multiple objects to the data store.
	 * @param <P> the type of object
	 * @param objects the list of objects to save
	 */
	<P extends ParaObject> void createAll(List<P> objects);

	/**
	 * Retrieves multiple objects from the data store.
	 * @param <P> the type of object
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param keys a list of object ids
	 * @param getAllColumns true if all columns must be retrieved. used to save bandwidth.
	 * @return a map of ids to objects
	 */
	@Cached(action = Cached.Action.GET_ALL)
	<P extends ParaObject> Map<String, P> readAll(String appid, List<String> keys, boolean getAllColumns);

	/**
	 * Retrieves multiple objects from the data store.
	 * @param <P> the type of object
	 * @param keys a list of object ids
	 * @param getAllColumns true if all columns must be retrieved. used to save bandwidth.
	 * @return a map of ids to objects
	 */
	<P extends ParaObject> Map<String, P> readAll(List<String> keys, boolean getAllColumns);

	/**
	 * Reads a fixed number of objects. Used for scanning a data store page by page.
	 * Calling this method would bypass the read cache and will hit the DB.
	 * @param <P> the type of object
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects
	 */
	<P extends ParaObject> List<P> readPage(String appid, Pager pager);

	/**
	 * Reads a fixed number of objects. Used for scanning a data store page by page.
	 * Calling this method would bypass the read cache and will hit the DB.
	 * @param <P> the type of object
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects
	 */
	<P extends ParaObject> List<P> readPage(Pager pager);

	/**
	 * Updates multiple objects.
	 * @param <P> the type of object
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param objects a list of objects to update
	 */
	@Indexed(action = Indexed.Action.ADD_ALL)
	@Cached(action = Cached.Action.PUT_ALL)
	<P extends ParaObject> void updateAll(String appid, List<P> objects);

	/**
	 * Updates multiple objects.
	 * @param <P> the type of object
	 * @param objects a list of objects to update
	 */
	<P extends ParaObject> void updateAll(List<P> objects);

	/**
	 * Deletes multiple objects.
	 * @param <P> the type of object
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param objects a list of objects to delete
	 */
	@Indexed(action = Indexed.Action.REMOVE_ALL)
	@Cached(action = Cached.Action.DELETE_ALL)
	<P extends ParaObject> void deleteAll(String appid, List<P> objects);

	/**
	 * Deletes multiple objects.
	 * @param <P> the type of object
	 * @param objects a list of objects to delete
	 */
	<P extends ParaObject> void deleteAll(List<P> objects);
}
