/*
 * Copyright 2013-2017 Erudika. https://erudika.com
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
import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DAO implementation using MapDB for persistence.
 * Great for local development and for small production servers.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class MapDBDAO implements DAO {

	private static final Logger logger = LoggerFactory.getLogger(MapDBDAO.class);
	private static final Map<String, Map<String, ParaObject>> MAPS =
			new ConcurrentHashMap<String, Map<String, ParaObject>>();

	@Override
	public <P extends ParaObject> String create(String appid, P so) {
		if (so == null) {
			return null;
		}
		createAll(appid, Collections.singletonList(so));
		logger.debug("DAO.create() {}", so.getId());
		return so.getId();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> P read(String appid, String key) {
		if (key == null || StringUtils.isBlank(appid)) {
			return null;
		}
		P so = (P) readAll(appid, Collections.singletonList(key), true).get(key);
		logger.debug("DAO.read() {} -> {}", key, so);
		return so;
	}

	@Override
	public <P extends ParaObject> void update(String appid, P so) {
		if (so != null && !StringUtils.isBlank(appid)) {
			updateAll(appid, Collections.singletonList(so));
			logger.debug("DAO.update() {}", so.getId());
		}
	}

	@Override
	public <P extends ParaObject> void delete(String appid, P so) {
		if (so != null && !StringUtils.isBlank(appid)) {
			deleteAll(appid, Collections.singletonList(so));
			logger.debug("DAO.delete() {}", so.getId());
		}
	}

	@Override
	public <P extends ParaObject> void createAll(String appid, List<P> objects) {
		if (StringUtils.isBlank(appid) || objects == null) {
			return;
		}
		DB db = db(appid);
		ConcurrentMap<String, Object> map = getMap(db, appid);
		for (P po : objects) {
			if (po != null) {
				if (StringUtils.isBlank(po.getId())) {
					po.setId(Utils.getNewId());
				}
				if (po.getTimestamp() == null) {
					po.setTimestamp(Utils.timestamp());
				}
				po.setAppid(appid);
				map.put(po.getId(), ParaObjectUtils.setAnnotatedFields(ParaObjectUtils.toObject(po.getType()),
						ParaObjectUtils.getAnnotatedFields(po), null));
			}
		}
		db.commit();
		db.close();
		logger.debug("DAO.createAll() {}", objects.size());
	}

	@Override
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> Map<String, P> readAll(String appid, List<String> keys, boolean getAllColumns) {
		if (keys == null || StringUtils.isBlank(appid)) {
			return Collections.emptyMap();
		}
		DB db = db(appid);
		ConcurrentMap<String, Object> map = getMap(db, appid);
		Map<String, P> results = new LinkedHashMap<String, P>(keys.size());
		for (String key : keys) {
			if (map.containsKey(key)) {
				P so = (P) map.get(key);
				results.put(key, so);
			}
		}
		db.close();
		logger.debug("DAO.readAll() {}", results.size());
		return results;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> readPage(String appid, Pager pager) {
		List<P> results = new LinkedList<P>();
		if (StringUtils.isBlank(appid)) {
			return results;
		}
		if (pager == null) {
			pager = new Pager();
		}
		DB db = db(appid);
		if (pager.getCount() >= getMap(db, appid).size()) {
			return results;
		}

		ConcurrentMap<String, Object> map = getMap(db, appid);
		String lastKey = pager.getLastKey();
		boolean found = false;
		int	i = 0;
		for (String key : map.keySet()) {
			if (lastKey != null && !found) {
				found = key.equals(lastKey);
			} else {
				results.add((P) map.get(key));
				i++;
			}
			if (i >= pager.getLimit()) {
				pager.setLastKey(key);
				break;
			}
		}
		db.close();
		pager.setCount(pager.getCount() + i);
		return results;
	}

	@Override
	public <P extends ParaObject> void updateAll(String appid, List<P> objects) {
		if (!StringUtils.isBlank(appid) && objects != null) {
			DB db = db(appid);
			ConcurrentMap<String, Object> map = getMap(db, appid);
			for (P so : objects) {
				if (so != null) {
					so.setUpdated(Utils.timestamp());
					ParaObject soUpdated = (ParaObject) map.get(so.getId());
					map.put(so.getId(), ParaObjectUtils.setAnnotatedFields(soUpdated,
							ParaObjectUtils.getAnnotatedFields(so), Locked.class));
					logger.debug("DAO.update() {}", so.getId());
				}
			}
			db.commit();
			db.close();
			logger.debug("DAO.updateAll() {}", objects.size());
		}
	}

	@Override
	public <P extends ParaObject> void deleteAll(String appid, List<P> objects) {
		if (!StringUtils.isBlank(appid) && objects != null) {
			DB db = db(appid);
			ConcurrentMap<String, Object> map = getMap(db, appid);
			for (P so : objects) {
				if (so != null) {
					map.remove(so.getId());
				}
			}
			db.commit();
			db.close();
			logger.debug("DAO.deleteAll() {}", objects.size());
		}
	}

	@SuppressWarnings("unchecked")
	private ConcurrentMap<String, Object> getMap(DB db, String appid) {
		if (db == null) {
			throw new IllegalArgumentException("DB not initialized.");
		}
		return db.hashMap(getDBNameForAppid(appid), Serializer.STRING, Serializer.JAVA).counterEnable().createOrOpen();
	}

	private DB db(String appid) {
		return DBMaker.fileDB(getDBFileForAppid(appid)).transactionEnable().closeOnJvmShutdown().make();
	}

	/**
	 * Returns the {@link File} object where the database is stored.
	 * @param appid app id
	 * @return File handle for the DB/table file
	 */
	public static File getDBFileForAppid(String appid) {
		if (StringUtils.isBlank(appid)) {
			throw new IllegalArgumentException("Appid cannot be blank.");
		}
		String dataDir = Config.getConfigParam("mapdb.dir", Paths.get(".").toAbsolutePath().normalize().toString());
		String dbName = getDBNameForAppid(appid) + ".db";
		Path path = FileSystems.getDefault().getPath(dataDir, "data", dbName);
		return path.toFile();
	}

	/**
	 * Returns the table name for a given app id. Table names are usually in the form 'prefix-appid'.
	 * @param appid app id
	 * @return the table name
	 */
	public static String getDBNameForAppid(String appid) {
		if (StringUtils.isBlank(appid)) {
			throw new IllegalArgumentException("Appid cannot be blank.");
		}
		return ((App.isRoot(appid) || appid.startsWith(Config.PARA.concat("-"))) ?
					appid : Config.PARA + "-" + appid);
	}

	////////////////////////////////////////////////////////////////////

	@Override
	public <P extends ParaObject> String create(P so) {
		return create(Config.APP_NAME_NS, so);
	}

	@Override
	public <P extends ParaObject> P read(String key) {
		return read(Config.APP_NAME_NS, key);
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
