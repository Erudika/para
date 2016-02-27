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

import com.erudika.para.annotations.Cached;
import com.erudika.para.annotations.Indexed;
import com.erudika.para.cache.Cache;
import com.erudika.para.core.ParaObject;
import com.erudika.para.persistence.DAO;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.validation.ValidationUtils;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
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
@SuppressWarnings("unchecked")
public class IndexAndCacheAspect implements MethodInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(IndexAndCacheAspect.class);

	private Search search;
	private Cache cache;

	public Search getSearch() {
		return search;
	}

	@Inject
	public void setSearch(Search search) {
		this.search = search;
	}

	public Cache getCache() {
		return cache;
	}

	@Inject
	public void setCache(Cache cache) {
		this.cache = cache;
	}

	/**
	 * Executes code when a method is invoked. A big switch statement.
	 * @param mi method invocation
	 * @return the returned value of the method invoked or something else (decided here)
	 * @throws Throwable error
	 */
	public Object invoke(MethodInvocation mi) throws Throwable {
		if (!Modifier.isPublic(mi.getMethod().getModifiers())) {
			return mi.proceed();
		}
		Object result = null;
		Method m = mi.getMethod();
		Method superMethod = null;
		Indexed indexedAnno = null;
		Cached cachedAnno = null;
		String cn = getClass().getSimpleName();
		try {
			superMethod = DAO.class.getMethod(m.getName(), m.getParameterTypes());
			indexedAnno = Config.isSearchEnabled() ? superMethod.getAnnotation(Indexed.class) : null;
			cachedAnno = Config.isCacheEnabled() ? superMethod.getAnnotation(Cached.class) : null;
		} catch (Exception e) {
			logger.error(null, e);
		}

		Object[] args = mi.getArguments();
		String appid = AOPUtils.getFirstArgOfString(args);

		if (indexedAnno != null) {
			switch (indexedAnno.action()) {
				case ADD:
					ParaObject addMe = AOPUtils.getArgOfParaObject(args);
					String[] errors = ValidationUtils.validateObject(addMe);
					if (errors.length == 0) {
						if (addMe.getStored()) {
							result = mi.proceed();
						}
						if (addMe.getIndexed()) {
							search.index(appid, addMe);
							logger.debug("{}: Indexed {}->{}", cn, appid, addMe.getId());
						}
					} else {
						logger.warn("{}: Invalid object {}->{} errors: [{}]. Changes weren't persisted.",
								cn, appid, addMe, String.join("; ", errors));
					}
					break;
				case REMOVE:
					result = mi.proceed(); // delete from DB even if "isStored = false"
					ParaObject removeMe = AOPUtils.getArgOfParaObject(args);
					search.unindex(appid, removeMe); // remove from index even if "isIndexed = false"
					logger.debug("{}: Unindexed {}->{}", cn, appid, (removeMe == null) ? null : removeMe.getId());
					break;
				case ADD_ALL:
					List<ParaObject> addUs = AOPUtils.getArgOfListOfType(args, ParaObject.class);
					List<ParaObject> indexUs = new LinkedList<ParaObject>();
					List<ParaObject> removedObjects = removeNotStoredNotIndexed(addUs, indexUs);
					result = mi.proceed();
					search.indexAll(appid, indexUs);
					if (cachedAnno != null) {
						// restore removed objects - needed if we have to cache them later
						// do not remove this line - breaks tests
						addUs.addAll(removedObjects); // don't delete!
					}
					logger.debug("{}: Indexed all {}->{}", cn, appid, (indexUs == null) ? null : indexUs.size());
					break;
				case REMOVE_ALL:
					List<ParaObject> removeUs = AOPUtils.getArgOfListOfType(args, ParaObject.class);
					result = mi.proceed(); // delete from DB even if "isStored = false"
					search.unindexAll(appid, removeUs); // remove from index even if "isIndexed = false"
					logger.debug("{}: Unindexed all {}->{}", cn, appid, (removeUs == null) ? null : removeUs.size());
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
						if (result != null && ((ParaObject) result).getCached()) {
							cache.put(appid, getMeId, result);
							logger.debug("{}: Cache miss: {}->{}", cn, appid, getMeId);
						}
					}
					break;
				case PUT:
					ParaObject putMe = AOPUtils.getArgOfParaObject(args);
					if (putMe != null && putMe.getCached()) {
						cache.put(appid, putMe.getId(), putMe);
						logger.debug("{}: Cache put: {}->{}", cn, appid, putMe.getId());
					}
					break;
				case DELETE:
					ParaObject deleteMe = AOPUtils.getArgOfParaObject(args);
					if (deleteMe != null) { // clear from cache even if "isCached = false"
						cache.remove(appid, deleteMe.getId());
						logger.debug("{}: Cache delete: {}->{}", cn, appid, deleteMe.getId());
					}
					break;
				case GET_ALL:
					List<String> getUs = AOPUtils.getArgOfListOfType(args, String.class);
					if (getUs != null) {
						Map<String, ParaObject> cached = cache.getAll(appid, getUs);
						logger.debug("{}: Cache getAll(): {}->{}", cn, appid, getUs);
						// hit the database if even a single object is missing from cache, then cache it
						boolean missingFromCache = cached.size() < getUs.size();
						if (result == null && missingFromCache) {
							logger.debug("{}: Cache getAll() will read from DB: {}", cn, appid);
							result = mi.proceed();
							if (result != null) {
								for (String id : getUs) {
									logger.debug("{}: Cache getAll() got from DB: {}", cn, id);
									if (!cached.containsKey(id)) {
										ParaObject obj = ((Map<String, ParaObject>) result).get(id);
										if (obj != null && obj.getCached()) {
											cache.put(appid, obj.getId(), obj);
											logger.debug("{}: Cache miss on readAll: {}->{}", cn, appid, id);
										}
									}
								}
							}
						}
						if (result == null) {
							result = cached;
						}
					}
					break;
				case PUT_ALL:
					List<ParaObject> putUs = AOPUtils.getArgOfListOfType(args, ParaObject.class);
					if (putUs != null && !putUs.isEmpty()) {
						Map<String, ParaObject> map1 = new LinkedHashMap<String, ParaObject>(putUs.size());
						for (ParaObject obj : putUs) {
							if (obj != null && obj.getCached()) {
								map1.put(obj.getId(), obj);
							}
						}
						if (!map1.isEmpty()) {
							cache.putAll(appid, map1);
						}
						logger.debug("{}: Cache put page: {}->{}", cn, appid, map1.keySet());
					}
					break;
				case DELETE_ALL:
					List<ParaObject> deleteUs = AOPUtils.getArgOfListOfType(args, ParaObject.class);
					if (deleteUs != null && !deleteUs.isEmpty()) {
						List<String> list = new ArrayList<String>(deleteUs.size());
						for (ParaObject paraObject : deleteUs) {
							list.add(paraObject.getId());
						}
						// clear from cache even if "isCached = false"
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

	private List<ParaObject> removeNotStoredNotIndexed(List<ParaObject> addUs, List<ParaObject> indexUs) {
		if (addUs != null) {
			List<ParaObject> removed = new LinkedList<ParaObject>();
			for (Iterator<ParaObject> it = addUs.iterator(); it.hasNext();) {
				ParaObject obj = it.next();
				if (obj != null) {
					if (obj.getIndexed()) {
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
}
