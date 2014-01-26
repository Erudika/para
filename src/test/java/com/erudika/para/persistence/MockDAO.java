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

import com.erudika.para.core.ParaObject;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
@Singleton
public class MockDAO implements DAO {

	private static final Logger logger = LoggerFactory.getLogger(MockDAO.class);
	private Map<String, Map<String, ParaObject>> maps = new HashMap<String, Map<String, ParaObject>>();
	
	@Override
	public <P extends ParaObject> String create(String appName, P so) {
		if(so == null) return null;
		if(StringUtils.isBlank(so.getId())) so.setId(Utils.getNewId());
		if(so.getTimestamp() == null) so.setTimestamp(Utils.timestamp());
		getMap(appName).put(so.getId(), so);
		logger.debug("DAO.create() {}", so.getId());
		return so.getId();
	}

	@Override
	public <P extends ParaObject> P read(String appName, String key) {
		if(key == null || StringUtils.isBlank(appName)) return null;
		P so = (P) getMap(appName).get(key);
		logger.debug("DAO.read() {} -> {}", key, so);
		return so;
	}

	@Override
	public <P extends ParaObject> void update(String appName, P so) {
		if(so == null || StringUtils.isBlank(appName)) return;
		so.setUpdated(System.currentTimeMillis());
		getMap(appName).put(so.getId(), so);
		logger.debug("DAO.update() {}", so.getId());
	}

	@Override
	public <P extends ParaObject> void delete(String appName, P so) {
		if(so == null || StringUtils.isBlank(appName)) return;
		getMap(appName).remove(so.getId());
		logger.debug("DAO.delete() {}", so.getId());
	}

	@Override
	public String getColumn(String appName, String key, String colName) {
		if(StringUtils.isBlank(key) || StringUtils.isBlank(appName) || StringUtils.isBlank(colName)) return null;
		try {
			return BeanUtils.getProperty(read(appName, key), colName);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public void putColumn(String appName, String key, String colName, String colValue) {
		if(key == null || StringUtils.isBlank(colName) || StringUtils.isBlank(colValue) 
				|| StringUtils.isBlank(appName)) return;
		ParaObject p = getMap(appName).get(key);
		if(p == null) return;
		try {
			BeanUtils.setProperty(p, colName, colValue);
			getMap(appName).put(key, p);
		} catch (Exception ex) {}
	}

	@Override
	public void removeColumn(String appName, String key, String colName) {
		if(key == null || StringUtils.isBlank(colName) || StringUtils.isBlank(appName)) return;
		ParaObject p = getMap(appName).get(key);
		if(p == null) return;
		try {
			PropertyUtils.setProperty(p, colName, null);
			getMap(appName).put(key, p);
		} catch (Exception ex) {}
	}

	@Override
	public boolean existsColumn(String appName, String key, String columnName) {
		if(StringUtils.isBlank(key)) return false;
		try {
			return getColumn(appName, key, columnName) != null;
		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public <P extends ParaObject> void createAll(String appName, List<P> objects) {
		if(StringUtils.isBlank(appName)) return;
		for (P p : objects) {
			create(p);
		}
		logger.debug("DAO.createAll() {}", objects.size());
	}

	@Override
	public <P extends ParaObject> Map<String, P> readAll(String appName, List<String> keys, boolean getAllAtrributes) {
		Map<String, P> results = new HashMap<String, P>();
		if(keys == null || StringUtils.isBlank(appName)) return results;
		for (String key : keys) {
			if(getMap(appName).containsKey(key)){
				results.put(key, (P) getMap(appName).get(key));
			}
		}
		logger.debug("DAO.readAll() {}", results.size());
		return results;
	}

	@Override
	public <P extends ParaObject> List<P> readPage(String appName, String lastKey) {
		return new ArrayList<P> ();
	}

	@Override
	public <P extends ParaObject> void updateAll(String appName, List<P> objects) {
		if(StringUtils.isBlank(appName)) return;
		for (P obj : objects) {
			if(obj != null) getMap(appName).put(obj.getId(), obj);
		}
		logger.debug("DAO.updateAll() {}", objects.size());
	}

	@Override
	public <P extends ParaObject> void deleteAll(String appName, List<P> objects) {
		if(StringUtils.isBlank(appName)) return;
		for (P obj : objects) {
			if(obj != null && getMap(appName).containsKey(obj.getId())) getMap(appName).remove(obj.getId());
		}
		logger.debug("DAO.deleteAll() {}", objects.size());
	}
	
	private Map<String, ParaObject> getMap(String appName){
		if(!maps.containsKey(appName)){
			maps.put(appName, new  HashMap<String, ParaObject>());
		}
		return maps.get(appName);
	}

	////////////////////////////////////////////////////////////////////
	
	@Override
	public <P extends ParaObject> String create(P so) {
		return create(Config.APP_NAME_NS, so);
	}

	@Override
	public <P extends ParaObject> P read(String key) {
		return read(Config.	APP_NAME_NS, key);
	}

	@Override
	public <P extends ParaObject> void update(P so) {
		update(Config.APP_NAME_NS, so);
	}

	@Override
	public <P extends ParaObject> void delete(P so) {
		delete(Config.APP_NAME_NS, so);
	}

	@Override
	public String getColumn(String key, String colName) {
		return getColumn(Config.APP_NAME_NS, key, colName);
	}

	@Override
	public void putColumn(String key, String colName, String colValue) {
		putColumn(Config.APP_NAME_NS, key, colName, colValue);
	}

	@Override
	public void removeColumn(String key, String colName) {
		removeColumn(Config.APP_NAME_NS, key, colName);
	}

	@Override
	public boolean existsColumn(String key, String columnName) {
		return existsColumn(Config.APP_NAME_NS, key, columnName);
	}

	@Override
	public <P extends ParaObject> void createAll(List<P> objects) {
		createAll(Config.APP_NAME_NS, objects);
	}

	@Override
	public <P extends ParaObject> Map<String, P> readAll(List<String> keys, boolean getAllAtrributes) {
		return readAll(Config.APP_NAME_NS, keys, getAllAtrributes);
	}

	@Override
	public <P extends ParaObject> List<P> readPage(String lastKey) {
		return readPage(Config.APP_NAME_NS, lastKey);
	}

	@Override
	public <P extends ParaObject> void updateAll(List<P> objects) {
		updateAll(Config.APP_NAME_NS, objects);
	}

	@Override
	public <P extends ParaObject> void deleteAll(List<P> objects) {
		deleteAll(Config.APP_NAME_NS, objects);
	}
		
}