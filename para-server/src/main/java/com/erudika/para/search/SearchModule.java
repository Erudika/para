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
package com.erudika.para.search;

import com.erudika.para.Para;
import com.erudika.para.utils.Config;
import com.google.inject.AbstractModule;
import java.util.ServiceLoader;

/**
 * The default search module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SearchModule extends AbstractModule {

	protected void configure() {
		String selectedSearch = Config.getConfigParam("search", "");
		Search searchPlugin = loadExternalSearch(selectedSearch);
		if (searchPlugin != null) {
			bind(Search.class).to(searchPlugin.getClass()).asEagerSingleton();
		} else {
			// default fallback - not implemented!
			bindToDefault();
		}
	}

	void bindToDefault() {
		bind(Search.class).to(MockSearch.class).asEagerSingleton();
	}

	/**
	 * Scans the classpath for Search implementations, through the
	 * {@link ServiceLoader} mechanism and returns one.
	 * @param classSimpleName the name of the class name to look for and load
	 * @return a Search instance if found, or null
	 */
	final Search loadExternalSearch(String classSimpleName) {
		ServiceLoader<Search> searchLoader = ServiceLoader.load(Search.class, Para.getParaClassLoader());
		for (Search search : searchLoader) {
			if (search != null && classSimpleName.equalsIgnoreCase(search.getClass().getSimpleName())) {
				return search;
			}
		}
		return null;
	}

}
