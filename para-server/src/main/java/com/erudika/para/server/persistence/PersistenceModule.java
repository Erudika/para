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
package com.erudika.para.server.persistence;

import com.erudika.para.core.persistence.DAO;
import com.erudika.para.core.persistence.MockDAO;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Para;
import java.util.ServiceLoader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The default persistence module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Configuration
public class PersistenceModule {

	@Bean
	public DAO getDAO() {
		DAO dao;
		String selectedDAO = Para.getConfig().daoPlugin();
		if (StringUtils.isBlank(selectedDAO)) {
			dao = bindToDefault();
		} else {
			DAO daoPlugin = loadExternalDAO(selectedDAO);
			if (daoPlugin != null) {
				// external plugins - MongoDB, Cassandra, H2DAO, xSQL, etc.
				dao = daoPlugin;
			} else {
				// in-memory DAO - default fallback
				dao = bindToDefault();
			}
		}
		CoreUtils.getInstance().setDao(new ManagedDAO(dao));
		return Para.getDAO();
	}

	DAO bindToDefault() {
		return new MockDAO();
	}

	/**
	 * Scans the classpath for DAO implementations, through the
	 * {@link ServiceLoader} mechanism and returns one.
	 * @param classSimpleName the name of the class name to look for and load
	 * @return a DAO instance if found, or null
	 */
	final DAO loadExternalDAO(String classSimpleName) {
		ServiceLoader<DAO> daoLoader = ServiceLoader.load(DAO.class, Para.getParaClassLoader());
		for (DAO dao : daoLoader) {
			if (dao != null && classSimpleName.equalsIgnoreCase(dao.getClass().getSimpleName())) {
				return dao;
			}
		}
		return null;
	}

}
