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
		Method superMethod = DAO.class.getMethod(m.getName(), m.getParameterTypes());
		Indexed ano = superMethod.getAnnotation(Indexed.class);
		
		if(ano != null){
			Object[] params = mi.getArguments();
			switch(ano.action()){
				case ADD: 
					if(ano.batch()){
						List<ParaObject> indexables = getIndexableParameter(params);
						search.indexAll(indexables);
					}else{
						ParaObject indexable = getIndexableParameter(params);
						search.index(indexable, indexable.getClassname());
					}
					break;
				case REMOVE: 
					if(ano.batch()){
						List<ParaObject> indexables = getIndexableParameter(params);
						search.unindexAll(indexables);
					}else{
						ParaObject indexable = getIndexableParameter(params);
						search.unindex(indexable, indexable.getClassname());
					}
					break;
				default: break;
			}
		}
		
		return result;
	}
	
	<P> P getIndexableParameter(Object[] params){
		if(params.length == 0){
			return null;
		}else{
			P indexable = null;
			for (Object param : params) {
				if(param != null){
					if(param instanceof ParaObject){
						indexable = (P) param; 
						break;
					}else if(param instanceof List){
						List<?> list = (List) param;
						// the only valid type is List<? extends ParaObject>
						if(!list.isEmpty() && ParaObject.class.isAssignableFrom((list.get(0).getClass()))){
							indexable = (P) list;
						}
						break;
					}
				}
			}
			return indexable;
		}
	}
	
}
