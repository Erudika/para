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

import com.erudika.para.annotations.Locked;
import com.erudika.para.core.ParaObject;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the {@link DAO} interface used for local development only.
 * It uses is based on the {@link com.erudika.para.search.Search} implementation.
 * Objects are stored in the index rather than in a data store.
 *
 * <b>Note</b>: This implementation doesn't work well with shared apps (app.isShared() must be false).
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class IndexBasedDAO implements DAO {

	private static final Logger logger = LoggerFactory.getLogger(IndexBasedDAO.class);
	private Map<String, Map<String, ParaObject>> maps = new HashMap<String, Map<String, ParaObject>>();
	private Search search;

	/**
	 * No-args constructor
	 */
	public IndexBasedDAO() { }

	/**
	 * Default constructor.
	 * @param search the search object
	 */
	@Inject
	public IndexBasedDAO(Search search) {
		this.search = search;
	}

	/**
	 * Sets the search instance
	 * @param search a serch instance
	 */
	public void setSearch(Search search) {
		this.search = search;
	}

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
		so.setAppid(appid);
		getMap(appid).put(so.getId(), Utils.setAnnotatedFields(Utils.toObject(so.getType()),
				Utils.getAnnotatedFields(so), null));
		logger.debug("DAO.create() {}", so.getId());
		return so.getId();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> P read(String appid, String key) {
		if (key == null || StringUtils.isBlank(appid) || search == null) {
			return null;
		}

		P so = search.findById(getAppidWithRouting(appid), key);
		if (so == null) {
			so = (P) getMap(appid).get(key);
		}
		logger.debug("DAO.read() {} -> {}", key, so == null ? null : so.getType());
		return so;
	}

	@Override
	public <P extends ParaObject> void update(String appid, P so) {
		if (so != null && !StringUtils.isBlank(appid)) {
			so.setUpdated(Utils.timestamp());
			ParaObject soUpdated = getMap(appid).get(so.getId());
			Utils.setAnnotatedFields(soUpdated, Utils.getAnnotatedFields(so), Locked.class);
			getMap(appid).put(so.getId(), soUpdated);
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
	public <P extends ParaObject> void createAll(String appid, List<P> objects) {
		if (StringUtils.isBlank(appid) || objects == null) {
			return;
		}
		for (P p : objects) {
			create(appid, p);
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
		List<P> list = search.findByIds(getAppidWithRouting(appid), keys);

		if (list.isEmpty()) {
			for (String key : keys) {
				if (getMap(appid).containsKey(key)) {
					results.put(key, (P) read(key));
				}
			}
		} else {
			for (P p : list) {
				if (p != null) {
					results.put(p.getId(), p);
				}
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
		if (!StringUtils.isBlank(appid) && objects != null) {
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
		if (!StringUtils.isBlank(appid) && objects != null) {
			for (P obj : objects) {
				if (obj != null && getMap(appid).containsKey(obj.getId())) {
					delete(appid, obj);
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

	private String getAppidWithRouting(String appid) {
		return appid;
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
