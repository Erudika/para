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

import com.erudika.para.annotations.Locked;
import com.erudika.para.core.ParaObject;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.core.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.security.Principal;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServiceUnavailableException;
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
import org.springframework.util.ClassUtils;

/**
 * A few helper methods for handling REST requests and responses.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class RestUtils {

	private static final Logger logger = LoggerFactory.getLogger(RestUtils.class);
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
	public static String getPrincipalAppid(Principal princ) {
		String def = Config.APP_NAME_NS;
		if (princ == null) {
			return def;
		} else {
			return StringUtils.isBlank(princ.getName()) ? def : princ.getName();
		}
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
	 * @return a status code 201 or 400
	 */
	public static Response getCreateResponse(String type, InputStream is) {
		ParaObject content = null;
		try {
			if (is != null) {
				Map<String, Object> newContent = Utils.getJsonReader(Map.class).readValue(is);
				content = Utils.setAnnotatedFields(newContent);
			}
		} catch (JsonParseException e) {
			return RestUtils.getStatusResponse(Response.Status.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			logger.error(null, e);
			return RestUtils.getStatusResponse(Response.Status.INTERNAL_SERVER_ERROR, e.toString());
		}
		return getCreateResponse(content);
	}

	/**
	 * Create response as JSON
	 * @param content the object to create
	 * @return a status code 201 or 400
	 */
	public static Response getCreateResponse(ParaObject content) {
		if (content != null && content.getId() != null && content.exists()) {
			return Response.ok().build();
		}
		String[] errors = Utils.validateObject(content);
		if (content != null && errors.length == 0) {
			String id = content.create();
			if (id == null) {
				return getStatusResponse(Response.Status.BAD_REQUEST, "Failed to create object.");
			} else {
				return Response.created(URI.create(content.getObjectURI())).entity(content).build();
			}
		} else {
			return getStatusResponse(Response.Status.BAD_REQUEST, errors);
		}
	}

	/**
	 * Update response as JSON
	 * @param object object to validate and update
	 * @param is entity input stream
	 * @return a status code 200 or 400 or 404
	 */
	public static Response getUpdateResponse(ParaObject object, InputStream is) {
		Map<String, Object> newContent = null;
		try {
			if (is != null) {
				newContent = Utils.getJsonReader(Map.class).readValue(is);
			}
		} catch (JsonParseException e) {
			return RestUtils.getStatusResponse(Response.Status.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			logger.error(null, e);
			return RestUtils.getStatusResponse(Response.Status.INTERNAL_SERVER_ERROR, e.toString());
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
		if (object != null) {
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
	 * @return a status code 200 or 400
	 */
	public static Response getDeleteResponse(ParaObject content) {
		if (content != null && content.getId() != null) {
			content.delete();
			return Response.ok().build();
		} else {
			return getStatusResponse(Response.Status.BAD_REQUEST);
		}
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
			Utils.getJsonWriter().writeValue(out, RestUtils.
					getStatusResponse(Response.Status.fromStatusCode(status), message).getEntity());
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
	 * Converts a JSON entity from a POST/PUT request to a Map
	 * @param is entity stream
	 * @return a map representing the consumed entity
	 */
	static Map<String, Object> getMapFromEntity(InputStream is) {
		Map<String, Object> newContent = null;
		try {
			if (is != null) {
				newContent = Utils.getJsonReader(Map.class).readValue(is);
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
		return newContent;
	}

	/**
	 * This scans a package for Para objects and adds the to the set.
	 * NOTE: This method is slow!
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
		 *
		 * @param ex exception
		 * @return a response
		 */
		public Response toResponse(final Exception ex) {
			return getExceptionResponse(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), ex.getMessage());
		}
	}

	/**
	 * Error 403 exception mapper.
	 */
	@Provider
	public static class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {
		/**
		 *
		 * @param ex exception
		 * @return a response
		 */
		public Response toResponse(final ForbiddenException ex) {
			return getExceptionResponse(Response.Status.FORBIDDEN.getStatusCode(), ex.getMessage());
		}
	}

	/**
	 * Error 404 exception mapper.
	 */
	@Provider
	public static class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {
		/**
		 * Convert to response.
		 * @param ex exception
		 * @return a response
		 */
		public Response toResponse(NotFoundException ex) {
			return getExceptionResponse(Response.Status.NOT_FOUND.getStatusCode(), ex.getMessage());
		}
	}

	/**
	 * Error 500 exception mapper.
	 */
	@Provider
	public static class InternalExceptionMapper implements ExceptionMapper<InternalServerErrorException> {
		/**
		 *
		 * @param ex exception
		 * @return a response
		 */
		public Response toResponse(InternalServerErrorException ex) {
			return getExceptionResponse(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), ex.getMessage());
		}
	}

	/**
	 * Error 503 exception mapper.
	 */
	@Provider
	public static class UnavailableExceptionMapper implements ExceptionMapper<ServiceUnavailableException> {
		/**
		 *
		 * @param ex exception
		 * @return a response
		 */
		public Response toResponse(ServiceUnavailableException ex) {
			return getExceptionResponse(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), ex.getMessage());
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
