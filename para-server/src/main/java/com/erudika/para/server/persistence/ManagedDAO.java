/*
 * Copyright 2013-2025 Erudika. http://erudika.com
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
package com.erudika.para.server.persistence;

import com.erudika.para.core.ParaObject;
import com.erudika.para.core.listeners.IOListener;
import com.erudika.para.core.metrics.Metrics;
import com.erudika.para.core.persistence.DAO;
import com.erudika.para.core.utils.Pager;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.validation.ValidationUtils;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Managed Para DAO wrapper, which automatically calls the DAO functions, indexes and caches objects.
 * It also retrieves objects from cache if possible before hitting the DB.
 *
 * @see {@link com.erudika.para.core.persistence.DAO} interface.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ManagedDAO implements DAO {

	private static final Logger logger = LoggerFactory.getLogger(ManagedDAO.class);

	private final DAO dao;
	private final Set<IOListener> ioListeners;


	public ManagedDAO(DAO dao) {
		this.dao = Objects.requireNonNull(dao, "DAO implementation not provided.");
		this.ioListeners = Para.getIOListeners();
	}

	<P extends ParaObject> P invokeDAORead(String appid, String key,
			BiFunction<String, String, P> daoFunction, String opName) {
		try (Metrics.Context context = Metrics.time(appid, dao.getClass(), opName)) {
			Object[] args = new Object[] {appid, key};
			Class<?>[] params = new Class<?>[] {String.class, String.class};

			onPreInvoke(opName, args, params);
			P result = daoFunction.apply(appid, key);
			onPostInvoke(opName, args, result, params);

			return result;
		}
	}

	<P extends ParaObject, R> R invokeDAOWrite(String appid, P object,
			BiFunction<String, P, R> daoFunction, String opName) {
		try (Metrics.Context context = Metrics.time(appid, dao.getClass(), opName)) {
			Object[] args = new Object[] {appid, object};
			Class<?>[] params = new Class<?>[] {String.class, ParaObject.class};

			onPreInvoke(opName, args, params);
			R result = daoFunction.apply(appid, object);
			onPostInvoke(opName, args, result, params);

			return result;
		}
	}

	<P extends ParaObject, R> R invokeDAOBatchWrite(String appid, List<P> objects,
			BiFunction<String, List<P>, R> daoFunction, String opName) {
		try (Metrics.Context context = Metrics.time(appid, dao.getClass(), opName)) {
			Object[] args = new Object[] {appid, objects};
			Class<?>[] params = new Class<?>[] {String.class, List.class};

			onPreInvoke(opName, args, params);
			R result = daoFunction.apply(appid, objects);
			onPostInvoke(opName, args, result, params);

			return result;
		}
	}

	<P extends ParaObject> Map<String, P> invokeDAOBatchRead(String appid, List<String> keys,
			BiFunction<String, List<String>, Map<String, P>> daoFunction, String opName) {
		try (Metrics.Context context = Metrics.time(appid, dao.getClass(), opName)) {
			Object[] args = new Object[] {appid, keys};
			Class<?>[] params = new Class<?>[] {String.class, List.class, Boolean.class};

			onPreInvoke(opName, args, params);
			Map<String, P> result = daoFunction.apply(appid, keys);
			onPostInvoke(opName, args, result, params);

			return result;
		}
	}

	void onPreInvoke(String opName, Object[] args, Class<?>... params) {
		try {
			for (IOListener ioListener : ioListeners) {
				ioListener.onPreInvoke(DAO.class.getMethod(opName, params), args);
				logger.debug("Executed {}.onPreInvoke().", ioListener.getClass().getName());
			}
		} catch (NoSuchMethodException e) {
			logger.error(null, e);
		}
	}

	void onPostInvoke(String opName, Object[] args, Object result, Class<?>... params) {
		try {
			for (IOListener ioListener : ioListeners) {
				ioListener.onPostInvoke(DAO.class.getMethod(opName, params), args, result);
				logger.debug("Executed {}.onPostInvoke().", ioListener.getClass().getName());
			}
		} catch (NoSuchMethodException e) {
			logger.error(null, e);
		}
	}

	<P extends ParaObject, R> R addToIndexAndCache(String appid, P obj,
			BiFunction<String, P, R> daoFunction, String opName) {
		String[] errors = ValidationUtils.validateObject(obj);
		R result = null;
		if (obj != null && errors.length == 0) {
			ParaObjectUtils.checkAndFixType(obj);
			if (obj.getStored()) {
				result = invokeDAOWrite(appid, obj, daoFunction, opName);
				if (obj.getVersion() == -1) {
					logger.warn("DAO operation failed for object '{}' due to version mismatch. "
							+ "Indexing and caching will be skipped.", obj.getId());
				}
			}
			if (obj.getIndexed() && obj.getVersion() >= 0 && Para.getConfig().isSearchEnabled()) {
				try (Metrics.Context context = Metrics.time(appid, Para.getSearch().getClass(), "index")) {
					Para.getSearch().index(appid, obj);
					logger.debug("Search: Indexed {}->{}", appid, obj.getId());
				}
			}
			if (obj.getCached() && obj.getVersion() >= 0 && Para.getConfig().isCacheEnabled()) {
				try (Metrics.Context context = Metrics.time(appid, Para.getCache().getClass(), "put")) {
					Para.getCache().put(appid, obj.getId(), obj);
				}
				logger.debug("Cache: Cache put: {}->{}", appid, obj.getId());
			}
		} else {
			logger.warn("DAO: Invalid object {}->{} errors: [{}]. Changes weren't persisted.",
					appid, obj, String.join("; ", errors));
		}
		return result;
	}

	<P extends ParaObject> void addAllToIndexAndCache(String appid, List<P> objects,
			BiFunction<String, List<P>, Void> daoFunction, String opName) {
		if (objects == null || objects.isEmpty()) {
			return;
		}
		objects = objects.stream().map(o -> ParaObjectUtils.checkAndFixType(o)).toList();
		invokeDAOBatchWrite(appid, objects.stream().filter(o -> o != null && o.getStored()).toList(), daoFunction, opName);
		if (Para.getConfig().isSearchEnabled()) {
			try (Metrics.Context context = Metrics.time(appid, Para.getSearch().getClass(), "indexAll")) {
				Para.getSearch().indexAll(appid, objects.stream().filter(o -> o != null && o.getIndexed() && o.getVersion() >= 0).toList());
			}
			logger.debug("Search: Indexed all {}->{}", appid, objects.size());
		}
		Map<String, ParaObject> toCache = objects.stream().
				filter(o -> o != null && o.getCached() && o.getVersion() >= 0).
				collect(Collectors.toMap(k -> k.getId(), v -> v));
		if (!toCache.isEmpty() && Para.getConfig().isCacheEnabled()) {
			try (Metrics.Context context = Metrics.time(appid, Para.getCache().getClass(), "putAll")) {
				Para.getCache().putAll(appid, toCache);
			}
			logger.debug("Cache: Cache put page: {}->{}", appid, toCache.keySet());
		}
	}

	<P extends ParaObject, R> P readFromCachOrDB(String appid, String key,
			BiFunction<String, String, P> daoFunction, String opName) {
		P result = null;
		if (Para.getConfig().isCacheEnabled()) {
			try (Metrics.Context context = Metrics.time(appid, Para.getCache().getClass(), "get")) {
				result = Para.getCache().get(appid, key);
			}
		}
		if (result != null) {
			logger.debug("Cache: Cache hit: {}->{}", appid, key);
		} else if (key != null) {
			result = invokeDAORead(appid, key, daoFunction, opName);
			if (result != null && ((ParaObject) result).getCached()) {
				try (Metrics.Context context = Metrics.time(appid, Para.getCache().getClass(), "put")) {
					Para.getCache().put(appid, key, result);
				}
				logger.debug("Cache: Cache miss: {}->{}", appid, key);
			}
		}
		return result;
	}

	<P extends ParaObject, R> Map<String, P> readAllFromCacheOrDB(String appid, List<String> keys,
			BiFunction<String, List<String>, Map<String, P>> daoFunction, String opName) {
		if (keys == null || keys.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, P> fromCache;
		if (Para.getConfig().isCacheEnabled()) {
			try (Metrics.Context context = Metrics.time(appid, Para.getCache().getClass(), "getAll")) {
				fromCache = Para.getCache().getAll(appid, keys);
			}
			logger.debug("Cache: Cache getAll(): {}->{}", appid, keys);
		} else {
			fromCache = new LinkedHashMap<>(0);
		}
		// hit the database if even a single object is missing from cache, then cache it
		if (fromCache.size() < keys.size()) {
			logger.debug("Cache: Cache getAll() will read from DB: {}", appid);
			List<String> notCachedKeys = keys.stream().filter(k -> !fromCache.containsKey(k)).toList();
			Map<String, P> fromDB = invokeDAOBatchRead(appid, notCachedKeys, daoFunction, opName);
			if (fromDB == null || fromDB.isEmpty()) {
				return fromCache;
			}
			Map<String, ParaObject> toCache = fromDB.values().stream().
					filter(o -> o != null && o.getCached() && o.getVersion() >= 0).
					collect(Collectors.toMap(k -> k.getId(), v -> v));
			if (!toCache.isEmpty() && Para.getConfig().isCacheEnabled()) {
				logger.debug("Cache: Cache miss on readAll: {}->{}", appid, toCache.keySet());
				try (Metrics.Context context = Metrics.time(appid, Para.getCache().getClass(), "putAll")) {
					Para.getCache().putAll(appid, toCache);
				}
			}
			Map<String, P> combinedResults = new LinkedHashMap<>();
			keys.forEach(key -> combinedResults.put(key, fromCache.computeIfAbsent(key, k -> fromDB.get(k))));
			return combinedResults;
		}
		return fromCache;
	}

	<P extends ParaObject> void removeFromIndexAndCache(String appid, P removeMe,
			BiFunction<String, P, Void> daoFunction, String opName) {
		invokeDAOWrite(appid, removeMe, daoFunction, opName);  // delete from DB even if "isStored = false"
		ParaObjectUtils.checkAndFixType(removeMe);
		if (removeMe != null) { // clear from cache even if "isCached = false"
			if (Para.getConfig().isSearchEnabled()) {
				try (Metrics.Context context = Metrics.time(appid, Para.getSearch().getClass(), "unindex")) {
					Para.getSearch().unindex(appid, removeMe); // remove from index even if "isIndexed = false"
					logger.debug("Search: Unindexed {}->{}", appid, removeMe.getId());
				}
			}
			if (Para.getConfig().isCacheEnabled()) {
				try (Metrics.Context context = Metrics.time(appid, Para.getCache().getClass(), "remove")) {
					Para.getCache().remove(appid, removeMe.getId());
				}
				logger.debug("Cache: Cache delete: {}->{}", appid, removeMe.getId());
			}
		}
	}

	<P extends ParaObject> void removeAllFromIndexAndCache(String appid, List<P> objects,
			BiFunction<String, List<P>, Void> daoFunction, String opName) {
		if (objects == null || objects.isEmpty()) {
			return;
		}
		invokeDAOBatchWrite(appid, objects, daoFunction, opName); // delete from DB even if "isStored = false"
		if (Para.getConfig().isSearchEnabled()) { // remove from index even if "isIndexed = false"
			try (Metrics.Context context = Metrics.time(appid, Para.getSearch().getClass(), "unindexAll")) {
				Para.getSearch().unindexAll(appid, objects);
			}
			logger.debug("Search: Unindexed all {}->{}", appid, objects.size());
		}
		if (Para.getConfig().isCacheEnabled()) { // clear from cache even if "isCached = false"
			try (Metrics.Context context = Metrics.time(appid, Para.getCache().getClass(), "removeAll")) {
				Para.getCache().removeAll(appid, objects.stream().map(o -> o.getId()).distinct().toList());
			}
			logger.debug("Cache: Cache delete page: {}->{}", appid, objects);
		}
	}

	@Override
	public <P extends ParaObject> String create(String appid, P object) {
		return addToIndexAndCache(appid, object, (aid, pobj) -> dao.create(aid, pobj), "create");
	}

	@Override
	public <P extends ParaObject> String create(P object) {
		return create(Para.getConfig().getRootAppIdentifier(), object);
	}

	@Override
	public <P extends ParaObject> P read(String appid, String key) {
		return readFromCachOrDB(appid, key, (aid, pobj) -> dao.read(appid, key), appid);
	}

	@Override
	public <P extends ParaObject> P read(String key) {
		return read(Para.getConfig().getRootAppIdentifier(), key);
	}

	@Override
	public <P extends ParaObject> void update(String appid, P object) {
		addToIndexAndCache(appid, object, (aid, pobj) -> {
			dao.update(aid, pobj);
			return null;
		}, "update");
	}

	@Override
	public <P extends ParaObject> void update(P object) {
		update(Para.getConfig().getRootAppIdentifier(), object);
	}

	@Override
	public <P extends ParaObject> void delete(String appid, P object) {
		removeFromIndexAndCache(appid, object, (aid, pobj) -> {
			dao.delete(aid, pobj);
			return null;
		}, "delete");
	}

	@Override
	public <P extends ParaObject> void delete(P object) {
		delete(Para.getConfig().getRootAppIdentifier(), object);
	}

	@Override
	public <P extends ParaObject> void createAll(String appid, List<P> objects) {
		addAllToIndexAndCache(appid, objects, (aid, list) -> {
			dao.createAll(aid, list);
			return null;
		}, "createAll");
	}

	@Override
	public <P extends ParaObject> void createAll(List<P> objects) {
		createAll(Para.getConfig().getRootAppIdentifier(), objects);
	}

	@Override
	public <P extends ParaObject> Map<String, P> readAll(String appid, List<String> keys, boolean getAllColumns) {
		return readAllFromCacheOrDB(appid, keys, (aid, keyz) -> dao.readAll(aid, keyz, getAllColumns), "readAll");
	}

	@Override
	public <P extends ParaObject> Map<String, P> readAll(List<String> keys, boolean getAllColumns) {
		return readAll(Para.getConfig().getRootAppIdentifier(), keys, getAllColumns);
	}

	@Override
	public <P extends ParaObject> List<P> readPage(String appid, Pager pager) {
		return dao.readPage(appid, pager);
	}

	@Override
	public <P extends ParaObject> List<P> readPage(Pager pager) {
		return readPage(Para.getConfig().getRootAppIdentifier(), pager);
	}

	@Override
	public <P extends ParaObject> void updateAll(String appid, List<P> objects) {
		addAllToIndexAndCache(appid, objects, (aid, list) -> {
			dao.updateAll(aid, list);
			return null;
		}, "updateAll");
	}

	@Override
	public <P extends ParaObject> void updateAll(List<P> objects) {
		updateAll(Para.getConfig().getRootAppIdentifier(), objects);
	}

	@Override
	public <P extends ParaObject> void deleteAll(String appid, List<P> objects) {
		removeAllFromIndexAndCache(appid, objects, (aid, list) -> {
			dao.deleteAll(aid, list);
			return null;
		}, "deleteAll");
	}

	@Override
	public <P extends ParaObject> void deleteAll(List<P> objects) {
		deleteAll(Para.getConfig().getRootAppIdentifier(), objects);
	}

	@Override
	public String toString() {
		return "ParaManagedDAO{" + dao + "}";
	}

	@Override
	public String getDaoClassName() {
		return dao == null ? ManagedDAO.class.getSimpleName() : dao.getClass().getSimpleName();
	}

}
