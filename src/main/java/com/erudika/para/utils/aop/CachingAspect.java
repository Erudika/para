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
import static com.erudika.para.utils.aop.IndexingAspect.getArgOfListOfType;
import static com.erudika.para.utils.aop.IndexingAspect.getFirstArgOfString;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class CachingAspect implements MethodInterceptor {
	
	@Inject private Cache cache;
	
	public Object invoke(MethodInvocation mi) throws Throwable {
		Object result = null;
		Method m = mi.getMethod();
		
		Method superMethod = null;
		try {
			superMethod = DAO.class.getMethod(m.getName(), m.getParameterTypes());
		} catch (Exception e) {}
		
		if(superMethod != null){
			Cached ano = superMethod.getAnnotation(Cached.class);
			if(ano != null){
				Object[] args = mi.getArguments();
				String appName = getFirstArgOfString(args);
				switch(ano.action()){
					case GET:
						String getMe = (String) args[0];
						if(cache.contains(appName, getMe)){
							result = cache.get(appName, getMe);
						}else{
							result = mi.proceed();
							cache.put(appName, getMe, result);
						}
						break;
					case PUT:
						ParaObject putMe = IndexingAspect.getArgOfParaObject(args);
						result = mi.proceed();
						cache.put(appName, putMe.getId(), putMe);
						break;
					case DELETE:
						ParaObject deleteMe = IndexingAspect.getArgOfParaObject(args);
						result = mi.proceed();
						cache.remove(appName, deleteMe.getId());
						break;
					case GET_ALL:
						List<String> getUs = getArgOfListOfType(args, String.class);
						result = cache.getAll(appName, getUs);
						break;
					case PUT_ALL:
						List<ParaObject> putUs = IndexingAspect.getArgOfListOfType(args, ParaObject.class);
						result = mi.proceed();
						Map<String, ParaObject> map1 = new TreeMap<String, ParaObject>();
						for (ParaObject paraObject : putUs) {
							map1.put(paraObject.getId(), paraObject);
						}
						cache.putAll(appName, map1);						
						break;
					case DELETE_ALL:
						List<ParaObject> deleteUs = IndexingAspect.getArgOfListOfType(args, ParaObject.class);
						result = mi.proceed();
						List<String> list = new ArrayList<String>();
						for (ParaObject paraObject : deleteUs) {
							list.add(paraObject.getId());
						}
						cache.removeAll(appName, list);
						break;
					default: break;
				}
			}
		}
		
		return result;
	}
}
