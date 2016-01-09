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
package com.erudika.para.utils.filters;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

/**
 * Filter response entities dynamically, based on a list of selected fields. Returns partial objects.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Provider
public class FieldFilter implements ContainerResponseFilter {

	@Context
	private HttpServletRequest request;

	@Override
	public void filter(ContainerRequestContext requestContext,
			ContainerResponseContext responseContext) throws IOException {
		if (responseContext.getEntity() != null) {
			String[] sarr = StringUtils.split(request.getParameter("select"), ",");
			List<String> fields = sarr == null ? new ArrayList<String>(0) : Arrays.asList(sarr);
			filterObject(fields, responseContext.getEntity());
		}
	}

	private void filterObject(List<String> fields, Object entity) {
		if (fields == null || fields.isEmpty()) {
			return;
		}
		if (entity instanceof List) {
			for (Object obj : (List) entity) {
				filterObject(fields, obj);
			}
		} else if (entity instanceof Map) {
			for (Object obj : ((Map) entity).values()) {
				filterObject(fields, obj);
			}
		} else if (entity instanceof Object[]) {
			for (Object obj : (Object[]) entity) {
				filterObject(fields, obj);
			}
		} else if (!ClassUtils.isPrimitiveOrWrapper(entity.getClass()) && !(entity instanceof String)) {
			for (Field field : entity.getClass().getDeclaredFields()) {
				try {
					String fieldName = field.getName();
					field.setAccessible(true);
					if (!fields.contains(fieldName)) {
						field.set(entity, null);
					}
				} catch (Exception e) {
					LoggerFactory.getLogger(this.getClass()).warn(null, e);
				}
			}
		}
	}
}
