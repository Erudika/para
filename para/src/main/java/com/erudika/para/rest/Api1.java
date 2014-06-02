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

import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.persistence.DAO;
import com.erudika.para.rest.RestUtils.GenericExceptionMapper;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.HumanTime;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
public final class Api1 extends ResourceConfig {

	public static final String PATH = "/v1/";

	private static final Logger logger = LoggerFactory.getLogger(Api1.class);

	private static final String JSON = MediaType.APPLICATION_JSON; // + "; charset=utf-8"?
	private static final String GET = HttpMethod.GET;
	private static final String PUT = HttpMethod.PUT;
	private static final String POST = HttpMethod.POST;
	private static final String DELETE = HttpMethod.DELETE;

	// maps plural to singular type definitions
	private static final Map<String, String> coreTypes = new HashMap<String, String>();

	private final DAO dao;
	private final Search search;

	/**
	 * Initializes all of the API resources.
	 */
	public Api1() {
		dao = Para.getDAO()	;
		search = Para.getSearch();

		if (!Config.REST_ENABLED) {
			return;
		}

		setApplicationName(Config.APP_NAME_NS);

		register(new JacksonJsonProvider(Utils.getJsonMapper()));
		register(GenericExceptionMapper.class);
//		register(ForbiddenExceptionMapper.class);
//		register(NotFoundExceptionMapper.class);
//		register(InternalExceptionMapper.class);
//		register(UnavailableExceptionMapper.class);

		initCoreTypes();

		// core objects CRUD API
		registerCrudApi("{type}", typeCrudHandler());
		registerBatchCrudApi(batchCrudHandler());

		// search API
		Resource.Builder searchRes = Resource.builder("search");
		searchRes.addMethod(GET).produces(JSON).handledBy(searchHandler(null));
		registerResources(searchRes.build());

		// first time setup
		Resource.Builder setupRes = Resource.builder("setup");
		setupRes.addMethod(GET).produces(JSON).handledBy(setupHandler());
		registerResources(setupRes.build());

		// reset API keys
		Resource.Builder keysRes = Resource.builder("newkeys");
		keysRes.addMethod(POST).produces(JSON).handledBy(keysHandler());
		registerResources(keysRes.build());

		// user-defined types
		Resource.Builder typesRes = Resource.builder("types");
		typesRes.addMethod(GET).produces(JSON).handledBy(listTypesHandler());
		registerResources(typesRes.build());

		// util functions API
		Resource.Builder utilsRes = Resource.builder("utils");
		utilsRes.addMethod(GET).produces(JSON).handledBy(utilsHandler());
		registerResources(utilsRes.build());
	}

