/*
 * Copyright 2013-2026 Erudika. https://erudika.com
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
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.User;
import com.erudika.para.core.metrics.Metrics;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.HumanTime;
import com.erudika.para.core.utils.Pager;
import com.erudika.para.core.utils.Para;
import static com.erudika.para.core.utils.Para.getDAO;
import static com.erudika.para.core.utils.Para.getRevision;
import static com.erudika.para.core.utils.Para.getSearch;
import static com.erudika.para.core.utils.Para.getVersion;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.utils.Utils;
import com.erudika.para.core.validation.Constraint;
import com.erudika.para.server.ParaServer;
import static com.erudika.para.server.rest.RestUtils.getBatchCreateResponse;
import static com.erudika.para.server.rest.RestUtils.getBatchDeleteResponse;
import static com.erudika.para.server.rest.RestUtils.getBatchReadResponse;
import static com.erudika.para.server.rest.RestUtils.getBatchUpdateResponse;
import static com.erudika.para.server.rest.RestUtils.getCreateResponse;
import static com.erudika.para.server.rest.RestUtils.getDeleteResponse;
import static com.erudika.para.server.rest.RestUtils.getEntity;
import static com.erudika.para.server.rest.RestUtils.getOverwriteResponse;
import static com.erudika.para.server.rest.RestUtils.getReadResponse;
import static com.erudika.para.server.rest.RestUtils.getStatusResponse;
import static com.erudika.para.server.rest.RestUtils.getUpdateResponse;
import static com.erudika.para.server.rest.RestUtils.queryParam;
import static com.erudika.para.server.rest.RestUtils.queryParams;
import com.erudika.para.server.security.SecurityUtils;
import static com.erudika.para.server.security.SecurityUtils.getPrincipalApp;
import com.erudika.para.server.utils.HealthUtils;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * This is the main REST API configuration class which defines all endpoints for all resources and the way API request
 * will be handled. This is API version 1.0.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@RestController
@ConditionalOnProperty(value = "para.api_enabled", havingValue = "true")
@RequestMapping(value = ParaServer.API_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public final class Api1 {

	private static final Logger logger = LoggerFactory.getLogger(Api1.class);

	@GetMapping({"", "/"})
	public ResponseEntity<?> intro() {
		Map<String, String> info = new TreeMap<>();
		info.put("info", "Para - the backend for busy developers.");
		if (Para.getConfig().versionBannerEnabled()) {
			info.put("version", Strings.CS.replace(getVersion(), "-SNAPSHOT", ""));
			info.put("revision", StringUtils.trimToEmpty(getRevision()));
		}
		return ResponseEntity.ok(info);
	}

	@GetMapping("/_setup")
	public ResponseEntity<?> setup() {
		return setupInternal(null, null);
	}

	@GetMapping("/_setup/{appid}")
	public ResponseEntity<?> setup(@PathVariable String appid, HttpServletRequest req) {
		return setupInternal(appid, req);
	}

	@PostMapping("/_newkeys")
	public ResponseEntity<?> newKeys() {
		return newKeysHandler(getPrincipalApp());
	}

	public ResponseEntity<?> newKeysHandler(App app) {
		if (app != null) {
			app.resetSecret();
			CoreUtils.getInstance().overwrite(app);
			Map<String, String> creds = app.getCredentials();
			creds.put("info", "Save the secret key! It is showed only once!");
			return ResponseEntity.ok(creds);
		}
		return getStatusResponse(HttpStatus.UNAUTHORIZED, "Not an app.");
	}

	@PostMapping("/{type}")
	public ResponseEntity<?> create(@PathVariable String type, HttpServletRequest req) throws IOException {
		return createHandler(getPrincipalApp(), type, req);
	}

	public ResponseEntity<?> createHandler(App app, String type, HttpServletRequest req) throws IOException {
		if (app == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
		}
		String resolvedType = resolveType(app, type);
		return getCreateResponse(app, resolvedType, req.getInputStream());
	}

	@GetMapping("/{type}/{id}")
	public ResponseEntity<?> read(@PathVariable String type, @PathVariable String id) {
		return readHandler(getPrincipalApp(), type, id);
	}

	public ResponseEntity<?> readHandler(App app, String type, String id) {
		if (app == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
		}
		if (app.getId().equals(id)) {
			return getReadResponse(app, app);
		}
		ParaObject obj = loadObject(app, type, id);
		return getReadResponse(app, obj);
	}

	@PatchMapping("/{type}/{id}")
	public ResponseEntity<?> update(@PathVariable String type, @PathVariable String id, HttpServletRequest req)
			throws IOException {
		return updateHandler(getPrincipalApp(), type, id, req);
	}

	public ResponseEntity<?> updateHandler(App app, String type, String id, HttpServletRequest req)
			throws IOException {
		if (app == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
		}
		String resolvedType = resolveType(app, type);
		String appid = Strings.CS.equals(resolvedType, Utils.type(App.class)) ? app.getAppid() : app.getAppIdentifier();
		ParaObject target = getDAO().read(appid, id);
		return getUpdateResponse(app, target, req.getInputStream());
	}

	@PutMapping("/{type}/{id}")
	public ResponseEntity<?> overwrite(@PathVariable String type, @PathVariable String id, HttpServletRequest req)
			throws IOException {
		return overwriteHandler(getPrincipalApp(), type, id, req);
	}

	public ResponseEntity<?> overwriteHandler(App app, String type, String id, HttpServletRequest req)
			throws IOException {
		if (app == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
		}
		String resolvedType = resolveType(app, type);
		return getOverwriteResponse(app, id, resolvedType, req.getInputStream());
	}

	@DeleteMapping("/{type}/{id}")
	public ResponseEntity<?> delete(@PathVariable String type, @PathVariable String id) {
		return deleteHandler(getPrincipalApp(), type, id);
	}

	public ResponseEntity<?> deleteHandler(App app, String type, String id) {
		if (app == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
		}
		ParaObject obj = loadObject(app, type, id);
		return getDeleteResponse(app, obj);
	}

	@PostMapping("/_batch")
	public ResponseEntity<?> batchCreate(HttpServletRequest req) throws IOException {
		return batchCreateHandler(getPrincipalApp(), req);
	}

	@PutMapping("/_batch")
	public ResponseEntity<?> batchPut(HttpServletRequest req) throws IOException {
		return batchCreateHandler(getPrincipalApp(), req);
	}

	public ResponseEntity<?> batchCreateHandler(App app, HttpServletRequest req) throws IOException {
		if (app == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
		}
		return getBatchCreateResponse(app, req.getInputStream());
	}

	@GetMapping("/_batch")
	public ResponseEntity<?> batchRead(HttpServletRequest req) {
		return batchReadHandler(getPrincipalApp(), req);
	}

	public ResponseEntity<?> batchReadHandler(App app, HttpServletRequest req) {
		if (app == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
		}
		return getBatchReadResponse(app, queryParams("ids", req));
	}

	@PatchMapping("_batch")
	public ResponseEntity<?> batchUpdate(HttpServletRequest req) throws IOException {
		return batchUpdateHandler(getPrincipalApp(), req);
	}

	@SuppressWarnings("unchecked")
	public ResponseEntity<?> batchUpdateHandler(App app, HttpServletRequest req) throws IOException {
		if (app == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
		}
		ResponseEntity<?> entityRes = getEntity(req.getInputStream(), List.class, true);
		if (entityRes.getStatusCode().is2xxSuccessful()) {
			List<Map<String, Object>> newProps = (List<Map<String, Object>>) entityRes.getBody();
			ArrayList<String> ids = new ArrayList<>(newProps.size());
			for (Map<String, Object> props : newProps) {
				if (props != null && props.containsKey(Config._ID)) {
					ids.add((String) props.get(Config._ID));
				}
			}
			return getBatchUpdateResponse(app, getDAO().readAll(app.getAppIdentifier(), ids, true), newProps);
		}
		return entityRes;
	}

	@DeleteMapping("/_batch")
	public ResponseEntity<?> batchDelete(HttpServletRequest req) {
		return batchDeleteHandler(getPrincipalApp(), req);
	}

	public ResponseEntity<?> batchDeleteHandler(App app, HttpServletRequest req) {
		if (app == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
		}
		return getBatchDeleteResponse(app, queryParams("ids", req));
	}

	@GetMapping("/search")
	public ResponseEntity<?> search(HttpServletRequest req) {
		return searchHandler(getPrincipalApp(), null, null, req);
	}

	@GetMapping("/search/{querytype}")
	public ResponseEntity<?> search(@PathVariable String querytype, HttpServletRequest req) {
		return searchHandler(getPrincipalApp(), null, querytype, req);
	}

	@GetMapping("/{type}")
	public ResponseEntity<?> searchType(@PathVariable String type, HttpServletRequest req) {
		return searchHandler(getPrincipalApp(), type, null, req);
	}

	@GetMapping("/{type}/search")
	public ResponseEntity<?> searchTypeDefault(@PathVariable String type, HttpServletRequest req) {
		return searchHandler(getPrincipalApp(), type, null, req);
	}

	@GetMapping("/{type}/search/{querytype}")
	public ResponseEntity<?> searchType(@PathVariable String type, @PathVariable String querytype,
			HttpServletRequest req) {
		return searchHandler(getPrincipalApp(), type, querytype, req);
	}

	public ResponseEntity<?> searchHandler(App app, String typeParam, String querytype, HttpServletRequest req) {
		if (app == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
		}
		String query = queryParam("q", req);
		if (!StringUtils.isBlank(query) && !getSearch().isValidQueryString(query)) {
			return getStatusResponse(HttpStatus.BAD_REQUEST, "Invalid query string syntax q=" + query
					+ " in request " + req.getMethod() + " " + req.getRequestURI());
		}
		String type = StringUtils.isBlank(typeParam) ? null : resolveType(app, typeParam);
		String typeOverride = (StringUtils.isBlank(type)) ? null : type;
		String queryType = querytype;
		if (StringUtils.isBlank(queryType)) {
			queryType = "default";
		}
		Map<String, Object> result = RestUtils.buildQueryAndSearch(app, queryType, typeOverride, req);
		return ResponseEntity.ok(result);
	}

	@GetMapping("/utils/{method}")
	public ResponseEntity<?> utilsHandler(@PathVariable String method, HttpServletRequest req) {
		method = StringUtils.isBlank(method) ? queryParam("method", req) : method;
		if ("newid".equals(method)) {
			return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Utils.getNewId());
		} else if ("timestamp".equals(method)) {
			return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Long.toString(Utils.timestamp()));
		} else if ("formatdate".equals(method)) {
			String format = queryParam("format", req);
			String locale = queryParam("locale", req);
			Locale loc = Utils.getLocale(locale);
			return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Utils.formatDate(format, loc));
		} else if ("formatmessage".equals(method)) {
			String msg = queryParam("message", req);
			Object[] paramz = req.getParameterValues("fields");
			return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Utils.formatMessage(msg, paramz));
		} else if ("nospaces".equals(method)) {
			String str = queryParam("string", req);
			String repl = queryParam("replacement", req);
			return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Utils.noSpaces(str, repl));
		} else if ("nosymbols".equals(method)) {
			String str = queryParam("string", req);
			return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Utils.stripAndTrim(str));
		} else if ("md2html".equals(method)) {
			String md = queryParam("md", req);
			return ResponseEntity.ok(Utils.markdownToHtml(md));
		} else if ("timeago".equals(method)) {
			String d = queryParam("delta", req);
			long delta = NumberUtils.toLong(d, 1);
			return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(HumanTime.approximately(delta));
		}
		return getStatusResponse(HttpStatus.BAD_REQUEST, "Unknown method: " + ((method == null) ? "empty" : method));
	}

	@GetMapping("/_types")
	public ResponseEntity<?> listTypes(HttpServletRequest req) {
		return listTypesHandler(getPrincipalApp(), req);
	}

	public ResponseEntity<?> listTypesHandler(App app, HttpServletRequest req) {
		if (app != null) {
			Map<String, String> types = ParaObjectUtils.getAllTypes(app);
			if ("true".equalsIgnoreCase(queryParam("count", req))) {
				Map<String, Long> typesCount = new HashMap<>(types.size());
				types.values().forEach(v -> typesCount.put(v, getSearch().getCount(app.getAppIdentifier(), v)));
				return ResponseEntity.ok(typesCount);
			}
			return ResponseEntity.ok(types);
		}
		return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
	}

	@GetMapping("/_me")
	public ResponseEntity<?> me(HttpServletRequest req) {
		User user = SecurityUtils.getAuthenticatedUser();
		App app = SecurityUtils.getAuthenticatedApp();
		if (user != null) {
			return ResponseEntity.ok(user);
		}
		if (app != null) {
			String bearer = req.getHeader(HttpHeaders.AUTHORIZATION);
			if (app.isRootApp() && Strings.CS.startsWith(bearer, "Bearer")) {
				try {
					String token = bearer.substring(6).trim();
					SignedJWT jwt = SignedJWT.parse(token);
					if (jwt != null && jwt.getJWTClaimsSet().getClaim("getCredentials") != null) {
						App a = getDAO().read(App.id((String) jwt.getJWTClaimsSet().getClaim("getCredentials")));
						if (a != null) {
							Map<String, Object> obj = ParaObjectUtils.getJsonReader(Map.class)
									.readValue(ParaObjectUtils.toJSON(a));
							obj.put("credentials", a.getCredentials());
							return ResponseEntity.ok(obj);
						}
					}
				} catch (Exception ex) {
					logger.error("Operation failed: {}", ex.getMessage());
				}
			}
			return ResponseEntity.ok(app);
		}
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}

	@GetMapping("/_id/{id}")
	public ResponseEntity<?> readId(@PathVariable String id) {
		return readIdHandler(getPrincipalApp(), id);
	}

	public ResponseEntity<?> readIdHandler(App app, String id) {
		if (app != null) {
			return getReadResponse(app, getDAO().read(app.getAppIdentifier(), id));
		}
		return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
	}

	@GetMapping("/_config/options")
	public ResponseEntity<?> configOptions(HttpServletRequest req) {
		String format = queryParam("format", req);
		String groupby = queryParam("groupby", req);
		String type = "text/plain";
		if ("markdown".equalsIgnoreCase(format)) {
			type = "text/markdown";
		} else if ("hocon".equalsIgnoreCase(format)) {
			type = "application/hocon";
		} else if (StringUtils.isBlank(format) || "json".equalsIgnoreCase(format)) {
			type = "application/json";
		}
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).body(
				Para.getConfig().renderConfigDocumentation(format,
						StringUtils.isBlank(groupby) || "category".equalsIgnoreCase(groupby)));
	}

	@GetMapping("/_constraints")
	public ResponseEntity<?> getConstraints() {
		return getConstraintsHandler(getPrincipalApp(), null);
	}

	@GetMapping("/_constraints/{type}")
	public ResponseEntity<?> getConstraints(@PathVariable String type) {
		return getConstraintsHandler(getPrincipalApp(), type);
	}

	public ResponseEntity<?> getConstraintsHandler(App app, String type) {
		if (app != null) {
			if (type != null) {
				return ResponseEntity.ok(app.getAllValidationConstraints(type));
			}
			return ResponseEntity.ok(app.getAllValidationConstraints());
		}
		return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
	}

	@PutMapping("/_constraints/{type}/{field}/{cname}")
	public ResponseEntity<?> addConstraint(@PathVariable String type, @PathVariable String field,
			@PathVariable String cname, HttpServletRequest req) throws IOException {
		return addConstraintHandler(getPrincipalApp(), type, field, cname, req);
	}

	@SuppressWarnings("unchecked")
	public ResponseEntity<?> addConstraintHandler(App app, String type, String field, String cname, HttpServletRequest req)
			throws IOException {
		if (app != null) {
			ResponseEntity<?> payloadRes = getEntity(req.getInputStream(), Map.class);
			if (payloadRes.getStatusCode().is2xxSuccessful()) {
				Map<String, Object> payload = (Map<String, Object>) payloadRes.getBody();
				if (app.addValidationConstraint(type, field, Constraint.build(cname, payload))) {
					app.update();
				}
			}
			return ResponseEntity.ok(app.getAllValidationConstraints(type));
		}
		return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
	}

	@DeleteMapping("/_constraints/{type}/{field}/{cname}")
	public ResponseEntity<?> removeConstraint(@PathVariable String type, @PathVariable String field,
			@PathVariable String cname) {
		return removeConstraintHandler(getPrincipalApp(), type, field, cname);
	}

	public ResponseEntity<?> removeConstraintHandler(App app, String type, String field, String cname) {
		if (app != null) {
			if (app.removeValidationConstraint(type, field, cname)) {
				app.update();
			}
			return ResponseEntity.ok(app.getAllValidationConstraints(type));
		}
		return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
	}

	@GetMapping("/_permissions")
	public ResponseEntity<?> getPermissions() {
		return getPermissionsHandler(getPrincipalApp(), null);
	}

	@GetMapping("/_permissions/{subjectid}")
	public ResponseEntity<?> getPermissions(@PathVariable String subjectid) {
		return getPermissionsHandler(getPrincipalApp(), subjectid);
	}

	public ResponseEntity<?> getPermissionsHandler(App app, String subjectid) {
		if (app != null) {
			if (subjectid != null) {
				return ResponseEntity.ok(app.getAllResourcePermissions(subjectid));
			}
			return ResponseEntity.ok(app.getAllResourcePermissions());
		}
		return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
	}

	@GetMapping("/_permissions/{subjectid}/{type}/{method}")
	public ResponseEntity<?> checkPermission(@PathVariable String subjectid, @PathVariable String type,
			@PathVariable String method) {
		return checkPermissionHandler(getPrincipalApp(), subjectid, type, method);
	}

	public ResponseEntity<?> checkPermissionHandler(App app, String subjectid, String type, String method) {
		if (app != null) {
			String resourcePath = Utils.base64dec(type);
			return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).
					body(Boolean.toString(app.isAllowedTo(subjectid, resourcePath, method)));
		}
		return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
	}

	@PutMapping("/_permissions/{subjectid}/{type}")
	public ResponseEntity<?> grantPermission(@PathVariable String subjectid, @PathVariable String type,
			HttpServletRequest req) throws IOException {
		return grantPermissionHandler(getPrincipalApp(), subjectid, type, req);
	}

	@SuppressWarnings("unchecked")
	public ResponseEntity<?> grantPermissionHandler(App app, String subjectid, String type, HttpServletRequest req)
			throws IOException {
		if (app != null) {
			String resourcePath = Utils.base64dec(type);
			ResponseEntity<?> resp = getEntity(req.getInputStream(), List.class);
			if (resp.getStatusCode().is2xxSuccessful()) {
				List<String> permission = (List<String>) resp.getBody();
				Set<App.AllowedMethods> set = new HashSet<>(permission.size());
				for (String perm : permission) {
					if (!StringUtils.isBlank(perm)) {
						App.AllowedMethods method = App.AllowedMethods.fromString(perm);
						if (method != null) {
							set.add(method);
						}
					}
				}
				if (!set.isEmpty()) {
					if (app.grantResourcePermission(subjectid, resourcePath, EnumSet.copyOf(set))) {
						app.update();
					}
					return ResponseEntity.ok(app.getAllResourcePermissions(subjectid));
				}
				return getStatusResponse(HttpStatus.BAD_REQUEST, "No allowed methods specified.");
			}
			return resp;
		}
		return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
	}

	@DeleteMapping("/_permissions/{subjectid}/{type}")
	public ResponseEntity<?> revokePermission(@PathVariable String subjectid, @PathVariable String type) {
		return revokePermissionHandler(getPrincipalApp(), subjectid, type);
	}

	@DeleteMapping("/_permissions/{subjectid}")
	public ResponseEntity<?> revokeAllPermissions(@PathVariable String subjectid) {
		return revokePermissionHandler(getPrincipalApp(), subjectid, null);
	}

	public ResponseEntity<?> revokePermissionHandler(App app, String subjectid, String type) {
		if (app != null) {
			String resourcePath = Utils.base64dec(type);
			boolean revoked = StringUtils.isBlank(resourcePath)
					? app.revokeAllResourcePermissions(subjectid)
					: app.revokeResourcePermission(subjectid, resourcePath);
			if (revoked) {
				app.update();
			}
			return ResponseEntity.ok(app.getAllResourcePermissions(subjectid));
		}
		return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
	}

	@GetMapping("/_settings")
	public ResponseEntity<?> getSettings() {
		return getSettingHandler(getPrincipalApp(), null);
	}

	@GetMapping("/_settings/{key}")
	public ResponseEntity<?> getSetting(@PathVariable String key) {
		return getSettingHandler(getPrincipalApp(), key);
	}

	public ResponseEntity<?> getSettingHandler(App app, String key) {
		if (app != null) {
			if (key == null) {
				return ResponseEntity.ok(app.getSettings());
			} else {
				return ResponseEntity.ok(Collections.singletonMap("value", app.getSetting(key)));
			}
		}
		return getStatusResponse(HttpStatus.FORBIDDEN, "Not allowed.");
	}

	@PutMapping("/_settings")
	public ResponseEntity<?> putSettings(HttpServletRequest req) throws IOException {
		return putSettingHandler(getPrincipalApp(), null, req);
	}

	@PutMapping("/_settings/{key}")
	public ResponseEntity<?> putSetting(@PathVariable String key, HttpServletRequest req) throws IOException {
		return putSettingHandler(getPrincipalApp(), key, req);
	}

	@SuppressWarnings("unchecked")
	public ResponseEntity<?> putSettingHandler(App app, String key, HttpServletRequest req) throws IOException {
		if (app != null) {
			ResponseEntity<?> resp = getEntity(req.getInputStream(), Map.class);
			if (resp.getStatusCode().is2xxSuccessful()) {
				Map<String, Object> setting = (Map<String, Object>) resp.getBody();
				if (!StringUtils.isBlank(key) && setting.containsKey("value")) {
					app.addSetting(key, setting.get("value"));
				} else {
					app.clearSettings().addAllSettings(setting);
				}
				app.update();
				return ResponseEntity.ok().build();
			}
			return getStatusResponse(HttpStatus.BAD_REQUEST);
		}
		return getStatusResponse(HttpStatus.FORBIDDEN, "Not allowed.");
	}

	@DeleteMapping("/_settings/{key}")
	public ResponseEntity<?> deleteSetting(@PathVariable String key) {
		return deleteSettingHandler(getPrincipalApp(), key);
	}

	public ResponseEntity<?> deleteSettingHandler(App app, String key) {
		if (app != null) {
			app.removeSetting(key);
			app.update();
			return ResponseEntity.ok().build();
		}
		return getStatusResponse(HttpStatus.FORBIDDEN, "Not allowed.");
	}

	@GetMapping("/_health")
	public ResponseEntity<?> health() {
		if (HealthUtils.getInstance().isHealthy()) {
			return ResponseEntity.ok(Collections.singletonMap("message", "healthy"));
		}
		return getStatusResponse(HttpStatus.INTERNAL_SERVER_ERROR, "unhealthy");
	}

	@PostMapping("/_reindex")
	public ResponseEntity<?> reindex(HttpServletRequest req) {
		return reindexHandler(getPrincipalApp(), req);
	}

	public ResponseEntity<?> reindexHandler(App app, HttpServletRequest req) {
		if (app == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
		}
		long startTime = System.nanoTime();
		Pager pager = RestUtils.getPagerFromParams(req);
		String destinationIndex = queryParam("destinationIndex", req);
		try (Metrics.Context context = Metrics.time(app.getAppIdentifier(), Api1.class, "rebuildIndex")) {
			getSearch().rebuildIndex(getDAO(), app, destinationIndex, pager);
		}
		long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
		Map<String, Object> response = new HashMap<>(2);
		response.put("reindexed", pager.getCount());
		response.put("tookMillis", tookMillis);
		return ResponseEntity.ok().body(response);
	}

	@GetMapping(value = "/_export", produces = "application/zip")
	public ResponseEntity<StreamingResponseBody> backup() {
		return backupHandler(getPrincipalApp());
	}

	public ResponseEntity<StreamingResponseBody> backupHandler(App app) {
		if (app == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		String fileName = app.getAppIdentifier().trim() + "_" + Utils.formatDate("YYYYMMdd_HHmmss", Locale.US);
		StreamingResponseBody stream = os -> {
			ObjectWriter writer = JsonMapper.builder().disable(MapperFeature.USE_ANNOTATIONS).build().writer()
					.without(SerializationFeature.INDENT_OUTPUT)
					.without(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
			try (ZipOutputStream zipOut = new ZipOutputStream(os)) {
				long count = 0;
				int partNum = 0;
				Pager pager = new Pager();
				List<ParaObject> objects;
				do {
					objects = getDAO().readPage(app.getAppIdentifier(), pager);
					ZipEntry zipEntry = new ZipEntry(fileName + "_part" + (++partNum) + ".json");
					zipOut.putNextEntry(zipEntry);
					writer.writeValue(zipOut, objects);
					count += objects.size();
				} while (!objects.isEmpty());
				logger.info("Exported {} objects from app '{}'. (pager.count={})",
						count, app.getId(), pager.getCount());
			} catch (IOException e) {
				logger.error("Failed to export data.", e);
			}
		};
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/zip"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + fileName + ".zip")
				.body(stream);
	}

	@PutMapping(value = "/_import", consumes = "application/zip")
	public ResponseEntity<?> restore(HttpServletRequest req) {
		return restoreHandler(getPrincipalApp(), req);
	}

	public ResponseEntity<?> restoreHandler(App app, HttpServletRequest req) {
		if (app == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "App not found.");
		}
		ObjectReader reader = ParaObjectUtils.getJsonMapper()
				.readerFor(new TypeReference<List<Map<String, Object>>>() {
				});
		int count = 0;
		int importBatchSize = Para.getConfig().importBatchSize();
		String filename = Optional.ofNullable(queryParam("filename", req))
				.orElse(app.getAppIdentifier().trim() + "_backup.zip");
		Sysprop s = new Sysprop();
		s.setType("paraimport");
		try (InputStream inputStream = req.getInputStream(); ZipInputStream zipIn = new ZipInputStream(inputStream)) {
			ZipEntry zipEntry;
			List<ParaObject> toCreate = new LinkedList<>();
			while ((zipEntry = zipIn.getNextEntry()) != null) {
				if (zipEntry.getName().endsWith(".json")) {
					List<Map<String, Object>> objects = reader.readValue(new FilterInputStream(zipIn) {
						public void close() throws IOException {
							zipIn.closeEntry();
						}
					});
					objects.forEach(o -> toCreate.add(ParaObjectUtils.setAnnotatedFields(o)));
					if (toCreate.size() >= importBatchSize) {
						getDAO().createAll(app.getAppIdentifier(), toCreate);
						toCreate.clear();
					}
					count += objects.size();
				}
			}
			if (!toCreate.isEmpty()) {
				getDAO().createAll(app.getAppIdentifier(), toCreate);
			}
			s.setCreatorid(app.getAppIdentifier());
			s.setName(filename);
			s.addProperty("count", count);
			logger.info("Imported {} objects to app '{}'", count, app.getId());
			if (count > 0) {
				getDAO().create(app.getAppIdentifier(), s);
			}
			return ResponseEntity.ok(s);
		} catch (Exception e) {
			logger.error("Failed to import " + filename, e);
			return getStatusResponse(HttpStatus.BAD_REQUEST, "Import failed - " + e.getMessage());
		}
	}

	@GetMapping("/{type}/{id}/links/{type2}/{id2}")
	public ResponseEntity<?> readLink(@PathVariable String type, @PathVariable String id,
			@PathVariable String type2, @PathVariable String id2, HttpServletRequest req) {
		return readLinks(type, id, type2, id2, req);
	}

	@GetMapping("/{type}/{id}/links/{type2}")
	public ResponseEntity<?> readLinksForType(@PathVariable String type, @PathVariable String id,
			@PathVariable String type2, HttpServletRequest req) {
		return readLinks(type, id, type2, null, req);
	}

	@PostMapping("/{type}/{id}/links/{id2}")
	public ResponseEntity<?> createLink(@PathVariable String type, @PathVariable String id,
			@PathVariable String id2) {
		return modifyLink(type, id, id2);
	}

	@PutMapping("/{type}/{id}/links/{id2}")
	public ResponseEntity<?> putLink(@PathVariable String type, @PathVariable String id,
			@PathVariable String id2) {
		return modifyLink(type, id, id2);
	}

	@DeleteMapping("/{type}/{id}/links/{type2}/{id2}")
	public ResponseEntity<?> deleteLink(@PathVariable String type, @PathVariable String id,
			@PathVariable String type2, @PathVariable String id2, HttpServletRequest req) {
		return deleteLinks(type, id, type2, id2, req);
	}

	@DeleteMapping("/{type}/{id}/links")
	public ResponseEntity<?> deleteAllLinks(@PathVariable String type, @PathVariable String id,
			HttpServletRequest req) {
		return deleteLinks(type, id, null, null, req);
	}

	private ResponseEntity<?> readLinks(String type, String id, String type2, String id2, HttpServletRequest req) {
		App app = getPrincipalApp();
		if (app == null) {
			return getStatusResponse(HttpStatus.BAD_REQUEST);
		}
		ParaObject pobj = loadObject(app, type, id);
		if (pobj == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "Object not found: " + id);
		}
		String resolvedType2 = StringUtils.isBlank(type2) ? null : ParaObjectUtils.toObject(app, type2).getType();
		Pager pager = RestUtils.getPagerFromParams(req);
		boolean childrenOnly = StringUtils.isNotBlank(queryParam("childrenonly", req));
		return RestUtils.readLinksHandler(pobj, id2, resolvedType2, pager, childrenOnly, req);
	}

	private ResponseEntity<?> modifyLink(String type, String id, String id2) {
		App app = getPrincipalApp();
		if (app == null) {
			return getStatusResponse(HttpStatus.BAD_REQUEST);
		}
		ParaObject pobj = loadObject(app, type, id);
		if (pobj == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "Object not found: " + id);
		}
		return RestUtils.createLinksHandler(pobj, id2);
	}

	private ResponseEntity<?> deleteLinks(String type, String id, String type2, String id2, HttpServletRequest req) {
		App app = getPrincipalApp();
		if (app == null) {
			return getStatusResponse(HttpStatus.BAD_REQUEST);
		}
		ParaObject pobj = loadObject(app, type, id);
		if (pobj == null) {
			return getStatusResponse(HttpStatus.NOT_FOUND, "Object not found: " + id);
		}
		String resolvedType2 = StringUtils.isBlank(type2) ? null : ParaObjectUtils.toObject(app, type2).getType();
		boolean childrenOnly = StringUtils.isNotBlank(queryParam("childrenonly", req));
		return RestUtils.deleteLinksHandler(pobj, id2, resolvedType2, childrenOnly);
	}

	private ResponseEntity<?> setupInternal(String appid, HttpServletRequest req) {
		if (StringUtils.isBlank(appid)) {
			return ResponseEntity.ok(Para.setup());
		}
		App app = SecurityUtils.getAuthenticatedApp();
		if (app != null && app.isRootApp()) {
			boolean sharedIndex = "true".equalsIgnoreCase(queryParam("sharedIndex", req));
			boolean sharedTable = "true".equalsIgnoreCase(queryParam("sharedTable", req));
			return ResponseEntity.ok(Para.newApp(appid, queryParam(Config._NAME, req),
					queryParam(Config._CREATORID, req), sharedIndex, sharedTable));
		}
		return getStatusResponse(HttpStatus.FORBIDDEN, "Only root app can create and initialize other apps.");
	}

	private String resolveType(App app, String typeParam) {
		if (app == null || StringUtils.isBlank(typeParam)) {
			return typeParam;
		}
		Map<String, String> types = ParaObjectUtils.getAllTypes(app);
		String resolved = types.get(typeParam);
		return (resolved == null) ? typeParam : resolved;
	}

	private ParaObject loadObject(App app, String typeParam, String id) {
		if (app == null || StringUtils.isBlank(typeParam) || StringUtils.isBlank(id)) {
			return null;
		}
		String type = resolveType(app, typeParam);
		if (StringUtils.isBlank(type)) {
			return null;
		}
		ParaObject pobj = ParaObjectUtils.toObject(app, type);
		pobj.setId(id);
		return getDAO().read(app.getAppIdentifier(), pobj.getId());
	}
}
