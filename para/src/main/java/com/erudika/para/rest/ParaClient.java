/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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
package com.erudika.para.rest;

import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Tag;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static javax.ws.rs.HttpMethod.*;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;

/**
 * The Java REST client.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class ParaClient {

	private static final String DEFAULT_ENDPOINT = "http://localhost:8080";
	private static final String DEFAULT_PATH = Api1.PATH;
	private String endpoint;
	private String path;
	private final String accessKey;
	private final String secretKey;

	public ParaClient(String accessKey, String secretKey) {
		this.accessKey = accessKey;
		this.secretKey = secretKey;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	/**
	 * Returns the {@link App} for the current access key (appid).
	 * @return the App object
	 */
	public App getApp() {
		return read(Utils.type(App.class), accessKey);
	}

	/**
	 * Returns the endpoint URL
	 * @return the endpoint
	 */
	public String getEndpoint() {
		if (StringUtils.isBlank(endpoint)) {
			return DEFAULT_ENDPOINT;
		} else {
			return endpoint;
		}
	}

	/**
	 * Sets the API request path
	 * @param path a new path
	 */
	public void setApiPath(String path) {
		this.path = path;
	}

	/**
	 * Returns the API request path
	 * @return the request path without parameters
	 */
	public String getApiPath() {
		if (StringUtils.isBlank(path)) {
			return DEFAULT_PATH;
		} else {
			if (!path.endsWith("/")) {
				path += "/";
			}
			return path;
		}
	}

	@SuppressWarnings("unchecked")
	private <T> T getEntity(Response res, Class<?> type) {
		if (res != null) {
			if (res.getStatus() == Response.Status.OK.getStatusCode()
					|| res.getStatus() == Response.Status.CREATED.getStatusCode()
					|| res.getStatus() == Response.Status.NOT_MODIFIED.getStatusCode()) {
					return res.hasEntity() ? res.readEntity((Class<T>) type) : null;
			} else if (res.getStatus() != Response.Status.NOT_FOUND.getStatusCode()
					&& res.getStatus() != Response.Status.NOT_MODIFIED.getStatusCode()
					&& res.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {
				Map<String, Object> error = res.hasEntity() ? res.readEntity(Map.class) : null;
				if (error != null && error.containsKey("code")) {
					String msg = error.containsKey("message") ? (String) error.get("message") : "error";
					msg = msg.concat(" ").concat(Integer.toString((Integer) error.get("code")));
					throw new WebApplicationException(msg, (Integer) error.get("code"));
				}
			}
		}
		return null;
	}

	private String getFullPath(String resourcePath) {
		if (resourcePath == null) {
			resourcePath = "";
		} else if (resourcePath.startsWith("/")) {
			resourcePath = resourcePath.substring(1);
		}
		return getApiPath() + resourcePath;
	}

	private Response invokeGet(String resourcePath, MultivaluedMap<String, String> params) {
		return RestUtils.invokeSignedRequest(accessKey, secretKey, GET,
				getEndpoint(), getFullPath(resourcePath), null, params, new byte[0]);
	}

	private Response invokePost(String resourcePath, Entity<?> entity) {
		return RestUtils.invokeSignedRequest(accessKey, secretKey, POST,
				getEndpoint(), getFullPath(resourcePath), null, null, entity);
	}

	private Response invokePut(String resourcePath, Entity<?> entity) {
		return RestUtils.invokeSignedRequest(accessKey, secretKey, PUT,
				getEndpoint(), getFullPath(resourcePath), null, null, entity);
	}

	private Response invokeDelete(String resourcePath, MultivaluedMap<String, String> params) {
		return RestUtils.invokeSignedRequest(accessKey, secretKey, DELETE,
				getEndpoint(), getFullPath(resourcePath), null, params, new byte[0]);
	}

	private MultivaluedMap<String, String> pagerToParams(Pager... pager) {
		MultivaluedMap<String, String> map = new MultivaluedHashMap<String, String>();
		if (pager != null && pager.length > 0) {
			Pager p = pager[0];
			if (p != null) {
				map.put("page", Collections.singletonList(Long.toString(p.getPage())));
				map.put("desc", Collections.singletonList(Boolean.toString(p.isDesc())));
				map.put("limit", Collections.singletonList(Integer.toString(p.getLimit())));
				if (p.getSortby() != null) {
					map.put("sort", Collections.singletonList(p.getSortby()));
				}
			}
		}
		return map;
	}

	@SuppressWarnings("unchecked")
	private <P extends ParaObject> List<P> getItemsFromList(List<?> result) {
		if (result != null && !result.isEmpty()) {
			// this isn't very efficient but there's no way to know what type of objects we're reading
			ArrayList<P> objects = new ArrayList<P>();
			for (Object map : result) {
				P p = Utils.setAnnotatedFields((Map<String, Object>) map);
				if (p != null) {
					objects.add(p);
				}
			}
			return objects;
		}
		return Collections.emptyList();
	}

	@SuppressWarnings("unchecked")
	private <P extends ParaObject> List<P> getItems(Map<String, Object> result, Pager... pager) {
		if (result != null && !result.isEmpty() && result.containsKey("items")) {
			if (pager != null && pager.length > 0 && pager[0] != null && result.containsKey("totalHits")) {
				pager[0].setCount(((Integer) result.get("totalHits")).longValue());
			}
			return (List<P>) getItemsFromList((List<?>) result.get("items"));
		}
		return Collections.emptyList();
	}

	/////////////////////////////////////////////
	//				 PERSISTENCE
	/////////////////////////////////////////////

	/**
	 * Persists an object to the data store.
	 * @param <P> the type of object
	 * @param obj the domain object
	 * @return the same object with assigned id or null if not created.
	 */
	public <P extends ParaObject> P create(P obj) {
		if (obj == null) {
			return null;
		}
		return getEntity(invokePost(obj.getType(), Entity.json(obj)), obj.getClass());
	}

	/**
	 * Retrieves an object from the data store.
	 * @param <P> the type of object
	 * @param type the type of the object
	 * @param id the id of the object
	 * @return the retrieved object or null if not found
	 */
	public <P extends ParaObject> P read(String type, String id) {
		if (StringUtils.isBlank(type) || StringUtils.isBlank(id)) {
			return null;
		}

		return getEntity(invokeGet(type.concat("/").concat(id), null), Utils.toClass(type));
	}

	/**
	 * Retrieves an object from the data store.
	 * @param <P> the type of object
	 * @param id the id of the object
	 * @return the retrieved object or null if not found
	 */
	public <P extends ParaObject> P read(String id) {
		if (StringUtils.isBlank(id)) {
			return null;
		}
		Map<String, Object> data = getEntity(invokeGet("_id/".concat(id), null), Map.class);
		return Utils.setAnnotatedFields(data);
	}

	/**
	 * Updates an object permanently.
	 * @param <P> the type of object
	 * @param obj the object to update
	 * @return the updated object
	 */
	public <P extends ParaObject> P update(P obj) {
		if (obj == null) {
			return null;
		}
		return getEntity(invokePut(obj.getObjectURI(), Entity.json(obj)), obj.getClass());
	}

	/**
	 * Deletes an object permanently.
	 * @param <P> the type of object
	 * @param obj the object
	 */
	public <P extends ParaObject> void delete(P obj) {
		if (obj == null) {
			return;
		}
		invokeDelete(obj.getObjectURI(), null);
	}

	/**
	 * Saves multiple objects to the data store.
	 * @param <P> the type of object
	 * @param objects the list of objects to save
	 * @return a list of objects
	 */
	public <P extends ParaObject> List<P> createAll(List<P> objects) {
		if (objects == null || objects.isEmpty() || objects.get(0) == null) {
			return Collections.emptyList();
		}
		return getItemsFromList((List<?>) getEntity(invokePost("_batch", Entity.json(objects)), List.class));
	}

	/**
	 * Retrieves multiple objects from the data store.
	 * @param <P> the type of object
	 * @param keys a list of object ids
	 * @return a list of objects
	 */
	public <P extends ParaObject> List<P> readAll(List<String> keys) {
		if (keys == null || keys.isEmpty()) {
			return Collections.emptyList();
		}
		MultivaluedMap<String, String> ids = new MultivaluedHashMap<String, String>();
		ids.put("ids", keys);
		return getItemsFromList((List<?>) getEntity(invokeGet("_batch", ids), List.class));
	}

	/**
	 * Updates multiple objects.
	 * @param <P> the type of object
	 * @param objects the objects to update
	 * @return a list of objects
	 */
	public <P extends ParaObject> List<P> updateAll(List<P> objects) {
		if (objects == null || objects.isEmpty()) {
			return Collections.emptyList();
		}
		return getItemsFromList((List<?>) getEntity(invokePut("_batch", Entity.json(objects)), List.class));
	}

	/**
	 * Deletes multiple objects.
	 * @param <P> the type of object
	 * @param keys the ids of the objects to delete
	 */
	public <P extends ParaObject> void deleteAll(List<String> keys) {
		if (keys == null || keys.isEmpty()) {
			return;
		}
		MultivaluedMap<String, String> ids = new MultivaluedHashMap<String, String>();
		ids.put("ids", keys);
		invokeDelete("_batch", ids);
	}

	/**
	 * Returns a list all objects found for the given type.
	 * The result is paginated so only one page of items is returned, at a time.
	 * @param <P> the type of object
	 * @param type the type of objects to search for
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects
	 */
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> list(String type, Pager... pager) {
		if (StringUtils.isBlank(type)) {
			return Collections.emptyList();
		}
		return getItems((Map<String, Object>) getEntity(invokeGet(type, pagerToParams(pager)), Map.class), pager);
	}

	/////////////////////////////////////////////
	//				 SEARCH
	/////////////////////////////////////////////

	/**
	 * Simple id search.
	 * @param <P> type of the object
	 * @param id the id
	 * @return the object if found or null
	 */
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> P findById(String id) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle(Config._ID, id);
		List<P> list = getItems(find("id", params));
		return list.isEmpty() ? null : list.get(0);
	}

	/**
	 * Simple multi id search.
	 * @param <P> type of the object
	 * @param ids a list of ids to search for
	 * @return the object if found or null
	 */
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> findByIds(List<String> ids) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.put("ids", ids);
		return (List<P>) getItems(find("ids", params));
	}

	/**
	 * Search for {@link com.erudika.para.core.Address} objects in a radius of X km from a given point.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param query the query string
	 * @param radius the radius of the search circle
	 * @param lat latitude
	 * @param lng longitude
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findNearby(String type, String query, int radius, double lat, double lng,
			Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("latlng", lat + "," + lng);
		params.putSingle("radius", Integer.toString(radius));
		params.putSingle("q", query);
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("nearby", params), pager);
	}

	/**
	 * Searches for objects that have a property which value starts with a given prefix.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param field the property name of an object
	 * @param prefix the prefix
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findPrefix(String type, String field, String prefix, Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("field", field);
		params.putSingle("prefix", prefix);
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("prefix", params), pager);
	}

	/**
	 * Simple query string search. This is the basic search method.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param query the query string
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findQuery(String type, String query, Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("q", query);
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("", params), pager);
	}

	/**
	 * Searches for objects that have similar property values to a given text. A "find like this" query.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param filterKey exclude an object with this key from the results (optional)
	 * @param fields a list of property names
	 * @param liketext text to compare to
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findSimilar(String type, String filterKey, String[] fields, String liketext,
			Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.put("fields", fields == null ? null : Arrays.asList(fields));
		params.putSingle("filterid", filterKey);
		params.putSingle("like", liketext);
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("similar", params), pager);
	}

	/**
	 * Searches for objects tagged with one or more tags.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param tags the list of tags
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findTagged(String type, String[] tags, Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.put("tags", tags == null ? null : Arrays.asList(tags));
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("tagged", params), pager);
	}

	/**
	 * Searches for {@link com.erudika.para.core.Tag} objects.
	 * This method might be deprecated in the future.
	 * @param <P> type of the object
	 * @param keyword the tag keyword to search for
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findTags(String keyword, Pager... pager) {
		keyword = (keyword == null) ? "*" : keyword.concat("*");
		return findWildcard(Utils.type(Tag.class), "tag", keyword, pager);
	}

	/**
	 * Searches for objects having a property value that is in list of possible values.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param field the property name of an object
	 * @param terms a list of terms (property values)
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findTermInList(String type, String field, List<String> terms, Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("field", field);
		params.put("terms", terms);
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("in", params), pager);
	}

	/**
	 * Searches for objects that have properties matching some given values. A terms query.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param terms a map of fields (property names) to terms (property values)
	 * @param matchAll match all terms. If true - AND search, if false - OR search
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findTerms(String type, Map<String, ?> terms, boolean matchAll,
			Pager... pager) {
		if (terms == null) {
			return Collections.emptyList();
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("matchall", Boolean.toString(matchAll));
		ArrayList<String> list = new ArrayList<String>();
		for (Map.Entry<String, ? extends Object> term : terms.entrySet()) {
			String key = term.getKey();
			Object value = term.getValue();
			if (value != null) {
				list.add(key.concat(Config.SEPARATOR).concat(value.toString()));
			}
		}
		if (!terms.isEmpty()) {
			params.put("terms", list);
		}
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("terms", params), pager);
	}

	/**
	 * Searches for objects that have a property with a value matching a wildcard query.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param field the property name of an object
	 * @param wildcard wildcard query string. For example "cat*".
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findWildcard(String type, String field, String wildcard, Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("field", field);
		params.putSingle("q", wildcard);
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("wildcard", params), pager);
	}

	/**
	 * Counts indexed objects.
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @return the number of results found
	 */
	public Long getCount(String type) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle(Config._TYPE, type);
		Pager pager = new Pager();
		getItems(find("count", params), pager);
		return pager.getCount();
	}

	/**
	 * Counts indexed objects matching a set of terms/values.
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param terms a list of terms (property values)
	 * @return the number of results found
	 */
	public Long getCount(String type, Map<String, ?> terms) {
		if (terms == null) {
			return 0L;
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		ArrayList<String> list = new ArrayList<String>();
		for (Map.Entry<String, ? extends Object> term : terms.entrySet()) {
			String key = term.getKey();
			Object value = term.getValue();
			if (value != null) {
				list.add(key.concat(Config.SEPARATOR).concat(value.toString()));
			}
		}
		if (!terms.isEmpty()) {
			params.put("terms", list);
		}
		params.putSingle(Config._TYPE, type);
		params.putSingle("count", "true");
		Pager pager = new Pager();
		getItems(find("terms", params), pager);
		return pager.getCount();
	}

	private <P extends ParaObject> Map<String, Object> find(String queryType, MultivaluedMap<String, String> params) {
		Map<String, Object> map = new HashMap<String, Object>();
		if (params != null && !params.isEmpty()) {
			String qType = StringUtils.isBlank(queryType) ? "" : "/".concat(queryType);
			return getEntity(invokeGet("search".concat(qType), params), Map.class);
		} else {
			map.put("items", new ArrayList<P>());
			map.put("totalHits", 0);
		}
		return map;
	}

	/////////////////////////////////////////////
	//				 LINKS
	/////////////////////////////////////////////

	/**
	 * Count the total number of links between this object and another type of object.
	 * @param type2 the other type of object
	 * @param obj the object to execute this method on
	 * @return the number of links for the given object
	 */
	@SuppressWarnings("unchecked")
	public Long countLinks(ParaObject obj, String type2) {
		if (obj == null || obj.getId() == null || type2 == null) {
			return 0L;
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("count", "true");
		Pager pager = new Pager();
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
		getItems((Map<String, Object>) getEntity(invokeGet(url, params), Map.class), pager);
		return pager.getCount();
	}

	/**
	 * Returns all objects linked to the given one. Only applicable to many-to-many relationships.
	 * @param <P> type of linked objects
	 * @param type2 type of linked objects to search for
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of linked objects
	 */
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> getLinkedObjects(ParaObject obj, String type2, Pager... pager) {
		ArrayList<P> list = new ArrayList<P>();
		if (obj == null || obj.getId() == null || type2 == null) {
			return list;
		}
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
		return getItems((Map<String, Object>) getEntity(invokeGet(url, null), Map.class), pager);
	}

	/**
	 * Checks if this object is linked to another.
	 * @param type2 the other type
	 * @param id2 the other id
	 * @param obj the object to execute this method on
	 * @return true if the two are linked
	 */
	public boolean isLinked(ParaObject obj, String type2, String id2) {
		if (obj == null || obj.getId() == null || type2 == null || id2 == null) {
			return false;
		}
		String url = Utils.formatMessage("{0}/links/{1}/{2}", obj.getObjectURI(), type2, id2);
		return getEntity(invokeGet(url, null), Boolean.class);
	}

	/**
	 * Checks if a given object is linked to this one.
	 * @param toObj the other object
	 * @param obj the object to execute this method on
	 * @return true if linked
	 */
	public boolean isLinked(ParaObject obj, ParaObject toObj) {
		if (obj == null || obj.getId() == null || toObj == null || toObj.getId() == null) {
			return false;
		}
		return isLinked(obj, toObj.getType(), toObj.getId());
	}

	/**
	 * Links an object to this one in a many-to-many relationship.
	 * Only a link is created. Objects are left untouched.
	 * The type of the second object is automatically determined on read.
	 * @param id2 link to the object with this id
	 * @param obj the object to execute this method on
	 * @return the id of the {@link com.erudika.para.core.Linker} object that is created
	 */
	public String link(ParaObject obj, String id2) {
		if (obj == null || obj.getId() == null || id2 == null) {
			return null;
		}
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), id2);
		return getEntity(invokePost(url, null), String.class);
	}

	/**
	 * Unlinks an object from this one.
	 * Only a link is deleted. Objects are left untouched.
	 * @param type2 the other type
	 * @param obj the object to execute this method on
	 * @param id2 the other id
	 */
	public void unlink(ParaObject obj, String type2, String id2) {
		if (obj == null || obj.getId() == null || type2 == null || id2 == null) {
			return;
		}
		String url = Utils.formatMessage("{0}/links/{1}/{2}", obj.getObjectURI(), type2, id2);
		invokeDelete(url, null);
	}

	/**
	 * Unlinks all objects that are linked to this one.
	 * @param obj the object to execute this method on
	 * Deletes all {@link com.erudika.para.core.Linker} objects.
	 * Only the links are deleted. Objects are left untouched.
	 */
	public void unlinkAll(ParaObject obj) {
		if (obj == null || obj.getId() == null) {
			return;
		}
		String url = Utils.formatMessage("{0}/links", obj.getObjectURI());
		invokeDelete(url, null);
	}

	/**
	 * Count the total number of child objects for this object.
	 * @param type2 the type of the other object
	 * @param obj the object to execute this method on
	 * @return the number of links
	 */
	@SuppressWarnings("unchecked")
	public Long countChildren(ParaObject obj, String type2) {
		if (obj == null || obj.getId() == null || type2 == null) {
			return 0L;
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("count", "true");
		params.putSingle("childrenonly", "true");
		Pager pager = new Pager();
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
		getItems((Map<String, Object>) getEntity(invokeGet(url, params), Map.class), pager);
		return pager.getCount();
	}

	/**
	 * Returns all child objects linked to this object.
	 * @param <P> the type of children
	 * @param type2 the type of children to look for
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of {@link ParaObject} in a one-to-many relationship with this object
	 */
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> getChildren(ParaObject obj, String type2, Pager... pager) {
		ArrayList<P> list = new ArrayList<P>();
		if (obj == null || obj.getId() == null || type2 == null) {
			return list;
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("childrenonly", "true");
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
		return getItems((Map<String, Object>) getEntity(invokeGet(url, params), Map.class), pager);
	}

	/**
	 * Returns all child objects linked to this object.
	 * @param <P> the type of children
	 * @param type2 the type of children to look for
	 * @param field the field name to use as filter
	 * @param term the field value to use as filter
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of {@link ParaObject} in a one-to-many relationship with this object
	 */
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> getChildren(ParaObject obj, String type2, String field, String term,
			Pager... pager) {
		ArrayList<P> list = new ArrayList<P>();
		if (obj == null || obj.getId() == null || type2 == null) {
			return list;
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("childrenonly", "true");
		params.putSingle("field", field);
		params.putSingle("term", term);
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
		return getItems((Map<String, Object>) getEntity(invokeGet(url, params), Map.class), pager);
	}

	/**
	 * Deletes all child objects permanently.
	 * @param obj the object to execute this method on
	 * @param type2 the children's type.
	 */
	public void deleteChildren(ParaObject obj, String type2) {
		if (obj == null || obj.getId() == null || type2 == null) {
			return;
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("childrenonly", "true");
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
		invokeDelete(url, params);
	}

	/////////////////////////////////////////////
	//				 UTILS
	/////////////////////////////////////////////

	/**
	 * Generates a new unique id.
	 * @return a new id
	 */
	public String newId() {
		String res = getEntity(invokeGet("utils/newid", null), String.class);
		return res != null ? res : "";
	}

	/**
	 * Returns the current timestamp.
	 * @return a long number
	 */
	public long getTimestamp() {
		Long res = getEntity(invokeGet("utils/timestamp", null), Long.class);
		return res != null ? res : 0L;
	}

	/**
	 * Formats a date in a specific format.
	 * @param format the date format
	 * @param loc the locale instance
	 * @return a formatted date
	 */
	public String formatDate(String format, Locale loc) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("format", format);
		params.putSingle("locale", loc == null ? null : loc.toString());
		return getEntity(invokeGet("utils/formatdate", params), String.class);
	}

	/**
	 * Converts spaces to dashes.
	 * @param str a string with spaces
	 * @param replaceWith a string to replace spaces with
	 * @return a string with dashes
	 */
	public String noSpaces(String str, String replaceWith) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("string", str);
		params.putSingle("replacement", replaceWith);
		return getEntity(invokeGet("utils/nospaces", params), String.class);
	}

	/**
	 * Strips all symbols, punctuation, whitespace and control chars from a string.
	 * @param str a dirty string
	 * @return a clean string
	 */
	public String stripAndTrim(String str) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("string", str);
		return getEntity(invokeGet("utils/nosymbols", params), String.class);
	}

	/**
	 * Converts Markdown to HTML
	 * @param markdownString Markdown
	 * @return HTML
	 */
	public String markdownToHtml(String markdownString) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("md", markdownString);
		return getEntity(invokeGet("utils/md2html", params), String.class);
	}

	/**
	 * Returns the number of minutes, hours, months elapsed for a time delta (milliseconds).
	 * @param delta the time delta between two events, in milliseconds
	 * @return a string like "5m", "1h"
	 */
	public String approximately(long delta) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
		params.putSingle("delta", Long.toString(delta));
		return getEntity(invokeGet("utils/timeago", params), String.class);
	}

	/////////////////////////////////////////////
	//				 MISC
	/////////////////////////////////////////////

	/**
	 * First-time setup - creates the root app and returns its credentials.
	 * @return a map of credentials
	 */
	Map<String, String> setup() {
		return getEntity(invokeGet("_setup", null), Map.class);
	}

	/**
	 * Generates a new set of access/secret keys.
	 * Old keys are discarded and invalid after this.
	 * @return a map of new credentials
	 */
	public Map<String, String> newKeys() {
		return getEntity(invokePost("_newkeys", null), Map.class);
	}

	/**
	 * Returns all registered types for this App.
	 * @return a map of plural-singular form of all the registered types.
	 */
	public Map<String, String> types() {
		return getEntity(invokeGet("_types", null), Map.class);
	}

	/**
	 * Returns a {@link com.erudika.para.core.User} or an
	 * {@link com.erudika.para.core.App} that is currently authenticated.
	 * @param <P> an App or User
	 * @return a {@link com.erudika.para.core.User} or an {@link com.erudika.para.core.App}
	 */
	public <P extends ParaObject> P me() {
		Map<String, Object> data = getEntity(invokeGet("_me", null), Map.class);
		return Utils.setAnnotatedFields(data);
	}
}
