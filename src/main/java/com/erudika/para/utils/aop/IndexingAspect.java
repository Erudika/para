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
import com.erudika.para.api.ParaObject;
import com.erudika.para.api.Search;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
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
		Method m = mi.getMethod();
		Indexed ano = m.getAnnotation(Indexed.class);
		
		Object[] params = mi.getArguments();
		ParaObject indexable = null;
		List<ParaObject> indexables = null;
		
		if(params.length == 0){
			throw new IllegalAccessException("Method " + m.getName() + " has no parameters.");
		}else{
			for (Object param : params) {
//				TypeVariable<Class<?>>[] ts = (TypeVariable<Class<?>>[]) param.getClass().getTypeParameters();
//				&& ParaObject.class.isAssignableFrom(((List) param).get(0).getClass())
				if(param != null){
					if(param instanceof ParaObject){
						indexable = (ParaObject) param; 
						break;
					}else if(param instanceof List){
						indexables = (List<ParaObject>) param;
						break;
					}
				}
			}
		}
		
		switch(ano.action()){
			case ADD: 
				if(ano.batch()){
					search.indexAll(indexables);
				}else{
					search.index(indexable, indexable.getClassname());
				}
				break;
			case REMOVE: 
				if(ano.batch()){
					search.unindexAll(indexables);
				}else{
					search.unindex(indexable, indexable.getClassname());
				}
				break;
			default: break;
		}
		
		return null;
	}
	
}
