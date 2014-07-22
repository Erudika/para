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

import com.erudika.para.Para;
import com.erudika.para.annotations.Locked;
import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.User;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.core.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;

/**
 * A few helper methods for handling REST requests and responses.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class RestUtils {

	private static final Logger logger = LoggerFactory.getLogger(RestUtils.class);
	// maps plural to singular type definitions
	private static final Map<String, String> coreTypes = new HashMap<String, String>();
	private static final CoreClassScanner scanner = new CoreClassScanner();
	private static final DateTimeFormatter timeFormatter = DateTimeFormat.
			forPattern("yyyyMMdd'T'HHmmss'Z'").withZoneUTC();

	private RestUtils() { }

	/////////////////////////////////////////////
	//	    	 REST REQUEST UTILS
	/////////////////////////////////////////////

	/**
	 * Returs a parsed Date
	 * @param date a date in the AWS format yyyyMMdd'T'HHmmss'Z'
	 * @return a date
	 */
	public static Date parseAWSDate(String date) {
		if (date == null) {
			return null;
		}
		return timeFormatter.parseDateTime(date).toDate();
	}

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
	 * Returns the {@code appid} of the requesting App or the
	 * default {@code appid} if this request is not signed.
	 * @param princ the authenticated App
	 * @return the {@code appid}
	 */
	protected static String getPrincipalAppid(Principal princ) {
		String def = Config.APP_NAME_NS;
		if (princ == null) {
			return def;
		} else {
			if (princ instanceof User) {
				return StringUtils.isBlank(((User) princ).getAppid()) ? null : ((User) princ).getAppid();
			} else {
				return StringUtils.isBlank(princ.getName()) ? def : princ.getName();
			}
		}
	}

	/**
	 * Returns the current {@link App} object.
	 * @param appid the id of the app
	 * @return an App object or null
	 */
	protected static App getApp(String appid) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (!StringUtils.isBlank(appid) && auth != null) {
			Object principal = auth.getPrincipal();
			if (principal != null && principal instanceof App) {
				return (App) principal;
			} else {
				return Para.getDAO().read(Config.APP_NAME_NS, new App(appid).getId());
			}
		}
		return null;
	}

	/**
	 * Adds unknown types to this App's list of data types. Called on create().
	 * @param app the current app
	 * @param objects a list of new objects
	 */
	protected static void registerNewTypes(App app, ParaObject... objects) {
		// register a new data type
		if (app != null && objects != null && objects.length > 0) {
			boolean update = false;
			for (ParaObject obj : objects) {
				if (obj != null && obj.getType() != null &&
						!getCoreTypes().containsKey(obj.getPlural()) &&
						!app.getDatatypes().containsKey(obj.getPlural())) {

					app.addDatatype(obj.getPlural(), obj.getType());
					update = true;
				}
			}
			if (update) {
				app.update();
			}
		}
	}

	/**
	 * Returns a map of the core data types.
	 * @return a map of type plural - type singular form
	 */
	protected static Map<String, String> getCoreTypes() {
		if (coreTypes.isEmpty()) {
			Set<Class<? extends ParaObject>> coreClasses = new HashSet<Class<? extends ParaObject>>();
			scanForDomainClasses(coreClasses);
			try {
				for (Class<? extends ParaObject> clazz : coreClasses) {
					ParaObject p = clazz.newInstance();
					coreTypes.put(p.getPlural(), p.getType());
				}
			} catch (Exception ex) {
				logger.error(null, ex);
			}
		}
		return Collections.unmodifiableMap(coreTypes);
	}

	/**
	 * Returns a map of all registered types.
	 * @param app the app to search for custom types
	 * @return a map of plural - singular form of type names
	 */
	protected static Map<String, String> getAllTypes(App app) {
		Map<String, String> map = new HashMap<String, String>(getCoreTypes());
		if (app != null) {
			map.putAll(app.getDatatypes());
		}
		return map;
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
	 * @param type type of the object to create (not used)
	 * @param is entity input stream
	 * @param app the app object
	 * @return a status code 201 or 400
	 */
	public static Response getCreateResponse(App app, String type, InputStream is) {
		ParaObject content;
		try {
			if (is != null && is.available() > 0) {
				if (is.available() > (1024 * 1024)) {
					return getStatusResponse(Response.Status.BAD_REQUEST,
							"Request is too large - the maximum is 1MB.");
				}
				Map<String, Object> newContent = Utils.getJsonReader(Map.class).readValue(is);
				// type is not fount in datatypes (try to get it from req. body)
				if (!StringUtils.isBlank(type)) {
					newContent.put(Config._TYPE, type);
				}
				content = Utils.setAnnotatedFields(newContent);
				content.setAppid(app.getAppIdentifier());
				content.setShardKey(app.isShared() ? app.getAppIdentifier() : null);
				registerNewTypes(app, content);
			} else {
				return getStatusResponse(Response.Status.BAD_REQUEST, "Missing request body.");
			}
		} catch (JsonParseException e) {
			return getStatusResponse(Response.Status.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			logger.error(null, e);
			return getStatusResponse(Response.Status.INTERNAL_SERVER_ERROR, e.toString());
		}
		return getCreateResponse(content);
	}

	/**
	 * Create response as JSON
	 * @param content the object to create
	 * @return a status code 201 or 400
	 */
	public static Response getCreateResponse(ParaObject content) {
		String[] errors = Utils.validateObject(content);
		if (content != null && errors.length == 0) {
			String id = content.create();
			if (id == null) {
				return getStatusResponse(Response.Status.BAD_REQUEST, "Failed to create object.");
			} else {
				return Response.created(URI.create(Utils.urlEncode(content.getObjectURI()))).entity(content).build();
			}
		} else {
			return getStatusResponse(Response.Status.BAD_REQUEST, errors);
		}
	}

	/**
	 * Update response as JSON
	 * @param object object to validate and update
	 * @param is entity input stream
	 * @param app the app object
	 * @return a status code 200 or 400 or 404
	 */
	public static Response getUpdateResponse(App app, ParaObject object, InputStream is) {
		Map<String, Object> newContent;
		try {
			if (is != null && is.available() > 0) {
				if (is.available() > (1024 * 1024)) {
					return getStatusResponse(Response.Status.BAD_REQUEST,
							"Request is too large - the maximum is 1MB.");
				}
				object.setShardKey(app.isShared() ? app.getAppIdentifier() : null);
				newContent = Utils.getJsonReader(Map.class).readValue(is);
			} else {
				return getStatusResponse(Response.Status.BAD_REQUEST, "Missing request body.");
			}
		} catch (JsonParseException e) {
			return getStatusResponse(Response.Status.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			logger.error(null, e);
			return getStatusResponse(Response.Status.INTERNAL_SERVER_ERROR, e.toString());
		}
		return getUpdateResponse(object, newContent);
	}

	/**
	 * Update response as JSON
	 * @param object object to validate and update
	 * @param newContent new updated content
	 * @return a status code 200 or 400 or 404
	 */
	public static Response getUpdateResponse(ParaObject object, Map<String, Object> newContent) {
		if (object != null && object.getAppid() != null) {
			Utils.setAnnotatedFields(object, newContent, Locked.class);
			String[] errors = Utils.validateObject(object);
			if (errors.length == 0) {
				object.update();
				return Response.ok(object).build();
			} else {
				return getStatusResponse(Response.Status.BAD_REQUEST, errors);
			}
		} else {
			return getStatusResponse(Response.Status.NOT_FOUND);
		}
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
			content.setShardKey(app.isShared() ? app.getAppIdentifier() : null);
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
	@SuppressWarnings("unchecked")
	public static Response getBatchCreateResponse(final App app, InputStream is) {
		final ArrayList<ParaObject> objects = new ArrayList<ParaObject>();
		try {
			if (is != null && is.available() > 0) {
				if (is.available() > (1024 * 1024)) {
					return getStatusResponse(Response.Status.BAD_REQUEST,
							"Request is too large - the maximum is 1MB.");
				}
				List<Map<String, Object>> items = Utils.getJsonReader(List.class).readValue(is);
				for (Map<String, Object> object : items) {
					ParaObject pobj = Utils.setAnnotatedFields(object);
					if (pobj != null && Utils.isValidObject(pobj)) {
						pobj.setAppid(app.getAppIdentifier());
						pobj.setShardKey(app.isShared() ? app.getAppIdentifier() : null);
						objects.add(pobj);
					}
				}

				Para.getDAO().createAll(app.getAppIdentifier(), objects);

				Utils.asyncExecute(new Runnable() {
					public void run() {
						registerNewTypes(app, objects.toArray(new ParaObject[objects.size()]));
					}
				});
			} else {
				return getStatusResponse(Response.Status.BAD_REQUEST, "Missing request body.");
			}
		} catch (JsonParseException e) {
			return getStatusResponse(Response.Status.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			logger.error(null, e);
			return getStatusResponse(Response.Status.INTERNAL_SERVER_ERROR, e.toString());
		}
		return Response.ok(objects).build();
	}

	/**
	 * Batch update response as JSON
	 * @param app the current App object
	 * @param is entity input stream
	 * @return a status code 200 or 400
	 */
	@SuppressWarnings("unchecked")
	public static Response getBatchUpdateResponse(App app, InputStream is) {
		ArrayList<ParaObject> objects = new ArrayList<ParaObject>();
		try {
			if (is != null && is.available() > 0) {
				if (is.available() > (1024 * 1024)) {
					return getStatusResponse(Response.Status.BAD_REQUEST,
							"Request is too large - the maximum is 1MB.");
				}
				List<Map<String, Object>> items = Utils.getJsonReader(List.class).readValue(is);
				// WARN: objects will not be validated here as this would require them to be read first
				for (Map<String, Object> item : items) {
					if (item != null && item.containsKey(Config._ID) && item.containsKey(Config._TYPE)) {
						ParaObject pobj = Utils.setAnnotatedFields(null, item, Locked.class);
						if (pobj != null) {
							pobj.setId((String) item.get(Config._ID));
							pobj.setType((String) item.get(Config._TYPE));
							pobj.setShardKey(app.isShared() ? app.getAppIdentifier() : null);
							objects.add(pobj);
						}
					}
				}
				Para.getDAO().updateAll(app.getAppIdentifier(), objects);
			} else {
				return getStatusResponse(Response.Status.BAD_REQUEST, "Missing request body.");
			}
		} catch (JsonParseException e) {
			return getStatusResponse(Response.Status.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			logger.error(null, e);
			return getStatusResponse(Response.Status.INTERNAL_SERVER_ERROR, e.toString());
		}
		return Response.ok(objects).build();
	}

	/**
	 * Batch delete response as JSON
	 * @param app the current App object
	 * @param ids list of ids to delete
	 * @return a status code 200 or 400
	 */
	@SuppressWarnings("unchecked")
	public static Response getBatchDeleteResponse(App app, List<String> ids) {
		ArrayList<ParaObject> objects = new ArrayList<ParaObject>();
		if (ids != null && !ids.isEmpty()) {
			if (ids.size() <= Config.MAX_ITEMS_PER_PAGE) {
				for (ParaObject pobj : Para.getDAO().readAll(app.getAppIdentifier(), ids, false).values()) {
					if (pobj != null && pobj.getId() != null && pobj.getType() != null) {
						pobj.setShardKey(app.isShared() ? app.getAppIdentifier() : null);
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
		String msg = (messages == null || messages.length == 0) ? "" : StringUtils.join(messages, ". ");
		try {
			return getExceptionResponse(status.getStatusCode(), status.getReasonPhrase().concat(". ").concat(msg));
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
			Utils.getJsonWriter().writeValue(out, getStatusResponse(Response.Status.
					fromStatusCode(status), message).getEntity());
		} catch (Exception ex) {
			logger.error(null, ex);
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}


	/////////////////////////////////////////////
	//				MISC METHODS
	/////////////////////////////////////////////

	/**
	 * This scans a package for Para objects and adds them to the set.
	 * @param classes a set
	 */
	static void scanForDomainClasses(Set<Class<? extends ParaObject>> classes) {
		if (classes == null) {
			return;
		}
		try {
			Set<Class<? extends ParaObject>> s = scanner.getComponentClasses(ParaObject.class.getPackage().getName());
			if (!Config.CORE_PACKAGE_NAME.isEmpty()) {
				Set<Class<? extends ParaObject>> s2 = scanner.getComponentClasses(Config.CORE_PACKAGE_NAME);
				s.addAll(s2);
			}

			for (Class<? extends ParaObject> coreClass : s) {
				boolean isAbstract = Modifier.isAbstract(coreClass.getModifiers());
				boolean isInterface = Modifier.isInterface(coreClass.getModifiers());
				boolean isFinal = Modifier.isFinal(coreClass.getModifiers());
				boolean isCoreObject = ParaObject.class.isAssignableFrom(coreClass);
				if (isCoreObject && !isAbstract && !isInterface && !isFinal) {
					classes.add(coreClass);
				}
			}
			logger.debug("Found {} ParaObject classes: {}", classes.size(), classes);
		} catch (Exception ex) {
			logger.error(null, ex);
		}
	}

	private static class CoreClassScanner extends ClassPathScanningCandidateComponentProvider {
		public CoreClassScanner() {
			super(false);
			addIncludeFilter(new AssignableTypeFilter(ParaObject.class));
		}

		@SuppressWarnings("unchecked")
		public final Set<Class<? extends ParaObject>> getComponentClasses(String basePackage) {
			basePackage = (basePackage == null) ? "" : basePackage;
			Set<Class<? extends ParaObject>> classes = new HashSet<Class<? extends ParaObject>>();
			for (BeanDefinition candidate : findCandidateComponents(basePackage)) {
				try {
					Class<? extends ParaObject> cls = (Class<? extends ParaObject>) ClassUtils.
							resolveClassName(candidate.getBeanClassName(), ClassUtils.getDefaultClassLoader());
					classes.add(cls);
				} catch (Exception ex) {
					logger.error(null, ex);
				}
			}
			return classes;
		}
	}

	/////////////////////////////////////////////
	//	    	  EXCEPTION MAPPERS
	/////////////////////////////////////////////

	/**
	 * Generic exception mapper.
	 */
	@Provider
	public static class GenericExceptionMapper implements ExceptionMapper<Exception> {
		/**
		 * @param ex exception
		 * @return a response
		 */
		public Response toResponse(final Exception ex) {
			if (ex instanceof WebApplicationException) {
				return getExceptionResponse(((WebApplicationException) ex).getResponse().getStatus(), ex.getMessage());
			} else {
				return getExceptionResponse(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), ex.getMessage());
			}
		}
	}

	private static Response getExceptionResponse(final int status, final String msg) {
		return Response.status(status).entity(new LinkedHashMap<String, Object>() {
			private static final long serialVersionUID = 1L;
			{
				put("code", status);
				put("message", msg);
			}
		}).type(MediaType.APPLICATION_JSON).build();
	}

}
