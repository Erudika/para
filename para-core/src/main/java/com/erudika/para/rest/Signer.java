/*
 * Copyright 2013-2016 Erudika. http://erudika.com
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

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.DefaultRequest;
import com.amazonaws.Request;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.http.HttpMethodName;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.utils.Config;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class extends {@code AWS4Signer} implementing the AWS Signature Version 4 algorithm.
 * Also contains a method for signature validation. The signatures that this class produces are
 * compatible with the original AWS SDK implementation.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class Signer extends AWS4Signer {

	private static final Logger logger = LoggerFactory.getLogger(Signer.class);
	private static final DateTimeFormatter timeFormatter = DateTimeFormat.
			forPattern("yyyyMMdd'T'HHmmss'Z'").withZoneUTC();

	/**
	 * No-args constructor.
	 */
	public Signer() {
		super(false);
		super.setServiceName(Config.PARA);
	}

	/**
	 * Signs a request using AWS signature V4.
	 * @param httpeMethod GET/POST/PUT... etc.
	 * @param endpoint the hostname of the API server
	 * @param resourcePath the path of the resource (starting from root e.g. "/path/to/res")
	 * @param headers the headers map
	 * @param params the params map
	 * @param entity the entity object or null
	 * @param accessKey the app's access key
	 * @param secretKey the app's secret key
	 * @return a signed request. The actual signature is inside the {@code Authorization} header.
	 */
	public Map<String, String> sign(String httpeMethod, String endpoint, String resourcePath,
			Map<String, String> headers, Map<String, String> params, InputStream entity,
			String accessKey, String secretKey) {

		Request<?> req = buildAWSRequest(httpeMethod, endpoint, resourcePath, headers, params, entity);
		sign(req, accessKey, secretKey);
		return req.getHeaders();
	}

	/**
	 * Signs a request using AWS signature V4.
	 * @param request the request instance
	 * @param accessKey the app's access key
	 * @param secretKey the app's secret key
	 */
	public void sign(Request<?> request, String accessKey, String secretKey) {
		super.sign(request, new BasicAWSCredentials(accessKey, secretKey));
		resetDate();
	}

	/**
	 * Validates the signature of the request.
	 * @param incoming the incoming HTTP request containing a signature
	 * @param secretKey the app's secret key
	 * @return true if the signature is valid
	 */
	public boolean isValidSignature(HttpServletRequest incoming, String secretKey) {
		if (incoming == null || StringUtils.isBlank(secretKey)) {
			return false;
		}
		String auth = incoming.getHeader(HttpHeaders.AUTHORIZATION);
		String givenSig = StringUtils.substringAfter(auth, "Signature=");
		String sigHeaders = StringUtils.substringBetween(auth, "SignedHeaders=", ",");
		String credential = StringUtils.substringBetween(auth, "Credential=", ",");
		String accessKey = StringUtils.substringBefore(credential, "/");

		if (StringUtils.isBlank(auth)) {
			givenSig = incoming.getParameter("X-Amz-Signature");
			sigHeaders = incoming.getParameter("X-Amz-SignedHeaders");
			credential = incoming.getParameter("X-Amz-Credential");
			accessKey = StringUtils.substringBefore(credential, "/");
		}

		Request<?> awsReq = buildAWSRequest(incoming, new HashSet<String>(Arrays.asList(sigHeaders.split(";"))));
		sign(awsReq, accessKey, secretKey);

		String auth2 = awsReq.getHeaders().get(HttpHeaders.AUTHORIZATION);
		String recreatedSig = StringUtils.substringAfter(auth2, "Signature=");

		return StringUtils.equals(givenSig, recreatedSig);
	}

	@Override
	public void setRegionName(String regionName) {
	}

	/**
	 * The region name.
	 * @return {@link Config#AWS_REGION}
	 */
	@Override
	public String getRegionName() {
		return Config.AWS_REGION;
	}

	private void resetDate() {
		overriddenDate = null;
	}

	private Request<?> buildAWSRequest(String httpMethod, String endpoint, String resourcePath,
			Map<String, String> headers, Map<String, String> params, InputStream entity) {
		Request<?> r = new DefaultRequest<AmazonWebServiceRequest>(Config.PARA);

		if (!StringUtils.isBlank(httpMethod)) {
			r.setHttpMethod(HttpMethodName.valueOf(httpMethod));
		}
		if (!StringUtils.isBlank(endpoint)) {
			if (!endpoint.startsWith("http")) {
				endpoint = "https://" + endpoint;
			}
			r.setEndpoint(URI.create(endpoint));
		}
		if (!StringUtils.isBlank(resourcePath)) {
			r.setResourcePath(resourcePath);
		}
		if (headers != null) {
			if (headers.containsKey("x-amz-date")) {
				overriddenDate = parseAWSDate(headers.get("x-amz-date"));
			}
			// we don't need these here, added by default
			headers.remove("host");
			headers.remove("x-amz-date");
			r.setHeaders(headers);
		}
		if (params != null) {
			for (Map.Entry<String, String> param : params.entrySet()) {
				r.addParameter(param.getKey(), param.getValue());
			}
		}
		if (entity != null) {
			r.setContent(entity);
		}
		return r;
	}

	private Request<?> buildAWSRequest(HttpServletRequest req, Set<String> headersUsed) {
		Map<String, String> headers = new HashMap<String, String>();
		for (Enumeration<String> e = req.getHeaderNames(); e.hasMoreElements();) {
			String head = e.nextElement().toLowerCase();
			if (headersUsed.contains(head)) {
				headers.put(head, req.getHeader(head));
			}
		}

		Map<String, String> params = new HashMap<String, String>();
		for (Map.Entry<String, String[]> param : req.getParameterMap().entrySet()) {
			params.put(param.getKey(), param.getValue()[0]);
		}

		String path = req.getRequestURI();
		String endpoint = StringUtils.removeEndIgnoreCase(req.getRequestURL().toString(), path);
		String httpMethod = req.getMethod();
		InputStream entity;
		try {
			entity = new BufferedInputStream(req.getInputStream());
			if (entity.available() <= 0) {
				entity = null;
			}
		} catch (IOException ex) {
			logger.error(null, ex);
			entity = null;
		}

		return buildAWSRequest(httpMethod, endpoint, path, headers, params, entity);
	}

	/**
	 * Returns a parsed Date
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
	 * Builds, signs and executes a request to an API endpoint using the provided credentials.
	 * Signs the request using the Amazon Signature 4 algorithm and returns the response.
	 * @param apiClient Jersey Client object
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
	public Response invokeSignedRequest(Client apiClient, String accessKey, String secretKey,
			String httpMethod, String endpointURL, String reqPath, Map<String, String> headers,
			MultivaluedMap<String, String> params, Entity<?> entity) {
		byte[] jsonEntity = null;
		if (entity != null) {
			try {
				jsonEntity = ParaObjectUtils.getJsonWriter().writeValueAsBytes(entity.getEntity());
			} catch (JsonProcessingException ex) {
				jsonEntity = null;
				logger.error(null, ex);
			}
		}
		return invokeSignedRequest(apiClient, accessKey, secretKey, httpMethod,
				endpointURL, reqPath, headers, params, jsonEntity);
	}

	/**
	 * Builds, signs and executes a request to an API endpoint using the provided credentials.
	 * Signs the request using the Amazon Signature 4 algorithm and returns the response.
	 * @param apiClient Jersey Client object
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
	public Response invokeSignedRequest(Client apiClient, String accessKey, String secretKey,
			String httpMethod, String endpointURL, String reqPath,
			Map<String, String> headers, MultivaluedMap<String, String> params, byte[] jsonEntity) {

		boolean isJWT = StringUtils.startsWithIgnoreCase(secretKey, "Bearer");

		WebTarget target = apiClient.target(endpointURL).path(reqPath);
		Map<String, String> signedHeaders = null;
		if (!isJWT) {
			signedHeaders = signRequest(accessKey, secretKey, httpMethod, endpointURL, reqPath,
					headers, params, jsonEntity);
		}

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

		if (isJWT) {
			builder.header(HttpHeaders.AUTHORIZATION, secretKey);
		} else {
			builder.header(HttpHeaders.AUTHORIZATION, signedHeaders.get(HttpHeaders.AUTHORIZATION)).
					header("X-Amz-Date", signedHeaders.get("X-Amz-Date"));
		}

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
	public Map<String, String> signRequest(String accessKey, String secretKey,
			String httpMethod, String endpointURL, String reqPath,
			Map<String, String> headers, MultivaluedMap<String, String> params, byte[] jsonEntity) {

		if (StringUtils.isBlank(accessKey)) {
			logger.error("Blank access key: {} {}", httpMethod, reqPath);
			return headers;
		}

		if (StringUtils.isBlank(secretKey)) {
			logger.debug("Anonymous request: {} {}", httpMethod, reqPath);
			// guest access
			headers.put("Authorization", "Anonymous " + accessKey);
			return headers;
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

		return sign(httpMethod, endpointURL, reqPath, headers, sigParams, in, accessKey, secretKey);
	}
}
