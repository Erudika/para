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
package com.erudika.para.client;

import com.erudika.para.core.App;
import static com.erudika.para.core.App.AllowedMethods.DELETE;
import static com.erudika.para.core.App.AllowedMethods.GET;
import static com.erudika.para.core.App.AllowedMethods.PATCH;
import static com.erudika.para.core.App.AllowedMethods.POST;
import static com.erudika.para.core.App.AllowedMethods.PUT;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import com.erudika.para.core.rest.Signer;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.Pager;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.utils.Utils;
import com.erudika.para.core.validation.Constraint;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import nl.altindag.ssl.SSLFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Java REST client for communicating with a Para API server.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class ParaClient implements Closeable {

	private static final Logger logger = LoggerFactory.getLogger(ParaClient.class);
	private static final String DEFAULT_ENDPOINT = "https://paraio.com";
	private static final String DEFAULT_PATH = "/v1/";
	private static final String JWT_PATH = "/jwt_auth";

	private final String protocols = Para.getConfig().clientSslProtocols();
	private final String keystorePath = Para.getConfig().clientSslKeystore();
	private final String keystorePass = Para.getConfig().clientSslKeystorePassword();
	private final String truststorePath = Para.getConfig().clientSslTruststore();
	private final String truststorePass = Para.getConfig().clientSslTruststorePassword();

	private String endpoint;
	private String path;
	private String accessKey;
	private String secretKey;
	private String tokenKey;
	private Long tokenKeyExpires;
	private Long tokenKeyNextRefresh;
	private int chunkSize = 0;
	private boolean throwExceptionOnHTTPError;
	private final CloseableHttpClient httpclient;
	private final ObjectMapper mapper;

	/**
	 * Default constructor.
	 * @param accessKey app access key
	 * @param secretKey app secret key
	 */
	public ParaClient(String accessKey, String secretKey) {
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		if (StringUtils.length(secretKey) < 6) {
			logger.warn("Secret key appears to be invalid. Make sure you call 'signIn()' first.");
		}
		this.throwExceptionOnHTTPError = false;
		mapper = ParaObjectUtils.getJsonMapper();
		mapper.setSerializationInclusion(JsonInclude.Include.USE_DEFAULTS);

		SSLFactory sslFactory = null;
		if (!StringUtils.isBlank(truststorePath)) {
			sslFactory = SSLFactory.builder()
					.withTrustMaterial(Paths.get(truststorePath), truststorePass.toCharArray())
					.withProtocols(protocols).build();
		}
		if (!StringUtils.isBlank(keystorePath)) {
			sslFactory = SSLFactory.builder()
					.withIdentityMaterial(Paths.get(keystorePath), keystorePass.toCharArray())
					.withTrustMaterial(Paths.get(truststorePath), truststorePass.toCharArray())
					.withProtocols(protocols).build();
		}
		if (sslFactory == null) {
			sslFactory = SSLFactory.builder().withDefaultTrustMaterial().build();
		}
		HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create().
				setTlsSocketStrategy(new DefaultClientTlsStrategy(sslFactory.getSslContext(),
						sslFactory.getHostnameVerifier())).build();

		int timeout = 30;
		this.httpclient = HttpClientBuilder.create().
				setConnectionManager(cm).
				setConnectionReuseStrategy((HttpRequest hr, HttpResponse hr1, HttpContext hc) -> false).
				setDefaultRequestConfig(RequestConfig.custom().
						setConnectionRequestTimeout(timeout, TimeUnit.SECONDS).
						build()).
				build();
	}


	/**
	 * Sets the host URL of the Para server.
	 * @param endpoint the Para server location
	 */
	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	/**
	 * Closes the underlying Jersey client and releases resources.
	 */
	public void close() {
		if (httpclient != null) {
			try {
				httpclient.close();
			} catch (IOException ex) {
				logger.error(null, ex);
			}
		}
	}

	/**
	 * Returns the {@link App} for the current access key (appid).
	 * @return the App object
	 */
	public App getApp() {
		return me();
	}

	/**
	 * Returns the endpoint URL.
	 * @return the endpoint
	 */
	public String getEndpoint() {
		if (StringUtils.isBlank(endpoint)) {
			return DEFAULT_ENDPOINT;
		} else {
			return endpoint;
		}
	}

	/**
	 * Sets the API request path.
	 * @param path a new path
	 */
	public void setApiPath(String path) {
		this.path = path;
	}

	/**
	 * Returns the API request path.
	 * @return the request path without parameters
	 */
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

	/**
	 * Returns the JWT access token if any.
	 * @return the JWT access token, or null if not signed in
	 */
	public String getAccessToken() {
		return tokenKey;
	}

	/**
	 * Returns the Para server version.
	 * @return the version of Para server
	 */
	public String getServerVersion() {
		Map<?, ?> res = invokeGet("", null, Map.class);
		if (res == null || StringUtils.isBlank((String) res.get("version"))) {
			return "unknown";
		} else {
			return (String) res.get("version");
		}
	}

	/**
	 * Sets the JWT access token.
	 * @param token a valid token
	 */
	public void setAccessToken(String token) {
		if (!StringUtils.isBlank(token)) {
			try {
				String payload = Utils.base64dec(StringUtils.substringBetween(token, ".", "."));
				Map<?, ?> decoded = mapper.readValue(payload, Map.class);
				if (decoded != null && decoded.containsKey("exp")) {
					this.tokenKeyExpires = (Long) decoded.get("exp");
					this.tokenKeyNextRefresh = (Long) decoded.get("refresh");
				}
			} catch (Exception ex) {
				this.tokenKeyExpires = null;
				this.tokenKeyNextRefresh = null;
			}
		}
		this.tokenKey = token;
	}

	/**
	 * Clears the JWT token from memory, if such exists.
	 */
	private void clearAccessToken() {
		tokenKey = null;
		tokenKeyExpires = null;
		tokenKeyNextRefresh = null;
	}

	/**
	 * Sets the chunk size for batch CRUD operations. If chunkSize is greater than zero, any requests made to
	 * createAll(), readAll(), updateAll() and deleteAll() will be partitioned into chunks equal to this size.
	 * The chunked requests made to Para Server will be executed synchronously, and the results of all chunk
	 * operations are aggregated into a single response.
	 * @param chunkSize the number of objects per chunk
	 */
	public void setChunkSize(int chunkSize) {
		this.chunkSize = chunkSize;
	}

	/**
	 * Returns the batch chunk size.
	 * @return the chunk size used for batch CRUD operations
	 */
	public int getChunkSize() {
		return chunkSize;
	}

	/**
	 * Enable/disable exception throwing in ParaClient.
	 * @param enabled if true, the client will throw an exception when an error response is received.
	 * If false, the error is only logged. Default is false.
	 */
	public void throwExceptionOnHTTPError(boolean enabled) {
		this.throwExceptionOnHTTPError = enabled;
	}

	private String key(boolean refresh) {
		if (tokenKey != null) {
			if (refresh) {
				refreshToken();
			}
			return "Bearer " + tokenKey;
		}
		return secretKey;
	}

	/**
	 * Deserializes a JSON response to POJO of some type.
	 * @param respBuilder response builder
	 * @param respEntity entity
	 * @param returnType return type
	 * @param statusCode status code
	 * @param reason reason phrase
	 * @throws IOException
	 */
	private <T> T readEntity(HttpEntity respEntity, Class<?> returnType, int statusCode, String reason) throws IOException {
		if (respEntity != null) {
			if (statusCode == HttpStatus.SC_OK
					|| statusCode == HttpStatus.SC_CREATED
					|| statusCode == HttpStatus.SC_NOT_MODIFIED) {
				return readEntity(respEntity, returnType);
			} else if (statusCode != HttpStatus.SC_NOT_FOUND
					&& statusCode != HttpStatus.SC_NOT_MODIFIED
					&& statusCode != HttpStatus.SC_NO_CONTENT) {
				Map<String, Object> error = readEntity(respEntity, Map.class);
				if (error != null && error.containsKey("code")) {
					String msg = error.containsKey("message") ? (String) error.get("message") : "error";
					RuntimeException e = new RuntimeException((Integer) error.get("code") + " - " + msg);
					logger.error("{} - {}", error.get("code"), e.getMessage());
					if (throwExceptionOnHTTPError) {
						throw e;
					}
				} else {
					logger.error("{} - {}", statusCode, reason);
					if (throwExceptionOnHTTPError) {
						throw new RuntimeException(statusCode + " - " + reason);
					}
				}
			}
		}
		return null;
	}

	/**
	 * Deserializes a JSON response to POJO.
	 * @param <T> type
	 * @param entity entity
	 * @param type class type
	 * @return a POJO or String
	 */
	@SuppressWarnings("unchecked")
	private <T> T readEntity(HttpEntity entity, Class<?> type) {
		try (InputStream in = entity.getContent()) {
			if (in != null && type != null) {
				if (type.isAssignableFrom(String.class)) {
					return (T) new String(IOUtils.toByteArray(in), Para.getConfig().defaultEncoding());
				} else {
					return mapper.readerFor(type).readValue(in);
				}
			}
		} catch (Exception ex) {
			logger.debug(null, ex);
		} finally {
			EntityUtils.consumeQuietly(entity);
		}
		return null;
	}

	/**
	 * @param resourcePath API subpath
	 * @return the full resource path, e.g. "/v1/path"
	 */
	protected String getFullPath(String resourcePath) {
		if (StringUtils.startsWith(resourcePath, JWT_PATH)) {
			if (StringUtils.countMatches(getApiPath(), "/") > 2) {
				String s = getApiPath().substring(0, getApiPath().indexOf("/", 1)) + resourcePath;
				return s;
			}
			return resourcePath;
		}
		if (resourcePath == null) {
			resourcePath = "";
		} else if (resourcePath.startsWith("/")) {
			resourcePath = resourcePath.substring(1);
		}
		return getApiPath() + resourcePath;
	}

	/**
	 * Invoke a GET request to the Para API.
	 * @param <T> return type
	 * @param resourcePath the subpath after '/v1/', should not start with '/'
	 * @param params query parameters
	 * @param returnType the type of object to return
	 * @return a POJO
	 */
	public <T> T invokeGet(String resourcePath, MultivaluedMap<String, String> params, Class<?> returnType) {
		logger.debug("GET {}, params: {}", getFullPath(resourcePath), params);
		return invokeSignedRequest(accessKey, key(!JWT_PATH.equals(resourcePath)), GET.toString(),
				getEndpoint(), getFullPath(resourcePath), null, params, null, returnType);
	}

	/**
	 * Invoke a POST request to the Para API.
	 * @param <T> return type
	 * @param resourcePath the subpath after '/v1/', should not start with '/'
	 * @param entity request body
	 * @param returnType the type of object to return
	 * @return a POJO
	 */
	public <T> T invokePost(String resourcePath, Object entity, Class<?> returnType) {
		logger.debug("POST {}, entity: {}", getFullPath(resourcePath), entity, returnType);
		return invokeSignedRequest(accessKey, key(true), POST.toString(),
				getEndpoint(), getFullPath(resourcePath), null, null, entity, returnType);
	}

	/**
	 * Invoke a PUT request to the Para API.
	 * @param <T> return type
	 * @param resourcePath the subpath after '/v1/', should not start with '/'
	 * @param entity request body
	 * @param returnType the type of object to return
	 * @return a POJO
	 */
	public <T> T invokePut(String resourcePath, Object entity, Class<?> returnType) {
		logger.debug("PUT {}, entity: {}", getFullPath(resourcePath), entity);
		return invokeSignedRequest(accessKey, key(true), PUT.toString(),
				getEndpoint(), getFullPath(resourcePath), null, null, entity, returnType);
	}

	/**
	 * Invoke a PATCH request to the Para API.
	 * @param <T> return type
	 * @param resourcePath the subpath after '/v1/', should not start with '/'
	 * @param entity request body
	 * @param returnType the type of object to return
	 * @return a POJO
	 */
	public <T> T invokePatch(String resourcePath, Object entity, Class<?> returnType) {
		logger.debug("PATCH {}, entity: {}", getFullPath(resourcePath), entity);
		return invokeSignedRequest(accessKey, key(true), PATCH.toString(),
				getEndpoint(), getFullPath(resourcePath), null, null, entity, returnType);
	}

	/**
	 * Invoke a DELETE request to the Para API.
	 * @param <T> return type
	 * @param resourcePath the subpath after '/v1/', should not start with '/'
	 * @param params query parameters
	 * @param returnType the type of object to return
	 * @return a POJO
	 */
	public <T> T invokeDelete(String resourcePath, MultivaluedMap<String, String> params, Class<?> returnType) {
		logger.debug("DELETE {}, params: {}", getFullPath(resourcePath), params);
		return invokeSignedRequest(accessKey, key(true), DELETE.toString(),
				getEndpoint(), getFullPath(resourcePath), null, params, null, returnType);
	}

	<T> T invokeSignedRequest(String accessKey, String secretKey,
			String method, String apiURL, String path,
			Map<String, String> headers, MultivaluedMap<String, String> params, Object entity, Class<?> returnType) {

		boolean isJWT = StringUtils.startsWithIgnoreCase(secretKey, "Bearer");

		try {
			String uri = getEndpoint() + path;
			Map<String, String> signedHeaders = new HashMap<>();
			byte[] jsonEntity = getJsonEntityAsBytes(entity);

			Signer signer = new Signer();
			if (!isJWT) {
				signedHeaders = signer.signRequest(accessKey, secretKey, method, getEndpoint(), path,
						headers, params, jsonEntity);
			}

			uri = setQueryParameters(uri, params);
			String reqDetails = Utils.formatMessage(" [{0} {1}]", method, uri);

			HttpUriRequest req = getHttpUriRequest(uri, method, jsonEntity);

			if (headers != null) {
				for (Map.Entry<String, String> header : headers.entrySet()) {
					req.addHeader(header.getKey(), header.getValue());
				}
			}

			if (isJWT) {
				req.setHeader(HttpHeaders.AUTHORIZATION, secretKey);
			} else {
				req.setHeader(HttpHeaders.AUTHORIZATION, signedHeaders.get(HttpHeaders.AUTHORIZATION));
				req.setHeader("X-Amz-Date", signedHeaders.get("X-Amz-Date"));
			}

			if (Para.getConfig().clientUserAgentEnabled()) {
				String userAgent = new StringBuilder("Para client ").append(Para.getVersion()).append(" ").append(accessKey).
						append(" (Java ").append(System.getProperty("java.runtime.version")).append(")").toString();
				req.setHeader(HttpHeaders.USER_AGENT, userAgent);
			}

			try {
				return httpclient.execute(req, (resp) -> {
					HttpEntity respEntity = resp.getEntity();
					int statusCode = resp.getCode();
					String reason = resp.getReasonPhrase() + reqDetails;
					return readEntity(respEntity, returnType, statusCode, reason);
				});
			} catch (Exception ex) {
				String msg = "Failed to execute signed " + method + " request to " + uri + ": " + ex.getMessage();
				if (throwExceptionOnHTTPError) {
					throw new RuntimeException(msg);
				} else {
					logger.error(msg + reqDetails);
				}
			}
		} catch (URISyntaxException ex) {
			logger.error(null, ex);
		}
		return null;
	}

	private byte[] getJsonEntityAsBytes(Object entity) {
		if (entity != null) {
			try {
				return ParaObjectUtils.getJsonWriterNoIdent().writeValueAsBytes(entity);
			} catch (JsonProcessingException ex) {
				logger.error(null, ex);
			}
		}
		return null;
	}

	private String setQueryParameters(String uri, MultivaluedMap<String, String> params) {
		if (params != null) {
			List<String> paramz = new LinkedList<>();
			for (Map.Entry<String, List<String>> param : params.entrySet()) {
				String key = param.getKey();
				List<String> value = param.getValue();
				if (value != null && !value.isEmpty() && value.get(0) != null) {
					for (String pv : value) {
						paramz.add(key + "=" + Utils.urlEncode(pv));
					}
				}
			}
			if (!paramz.isEmpty()) {
				uri = uri + "?" + String.join("&", paramz);
			}
		}
		return uri;
	}

	private HttpUriRequest getHttpUriRequest(String uri, String method, byte[] jsonEntity) throws URISyntaxException {
		HttpUriRequest req;
		switch (method) {
			case "GET":
				req = new HttpGet(uri);
				break;
			case "POST":
				req = new HttpPost(uri);
				if (jsonEntity != null) {
					((HttpPost) req).setEntity(new ByteArrayEntity(jsonEntity, ContentType.APPLICATION_JSON));
				}
				break;
			case "PUT":
				req = new HttpPut(uri);
				if (jsonEntity != null) {
					((HttpPut) req).setEntity(new ByteArrayEntity(jsonEntity, ContentType.APPLICATION_JSON));
				}
				break;
			case "PATCH":
				req = new HttpPatch(uri);
				if (jsonEntity != null) {
					((HttpPatch) req).setEntity(new ByteArrayEntity(jsonEntity, ContentType.APPLICATION_JSON));
				}
				break;
			case "DELETE":
				req = new HttpDelete(uri);
				break;
			default:
				throw new UnsupportedOperationException();
		}
		return req;
	}

	/**
	 * Converts a {@link Pager} object to query parameters.
	 * @param pager a Pager
	 * @return list of query parameters
	 */
	public MultivaluedMap<String, String> pagerToParams(Pager... pager) {
		MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
		if (pager != null && pager.length > 0) {
			Pager p = pager[0];
			if (p != null) {
				map.put("page", Collections.singletonList(Long.toString(p.getPage())));
				map.put("desc", Collections.singletonList(Boolean.toString(p.isDesc())));
				map.put("limit", Collections.singletonList(Integer.toString(p.getLimit())));
				if (p.getLastKey() != null) {
					map.put("lastKey", Collections.singletonList(p.getLastKey()));
				}
				if (p.getSortby() != null) {
					map.put("sort", Collections.singletonList(p.getSortby()));
				}
				if (!p.getSelect().isEmpty()) {
					map.put("select", Collections.singletonList(StringUtils.join(p.getSelect(), ",")));
				}
			}
		}
		return map;
	}

	/**
	 * Deserializes ParaObjects from a JSON array (the "items:[]" field in search results).
	 * @param <P> type
	 * @param result a list of deserialized maps
	 * @return a list of ParaObjects
	 */
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> getItemsFromList(List<?> result) {
		if (result != null && !result.isEmpty()) {
			// this isn't very efficient but there's no way to know what type of objects we're reading
			ArrayList<P> objects = new ArrayList<>(result.size());
			for (Object map : result) {
				P p = ParaObjectUtils.setAnnotatedFields((Map<String, Object>) map);
				if (p != null) {
					objects.add(p);
				}
			}
			return objects;
		}
		return Collections.emptyList();
	}

	/**
	 * Converts a list of Maps to a List of ParaObjects, at a given path within the JSON tree structure.
	 * @param <P> type
	 * @param at the path (field) where the array of objects is located
	 * @param result the response body for an API request
	 * @param pager a {@link Pager} object
	 * @return a list of ParaObjects
	 */
	public <P extends ParaObject> List<P> getItems(String at, Map<String, Object> result, Pager... pager) {
		if (result != null && !result.isEmpty() && !StringUtils.isBlank(at) && result.containsKey(at)) {
			if (pager != null && pager.length > 0 && pager[0] != null) {
				if (result.containsKey("totalHits")) {
					pager[0].setCount(((Integer) result.get("totalHits")).longValue());
				}
				if (result.containsKey("lastKey")) {
					pager[0].setLastKey((String) result.get("lastKey"));
				}
			}
			return getItemsFromList((List<?>) result.get(at));
		}
		return Collections.emptyList();
	}

	private <P extends ParaObject> List<P> getItems(Map<String, Object> result, Pager... pager) {
		return getItems("items", result, pager);
	}

	private int getNumChunks(List<?> objects, int size) {
		return size <= 0 ? 1 : (objects.size() + size - 1) / size;
	}

	private List<?> partitionList(List<?> objects, int i, int size) {
		return size <= 0 ? objects : objects.subList(i * size, Math.min((i + 1) * size, objects.size()));
	}

	/////////////////////////////////////////////
	//				 PERSISTENCE
	/////////////////////////////////////////////

	/**
	 * Persists an object to the data store. If the object's type and id are given,
	 * then the request will be a {@code PUT} request and any existing object will be
	 * overwritten.
	 * @param <P> the type of object
	 * @param obj the domain object
	 * @return the same object with assigned id or null if not created.
	 */
	public <P extends ParaObject> P create(P obj) {
		if (obj == null) {
			return null;
		}
		if (StringUtils.isBlank(obj.getId()) || StringUtils.isBlank(obj.getType())) {
			return invokePost(Utils.urlEncode(obj.getType()), obj, obj.getClass());
		} else {
			return invokePut(obj.getObjectURI(), obj, obj.getClass());
		}
	}

	/**
	 * Retrieves an object from the data store.
	 * @param <P> the type of object
	 * @param type the type of the object
	 * @param id the id of the object
	 * @return the retrieved object or null if not found
	 */
	public <P extends ParaObject> P read(String type, String id) {
		if (StringUtils.isBlank(type) || StringUtils.isBlank(id)) {
			return null;
		}

		return invokeGet(Utils.urlEncode(type).concat("/").concat(Utils.urlEncode(id)), null,
				ParaObjectUtils.toClass(type));
	}

	/**
	 * Retrieves an object from the data store.
	 * @param <P> the type of object
	 * @param id the id of the object
	 * @return the retrieved object or null if not found
	 */
	public <P extends ParaObject> P read(String id) {
		if (StringUtils.isBlank(id)) {
			return null;
		}
		Map<String, Object> data = invokeGet("_id/".concat(Utils.urlEncode(id)), null, Map.class);
		return ParaObjectUtils.setAnnotatedFields(data);
	}

	/**
	 * Updates an object permanently. Supports partial updates.
	 * @param <P> the type of object
	 * @param obj the object to update
	 * @return the updated object
	 */
	public <P extends ParaObject> P update(P obj) {
		if (obj == null) {
			return null;
		}
		return invokePatch(obj.getObjectURI(), obj, obj.getClass());
	}

	/**
	 * Deletes an object permanently.
	 * @param <P> the type of object
	 * @param obj the object
	 */
	public <P extends ParaObject> void delete(P obj) {
		if (obj == null || obj.getId() == null) {
			return;
		}
		invokeDelete(obj.getObjectURI(), null, null);
	}

	/**
	 * Saves multiple objects to the data store.
	 * @param <P> the type of object
	 * @param objects the list of objects to save
	 * @return a list of objects
	 */
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> createAll(List<P> objects) {
		if (objects == null || objects.isEmpty() || objects.get(0) == null) {
			return Collections.emptyList();
		}
		final int size = this.chunkSize;
		return IntStream.range(0, getNumChunks(objects, size))
				.mapToObj(i -> (List<P>) partitionList(objects, i, size))
				.map(chunk -> invokePost("_batch", chunk, List.class))
				.map(response -> (List<P>) response)
				.map(entity -> (List<P>) getItemsFromList(entity))
				.flatMap(List::stream)
				.collect(Collectors.toList());
	}

	/**
	 * Retrieves multiple objects from the data store.
	 * @param <P> the type of object
	 * @param keys a list of object ids
	 * @return a list of objects
	 */
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> readAll(List<String> keys) {
		if (keys == null || keys.isEmpty()) {
			return Collections.emptyList();
		}
		final int size = this.chunkSize;
		return IntStream.range(0, getNumChunks(keys, size))
				.mapToObj(i -> (List<String>) partitionList(keys, i, size))
				.map(chunk -> {
					MultivaluedMap<String, String> ids = new MultivaluedHashMap<>();
					ids.put("ids", chunk);
					return invokeGet("_batch", ids, List.class);
				})
				.map(response -> (List<P>) response)
				.map(entity -> (List<P>) getItemsFromList(entity))
				.flatMap(List::stream)
				.collect(Collectors.toList());
	}

	/**
	 * Updates multiple objects.
	 * @param <P> the type of object
	 * @param objects the objects to update
	 * @return a list of objects
	 */
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> updateAll(List<P> objects) {
		if (objects == null || objects.isEmpty()) {
			return Collections.emptyList();
		}
		final int size = this.chunkSize;
		return IntStream.range(0, getNumChunks(objects, size))
				.mapToObj(i -> (List<P>) partitionList(objects, i, size))
				.map(chunk -> invokePatch("_batch", chunk, List.class))
				.map(response -> (List<P>) response)
				.map(entity -> (List<P>) getItemsFromList(entity))
				.flatMap(List::stream)
				.collect(Collectors.toList());
	}

	/**
	 * Deletes multiple objects.
	 * @param keys the ids of the objects to delete
	 */
	@SuppressWarnings("unchecked")
	public void deleteAll(List<String> keys) {
		if (keys == null || keys.isEmpty()) {
			return;
		}
		final int size = (keys.size() > 100 && this.chunkSize <= 0) ? 50 : this.chunkSize;
		IntStream.range(0, getNumChunks(keys, size))
			.mapToObj(i -> (List<String>) partitionList(keys, i, size))
			.forEach(chunk -> {
				MultivaluedMap<String, String> ids = new MultivaluedHashMap<>();
				ids.put("ids", chunk);
				invokeDelete("_batch", ids, null);
			});
	}

	/**
	 * Returns a list all objects found for the given type.
	 * The result is paginated so only one page of items is returned, at a time.
	 * @param <P> the type of object
	 * @param type the type of objects to search for
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of objects
	 */
	public <P extends ParaObject> List<P> list(String type, Pager... pager) {
		if (StringUtils.isBlank(type)) {
			return Collections.emptyList();
		}
		return getItems(invokeGet(Utils.urlEncode(type), pagerToParams(pager), Map.class), pager);
	}

	/////////////////////////////////////////////
	//				 SEARCH
	/////////////////////////////////////////////

	/**
	 * Simple id search.
	 * @param <P> type of the object
	 * @param id the id
	 * @return the object if found or null
	 */
	public <P extends ParaObject> P findById(String id) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle(Config._ID, id);
		List<P> list = getItems(find("id", params));
		return list.isEmpty() ? null : list.get(0);
	}

	/**
	 * Simple multi id search.
	 * @param <P> type of the object
	 * @param ids a list of ids to search for
	 * @return a list of object found
	 */
	public <P extends ParaObject> List<P> findByIds(List<String> ids) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.put("ids", ids);
		return getItems(find("ids", params));
	}

	/**
	 * Search for {@link com.erudika.para.core.Address} objects in a radius of X km from a given point.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param query the query string
	 * @param radius the radius of the search circle
	 * @param lat latitude
	 * @param lng longitude
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findNearby(String type, String query, int radius, double lat, double lng,
			Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("latlng", lat + "," + lng);
		params.putSingle("radius", Integer.toString(radius));
		params.putSingle("q", query);
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("nearby", params), pager);
	}

	/**
	 * Searches for objects that have a property which value starts with a given prefix.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param field the property name of an object
	 * @param prefix the prefix
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findPrefix(String type, String field, String prefix, Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("field", field);
		params.putSingle("prefix", prefix);
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("prefix", params), pager);
	}

	/**
	 * Simple query string search. This is the basic search method.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param query the query string
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findQuery(String type, String query, Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("q", query);
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("", params), pager);
	}

	/**
	 * Searches within a nested field. The objects of the given type must contain a nested field "nstd".
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param field the name of the field to target (within a nested field "nstd")
	 * @param query the query string
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findNestedQuery(String type, String field, String query, Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("q", query);
		params.putSingle("field", field);
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("nested", params), pager);
	}

	/**
	 * Searches for objects that have similar property values to a given text. A "find like this" query.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param filterKey exclude an object with this key from the results (optional)
	 * @param fields a list of property names
	 * @param liketext text to compare to
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findSimilar(String type, String filterKey, String[] fields, String liketext,
			Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.put("fields", fields == null ? null : Arrays.asList(fields));
		params.putSingle("filterid", filterKey);
		params.putSingle("like", liketext);
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("similar", params), pager);
	}

	/**
	 * Searches for objects tagged with one or more tags.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param tags the list of tags
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findTagged(String type, String[] tags, Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.put("tags", tags == null ? null : Arrays.asList(tags));
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("tagged", params), pager);
	}

	/**
	 * Searches for {@link com.erudika.para.core.Tag} objects.
	 * This method might be deprecated in the future.
	 * @param <P> type of the object
	 * @param keyword the tag keyword to search for
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findTags(String keyword, Pager... pager) {
		keyword = (keyword == null) ? "*" : keyword.concat("*");
		return findWildcard(Utils.type(Tag.class), "tag", keyword, pager);
	}

	/**
	 * Searches for objects having a property value that is in list of possible values.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param field the property name of an object
	 * @param terms a list of terms (property values)
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findTermInList(String type, String field, List<String> terms, Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("field", field);
		params.put("terms", terms);
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("in", params), pager);
	}

	/**
	 * Searches for objects that have properties matching some given values. A terms query.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param terms a map of fields (property names) to terms (property values)
	 * @param matchAll match all terms. If true - AND search, if false - OR search
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findTerms(String type, Map<String, ?> terms, boolean matchAll,
			Pager... pager) {
		if (terms == null) {
			return Collections.emptyList();
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("matchall", Boolean.toString(matchAll));
		LinkedList<String> list = new LinkedList<>();
		for (Map.Entry<String, ? extends Object> term : terms.entrySet()) {
			String key = term.getKey();
			Object value = term.getValue();
			if (value != null) {
				list.add(key.concat(Para.getConfig().separator()).concat(value.toString()));
			}
		}
		if (!terms.isEmpty()) {
			params.put("terms", list);
		}
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("terms", params), pager);
	}

	/**
	 * Searches for objects that have a property with a value matching a wildcard query.
	 * @param <P> type of the object
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param field the property name of an object
	 * @param wildcard wildcard query string. For example "cat*".
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of objects found
	 */
	public <P extends ParaObject> List<P> findWildcard(String type, String field, String wildcard, Pager... pager) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("field", field);
		params.putSingle("q", wildcard);
		params.putSingle(Config._TYPE, type);
		params.putAll(pagerToParams(pager));
		return getItems(find("wildcard", params), pager);
	}

	/**
	 * Counts indexed objects.
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @return the number of results found
	 */
	public Long getCount(String type) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle(Config._TYPE, type);
		Pager pager = new Pager();
		getItems(find("count", params), pager);
		return pager.getCount();
	}

	/**
	 * Counts indexed objects matching a set of terms/values.
	 * @param type the type of object to search for. See {@link com.erudika.para.core.ParaObject#getType()}
	 * @param terms a list of terms (property values)
	 * @return the number of results found
	 */
	public Long getCount(String type, Map<String, ?> terms) {
		if (terms == null) {
			return 0L;
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		LinkedList<String> list = new LinkedList<>();
		for (Map.Entry<String, ? extends Object> term : terms.entrySet()) {
			String key = term.getKey();
			Object value = term.getValue();
			if (value != null) {
				list.add(key.concat(Para.getConfig().separator()).concat(value.toString()));
			}
		}
		if (!terms.isEmpty()) {
			params.put("terms", list);
		}
		params.putSingle(Config._TYPE, type);
		params.putSingle("count", "true");
		Pager pager = new Pager();
		getItems(find("terms", params), pager);
		return pager.getCount();
	}

	private Map<String, Object> find(String queryType, MultivaluedMap<String, String> params) {
		Map<String, Object> map = new HashMap<>();
		if (params != null && !params.isEmpty()) {
			String qType = StringUtils.isBlank(queryType) ? "/default" : "/".concat(queryType);
			if (StringUtils.isBlank(params.getFirst(Config._TYPE))) {
				return invokeGet("search" + qType, params, Map.class);
			} else {
				return invokeGet(params.getFirst(Config._TYPE) + "/search" + qType, params, Map.class);
			}
		} else {
			map.put("items", Collections.emptyList());
			map.put("totalHits", 0);
		}
		return map;
	}

	/////////////////////////////////////////////
	//				 LINKS
	/////////////////////////////////////////////

	/**
	 * Count the total number of links between this object and another type of object.
	 * @param type2 the other type of object
	 * @param obj the object to execute this method on
	 * @return the number of links for the given object
	 */
	public Long countLinks(ParaObject obj, String type2) {
		if (obj == null || obj.getId() == null || type2 == null) {
			return 0L;
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("count", "true");
		Pager pager = new Pager();
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), Utils.urlEncode(type2));
		getItems(invokeGet(url, params, Map.class), pager);
		return pager.getCount();
	}

	/**
	 * Returns all objects linked to the given one. Only applicable to many-to-many relationships.
	 * @param <P> type of linked objects
	 * @param type2 type of linked objects to search for
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of linked objects
	 */
	public <P extends ParaObject> List<P> getLinkedObjects(ParaObject obj, String type2, Pager... pager) {
		if (obj == null || obj.getId() == null || type2 == null) {
			return Collections.emptyList();
		}
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), Utils.urlEncode(type2));
		return getItems(invokeGet(url, pagerToParams(pager), Map.class), pager);
	}

	/**
	 * Searches through all linked objects in many-to-many relationships.
	 * @param <P> type of linked objects
	 * @param type2 type of linked objects to search for
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @param field the name of the field to target (within a nested field "nstd")
	 * @param query a query string
	 * @return a list of linked objects matching the search query
	 */
	public <P extends ParaObject> List<P> findLinkedObjects(ParaObject obj, String type2, String field,
			String query, Pager... pager) {
		if (obj == null || obj.getId() == null || type2 == null) {
			return Collections.emptyList();
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("field", field);
		params.putSingle("q", (query == null) ? "*" : query);
		params.putAll(pagerToParams(pager));
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), Utils.urlEncode(type2));
		return getItems(invokeGet(url, params, Map.class), pager);
	}

	/**
	 * Checks if this object is linked to another.
	 * @param type2 the other type
	 * @param id2 the other id
	 * @param obj the object to execute this method on
	 * @return true if the two are linked
	 */
	public boolean isLinked(ParaObject obj, String type2, String id2) {
		if (obj == null || obj.getId() == null || type2 == null || id2 == null) {
			return false;
		}
		String url = Utils.formatMessage("{0}/links/{1}/{2}", obj.getObjectURI(),
				Utils.urlEncode(type2), Utils.urlEncode(id2));
		Boolean result = (Boolean) invokeGet(url, null, Boolean.class);
		return result != null && result;
	}

	/**
	 * Checks if a given object is linked to this one.
	 * @param toObj the other object
	 * @param obj the object to execute this method on
	 * @return true if linked
	 */
	public boolean isLinked(ParaObject obj, ParaObject toObj) {
		if (obj == null || obj.getId() == null || toObj == null || toObj.getId() == null) {
			return false;
		}
		return isLinked(obj, toObj.getType(), toObj.getId());
	}

	/**
	 * Links an object to this one in a many-to-many relationship.
	 * Only a link is created. Objects are left untouched.
	 * The type of the second object is automatically determined on read.
	 * @param id2 link to the object with this id
	 * @param obj the object to execute this method on
	 * @return the id of the {@link com.erudika.para.core.Linker} object that is created
	 */
	public String link(ParaObject obj, String id2) {
		if (obj == null || obj.getId() == null || id2 == null) {
			return null;
		}
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), Utils.urlEncode(id2));
		return (String) invokePost(url, null, String.class);
	}

	/**
	 * Unlinks an object from this one.
	 * Only a link is deleted. Objects are left untouched.
	 * @param type2 the other type
	 * @param obj the object to execute this method on
	 * @param id2 the other id
	 */
	public void unlink(ParaObject obj, String type2, String id2) {
		if (obj == null || obj.getId() == null || type2 == null || id2 == null) {
			return;
		}
		String url = Utils.formatMessage("{0}/links/{1}/{2}", obj.getObjectURI(),
				Utils.urlEncode(type2), Utils.urlEncode(id2));
		invokeDelete(url, null, null);
	}

	/**
	 * Unlinks all objects that are linked to this one.
	 * @param obj the object to execute this method on
	 * Only {@link com.erudika.para.core.Linker} objects are deleted.
	 * {@link com.erudika.para.core.ParaObject}s are left untouched.
	 */
	public void unlinkAll(ParaObject obj) {
		if (obj == null || obj.getId() == null) {
			return;
		}
		String url = Utils.formatMessage("{0}/links", obj.getObjectURI());
		invokeDelete(url, null, null);
	}

	/**
	 * Count the total number of child objects for this object.
	 * @param type2 the type of the other object
	 * @param obj the object to execute this method on
	 * @return the number of links
	 */
	public Long countChildren(ParaObject obj, String type2) {
		if (obj == null || obj.getId() == null || type2 == null) {
			return 0L;
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("count", "true");
		params.putSingle("childrenonly", "true");
		Pager pager = new Pager();
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), Utils.urlEncode(type2));
		getItems(invokeGet(url, params, Map.class), pager);
		return pager.getCount();
	}

	/**
	 * Returns all child objects linked to this object.
	 * @param <P> the type of children
	 * @param type2 the type of children to look for
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of {@link ParaObject} in a one-to-many relationship with this object
	 */
	public <P extends ParaObject> List<P> getChildren(ParaObject obj, String type2, Pager... pager) {
		if (obj == null || obj.getId() == null || type2 == null) {
			return Collections.emptyList();
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("childrenonly", "true");
		params.putAll(pagerToParams(pager));
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), Utils.urlEncode(type2));
		return getItems(invokeGet(url, params, Map.class), pager);
	}

	/**
	 * Returns all child objects linked to this object.
	 * @param <P> the type of children
	 * @param type2 the type of children to look for
	 * @param field the field name to use as filter
	 * @param term the field value to use as filter
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of {@link ParaObject} in a one-to-many relationship with this object
	 */
	public <P extends ParaObject> List<P> getChildren(ParaObject obj, String type2, String field, String term,
			Pager... pager) {
		if (obj == null || obj.getId() == null || type2 == null) {
			return Collections.emptyList();
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("childrenonly", "true");
		params.putSingle("field", field);
		params.putSingle("term", term);
		params.putAll(pagerToParams(pager));
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), Utils.urlEncode(type2));
		return getItems(invokeGet(url, params, Map.class), pager);
	}

	/**
	 * Search through all child objects. Only searches child objects directly
	 * connected to this parent via the {@code parentid} field.
	 * @param <P> the type of children
	 * @param type2 the type of children to look for
	 * @param query a query string
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.core.utils.Pager}
	 * @return a list of {@link ParaObject} in a one-to-many relationship with this object
	 */
	public <P extends ParaObject> List<P> findChildren(ParaObject obj, String type2, String query, Pager... pager) {
		if (obj == null || obj.getId() == null || type2 == null) {
			return Collections.emptyList();
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("childrenonly", "true");
		params.putSingle("q", (query == null) ? "*" : query);
		params.putAll(pagerToParams(pager));
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), Utils.urlEncode(type2));
		return getItems(invokeGet(url, params, Map.class), pager);
	}

	/**
	 * Deletes all child objects permanently.
	 * @param obj the object to execute this method on
	 * @param type2 the children's type.
	 */
	public void deleteChildren(ParaObject obj, String type2) {
		if (obj == null || obj.getId() == null || type2 == null) {
			return;
		}
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("childrenonly", "true");
		String url = Utils.formatMessage("{0}/links/{1}", obj.getObjectURI(), Utils.urlEncode(type2));
		invokeDelete(url, params, null);
	}

	/////////////////////////////////////////////
	//				 UTILS
	/////////////////////////////////////////////

	/**
	 * Generates a new unique id.
	 * @return a new id
	 */
	public String newId() {
		String res = (String) invokeGet("utils/newid", null, String.class);
		return res != null ? res : "";
	}

	/**
	 * Returns the current timestamp.
	 * @return a long number
	 */
	public long getTimestamp() {
		Long res = (Long) invokeGet("utils/timestamp", null, Long.class);
		return res != null ? res : 0L;
	}

	/**
	 * Formats a date in a specific format.
	 * @param format the date format
	 * @param loc the locale instance
	 * @return a formatted date
	 */
	public String formatDate(String format, Locale loc) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("format", format);
		params.putSingle("locale", loc == null ? null : loc.toString());
		return (String) invokeGet("utils/formatdate", params, String.class);
	}

	/**
	 * Converts spaces to dashes.
	 * @param str a string with spaces
	 * @param replaceWith a string to replace spaces with
	 * @return a string with dashes
	 */
	public String noSpaces(String str, String replaceWith) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("string", str);
		params.putSingle("replacement", replaceWith);
		return (String) invokeGet("utils/nospaces", params, String.class);
	}

	/**
	 * Strips all symbols, punctuation, whitespace and control chars from a string.
	 * @param str a dirty string
	 * @return a clean string
	 */
	public String stripAndTrim(String str) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("string", str);
		return (String) invokeGet("utils/nosymbols", params, String.class);
	}

	/**
	 * Converts Markdown to HTML.
	 * @param markdownString Markdown
	 * @return HTML
	 */
	public String markdownToHtml(String markdownString) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("md", markdownString);
		return (String) invokeGet("utils/md2html", params, String.class);
	}

	/**
	 * Returns the number of minutes, hours, months elapsed for a time delta (milliseconds).
	 * @param delta the time delta between two events, in milliseconds
	 * @return a string like "5m", "1h"
	 */
	public String approximately(long delta) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("delta", Long.toString(delta));
		return (String) invokeGet("utils/timeago", params, String.class);
	}

	/////////////////////////////////////////////
	//				 MISC
	/////////////////////////////////////////////

	/**
	 * Generates a new set of access/secret keys.
	 * Old keys are discarded and invalid after this.
	 * @return a map of new credentials
	 */
	public Map<String, String> newKeys() {
		Map<String, String> keys = invokePost("_newkeys", null, Map.class);
		if (keys != null && keys.containsKey("secretKey")) {
			this.secretKey = keys.get("secretKey");
		}
		return keys;
	}

	/**
	 * Returns all registered types for this App.
	 * @return a map of plural-singular form of all the registered types.
	 */
	public Map<String, String> types() {
		return invokeGet("_types", null, Map.class);
	}

	/**
	 * Returns the number of objects for each existing type in this App.
	 * @return a map of singular object type to object count.
	 */
	public Map<String, Number> typesCount() {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("count", "true");
		return invokeGet("_types", params, Map.class);
	}

	/**
	 * Returns a {@link com.erudika.para.core.User} or an
	 * {@link com.erudika.para.core.App} that is currently authenticated.
	 * @param <P> an App or User
	 * @return a {@link com.erudika.para.core.User} or an {@link com.erudika.para.core.App}
	 */
	public <P extends ParaObject> P me() {
		Map<String, Object> data = invokeGet("_me", null, Map.class);
		return ParaObjectUtils.setAnnotatedFields(data);
	}

	/**
	 * Verifies a given JWT and returns the authenticated subject. This request will not remember the JWT in memory.
	 * @param <P> an App or User
	 * @param accessToken a valid JWT access token
	 * @return a {@link com.erudika.para.core.User} or an {@link com.erudika.para.core.App}
	 */
	public <P extends ParaObject> P me(String accessToken) {
		if (!StringUtils.isBlank(accessToken)) {
			String auth = accessToken.startsWith("Bearer") ? accessToken : "Bearer " + accessToken;
			Map<String, Object> data = invokeSignedRequest(accessKey, auth, GET.toString(),
					getEndpoint(), getFullPath("_me"), null, null, null, Map.class);
			return ParaObjectUtils.setAnnotatedFields(data);
		}
		return me();
	}

	/**
	 * Upvote an object and register the vote in DB.
	 * @param obj the object to receive +1 votes
	 * @param voterid the userid of the voter
	 * @return true if vote was successful
	 */
	public boolean voteUp(ParaObject obj, String voterid) {
		return voteUp(obj, voterid, null, null);
	}

	/**
	 * Upvote an object and register the vote in DB.
	 * @param obj the object to receive +1 votes
	 * @param voterid the userid of the voter
	 * @param expiresAfter expires after seconds
	 * @param lockedAfter locked after seconds
	 * @return true if vote was successful
	 */
	public boolean voteUp(ParaObject obj, String voterid, Integer expiresAfter, Integer lockedAfter) {
		if (obj == null || StringUtils.isBlank(voterid)) {
			return false;
		}
		if (expiresAfter == null && lockedAfter == null) {
			return (boolean) invokePatch(obj.getObjectURI(), Collections.singletonMap("_voteup", voterid), Boolean.class);
		}
		Map<String, Object> vote = new HashMap<>();
		vote.put("_voteup", voterid);
		vote.put("_vote_locked_after", lockedAfter);
		vote.put("_vote_expires_after", expiresAfter);
		return (boolean) invokePatch(obj.getObjectURI(), vote, Boolean.class);
	}

	/**
	 * Downvote an object and register the vote in DB.
	 * @param obj the object to receive -1 votes
	 * @param voterid the userid of the voter
	 * @return true if vote was successful
	 */
	public boolean voteDown(ParaObject obj, String voterid) {
		return voteDown(obj, voterid, null, null);
	}

	/**
	 * Downvote an object and register the vote in DB.
	 * @param obj the object to receive -1 votes
	 * @param voterid the userid of the voter
	 * @param expiresAfter expires after seconds
	 * @param lockedAfter locked after seconds
	 * @return true if vote was successful
	 */
	public boolean voteDown(ParaObject obj, String voterid, Integer expiresAfter, Integer lockedAfter) {
		if (obj == null || StringUtils.isBlank(voterid)) {
			return false;
		}
		if (expiresAfter == null && lockedAfter == null) {
			return (boolean) invokePatch(obj.getObjectURI(), Collections.singletonMap("_votedown", voterid), Boolean.class);
		}
		Map<String, Object> vote = new HashMap<>();
		vote.put("_votedown", voterid);
		vote.put("_vote_locked_after", lockedAfter);
		vote.put("_vote_expires_after", expiresAfter);
		return (boolean) invokePatch(obj.getObjectURI(), vote, Boolean.class);
	}

	/**
	 * Rebuilds the entire search index.
	 * @return a response object with properties "tookMillis" and "reindexed"
	 */
	public Map<String, Object> rebuildIndex() {
		return invokePost("_reindex", null, Map.class);
	}

	/**
	 * Rebuilds the entire search index.
	 * @param destinationIndex an existing index as destination
	 * @return a response object with properties "tookMillis" and "reindexed"
	 */
	public Map<String, Object> rebuildIndex(String destinationIndex) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle("destinationIndex", destinationIndex);
		return invokeSignedRequest(accessKey, key(true), POST.toString(),
				getEndpoint(), getFullPath("_reindex"), null, params, null, Map.class);
	}

	/////////////////////////////////////////////
	//			Validation Constraints
	/////////////////////////////////////////////

	/**
	 * Returns the validation constraints map.
	 * @return a map containing all validation constraints.
	 */
	public Map<String, Map<String, Map<String, Map<String, ?>>>> validationConstraints() {
		return invokeGet("_constraints", null, Map.class);
	}

	/**
	 * Returns the validation constraints map.
	 * @param type a type
	 * @return a map containing all validation constraints for this type.
	 */
	public Map<String, Map<String, Map<String, Map<String, ?>>>> validationConstraints(String type) {
		return invokeGet(Utils.formatMessage("_constraints/{0}", Utils.urlEncode(type)), null, Map.class);
	}

	/**
	 * Add a new constraint for a given field.
	 * @param type a type
	 * @param field a field name
	 * @param c the constraint
	 * @return a map containing all validation constraints for this type.
	 */
	public Map<String, Map<String, Map<String, Map<String, ?>>>> addValidationConstraint(String type,
			String field, Constraint c) {
		if (StringUtils.isBlank(type) || StringUtils.isBlank(field) || c == null) {
			return Collections.emptyMap();
		}
		return invokePut(Utils.formatMessage("_constraints/{0}/{1}/{2}", Utils.urlEncode(type),
				field, c.getName()), c.getPayload(), Map.class);
	}

	/**
	 * Removes a validation constraint for a given field.
	 * @param type a type
	 * @param field a field name
	 * @param constraintName the name of the constraint to remove
	 * @return a map containing all validation constraints for this type.
	 */
	public Map<String, Map<String, Map<String, Map<String, ?>>>> removeValidationConstraint(String type,
			String field, String constraintName) {
		if (StringUtils.isBlank(type) || StringUtils.isBlank(field) || StringUtils.isBlank(constraintName)) {
			return Collections.emptyMap();
		}
		return invokeDelete(Utils.formatMessage("_constraints/{0}/{1}/{2}", Utils.urlEncode(type),
				field, constraintName), null, Map.class);
	}

	/////////////////////////////////////////////
	//			Resource Permissions
	/////////////////////////////////////////////

	/**
	 * Returns the permissions for all subjects and resources for current app.
	 * @return a map of subject ids to resource names to a list of allowed methods
	 */
	public Map<String, Map<String, List<String>>> resourcePermissions() {
		return invokeGet("_permissions", null, Map.class);
	}

	/**
	 * Returns only the permissions for a given subject (user) of the current app.
	 * @param subjectid the subject id (user id)
	 * @return a map of subject ids to resource names to a list of allowed methods
	 */
	public Map<String, Map<String, List<String>>> resourcePermissions(String subjectid) {
		subjectid = Utils.urlEncode(subjectid);
		return invokeGet(Utils.formatMessage("_permissions/{0}", subjectid), null, Map.class);
	}

	/**
	 * Grants a permission to a subject that allows them to call the specified HTTP methods on a given resource.
	 * @param subjectid subject id (user id)
	 * @param resourcePath resource path or object type
	 * @param permission a set of HTTP methods
	 * @return a map of the permissions for this subject id
	 */
	public Map<String, Map<String, List<String>>> grantResourcePermission(String subjectid, String resourcePath,
			EnumSet<App.AllowedMethods> permission) {
		return grantResourcePermission(subjectid, resourcePath, permission, false);
	}

	/**
	 * Grants a permission to a subject that allows them to call the specified HTTP methods on a given resource.
	 * @param subjectid subject id (user id)
	 * @param resourcePath resource path or object type
	 * @param permission a set of HTTP methods
	 * @param allowGuestAccess if true - all unauthenticated requests will go through, 'false' by default.
	 * @return a map of the permissions for this subject id
	 */
	public Map<String, Map<String, List<String>>> grantResourcePermission(String subjectid, String resourcePath,
			EnumSet<App.AllowedMethods> permission, boolean allowGuestAccess) {
		if (StringUtils.isBlank(subjectid) || StringUtils.isBlank(resourcePath) || permission == null) {
			return Collections.emptyMap();
		}
		if (allowGuestAccess && App.ALLOW_ALL.equals(subjectid)) {
			permission.add(App.AllowedMethods.GUEST);
		}
		subjectid = Utils.urlEncode(subjectid);
		resourcePath = Utils.base64encURL(resourcePath.getBytes());
		return invokePut(Utils.formatMessage("_permissions/{0}/{1}", subjectid, resourcePath), permission, Map.class);
	}

	/**
	 * Revokes a permission for a subject, meaning they no longer will be able to access the given resource.
	 * @param subjectid subject id (user id)
	 * @param resourcePath resource path or object type
	 * @return a map of the permissions for this subject id
	 */
	public Map<String, Map<String, List<String>>> revokeResourcePermission(String subjectid, String resourcePath) {
		if (StringUtils.isBlank(subjectid) || StringUtils.isBlank(resourcePath)) {
			return Collections.emptyMap();
		}
		subjectid = Utils.urlEncode(subjectid);
		resourcePath = Utils.base64encURL(resourcePath.getBytes());
		return invokeDelete(Utils.formatMessage("_permissions/{0}/{1}", subjectid, resourcePath), null, Map.class);
	}

	/**
	 * Revokes all permission for a subject.
	 * @param subjectid subject id (user id)
	 * @return a map of the permissions for this subject id
	 */
	public Map<String, Map<String, List<String>>> revokeAllResourcePermissions(String subjectid) {
		if (StringUtils.isBlank(subjectid)) {
			return Collections.emptyMap();
		}
		subjectid = Utils.urlEncode(subjectid);
		return invokeDelete(Utils.formatMessage("_permissions/{0}", subjectid), null, Map.class);
	}

	/**
	 * Checks if a subject is allowed to call method X on resource Y.
	 * @param subjectid subject id
	 * @param resourcePath resource path or object type
	 * @param httpMethod HTTP method name
	 * @return true if allowed
	 */
	public boolean isAllowedTo(String subjectid, String resourcePath, String httpMethod) {
		if (StringUtils.isBlank(subjectid) || StringUtils.isBlank(resourcePath) || StringUtils.isBlank(httpMethod)) {
			return false;
		}
		subjectid = Utils.urlEncode(subjectid);
		resourcePath = Utils.base64encURL(resourcePath.getBytes());
		String url = Utils.formatMessage("_permissions/{0}/{1}/{2}", subjectid, resourcePath, httpMethod);
		Boolean result = invokeGet(url, null, Boolean.class);
		return result != null && result;
	}

	/////////////////////////////////////////////
	//			App Settings
	/////////////////////////////////////////////

	/**
	 * Returns the map containing app-specific settings.
	 * @return a map
	 */
	public Map<String, Object> appSettings() {
		return invokeGet("_settings", null, Map.class);
	}

	/**
	 * Returns the value of a specific app setting (property).
	 * @param key a key
	 * @return a map containing one element {"value": "the_value"} or an empty map.
	 */
	public Map<String, Object> appSettings(String key) {
		if (StringUtils.isBlank(key)) {
			return appSettings();
		}
		return invokeGet(Utils.formatMessage("_settings/{0}", key), null, Map.class);
	}

	/**
	 * Adds or overwrites an app-specific setting.
	 * @param key a key
	 * @param value a value
	 */
	public void addAppSetting(String key, Object value) {
		if (!StringUtils.isBlank(key) && value != null) {
			invokePut(Utils.formatMessage("_settings/{0}", key), Collections.singletonMap("value", value), Map.class);
		}
	}

	/**
	 * Overwrites all app-specific settings.
	 * @param settings a key-value map of properties
	 */
	public void setAppSettings(Map<?, ?> settings) {
		if (settings != null) {
			invokePut("_settings", settings, Map.class);
		}
	}

	/**
	 * Removes an app-specific setting.
	 * @param key a key
	 */
	public void removeAppSetting(String key) {
		if (!StringUtils.isBlank(key)) {
			invokeDelete(Utils.formatMessage("_settings/{0}", key), null, null);
		}
	}

	/////////////////////////////////////////////
	//				Access Tokens
	/////////////////////////////////////////////

	/**
	 * Takes an identity provider access token and fetches the user data from that provider.
	 * A new {@link  User} object is created if that user doesn't exist.
	 * Access tokens are returned upon successful authentication using one of the SDKs from
	 * Facebook, Google, Twitter, etc.
	 * <b>Note:</b> Twitter uses OAuth 1 and gives you a token and a token secret.
	 * <b>You must concatenate them like this: <code>{oauth_token}:{oauth_token_secret}</code> and
	 * use that as the provider access token.</b>
	 * @param provider identity provider, e.g. 'facebook', 'google'...
	 * @param providerToken access token from a provider like Facebook, Google, Twitter
	 * @param rememberJWT it true the access token returned by Para will be stored locally and
	 * available through {@link #getAccessToken()}
	 * @return a {@link User} object or null if something failed. The JWT is available
	 * on the returned User object via {@link User#getPassword()}.
	 */
	@SuppressWarnings("unchecked")
	public User signIn(String provider, String providerToken, boolean rememberJWT) {
		if (!StringUtils.isBlank(provider) && !StringUtils.isBlank(providerToken)) {
			Map<String, String> credentials = new HashMap<>();
			credentials.put(Config._APPID, accessKey);
			credentials.put("provider", provider);
			credentials.put("token", providerToken);
			Map<String, Object> result = invokePost(JWT_PATH, credentials, Map.class);
			if (result != null && result.containsKey("user") && result.containsKey("jwt")) {
				Map<?, ?> jwtData = (Map<?, ?>) result.get("jwt");
				if (rememberJWT) {
					tokenKey = (String) jwtData.get("access_token");
					tokenKeyExpires = (Long) jwtData.get("expires");
					tokenKeyNextRefresh = (Long) jwtData.get("refresh");
				}
				User signedInUser = ParaObjectUtils.setAnnotatedFields((Map<String, Object>) result.get("user"));
				signedInUser.setPassword((String) jwtData.get("access_token"));
				return signedInUser;
			} else {
				clearAccessToken();
			}
		}
		return null;
	}

	/**
	 * Takes an identity provider access token and fetches the user data from that provider.
	 * @see #signIn(java.lang.String, java.lang.String, boolean)
	 * @param provider identity provider, e.g. 'facebook', 'google'...
	 * @param providerToken access token from a provider like Facebook, Google, Twitter
	 * @return a {@link User} object or null if something failed
	 */
	public User signIn(String provider, String providerToken) {
		return signIn(provider, providerToken, true);
	}

	/**
	 * Clears the JWT access token but token is not revoked.
	 * Tokens can be revoked globally per user with {@link #revokeAllTokens()}.
	 */
	public void signOut() {
		clearAccessToken();
	}

	/**
	 * Refreshes the JWT access token. This requires a valid existing token.
	 *	Call {@link #signIn(java.lang.String, java.lang.String)} first.
	 * @return true if token was refreshed
	 */
	protected boolean refreshToken() {
		long now = System.currentTimeMillis();
		boolean notExpired = tokenKeyExpires != null && tokenKeyExpires > now;
		boolean canRefresh = tokenKeyNextRefresh != null &&
				(tokenKeyNextRefresh < now || tokenKeyNextRefresh > tokenKeyExpires);
		// token present and NOT expired
		if (tokenKey != null && notExpired && canRefresh) {
			Map<String, Object> result = invokeGet(JWT_PATH, null, Map.class);
			if (result != null && result.containsKey("user") && result.containsKey("jwt")) {
				Map<?, ?> jwtData = (Map<?, ?>) result.get("jwt");
				tokenKey = (String) jwtData.get("access_token");
				tokenKeyExpires = (Long) jwtData.get("expires");
				tokenKeyNextRefresh = (Long) jwtData.get("refresh");
				return true;
			} else {
				clearAccessToken();
			}
		}
		return false;
	}

	/**
	 * Revokes all user tokens for a given user id.
	 * This would be equivalent to "logout everywhere".
	 * <b>Note:</b> Generating a new API secret on the server will also invalidate all client tokens.
	 * Requires a valid existing token.
	 * @return true if successful
	 */
	public boolean revokeAllTokens() {
		return invokeDelete(JWT_PATH, null, Map.class) != null;
	}

	/////////////////////////////////////////////
	//				Utilities
	/////////////////////////////////////////////

	/**
	 * Paginates through all objects and executes the provided function on the results.
	 * @param <T> type of object
	 * @param paginatingFunc paginating function
	 */
	public <T extends ParaObject> void readEverything(Function<Pager, List<T>> paginatingFunc) {
		readEverything(paginatingFunc, Para.getConfig().maxItemsPerPage());
	}

	/**
	 * Paginates through all objects and executes the provided function on the results.
	 * @param <T> type of object
	 * @param paginatingFunc paginating function
	 * @param pageSize page size for pager (pager.limit)
	 */
	public <T extends ParaObject> void readEverything(Function<Pager, List<T>> paginatingFunc, int pageSize) {
		if (paginatingFunc != null) {
			// find all objects even if there are more than 10000 in the system
			Pager pager = new Pager(1, "_docid", false, pageSize);
			List<T> results;
			do {
				results = paginatingFunc.apply(pager);
			} while (!results.isEmpty());
		}
	}

	/**
	 * Performs a partial batch update on all objects of given type. This method encapsulates the specific logic for
	 * performing the batch update safely because updating while searching for objects can lead to bugs due to the
	 * fact that _docid values change on each update call.
	 * @param <T> type of object
	 * @param paginatingFunc paginating function
	 */
	public <T extends ParaObject> void updateAllPartially(BiFunction<List<Map<String, Object>>, Pager, List<T>> paginatingFunc) {
		updateAllPartially(paginatingFunc, Para.getConfig().maxItemsPerPage(), 100);
	}

	/**
	 * Performs a partial batch update on all objects of given type. This method encapsulates the specific logic for
	 * performing the batch update safely because updating while searching for objects can lead to bugs due to the
	 * fact that _docid values change on each update call.
	 * @param <T> type of object
	 * @param paginatingFunc paginating function which returns a list of object
	 * @param pageSize page size for pager (pager.limit)
	 * @param updateBatchSize batch size for updating objects
	 */
	public <T extends ParaObject> void updateAllPartially(BiFunction<List<Map<String, Object>>, Pager, List<T>> paginatingFunc,
			int pageSize, int updateBatchSize) {
		if (paginatingFunc != null) {
			LinkedList<Map<String, Object>> toUpdate = new LinkedList<>();
			readEverything((pager) -> paginatingFunc.apply(toUpdate, pager), pageSize);
			// always patch outside the loop because _docid value changes on each update!
			// this causes updated results to be found again and could lead to exhaution of resources
			LinkedList<Map<String, Object>> batch = new LinkedList<>();
			while (!toUpdate.isEmpty()) {
				batch.add(toUpdate.pop());
				if (batch.size() >= updateBatchSize) {
					// partial batch update
					invokePatch("_batch", batch, Map.class);
					batch.clear();
				}
			}
			if (!batch.isEmpty()) {
				invokePatch("_batch", batch, Map.class);
			}
		}
	}
}
