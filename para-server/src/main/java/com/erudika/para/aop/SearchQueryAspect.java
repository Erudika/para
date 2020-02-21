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

import com.erudika.para.IOListener;
import com.erudika.para.Para;
import com.erudika.para.annotations.Measured;
import com.erudika.para.metrics.Metrics;
import static com.erudika.para.metrics.Metrics.time;
import com.erudika.para.search.Search;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This method interceptor watches search queries fora all {@link Search} implementations and gathers metrics.
 * It listens for calls to {@link Search} methods, annotated with {@link Measured}.
 * @author Alex Bogdanovski [alex@erudika.com]
 * @see Search
 */
@SuppressWarnings("unchecked")
public class SearchQueryAspect implements MethodInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(SearchQueryAspect.class);

	/**
	 * Executes code when a method is invoked.
	 * @param mi method invocation
	 * @return the returned value of the method invoked or something else (decided here)
	 * @throws Throwable error
	 */
	public Object invoke(MethodInvocation mi) throws Throwable {
		if (!Modifier.isPublic(mi.getMethod().getModifiers())) {
			return mi.proceed();
		}

		Method searchMethod = mi.getMethod();
		Object[] args = mi.getArguments();
		String appid = AOPUtils.getFirstArgOfString(args);

		Method superMethod = null;
		Measured measuredAnno = null;

		try {
			superMethod = Search.class.getMethod(searchMethod.getName(), searchMethod.getParameterTypes());
			measuredAnno = superMethod.getAnnotation(Measured.class);
		} catch (Exception e) {
			logger.error("Error in search AOP layer!", e);
		}

		Set<IOListener> ioListeners = Para.getSearchQueryListeners();
		for (IOListener ioListener : ioListeners) {
			ioListener.onPreInvoke(superMethod, args);
			logger.debug("Executed {}.onPreInvoke().", ioListener.getClass().getName());
		}

		Object result = null;
		if (measuredAnno != null) {
			result = invokeTimedSearch(appid, searchMethod, mi);
		} else {
			result = mi.proceed();
		}

		for (IOListener ioListener : ioListeners) {
			ioListener.onPostInvoke(superMethod, args, result);
			logger.debug("Executed {}.onPostInvoke().", ioListener.getClass().getName());
		}

		return result;
	}

	private Object invokeTimedSearch(String appid, Method searchMethod, MethodInvocation mi) throws Throwable {
		try (Metrics.Context context = time(appid, searchMethod.getDeclaringClass(), searchMethod.getName())) {
			return mi.proceed();
		}
	}
}
