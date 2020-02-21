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

import com.erudika.para.annotations.Locked;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fake DAO for in-memory persistence.
 * Used for testing and development without a database.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class MockDAO implements DAO {

	private static final Logger logger = LoggerFactory.getLogger(MockDAO.class);
	private static final Map<String, Map<String, ParaObject>> MAPS =
			new ConcurrentHashMap<String, Map<String, ParaObject>>();

	@Override
	public <P extends ParaObject> String create(String appid, P so) {
		if (so == null) {
			return null;
		}
		createObject(appid, so);
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
		updateObject(appid, so);
	}

	@Override
	public <P extends ParaObject> void delete(String appid, P so) {
		deleteObject(appid, so);
	}

	@Override
	public <P extends ParaObject> void createAll(String appid, List<P> objects) {
		if (StringUtils.isBlank(appid) || objects == null) {
			return;
		}
		for (P p : objects) {
			createObject(appid, p);
		}
		logger.debug("DAO.createAll() {}", objects.size());
	}

	@Override
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> Map<String, P> readAll(String appid, List<String> keys, boolean getAllColumns) {
		if (keys == null || StringUtils.isBlank(appid)) {
			return Collections.emptyMap();
		}
		Map<String, P> results = new LinkedHashMap<>(keys.size());
		for (String key : keys) {
			if (getMap(appid).containsKey(key)) {
				results.put(key, (P) read(appid, key));
			}
		}
		logger.debug("DAO.readAll() {}", results.size());
		return results;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> readPage(String appid, Pager pager) {
		List<P> results = new LinkedList<>();
		if (StringUtils.isBlank(appid)) {
			return results;
		}
		if (pager == null) {
			pager = new Pager();
		}
		if (pager.getCount() >= getMap(appid).size()) {
			return results;
		}

		String lastKey = pager.getLastKey();
		boolean found = false;
		int	i = 0;
		for (String key : getMap(appid).keySet()) {
			if (lastKey != null && !found) {
				found = key.equals(lastKey);
			} else {
				results.add((P) getMap(appid).get(key));
				i++;
			}
			if (i >= pager.getLimit()) {
				pager.setLastKey(key);
				break;
			}
		}
		pager.setCount(pager.getCount() + i);
		return results;
	}

	@Override
	public <P extends ParaObject> void updateAll(String appid, List<P> objects) {
		if (!StringUtils.isBlank(appid) && objects != null) {
			for (P obj : objects) {
				if (obj != null) {
					updateObject(appid, obj);
				}
			}
			logger.debug("DAO.updateAll() {}", objects.size());
		}
	}

	@Override
	public <P extends ParaObject> void deleteAll(String appid, List<P> objects) {
		if (!StringUtils.isBlank(appid) && objects != null) {
			for (P obj : objects) {
				if (obj != null && getMap(appid).containsKey(obj.getId())) {
					deleteObject(appid, obj);
				}
			}
			logger.debug("DAO.deleteAll() {}", objects.size());
		}
	}

	private <P extends ParaObject> void createObject(String appid, P so) {
		if (so == null) {
			return;
		}
		if (StringUtils.isBlank(so.getId())) {
			so.setId(Utils.getNewId());
		}
		if (so.getTimestamp() == null) {
			so.setTimestamp(Utils.timestamp());
		}
		so.setAppid(appid);
		getMap(appid).put(so.getId(), ParaObjectUtils.setAnnotatedFields(ParaObjectUtils.toObject(null, so.getType()),
				ParaObjectUtils.getAnnotatedFields(so), null));
	}

	private <P extends ParaObject> void updateObject(String appid, P so) {
		if (so != null && !StringUtils.isBlank(appid)) {
			so.setUpdated(Utils.timestamp());
			ParaObject soUpdated = getMap(appid).get(so.getId());
			getMap(appid).put(so.getId(), ParaObjectUtils.setAnnotatedFields(soUpdated,
					ParaObjectUtils.getAnnotatedFields(so), Locked.class));
			logger.debug("DAO.update() {}", so.getId());
		}
	}

	private <P extends ParaObject> void deleteObject(String appid, P so) {
		if (so != null && !StringUtils.isBlank(appid)) {
			getMap(appid).remove(so.getId());
			logger.debug("DAO.delete() {}", so.getId());
		}
	}

	private Map<String, ParaObject> getMap(String appid) {
		if (!MAPS.containsKey(appid)) {
			MAPS.put(appid, new ConcurrentHashMap<>());
		}
		return MAPS.get(appid);
	}

	////////////////////////////////////////////////////////////////////

	@Override
	public <P extends ParaObject> String create(P so) {
		return create(Config.getRootAppIdentifier(), so);
	}

	@Override
	public <P extends ParaObject> P read(String key) {
		return read(Config.getRootAppIdentifier(), key);
	}

	@Override
	public <P extends ParaObject> void update(P so) {
		update(Config.getRootAppIdentifier(), so);
	}

	@Override
	public <P extends ParaObject> void delete(P so) {
		delete(Config.getRootAppIdentifier(), so);
	}

	@Override
	public <P extends ParaObject> void createAll(List<P> objects) {
		createAll(Config.getRootAppIdentifier(), objects);
	}

	@Override
	public <P extends ParaObject> Map<String, P> readAll(List<String> keys, boolean getAllColumns) {
		return readAll(Config.getRootAppIdentifier(), keys, getAllColumns);
	}

	@Override
	public <P extends ParaObject> List<P> readPage(Pager pager) {
		return readPage(Config.getRootAppIdentifier(), pager);
	}

	@Override
	public <P extends ParaObject> void updateAll(List<P> objects) {
		updateAll(Config.getRootAppIdentifier(), objects);
	}

	@Override
	public <P extends ParaObject> void deleteAll(List<P> objects) {
		deleteAll(Config.getRootAppIdentifier(), objects);
	}

}
