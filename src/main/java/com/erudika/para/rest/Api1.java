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
import com.erudika.para.rest.RestUtils.ForbiddenExceptionMapper;
import com.erudika.para.rest.RestUtils.GenericExceptionMapper;
import com.erudika.para.rest.RestUtils.InternalExceptionMapper;
import com.erudika.para.rest.RestUtils.NotFoundExceptionMapper;
import com.erudika.para.rest.RestUtils.UnavailableExceptionMapper;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.HumanTime;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.ApplicationPath;
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
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
@ApplicationPath("/v1/")
public final class Api1 extends ResourceConfig {

	private static final Logger logger = LoggerFactory.getLogger(Api1.class);

	private static final String JSON = MediaType.APPLICATION_JSON; // + "; charset=utf-8"?
	private static final String GET = HttpMethod.GET;
	private static final String PUT = HttpMethod.PUT;
	private static final String POST = HttpMethod.POST;
	private static final String DELETE = HttpMethod.DELETE;

	private static Set<Class<? extends ParaObject>> coreClasses = new HashSet<Class<? extends ParaObject>>();
	private static Set<String> allTypes = new LinkedHashSet<String>();

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

		RestUtils.scanForDomainClasses(coreClasses, allTypes);
		setApplicationName(Config.APP_NAME_NS);

//		register(JsonParseExceptionMapper.class);
//		register(JsonMappingExceptionMapper.class);
		register(GenericExceptionMapper.class);
		register(ForbiddenExceptionMapper.class);
		register(NotFoundExceptionMapper.class);
		register(InternalExceptionMapper.class);
		register(UnavailableExceptionMapper.class);

		// core objects CRUD API
		try {
			for (Class<? extends ParaObject> clazz : coreClasses) {
				ParaObject p = clazz.newInstance();
				registerCrudApi(p.getPlural(), crudHandler(Utils.type(clazz)));
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}

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
		typesRes.addMethod(GET).produces(JSON).handledBy(readTypesHandler());
		typesRes.addMethod(POST).produces(JSON).consumes(JSON).handledBy(addRemoveTypesHandler());
		typesRes.addMethod(DELETE).produces(JSON).handledBy(addRemoveTypesHandler());
		registerResources(typesRes.build());
		registerCrudApi("{type}", typeCrudHandler());

		// util functions API
		Resource.Builder utilsRes = Resource.builder("utils");
		utilsRes.addMethod(GET).produces(JSON).handledBy(utilsHandler());
		registerResources(utilsRes.build());
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
				String id = ctx.getUriInfo().getPathParameters().getFirst(Config._ID);
				String type = ctx.getUriInfo().getPathParameters().getFirst(Config._TYPE);
				String appid = Config.APP_NAME_NS; // TODO
				App app = dao.read(new App(appid).getId());
				if (app != null && !StringUtils.isBlank(type)) {
					if (app.getDatatypes().contains(type)) {
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
					} else {
						return RestUtils.getStatusResponse(Response.Status.BAD_REQUEST,
								"Type '" + type + "' is undefined.");
					}
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "Type '" + type + "' not found.");
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

	private Inflector<ContainerRequestContext, Response> addRemoveTypesHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String appid = Config.APP_NAME_NS; // TODO
				App app = dao.read(new App(appid).getId());
				Map<String, Object> tmap = RestUtils.getMapFromEntity(ctx.getEntityStream());
				if (app != null && tmap != null) {
					String datatype = (String) tmap.get("type");
					if (StringUtils.isBlank(datatype)) {
						if (POST.equals(ctx.getMethod())) {
							app.addDatatypes(datatype);
						} else if (DELETE.equals(ctx.getMethod())) {
							app.removeDatatypes(datatype);
						}
						return Response.ok(app.getDatatypes()).build();
					} else {
						return RestUtils.getStatusResponse(Response.Status.BAD_REQUEST, "'type' cannot be empty.");
					}
				} else {
					return RestUtils.getStatusResponse(Response.Status.BAD_REQUEST);
				}
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> readTypesHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String appid = Config.APP_NAME_NS; // TODO
				App app = dao.read(new App(appid).getId());
				if (app != null) {
					allTypes.addAll(app.getDatatypes());
				}
				return Response.ok(allTypes).build();
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> keysHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String appid = Config.APP_NAME_NS; // TODO
				App app = dao.read(new App(appid).getId());
				if (app != null) {
					app.resetSecret();
					app.update();
					return Response.ok(app.credentialsMap()).build();
				}
				return RestUtils.getStatusResponse(Response.Status.NOT_FOUND, "App not found: " + appid);
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> setupHandler() {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				App app = new App(Config.APP_NAME_NS);
				if (app.exists()) {
					return RestUtils.getStatusResponse(Response.Status.OK, "All set!");
				} else {
					app.setName(Config.APP_NAME);
					app.create();
					return Response.ok(app.credentialsMap()).build();
				}
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> searchHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				MultivaluedMap<String, String> params = ctx.getUriInfo().getQueryParameters();
				return Response.ok(buildQueryAndSearch(params, type)).build();
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

	private Inflector<ContainerRequestContext, Response> createHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				return RestUtils.getCreateResponse(type, ctx.getEntityStream());
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> updateHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				String id = ctx.getUriInfo().getPathParameters().getFirst(Config._ID);
				return RestUtils.getUpdateResponse(dao.read(id), ctx.getEntityStream());
			}
		};
	}

	private Inflector<ContainerRequestContext, Response> deleteHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public Response apply(ContainerRequestContext ctx) {
				ParaObject obj = Utils.toObject(type);
				obj.setId(ctx.getUriInfo().getPathParameters().getFirst(Config._ID));
				return RestUtils.getDeleteResponse(obj);
			}
		};
	}

	private <P extends ParaObject> Map<String, Object> buildQueryAndSearch(MultivaluedMap<String, String> params,
			String typeOverride) {
		String query = params.containsKey("q") ? params.getFirst("q") : "*";
		String type = (typeOverride != null) ? typeOverride : params.getFirst(Config._TYPE);
		String appid = Config.APP_NAME_NS; // TODO
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
