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

import com.erudika.para.annotations.Indexed;
import com.erudika.para.persistence.DAO;
import com.erudika.para.core.ParaObject;
import com.erudika.para.search.Search;
import java.lang.reflect.Method;
import java.util.List;
import javax.inject.Inject;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.erudika.para.utils.aop.AOPUtils.*;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class IndexingAspect implements MethodInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(IndexingAspect.class);
	
	@Inject private Search search;
	
	public Object invoke(MethodInvocation mi) throws Throwable {
		Object result = mi.proceed();
		Method m = mi.getMethod();
		Method superMethod = null;
		Indexed indexedAnno = null;
		logger.debug("{}: intercepting {}()", getClass().getSimpleName(), m.getName());
		
		try {
			superMethod = DAO.class.getMethod(m.getName(), m.getParameterTypes());
			indexedAnno = superMethod.getAnnotation(Indexed.class);
		} catch (Exception e) {}
		
		if(indexedAnno != null){
			Object[] args = mi.getArguments();
			String appName = getFirstArgOfString(args);
			switch(indexedAnno.action()){
				case ADD:
					ParaObject addMe = getArgOfParaObject(args);
					search.index(appName, addMe);
					logger.debug("Indexed {} {}", appName, addMe.getId());
					break;
				case REMOVE: 
					ParaObject removeMe = getArgOfParaObject(args);
					search.unindex(appName, removeMe);
					logger.debug("Unindexed {} {}", appName, removeMe.getId());
					break;
				case ADD_ALL: 
					List<ParaObject> addUs = getArgOfListOfType(args, ParaObject.class);
					search.indexAll(appName, addUs);
					logger.debug("Indexed all {} {}", appName, addUs.size());
					break;
				case REMOVE_ALL: 
					List<ParaObject> removeUs = getArgOfListOfType(args, ParaObject.class);
					search.unindexAll(appName, removeUs);
					logger.debug("Unindexed all {} {}", appName, removeUs.size());
					break;
				default: break;
			}
		}else{
//			result = mi.proceed();
		}
		
		return result;
	}
	
}
