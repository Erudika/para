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
package com.erudika.para.persistence;

import com.erudika.para.annotations.Cached;
import com.erudika.para.annotations.Indexed;
import com.erudika.para.core.ParaObject;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public interface DAO {
	
	public static final String CN_AUTHTOKEN = "authtoken";
	public static final String CN_CLASSNAME = "classname";
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
	public static final String CN_TAGS = "tags";
	public static final String CN_EMAIL = "email";
	public static final String CN_GROUPS = "groups";
	
	/********************************************
	 *			CORE FUNCTIONS
	 ********************************************/
	@Indexed(action = Indexed.Action.ADD)
	@Cached(action = Cached.Action.PUT)
	public <P extends ParaObject> String create(String appName, P so);
	public <P extends ParaObject> String create(P so);
	
	@Cached(action = Cached.Action.GET)
	public <P extends ParaObject> P read(String appName, String key);
	public <P extends ParaObject> P read(String key);

	@Indexed(action = Indexed.Action.ADD)
	@Cached(action = Cached.Action.PUT)
	public <P extends ParaObject> void update(String appName, P so);
	public <P extends ParaObject> void update(P so);
	
	@Indexed(action = Indexed.Action.REMOVE)
	@Cached(action = Cached.Action.DELETE)
	public <P extends ParaObject> void delete(String appName, P so);
	public <P extends ParaObject> void delete(P so);

	/********************************************
	 *				COLUMN FUNCTIONS
	 ********************************************/
	public String getColumn(String key, String colName);
	public String getColumn(String appName, String key, String colName);
	
	public void putColumn(String key, String colName, String colValue);
	public void putColumn(String appName, String key, String colName, String colValue);

	public void removeColumn(String key, String colName);
	public void removeColumn(String appName, String key, String colName);

	public boolean existsColumn(String key, String columnName);
	public boolean existsColumn(String appName, String key, String columnName);
	
	/********************************************
	 *				READ ALL FUNCTIONS
	 ********************************************/	
	@Indexed(action = Indexed.Action.ADD_ALL)
	@Cached(action = Cached.Action.PUT_ALL)
	public <P extends ParaObject> void createAll(String appName, List<P> objects);
	public <P extends ParaObject> void createAll(List<P> objects);
	
	@Cached(action = Cached.Action.GET_ALL)
	public <P extends ParaObject> Map<String, P> readAll(String appName, List<String> keys, boolean getAllAtrributes);
	public <P extends ParaObject> Map<String, P> readAll(List<String> keys, boolean getAllAtrributes);

	public <P extends ParaObject> List<P> readPage(String appName, String lastKey);
	public <P extends ParaObject> List<P> readPage(String lastKey);

	@Indexed(action = Indexed.Action.ADD_ALL)
	@Cached(action = Cached.Action.PUT_ALL)
	public <P extends ParaObject> void updateAll(String appName, List<P> objects);
	public <P extends ParaObject> void updateAll(List<P> objects);
	
	@Indexed(action = Indexed.Action.REMOVE_ALL)
	@Cached(action = Cached.Action.DELETE_ALL)
	public <P extends ParaObject> void deleteAll(String appName, List<P> objects);
	public <P extends ParaObject> void deleteAll(List<P> objects);
}
