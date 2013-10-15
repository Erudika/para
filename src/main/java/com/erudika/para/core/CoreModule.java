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
package com.erudika.para.core;

import com.erudika.para.cache.CacheModule;
import com.erudika.para.email.EmailModule;
import com.erudika.para.i18n.I18nModule;
import com.erudika.para.persistence.PersistenceModule;
import com.erudika.para.queue.QueueModule;
import com.erudika.para.search.SearchModule;
import com.erudika.para.security.SecurityModule;
import com.erudika.para.storage.StorageModule;
import com.erudika.para.utils.aop.AOPModule;
import com.google.inject.AbstractModule;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class CoreModule extends AbstractModule {

	protected void configure() {
		bind(PObject.class);
//		bind(Translation.class);
		
		install(new CacheModule());
		install(new EmailModule());
		install(new I18nModule());
		install(new PersistenceModule());
		install(new QueueModule());
		install(new SearchModule());
		install(new SecurityModule());
		install(new StorageModule());
		install(new AOPModule());
	}
	
}