	private void initCoreTypes() {
		Set<Class<? extends ParaObject>> coreClasses = new HashSet<Class<? extends ParaObject>>();
		RestUtils.scanForDomainClasses(coreClasses);
		try {
			for (Class<? extends ParaObject> clazz : coreClasses) {
				ParaObject p = clazz.newInstance();
				coreTypes.put(p.getPlural(), p.getType());
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}
	}

	private void registerCrudApi(String path, Inflector<ContainerRequestContext, Response> handler) {
		Resource.Builder core = Resource.builder(path);
		core.addMethod(GET).produces(JSON).handledBy(handler);
		core.addMethod(POST).produces(JSON).consumes(JSON).handledBy(handler);
		core.addChildResource("search").addMethod(GET).produces(JSON).handledBy(handler);
		core.addChildResource("{id}").addMethod(GET).produces(JSON).handledBy(handler);
		core.addChildResource("{id}").addMethod(PUT).produces(JSON).consumes(JSON).handledBy(handler);
		core.addChildResource("{id}").addMethod(DELETE).produces(JSON).handledBy(handler);
		registerResources(core.build());
	}

	private void registerBatchCrudApi(Inflector<ContainerRequestContext, Response> handler) {
		Resource.Builder batch = Resource.builder();
		batch.addMethod(GET).produces(JSON).handledBy(handler);
		batch.addMethod(POST).produces(JSON).consumes(JSON).handledBy(handler);
		batch.addChildResource("search").addMethod(GET).produces(JSON).handledBy(handler);
		batch.addChildResource("{id}").addMethod(GET).produces(JSON).handledBy(handler);
		batch.addChildResource("{id}").addMethod(PUT).produces(JSON).consumes(JSON).handledBy(handler);
		batch.addChildResource("{id}").addMethod(DELETE).produces(JSON).handledBy(handler);
		registerResources(batch.build());
	}

	private Inflector<ContainerRequestContext, Response> utilsHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				MultivaluedMap<String, String> params = ctx.getUriInfo().getQueryParameters();
				String method = params.getFirst("method");
				if ("newid".equals(method)) {
					return Response.ok(Collections.singletonMap("value", Utils.getNewId())).build();
				} else if ("timestamp".equals(method)) {
//					String type = params.getFirst(Config._TYPE);
					return Response.ok(Collections.singletonMap("value", Utils.timestamp())).build();
				} else if ("currentyear".equals(method)) {
					return Response.ok(Collections.singletonMap("value", Utils.getCurrentYear())).build();
				} else if ("formatdate".equals(method)) {
					String format = params.getFirst("format");
					String locale = params.getFirst("locale");
					Locale loc = LocaleUtils.toLocale(locale);
					return Response.ok(Collections.singletonMap("value", Utils.formatDate(format, loc))).build();
				} else if ("formatmessage".equals(method)) {
					String msg = params.getFirst("message");
					Object[] paramz = params.get("fields").toArray();
					return Response.ok(Collections.singletonMap("value", Utils.formatMessage(msg, paramz))).build();
				} else if ("nospaces".equals(method)) {
					String str = params.getFirst("string");
					String repl = params.getFirst("replacement");
					return Response.ok(Collections.singletonMap("value", Utils.noSpaces(str, repl))).build();
				} else if ("nosymbols".equals(method)) {
					String str = params.getFirst("string");
					return Response.ok(Collections.singletonMap("value", Utils.stripAndTrim(str))).build();
				} else if ("md2html".equals(method)) {
					String md = params.getFirst("md");
					return Response.ok(Collections.singletonMap("value", Utils.markdownToHtml(md))).build();
				} else if ("timeago".equals(method)) {
					String d = params.getFirst("delta");
					long delta = NumberUtils.toLong(d, 1);
					return Response.ok(Collections.singletonMap("value", HumanTime.approximately(delta))).build();
				}
				return RestUtils.getStatusResponse(Response.Status.BAD_REQUEST, "Method not specified.");
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> typeCrudHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String typePlural = ctx.getUriInfo().getPathParameters().getFirst(Config._TYPE);
				String appid = RestUtils.getPrincipalAppid(ctx.getSecurityContext().getUserPrincipal());
				App app = dao.read(new App(appid).getId());
				if (app != null && !StringUtils.isBlank(typePlural)) {
					String type = coreTypes.get(typePlural);
					if (type == null) {
						type = app.getDatatypes().get(typePlural);
					}
					// register a new data type
					if (type == null && POST.equals(ctx.getMethod())) {
						Response res = crudHandler(type).apply(ctx);
						Object ent = res.getEntity();
						if (ent != null && ent instanceof ParaObject) {
							type = ((ParaObject) ent).getType();
							typePlural = ((ParaObject) ent).getPlural();
							if (type != null) {
								app.addDatatype(typePlural, type);
								app.update();
							}
						}
						return res;
					}
					if (type == null) {
						type = typePlural;
					}
					return crudHandler(type).apply(ctx);
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "App not found: " + appid);
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> crudHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String id = ctx.getUriInfo().getPathParameters().getFirst(Config._ID);
				if (StringUtils.isBlank(id)) {
					if (GET.equals(ctx.getMethod())) {
						return searchHandler(type).apply(ctx);
					} else if (POST.equals(ctx.getMethod())) {
						return createHandler(type).apply(ctx);
					} else if (ctx.getUriInfo().getPath().endsWith("search")) {
						return searchHandler(type).apply(ctx);
					}
				} else {
					if (GET.equals(ctx.getMethod())) {
						return readHandler(type).apply(ctx);
					} else if (PUT.equals(ctx.getMethod())) {
						return updateHandler(type).apply(ctx);
					} else if (DELETE.equals(ctx.getMethod())) {
						return deleteHandler(type).apply(ctx);
					}
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "Type '" + type + "' not found.");
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> batchCrudHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				if (POST.equals(ctx.getMethod())) {
					return batchCreateHandler().apply(ctx);
				} else if (GET.equals(ctx.getMethod())) {
					return batchReadHandler().apply(ctx);
				} else if (PUT.equals(ctx.getMethod())) {
					return batchUpdateHandler().apply(ctx);
				} else if (DELETE.equals(ctx.getMethod())) {
					return batchDeleteHandler().apply(ctx);
				}
				return RestUtils.getStatusResponse(Response.Status.BAD_REQUEST, "Unknown method.");
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> listTypesHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String appid = RestUtils.getPrincipalAppid(ctx.getSecurityContext().getUserPrincipal());
				App app = dao.read(new App(appid).getId());
				if (app != null) {
					Map<String, String> allTypes = new HashMap<String, String>(app.getDatatypes());
					allTypes.putAll(coreTypes);
					return Response.ok(allTypes).build();
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "App not found: " + appid);
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> keysHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String appid = RestUtils.getPrincipalAppid(ctx.getSecurityContext().getUserPrincipal());
				App app = dao.read(new App(appid).getId());
				if (app != null) {
					app.resetSecret();
					app.update();
					Map<String, String> creds = app.getCredentials();
					creds.put("info", "Save the secret key! It is showed only once!");
					return Response.ok(creds).build();
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "App not found: " + appid);
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> setupHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = new App(Config.APP_NAME_NS); // the root app name
				if (app.exists()) {
					return RestUtils.getStatusResponse(Response.Status.OK, "All set!");
				} else {
					app.setName(Config.APP_NAME);
					app.create();
					Map<String, String> creds = app.getCredentials();
					creds.put("info", "Save the secret key! It is showed only once!");
					return Response.ok(creds).build();
				}
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> createHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				return RestUtils.getCreateResponse(type, ctx.getEntityStream());
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> readHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				ParaObject obj = Utils.toObject(type);
				obj.setId(ctx.getUriInfo().getPathParameters().getFirst(Config._ID));
				return RestUtils.getReadResponse(dao.read(obj.getId()));
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> updateHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				ParaObject obj = Utils.toObject(type);
				obj.setType(type);
				obj.setId(ctx.getUriInfo().getPathParameters().getFirst(Config._ID));
				return RestUtils.getUpdateResponse(dao.read(obj.getId()), ctx.getEntityStream());
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> deleteHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				ParaObject obj = Utils.toObject(type);
				String appid = RestUtils.getPrincipalAppid(ctx.getSecurityContext().getUserPrincipal());
				obj.setType(type);
				obj.setAppid(appid);
				obj.setId(ctx.getUriInfo().getPathParameters().getFirst(Config._ID));
				return RestUtils.getDeleteResponse(obj);
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> batchCreateHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String appid = RestUtils.getPrincipalAppid(ctx.getSecurityContext().getUserPrincipal());
				return RestUtils.getBatchCreateResponse(appid, ctx.getEntityStream());
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> batchReadHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String appid = RestUtils.getPrincipalAppid(ctx.getSecurityContext().getUserPrincipal());
				return RestUtils.getBatchReadResponse(appid, ctx.getUriInfo().getQueryParameters().get("ids"));
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> batchUpdateHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String appid = RestUtils.getPrincipalAppid(ctx.getSecurityContext().getUserPrincipal());
				return RestUtils.getBatchUpdateResponse(appid, ctx.getEntityStream());
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> batchDeleteHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String appid = RestUtils.getPrincipalAppid(ctx.getSecurityContext().getUserPrincipal());
				return RestUtils.getBatchDeleteResponse(appid, ctx.getEntityStream());
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> searchHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String appid = RestUtils.getPrincipalAppid(ctx.getSecurityContext().getUserPrincipal());
				MultivaluedMap<String, String> params = ctx.getUriInfo().getQueryParameters();
				return Response.ok(buildQueryAndSearch(appid, params, type)).build();
			}
		};
	}

	private <P extends ParaObject> Map<String, Object> buildQueryAndSearch(String appid,
			MultivaluedMap<String, String> params, String typeOverride) {
		String query = params.containsKey("q") ? params.getFirst("q") : "*";
		String type = (typeOverride != null) ? typeOverride : params.getFirst(Config._TYPE);
		String desc = params.containsKey("desc") ? params.getFirst("desc") : "true";

		Pager pager = new Pager();
		pager.setPage(NumberUtils.toLong(params.getFirst("page"), 0));
		pager.setSortby(params.getFirst("sort"));
		pager.setDesc(Boolean.parseBoolean(desc));

		String queryType = params.containsKey("querytype") ? params.getFirst("querytype") : null;
		Map<String, Object> result = new HashMap<String, Object>();
		List<P> items = new ArrayList<P>();

		if ("id".equals(queryType)) {
			String id = params.containsKey(Config._ID) ? params.getFirst(Config._ID) : null;
			P obj = search.findById(appid, id);
			if (obj != null) {
				items = Collections.singletonList(obj);
				pager.setCount(1);
			}
		} else if ("nearby".equals(queryType)) {
			List<String> ids = params.get("ids");
			items = search.findByIds(appid, ids);
			pager.setCount(items.size());
		} else if ("nearby".equals(queryType)) {
			String latlng = params.getFirst("latlng");
			if (latlng != null) {
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
		result.put("totalHits", pager.getCount());
		return result;
	}

}
