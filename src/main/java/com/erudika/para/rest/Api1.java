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
import com.erudika.para.utils.Utils;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.process.Inflector;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
@ApplicationPath(value = "/v1/")
public class Api1 extends ResourceConfig {

	private static final Logger logger = LoggerFactory.getLogger(Api1.class);
	private static final String JSON = MediaType.APPLICATION_JSON; // + "; charset=utf-8"
	private static Set<Class<? extends ParaObject>> coreClasses;
	private final DAO dao;
	private final Search search;
	
	public Api1() {
		dao = Para.getDAO();
		search = Para.getSearch();
		coreClasses = scanForCoreObjects();

		try {
			for (Class<? extends ParaObject> clazz : coreClasses) {
				String classname = Utils.classname(clazz);
				ParaObject p = clazz.newInstance();
				final Resource.Builder builder = Resource.builder(p.getPlural());
				builder.addMethod("GET").produces(JSON).handledBy(readAllHandler(classname));
				builder.addChildResource("{id}").addMethod("GET").produces(JSON).handledBy(readHandler(classname));
				builder.addMethod("POST").produces(JSON).consumes(JSON).handledBy(createHandler(classname));
				builder.addChildResource("{id}").addMethod("PUT").produces(JSON).consumes(JSON).handledBy(updateHandler(classname));
				builder.addChildResource("{id}").addMethod("DELETE").produces(JSON).handledBy(deleteHandler(classname));
				registerResources(builder.build());
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}
		
	}
	
	private Inflector<ContainerRequestContext, Response> readAllHandler(final String classname) {
		return new Inflector<ContainerRequestContext, Response>() {
			public final Response apply(ContainerRequestContext ctx) {
				return Response.ok(search.findQuery(classname, "*")).build();
			}
		};
	}
	
	private Inflector<ContainerRequestContext, Response> readHandler(final String classname) {
		return new Inflector<ContainerRequestContext, Response>() {
			public final Response apply(ContainerRequestContext ctx) {
				ParaObject obj = Utils.toObject(classname);
				obj.setId(ctx.getUriInfo().getPathParameters(true).getFirst("id"));
				return Utils.getReadResponse(dao.read(obj.getId()));
			}
		};
	}
	
	private Inflector<ContainerRequestContext, Response> createHandler(final String classname) {
		return new Inflector<ContainerRequestContext, Response>() {
			public final Response apply(ContainerRequestContext ctx) {
				try {
					Map<String, Object> map = Utils.getJsonReader(Map.class).readValue(ctx.getEntityStream());
					ParaObject obj = Utils.setAnnotatedFields(map);
					return Utils.getCreateResponse(obj, ctx.getUriInfo().getAbsolutePathBuilder());
				} catch (Exception e) {
					logger.error(null, e);
					return Utils.getJSONResponse(Response.Status.INTERNAL_SERVER_ERROR, e.toString());
				}
			}
		};
	}
	
	private Inflector<ContainerRequestContext, Response> updateHandler(final String classname) {
		return new Inflector<ContainerRequestContext, Response>() {
			public final Response apply(ContainerRequestContext ctx) {
				try {
					ParaObject obj = Utils.toObject(classname);
					obj.setId(ctx.getUriInfo().getPathParameters(true).getFirst("id"));
					Map<String, Object> map = Utils.getJsonReader(Map.class).readValue(ctx.getEntityStream());
					return Utils.getUpdateResponse(dao.read(obj.getId()), map);
				} catch (Exception e) {
					logger.error(null, e);
					return Utils.getJSONResponse(Response.Status.INTERNAL_SERVER_ERROR, e.toString());
				}
			}
		};
	}
	
	private Inflector<ContainerRequestContext, Response> deleteHandler(final String classname) {
		return new Inflector<ContainerRequestContext, Response>() {
			public final Response apply(ContainerRequestContext ctx) {
				try {
					ParaObject obj = Utils.toObject(classname);
					obj.setId(ctx.getUriInfo().getPathParameters(true).getFirst("id"));
					return Utils.getDeleteResponse(obj);
				} catch (Exception e) {
					logger.error(null, e);
					return Utils.getJSONResponse(Response.Status.INTERNAL_SERVER_ERROR, e.toString());
				}
			}
		};
	}
	
	
	private static Set<Class<? extends ParaObject>> scanForCoreObjects() {
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
				String classname = classInfo.getSimpleName();
				Class<? extends ParaObject> coreClass = Utils.toClass(classname, classInfo.getPackageName(), null);
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
	
	private void initDynamicResources() {
		
	}
	
	private void initResources() {
		
	}
	
	
	public static void main(String[] args) throws IOException {
		scanForCoreObjects();
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
