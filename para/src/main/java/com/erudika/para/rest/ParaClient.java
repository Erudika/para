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
package com.erudika.para.rest;

import com.amazonaws.Request;
import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jetty.connector.JettyConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Java REST client.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class ParaClient {

	private static final Logger logger = LoggerFactory.getLogger(ParaClient.class);
	private static final String DEFAULT_ENDPOINT = "http://localhost:8080";
	private static final String DEFAULT_PATH = "/v1/";
	private static final String GET = HttpMethod.GET;
	private static final String PUT = HttpMethod.PUT;
	private static final String POST = HttpMethod.POST;
	private static final String DELETE = HttpMethod.DELETE;
	private final Signer signer = new Signer();
	private final Client client;
	private String endpoint;
	private String path;
	private String accessKey;
	private String secretKey;

	public ParaClient(String accessKey, String secretKey) {
		ClientConfig clientConfig = new ClientConfig();
		clientConfig.register(RestUtils.GenericExceptionMapper.class);
		clientConfig.register(new JacksonJsonProvider(Utils.getJsonMapper()));
		clientConfig.connectorProvider(new JettyConnectorProvider());
		this.client = ClientBuilder.newClient(clientConfig);
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
				return res.readEntity((Class<T>) type);
			} else if (res.getStatus() != Response.Status.NOT_FOUND.getStatusCode()
					&& res.getStatus() != Response.Status.NOT_MODIFIED.getStatusCode()
					&& res.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {
				Map<String, Object> error = res.readEntity(Map.class);
				if (error != null && error.containsKey("code")) {
					String msg = error.containsKey("message") ? (String) error.get("message") : "error";
					msg = msg.concat(" ").concat(Integer.toString((int) error.get("code")));
					throw new WebApplicationException(msg, (int) error.get("code"));
				}
			}
		}
		return null;
	}

	private Response invokeGet(String resourcePath, MultivaluedMap<String, String> params) {
		return invoke(GET, resourcePath, null, params, null);
	}

	private Response invokePost(String resourcePath, Entity<?> entity) {
		return invoke(POST, resourcePath, null, null, entity);
	}

	private Response invokePut(String resourcePath, Entity<?> entity) {
		return invoke(PUT, resourcePath, null, null, entity);
	}

	private Response invokeDelete(String resourcePath, MultivaluedMap<String, String> params) {
		return invoke(DELETE, resourcePath, null, params, null);
	}

	private Response invoke(String httpMethod, String resourcePath,
			Map<String, String> headers, MultivaluedMap<String, String> params, Entity<?> entity) {

		if (StringUtils.isBlank(accessKey) || StringUtils.isBlank(secretKey)) {
			logger.warn("Blank access key or secret key!");
			accessKey = "";
			secretKey = "";
		}

		if (httpMethod == null) {
			httpMethod = GET;
		}
		if (resourcePath == null) {
			resourcePath = "";
		} else if (resourcePath.startsWith("/")) {
			resourcePath = resourcePath.substring(1);
		}

		String reqPath = getApiPath() + resourcePath;
		WebTarget target = client.target(getEndpoint()).path(reqPath);
		InputStream in = null;
		Entity<?> jsonPayload = null;
		Map<String, String> sigParams = new HashMap<String, String>();

		if (params != null) {
			for (Map.Entry<String, List<String>> param : params.entrySet()) {
				String key = param.getKey();
				List<String> value = param.getValue();
				if (value != null && !value.isEmpty() && value.get(0) != null) {
					target = target.queryParam(key, value.toArray());
					sigParams.put(key, value.get(0));
				}
			}
		}

		Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON);

		if (headers != null) {
			for (Map.Entry<String, String> header : headers.entrySet()) {
				builder.header(header.getKey(), header.getValue());
			}
		}

		if (entity != null) {
			try {
				byte[] entt = Utils.getJsonWriter().writeValueAsBytes(entity.getEntity());
				in = new BufferedInputStream(new ByteArrayInputStream(entt));
				jsonPayload = Entity.json(new String(entt, Config.DEFAULT_ENCODING));
			} catch (IOException ex) {
				logger.error(null, ex);
			}
		}

		Request<?> signed = signer.sign(httpMethod, getEndpoint(), reqPath, headers, sigParams, in, accessKey, secretKey);

		builder.header(HttpHeaders.AUTHORIZATION, signed.getHeaders().get(HttpHeaders.AUTHORIZATION)).
				header("X-Amz-Date", signed.getHeaders().get("X-Amz-Date"));

		if (jsonPayload != null) {
			return builder.method(httpMethod, jsonPayload);
		} else {
			return builder.method(httpMethod);
		}
	}

	private MultivaluedMap<String, String> pagerToParams(Pager... pager) {
		MultivaluedMap<String, String> map = null;
		if (pager != null && pager.length > 0) {
			map = new MultivaluedHashMap<String, String>();
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
	private <P extends ParaObject> List<P> getItems(Map<String, Object> result, Pager... pager) {
		if (result != null && result.containsKey("items")) {
			if (pager != null && pager.length > 0 && pager[0] != null && result.containsKey("totalHits")) {
				pager[0].setCount(Integer.valueOf((int) result.get("totalHits")).longValue());
			}
			return (List<P>) result.get("items");
		}
		return new ArrayList<P>();
	}

	private <P extends ParaObject> List<P> getItemsFromMaps(List<Map<String, Object>> list) {
		// this isn't very efficient but there's no way to know what type of objects we're reading
		ArrayList<P> objects = new ArrayList<P>();
		if (list != null) {
			for (Map<String, Object> map : list) {
				P p = Utils.setAnnotatedFields(map);
				if (p != null) {
					objects.add(p);
				}
			}
		}
		return objects;
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
		if (type == null || id == null) {
			return null;
		}
		return getEntity(invokeGet(type.concat("/").concat(id), null), Utils.toClass(type));
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
			return new ArrayList<P>();
		}
		List<Map<String, Object>> list = getEntity(invokePost("_batch", Entity.json(objects)), List.class);
		return getItemsFromMaps(list);
	}

	/**
	 * Retrieves multiple objects from the data store.
	 * @param <P> the type of object
	 * @param keys a list of object ids
	 * @return a list of objects
	 */
	public <P extends ParaObject> List<P> readAll(List<String> keys) {
		if (keys == null || keys.isEmpty()) {
			return new ArrayList<P>();
		}
		MultivaluedMap<String, String> ids = new MultivaluedHashMap<String, String>();
		ids.put("ids", keys);
		List<Map<String, Object>> list = getEntity(invokeGet("_batch", ids), List.class);
		return getItemsFromMaps(list);
	}

	/**
	 * Updates multiple objects.
	 * @param <P> the type of object
	 * @param objects the objects to update
	 * @return a list of objects
	 */
	public <P extends ParaObject> List<P> updateAll(List<P> objects) {
		if (objects == null || objects.isEmpty()) {
			return new ArrayList<P>();
		}
		List<Map<String, Object>> list = getEntity(invokePut("_batch", Entity.json(objects)), List.class);
		return getItemsFromMaps(list);
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
	 * @param <P> the type of object
	 * @param type the type of objects to search for
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects
	 */
	public <P extends ParaObject> List<P> list(String type, Pager... pager) {
		if (type == null) {
			return new ArrayList<P>();
		}
		return getItems(getEntity(invokeGet(type, pagerToParams(pager)), Map.class), pager);
	}

	/////////////////////////////////////////////
	//				 SEARCH
	/////////////////////////////////////////////



	/////////////////////////////////////////////
	//				 LINKS
	/////////////////////////////////////////////



	/////////////////////////////////////////////
	//				 UTILS
	/////////////////////////////////////////////


	public long getTimestamp() {
		Long res = getEntity(invokeGet("utils/timestamp", null), Long.class);
		return res != null ? res : 0L;
	}

	/////////////////////////////////////////////
	//				 MISC
	/////////////////////////////////////////////

	public Map<String, String> setup() {
		return getEntity(invokeGet("setup", null), Map.class);
	}

	public void close() {
		if (client != null) {
			client.close();
		}
	}
}
