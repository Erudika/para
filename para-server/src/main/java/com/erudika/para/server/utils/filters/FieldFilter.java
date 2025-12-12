/*
 * Copyright 2013-2022 Erudika. https://erudika.com
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
package com.erudika.para.server.utils.filters;

import com.erudika.para.core.ParaObject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.servlet.http.HttpServletRequest;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Filter response entities dynamically, based on a list of selected fields. Returns partial objects.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@ControllerAdvice
@Order(100)
public class FieldFilter implements ResponseBodyAdvice<Object> {

	private static final Logger LOGGER = LoggerFactory.getLogger(FieldFilter.class);

	@Override
	public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
		return true;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
			Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
			ServerHttpResponse response) {
		try {
			if (body == null) {
				return body;
			}
			HttpServletRequest servletRequest = getServletRequest(request);
			if (servletRequest == null) {
				return body;
			}
			String select = servletRequest.getParameter("select");
			if (StringUtils.isBlank(select)) {
				return body;
			}
			String[] sarr = StringUtils.split(select, ",");
			if (sarr == null || sarr.length == 0) {
				return body;
			}
			List<String> fields = Arrays.asList(sarr);
			Object filtered = filterEntity(body, fields);
			return filtered != null ? filtered : body;
		} catch (Exception e) {
			LOGGER.warn("Failed to limit returned fields using ?select=:", e);
			return body;
		}
	}

	private HttpServletRequest getServletRequest(ServerHttpRequest request) {
		if (request instanceof ServletServerHttpRequest) {
			return ((ServletServerHttpRequest) request).getServletRequest();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private Object filterEntity(Object entity, List<String> fields) {
		if (fields == null || fields.isEmpty()) {
			return null;
		}
		Object newEntity = null;
		if (entity instanceof ParaObject) {
			newEntity = getFilteredProperties(entity, fields);
		} else if (entity instanceof Map) {
			Map<String, Object> map = (Map<String, Object>) entity;
			if (map.containsKey("items") && map.get("items") instanceof List) {
				List<Object> filteredItems = new ArrayList<>();
				for (Object item : (List<?>) map.get("items")) {
					if (item instanceof ParaObject) {
						filteredItems.add(getFilteredProperties(item, fields));
					} else {
						filteredItems.add(item);
					}
				}
				map.put("items", filteredItems);
				newEntity = map;
			}
		} else if (entity instanceof List) {
			List<?> list = (List<?>) entity;
			if (!list.isEmpty() && list.get(0) instanceof ParaObject) {
				List<Map<String, Object>> newList = new ArrayList<>();
				for (Object item : list) {
					if (item instanceof ParaObject) {
						newList.add(getFilteredProperties(item, fields));
					}
				}
				newEntity = newList;
			}
		}
		return newEntity;
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
