/*
 * Copyright 2013 Alex Bogdanovski <alex@erudika.com>.
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
 * You can reach the author at: https://github.com/albogdano
 */
package com.erudika.para.utils.aop;

import com.erudika.para.annotations.Cached;
import static com.erudika.para.annotations.Cached.Action.*;
import static com.erudika.para.annotations.Indexed.Action.*;
import com.erudika.para.annotations.Indexed;
import com.erudika.para.cache.Cache;
import com.erudika.para.core.ParaObject;
import com.erudika.para.persistence.DAO;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import static com.erudika.para.utils.aop.AOPUtils.*;
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
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class ValidationAspect implements MethodInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(ValidationAspect.class);
	
	@Inject private Search search;
	@Inject private Cache cache;
	
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
		} catch (Exception e) {}
		
//			result = indexingAction(mi, indexedAnno);
		Object[] args = mi.getArguments();
		String appName = getFirstArgOfString(args);
		
		if(indexedAnno != null){
			switch (indexedAnno.action()) {
				case ADD:
					ParaObject addMe = getArgOfParaObject(args);
					if (Utils.isValidObject(addMe)) {
						result = mi.proceed();
						search.index(appName, addMe);
						logger.debug("{}: Indexed {}->{}", cn, appName, addMe.getId());
					} else {
						logger.debug("{}: Invalid object {}->{}", cn, appName, addMe);
					}
					break;
				case REMOVE:
					result = mi.proceed();
					ParaObject removeMe = getArgOfParaObject(args);
					search.unindex(appName, removeMe);
					logger.debug("{}: Unindexed {}->{}", cn, appName, removeMe.getId());
					break;
				case ADD_ALL:
					result = mi.proceed();
					List<ParaObject> addUs = getArgOfListOfType(args, ParaObject.class);
					search.indexAll(appName, addUs);
					logger.debug("{}: Indexed all {}->#{}", cn, appName, addUs.size());
					break;
				case REMOVE_ALL:
					result = mi.proceed();
					List<ParaObject> removeUs = getArgOfListOfType(args, ParaObject.class);
					search.unindexAll(appName, removeUs);
					logger.debug("{}: Unindexed all {}->#{}", cn, appName, removeUs.size());
					break;
				default:
					break;
			}
		}
		if(cachedAnno != null){
			switch (cachedAnno.action()) {
				case GET:
					String getMe = (String) args[1];
					if (cache.contains(appName, getMe)) {
						result = cache.get(appName, getMe);
						logger.debug("{}: Cache hit: {}->{}", cn, appName, getMe);
					} else {
						if(result == null) result = mi.proceed();
						if(result != null){
							cache.put(appName, getMe, result);
							logger.debug("{}: Cache miss: {}->{}", cn, appName, getMe);
						}
					}
					break;
				case PUT:
					ParaObject putMe = getArgOfParaObject(args);
					if(result != null){
						cache.put(appName, putMe.getId(), putMe);
						logger.debug("{}: Cache put: {}->{}", cn, appName, putMe.getId());
					}
					break;
				case DELETE:
					ParaObject deleteMe = getArgOfParaObject(args);
					cache.remove(appName, deleteMe.getId());
					logger.debug("{}: Cache delete: {}->{}", cn, appName, deleteMe.getId());
					break;
				case GET_ALL:
					List<String> getUs = getArgOfListOfType(args, String.class);
					Map<String, ParaObject> cached = cache.getAll(appName, getUs);
					logger.debug("{}: Cache get page: {}->{}", cn, appName, getUs);
					for (String id : getUs) {
						if (!cached.containsKey(id)) {
							if(result == null) result = mi.proceed();
							cache.putAll(appName, (Map<String, ParaObject>) result);
							logger.debug("{}: Cache get page reload: {}->{}", cn, appName, id);
							break;
						}
					}
					if(result == null) result = cached;
					break;
				case PUT_ALL:
					List<ParaObject> putUs = getArgOfListOfType(args, ParaObject.class);
					if(result != null){
						Map<String, ParaObject> map1 = new LinkedHashMap<String, ParaObject>();
						for (ParaObject paraObject : putUs) {
							map1.put(paraObject.getId(), paraObject);
						}
						cache.putAll(appName, map1);
						logger.debug("{}: Cache put page: {}->{}", cn, appName, map1.keySet());
					}
					break;
				case DELETE_ALL:
					List<ParaObject> deleteUs = getArgOfListOfType(args, ParaObject.class);
					List<String> list = new ArrayList<String>();
					for (ParaObject paraObject : deleteUs) {
						list.add(paraObject.getId());
					}
					cache.removeAll(appName, list);
					logger.debug("{}: Cache delete page: {}->{}", cn, appName, list);
					break;
				default:
					break;
			}
		}
		
		if(indexedAnno == null && cachedAnno == null){
			result = mi.proceed();
		}
		
		return result;
	}
	
}
