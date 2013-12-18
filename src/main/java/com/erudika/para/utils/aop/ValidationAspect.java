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
import static com.erudika.para.annotations.Indexed.Action.ADD;
import com.erudika.para.core.ParaObject;
import com.erudika.para.persistence.DAO;
import com.erudika.para.utils.Utils;
import static com.erudika.para.utils.aop.IndexingAspect.getArgOfParaObject;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class ValidationAspect implements MethodInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(ValidationAspect.class);
	
	public Object invoke(MethodInvocation mi) throws Throwable {
		Object result = null;
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
						String[] err = Utils.validateRequest(addMe);
						if (err.length == 0){
							result = mi.proceed();
						}
						break;
					default: break;
				}
			}
		}
		
		return result;
	}
	
}
