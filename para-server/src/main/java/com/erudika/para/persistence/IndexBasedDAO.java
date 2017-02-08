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
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the {@link DAO} interface used <b>for local development only</b>.
 * It uses is based on the {@link com.erudika.para.search.Search} implementation.
 * Objects are stored in the index rather than in a data store.
 *
 * <b>Note</b>: This implementation doesn't work well with apps sharing one index (app.isSharingIndex() must be false).
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class IndexBasedDAO implements DAO {

	private static final Logger logger = LoggerFactory.getLogger(IndexBasedDAO.class);
	private Search search;

	/**
	 * No-args constructor.
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
	 * Sets the search instance.
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
		so.setIndexed(false); // skip indexing - already indexed here
		search.index(appid, ParaObjectUtils.setAnnotatedFields(ParaObjectUtils.toObject(so.getType()),
				ParaObjectUtils.getAnnotatedFields(so), null));
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
		logger.debug("DAO.read() {} -> {}", key, so == null ? null : so.getType());
		return so;
	}

	@Override
	public <P extends ParaObject> void update(String appid, P so) {
		if (so != null && !StringUtils.isBlank(appid)) {
			so.setUpdated(Utils.timestamp());
			so.setIndexed(false); // skip indexing - already indexed here
			ParaObject soUpdated = read(appid, so.getId());
			search.index(appid, ParaObjectUtils.setAnnotatedFields(soUpdated,
					ParaObjectUtils.getAnnotatedFields(so), Locked.class));
			logger.debug("DAO.update() {}", so.getId());
		}
	}

	@Override
	public <P extends ParaObject> void delete(String appid, P so) {
		if (so != null && !StringUtils.isBlank(appid)) {
			search.unindex(appid, so);
			logger.debug("DAO.delete() {}", so.getId());
		}
	}

	@Override
	public <P extends ParaObject> void createAll(String appid, List<P> objects) {
		if (StringUtils.isBlank(appid) || objects == null) {
			return;
		}
		Iterator<P> iter = objects.iterator();
		while (iter.hasNext()) {
			P p = iter.next();
			if (p == null) {
				iter.remove();
				continue;
			}
			p.setAppid(appid);
			p.setIndexed(false);
			if (StringUtils.isBlank(p.getId())) {
				p.setId(Utils.getNewId());
			}
			if (p.getTimestamp() == null) {
				p.setTimestamp(Utils.timestamp());
			}
		}
		search.indexAll(appid, objects);
		logger.debug("DAO.createAll() {}", objects.size());
	}

	@Override
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> Map<String, P> readAll(String appid, List<String> keys, boolean getAllColumns) {
		if (keys == null || StringUtils.isBlank(appid)) {
			return Collections.emptyMap();
		}
		Map<String, P> results = new LinkedHashMap<String, P>(keys.size());
		List<P> list = search.findByIds(getAppidWithRouting(appid), keys);

		for (P p : list) {
			if (p != null) {
				results.put(p.getId(), p);
			}
		}

		logger.debug("DAO.readAll() {}", results.size());
		return results;
	}

	@Override
	public <P extends ParaObject> List<P> readPage(String appid, Pager pager) {
		List<P> results = new LinkedList<P>();
		if (StringUtils.isBlank(appid)) {
			return results;
		}
		if (pager == null) {
			pager = new Pager();
		}

		List<P> res = search.findQuery(appid, null, "*", pager);
		for (P obj : res) {
			if (obj != null) {
				results.add(obj);
			}
		}
		if (results.size() > 0) {
			pager.setPage(pager.getPage() + 1);
		}
		return results;
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
			search.unindexAll(appid, objects);
			logger.debug("DAO.deleteAll() {}", objects.size());
		}
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
