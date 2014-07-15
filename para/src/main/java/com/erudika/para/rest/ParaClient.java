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
import com.erudika.para.core.ParaObject;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jetty.connector.JettyConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Java REST client
 *
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

	@SuppressWarnings("unchecked")
	private <T> T getEntity(Response res, Class<?> type) {
		if (res != null) {
			if (res.getStatus() == Response.Status.OK.getStatusCode()
					|| res.getStatus() == Response.Status.CREATED.getStatusCode()
					|| res.getStatus() == Response.Status.NOT_MODIFIED.getStatusCode()) {
				return (T) res.readEntity(type);
			} else {
				Map<String, Object> error = res.readEntity(Map.class);
				if (error != null && error.containsKey("code")) {
					throw new WebApplicationException((String) error.get("message"), (int) error.get("code"));
				}
			}
		}
		return null;
	}

	private Response invokeGet(String resourcePath, Map<String, String> params) {
		return invoke(GET, resourcePath, null, params, null);
	}

	private Response invokePost(String resourcePath, Map<String, String> params, Entity<?> entity) {
		return invoke(POST, resourcePath, null, params, entity);
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
		Entity<?> jsonPayload = null;

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
			try {
				byte[] entt = Utils.getJsonWriter().writeValueAsBytes(entity.getEntity());
				in = new BufferedInputStream(new ByteArrayInputStream(entt));
				jsonPayload = Entity.json(new String(entt, Config.DEFAULT_ENCODING));
			} catch (IOException ex) {
				logger.error(null, ex);
			}
		}

		Request<?> signed = signer.sign(httpMethod, getEndpoint(), reqPath, headers, params, in, accessKey, secretKey);

		builder.header(HttpHeaders.AUTHORIZATION, signed.getHeaders().get(HttpHeaders.AUTHORIZATION)).
				header("X-Amz-Date", signed.getHeaders().get("X-Amz-Date"));

		if (jsonPayload != null) {
			return builder.method(httpMethod, jsonPayload);
		} else {
			return builder.method(httpMethod);
		}
	}

	public void close() {
		if (client != null) {
			client.close();
		}
	}

	/////////////////////////////////////////////
	//				API METHODS
	/////////////////////////////////////////////
	public <P extends ParaObject> P create(ParaObject obj) {
		if (obj == null) {
			return null;
		}
		return getEntity(invokePost(obj.getType(), null, Entity.json(obj)), obj.getClass());
	}

	public <P extends ParaObject> P read(String type, String id) {
		if (type == null || id == null) {
			return null;
		}
		return getEntity(invokeGet(type.concat("/").concat(id), null), Utils.toClass(type));
	}

	public long getTimestamp() {
		Long res = getEntity(invokeGet("utils/timestamp", null), Long.class);
		return res != null ? res : 0L;
	}
}
