/*
 * Copyright 2013-2022 Erudika. https://erudika.com
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
package com.erudika.para.server.rest;

import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.User;
import com.erudika.para.core.Votable;
import com.erudika.para.core.annotations.Locked;
import com.erudika.para.core.metrics.Metrics;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Pager;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.utils.Utils;
import static com.erudika.para.core.validation.ValidationUtils.isValidObject;
import static com.erudika.para.core.validation.ValidationUtils.validateObject;
import static com.erudika.para.server.security.SecurityUtils.checkIfUserCanModifyObject;
import static com.erudika.para.server.security.SecurityUtils.checkImplicitAppPermissions;
import static com.erudika.para.server.security.SecurityUtils.getAuthenticatedUser;
import static com.erudika.para.server.security.SecurityUtils.getPrincipalApp;
import static com.erudika.para.server.security.SecurityUtils.isNotAnApp;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.math.NumberUtils;
import org.eclipse.jetty.http.BadMessageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

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
		try {
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
		} catch (BadMessageException e) {
			logger.error("Bad or invalid query: {} [{} {}?{}]", e.getMessage(), request.getMethod(),
					request.getServletPath(), request.getQueryString());
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
				(Strings.CS.startsWith(request.getHeader(HttpHeaders.AUTHORIZATION), "Anonymous") ||
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
			logger.debug("Invalid resource path {}:", path, e);
		}
		return null;
	}

	/**
	 * Convenience overload that reads an entity from the request stream without batch processing.
	 * @param is the raw request stream
	 * @param type the expected entity type
	 * @see #getEntity(java.io.InputStream, java.lang.Class, boolean)
	 * @return response with 200 or error status
	 */
	public static ResponseEntity<?> getEntity(InputStream is, Class<?> type) {
		return getEntity(is, type, false);
	}

	/**
	 * Returns a Response with the entity object inside it and 200 status code.
	 * If there was and error the status code is different than 200.
	 *
	 * @param is the entity input stream
	 * @param type the type to convert the entity into, for example a Map. If null, this returns the InputStream.
	 * @param batchMode if true, checks size of individual objects only, not size of whole batch.
	 * @return response with 200 or error status
	 */
	public static ResponseEntity<?> getEntity(InputStream is, Class<?> type, boolean batchMode) {
		Object entity;
		try {
			if (is != null) {
				int maxReqSize = Para.getConfig().maxEntitySizeBytes();
				int maxReqSizeKb = (maxReqSize / 1024);
				if (!batchMode && is.available() > maxReqSize) {
					return getStatusResponse(HttpStatus.BAD_REQUEST,
							"Request is too large - the maximum is " + maxReqSizeKb + " KB.");
				} else if (batchMode && is.available() > 100 * maxReqSize) {
					return getStatusResponse(HttpStatus.BAD_REQUEST, "Batch request is too large - "
							+ "the maximum total batch size is " + 100 * maxReqSizeKb + " KB.");
				}
				if (type == null) {
					entity = is;
				} else {
					if (batchMode && is.available() > maxReqSize) {
						JsonNode entityNode = ParaObjectUtils.getJsonReader(type).readTree(is);
						boolean tooLarge = false;
						if (entityNode.isArray()) {
							for (JsonNode i : entityNode) {
								if (calculateObjectSize(i) > maxReqSize) {
									tooLarge = true;
									break;
								}
							}
						}
						if (tooLarge) {
							return getStatusResponse(HttpStatus.BAD_REQUEST, "Batch request too large or "
									+ "containing items larger than the max. allowed size " + maxReqSizeKb + " KB.");
						}
						entity = ParaObjectUtils.getJsonReader(type).readValue(entityNode);
					} else {
						entity = ParaObjectUtils.getJsonReader(type).readValue(is);
					}
				}
			} else {
				return getStatusResponse(HttpStatus.BAD_REQUEST, "Missing request body.");
			}
		} catch (JsonMappingException e) {
			return getStatusResponse(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (JsonParseException e) {
			return getStatusResponse(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			logger.error(null, e);
			return getStatusResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.toString());
		}

		return ResponseEntity.ok(entity);
	}

	/**
	 * Process voting request and create vote object.
	 * @param object the object to cast vote on
	 * @param entity request entity data
	 * @return status codetrue if vote was successful
	 */
	public static ResponseEntity<?> getVotingResponse(ParaObject object, Map<String, Object> entity) {
		boolean voteSuccess = false;
		if (object != null && entity != null) {
			String upvoterId = (String) entity.get("_voteup");
			String downvoterId = (String) entity.get("_votedown");
			Integer lockAfter = (Integer) entity.get("_vote_locked_after");
			Integer expiresAfter = (Integer) entity.get("_vote_expires_after");
			if (!StringUtils.isBlank(upvoterId)) {
				if (lockAfter == null && expiresAfter == null) {
					voteSuccess = object.voteUp(upvoterId);
				} else {
					voteSuccess = CoreUtils.getInstance().
							vote(object, upvoterId, Votable.VoteValue.UP, expiresAfter, lockAfter);
				}
			} else if (!StringUtils.isBlank(downvoterId)) {
				if (lockAfter == null && expiresAfter == null) {
					voteSuccess = object.voteDown(downvoterId);
				} else {
					voteSuccess = CoreUtils.getInstance().
							vote(object, downvoterId, Votable.VoteValue.DOWN, expiresAfter, lockAfter);
				}
			}
			if (voteSuccess) {
				object.update();
			}
		}
		return ResponseEntity.ok(voteSuccess);
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
	public static ResponseEntity<?> getReadResponse(App app, ParaObject content) {
		try (Metrics.Context context = Metrics.time(app == null ? null : app.getAppid(),
				RestUtils.class, "crud", "read")) {
			// app can't modify other apps except itself
			if (app != null && content != null &&
					checkImplicitAppPermissions(app, content) && checkIfUserCanModifyObject(app, content)) {
				return ResponseEntity.ok(content);
			}
			return getStatusResponse(HttpStatus.NOT_FOUND);
		}
	}

	/**
	 * Create response as JSON.
	 * @param type type of the object to create
	 * @param is entity input stream
	 * @param app the app object
	 * @return a status code 201 or 400
	 */
	public static ResponseEntity<?> getCreateResponse(App app, String type, InputStream is) {
		try (Metrics.Context context = Metrics.time(app == null ? null : app.getAppid(),
				RestUtils.class, "crud", "create")) {
			ParaObject content;
			ResponseEntity<?> entityRes = getEntity(is, Map.class);
			if (entityRes.getStatusCode().is2xxSuccessful()) {
				Map<String, Object> newContent = (Map<String, Object>) entityRes.getBody();
				String declaredType = (String) newContent.get(Config._TYPE);
				if (!StringUtils.isBlank(type) && (StringUtils.isBlank(declaredType) || !type.startsWith(declaredType))) {
					newContent.put(Config._TYPE, type);
				}
				content = ParaObjectUtils.setAnnotatedFields(newContent);
				if (app != null && content != null && isNotAnApp(type)) {
					content.setAppid(app.getAppIdentifier());
					setCreatorid(app, content);
					// The reason why we do two validation passes is because we want to return
					// the errors through the API and notify the end user.
					// This is the primary validation pass (validates not only core POJOS but also user defined objects).
					String[] errors = validateObject(app, content);
					if (errors.length == 0) {
						// Secondary validation pass called here. Object is validated again before being created
						// See: IndexAndCacheAspect.java
						String id = content.create();
						if (id != null) {
							// new type added so update app object
							if (app.addDatatypes(content)) {
								CoreUtils.getInstance().overwrite(app);
							}
							return ResponseEntity.created(URI.create(Utils.urlEncode(content.getObjectURI()))).body(content);
						}
					}
					return getStatusResponse(HttpStatus.BAD_REQUEST, errors);
				}
				return getStatusResponse(HttpStatus.BAD_REQUEST, "Failed to create object.");
			}
			return entityRes;
		}
	}

	/**
	 * Overwrite response as JSON.
	 * @param id the object id
	 * @param type type of the object to create
	 * @param is entity input stream
	 * @param app the app object
	 * @return a status code 200 or 400
	 */
	public static ResponseEntity<?> getOverwriteResponse(App app, String id, String type, InputStream is) {
		try (Metrics.Context context = Metrics.time(app == null ? null : app.getAppid(),
				RestUtils.class, "crud", "overwrite")) {
			ParaObject content;
			ResponseEntity<?> entityRes = getEntity(is, Map.class);
			if (entityRes.getStatusCode().is2xxSuccessful()) {
				Map<String, Object> newContent = (Map<String, Object>) entityRes.getBody();
				if (!StringUtils.isBlank(type)) {
					newContent.put(Config._TYPE, type);
				}
				content = ParaObjectUtils.setAnnotatedFields(newContent);
				if (app != null && content != null && !StringUtils.isBlank(id) && isNotAnApp(type)) {
					content.setType(type);
					content.setAppid(app.getAppIdentifier());
					content.setId(id);
					setCreatorid(app, content);
					// The reason why we do two validation passes is because we want to return
					// the errors through the API and notify the end user.
					// This is the primary validation pass (validates not only core POJOS but also user defined objects).
					String[] errors = validateObject(app, content);
					if (errors.length == 0 && checkIfUserCanModifyObject(app, content)) {
						// Secondary validation pass called here. Object is validated again before being created
						// See: IndexAndCacheAspect.java
						CoreUtils.getInstance().overwrite(app.getAppIdentifier(), content);
						// new type added so update app object
						if (app.addDatatypes(content)) {
							CoreUtils.getInstance().overwrite(app);
						}
						return ResponseEntity.ok(content);
					}
					return getStatusResponse(HttpStatus.BAD_REQUEST, errors);
				}
				return getStatusResponse(HttpStatus.BAD_REQUEST, "Failed to overwrite object.");
			}
			return entityRes;
		}
	}

	/**
	 * Update response as JSON.
	 * @param object object to validate and update
	 * @param is entity input stream
	 * @param app the app object
	 * @return a status code 200 or 400 or 404
	 */
	public static ResponseEntity<?> getUpdateResponse(App app, ParaObject object, InputStream is) {
		try (Metrics.Context context = Metrics.time(app == null ? null : app.getAppid(),
				RestUtils.class, "crud", "update")) {
			if (app != null && object != null) {
				Map<String, Object> newContent;
				ResponseEntity<?> entityRes = getEntity(is, Map.class);
				String[] errors = {};
				if (entityRes.getStatusCode().is2xxSuccessful()) {
					newContent = (Map<String, Object>) entityRes.getBody();
				} else {
					return entityRes;
				}
				object.setAppid(isNotAnApp(object.getType()) ? app.getAppIdentifier() : app.getAppid());
				if (newContent.containsKey("_voteup") || newContent.containsKey("_votedown")) {
					return getVotingResponse(object, newContent);
				} else {
					ParaObjectUtils.setAnnotatedFields(object, newContent, Locked.class);
					// app can't modify other apps except itself
					if (checkImplicitAppPermissions(app, object)) {
						// This is the primary validation pass (validates not only core POJOS but also user defined objects).
						errors = validateObject(app, object);
						if (errors.length == 0 && checkIfUserCanModifyObject(app, object)) {
							// Secondary validation pass: object is validated again before being updated
							object.update();
							// check if update failed due to optimistic locking
							if (object.getVersion() == -1) {
								return getStatusResponse(HttpStatus.PRECONDITION_FAILED,
										"Update failed due to 'version' mismatch.");
							}
							// new type added so update app object
							if (app.addDatatypes(object)) {
								CoreUtils.getInstance().overwrite(app);
							}
							return ResponseEntity.ok(object);
						}
					}
				}
				return getStatusResponse(HttpStatus.BAD_REQUEST, errors);
			}
			return getStatusResponse(HttpStatus.NOT_FOUND);
		}
	}

	/**
	 * Delete response as JSON.
	 * @param content the object to delete
	 * @param app the current App object
	 * @return a status code 200 or 400
	 */
	public static ResponseEntity<?> getDeleteResponse(App app, ParaObject content) {
		try (Metrics.Context context = Metrics.time(app == null ? null : app.getAppid(),
				RestUtils.class, "crud", "delete")) {
			if (app != null && content != null && content.getId() != null && content.getAppid() != null) {
				if (checkImplicitAppPermissions(app, content) && checkIfUserCanModifyObject(app, content)) {
					content.setAppid(isNotAnApp(content.getType()) ? app.getAppIdentifier() : app.getAppid());
					content.delete();
					return ResponseEntity.ok().build();
				}
				return getStatusResponse(HttpStatus.BAD_REQUEST);
			}
			return getStatusResponse(HttpStatus.NOT_FOUND);
		}
	}

	/**
	 * Batch read response as JSON.
	 * @param app the current App object
	 * @param ids list of ids
	 * @return status code 200 or 400
	 */
	public static ResponseEntity<?> getBatchReadResponse(App app, List<String> ids) {
		try (Metrics.Context context = Metrics.time(app == null ? null : app.getAppid(),
				RestUtils.class, "batch", "read")) {
			if (app != null && ids != null && !ids.isEmpty()) {
				ArrayList<ParaObject> results = new ArrayList<>(ids.size());
				for (ParaObject result : Para.getDAO().readAll(app.getAppIdentifier(), ids, true).values()) {
					if (checkImplicitAppPermissions(app, result) && checkIfUserCanModifyObject(app, result)) {
						results.add(result);
					}
				}
				return ResponseEntity.ok(results);
			} else {
				return getStatusResponse(HttpStatus.BAD_REQUEST, "Missing ids.");
			}
		}
	}

	/**
	 * Batch create response as JSON.
	 * @param app the current App object
	 * @param is entity input stream
	 * @return a status code 200 or 400
	 */
	public static ResponseEntity<?> getBatchCreateResponse(final App app, InputStream is) {
		try (Metrics.Context context = Metrics.time(app == null ? null : app.getAppid(),
				RestUtils.class, "batch", "create")) {
			if (app != null) {
				final LinkedList<ParaObject> newObjects = new LinkedList<>();
				Set<String> ids = new LinkedHashSet<>();
				ResponseEntity<?> entityRes = getEntity(is, List.class, true);
				if (entityRes.getStatusCode().is2xxSuccessful()) {
					List<Map<String, Object>> items = (List<Map<String, Object>>) entityRes.getBody();
					for (Map<String, Object> object : items) {
						// can't create multiple apps in batch
						String type = (String) object.get(Config._TYPE);
						if (isNotAnApp(type)) {
							ParaObject pobj = ParaObjectUtils.setAnnotatedFields(object);
							if (pobj != null && isValidObject(app, pobj)) {
								pobj.setAppid(app.getAppIdentifier());
								setCreatorid(app, pobj);
								if (pobj.getId() != null && ids.contains(pobj.getId())) {
									logger.warn("Batch contains objects with duplicate ids. "
											+ "Duplicate object {} might not be persisted!", pobj.getId());
								}
								ids.add(pobj.getId());
								newObjects.add(pobj);
							}
						}
					}

					Para.getDAO().createAll(app.getAppIdentifier(), newObjects);

					Para.asyncExecute(() -> {
						if (app.addDatatypes(newObjects.toArray(ParaObject[]::new))) {
							CoreUtils.getInstance().overwrite(app);
						}
					});
				} else {
					return entityRes;
				}
				return ResponseEntity.ok(newObjects);
			} else {
				return getStatusResponse(HttpStatus.BAD_REQUEST);
			}
		}
	}

	/**
	 * Batch update response as JSON.
	 * @param app the current App object
	 * @param oldObjects a list of old objects read from DB
	 * @param newProperties a list of new object properties to be updated
	 * @return a status code 200 or 400
	 */
	public static ResponseEntity<?> getBatchUpdateResponse(App app, Map<String, ParaObject> oldObjects,
			List<Map<String, Object>> newProperties) {
		try (Metrics.Context context = Metrics.time(app == null ? null : app.getAppid(),
				RestUtils.class, "batch", "update")) {
			if (app != null && oldObjects != null && newProperties != null) {
				LinkedList<ParaObject> updatedObjects = new LinkedList<>();
				boolean hasPositiveVersions = false;
				for (Map<String, Object> newProps : newProperties) {
					if (newProps != null && newProps.containsKey(Config._ID)) {
						ParaObject oldObject = oldObjects.get((String) newProps.get(Config._ID));
						// updating apps in batch is not allowed
						if (oldObject != null && checkImplicitAppPermissions(app, oldObject)) {
							ParaObject updatedObject = ParaObjectUtils.setAnnotatedFields(oldObject, newProps, Locked.class);
							if (isValidObject(app, updatedObject) && checkIfUserCanModifyObject(app, updatedObject)) {
								updatedObject.setAppid(app.getAppIdentifier());
								updatedObjects.add(updatedObject);
								if (updatedObject.getVersion() != null && updatedObject.getVersion() > 0) {
									hasPositiveVersions = true;
								}
							}
						}
					}
				}
				Para.getDAO().updateAll(app.getAppIdentifier(), updatedObjects);
				// check if any or all updates failed due to optimistic locking
				return handleFailedUpdates(hasPositiveVersions, updatedObjects);
			} else {
				return getStatusResponse(HttpStatus.BAD_REQUEST);
			}
		}
	}

	/**
	 * Batch delete response as JSON.
	 * @param app the current App object
	 * @param ids list of ids to delete
	 * @return a status code 200 or 400
	 */
	public static ResponseEntity<?> getBatchDeleteResponse(App app, List<String> ids) {
		try (Metrics.Context context = Metrics.time(app == null ? null : app.getAppid(),
				RestUtils.class, "batch", "delete")) {
			LinkedList<ParaObject> objects = new LinkedList<>();
			if (app != null && ids != null && !ids.isEmpty()) {
				for (ParaObject pobj : Para.getDAO().readAll(app.getAppIdentifier(), ids, true).values()) {
					if (pobj != null && pobj.getId() != null && pobj.getType() != null) {
						// deleting apps in batch is not allowed
						if (isNotAnApp(pobj.getType()) && checkIfUserCanModifyObject(app, pobj)) {
							objects.add(pobj);
						}
					}
				}
				Para.getDAO().deleteAll(app.getAppIdentifier(), objects);
			} else {
				return getStatusResponse(HttpStatus.BAD_REQUEST, "Missing ids.");
			}
			return ResponseEntity.ok().build();
		}
	}

	/////////////////////////////////////////////
	//		 LINKS REST RESPONSE HANDLERS
	/////////////////////////////////////////////

	/**
	 * Handles requests to search for linked objects.
	 * @param pobj the object to operate on
	 * @param id2 the id of the second object (optional)
	 * @param type2 the type of the second object
	 * @param pager a {@link Pager} object
	 * @param childrenOnly find only directly linked objects in 1-to-many relationship
	 * @param req request
	 * @return a Response
	 */
	public static ResponseEntity<?> readLinksHandler(ParaObject pobj, String id2, String type2,
			Pager pager, boolean childrenOnly, HttpServletRequest req) {
		try (Metrics.Context context = Metrics.time(null, RestUtils.class, "links", "read")) {
			String query = req.getParameter("q");
			if (type2 != null) {
				if (id2 != null) {
					return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).
							body(Boolean.toString(pobj.isLinked(type2, id2)));
				} else {
					List<ParaObject> items = new ArrayList<>();
					if (childrenOnly) {
						if (req.getParameter("count") != null) {
							pager.setCount(pobj.countChildren(type2));
						} else {
							if (req.getParameter("field") != null && req.getParameter("term") != null) {
								items = pobj.getChildren(type2, req.getParameter("field"), req.getParameter("term"), pager);
							} else {
								if (StringUtils.isBlank(query)) {
									items = pobj.getChildren(type2, pager);
								} else {
									items = pobj.findChildren(type2, query, pager);
								}
							}
						}
					} else {
						if (req.getParameter("count") != null) {
							pager.setCount(pobj.countLinks(type2));
						} else {
							if (StringUtils.isBlank(query)) {
								items = pobj.getLinkedObjects(type2, pager);
							} else {
								items = pobj.findLinkedObjects(type2, req.getParameter("field"), query, pager);
							}
						}
					}
					return ResponseEntity.ok(buildPageResponse(items, pager));
				}
			} else {
				return getStatusResponse(HttpStatus.BAD_REQUEST, "Parameter 'type' is missing.");
			}
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
	public static ResponseEntity<?> deleteLinksHandler(ParaObject pobj, String id2, String type2, boolean childrenOnly) {
		try (Metrics.Context context = Metrics.time(null, RestUtils.class, "links", "delete")) {
			if (type2 == null && id2 == null) {
				pobj.unlinkAll();
			} else if (type2 != null) {
				if (id2 != null) {
					pobj.unlink(type2, id2);
				} else if (childrenOnly) {
					pobj.deleteChildren(type2);
				}
			}
			return ResponseEntity.ok().build();
		}
	}

	/**
	 * Handles requests to link an object to other objects.
	 * @param pobj the object to operate on
	 * @param id2 the id of the second object (optional)
	 * @return a Response
	 */
	public static ResponseEntity<?> createLinksHandler(ParaObject pobj, String id2) {
		try (Metrics.Context context = Metrics.time(null, RestUtils.class, "links", "create")) {
			if (id2 != null && pobj != null) {
				String linkid = pobj.link(id2);
				if (linkid == null) {
					return getStatusResponse(HttpStatus.BAD_REQUEST, "Failed to create link.");
				} else {
					return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(linkid);
				}
			} else {
				return getStatusResponse(HttpStatus.BAD_REQUEST, "Parameters 'type' and 'id' are missing.");
			}
		}
	}

	/////////////////////////////////////////////
	//			SEARCH RESPONSE HANDLERS
	/////////////////////////////////////////////

	static <P extends ParaObject> Map<String, Object> buildQueryAndSearch(App app, String querytype,
			String typeOverride, HttpServletRequest req) {
		Map<String, String[]> params = req.getParameterMap();
		String query = paramOrDefault(req, "q", "*");
		String appid = app.getAppIdentifier();
		Pager pager = getPagerFromParams(req);
		List<P> items = Collections.emptyList();
		String queryType = paramOrDefault(req, "querytype", querytype);
		String type = paramOrDefault(req, Config._TYPE, null);
		if (!StringUtils.isBlank(typeOverride) && !"search".equals(typeOverride)) {
			type = typeOverride;
		}
		try (Metrics.Context context = Metrics.time(appid, RestUtils.class, "search", queryType)) {
			if (params == null) {
				return buildPageResponse(Para.getSearch().findQuery(appid, type, query, pager), pager);
			}
			if ("id".equals(queryType)) {
				items = findByIdQuery(req, appid, pager);
			} else if ("ids".equals(queryType)) {
				items = Para.getSearch().findByIds(appid, queryParams("ids", req));
				pager.setCount(items.size());
			} else if ("nested".equals(queryType)) {
				items = Para.getSearch().findNestedQuery(appid, type, req.getParameter("field"), query, pager);
			} else if ("nearby".equals(queryType)) {
				items = findNearbyQuery(req, appid, type, query, pager);
			} else if ("prefix".equals(queryType)) {
				items = Para.getSearch().findPrefix(appid, type, req.getParameter("field"), req.getParameter("prefix"), pager);
			} else if ("similar".equals(queryType)) {
				items = findSimilarQuery(req, appid, type, pager);
			} else if ("tagged".equals(queryType)) {
				items = Para.getSearch().findTagged(appid, type, queryParams("tags", req).toArray(String[]::new), pager);
			} else if ("in".equals(queryType)) {
				items = Para.getSearch().findTermInList(appid, type,
						req.getParameter("field"), queryParams("terms", req), pager);
			} else if ("terms".equals(queryType)) {
				items = findTermsQuery(req, pager, appid, type);
			} else if ("wildcard".equals(queryType)) {
				items = Para.getSearch().findWildcard(appid, type, req.getParameter("field"), query, pager);
			} else if ("count".equals(queryType)) {
				pager.setCount(Para.getSearch().getCount(appid, type));
			} else {
				items = Para.getSearch().findQuery(appid, type, query, pager);
			}
			return buildPageResponse(items, pager);
		}
	}

	private static <P extends ParaObject> List<P> findTermsQuery(HttpServletRequest req,
			Pager pager, String appid, String type) {
		String matchAll = paramOrDefault(req, "matchall", "true");
		List<String> termsList = queryParams("terms", req);
		if (termsList != null) {
			Map<String, String> terms = new HashMap<>(termsList.size());
			for (String termTuple : termsList) {
				if (!StringUtils.isBlank(termTuple) && termTuple.contains(Para.getConfig().separator())) {
					String[] split = termTuple.split(Para.getConfig().separator(), 2);
					terms.put(split[0], split[1]);
				}
			}
			if (req.getParameter("count") != null) {
				pager.setCount(Para.getSearch().getCount(appid, type, terms));
			} else {
				return Para.getSearch().findTerms(appid, type, terms, Boolean.parseBoolean(matchAll), pager);
			}
		}
		return Collections.emptyList();
	}


	private static <P extends ParaObject> List<P> findSimilarQuery(HttpServletRequest req,
			String appid, String type, Pager pager) {
		String like = req.getParameter("like");
		List<String> fieldz = queryParams("fields", req);
		if (Strings.CS.startsWith(like, "id:")) {
			ParaObject likeObj = Para.getDAO().read(appid, Strings.CS.removeStart(like, "id:"));
			if (likeObj != null) {
				StringBuilder sb = new StringBuilder();
				fieldz.forEach(field -> {
					try {
						String value = BeanUtils.getProperty(likeObj, field);
						if (value != null) {
							sb.append(Utils.stripAndTrim(value)).append(" ");
						}
					} catch (Exception e) {
						logger.debug("Missing fields {} in object {}: {}", fieldz, likeObj.getId(), e.getMessage());
					}
				});
				like = sb.toString();
			}
		}
		return Para.getSearch().findSimilar(appid, type, req.getParameter("filterid"),
				fieldz.toArray(String[]::new), like, pager);
	}

	private static <P extends ParaObject> List<P> findNearbyQuery(HttpServletRequest req,
			String appid, String type, String query, Pager pager) {
		String latlng = req.getParameter("latlng");
		if (Strings.CS.contains(latlng, ",")) {
			String[] coords = latlng.split(",", 2);
			String rad = paramOrDefault(req, "radius", null);
			int radius = NumberUtils.toInt(rad, 10);
			double lat = NumberUtils.toDouble(coords[0], 0);
			double lng = NumberUtils.toDouble(coords[1], 0);
			return Para.getSearch().findNearby(appid, type, query, radius, lat, lng, pager);
		}
		return Collections.emptyList();
	}

	private static <P extends ParaObject> List<P> findByIdQuery(HttpServletRequest req, String appid, Pager pager) {
		String id = paramOrDefault(req, Config._ID, null);
		P obj = Para.getSearch().findById(appid, id);
		if (obj != null) {
			pager.setCount(1);
			return Collections.singletonList(obj);
		}
		return Collections.emptyList();
	}

	private static ResponseEntity<?> handleFailedUpdates(boolean precondition, List<ParaObject> updatedObjects) {
		if (precondition) {
			boolean wasNotEmpty = !updatedObjects.isEmpty();
			Iterator<ParaObject> it = updatedObjects.iterator();
			while (it.hasNext()) {
				ParaObject updatedObject = it.next();
				if (updatedObject.getVersion() == -1) {
					it.remove();
				}
			}
			if (wasNotEmpty && updatedObjects.isEmpty()) {
				return getStatusResponse(HttpStatus.PRECONDITION_FAILED,
						"Update failed for all objects in batch due to 'version' mismatch.");
			}
		}
		return ResponseEntity.ok(updatedObjects);
	}

	private static String paramOrDefault(HttpServletRequest req, String name, String defaultValue) {
		return req != null && req.getParameter(name) != null ? req.getParameter(name) : defaultValue;
	}

	private static <P extends ParaObject> Map<String, Object> buildPageResponse(List<P> items, Pager pager) {
		App app = getPrincipalApp();
		ArrayList<P> checkedItems = new ArrayList<>(items.size());
		for (P item : items) {
			if (checkImplicitAppPermissions(app, item) && checkIfUserCanModifyObject(app, item)) {
				checkedItems.add(item);
			}
		}
		if (!items.isEmpty() && checkedItems.isEmpty()) {
			pager.setCount(0);
		}
		Map<String, Object> result = new HashMap<>();
		result.put("items", checkedItems);
		result.put("page", pager.getPage());
		result.put("totalHits", pager.getCount());
		if (!StringUtils.isBlank(pager.getLastKey())) {
			result.put("lastKey", pager.getLastKey());
		}
		return result;
	}

	private static void setCreatorid(App app, ParaObject content) {
		if (content != null) {
			User user = getAuthenticatedUser();
			if (user != null) {
				content.setCreatorid(user.getId());
				if (!StringUtils.isBlank(content.getId()) && content.getCreatorid() != null &&
						app.permissionsContainOwnKeyword(user, content) &&
						!content.getId().startsWith(content.getCreatorid() + "_")) {
					// prevents one user from overwriting another user's objects
					content.setId(content.getCreatorid() + "_" + content.getId());
				}
			}
		}
	}

	private static int calculateObjectSize(JsonNode jsonNode) {
		if (jsonNode == null || jsonNode.isNull()) {
			return 4; // null
		}
		if (jsonNode.isTextual() || jsonNode.isNumber() || jsonNode.isBoolean()) {
			return 2 + jsonNode.asText().length(); // two quotes + string
		}
		if (jsonNode.isArray() || jsonNode.isObject()) {
			AtomicInteger size = new AtomicInteger(2); // extra chars like quotes & braces
			if (jsonNode.isObject()) {
				jsonNode.fieldNames().forEachRemaining(
						field -> size.getAndAdd(12 + field.length()));
			}
			if (!jsonNode.isEmpty()) {
				size.getAndAdd(jsonNode.size() - 1); // commas
				jsonNode.elements().forEachRemaining(
						element -> size.getAndAdd(calculateObjectSize(element)));
			}
			return size.get();
		}
		return jsonNode.asText().length();
	}

	/////////////////////////////////////////////
	//			MISC RESPONSE HANDLERS
	/////////////////////////////////////////////

	/**
	 * Returns a {@link Pager} instance populated from request parameters.
	 * @param req request
	 * @return a Pager
	 */
	public static Pager getPagerFromParams(HttpServletRequest req) {
		Pager pager = new Pager();
		pager.setPage(NumberUtils.toLong(paramOrDefault(req, "page", ""), 0));
		if (pager.getPage() > Para.getConfig().maxPages()) {
			pager.setPage(Para.getConfig().maxPages());
		}
		pager.setLimit(NumberUtils.toInt(paramOrDefault(req, "limit", ""), pager.getLimit()));
		if (pager.getLimit() > Para.getConfig().maxPageLimit()) {
			pager.setLimit(Para.getConfig().maxPageLimit());
		}
		pager.setSortby(paramOrDefault(req, "sort", pager.getSortby()));
		pager.setDesc(Boolean.parseBoolean(paramOrDefault(req, "desc", "true")));
		pager.setLastKey(paramOrDefault(req, "lastKey", null));
		return pager;
	}

	/**
	 * A generic JSON response handler.
	 * @param status status code
	 * @param messages zero or more errors
	 * @return a response as JSON
	 */
	public static ResponseEntity<?> getStatusResponse(HttpStatus status, String... messages) {
		if (status == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}
		String msg = StringUtils.join(messages, ". ");
		if (StringUtils.isBlank(msg)) {
			msg = status.getReasonPhrase();
		}
		try {
			return GenericExceptionMapper.getExceptionResponse(status.value(), msg);
		} catch (Exception ex) {
			logger.error(null, ex);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
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
		try (ServletOutputStream out = response.getOutputStream()) {
			response.setStatus(status);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding(Para.getConfig().defaultEncoding());
			ParaObjectUtils.getJsonWriter().writeValue(out, getStatusResponse(HttpStatus.valueOf(status), message).getBody());
		} catch (Exception ex) {
			logger.error(null, ex);
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
		try (ServletOutputStream out = response.getOutputStream()) {
			response.setStatus(HttpServletResponse.SC_OK);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding(Para.getConfig().defaultEncoding());
			ParaObjectUtils.getJsonWriter().writeValue(out, obj);
		} catch (Exception ex) {
			logger.error(null, ex);
		}
	}

	/**
	 * Returns  the query parameter value.
	 * @param param a parameter name
	 * @param req request
	 * @return parameter value
	 */
	public static String queryParam(String param, HttpServletRequest req) {
		return req.getParameter(param);
	}

	/**
	 * Returns the query parameter values.
	 * @param param a parameter name
	 * @param req request
	 * @return a list of values
	 */
	public static List<String> queryParams(String param, HttpServletRequest req) {
		String[] params = req.getParameterValues(param);
		List<String> plist = new LinkedList<String>();
		if (params != null && params.length > 0) {
			plist.addAll(Arrays.asList(params));
		}
		return plist;
	}

}
