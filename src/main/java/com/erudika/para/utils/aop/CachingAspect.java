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
import com.erudika.para.cache.Cache;
import com.erudika.para.persistence.DAO;
import com.erudika.para.core.ParaObject;
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
public class CachingAspect implements MethodInterceptor {
	
	private static final Logger logger = LoggerFactory.getLogger(CachingAspect.class);
	
	@Inject private Cache cache;
	
	public Object invoke(MethodInvocation mi) throws Throwable {
		Object result = null;
		Method m = mi.getMethod();
		Method superMethod = null;
		Cached cachedAnno = null;
		logger.debug("{}: intercepting {}()", getClass().getSimpleName(), m.getName());
		
		try {
			superMethod = DAO.class.getMethod(m.getName(), m.getParameterTypes());
			cachedAnno = superMethod.getAnnotation(Cached.class);
		} catch (Exception e) {}
		
		if(cachedAnno != null){
			Object[] args = mi.getArguments();
			String appName = getFirstArgOfString(args);
			switch(cachedAnno.action()){
				case GET:
					String getMe = (String) args[1];
					if(cache.contains(appName, getMe)){
						result = cache.get(appName, getMe);
						logger.debug("Cache hit: {} {}", appName, getMe);							
					}else{
						result = mi.proceed();
						cache.put(appName, getMe, result);
						logger.debug("Cache miss: {} {}", appName, getMe);
					}
					break;
				case PUT:
					ParaObject putMe = getArgOfParaObject(args);
					result = mi.proceed();
					cache.put(appName, putMe.getId(), putMe);
					logger.debug("Cache put: {} {}", appName, putMe.getId());
					break;
				case DELETE:
					ParaObject deleteMe = getArgOfParaObject(args);
					result = mi.proceed();
					cache.remove(appName, deleteMe.getId());
					logger.debug("Cache delete: {} {}", appName, deleteMe.getId());
					break;
				case GET_ALL:
					List<String> getUs = getArgOfListOfType(args, String.class);
					Map<String, ParaObject> cached = cache.getAll(appName, getUs);
					logger.debug("Cache get page: {} {}", appName, getUs);
					for (String id : getUs) {
						if(!cached.containsKey(id)){
							result = mi.proceed();
							cache.putAll(appName, (Map<String, ParaObject>) result);
							logger.debug("Cache get page reload: {} {}", appName, id);
							break;
						}
					}
					result = cached;
					break;
				case PUT_ALL:
					List<ParaObject> putUs = getArgOfListOfType(args, ParaObject.class);
					result = mi.proceed();
					Map<String, ParaObject> map1 = new LinkedHashMap<String, ParaObject>();
					for (ParaObject paraObject : putUs) {
						map1.put(paraObject.getId(), paraObject);
					}
					cache.putAll(appName, map1);						
					logger.debug("Cache put page: {} {}", appName, map1.keySet());
					break;
				case DELETE_ALL:
					List<ParaObject> deleteUs = getArgOfListOfType(args, ParaObject.class);
					result = mi.proceed();
					List<String> list = new ArrayList<String>();
					for (ParaObject paraObject : deleteUs) {
						list.add(paraObject.getId());
					}
					cache.removeAll(appName, list);
					logger.debug("Cache delete page: {} {}", appName, list);	
					break;
				default: break;
			}
		}else{
//			result = mi.proceed();
		}
		
		return result;
	}
}
