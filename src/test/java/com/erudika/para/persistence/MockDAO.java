/*
 * Copyright 2013-2014 Erudika. http://erudika.com
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

import com.erudika.para.core.ParaObject;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
	public <P extends ParaObject> String create(String appid, P so) {
		if (so == null) {
			return null;
		}
		if (StringUtils.isBlank(so.getId())) {
			so.setId(Utils.getNewId());
		}
		if (so.getTimestamp() == null) {
			so.setTimestamp(Utils.timestamp());
		}
		getMap(appid).put(so.getId(), so);
		logger.debug("DAO.create() {}", so.getId());
		return so.getId();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> P read(String appid, String key) {
		if (key == null || StringUtils.isBlank(appid)) {
			return null;
		}
		P so = (P) getMap(appid).get(key);
		logger.debug("DAO.read() {} -> {}", key, so);
		return so;
	}

	@Override
	public <P extends ParaObject> void update(String appid, P so) {
		if (so != null && !StringUtils.isBlank(appid)) {
			so.setUpdated(Utils.timestamp());
			getMap(appid).put(so.getId(), so);
			logger.debug("DAO.update() {}", so.getId());
		}
	}

	@Override
	public <P extends ParaObject> void delete(String appid, P so) {
		if (so != null && !StringUtils.isBlank(appid)) {
			getMap(appid).remove(so.getId());
			logger.debug("DAO.delete() {}", so.getId());
		}
	}

	@Override
	public String getColumn(String appid, String key, String colName) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(appid) || StringUtils.isBlank(colName)) {
			return null;
		}
		try {
			return BeanUtils.getProperty(read(appid, key), colName);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public void putColumn(String appid, String key, String colName, String colValue) {
		if (key == null || StringUtils.isBlank(colName) || StringUtils.isBlank(colValue)
				|| StringUtils.isBlank(appid)) return;
		ParaObject p = read(appid, key);
		if (p != null) {
			try {
				BeanUtils.setProperty(p, colName, colValue);
				update(appid, p);
			} catch (Exception ex) { }
		}
	}

	@Override
	public void removeColumn(String appid, String key, String colName) {
		if (key == null || StringUtils.isBlank(colName) || StringUtils.isBlank(appid)) {
			return;
		}
		ParaObject p = read(appid, key);
		if (p != null) {
			try {
				PropertyUtils.setProperty(p, colName, null);
				update(appid, p);
			} catch (Exception ex) { }
		}
	}

	@Override
	public boolean existsColumn(String appid, String key, String colName) {
		if (StringUtils.isBlank(key)) {
			return false;
		}
		try {
			return getColumn(appid, key, colName) != null;
		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public <P extends ParaObject> void createAll(String appid, List<P> objects) {
		if (StringUtils.isBlank(appid)) {
			return;
		}
		for (P p : objects) {
			create(p);
		}
		logger.debug("DAO.createAll() {}", objects.size());
	}

	@Override
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> Map<String, P> readAll(String appid, List<String> keys, boolean getAllColumns) {
		Map<String, P> results = new LinkedHashMap<String, P>();
		if (keys == null || StringUtils.isBlank(appid)) {
			return results;
		}
		for (String key : keys) {
			if (getMap(appid).containsKey(key)) {
				results.put(key, (P) read(key));
			}
		}
		logger.debug("DAO.readAll() {}", results.size());
		return results;
	}

	@Override
	public <P extends ParaObject> List<P> readPage(String appid, Pager pager) {
		return new ArrayList<P>();
	}

	@Override
	public <P extends ParaObject> void updateAll(String appid, List<P> objects) {
		if (!StringUtils.isBlank(appid)) {
			for (P obj : objects) {
				if (obj != null) {
					update(appid, obj);
				}
			}
			logger.debug("DAO.updateAll() {}", objects.size());
		}
	}

	@Override
	public <P extends ParaObject> void deleteAll(String appid, List<P> objects) {
		if (!StringUtils.isBlank(appid)) {
			for (P obj : objects) {
				if (obj != null && getMap(appid).containsKey(obj.getId())) {
					delete(obj);
				}
			}
			logger.debug("DAO.deleteAll() {}", objects.size());
		}
	}

	private Map<String, ParaObject> getMap(String appid) {
		if (!maps.containsKey(appid)) {
			maps.put(appid, new  HashMap<String, ParaObject>());
		}
		return maps.get(appid);
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
	public <P extends ParaObject> Map<String, P> readAll(List<String> keys, boolean getAllColumns) {
		return readAll(Config.APP_NAME_NS, keys, getAllColumns);
	}

	@Override
	public <P extends ParaObject> List<P> readPage(Pager pager) {
		return readPage(Config.APP_NAME_NS, pager);
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