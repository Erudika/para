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

import com.erudika.para.Para;
import com.erudika.para.annotations.Locked;
import com.erudika.para.core.App;
import com.erudika.para.core.CoreUtils;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.ParaObjectUtils;
import com.erudika.para.core.User;
import com.erudika.para.security.SecurityUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.erudika.para.validation.ValidationUtils;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
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
		String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (StringUtils.isBlank(auth)) {
			auth = request.getParameter("X-Amz-Credential");
			if (StringUtils.isBlank(auth)) {
				return "";
			} else {
				return StringUtils.substringBefore(auth, "/");
			}
		} else {
			String credential = StringUtils.substringBetween(auth, "Credential=", ",");
			return StringUtils.substringBefore(credential, "/");
		}
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
	 * Extracts the resource name, for example '/v1/_some_resource_name'
	 * returns '_some_resource_name'.
	 * @param request a request
	 * @return the resource name
	 */
	public static String extractResourceName(HttpServletRequest request) {
		if (request == null || request.getRequestURI().length() <= 3) {
			return "";
		}
		String uri = request.getRequestURI().substring(1);
		int start = uri.indexOf("/");

		if (start >= 0 && start + 1 < uri.length()) {
			int end = uri.substring(start + 1).indexOf("/") + start + 1;
			if (end > start) {
				return uri.substring(start + 1, end);
			} else {
				return uri.substring(start + 1);
			}
		} else {
			return "";
		}
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
	 * @param is the entity input stream
	 * @param type the type to convert the entity into, for example a Map.
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
				entity = ParaObjectUtils.getJsonReader(type).readValue(is);
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

	/////////////////////////////////////////////
	//			REST RESPONSE HANDLERS
	/////////////////////////////////////////////

	/**
	 * Read response as JSON
	 * @param content the object that was read
	 * @return status code 200 or 404
	 */
	public static Response getReadResponse(ParaObject content) {
		if (content != null) {
			return Response.ok(content).build();
		} else {
			return getStatusResponse(Response.Status.NOT_FOUND);
		}
	}

	/**
	 * Create response as JSON
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
			if (content != null) {
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
	 * Overwrite response as JSON
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
			if (content != null && !StringUtils.isBlank(id)) {
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
					CoreUtils.overwrite(app.getAppIdentifier(), content);
					// new type added so update app object
					if (typesCount < app.getDatatypes().size()) {
						app.update();
					}
					return Response.ok(content).build();
				}
				return getStatusResponse(Response.Status.BAD_REQUEST, errors);
			}
			return getStatusResponse(Response.Status.BAD_REQUEST, "Failed to create object.");
		}
		return entityRes;
	}

	/**
	 * Update response as JSON
	 * @param object object to validate and update
	 * @param is entity input stream
	 * @param app the app object
	 * @return a status code 200 or 400 or 404
	 */
	public static Response getUpdateResponse(App app, ParaObject object, InputStream is) {
		if (object != null) {
			Map<String, Object> newContent;
			Response entityRes = getEntity(is, Map.class);
			if (entityRes.getStatusInfo() == Response.Status.OK) {
				newContent = (Map<String, Object>) entityRes.getEntity();
			} else {
				return entityRes;
			}
			if (object.getAppid() != null) {
				ParaObjectUtils.setAnnotatedFields(object, newContent, Locked.class);
				// This is the primary validation pass (validates not only core POJOS but also user defined objects).
				String[] errors = ValidationUtils.validateObject(app, object);
				if (errors.length == 0) {
					// Secondary validation pass: object is validated again before being updated
					object.update();
					return Response.ok(object).build();
				}
				return getStatusResponse(Response.Status.BAD_REQUEST, errors);
			}
			return getStatusResponse(Response.Status.BAD_REQUEST, "Missing appid. Object must belong to an app.");
		}
		return getStatusResponse(Response.Status.NOT_FOUND);
	}

	/**
	 * Delete response as JSON
	 * @param content the object to delete
	 * @param app the current App object
	 * @return a status code 200 or 400
	 */
	public static Response getDeleteResponse(App app, ParaObject content) {
		if (content != null && content.getId() != null && content.getAppid() != null) {
			content.setAppid(app.getAppIdentifier());
			content.delete();
			return Response.ok().build();
		} else {
			return getStatusResponse(Response.Status.BAD_REQUEST);
		}
	}

	/**
	 * Batch read response as JSON
	 * @param app the current App object
	 * @param ids list of ids
	 * @return status code 200 or 400
	 */
	public static Response getBatchReadResponse(App app, List<String> ids) {
		if (ids != null && !ids.isEmpty()) {
			return Response.ok(Para.getDAO().readAll(app.getAppIdentifier(), ids, true).values()).build();
		} else {
			return getStatusResponse(Response.Status.BAD_REQUEST, "Missing ids.");
		}
	}

	/**
	 * Batch create response as JSON
	 * @param app the current App object
	 * @param is entity input stream
	 * @return a status code 200 or 400
	 */
	public static Response getBatchCreateResponse(final App app, InputStream is) {
		final LinkedList<ParaObject> objects = new LinkedList<ParaObject>();
		Response entityRes = getEntity(is, List.class);
		if (entityRes.getStatusInfo() == Response.Status.OK) {
			List<Map<String, Object>> items = (List<Map<String, Object>>) entityRes.getEntity();
			for (Map<String, Object> object : items) {
				ParaObject pobj = ParaObjectUtils.setAnnotatedFields(object);
				if (pobj != null && ValidationUtils.isValidObject(pobj)) {
					pobj.setAppid(app.getAppIdentifier());
					objects.add(pobj);
				}
			}

			Para.getDAO().createAll(app.getAppIdentifier(), objects);

			Para.asyncExecute(new Runnable() {
				public void run() {
					int typesCount = app.getDatatypes().size();
					app.addDatatypes(objects.toArray(new ParaObject[objects.size()]));
					if (typesCount < app.getDatatypes().size()) {
						app.update();
					}
				}
			});
		} else {
			return entityRes;
		}
		return Response.ok(objects).build();
	}

	/**
	 * Batch update response as JSON
	 * @param app the current App object
	 * @param is entity input stream
	 * @return a status code 200 or 400
	 */
	public static Response getBatchUpdateResponse(App app, InputStream is) {
		LinkedList<ParaObject> objects = new LinkedList<ParaObject>();
		Response entityRes = getEntity(is, List.class);
		if (entityRes.getStatusInfo() == Response.Status.OK) {
			List<Map<String, Object>> items = (List<Map<String, Object>>) entityRes.getEntity();
			// WARN: objects will not be validated here as this would require them to be read first
			for (Map<String, Object> item : items) {
				if (item != null && item.containsKey(Config._ID) && item.containsKey(Config._TYPE)) {
					ParaObject pobj = ParaObjectUtils.setAnnotatedFields(null, item, Locked.class);
					if (pobj != null) {
						pobj.setId((String) item.get(Config._ID));
						pobj.setType((String) item.get(Config._TYPE));
						objects.add(pobj);
					}
				}
			}
			Para.getDAO().updateAll(app.getAppIdentifier(), objects);
		} else {
			return entityRes;
		}
		return Response.ok(objects).build();
	}

	/**
	 * Batch delete response as JSON
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
						objects.add(pobj);
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

	/**
	 * A generic JSON response handler
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
	 * A generic JSON response handler
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

}
