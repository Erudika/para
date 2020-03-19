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
package com.erudika.para.utils.filters;

import com.erudika.para.core.ParaObject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import org.apache.commons.beanutils.PropertyUtils;
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
	@SuppressWarnings("unchecked")
	public void filter(ContainerRequestContext requestContext,
			ContainerResponseContext responseContext) throws IOException {
		try {
			if (responseContext.getEntity() != null && !StringUtils.isBlank(request.getParameter("select"))) {
				String[] sarr = StringUtils.split(request.getParameter("select"), ",");
				List<String> fields = sarr == null ? new ArrayList<>(0) : Arrays.asList(sarr);
				Object entity = responseContext.getEntity();
				Object newEntity = null;
				if (entity instanceof ParaObject) {
					newEntity = getFilteredProperties(entity, fields);
				} else if (entity instanceof Map) {
					if (((Map) entity).containsKey("items")) {
						newEntity = new ArrayList<Map<String, Object>>();
						for (ParaObject item : (List<ParaObject>) ((Map) entity).get("items")) {
							((List) newEntity).add(getFilteredProperties(item, fields));
						}
						((Map) entity).put("items", newEntity);
					}
				} else if (entity instanceof List) {
					newEntity = new ArrayList<Map<String, Object>>();
					if (!((List) entity).isEmpty() && ((List) entity).get(0) instanceof ParaObject) {
						for (ParaObject item : (List<ParaObject>) entity) {
							((List) newEntity).add(getFilteredProperties(item, fields));
						}
					}
				}
				if (newEntity != null) {
					responseContext.setEntity(newEntity);
				}
			}
		} catch (Exception e) {
			LoggerFactory.getLogger(this.getClass()).warn("Failed to limit returned fields using ?select=:", e);
		}
	}

	private Map<String, Object> getFilteredProperties(Object object, List<String> fields) {
		Map<String, Object> newItem = new HashMap<>();
		for (String f : fields) {
			String field = StringUtils.trimToEmpty(f);
			newItem.put(field, getProperty(object, field));
		}
		return newItem;
	}

	private Object getProperty(Object obj, String prop) {
		if (obj != null && !StringUtils.isBlank(prop)) {
			try {
				Method m = PropertyUtils.getReadMethod(new PropertyDescriptor(prop, obj.getClass()));
				if (m != null && !m.isAnnotationPresent(JsonIgnore.class)) {
					return PropertyUtils.getProperty(obj, prop);
				}
			} catch (Exception e) {	}
		}
		return null;
	}
}
