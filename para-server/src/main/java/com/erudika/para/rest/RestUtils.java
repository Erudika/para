/*
 * Copyright 2013-2017 Erudika. https://erudika.com
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

import com.erudika.para.Para;
import com.erudika.para.annotations.Locked;
import com.erudika.para.core.App;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.User;
import com.erudika.para.security.SecurityUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import com.erudika.para.validation.ValidationUtils;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A few helper methods for handling REST requests and responses.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@SuppressWarnings("unchecked")
public final class RestUtils {

	private static final Logger logger = LoggerFactory.getLogger(RestUtils.class);

	private RestUtils() { }

	/////////////////////////////////////////////
	//	    	 REST REQUEST UTILS
	/////////////////////////////////////////////

	/**
	 * Extracts the access key from a request. It can be a header or a parameter.
	 * @param request a request
	 * @return the access key
	 */
	public static String extractAccessKey(HttpServletRequest request) {
		if (request == null) {
			return "";
		}
		String accessKey = "";
		String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
		boolean isAnonymousRequest = isAnonymousRequest(request);
		if (StringUtils.isBlank(auth)) {
			auth = request.getParameter("X-Amz-Credential");
			if (!StringUtils.isBlank(auth)) {
				accessKey = StringUtils.substringBetween(auth, "=", "/");
			}
		} else {
			if (isAnonymousRequest) {
				accessKey = StringUtils.substringAfter(auth, "Anonymous").trim();
			} else {
				accessKey = StringUtils.substringBetween(auth, "=", "/");
			}
		}
		if (isAnonymousRequest && StringUtils.isBlank(accessKey)) {
			// try to get access key from parameter
			accessKey = request.getParameter("accessKey");
		}
		return accessKey;
	}

	/**
	 * Check if Authorization header starts with 'Anonymous'. Used for guest access.
	 * @param request a request
	 * @return true if user is unauthenticated
	 */
	public static boolean isAnonymousRequest(HttpServletRequest request) {
		return request != null &&
				(StringUtils.startsWith(request.getHeader(HttpHeaders.AUTHORIZATION), "Anonymous") ||
					StringUtils.isBlank(request.getHeader(HttpHeaders.AUTHORIZATION)));
	}

	/**
	 * Extracts the date field from a request. It can be a header or a parameter.
	 * @param request a request
	 * @return the date
	 */
	public static String extractDate(HttpServletRequest request) {
		if (request == null) {
			return "";
		}
		String date = request.getHeader("X-Amz-Date");
		if (StringUtils.isBlank(date)) {
			return request.getParameter("X-Amz-Date");
		} else {
			return date;
		}
	}

	/**
	 * Extracts the resource name, for example '/v1/_resource/path'
	 * returns '_resource/path'.
	 * @param request a request
	 * @return the resource path
	 */
	public static String extractResourcePath(HttpServletRequest request) {
		if (request == null || request.getRequestURI().length() <= 3) {
			return "";
		}
		// get request path, strip first slash '/'
		String uri = request.getRequestURI().substring(1);
		// skip to the end of API version prefix '/v1/'
		int start = uri.indexOf('/');

		if (start >= 0 && start + 1 < uri.length()) {
			return uri.substring(start + 1);
		} else {
			return "";
		}
	}

	/**
	 * Reads an object from a given resource path.
	 * Assumes that the "id" is located after the first slash "/" like this: {type}/{id}/...
	 * @param appid app id
	 * @param path resource path
	 * @return the object found at this path or null
	 */
	public static ParaObject readResourcePath(String appid, String path) {
		if (StringUtils.isBlank(appid) || StringUtils.isBlank(path) || !path.contains("/")) {
			return null;
		}
		try {
			URI uri = new URI(path);
			if (path.length() > 1) {
				path = path.startsWith("/") ? uri.getPath().substring(1) : uri.getPath();
				String[] parts = path.split("/");
				String id;
				if (parts.length == 1) {
					id = parts[0];	// case: {id}
				} else if (parts.length >= 2) {
					id = parts[1];	// case: {type}/{id}/...
				} else {
					return null;
				}
				return Para.getDAO().read(appid, id);
			}
		} catch (Exception e) {
			logger.debug("Invalid resource path {}: {}", path, e);
		}
		return null;
	}

	/**
	 * Returns the current authenticated {@link App} object.
	 * @return an App object or null
	 */
	public static App getPrincipalApp() {
		App app = SecurityUtils.getAuthenticatedApp();
		User user = SecurityUtils.getAuthenticatedUser();
		if (app != null) {
			return app;
		} else if (user != null) {
			return Para.getDAO().read(Config.APP_NAME_NS, App.id(user.getAppid()));
		}
		logger.info("Unauthenticated request - returning root App: {}", Config.APP_NAME_NS);
		return Para.getDAO().read(Config.APP_NAME_NS, App.id(Config.APP_NAME_NS));
	}

	/**
	 * Returns a Response with the entity object inside it and 200 status code.
	 * If there was and error the status code is different than 200.
	 *
	 * @param is the entity input stream
	 * @param type the type to convert the entity into, for example a Map. If null, this returns the InputStream.
	 * @return response with 200 or error status
	 */
	public static Response getEntity(InputStream is, Class<?> type) {
		Object entity;
		try {
			if (is != null && is.available() > 0) {
				if (is.available() > Config.MAX_ENTITY_SIZE_BYTES) {
					return getStatusResponse(Response.Status.BAD_REQUEST,
							"Request is too large - the maximum is " +
							(Config.MAX_ENTITY_SIZE_BYTES / 1024) + " KB.");
				}
				if (type == null) {
					entity = is;
				} else {
					entity = ParaObjectUtils.getJsonReader(type).readValue(is);
				}
			} else {
				return getStatusResponse(Response.Status.BAD_REQUEST, "Missing request body.");
			}
		} catch (JsonMappingException e) {
			return getStatusResponse(Response.Status.BAD_REQUEST, e.getMessage());
		} catch (JsonParseException e) {
			return getStatusResponse(Response.Status.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			logger.error(null, e);
			return getStatusResponse(Response.Status.INTERNAL_SERVER_ERROR, e.toString());
		}

		return Response.ok(entity).build();
	}

	/**
	 * An app can edit itself or delete itself. It can't read, edit, overwrite or delete other apps,
	 * unless it is the root app.
	 * @param app an app
	 * @param object another object
	 * @return true if app passes the check
	 */
	private static boolean checkImplicitAppPermissions(App app, ParaObject object) {
		if (app != null && object != null) {
			return !app.getType().equals(object.getType()) || app.getId().equals(object.getId()) || app.isRootApp();
		}
		return false;
	}

	/**
	 * Reads the bytes from an InputStream.
	 * @param in an InputStream
	 * @return byte[]
	 */
	public static byte[] readEntityBytes(InputStream in) {
		byte[] jsonEntity = null;
		try {
			if (in != null && in.available() > 0) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				byte[] buf = new byte[1024];
				int length;
				while ((length = in.read(buf)) > 0) {
					baos.write(buf, 0, length);
				}
				jsonEntity = baos.toByteArray();
			} else {
				jsonEntity = new byte[0];
			}
		} catch (IOException ex) {
			logger.error(null, ex);
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (IOException ex) {
				logger.error(null, ex);
			}
		}
		return jsonEntity;
	}

	/**
	 * Process voting request and create vote object.
	 * @param object the object to cast vote on
	 * @param entity request entity data
	 * @return status codetrue if vote was successful
	 */
	public static Response getVotingResponse(ParaObject object, Map<String, Object> entity) {
		boolean voteSuccess = false;
		if (object != null && entity != null) {
			String upvoterId = (String) entity.get("_voteup");
			String downvoterId = (String) entity.get("_votedown");
			if (!StringUtils.isBlank(upvoterId)) {
				voteSuccess = object.voteUp(upvoterId);
			} else if (!StringUtils.isBlank(downvoterId)) {
				voteSuccess = object.voteDown(downvoterId);
			}
			if (voteSuccess) {
				object.update();
			}
		}
		return Response.ok(voteSuccess).build();
	}

	/////////////////////////////////////////////
	//		CORE REST RESPONSE HANDLERS
	/////////////////////////////////////////////

	/**
	 * Read response as JSON.
	 * @param app the app object
	 * @param content the object that was read
	 * @return status code 200 or 404
	 */
	public static Response getReadResponse(App app, ParaObject content) {
		// app can't modify other apps except itself
		if (app != null && content != null && checkImplicitAppPermissions(app, content)) {
			return Response.ok(content).build();
		}
		return getStatusResponse(Response.Status.NOT_FOUND);
	}

	/**
	 * Create response as JSON.
	 * @param type type of the object to create
	 * @param is entity input stream
	 * @param app the app object
	 * @return a status code 201 or 400
	 */
	public static Response getCreateResponse(App app, String type, InputStream is) {
		ParaObject content;
		Response entityRes = getEntity(is, Map.class);
		if (entityRes.getStatusInfo() == Response.Status.OK) {
			Map<String, Object> newContent = (Map<String, Object>) entityRes.getEntity();
			if (!StringUtils.isBlank(type)) {
				newContent.put(Config._TYPE, type);
			}
			content = ParaObjectUtils.setAnnotatedFields(newContent);
			if (app != null && content != null && !app.getType().equals(type)) {
				content.setAppid(app.getAppIdentifier());
				int typesCount = app.getDatatypes().size();
				app.addDatatypes(content);
				// The reason why we do two validation passes is because we want to return
				// the errors through the API and notify the end user.
				// This is the primary validation pass (validates not only core POJOS but also user defined objects).
				String[] errors = ValidationUtils.validateObject(app, content);
				if (errors.length == 0) {
					// Secondary validation pass called here. Object is validated again before being created
					// See: IndexAndCacheAspect.java
					String id = content.create();
					if (id != null) {
						// new type added so update app object
						if (typesCount < app.getDatatypes().size()) {
							app.update();
						}
						return Response.created(URI.create(Utils.urlEncode(content.getObjectURI()))).
								entity(content).build();
					}
				}
				return getStatusResponse(Response.Status.BAD_REQUEST, errors);
			}
			return getStatusResponse(Response.Status.BAD_REQUEST, "Failed to create object.");
		}
		return entityRes;
	}

	/**
	 * Overwrite response as JSON.
	 * @param id the object id
	 * @param type type of the object to create
	 * @param is entity input stream
	 * @param app the app object
	 * @return a status code 200 or 400
	 */
	public static Response getOverwriteResponse(App app, String id, String type, InputStream is) {
		ParaObject content;
		Response entityRes = getEntity(is, Map.class);
		if (entityRes.getStatusInfo() == Response.Status.OK) {
			Map<String, Object> newContent = (Map<String, Object>) entityRes.getEntity();
			if (!StringUtils.isBlank(type)) {
				newContent.put(Config._TYPE, type);
			}
			content = ParaObjectUtils.setAnnotatedFields(newContent);
			if (app != null && content != null && !StringUtils.isBlank(id) && !app.getType().equals(type)) {
				content.setType(type);
				content.setAppid(app.getAppIdentifier());
				content.setId(id);
				int typesCount = app.getDatatypes().size();
				app.addDatatypes(content);
				// The reason why we do two validation passes is because we want to return
				// the errors through the API and notify the end user.
				// This is the primary validation pass (validates not only core POJOS but also user defined objects).
				String[] errors = ValidationUtils.validateObject(app, content);
				if (errors.length == 0) {
					// Secondary validation pass called here. Object is validated again before being created
					// See: IndexAndCacheAspect.java

					CoreUtils.getInstance().overwrite(app.getAppIdentifier(), content);
					// new type added so update app object
					if (typesCount < app.getDatatypes().size()) {
						app.update();
					}
					return Response.ok(content).build();
				}
				return getStatusResponse(Response.Status.BAD_REQUEST, errors);
			}
			return getStatusResponse(Response.Status.BAD_REQUEST, "Failed to overwrite object.");
		}
		return entityRes;
	}

	/**
	 * Update response as JSON.
	 * @param object object to validate and update
	 * @param is entity input stream
	 * @param app the app object
	 * @return a status code 200 or 400 or 404
	 */
	public static Response getUpdateResponse(App app, ParaObject object, InputStream is) {
		if (app != null && object != null) {
			Map<String, Object> newContent;
			Response entityRes = getEntity(is, Map.class);
			String[] errors = {};
			if (entityRes.getStatusInfo() == Response.Status.OK) {
				newContent = (Map<String, Object>) entityRes.getEntity();
			} else {
				return entityRes;
			}
			object.setAppid(app.getAppIdentifier());
			if (newContent.containsKey("_voteup") || newContent.containsKey("_votedown")) {
				return getVotingResponse(object, newContent);
			} else {
				ParaObjectUtils.setAnnotatedFields(object, newContent, Locked.class);
				// app can't modify other apps except itself
				if (checkImplicitAppPermissions(app, object)) {
					// This is the primary validation pass (validates not only core POJOS but also user defined objects).
					errors = ValidationUtils.validateObject(app, object);
					if (errors.length == 0) {
						// Secondary validation pass: object is validated again before being updated
						object.update();
						return Response.ok(object).build();
					}
				}
			}
			return getStatusResponse(Response.Status.BAD_REQUEST, errors);
		}
		return getStatusResponse(Response.Status.NOT_FOUND);
	}

	/**
	 * Delete response as JSON.
	 * @param content the object to delete
	 * @param app the current App object
	 * @return a status code 200 or 400
	 */
	public static Response getDeleteResponse(App app, ParaObject content) {
		if (app != null && content != null && content.getId() != null && content.getAppid() != null) {
			if (checkImplicitAppPermissions(app, content)) {
				content.setAppid(app.getAppIdentifier());
				content.delete();
				return Response.ok().build();
			}
			return getStatusResponse(Response.Status.BAD_REQUEST);
		}
		return getStatusResponse(Response.Status.NOT_FOUND);
	}

	/**
	 * Batch read response as JSON.
	 * @param app the current App object
	 * @param ids list of ids
	 * @return status code 200 or 400
	 */
	public static Response getBatchReadResponse(App app, List<String> ids) {
		if (app != null && ids != null && !ids.isEmpty()) {
			return Response.ok(Para.getDAO().readAll(app.getAppIdentifier(), ids, true).values()).build();
		} else {
			return getStatusResponse(Response.Status.BAD_REQUEST, "Missing ids.");
		}
	}

	/**
	 * Batch create response as JSON.
	 * @param app the current App object
	 * @param is entity input stream
	 * @return a status code 200 or 400
	 */
	public static Response getBatchCreateResponse(final App app, InputStream is) {
		if (app != null) {
			final LinkedList<ParaObject> newObjects = new LinkedList<ParaObject>();
			Response entityRes = getEntity(is, List.class);
			if (entityRes.getStatusInfo() == Response.Status.OK) {
				List<Map<String, Object>> items = (List<Map<String, Object>>) entityRes.getEntity();
				for (Map<String, Object> object : items) {
					// can't create multiple apps in batch
					if (!app.getType().equals(object.get(Config._TYPE))) {
						ParaObject pobj = ParaObjectUtils.setAnnotatedFields(object);
						if (pobj != null && ValidationUtils.isValidObject(pobj)) {
							pobj.setAppid(app.getAppIdentifier());
							newObjects.add(pobj);
						}
					}
				}

				Para.getDAO().createAll(app.getAppIdentifier(), newObjects);

				Para.asyncExecute(new Runnable() {
					public void run() {
						int typesCount = app.getDatatypes().size();
						app.addDatatypes(newObjects.toArray(new ParaObject[0]));
						if (typesCount < app.getDatatypes().size()) {
							app.update();
						}
					}
				});
			} else {
				return entityRes;
			}
			return Response.ok(newObjects).build();
		} else {
			return getStatusResponse(Response.Status.BAD_REQUEST);
		}
	}

	/**
	 * Batch update response as JSON.
	 * @param app the current App object
	 * @param oldObjects a list of old objects read from DB
	 * @param newProperties a list of new object properties to be updated
	 * @return a status code 200 or 400
	 */
	public static Response getBatchUpdateResponse(App app, Map<String, ParaObject> oldObjects,
			List<Map<String, Object>> newProperties) {
		if (app != null && oldObjects != null && newProperties != null) {
			LinkedList<ParaObject> updatedObjects = new LinkedList<ParaObject>();
			for (Map<String, Object> newProps : newProperties) {
				if (newProps != null && newProps.containsKey(Config._ID)) {
					ParaObject oldObject = oldObjects.get((String) newProps.get(Config._ID));
					// updating apps in batch is not allowed
					if (oldObject != null && checkImplicitAppPermissions(app, oldObject)) {
						ParaObject updatedObject = ParaObjectUtils.setAnnotatedFields(oldObject, newProps, Locked.class);
						if (ValidationUtils.isValidObject(app, updatedObject)) {
							updatedObject.setAppid(app.getAppIdentifier());
							updatedObjects.add(updatedObject);
						}
					}
				}
			}
			Para.getDAO().updateAll(app.getAppIdentifier(), updatedObjects);
			return Response.ok(updatedObjects).build();
		} else {
			return getStatusResponse(Response.Status.BAD_REQUEST);
		}
	}

	/**
	 * Batch delete response as JSON.
	 * @param app the current App object
	 * @param ids list of ids to delete
	 * @return a status code 200 or 400
	 */
	public static Response getBatchDeleteResponse(App app, List<String> ids) {
		LinkedList<ParaObject> objects = new LinkedList<ParaObject>();
		if (ids != null && !ids.isEmpty()) {
			if (ids.size() <= Config.MAX_ITEMS_PER_PAGE) {
				for (ParaObject pobj : Para.getDAO().readAll(app.getAppIdentifier(), ids, false).values()) {
					if (pobj != null && pobj.getId() != null && pobj.getType() != null) {
						// deleting apps in batch is not allowed
						if (!app.getType().equals(pobj.getType())) {
							objects.add(pobj);
						}
					}
				}
				Para.getDAO().deleteAll(app.getAppIdentifier(), objects);
			} else {
				return getStatusResponse(Response.Status.BAD_REQUEST,
						"Limit reached. Maximum number of items to delete is " + Config.MAX_ITEMS_PER_PAGE);
			}
		} else {
			return getStatusResponse(Response.Status.BAD_REQUEST, "Missing ids.");
		}
		return Response.ok().build();
	}

	/////////////////////////////////////////////
	//		 LINKS REST RESPONSE HANDLERS
	/////////////////////////////////////////////

	/**
	 * Handles requests to search for linked objects.
	 * @param pobj the object to operate on
	 * @param id2 the id of the second object (optional)
	 * @param type2 the type of the second object
	 * @param params query parameters
	 * @param pager a {@link Pager} object
	 * @param childrenOnly find only directly linked objects in 1-to-many relationship
	 * @return a Response
	 */
	public static Response readLinksHandler(ParaObject pobj, String id2, String type2,
			MultivaluedMap<String, String> params, Pager pager, boolean childrenOnly) {
		Map<String, Object> result = new HashMap<String, Object>();
		String query = params.getFirst("q");
		if (type2 != null) {
			if (id2 != null) {
				return Response.ok(pobj.isLinked(type2, id2), MediaType.TEXT_PLAIN_TYPE).build();
			} else {
				List<ParaObject> items = new ArrayList<ParaObject>();
				if (childrenOnly) {
					if (params.containsKey("count")) {
						pager.setCount(pobj.countChildren(type2));
					} else {
						if (params.containsKey("field") && params.containsKey("term")) {
							items = pobj.getChildren(type2, params.getFirst("field"), params.getFirst("term"), pager);
						} else {
							if (StringUtils.isBlank(query)) {
								items = pobj.getChildren(type2, pager);
							} else {
								items = pobj.findChildren(type2, query, pager);
							}
						}
					}
				} else {
					if (params.containsKey("count")) {
						pager.setCount(pobj.countLinks(type2));
					} else {
						if (StringUtils.isBlank(query)) {
							items = pobj.getLinkedObjects(type2, pager);
						} else {
							items = pobj.findLinkedObjects(type2, params.getFirst("field"), query, pager);
						}
					}
				}
				result.put("items", items);
				result.put("totalHits", pager.getCount());
				return Response.ok(result).build();
			}
		} else {
			return getStatusResponse(Response.Status.BAD_REQUEST, "Parameter 'type' is missing.");
		}
	}

	/**
	 * Handles requests to delete linked objects.
	 * @param pobj the object to operate on
	 * @param id2 the id of the second object (optional)
	 * @param type2 the type of the second object
	 * @param childrenOnly find only directly linked objects in 1-to-many relationship
	 * @return a Response
	 */
	public static Response deleteLinksHandler(ParaObject pobj, String id2, String type2, boolean childrenOnly) {
		if (type2 == null && id2 == null) {
			pobj.unlinkAll();
		} else if (type2 != null) {
			if (id2 != null) {
				pobj.unlink(type2, id2);
			} else if (childrenOnly) {
				pobj.deleteChildren(type2);
			}
		}
		return Response.ok().build();
	}

	/**
	 * Handles requests to link an object to other objects.
	 * @param pobj the object to operate on
	 * @param id2 the id of the second object (optional)
	 * @return a Response
	 */
	public static Response createLinksHandler(ParaObject pobj, String id2) {
		if (id2 != null && pobj != null) {
			String linkid = pobj.link(id2);
			if (linkid == null) {
				return getStatusResponse(Response.Status.BAD_REQUEST, "Failed to create link.");
			} else {
				return Response.ok(linkid, MediaType.TEXT_PLAIN_TYPE).build();
			}
		} else {
			return getStatusResponse(Response.Status.BAD_REQUEST, "Parameters 'type' and 'id' are missing.");
		}
	}

	/////////////////////////////////////////////
	//			SEARCH RESPONSE HANDLERS
	/////////////////////////////////////////////

	static <P extends ParaObject> Map<String, Object> buildQueryAndSearch(App app, String queryType,
			MultivaluedMap<String, String> params, String typeOverride) {
		String query = paramOrDefault(params, "q", "*");
		String appid = app.getAppIdentifier();
		Pager pager = getPagerFromParams(params);
		List<P> items = Collections.emptyList();
		queryType = StringUtils.isBlank(queryType) ? params.getFirst("querytype") : queryType;

		String type;
		if (!StringUtils.isBlank(typeOverride) && !"search".equals(typeOverride)) {
			type = typeOverride;
		} else {
			type = params.getFirst(Config._TYPE);
		}

		if ("id".equals(queryType)) {
			items = findByIdQuery(params, appid, pager);
		} else if ("ids".equals(queryType)) {
			items = Para.getSearch().findByIds(appid, params.get("ids"));
			pager.setCount(items.size());
		} else if ("nested".equals(queryType)) {
			items = Para.getSearch().findNestedQuery(appid, type, params.getFirst("field"), query, pager);
		} else if ("nearby".equals(queryType)) {
			items = findNearbyQuery(params, appid, type, query, pager);
		} else if ("prefix".equals(queryType)) {
			items = Para.getSearch().findPrefix(appid, type, params.getFirst("field"), params.getFirst("prefix"), pager);
		} else if ("similar".equals(queryType)) {
			items = findSimilarQuery(params, appid, type, pager);
		} else if ("tagged".equals(queryType)) {
			items = findTaggedQuery(params, appid, type, pager);
		} else if ("in".equals(queryType)) {
			items = Para.getSearch().findTermInList(appid, type, params.getFirst("field"), params.get("terms"), pager);
		} else if ("terms".equals(queryType)) {
			items = findTermsQuery(params, pager, appid, type);
		} else if ("wildcard".equals(queryType)) {
			items = Para.getSearch().findWildcard(appid, type, params.getFirst("field"), query, pager);
		} else if ("count".equals(queryType)) {
			pager.setCount(Para.getSearch().getCount(appid, type));
		} else {
			items = Para.getSearch().findQuery(appid, type, query, pager);
		}

		Map<String, Object> result = new HashMap<String, Object>();
		result.put("items", items);
		result.put("page", pager.getPage());
		result.put("totalHits", pager.getCount());
		return result;
	}

	private static Pager getPagerFromParams(MultivaluedMap<String, String> params) {
		Pager pager = new Pager();
		pager.setPage(NumberUtils.toLong(params.getFirst("page"), 0));
		pager.setSortby(params.getFirst("sort"));
		pager.setDesc(Boolean.parseBoolean(paramOrDefault(params, "desc", "true")));
		pager.setLimit(NumberUtils.toInt(params.getFirst("limit"), pager.getLimit()));
		return pager;
	}

	private static <P extends ParaObject> List<P> findTermsQuery(MultivaluedMap<String, String> params,
			Pager pager, String appid, String type) {
		String matchAll = paramOrDefault(params, "matchall", "true");
		List<String> termsList = params.get("terms");
		if (termsList != null) {
			Map<String, String> terms = new HashMap<String, String>(termsList.size());
			for (String termTuple : termsList) {
				if (!StringUtils.isBlank(termTuple) && termTuple.contains(Config.SEPARATOR)) {
					String[] split = termTuple.split(Config.SEPARATOR, 2);
					terms.put(split[0], split[1]);
				}
			}
			if (params.containsKey("count")) {
				pager.setCount(Para.getSearch().getCount(appid, type, terms));
			} else {
				return Para.getSearch().findTerms(appid, type, terms, Boolean.parseBoolean(matchAll), pager);
			}
		}
		return Collections.emptyList();
	}

	private static <P extends ParaObject> List<P> findTaggedQuery(MultivaluedMap<String, String> params,
			String appid, String type, Pager pager) {
		List<String> tags = params.get("tags");
		String[] tagz = tags != null ? tags.toArray(new String[0]) : null;
		return Para.getSearch().findTagged(appid, type, tagz, pager);
	}

	private static <P extends ParaObject> List<P> findSimilarQuery(MultivaluedMap<String, String> params,
			String appid, String type, Pager pager) {
		List<String> fields = params.get("fields");
		String[] fieldz = (fields != null) ? fields.toArray(new String[0]) : null;
		return Para.getSearch().findSimilar(appid, type, params.getFirst("filterid"),
				fieldz, params.getFirst("like"), pager);
	}

	private static <P extends ParaObject> List<P> findNearbyQuery(MultivaluedMap<String, String> params,
			String appid, String type, String query, Pager pager) {
		String latlng = params.getFirst("latlng");
		if (StringUtils.contains(latlng, ",")) {
			String[] coords = latlng.split(",", 2);
			String rad = paramOrDefault(params, "radius", null);
			int radius = NumberUtils.toInt(rad, 10);
			double lat = NumberUtils.toDouble(coords[0], 0);
			double lng = NumberUtils.toDouble(coords[1], 0);
			return Para.getSearch().findNearby(appid, type, query, radius, lat, lng, pager);
		}
		return Collections.emptyList();
	}

	private static <P extends ParaObject> List<P> findByIdQuery(MultivaluedMap<String, String> params,
			String appid, Pager pager) {
		String id = paramOrDefault(params, Config._ID, null);
		P obj = Para.getSearch().findById(appid, id);
		if (obj != null) {
			pager.setCount(1);
			return Collections.singletonList(obj);
		}
		return Collections.emptyList();
	}

	private static String paramOrDefault(MultivaluedMap<String, String> params, String name, String defaultValue) {
		return params != null && params.containsKey(name) ? params.getFirst(name) : defaultValue;
	}

	/////////////////////////////////////////////
	//			MISC RESPONSE HANDLERS
	/////////////////////////////////////////////

	/**
	 * A generic JSON response handler.
	 * @param status status code
	 * @param messages zero or more errors
	 * @return a response as JSON
	 */
	public static Response getStatusResponse(Response.Status status, String... messages) {
		if (status == null) {
			return Response.status(Response.Status.BAD_REQUEST).build();
		}
		String msg = StringUtils.join(messages, ". ");
		if (StringUtils.isBlank(msg)) {
			msg = status.getReasonPhrase();
		}
		try {
			return GenericExceptionMapper.getExceptionResponse(status.getStatusCode(), msg);
		} catch (Exception ex) {
			logger.error(null, ex);
			return Response.status(Response.Status.BAD_REQUEST).build();
		}
	}

	/**
	 * A generic JSON response handler. Returns a message and response code.
	 * @param response the response to write to
	 * @param status status code
	 * @param message error message
	 */
	public static void returnStatusResponse(HttpServletResponse response, int status, String message) {
		if (response == null) {
			return;
		}
		PrintWriter out = null;
		try {
			response.setStatus(status);
			response.setContentType(MediaType.APPLICATION_JSON);
			out = response.getWriter();
			ParaObjectUtils.getJsonWriter().writeValue(out, getStatusResponse(Response.Status.
					fromStatusCode(status), message).getEntity());
		} catch (Exception ex) {
			logger.error(null, ex);
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	/**
	 * A generic JSON response returning an object. Status code is always {@code 200}.
	 * @param response the response to write to
	 * @param obj an object
	 */
	public static void returnObjectResponse(HttpServletResponse response, Object obj) {
		if (response == null) {
			return;
		}
		PrintWriter out = null;
		try {
			response.setStatus(HttpServletResponse.SC_OK);
			response.setContentType(MediaType.APPLICATION_JSON);
			out = response.getWriter();
			ParaObjectUtils.getJsonWriter().writeValue(out, obj);
		} catch (Exception ex) {
			logger.error(null, ex);
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	/**
	 * Returns the path parameter value.
	 * @param param a parameter name
	 * @param ctx ctx
	 * @return a value
	 */
	public static String pathParam(String param, ContainerRequestContext ctx) {
		return ctx.getUriInfo().getPathParameters().getFirst(param);
	}

	/**
	 * Returns the path parameters values.
	 * @param param a parameter name
	 * @param ctx ctx
	 * @return a list of parameter values
	 */
	public static List<String> pathParams(String param, ContainerRequestContext ctx) {
		return ctx.getUriInfo().getPathParameters().get(param);
	}

	/**
	 * Returns  the query parameter value.
	 * @param param a parameter name
	 * @param ctx ctx
	 * @return parameter value
	 */
	public static String queryParam(String param, ContainerRequestContext ctx) {
		return ctx.getUriInfo().getQueryParameters().getFirst(param);
	}

	/**
	 * Returns the query parameter values.
	 * @param param a parameter name
	 * @param ctx ctx
	 * @return a list of values
	 */
	public static List<String> queryParams(String param, ContainerRequestContext ctx) {
		return ctx.getUriInfo().getQueryParameters().get(param);
	}

	/**
	 * Returns true if parameter exists.
	 * @param param a parameter name
	 * @param ctx ctx
	 * @return true if parameter is set
	 */
	public static boolean hasQueryParam(String param, ContainerRequestContext ctx) {
		return ctx.getUriInfo().getQueryParameters().containsKey(param);
	}

}
