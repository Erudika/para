/*
 * Copyright 2013-2020 Erudika. https://erudika.com
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

import com.erudika.para.persistence.DAO;
import com.erudika.para.search.Search;
import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;

/**
 * The default Aspect Oriented Programming module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AOPModule extends AbstractModule {

	protected void configure() {
		// enable automatic indexing and caching each time an object is created/updated
		IndexAndCacheAspect coreAspect = new IndexAndCacheAspect();
		requestInjection(coreAspect);
		bindInterceptor(Matchers.subclassesOf(DAO.class), Matchers.any(), coreAspect);
		// enable the search query interceptor for metrics collection
		SearchQueryAspect searchAspect = new SearchQueryAspect();
		//requestInjection(searchAspect);
		bindInterceptor(Matchers.subclassesOf(Search.class), Matchers.any(), searchAspect);
	}

}
