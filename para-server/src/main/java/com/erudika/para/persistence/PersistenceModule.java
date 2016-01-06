/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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

import com.erudika.para.utils.Config;
import com.google.inject.AbstractModule;
import org.apache.commons.lang3.StringUtils;

/**
 * The default persistence module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class PersistenceModule extends AbstractModule {

	protected void configure() {
		String selectedDAO = Config.getConfigParam("database", "");
		if (StringUtils.isBlank(selectedDAO)) {
			if ("embedded".equals(Config.ENVIRONMENT)) {
				bind(DAO.class).to(IndexBasedDAO.class).asEagerSingleton();
			} else {
				bind(DAO.class).to(AWSDynamoDAO.class).asEagerSingleton();
			}
		} else {
			if ("elasticsearch".equalsIgnoreCase(selectedDAO)) {
				bind(DAO.class).to(IndexBasedDAO.class).asEagerSingleton();
			} else if ("dynamodb".equalsIgnoreCase(selectedDAO)) {
				bind(DAO.class).to(AWSDynamoDAO.class).asEagerSingleton();
			} else if ("cassandra".equalsIgnoreCase(selectedDAO)) {
				// Cassandra connector plugin
			} else if ("mongodb".equalsIgnoreCase(selectedDAO)) {
				// MongoDB connector plugin
			} else if ("postgre".equalsIgnoreCase(selectedDAO)) {
				// MongoDB connector plugin
			} else {
				// in-memory DB
				bind(DAO.class).to(MockDAO.class).asEagerSingleton();
			}
		}
	}

}
