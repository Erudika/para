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
package com.erudika.para.persistence;

import com.erudika.para.annotations.Cached;
import com.erudika.para.annotations.Indexed;
import com.erudika.para.core.ParaObject;
import com.erudika.para.utils.Config;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public interface DAO {
	
	public static final String CN_AUTHTOKEN = "authtoken";
	public static final String CN_CLASSNAME = "classname";
	public static final String CN_COUNTS_COUNT = "count";
	public static final String CN_CREATORID = "creatorid";
	public static final String CN_ID = "id";
	public static final String CN_IDENTIFIER = "identifier";
	public static final String CN_KEY = "key";
	public static final String CN_NAME = "name";
	public static final String CN_PARENTID = "parentid";
	public static final String CN_PASSWORD = "password";
	public static final String CN_RESET_TOKEN = "token";
	public static final String CN_SALT = "salt";
	public static final String CN_TIMESTAMP = "timestamp";
	public static final String CN_UPDATED = "updated";
	
	//////////  DB CONFIG  //////////////
	public static final String TABLE_PREFIX = Config.CLUSTER_NAME.concat("_");
	//////////  DB TABLES  //////////////
	public static final String OBJECTS = TABLE_PREFIX.concat("objects");

	/********************************************
	 *			CORE FUNCTIONS
	 ********************************************/
	@Indexed(action = Indexed.Action.ADD)
	@Cached(action = Cached.Action.PUT)
	public <P extends ParaObject> String create(P so);

	@Cached(action = Cached.Action.GET)
	public <P extends ParaObject> P read(String key);

	@Indexed(action = Indexed.Action.ADD)
	@Cached(action = Cached.Action.PUT)
	public <P extends ParaObject> void update(P so);
	
	@Indexed(action = Indexed.Action.REMOVE)
	@Cached(action = Cached.Action.DELETE)
	public <P extends ParaObject> void delete(P so);

	/********************************************
	 *				COLUMN FUNCTIONS
	 ********************************************/
	public String getColumn(String key, String cf, String colName);
	
	public void putColumn(String key, String cf, String colName, String colValue);

	public void removeColumn(String key, String cf, String colName);

	public boolean existsColumn(String key, String cf, String columnName);
	
	/********************************************
	 *				READ ALL FUNCTIONS
	 ********************************************/	
	@Indexed(action = Indexed.Action.ADD_ALL)
	@Cached(action = Cached.Action.PUT_ALL)
	public <P extends ParaObject> void createAll(List<P> objects);
	
	@Cached(action = Cached.Action.GET_ALL)
	public <P extends ParaObject> Map<String, P> readAll(List<String> keys, boolean getAllAtrributes);

	public <P extends ParaObject> List<P> readPage(String cf, String lastKey);

	@Indexed(action = Indexed.Action.ADD_ALL)
	@Cached(action = Cached.Action.PUT_ALL)
	public <P extends ParaObject> void updateAll(List<P> objects);
	
	@Indexed(action = Indexed.Action.REMOVE_ALL)
	@Cached(action = Cached.Action.DELETE_ALL)
	public <P extends ParaObject> void deleteAll(List<P> objects);
}
