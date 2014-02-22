/*
 * Copyright 2014 Erudika.
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
 */
package com.erudika.para.rest;

import com.erudika.para.Para;
import com.erudika.para.core.ParaObject;
import com.erudika.para.persistence.DAO;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.core.JsonParseException;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.apache.commons.lang.BooleanUtils;
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
	private static final String JSON = MediaType.APPLICATION_JSON; // + "; charset=utf-8"
	private static Set<Class<? extends ParaObject>> coreClasses;
	private final DAO dao;
	private final Search search;
	
	public Api1() throws Exception {
		dao = Para.getDAO()	;
		search = Para.getSearch();
		coreClasses = scanForCoreObjects();
		
		// core objects CRUD API
		for (Class<? extends ParaObject> clazz : coreClasses) {
			String type = Utils.type(clazz);
			ParaObject p = clazz.newInstance();
			Resource.Builder coreBuilder = Resource.builder(p.getPlural());
			coreBuilder.addChildResource("search").addMethod("GET").produces(JSON).handledBy(searchHandler(type));
			coreBuilder.addMethod("GET").produces(JSON).handledBy(readAllHandler(type));
			coreBuilder.addChildResource("{id}").addMethod("GET").produces(JSON).handledBy(readHandler(type));
			coreBuilder.addMethod("POST").produces(JSON).consumes(JSON).handledBy(createHandler(type));
			coreBuilder.addChildResource("{id}").addMethod("PUT").produces(JSON).consumes(JSON).handledBy(updateHandler(type));
			coreBuilder.addChildResource("{id}").addMethod("DELETE").produces(JSON).handledBy(deleteHandler(type));
			registerResources(coreBuilder.build());
		}
		
		// search API
		Resource.Builder searchBuilder = Resource.builder("search");
		searchBuilder.addMethod("GET").produces(JSON).handledBy(searchHandler(null));
		registerResources(searchBuilder.build());
		
		// first time setup
		
		// user-defined types
		
		// util functions API
		
	}
	
	private Inflector<ContainerRequestContext, Response> searchHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public final Response apply(ContainerRequestContext ctx) {
				MultivaluedMap<String, String> params = ctx.getUriInfo().getQueryParameters();
				return Response.ok(buildQueryAndSearch(params, type)).build();
			}
		};
	}
	
	private Inflector<ContainerRequestContext, Response> readAllHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public final Response apply(ContainerRequestContext ctx) {
				return Response.ok(search.findQuery(type, "*")).build();
			}
		};
	}
	
	private Inflector<ContainerRequestContext, Response> readHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public final Response apply(ContainerRequestContext ctx) {
				ParaObject obj = Utils.toObject(type);
				obj.setId(ctx.getUriInfo().getPathParameters().getFirst(Config._ID));
				return Utils.getReadResponse(dao.read(obj.getId()));
			}
		};
	}
	
	private Inflector<ContainerRequestContext, Response> createHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public final Response apply(ContainerRequestContext ctx) {
				try {
					Map<String, Object> map = Utils.getJsonReader(Map.class).readValue(ctx.getEntityStream());
					ParaObject obj = Utils.setAnnotatedFields(map);
					return Utils.getCreateResponse(obj, ctx.getUriInfo().getAbsolutePathBuilder());
				} catch (JsonParseException e) {
					return Utils.getJSONResponse(Response.Status.BAD_REQUEST, e.getMessage());
				} catch (IOException e) {
					logger.error(null, e);
					return Utils.getJSONResponse(Response.Status.INTERNAL_SERVER_ERROR, e.toString());
				}
			}
		};
	}
	
	private Inflector<ContainerRequestContext, Response> updateHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public final Response apply(ContainerRequestContext ctx) {
				try {
					ParaObject obj = Utils.toObject(type);
					obj.setId(ctx.getUriInfo().getPathParameters().getFirst(Config._ID));
					Map<String, Object> map = Utils.getJsonReader(Map.class).readValue(ctx.getEntityStream());
					return Utils.getUpdateResponse(dao.read(obj.getId()), map);
				} catch (JsonParseException e) {
					return Utils.getJSONResponse(Response.Status.BAD_REQUEST, e.getMessage());
				} catch (IOException e) {
					logger.error(null, e);
					return Utils.getJSONResponse(Response.Status.INTERNAL_SERVER_ERROR, e.toString());
				}
			}
		};
	}
	
	private Inflector<ContainerRequestContext, Response> deleteHandler(final String type) {
		return new Inflector<ContainerRequestContext, Response>() {
			public final Response apply(ContainerRequestContext ctx) {
				ParaObject obj = Utils.toObject(type);
				obj.setId(ctx.getUriInfo().getPathParameters().getFirst(Config._ID));
				return Utils.getDeleteResponse(obj);
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
				items = search.findTerms(appid, type, terms, Boolean.parseBoolean(matchAll), pager);
			}
		} else if ("wildcard".equals(queryType)) {
			items = search.findWildcard(appid, type, params.getFirst("field"), query, pager);
		} else {
			items = search.findQuery(appid, type, query, pager);
		}
		
		result.put("items", items);
		result.put("totalHits", pager.getCount());
		return result;
	}
	
	private Set<Class<? extends ParaObject>> scanForCoreObjects() {
		Set<Class<? extends ParaObject>> classes = new HashSet<Class<? extends ParaObject>>();
		try {
			ClassLoader cl = Thread.currentThread().getContextClassLoader();
	//		ClassLoader cl = ClassLoader.getSystemClassLoader();
	//		ClassLoader cl = ParaObject.class.getClassLoader();
			
			Set<ClassInfo> s = ClassPath.from(cl).getTopLevelClasses(ParaObject.class.getPackage().getName());
			if( !Config.CORE_PACKAGE_NAME.isEmpty()) {
				Set<ClassInfo> s2 = ClassPath.from(cl).getTopLevelClasses(Config.CORE_PACKAGE_NAME);
				s = new HashSet<ClassInfo>(s);
				s.addAll(s2);
			}

			for (ClassInfo classInfo : s) {
				String type = classInfo.getSimpleName();
				Class<? extends ParaObject> coreClass = Utils.toClass(type, classInfo.getPackageName(), null);
				if (coreClass != null) {
					boolean isAbstract = Modifier.isAbstract(coreClass.getModifiers());
					boolean isInterface = Modifier.isInterface(coreClass.getModifiers());
					boolean isFinal = Modifier.isFinal(coreClass.getModifiers());
					boolean isCoreObject = ParaObject.class.isAssignableFrom(coreClass);
					if (isCoreObject && !isAbstract && !isInterface && !isFinal) {
						classes.add(coreClass);
					}
				}
			}
		} catch (IOException ex) {
			logger.error(null, ex);
		}
		return classes;
	}
	
//	private String toJSON(Object obj) {
//		try {
//			if (obj == null) {
//				return "{}";
//			} else {
//				return Utils.getJsonWriter().writeValueAsString(obj);
//			}
//		} catch (Exception e) {
//			return "{}";
//		}
//	}
}
