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

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.DefaultRequest;
import com.amazonaws.Request;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.http.HttpMethodName;
import com.erudika.para.utils.Config;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
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
				overriddenDate = RestUtils.parseAWSDate(headers.get("x-amz-date"));
			}
			// we don't need these here, added by default
			headers.remove("host");
			headers.remove("x-amz-date");
			r.setHeaders(headers);
		}
		if (params != null) {
			r.setParameters(params);
		}
		if (entity != null && (httpMethod.equals(HttpMethod.POST) || httpMethod.equals(HttpMethod.PUT))) {
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

}
