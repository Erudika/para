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
package com.erudika.para.aop;

import com.erudika.para.core.ParaObject;
import java.util.List;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class AOPUtils {

	private AOPUtils() { }

	@SuppressWarnings("unchecked")
	static <T> List<T> getArgOfListOfType(Object[] args, Class<T> type) {
		if (args != null) {
			for (Object arg : args) {
				if (arg != null) {
					if (arg instanceof List) {
						List<T> list = (List) arg;
						if (!list.isEmpty() && type.isAssignableFrom((list.get(0).getClass()))) {
							return list;
						}
					}
				}
			}
		}
		return null;
	}

	static ParaObject getArgOfParaObject(Object[] args) {
		if (args != null) {
			for (Object arg : args) {
				if (arg != null) {
					if (ParaObject.class.isAssignableFrom(arg.getClass())) {
						return (ParaObject) arg;
					}
				}
			}
		}
		return null;
	}

	static String getFirstArgOfString(Object[] args) {
		if (args != null) {
			for (Object arg : args) {
				if (arg != null) {
					if (arg instanceof String) {
						return (String) arg;
					}
				}
			}
		}
		return null;
	}

}
