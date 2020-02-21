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
package com.erudika.para.persistence;

import com.erudika.para.Para;
import com.erudika.para.utils.Config;
import com.google.inject.AbstractModule;
import java.util.ServiceLoader;
import org.apache.commons.lang3.StringUtils;

/**
 * The default persistence module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class PersistenceModule extends AbstractModule {

	protected void configure() {
		String selectedDAO = Config.getConfigParam("dao", "");
		if (StringUtils.isBlank(selectedDAO)) {
			if ("embedded".equals(Config.ENVIRONMENT)) {
				bindToDefault();
			} else {
				bind(DAO.class).to(AWSDynamoDAO.class).asEagerSingleton();
			}
		} else {
			if ("dynamodb".equalsIgnoreCase(selectedDAO) ||
					AWSDynamoDAO.class.getSimpleName().equalsIgnoreCase(selectedDAO)) {
				bind(DAO.class).to(AWSDynamoDAO.class).asEagerSingleton();
			} else {
				DAO daoPlugin = loadExternalDAO(selectedDAO);
				if (daoPlugin != null) {
					// external plugins - MongoDB, Cassandra, H2DAO, xSQL, etc.
					bind(DAO.class).to(daoPlugin.getClass()).asEagerSingleton();
				} else {
					// in-memory DAO - default fallback
					bindToDefault();
				}
			}
		}
	}

	void bindToDefault() {
		bind(DAO.class).to(MockDAO.class).asEagerSingleton();
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
