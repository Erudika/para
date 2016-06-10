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

import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.User;
import com.erudika.para.persistence.DAO;
import com.erudika.para.search.Search;
import com.erudika.para.security.SecurityUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.HumanTime;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import com.erudika.para.utils.filters.FieldFilter;
import com.erudika.para.validation.Constraint;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.glassfish.jersey.process.Inflector;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main REST API configuration class which defines all endpoints for all resources
 * and the way API request will be handled. This is API version 1.0.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class Api1 extends ResourceConfig {

	public static final String PATH = "/v1/";

	private static final Logger logger = LoggerFactory.getLogger(Api1.class);

	private static final String JSON = MediaType.APPLICATION_JSON;
	private static final String GET = HttpMethod.GET;
	private static final String PUT = HttpMethod.PUT;
	private static final String POST = HttpMethod.POST;
	private static final String DELETE = HttpMethod.DELETE;
	private static final String PATCH = "PATCH";

	private final DAO dao;
	private final Search search;

	/**
	 * Initializes all of the API resources.
	 */
	public Api1() {
		dao = Para.getDAO()	;
		search = Para.getSearch();

		if (!Config.API_ENABLED) {
			return;
		}

		setApplicationName(Config.APP_NAME_NS);

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

		// first time setup
		Resource.Builder setupRes = Resource.builder("_setup");
		setupRes.addMethod(GET).produces(JSON).handledBy(setupHandler());
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

		// util functions API
		Resource.Builder utilsRes = Resource.builder("utils/{method}");
		utilsRes.addMethod(GET).produces(JSON).handledBy(utilsHandler());
		registerResources(utilsRes.build());

		// register custom resources
		for (final CustomResourceHandler handler : Para.getCustomResourceHandlers()) {
			Resource.Builder custom = Resource.builder(handler.getRelativePath());
			custom.addMethod(GET).produces(JSON).handledBy(new Inflector<ContainerRequestContext, Response>() {
				public Response apply(ContainerRequestContext ctx) {
					return handler.handleGet(ctx);
				}
			});
			custom.addMethod(POST).produces(JSON).consumes(JSON).
					handledBy(new Inflector<ContainerRequestContext, Response>() {
				public Response apply(ContainerRequestContext ctx) {
					return handler.handlePost(ctx);
				}
			});
			custom.addMethod(PUT).produces(JSON).consumes(JSON).
					handledBy(new Inflector<ContainerRequestContext, Response>() {
				public Response apply(ContainerRequestContext ctx) {
					return handler.handlePut(ctx);
				}
			});
			custom.addMethod(DELETE).produces(JSON).handledBy(new Inflector<ContainerRequestContext, Response>() {
				public Response apply(ContainerRequestContext ctx) {
					return handler.handleDelete(ctx);
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
		batch.addMethod(POST).produces(JSON).consumes(JSON).handledBy(batchCreateHandler());
		batch.addMethod(GET).produces(JSON).handledBy(batchReadHandler());
		batch.addMethod(PUT).produces(JSON).consumes(JSON).handledBy(batchCreateHandler());
		batch.addMethod(PATCH).produces(JSON).consumes(JSON).handledBy(batchUpdateHandler());
		batch.addMethod(DELETE).produces(JSON).handledBy(batchDeleteHandler());

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
					Locale loc = getLocale(locale);
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
				return RestUtils.getStatusResponse(Response.Status.BAD_REQUEST, "Unknown method: " +
						((method == null) ? "empty" : method));
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> introHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				Map<String, String> info = new TreeMap<String, String>();
				info.put("info", "Para - a backend for busy developers.");
				if (Config.getConfigBoolean("print_version", true)) {
					info.put("version", StringUtils.replace(Para.getVersion(), "-SNAPSHOT", ""));
				}
				return Response.ok(info).build();
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> typeCrudHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String typePlural = pathParam(Config._TYPE, ctx);
				App app = RestUtils.getPrincipalApp();
				if (app != null && !StringUtils.isBlank(typePlural)) {
					String type = ParaObjectUtils.getAllTypes(app).get(typePlural);
					if (type == null) {
						type = typePlural;
					}
					return crudHandler(app, type).apply(ctx);
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> crudHandler(final App app, final String type) {
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
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "Type '" + type + "' not found.");
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> linksHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				MultivaluedMap<String, String> params = ctx.getUriInfo().getQueryParameters();
				MultivaluedMap<String, String> pathp = ctx.getUriInfo().getPathParameters();
				String id = pathp.getFirst(Config._ID);
				String type = pathp.getFirst(Config._TYPE);
				String id2 = pathp.getFirst("id2");
				String type2 = pathp.getFirst("type2");
				App app = RestUtils.getPrincipalApp();

				if (app == null) {
					return RestUtils.getStatusResponse(Response.Status.BAD_REQUEST);
				}

				String typeSingular = (type == null) ? null : ParaObjectUtils.getAllTypes(app).get(type);
				type = (typeSingular == null) ? type : typeSingular;

				id2 = StringUtils.isBlank(id2) ? params.getFirst(Config._ID) : id2;
				type2 = StringUtils.isBlank(type2) ? params.getFirst(Config._TYPE) : type2;

				ParaObject pobj = ParaObjectUtils.toObject(type);
				pobj.setId(id);
				pobj = dao.read(app.getAppIdentifier(), pobj.getId());

				Pager pager = new Pager();
				pager.setPage(NumberUtils.toLong(params.getFirst("page"), 0));
				pager.setSortby(params.getFirst("sort"));
				pager.setDesc(Boolean.parseBoolean(params.containsKey("desc") ? params.getFirst("desc") : "true"));
				pager.setLimit(NumberUtils.toInt(params.getFirst("limit"), pager.getLimit()));

				String childrenOnly = params.getFirst("childrenonly");

				if (pobj != null) {
					if (POST.equals(ctx.getMethod()) || PUT.equals(ctx.getMethod())) {
						if (id2 != null) {
							String linkid = pobj.link(id2);
							if (linkid == null) {
								return RestUtils.getStatusResponse(Response.Status.BAD_REQUEST,
										"Failed to create link.");
							} else {
								return Response.ok(linkid, MediaType.TEXT_PLAIN_TYPE).build();
							}
						} else {
							return RestUtils.getStatusResponse(Response.Status.BAD_REQUEST,
									"Parameters 'type' and 'id' are missing.");
						}
					} else if (GET.equals(ctx.getMethod())) {
						Map<String, Object> result = new HashMap<String, Object>();
						if (type2 != null) {
							if (id2 != null) {
								return Response.ok(pobj.isLinked(type2, id2), MediaType.TEXT_PLAIN_TYPE).build();
							} else {
								List<ParaObject> items = new ArrayList<ParaObject>();
								if (childrenOnly == null) {
									if (params.containsKey("count")) {
										pager.setCount(pobj.countLinks(type2));
									} else {
										items = pobj.getLinkedObjects(type2, pager);
									}
								} else {
									if (params.containsKey("count")) {
										pager.setCount(pobj.countChildren(type2));
									} else {
										if (params.containsKey("field") && params.containsKey("term")) {
											items = pobj.getChildren(type2, params.getFirst("field"),
													params.getFirst("term"), pager);
										} else {
											items = pobj.getChildren(type2, pager);
										}
									}
								}
								result.put("items", items);
								result.put("totalHits", pager.getCount());
								return Response.ok(result).build();
							}
						} else {
							return RestUtils.getStatusResponse(Response.Status.BAD_REQUEST,
									"Parameter 'type' is missing.");
						}
					} else if (DELETE.equals(ctx.getMethod())) {
						if (type2 == null && id2 == null) {
							pobj.unlinkAll();
						} else if (type2 != null) {
							if (id2 != null) {
								pobj.unlink(type2, id2);
							} else if (childrenOnly != null) {
								pobj.deleteChildren(type2);
							}
						}
						return Response.ok().build();
					}
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "Object not found: " + id);
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> meHandler() {
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

	protected final Inflector<ContainerRequestContext, Response> readIdHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = RestUtils.getPrincipalApp();
				String id = pathParam(Config._ID, ctx);
				if (app != null) {
					return RestUtils.getReadResponse(dao.read(app.getAppIdentifier(), id));
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> getConstrHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : RestUtils.getPrincipalApp();
				String type = pathParam(Config._TYPE, ctx);
				if (app != null) {
					if (type != null) {
						return Response.ok(app.getAllValidationConstraints(type)).build();
					} else {
						return Response.ok(app.getAllValidationConstraints()).build();
					}
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	@SuppressWarnings("unchecked")
	protected final Inflector<ContainerRequestContext, Response> addConstrHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : RestUtils.getPrincipalApp();
				String type = pathParam(Config._TYPE, ctx);
				String field = pathParam("field", ctx);
				String cname = pathParam("cname", ctx);
				if (app != null) {
					Response payloadRes = RestUtils.getEntity(ctx.getEntityStream(), Map.class);
					if (payloadRes.getStatusInfo() == Response.Status.OK) {
						Map<String, Object> payload = (Map<String, Object>) payloadRes.getEntity();
						if (app.addValidationConstraint(type, field, Constraint.build(cname, payload))) {
							app.update();
						}
					}
					return Response.ok(app.getAllValidationConstraints(type)).build();
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> removeConstrHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : RestUtils.getPrincipalApp();
				String type = pathParam(Config._TYPE, ctx);
				String field = pathParam("field", ctx);
				String cname = pathParam("cname", ctx);
				if (app != null) {
					if (app.removeValidationConstraint(type, field, cname)) {
						app.update();
					}
					return Response.ok(app.getAllValidationConstraints(type)).build();
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> getPermitHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : RestUtils.getPrincipalApp();
				String subjectid = pathParam("subjectid", ctx);
				if (app != null) {
					if (subjectid != null) {
						return Response.ok(app.getAllResourcePermissions(subjectid)).build();
					} else {
						return Response.ok(app.getAllResourcePermissions()).build();
					}
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> checkPermitHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : RestUtils.getPrincipalApp();
				String subjectid = pathParam("subjectid", ctx);
				String resourcePath = pathParam(Config._TYPE, ctx);
				String httpMethod = pathParam("method", ctx);
				if (app != null) {
					return Response.ok(app.isAllowedTo(subjectid, resourcePath, httpMethod),
							MediaType.TEXT_PLAIN_TYPE).build();
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	@SuppressWarnings("unchecked")
	protected final Inflector<ContainerRequestContext, Response> grantPermitHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : RestUtils.getPrincipalApp();
				String subjectid = pathParam("subjectid", ctx);
				String resourcePath = pathParam(Config._TYPE, ctx);
				if (app != null) {
					Response resp = RestUtils.getEntity(ctx.getEntityStream(), List.class);
					if (resp.getStatusInfo() == Response.Status.OK) {
						List<String> permission = (List<String>) resp.getEntity();
						Set<App.AllowedMethods> set = new HashSet<App.AllowedMethods>(permission.size());
						for (String perm : permission) {
							if (!StringUtils.isBlank(perm)) {
								App.AllowedMethods method = App.AllowedMethods.fromString(perm);
								if (method != null) {
									set.add(method);
								}
							}
						}
						if (!set.isEmpty()) {
							boolean isPublic = set.contains(App.AllowedMethods.GUEST);
							if (app.grantResourcePermission(subjectid, resourcePath, EnumSet.copyOf(set), isPublic)) {
								app.update();
							}
							return Response.ok(app.getAllResourcePermissions(subjectid)).build();
						} else {
							return RestUtils.getStatusResponse(Response.Status.BAD_REQUEST, "No allowed methods specified.");
						}
					} else {
						return resp;
					}
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> revokePermitHandler(final App a) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = (a != null) ? a : RestUtils.getPrincipalApp();
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
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> listTypesHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = RestUtils.getPrincipalApp();
				if (app != null) {
					return Response.ok(ParaObjectUtils.getAllTypes(app)).build();
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "App not found.");
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
				return RestUtils.getStatusResponse(Response.Status.UNAUTHORIZED, "Not an app.");
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> setupHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				return Response.ok(Para.setup()).build();
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> createHandler(final App app, final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				return RestUtils.getCreateResponse(app, type, ctx.getEntityStream());
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> readHandler(final App app, final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				ParaObject obj = ParaObjectUtils.toObject(type);
				obj.setId(pathParam(Config._ID, ctx));
				return RestUtils.getReadResponse(dao.read(app.getAppIdentifier(), obj.getId()));
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> updateHandler(final App app, final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				ParaObject obj = ParaObjectUtils.toObject(type);
				obj.setType(type);
				obj.setId(pathParam(Config._ID, ctx));
				// partial update - equivalent to PATCH method
				return RestUtils.getUpdateResponse(app, dao.read(app.getAppIdentifier(), obj.getId()),
						ctx.getEntityStream());
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> overwriteHandler(final App app, final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				// full update - equivalent to PUT method
				return RestUtils.getOverwriteResponse(app, pathParam(Config._ID, ctx), type, ctx.getEntityStream());
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> deleteHandler(final App app, final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				ParaObject obj = ParaObjectUtils.toObject(type);
				obj.setType(type);
				obj.setId(pathParam(Config._ID, ctx));
				return RestUtils.getDeleteResponse(app, obj);
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> batchCreateHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = RestUtils.getPrincipalApp();
				return RestUtils.getBatchCreateResponse(app, ctx.getEntityStream());
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> batchReadHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = RestUtils.getPrincipalApp();
				return RestUtils.getBatchReadResponse(app, queryParams("ids", ctx));
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> batchUpdateHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = RestUtils.getPrincipalApp();
				return RestUtils.getBatchUpdateResponse(app, ctx.getEntityStream());
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> batchDeleteHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = RestUtils.getPrincipalApp();
				return RestUtils.getBatchDeleteResponse(app, queryParams("ids", ctx));
			}
		};
	}

	protected final Inflector<ContainerRequestContext, Response> searchHandler(final App app, final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app1 = (app == null) ? RestUtils.getPrincipalApp() : app;
				MultivaluedMap<String, String> params = ctx.getUriInfo().getQueryParameters();
				String queryType = pathParam("querytype", ctx);
				return Response.ok(buildQueryAndSearch(app1, queryType, params, type)).build();
			}
		};
	}

	private <P extends ParaObject> Map<String, Object> buildQueryAndSearch(App app, String queryType,
			MultivaluedMap<String, String> params, String typeOverride) {
		String query = params.containsKey("q") ? params.getFirst("q") : "*";
		String appid = app.getAppIdentifier();
		String type = (!StringUtils.isBlank(typeOverride) && !"search".equals(typeOverride)) ?
				typeOverride : params.getFirst(Config._TYPE);

		Pager pager = new Pager();
		pager.setPage(NumberUtils.toLong(params.getFirst("page"), 0));
		pager.setSortby(params.getFirst("sort"));
		pager.setDesc(Boolean.parseBoolean(params.containsKey("desc") ? params.getFirst("desc") : "true"));
		pager.setLimit(NumberUtils.toInt(params.getFirst("limit"), pager.getLimit()));

		queryType = StringUtils.isBlank(queryType) ? params.getFirst("querytype") : queryType;
		Map<String, Object> result = new HashMap<String, Object>();
		List<P> items = new ArrayList<P>();

		if ("id".equals(queryType)) {
			String id = params.containsKey(Config._ID) ? params.getFirst(Config._ID) : null;
			P obj = search.findById(appid, id);
			if (obj != null) {
				items = Collections.singletonList(obj);
				pager.setCount(1);
			}
		} else if ("ids".equals(queryType)) {
			List<String> ids = params.get("ids");
			items = search.findByIds(appid, ids);
			pager.setCount(items.size());
		} else if ("nearby".equals(queryType)) {
			String latlng = params.getFirst("latlng");
			if (StringUtils.contains(latlng, ",")) {
				String[] coords = latlng.split(",", 2);
				String rad = params.containsKey("radius") ? params.getFirst("radius") : null;
				int radius = NumberUtils.toInt(rad, 10);
				double lat = NumberUtils.toDouble(coords[0], 0);
				double lng = NumberUtils.toDouble(coords[1], 0);
				items = search.findNearby(appid, type, query, radius, lat, lng, pager);
			}
		} else if ("prefix".equals(queryType)) {
			items = search.findPrefix(appid, type, params.getFirst("field"), params.getFirst("prefix"), pager);
		} else if ("similar".equals(queryType)) {
			List<String> fields = params.get("fields");
			if (fields != null) {
				items = search.findSimilar(appid, type, params.getFirst("filterid"),
						fields.toArray(new String[]{}), params.getFirst("like"), pager);
			}
		} else if ("tagged".equals(queryType)) {
			List<String> tags = params.get("tags");
			if (tags != null) {
				items = search.findTagged(appid, type, tags.toArray(new String[]{}), pager);
			}
		} else if ("in".equals(queryType)) {
			items = search.findTermInList(appid, type, params.getFirst("field"), params.get("terms"), pager);
		} else if ("terms".equals(queryType)) {
			String matchAll = params.containsKey("matchall") ? params.getFirst("matchall") : "true";
			List<String> termsList = params.get("terms");
			if (termsList != null) {
				Map<String, String> terms = new HashMap<String, String>(termsList.size());
				for (String termTuple : termsList) {
					if (!StringUtils.isBlank(termTuple) && termTuple.contains(Config.SEPARATOR)) {
						String[] split = termTuple.split(Config.SEPARATOR, 2);
						terms.put(split[0], split[1]);
					}
				}
				if (params.containsKey("count")) {
					pager.setCount(search.getCount(appid, type, terms));
				} else {
					items = search.findTerms(appid, type, terms, Boolean.parseBoolean(matchAll), pager);
				}
			}
		} else if ("wildcard".equals(queryType)) {
			items = search.findWildcard(appid, type, params.getFirst("field"), query, pager);
		} else if ("count".equals(queryType)) {
			pager.setCount(search.getCount(appid, type));
		} else {
			items = search.findQuery(appid, type, query, pager);
		}

		result.put("items", items);
		result.put("page", pager.getPage());
		result.put("totalHits", pager.getCount());
		return result;
	}

	protected String pathParam(String param, ContainerRequestContext ctx) {
		return ctx.getUriInfo().getPathParameters().getFirst(param);
	}

	protected List<String> pathParams(String param, ContainerRequestContext ctx) {
		return ctx.getUriInfo().getPathParameters().get(param);
	}

	protected String queryParam(String param, ContainerRequestContext ctx) {
		return ctx.getUriInfo().getQueryParameters().getFirst(param);
	}

	protected List<String> queryParams(String param, ContainerRequestContext ctx) {
		return ctx.getUriInfo().getQueryParameters().get(param);
	}

	protected boolean hasQueryParam(String param, ContainerRequestContext ctx) {
		return ctx.getUriInfo().getQueryParameters().containsKey(param);
	}

	private Locale getLocale(String localeStr) {
		try {
			return LocaleUtils.toLocale(localeStr);
		} catch (Exception e) {
			return Locale.US;
		}
	}
}
