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
import com.erudika.para.core.App;
import com.erudika.para.core.PObject;
import com.erudika.para.persistence.DAO;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Utils;
import java.util.List;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.process.Inflector;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class Api1 extends ResourceConfig {

	private static final Logger logger = LoggerFactory.getLogger(Api1.class);
	private static final String JSON = MediaType.APPLICATION_JSON + "; charset=utf-8";
	private final DAO dao;
	private final Search search;
	
	public Api1() {
		dao = Para.getDAO();
		search = Para.getSearch();

		final App app = new App();
		final Resource.Builder getRes = Resource.builder();
		getRes.path(app.getPlural()+"/{id}");

		final ResourceMethod.Builder getAll = getRes.addMethod("GET");
		getAll.produces(JSON).handledBy(new Inflector<ContainerRequestContext, Response>() {
			public final Response apply(ContainerRequestContext ctx) {
				String id = ctx.getUriInfo().getPathParameters(true).getFirst("id");
				return Utils.getReadResponse(dao.read(id));
			}
		});
		registerResources(getRes.build());
		
		
		final Resource.Builder getAllRes = Resource.builder();
		getAllRes.path(app.getPlural());
		final ResourceMethod.Builder get = getAllRes.addMethod("GET");
		get.produces(JSON).handledBy(new Inflector<ContainerRequestContext, List<PObject>>() {
			public final List<PObject> apply(ContainerRequestContext ctx) {
				return search.findQuery(app.getClassname(), "*");
			}
		});
		registerResources(getAllRes.build());

		
	}

	private String toJSON(Object obj) {
		try {
			if (obj == null) {
				return "{}";
			} else {
				return Utils.getObjectMapper().writeValueAsString(obj);
			}
		} catch (Exception e) {
			return "{}";
		}
	}
}
