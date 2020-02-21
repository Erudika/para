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
package com.erudika.para.aop;

import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.utils.Utils;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * A few helper methods.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class AOPUtils {

	private static final String SPECIAL_PREFIX = "_";

	private AOPUtils() { }

	@SuppressWarnings("unchecked")
	protected static <T> List<T> getArgOfListOfType(Object[] args, Class<T> type) {
		if (args != null) {
			for (Object arg : args) {
				if (arg != null && arg instanceof List) {
					List<T> list = (List) arg;
					if (!list.isEmpty() && type.isAssignableFrom(list.get(0).getClass())) {
						return list;
					}
				}
			}
		}
		return null;
	}

	protected static ParaObject getArgOfParaObject(Object[] args) {
		if (args != null) {
			for (Object arg : args) {
				if (arg != null && arg instanceof ParaObject) {
					return (ParaObject) arg;
				}
			}
		}
		return null;
	}

	protected static String getFirstArgOfString(Object[] args) {
		if (args != null) {
			for (Object arg : args) {
				if (arg != null && arg instanceof String) {
					return (String) arg;
				}
			}
		}
		return null;
	}

	protected static List<ParaObject> removeNotStoredNotIndexed(List<ParaObject> addUs, List<ParaObject> indexUs) {
		if (addUs != null) {
			List<ParaObject> removed = new LinkedList<>();
			for (Iterator<ParaObject> it = addUs.iterator(); it.hasNext();) {
				ParaObject obj = it.next();
				if (obj != null) {
					checkAndFixType(obj);
					if (obj.getIndexed() && indexUs != null) {
						indexUs.add(obj);
					}
					if (!obj.getStored()) {
						removed.add(obj);
						it.remove();
					}
				}
			}
			return removed;
		}
		return Collections.emptyList();
	}

	/**
	 * Object types should not start with '_' because it is in conflict with the API.
	 * Some API resources have a path which also starts with '_' like {@code  /v1/_me}.
	 * @param obj an object
	 */
	protected static void checkAndFixType(ParaObject obj) {
		if (obj != null) {
			if (StringUtils.startsWith(obj.getType(), SPECIAL_PREFIX)) {
				obj.setType(obj.getType().replaceAll("^[" + SPECIAL_PREFIX + "]*", ""));
			}
			if (StringUtils.contains(obj.getType(), "#")) {
				// ElasticSearch doesn't allow # in type mappings
				obj.setType(obj.getType().replaceAll("#", ""));
			}
			if (StringUtils.contains(obj.getType(), "/")) {
				// type must not contain "/"
				obj.setType(obj.getType().replaceAll("/", ""));
			}
			if (obj.getType().isEmpty()) {
				obj.setType(Utils.type(Sysprop.class));
			}
		}
	}

}
