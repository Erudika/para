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
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.User;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Constraint;
import com.erudika.para.utils.Utils;
import com.erudika.para.utils.ValidationUtils;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jetty.connector.JettyConnectorProvider;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A few helper methods for handling REST requests and responses.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@SuppressWarnings("unchecked")
public final class RestUtils {

	private static final Logger logger = LoggerFactory.getLogger(RestUtils.class);
	private static final Signer signer = new Signer();
	private static final Client apiClient;
	// maps plural to singular type definitions
	private static final Map<String, String> coreTypes = new DualHashBidiMap();
	private static final DateTimeFormatter timeFormatter = DateTimeFormat.
			forPattern("yyyyMMdd'T'HHmmss'Z'").withZoneUTC();

	static {
		ClientConfig clientConfig = new ClientConfig();
		clientConfig.register(GenericExceptionMapper.class);
		clientConfig.register(new JacksonJsonProvider(Utils.getJsonMapper()));
		clientConfig.connectorProvider(new JettyConnectorProvider());
		apiClient = ClientBuilder.newClient(clientConfig);
	}

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
	 * Returns the current authenticated {@link App} object.
	 * @return an App object or null
	 */
	public static App getPrincipalApp() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null) {
			Object principal = auth.getPrincipal();
			if (principal != null) {
				if (principal instanceof App) {
					return (App) principal;
				} else if (principal instanceof User) {
					return Para.getDAO().read(Config.APP_NAME_NS, App.id(((User) principal).getAppid()));
				}
			}
		}
		logger.info("Unauthenticated request - returning root App: {}", Config.APP_NAME_NS);
		return Para.getDAO().read(Config.APP_NAME_NS, App.id(Config.APP_NAME_NS));
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
	public static Map<String, String> getCoreTypes() {
		if (coreTypes.isEmpty()) {
			try {
				for (Class<? extends ParaObject> clazz : Utils.getCoreClassesMap().values()) {
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
	public static Map<String, String> getAllTypes(App app) {
		Map<String, String> map = new HashMap<String, String>(getCoreTypes());
		if (app != null) {
			map.putAll(app.getDatatypes());
		}
		return map;
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
				entity = Utils.getJsonReader(type).readValue(is);
			} else {
				return getStatusResponse(Response.Status.BAD_REQUEST, "Missing request body.");
			}
		} catch (JsonParseException e) {
			return getStatusResponse(Response.Status.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			logger.error(null, e);
			return getStatusResponse(Response.Status.INTERNAL_SERVER_ERROR, e.toString());
		}

		return Response.ok(entity).build();
	}

	/**
	 * Builds, signs and executes a request to an API endpoint using the provided credentials.
	 * Signs the request using the Amazon Signature 4 algorithm and returns the response.
	 * @param accessKey access key
	 * @param secretKey secret key
	 * @param httpMethod the method (GET, POST...)
	 * @param endpointURL protocol://host:port
	 * @param reqPath the API resource path relative to the endpointURL
	 * @param headers headers map
	 * @param params parameters map
	 * @param entity an entity containing any Java object (payload), could be null
	 * @return a response object
	 */
	public static Response invokeSignedRequest(String accessKey, String secretKey,
			String httpMethod, String endpointURL, String reqPath,
			Map<String, String> headers, MultivaluedMap<String, String> params, Entity<?> entity) {
		byte[] jsonEntity = null;
		if (entity != null) {
			try {
				jsonEntity = Utils.getJsonWriter().writeValueAsBytes(entity.getEntity());
			} catch (JsonProcessingException ex) {
				jsonEntity = null;
				logger.error(null, ex);
			}
		}
		return invokeSignedRequest(accessKey, secretKey, httpMethod, endpointURL, reqPath, headers, params, jsonEntity);
	}

	/**
	 * Builds, signs and executes a request to an API endpoint using the provided credentials.
	 * Signs the request using the Amazon Signature 4 algorithm and returns the response.
	 * @param accessKey access key
	 * @param secretKey secret key
	 * @param httpMethod the method (GET, POST...)
	 * @param endpointURL protocol://host:port
	 * @param reqPath the API resource path relative to the endpointURL
	 * @param headers headers map
	 * @param params parameters map
	 * @param jsonEntity an object serialized to JSON byte array (payload), could be null
	 * @return a response object
	 */
	public static Response invokeSignedRequest(String accessKey, String secretKey,
			String httpMethod, String endpointURL, String reqPath,
			Map<String, String> headers, MultivaluedMap<String, String> params, byte[] jsonEntity) {

		WebTarget target = apiClient.target(endpointURL).path(reqPath);
		Map<String, String> signedHeaders = signRequest(accessKey, secretKey, httpMethod, endpointURL, reqPath,
				headers, params, jsonEntity);

		if (params != null) {
			for (Map.Entry<String, List<String>> param : params.entrySet()) {
				String key = param.getKey();
				List<String> value = param.getValue();
				if (value != null && !value.isEmpty() && value.get(0) != null) {
					target = target.queryParam(key, value.toArray());
				}
			}
		}

		Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON);

		if (headers != null) {
			for (Map.Entry<String, String> header : headers.entrySet()) {
				builder.header(header.getKey(), header.getValue());
			}
		}

		Entity<?> jsonPayload = null;
		if (jsonEntity != null && jsonEntity.length > 0) {
			try {
				jsonPayload = Entity.json(new String(jsonEntity, Config.DEFAULT_ENCODING));
			} catch (IOException ex) {
				logger.error(null, ex);
			}
		}

		builder.header(HttpHeaders.AUTHORIZATION, signedHeaders.get(HttpHeaders.AUTHORIZATION)).
				header("X-Amz-Date", signedHeaders.get("X-Amz-Date"));

		if (jsonPayload != null) {
			return builder.method(httpMethod, jsonPayload);
		} else {
			return builder.method(httpMethod);
		}
	}

	/**
	 * Builds and signs a request to an API endpoint using the provided credentials.
	 * @param accessKey access key
	 * @param secretKey secret key
	 * @param httpMethod the method (GET, POST...)
	 * @param endpointURL protocol://host:port
	 * @param reqPath the API resource path relative to the endpointURL
	 * @param headers headers map
	 * @param params parameters map
	 * @param jsonEntity an object serialized to JSON byte array (payload), could be null
	 * @return a map containing the "Authorization" header
	 */
	public static Map<String, String> signRequest(String accessKey, String secretKey,
			String httpMethod, String endpointURL, String reqPath,
			Map<String, String> headers, MultivaluedMap<String, String> params, byte[] jsonEntity) {

		if (StringUtils.isBlank(accessKey) || StringUtils.isBlank(secretKey)) {
			logger.warn("Blank access key or secret key!");
			accessKey = "";
			secretKey = "";
		}

		if (httpMethod == null) {
			httpMethod = HttpMethod.GET;
		}

		InputStream in = null;
		Map<String, String> sigParams = new HashMap<String, String>();

		if (params != null) {
			for (Map.Entry<String, List<String>> param : params.entrySet()) {
				String key = param.getKey();
				List<String> value = param.getValue();
				if (value != null && !value.isEmpty() && value.get(0) != null) {
					sigParams.put(key, value.get(0));
				}
			}
		}

		if (jsonEntity != null && jsonEntity.length > 0) {
			in = new BufferedInputStream(new ByteArrayInputStream(jsonEntity));
		}

		return signer.sign(httpMethod, endpointURL, reqPath, headers, sigParams, in, accessKey, secretKey);
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
		Response entityRes = getEntity(is, Map.class);
		if (entityRes.getStatusInfo() == Response.Status.OK) {
			Map<String, Object> newContent = (Map<String, Object>) entityRes.getEntity();
			// type is not fount in datatypes (try to get it from req. body)
			if (!StringUtils.isBlank(type)) {
				newContent.put(Config._TYPE, type);
			}
			content = Utils.setAnnotatedFields(newContent);
			content.setAppid(app.getAppIdentifier());
			registerNewTypes(app, content);
			// The reason why we do two validation passes is because we want to return
			// the errors through the API and notify the end user.
			// This is the primary validation pass (validates not only core POJOS but also user defined objects).
			String[] errors = ValidationUtils.validateObject(app, content);
			if (errors.length == 0) {
				// Secondary validation pass: object is validated again before being created
				String id = content.create();
				if (id == null) {
					return getStatusResponse(Response.Status.BAD_REQUEST, "Failed to create object.");
				} else {
					return Response.created(URI.create(Utils.urlEncode(content.getObjectURI()))).entity(content).build();
				}
			}
			return getStatusResponse(Response.Status.BAD_REQUEST, errors);
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
				Utils.setAnnotatedFields(object, newContent, Locked.class);
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
				ParaObject pobj = Utils.setAnnotatedFields(object);
				if (pobj != null && ValidationUtils.isValidObject(pobj)) {
					pobj.setAppid(app.getAppIdentifier());
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
					ParaObject pobj = Utils.setAnnotatedFields(null, item, Locked.class);
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
			return getExceptionResponse(status.getStatusCode(), msg);
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

	/**
	 * Returns a list of validation constraints for the given app.
	 * @param app the current App object
	 * @param type the type
	 * @return a response 200
	 */
	public static Response getConstraintsResponse(App app, String type) {
		if (type != null) {
			return Response.ok(Utils.getJsonMapper().createObjectNode().putPOJO(StringUtils.capitalize(type),
					ValidationUtils.getValidationConstraints(app, type))).build();
		} else {
			return Response.ok(ValidationUtils.getAllValidationConstraints(app)).build();
		}
	}

	/**
	 * Adds a new constraint to the list of constraint for the given app.
	 * @param app the current App object
	 * @param type the type
	 * @param field the field
	 * @param cname the constraint name
	 * @param in JSON payload
	 * @return the updated list of constraints (code 200)
	 */
	public static Response addCostraintsResponse(App app, String type, String field, String cname, InputStream in) {
		if (app != null) {
			Response payloadRes = RestUtils.getEntity(in, Map.class);
			if (payloadRes.getStatusInfo() == Response.Status.OK) {
				Map<String, Object> payload = (Map<String, Object>) payloadRes.getEntity();
				if (app.addValidationConstraint(type, field, Constraint.build(cname, payload))) {
					app.update();
				}
			}
			return Response.ok(app.getValidationConstraints().get(type)).build();
		} else {
			return getStatusResponse(Response.Status.NOT_FOUND);
		}
	}

	/**
	 * Removes a constraint from the list of constraint for the given app.
	 * @param app the current App object
	 * @param type the type
	 * @param field the field
	 * @param cname the constraint name
	 * @return the updated list of constraints (code 200)
	 */
	public static Response removeCostraintsResponse(App app, String type, String field, String cname) {
		if (app != null) {
			if (app.removeValidationConstraint(type, field, cname)) {
				app.update();
			}
			return Response.ok(app.getValidationConstraints().get(type)).build();
		} else {
			return getStatusResponse(Response.Status.NOT_FOUND);
		}
	}

	/////////////////////////////////////////////
	//	    	  EXCEPTIONS
	/////////////////////////////////////////////

	/**
	 * Returns an exception/error response as a JSON object.
	 * @param status HTTP status code
	 * @param msg message
	 * @return a JSON object
	 */
	public static Response getExceptionResponse(final int status, final String msg) {
		return Response.status(status).entity(new LinkedHashMap<String, Object>() {
			private static final long serialVersionUID = 1L;
			{
				put("code", status);
				put("message", msg);
			}
		}).type(MediaType.APPLICATION_JSON).build();
	}

}
