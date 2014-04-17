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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
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
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Java REST client
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class ParaClient {

	private static final Logger logger = LoggerFactory.getLogger(ParaClient.class);
	private static final String DEFAULT_ENDPOINT = "http://localhost:8080";
	private static final String DEFAULT_PATH = "/v1/";
	private static final String JSON = MediaType.APPLICATION_JSON; // + "; charset=utf-8"?
	private static final String GET = HttpMethod.GET;
	private static final String PUT = HttpMethod.PUT;
	private static final String POST = HttpMethod.POST;
	private static final String DELETE = HttpMethod.DELETE;
	private Signer signer = new Signer();
	private Client client = ClientBuilder.newClient();
	private String endpoint;
	private String path;
	private String accessKey;
	private String secretKey;

	public ParaClient(String accessKey, String secretKey) {
		this.accessKey = accessKey;
		this.secretKey = secretKey;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getEndpoint() {
		if (StringUtils.isBlank(endpoint)) {
			return DEFAULT_ENDPOINT;
		} else {
			return endpoint;
		}
	}

	public void setApiPath(String path) {
		this.path = path;
	}

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

	public long getTimestamp() {
//		Map<String, String> params = new HashMap<String, String>();
		Map<String, String> params = Collections.singletonMap("method", "timestamp");
		Map<String, Object> res = getEntity(invokeGet("utils", params), Map.class);
		return (long) (res != null ? res.get("value") : 0L);
	}

	@SuppressWarnings("unchecked")
	private <T> T getEntity(Response res, Class<?> type) {
		if (res != null) {
			if (res.getStatus() == Response.Status.OK.getStatusCode()) {
				return (T) res.readEntity(type);
			} else {
				throw new WebApplicationException(res);
			}
		} else {
			throw new WebApplicationException(res);
		}
	}

	private Response invokeGet(String resourcePath, Map<String, String> params) {
		return invoke(GET, resourcePath, null, params, null);
	}

	private Response invoke(String httpMethod, String resourcePath,
			Map<String, String> headers, Map<String, String> params, Entity<?> entity) {

		if (StringUtils.isBlank(accessKey) || StringUtils.isBlank(secretKey)) {
			logger.error("Blank access key or secret key!");
			accessKey = "";
			secretKey = "";
		}

		if (httpMethod == null) {
			httpMethod = GET;
		}
		if (resourcePath == null) {
			resourcePath = "";
		}

		String reqPath = getApiPath() + resourcePath;
		WebTarget target = client.target(getEndpoint()).path(reqPath);
		InputStream in = null;

		if (params != null) {
			for (Map.Entry<String, String> param : params.entrySet()) {
				target = target.queryParam(param.getKey(), param.getValue());
			}
		}

		Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON);

		if (headers != null) {
			for (Map.Entry<String, String> header : headers.entrySet()) {
				builder.header(header.getKey(), header.getValue());
			}
		}

		if (entity != null) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos;
			try {
				oos = new ObjectOutputStream(baos);
				oos.writeObject(entity.getEntity());
				oos.flush();
				oos.close();
				in = new ByteArrayInputStream(baos.toByteArray());
			} catch (IOException ex) {
				logger.error(null, ex);
			}
		}

		Request<?> signed = signer.sign(httpMethod, getEndpoint(), reqPath, headers, params, in, accessKey, secretKey);

		builder.header(HttpHeaders.AUTHORIZATION, signed.getHeaders().get(HttpHeaders.AUTHORIZATION)).
				header("X-Amz-Date", signed.getHeaders().get("X-Amz-Date"));

		if (entity != null) {
			return builder.method(httpMethod, entity);
		} else {
			return builder.method(httpMethod);
		}
	}
}
