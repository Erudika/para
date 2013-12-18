/*
 * Copyright 2013 Alex Bogdanovski <albogdano@me.com>.
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

import com.erudika.para.annotations.Indexed;
import com.erudika.para.persistence.DAO;
import com.erudika.para.core.ParaObject;
import com.erudika.para.search.Search;
import java.lang.reflect.Method;
import java.util.List;
import javax.inject.Inject;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class IndexingAspect implements MethodInterceptor {

	@Inject private Search search;
	
	public Object invoke(MethodInvocation mi) throws Throwable {
		Object result = mi.proceed();
		Method m = mi.getMethod();
		
		Method superMethod = null;
		try {
			superMethod = DAO.class.getMethod(m.getName(), m.getParameterTypes());
		} catch (Exception e) {}
		
		if(superMethod != null){
			Indexed ano = superMethod.getAnnotation(Indexed.class);
			if(ano != null){
				Object[] args = mi.getArguments();
				switch(ano.action()){
					case ADD: 
						ParaObject addMe = getArgOfParaObject(args);
						search.index(addMe, addMe.getClassname());
						break;
					case REMOVE: 
						ParaObject removeMe = getArgOfParaObject(args);
						search.unindex(removeMe, removeMe.getClassname());
						break;
					case ADD_ALL: 
						List<ParaObject> addUs = getArgOfListOfType(args, ParaObject.class);
						search.indexAll(addUs);
						break;
					case REMOVE_ALL: 
						List<ParaObject> removeUs = getArgOfListOfType(args, ParaObject.class);
						search.unindexAll(removeUs);
						break;
					default: break;
				}
			}
		}
		
		return result;
	}
	
	static <T> List<T> getArgOfListOfType(Object[] args, Class<T> type){
		if(args != null){
			for (Object arg : args) {
				if(arg != null){
					if(arg instanceof List){
						List<T> list = (List) arg;
						if(!list.isEmpty() && type.isAssignableFrom((list.get(0).getClass()))){
							return (List<T>) list;
						}
					}
				}
			}
		}
		return null;
	}
	
	static ParaObject getArgOfParaObject(Object[] args){
		if(args != null){
			for (Object arg : args) {
				if(arg != null){
					if(ParaObject.class.isAssignableFrom(arg.getClass())){
						return (ParaObject) arg;
					}
				}
			}
		}
		return null;
	}
	
}
