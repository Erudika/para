/*
 * Copyright 2013-2020 Erudika. https://erudika.com
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

import static com.erudika.para.Para.getCustomResourceHandlers;
import static com.erudika.para.Para.getDAO;
import static com.erudika.para.Para.getSearch;
import static com.erudika.para.Para.getVersion;

import com.erudika.para.core.App;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.User;
import static com.erudika.para.rest.RestUtils.getBatchCreateResponse;
import static com.erudika.para.rest.RestUtils.getBatchDeleteResponse;
import static com.erudika.para.rest.RestUtils.getBatchReadResponse;
import static com.erudika.para.rest.RestUtils.getBatchUpdateResponse;
import static com.erudika.para.rest.RestUtils.getCreateResponse;
import static com.erudika.para.rest.RestUtils.getDeleteResponse;
import static com.erudika.para.rest.RestUtils.getEntity;
import static com.erudika.para.rest.RestUtils.getOverwriteResponse;
import static com.erudika.para.rest.RestUtils.getReadResponse;
import static com.erudika.para.rest.RestUtils.getStatusResponse;
import static com.erudika.para.rest.RestUtils.getUpdateResponse;
import static com.erudika.para.rest.RestUtils.pathParam;
import static com.erudika.para.rest.RestUtils.queryParam;
import static com.erudika.para.rest.RestUtils.queryParams;

import com.erudika.para.security.SecurityUtils;
import static com.erudika.para.security.SecurityUtils.getPrincipalApp;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.HealthUtils;
import com.erudika.para.utils.HumanTime;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import com.erudika.para.utils.filters.FieldFilter;
import com.erudika.para.validation.Constraint;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.glassfish.jersey.process.Inflector;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.erudika.para.Para.newApp;
import static com.erudika.para.Para.setup;
import com.erudika.para.metrics.Metrics;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * This is the main REST API configuration class which defines all endpoints for all resources
 * and the way API request will be handled. This is API version 1.0.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class Api1 extends ResourceConfig {

	/**
	 * {@value #PATH}.
	 */
	public static final String PATH = "/v1/";

	private static final Logger logger = LoggerFactory.getLogger(Api1.class);

	private static final String JSON = MediaType.APPLICATION_JSON;
	private static final String GET = HttpMethod.GET;
	private static final String PUT = HttpMethod.PUT;
	private static final String POST = HttpMethod.POST;
	private static final String DELETE = HttpMethod.DELETE;
	private static final String PATCH = "PATCH";

	/**
	 * Initializes all of the API resources.
	 */
	public Api1() {
		if (!Config.API_ENABLED) {
			return;
		}

		setApplicationName(Config.getRootAppIdentifier());
		register(GenericExceptionMapper.class);
		register(new JacksonJsonProvider(ParaObjectUtils.getJsonMapper()));
		register(FieldFilter.class);

		// print logo
		Resource.Builder logo = Resource.builder("/");
		logo.addMethod(GET).produces(JSON).handledBy(introHandler());
		registerResources(logo.build());

		// core objects CRUD API
		registerCrudApi("{type}", typeCrudHandler(), linksHandler());

		// search API
		Resource.Builder searchRes = Resource.builder("search/{querytype}");
		searchRes.addMethod(GET).produces(JSON).handledBy(searchHandler(null, null));
		registerResources(searchRes.build());

		// first time newApp
		Resource.Builder setupRes = Resource.builder("_setup");
		setupRes.addMethod(GET).produces(JSON).handledBy(setupHandler());
		setupRes.addChildResource("{appid}").addMethod(GET).produces(JSON).handledBy(setupHandler());
		registerResources(setupRes.build());

		// reset API keys
		Resource.Builder keysRes = Resource.builder("_newkeys");
		keysRes.addMethod(POST).produces(JSON).handledBy(keysHandler());
		registerResources(keysRes.build());

		// user-defined types
		Resource.Builder typesRes = Resource.builder("_types");
		typesRes.addMethod(GET).produces(JSON).handledBy(listTypesHandler());
		registerResources(typesRes.build());

		// current user/app object
		Resource.Builder meRes = Resource.builder("_me");
		meRes.addMethod(GET).produces(JSON).handledBy(meHandler());
		registerResources(meRes.build());

		// getValidationConstraints by id
		Resource.Builder idRes = Resource.builder("_id/{id}");
		idRes.addMethod(GET).produces(JSON).handledBy(readIdHandler());
		registerResources(idRes.build());

		// validation
		Resource.Builder valRes = Resource.builder("_constraints");
		valRes.addMethod(GET).produces(JSON).handledBy(getConstrHandler(null));
		valRes.addChildResource("{type}").addMethod(GET).produces(JSON).handledBy(getConstrHandler(null));
		valRes.addChildResource("{type}/{field}/{cname}").addMethod(PUT).produces(JSON).handledBy(addConstrHandler(null));
		valRes.addChildResource("{type}/{field}/{cname}").addMethod(DELETE).produces(JSON).handledBy(removeConstrHandler(null));
		registerResources(valRes.build());

		// permissions
		Resource.Builder permRes = Resource.builder("_permissions");
		permRes.addMethod(GET).produces(JSON).handledBy(getPermitHandler(null));
		permRes.addChildResource("{subjectid}").addMethod(GET).produces(JSON).handledBy(getPermitHandler(null));
		permRes.addChildResource("{subjectid}/{type}/{method}").addMethod(GET).produces(JSON).handledBy(checkPermitHandler(null));
		permRes.addChildResource("{subjectid}/{type}").addMethod(PUT).produces(JSON).handledBy(grantPermitHandler(null));
		permRes.addChildResource("{subjectid}/{type}").addMethod(DELETE).produces(JSON).handledBy(revokePermitHandler(null));
		permRes.addChildResource("{subjectid}").addMethod(DELETE).produces(JSON).handledBy(revokePermitHandler(null));
		registerResources(permRes.build());

		// app settings
		Resource.Builder appSettingsRes = Resource.builder("_settings");
		appSettingsRes.addMethod(GET).produces(JSON).handledBy(appSettingsHandler(null));
		appSettingsRes.addMethod(PUT).produces(JSON).handledBy(appSettingsHandler(null));
		appSettingsRes.addChildResource("{key}").addMethod(GET).produces(JSON).handledBy(appSettingsHandler(null));
		appSettingsRes.addChildResource("{key}").addMethod(PUT).produces(JSON).handledBy(appSettingsHandler(null));
		appSettingsRes.addChildResource("{key}").addMethod(DELETE).produces(JSON).handledBy(appSettingsHandler(null));
		registerResources(appSettingsRes.build());

		// health check
		Resource.Builder healthCheckRes = Resource.builder("_health");
		healthCheckRes.addMethod(GET).produces(JSON).handledBy(healthCheckHandler());
		registerResources(healthCheckRes.build());

		// util functions API
		Resource.Builder utilsRes = Resource.builder("utils/{method}");
		utilsRes.addMethod(GET).produces(JSON).handledBy(utilsHandler());
		registerResources(utilsRes.build());

		// rebuild index
		Resource.Builder reindexRes = Resource.builder("_reindex");
		reindexRes.addMethod(POST).produces(JSON).handledBy(reindexHandler());
		registerResources(reindexRes.build());

		// register custom resources
		for (final CustomResourceHandler handler : getCustomResourceHandlers()) {
			Resource.Builder custom = Resource.builder(handler.getRelativePath());
			custom.addMethod(GET).produces(JSON).handledBy(new Inflector<ContainerRequestContext, Response>() {
				public Response apply(ContainerRequestContext ctx) {
					String appid = ParaObjectUtils.getAppidFromAuthHeader(ctx.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
					try (Metrics.Context context = Metrics.time(appid, handler.getClass(), "handleGet")) {
						return handler.handleGet(ctx);
					}
				}
			});
			custom.addMethod(POST).produces(JSON).consumes(JSON).
					handledBy(new Inflector<ContainerRequestContext, Response>() {
				public Response apply(ContainerRequestContext ctx) {
					String appid = ParaObjectUtils.getAppidFromAuthHeader(ctx.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
					try (Metrics.Context context = Metrics.time(appid, handler.getClass(), "handlePost")) {
						return handler.handlePost(ctx);
					}
				}
			});
			custom.addMethod(PATCH).produces(JSON).consumes(JSON).
					handledBy(new Inflector<ContainerRequestContext, Response>() {
				public Response apply(ContainerRequestContext ctx) {
					String appid = ParaObjectUtils.getAppidFromAuthHeader(ctx.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
					try (Metrics.Context context = Metrics.time(appid, handler.getClass(), "handlePatch")) {
						return handler.handlePatch(ctx);
					}
				}
			});
			custom.addMethod(PUT).produces(JSON).consumes(JSON).
					handledBy(new Inflector<ContainerRequestContext, Response>() {
				public Response apply(ContainerRequestContext ctx) {
					String appid = ParaObjectUtils.getAppidFromAuthHeader(ctx.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
					try (Metrics.Context context = Metrics.time(appid, handler.getClass(), "handlePut")) {
						return handler.handlePut(ctx);
					}
				}
			});
			custom.addMethod(DELETE).produces(JSON).handledBy(new Inflector<ContainerRequestContext, Response>() {
				public Response apply(ContainerRequestContext ctx) {
					String appid = ParaObjectUtils.getAppidFromAuthHeader(ctx.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
					try (Metrics.Context context = Metrics.time(appid, handler.getClass(), "handleDelete")) {
						return handler.handleDelete(ctx);
					}
				}
			});
			registerResources(custom.build());
		}
	}

	private void registerCrudApi(String path, Inflector<ContainerRequestContext, Response> handler,
			Inflector<ContainerRequestContext, Response> linksHandler) {
		Resource.Builder core = Resource.builder(path);
		// list endpoints (both do the same thing)
		core.addMethod(GET).produces(JSON).handledBy(handler);
		core.addChildResource("search/{querytype}").addMethod(GET).produces(JSON).handledBy(handler);
		core.addChildResource("search").addMethod(GET).produces(JSON).handledBy(handler);
		// CRUD endpoints (non-batch)
		core.addMethod(POST).produces(JSON).consumes(JSON).handledBy(handler);
		core.addChildResource("{id}").addMethod(GET).produces(JSON).handledBy(handler);
		core.addChildResource("{id}").addMethod(PUT).produces(JSON).consumes(JSON).handledBy(handler);
		core.addChildResource("{id}").addMethod(PATCH).produces(JSON).consumes(JSON).handledBy(handler);
		core.addChildResource("{id}").addMethod(DELETE).produces(JSON).handledBy(handler);
		// links CRUD endpoints
		core.addChildResource("{id}/links/{type2}/{id2}").addMethod(GET).produces(JSON).handledBy(linksHandler);
		core.addChildResource("{id}/links/{type2}").addMethod(GET).produces(JSON).handledBy(linksHandler);
		core.addChildResource("{id}/links/{id2}").addMethod(POST).produces(JSON).handledBy(linksHandler);
		core.addChildResource("{id}/links/{id2}").addMethod(PUT).produces(JSON).handledBy(linksHandler);
		core.addChildResource("{id}/links/{type2}/{id2}").addMethod(DELETE).produces(JSON).handledBy(linksHandler);
		core.addChildResource("{id}/links").addMethod(DELETE).produces(JSON).handledBy(linksHandler);
		// CRUD endpoints (batch)
		Resource.Builder batch = Resource.builder("_batch");
		batch.addMethod(POST).produces(JSON).consumes(JSON).handledBy(batchCreateHandler(null));
		batch.addMethod(GET).produces(JSON).handledBy(batchReadHandler(null));
		batch.addMethod(PUT).produces(JSON).consumes(JSON).handledBy(batchCreateHandler(null));
		batch.addMethod(PATCH).produces(JSON).consumes(JSON).handledBy(batchUpdateHandler(null));
		batch.addMethod(DELETE).produces(JSON).handledBy(batchDeleteHandler(null));

		registerResources(core.build());
		registerResources(batch.build());
	}

	private Inflector<ContainerRequestContext, Response> utilsHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				MultivaluedMap<String, String> params = ctx.getUriInfo().getQueryParameters();
				String method = pathParam("method", ctx);
				method = StringUtils.isBlank(method) ? params.getFirst("method") : method;
				if ("newid".equals(method)) {
					return Response.ok(Utils.getNewId(), MediaType.TEXT_PLAIN_TYPE).build();
				} else if ("timestamp".equals(method)) {
					return Response.ok(Utils.timestamp(), MediaType.TEXT_PLAIN_TYPE).build();
				} else if ("formatdate".equals(method)) {
					String format = params.getFirst("format");
					String locale = params.getFirst("locale");
					Locale loc = Utils.getLocale(locale);
					return Response.ok(Utils.formatDate(format, loc), MediaType.TEXT_PLAIN_TYPE).build();
				} else if ("formatmessage".equals(method)) {
					String msg = params.getFirst("message");
					Object[] paramz = params.get("fields").toArray();
					return Response.ok(Utils.formatMessage(msg, paramz), MediaType.TEXT_PLAIN_TYPE).build();
				} else if ("nospaces".equals(method)) {
					String str = params.getFirst("string");
					String repl = params.getFirst("replacement");
					return Response.ok(Utils.noSpaces(str, repl), MediaType.TEXT_PLAIN_TYPE).build();
				} else if ("nosymbols".equals(method)) {
					String str = params.getFirst("string");
					return Response.ok(Utils.stripAndTrim(str), MediaType.TEXT_PLAIN_TYPE).build();
				} else if ("md2html".equals(method)) {
					String md = params.getFirst("md");
					return Response.ok(Utils.markdownToHtml(md), MediaType.TEXT_HTML).build();
				} else if ("timeago".equals(method)) {
					String d = params.getFirst("delta");
					long delta = NumberUtils.toLong(d, 1);
					return Response.ok(HumanTime.approximately(delta), MediaType.TEXT_PLAIN_TYPE).build();
				}
				return getStatusResponse(Response.Status.BAD_REQUEST, "Unknown method: " +
						((method == null) ? "empty" : method));
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> introHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				Map<String, String> info = new TreeMap<>();
				info.put("info", "Para - the backend for busy developers.");
				if (Config.getConfigBoolean("print_version", true)) {
					info.put("version", StringUtils.replace(getVersion(), "-SNAPSHOT", ""));
				}
				return Response.ok(info).build();
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> typeCrudHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String typePlural = pathParam(Config._TYPE, ctx);
				App app = getPrincipalApp();
				if (app != null && !StringUtils.isBlank(typePlural)) {
					String type = ParaObjectUtils.getAllTypes(app).get(typePlural);
					if (type == null) {
						type = typePlural;
					}
					return crudHandler(app, type).apply(ctx);
				}
				return getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	/**
	 * @param app {@link App}
	 * @param type a type
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> crudHandler(final App app, final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String id = pathParam(Config._ID, ctx);
				if (StringUtils.isBlank(id)) {
					if (GET.equals(ctx.getMethod())) {
						return searchHandler(app, type).apply(ctx);
					} else if (POST.equals(ctx.getMethod())) {
						return createHandler(app, type).apply(ctx);
					} else if (ctx.getUriInfo().getPath().contains("search")) {
						return searchHandler(app, type).apply(ctx);
					}
				} else {
					if (GET.equals(ctx.getMethod())) {
						return readHandler(app, type).apply(ctx);
					} else if (PUT.equals(ctx.getMethod())) {
						return overwriteHandler(app, type).apply(ctx);
					} else if (PATCH.equals(ctx.getMethod())) {
						return updateHandler(app, type).apply(ctx);
					} else if (DELETE.equals(ctx.getMethod())) {
						return deleteHandler(app, type).apply(ctx);
					}
				}
				return getStatusResponse(Response.Status.NOT_FOUND, "Type '" + type + "' not found.");
			}
		};
	}

	/**
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> linksHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				MultivaluedMap<String, String> params = ctx.getUriInfo().getQueryParameters();
				MultivaluedMap<String, String> pathp = ctx.getUriInfo().getPathParameters();
				String id = pathp.getFirst(Config._ID);
				String type = pathp.getFirst(Config._TYPE);
				String id2 = pathp.getFirst("id2");
				String type2 = pathp.getFirst("type2");
				App app = getPrincipalApp();

				if (app == null) {
					return getStatusResponse(Response.Status.BAD_REQUEST);
				}

				String typeSingular = (type == null) ? null : ParaObjectUtils.getAllTypes(app).get(type);
				type = (typeSingular == null) ? type : typeSingular;

				id2 = StringUtils.isBlank(id2) ? null : id2;
				type2 = StringUtils.isBlank(type2) ? null : ParaObjectUtils.toObject(app, type2).getType();

				ParaObject pobj = ParaObjectUtils.toObject(app, type);
				pobj.setId(id);
				pobj = getDAO().read(app.getAppIdentifier(), pobj.getId());

				Pager pager = RestUtils.getPagerFromParams(params);
				String childrenOnly = params.getFirst("childrenonly");

				if (pobj != null) {
					if (POST.equals(ctx.getMethod()) || PUT.equals(ctx.getMethod())) {
						return RestUtils.createLinksHandler(pobj, id2);
					} else if (GET.equals(ctx.getMethod())) {
						return RestUtils.readLinksHandler(pobj, id2, type2, params, pager, childrenOnly != null);
					} else if (DELETE.equals(ctx.getMethod())) {
						return RestUtils.deleteLinksHandler(pobj, id2, type2, childrenOnly != null);
					}
				}
				return getStatusResponse(Response.Status.NOT_FOUND, "Object not found: " + id);
			}
		};
	}

	/**
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> meHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				if (GET.equals(ctx.getMethod())) {
					User user = SecurityUtils.getAuthenticatedUser();
					App app = SecurityUtils.getAuthenticatedApp();
					if (user != null) {
						return Response.ok(user).build();
					} else if (app != null) {
						return Response.ok(app).build();
					}
				}
				return Response.status(Response.Status.UNAUTHORIZED).build();
			}
		};
	}

	/**
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> readIdHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = getPrincipalApp();
				String id = pathParam(Config._ID, ctx);
				if (app != null) {
					return getReadResponse(app, getDAO().read(app.getAppIdentifier(), id));
				}
				return getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> getConstrHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				String type = pathParam(Config._TYPE, ctx);
				if (app != null) {
					if (type != null) {
						return Response.ok(app.getAllValidationConstraints(type)).build();
					} else {
						return Response.ok(app.getAllValidationConstraints()).build();
					}
				}
				return getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @return response
	 */
	@SuppressWarnings("unchecked")
	public static Inflector<ContainerRequestContext, Response> addConstrHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				String type = pathParam(Config._TYPE, ctx);
				String field = pathParam("field", ctx);
				String cname = pathParam("cname", ctx);
				if (app != null) {
					Response payloadRes = getEntity(ctx.getEntityStream(), Map.class);
					if (payloadRes.getStatusInfo() == Response.Status.OK) {
						Map<String, Object> payload = (Map<String, Object>) payloadRes.getEntity();
						if (app.addValidationConstraint(type, field, Constraint.build(cname, payload))) {
							app.update();
						}
					}
					return Response.ok(app.getAllValidationConstraints(type)).build();
				}
				return getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> removeConstrHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				String type = pathParam(Config._TYPE, ctx);
				String field = pathParam("field", ctx);
				String cname = pathParam("cname", ctx);
				if (app != null) {
					if (app.removeValidationConstraint(type, field, cname)) {
						app.update();
					}
					return Response.ok(app.getAllValidationConstraints(type)).build();
				}
				return getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> getPermitHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				String subjectid = pathParam("subjectid", ctx);
				if (app != null) {
					if (subjectid != null) {
						return Response.ok(app.getAllResourcePermissions(subjectid)).build();
					} else {
						return Response.ok(app.getAllResourcePermissions()).build();
					}
				}
				return getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> checkPermitHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				String subjectid = pathParam("subjectid", ctx);
				String resourcePath = pathParam(Config._TYPE, ctx);
				String httpMethod = pathParam("method", ctx);
				if (app != null) {
					return Response.ok(app.isAllowedTo(subjectid, resourcePath, httpMethod),
							MediaType.TEXT_PLAIN_TYPE).build();
				}
				return getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @return response
	 */
	@SuppressWarnings("unchecked")
	public static Inflector<ContainerRequestContext, Response> grantPermitHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				String subjectid = pathParam("subjectid", ctx);
				String resourcePath = pathParam(Config._TYPE, ctx);
				if (app != null) {
					Response resp = getEntity(ctx.getEntityStream(), List.class);
					if (resp.getStatusInfo() == Response.Status.OK) {
						List<String> permission = (List<String>) resp.getEntity();
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
							return Response.ok(app.getAllResourcePermissions(subjectid)).build();
						} else {
							return getStatusResponse(Response.Status.BAD_REQUEST, "No allowed methods specified.");
						}
					} else {
						return resp;
					}
				}
				return getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> revokePermitHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				String subjectid = pathParam("subjectid", ctx);
				String type = pathParam(Config._TYPE, ctx);
				if (app != null) {
					boolean revoked;
					if (type != null) {
						revoked = app.revokeResourcePermission(subjectid, type);
					} else {
						revoked = app.revokeAllResourcePermissions(subjectid);
					}
					if (revoked) {
						app.update();
					}
					return Response.ok(app.getAllResourcePermissions(subjectid)).build();
				}
				return getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @return response
	 */
	@SuppressWarnings("unchecked")
	public static Inflector<ContainerRequestContext, Response> appSettingsHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				String key = pathParam("key", ctx);
				if (app != null) {
					if (PUT.equals(ctx.getMethod())) {
						Response resp = getEntity(ctx.getEntityStream(), Map.class);
						if (resp.getStatusInfo() == Response.Status.OK) {
							Map<String, Object> setting = (Map<String, Object>) resp.getEntity();
							if (!StringUtils.isBlank(key) && setting.containsKey("value")) {
								app.addSetting(key, setting.get("value"));
							} else {
								app.clearSettings().addAllSettings(setting);
							}
							app.update();
							return Response.ok().build();
						} else {
							return getStatusResponse(Response.Status.BAD_REQUEST);
						}
					} else if (DELETE.equals(ctx.getMethod())) {
						app.removeSetting(key);
						app.update();
						return Response.ok().build();
					} else {
						if (StringUtils.isBlank(key)) {
							return Response.ok(app.getSettings()).build();
						} else {
							return Response.ok(Collections.singletonMap("value", app.getSetting(key))).build();
						}
					}
				}
				return getStatusResponse(Response.Status.FORBIDDEN, "Not allowed.");
			}
		};
	}

	private static Inflector<ContainerRequestContext, Response> healthCheckHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				if (HealthUtils.getInstance().isHealthy()) {
					return Response.ok(Collections.singletonMap("message", "healthy")).build();
				} else {
					return getStatusResponse(Response.Status.INTERNAL_SERVER_ERROR, "unhealthy");
				}
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> listTypesHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = getPrincipalApp();
				if (app != null) {
					return Response.ok(ParaObjectUtils.getAllTypes(app)).build();
				}
				return getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> keysHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = SecurityUtils.getAuthenticatedApp();
				if (app != null) {
					app.resetSecret();
					CoreUtils.getInstance().overwrite(app);
					Map<String, String> creds = app.getCredentials();
					creds.put("info", "Save the secret key! It is showed only once!");
					return Response.ok(creds).build();
				}
				return getStatusResponse(Response.Status.UNAUTHORIZED, "Not an app.");
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> setupHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String appid = pathParam(Config._APPID, ctx);
				if (!StringUtils.isBlank(appid)) {
					App app = SecurityUtils.getAuthenticatedApp();
					if (app != null && app.isRootApp()) {
						boolean sharedIndex = "true".equals(queryParam("sharedIndex", ctx));
						boolean sharedTable = "true".equals(queryParam("sharedTable", ctx));
						return Response.ok(newApp(appid, queryParam("name", ctx), sharedIndex, sharedTable)).build();
					} else {
						return getStatusResponse(Response.Status.FORBIDDEN,
								"Only root app can create and initialize other apps.");
					}
				}
				return Response.ok(setup()).build();
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @param type a type
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> createHandler(final App a, final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				return getCreateResponse(app, type, ctx.getEntityStream());
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @param type a type
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> readHandler(final App a, final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				ParaObject obj = ParaObjectUtils.toObject(app, type);
				obj.setId(pathParam(Config._ID, ctx));
				if (app.getId().equals(obj.getId())) {
					return getReadResponse(app, app);
				}
				return getReadResponse(app, getDAO().read(app.getAppIdentifier(), obj.getId()));
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @param type a type
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> updateHandler(final App a, final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				ParaObject obj = ParaObjectUtils.toObject(app, type);
				obj.setId(pathParam(Config._ID, ctx));
				// allow apps to partially update themselves
				String appid = StringUtils.equals(type, Utils.type(App.class)) ? app.getAppid() : app.getAppIdentifier();
				// partial update - equivalent to PATCH method
				return getUpdateResponse(app, getDAO().read(appid, obj.getId()), ctx.getEntityStream());
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @param type a type
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> overwriteHandler(final App a, final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				// full update - equivalent to PUT method
				App app = (a != null) ? a : getPrincipalApp();
				return getOverwriteResponse(app, pathParam(Config._ID, ctx), type, ctx.getEntityStream());
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @param type a type
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> deleteHandler(final App a, final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				ParaObject obj = ParaObjectUtils.toObject(app, type);
				obj.setId(pathParam(Config._ID, ctx));
				return getDeleteResponse(app, obj);
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> batchCreateHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				return getBatchCreateResponse(app, ctx.getEntityStream());
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> batchReadHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				return getBatchReadResponse(app, queryParams("ids", ctx));
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @return response
	 */
	@SuppressWarnings("unchecked")
	public static Inflector<ContainerRequestContext, Response> batchUpdateHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				Response entityRes = getEntity(ctx.getEntityStream(), List.class);
				if (entityRes.getStatusInfo() == Response.Status.OK) {
					List<Map<String, Object>> newProps = (List<Map<String, Object>>) entityRes.getEntity();
					ArrayList<String> ids = new ArrayList<>(newProps.size());
					for (Map<String, Object> props : newProps) {
						if (props != null && props.containsKey(Config._ID)) {
							ids.add((String) props.get(Config._ID));
						}
					}
					return getBatchUpdateResponse(app, getDAO().readAll(app.getAppIdentifier(), ids, true), newProps);
				} else {
					return entityRes;
				}
			}
		};
	}

	/**
	 * @param a {@link App}
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> batchDeleteHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : getPrincipalApp();
				return getBatchDeleteResponse(app, queryParams("ids", ctx));
			}
		};
	}

	/**
	 * @param app {@link App}
	 * @param type a type
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> searchHandler(final App app, final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app1 = (app == null) ? getPrincipalApp() : app;
				MultivaluedMap<String, String> params = ctx.getUriInfo().getQueryParameters();
				String query = params.getFirst("q");
				if (!StringUtils.isBlank(query) && !getSearch().isValidQueryString(query)) {
					return getStatusResponse(Response.Status.BAD_REQUEST, "Invalid query string syntax q=" +
							query + " in request " + ctx.getMethod() + " " + ctx.getUriInfo().getRequestUri());
				}
				String queryType = pathParam("querytype", ctx);
				if (StringUtils.isBlank(queryType)) {
					queryType = "default";
				}
				return Response.ok(RestUtils.buildQueryAndSearch(app1, queryType, params, type)).build();
			}
		};
	}

	/**
	 * @return response
	 */
	public static Inflector<ContainerRequestContext, Response> reindexHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = getPrincipalApp();
				if (app != null) {
					long startTime = System.nanoTime();
					MultivaluedMap<String, String> params = ctx.getUriInfo().getQueryParameters();
					Pager pager = RestUtils.getPagerFromParams(params);
					String destinationIndex = params.getFirst("destinationIndex");
					getSearch().rebuildIndex(getDAO(), app, destinationIndex, pager);
					long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
					Map<String, Object> response = new HashMap<>(2);
					response.put("reindexed", pager.getCount());
					response.put("tookMillis", tookMillis);
					return Response.ok(response, JSON).build();
				}
				return getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}
}
