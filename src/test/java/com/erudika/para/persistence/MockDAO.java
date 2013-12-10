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

import com.erudika.para.annotations.Stored;
import com.erudika.para.core.ParaObject;
import com.erudika.para.utils.Utils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@Singleton
public class MockDAO implements DAO {

	private Map<String, ParaObject> map = new HashMap<String, ParaObject>();
	private static final Logger logger = LoggerFactory.getLogger(MockDAO.class);
	
	@Override
	public <P extends ParaObject> String create(P so) {
		String[] err = Utils.validateRequest(so);
		if(err.length > 0){
			logger.warn("create ", err.toString());
			return null;
		}
		if(StringUtils.isBlank(so.getId())) so.setId(Utils.getNewId());
		if(so.getTimestamp() == null) so.setTimestamp(Utils.timestamp());
		map.put(so.getId(), so);
		return so.getId();
	}

	@Override
	public <P extends ParaObject> P read(String key) {
		return (P) map.get(key);
	}

	@Override
	public <P extends ParaObject> void update(P so) {
		String[] err = Utils.validateRequest(so);
		if(err.length > 0){
			logger.warn("update ", err.toString());
			return;
		}
		so.setUpdated(System.currentTimeMillis());
		map.put(so.getId(), so);
	}

	@Override
	public <P extends ParaObject> void delete(P so) {
		if(so == null) return;
		map.remove(so.getId());
	}

	@Override
	public String getColumn(String key, String cf, String colName) {
		try {
			return Utils.getAnnotatedFields(map.get(key), Stored.class, null).get(colName).toString();
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public void putColumn(String key, String cf, String colName, String colValue) {
		if(key == null || !map.containsKey(key)) return;
		ParaObject p = map.get(key);
		try {
			BeanUtils.setProperty(p, colName, colValue);
			map.put(key, p);
		} catch (Exception ex) {}
	}

	@Override
	public void removeColumn(String key, String cf, String colName) {
		if(key == null || !map.containsKey(key)) return;
		ParaObject p = map.get(key);
		try {
			BeanUtils.setProperty(p, colName, null);
		} catch (Exception ex) {}
	}

	@Override
	public boolean existsColumn(String key, String cf, String columnName) {
		try {
			return map.containsKey(key) && BeanUtils.getProperty(map.get(key), columnName) != null;
		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public <P extends ParaObject> void createAll(List<P> objects) {
		for (P p : objects) {
			create(p);
		}
	}

	@Override
	public <P extends ParaObject> Map<String, P> readAll(List<String> keys, boolean getAllAtrributes) {
		Map<String, P> map1 = new HashMap<String, P>();
		for (String key : keys) {
			if(map.containsKey(key)){
				map1.put(key, (P) map.get(key));
			}
		}
		return map1;
	}

	@Override
	public <P extends ParaObject> List<P> readPage(String cf, String lastKey) {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	@Override
	public <P extends ParaObject> void updateAll(List<P> objects) {
		for (P obj : objects) {
			if(obj != null) map.put(obj.getId(), obj);
		}
	}

	@Override
	public <P extends ParaObject> void deleteAll(List<P> objects) {
		for (P obj : objects) {
			if(obj != null && map.containsKey(obj.getId())) map.remove(obj.getId());
		}
	}
		
}