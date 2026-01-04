/*
 * Copyright 2013-2026 Erudika. https://erudika.com
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
package com.erudika.para.server.search;

import com.erudika.para.core.persistence.DAO;
import com.erudika.para.core.search.MockSearch;
import com.erudika.para.core.search.Search;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Para;
import java.util.ServiceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The default search module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Configuration
public class SearchModule {

	@Bean
	public Search getSearch(DAO dao) { // wait for DAO to be loaded
		Search search;
		String selectedSearch = Para.getConfig().searchPlugin();
		Search searchPlugin = loadExternalSearch(selectedSearch);
		if (searchPlugin != null) {
			search = searchPlugin;
		} else {
			// default fallback - not implemented!
			search = bindToDefault();
		}
		CoreUtils.getInstance().setSearch(new MeasuredSearch(search));
		return Para.getSearch();
	}

	Search bindToDefault() {
		return new MockSearch();
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
