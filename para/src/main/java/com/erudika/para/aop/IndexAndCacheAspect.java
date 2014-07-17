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
package com.erudika.para.aop;

import com.erudika.para.annotations.Cached;
import com.erudika.para.annotations.Indexed;
import com.erudika.para.cache.Cache;
import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.User;
import com.erudika.para.persistence.DAO;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the core method interceptor which enables caching and indexing.
 * It listens for calls to annotated {@link com.erudika.para.persistence.DAO} methods
 * and adds searching and caching functionality to them. This technique allows us to control
 * the caching and searching centrally for all implementations
 * of the {@link com.erudika.para.persistence.DAO} interface.
 * @author Alex Bogdanovski [alex@erudika.com]
 * @see com.erudika.para.persistence.DAO
 */
public class IndexAndCacheAspect implements MethodInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(IndexAndCacheAspect.class);

	@Inject private Search search;
	@Inject private Cache cache;

	/**
	 * Executes code when a method is invoked. A big switch statement.
	 * @param mi method invocation
	 * @return the returned value of the method invoked or something else (decided here)
	 * @throws Throwable error
	 */
	@SuppressWarnings("unchecked")
	public Object invoke(MethodInvocation mi) throws Throwable {
		Object result = null;
		Method m = mi.getMethod();
		Method superMethod = null;
		Indexed indexedAnno = null;
		Cached cachedAnno = null;
		String cn = getClass().getSimpleName();

		try {
			superMethod = DAO.class.getMethod(m.getName(), m.getParameterTypes());
			indexedAnno = Config.SEARCH_ENABLED ? superMethod.getAnnotation(Indexed.class) : null;
			cachedAnno = Config.CACHE_ENABLED ? superMethod.getAnnotation(Cached.class) : null;
		} catch (Exception e) {
			logger.error(null, e);
		}

		Object[] args = mi.getArguments();
		String appid = AOPUtils.getFirstArgOfString(args);

		if (indexedAnno != null) {
			switch (indexedAnno.action()) {
				case ADD:
					ParaObject addMe = AOPUtils.getArgOfParaObject(args);
					if (Utils.isValidObject(addMe)) {
						result = mi.proceed();
						search.index(appid, addMe);
						logger.debug("{}: Indexed {}->{}", cn, appid, addMe.getId());
					} else {
						logger.debug("{}: Invalid object {}->{}", cn, appid, addMe);
					}
					break;
				case REMOVE:
					result = mi.proceed();
					ParaObject removeMe = AOPUtils.getArgOfParaObject(args);
					search.unindex(appid, removeMe);
					logger.debug("{}: Unindexed {}->{}", cn, appid, (removeMe == null) ? null : removeMe.getId());
					break;
				case ADD_ALL:
					List<ParaObject> addUs = AOPUtils.getArgOfListOfType(args, ParaObject.class);
					removeSpecialClasses(addUs);
					result = mi.proceed();
					search.indexAll(appid, addUs);
					logger.debug("{}: Indexed all {}->#{}", cn, appid, (addUs == null) ? null : addUs.size());
					break;
				case REMOVE_ALL:
					List<ParaObject> removeUs = AOPUtils.getArgOfListOfType(args, ParaObject.class);
					removeSpecialClasses(removeUs);
					result = mi.proceed();
					search.unindexAll(appid, removeUs);
					logger.debug("{}: Unindexed all {}->#{}", cn, appid, (removeUs == null) ? null : removeUs.size());
					break;
				default:
					break;
			}
		}
		if (cachedAnno != null) {
			switch (cachedAnno.action()) {
				case GET:
					String getMeId = (String) args[1];
					if (cache.contains(appid, getMeId)) {
						result = cache.get(appid, getMeId);
						logger.debug("{}: Cache hit: {}->{}", cn, appid, getMeId);
					} else if (getMeId != null) {
						if (result == null) {
							result = mi.proceed();
						}
						if (result != null) {
							cache.put(appid, getMeId, result);
							logger.debug("{}: Cache miss: {}->{}", cn, appid, getMeId);
						}
					}
					break;
				case PUT:
					ParaObject putMe = AOPUtils.getArgOfParaObject(args);
					if (putMe != null) {
						cache.put(appid, putMe.getId(), putMe);
						logger.debug("{}: Cache put: {}->{}", cn, appid, putMe.getId());
					}
					break;
				case DELETE:
					ParaObject deleteMe = AOPUtils.getArgOfParaObject(args);
					if (deleteMe != null) {
						cache.remove(appid, deleteMe.getId());
						logger.debug("{}: Cache delete: {}->{}", cn, appid, deleteMe.getId());
					}
					break;
				case GET_ALL:
					List<String> getUs = AOPUtils.getArgOfListOfType(args, String.class);
					if (getUs != null) {
						Map<String, ParaObject> cached = cache.getAll(appid, getUs);
						logger.debug("{}: Cache get page: {}->{}", cn, appid, getUs);
						for (String id : getUs) {
							if (!cached.containsKey(id)) {
								if (result == null) {
									result = mi.proceed();
								}
								cache.putAll(appid, (Map<String, ParaObject>) result);
								logger.debug("{}: Cache get page reload: {}->{}", cn, appid, id);
								break;
							}
						}
						if (result == null) {
							result = cached;
						}
					}
					break;
				case PUT_ALL:
					List<ParaObject> putUs = AOPUtils.getArgOfListOfType(args, ParaObject.class);
					removeSpecialClasses(putUs);
					if (putUs != null && !putUs.isEmpty()) {
						Map<String, ParaObject> map1 = new LinkedHashMap<String, ParaObject>();
						for (ParaObject paraObject : putUs) {
							map1.put(paraObject.getId(), paraObject);
						}
						cache.putAll(appid, map1);
						logger.debug("{}: Cache put page: {}->{}", cn, appid, map1.keySet());
					}
					break;
				case DELETE_ALL:
					List<ParaObject> deleteUs = AOPUtils.getArgOfListOfType(args, ParaObject.class);
					removeSpecialClasses(deleteUs);
					if (deleteUs != null && !deleteUs.isEmpty()) {
						List<String> list = new ArrayList<String>();
						for (ParaObject paraObject : deleteUs) {
							list.add(paraObject.getId());
						}
						cache.removeAll(appid, list);
						logger.debug("{}: Cache delete page: {}->{}", cn, appid, list);
					}
					break;
				default:
					break;
			}
		}

		// both searching and caching are disabled - pass it through
		if (indexedAnno == null && cachedAnno == null) {
			result = mi.proceed();
		}

		return result;
	}

	private void removeSpecialClasses(List<ParaObject> objects) {
		if (objects != null) {
			ArrayList<ParaObject> list = new ArrayList<ParaObject>(objects);
			for (ParaObject paraObject : list) {
				if (paraObject instanceof User || paraObject instanceof App) {
					objects.remove(paraObject);
				}
			}
		}
	}
}
