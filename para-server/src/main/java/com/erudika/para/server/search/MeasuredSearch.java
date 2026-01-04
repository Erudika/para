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
package com.erudika.para.server.search;

import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.metrics.Metrics;
import static com.erudika.para.core.metrics.Metrics.time;
import com.erudika.para.core.persistence.DAO;
import com.erudika.para.core.search.Search;
import com.erudika.para.core.utils.Pager;
import com.erudika.para.core.utils.Para;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class watches search methods and gathers metrics for each one of them.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class MeasuredSearch implements Search {

	private static final Logger logger = LoggerFactory.getLogger(MeasuredSearch.class);

	private final Search search;

	public MeasuredSearch(Search search) {
		this.search = Objects.requireNonNull(search, "Search implementation not provided.");
	}

	@Override
	public void index(ParaObject po) {
		index(Para.getConfig().getRootAppIdentifier(), po);
	}

	@Override
	public void index(String appid, ParaObject po) {
		search.index(appid, po);
	}

	@Override
	public void unindex(ParaObject po) {
		unindex(Para.getConfig().getRootAppIdentifier(), po);
	}

	@Override
	public void unindex(String appid, ParaObject po) {
		search.unindex(appid, po);
	}

	@Override
	public <P extends ParaObject> void indexAll(List<P> objects) {
		indexAll(Para.getConfig().getRootAppIdentifier(), objects);
	}

	@Override
	public <P extends ParaObject> void indexAll(String appid, List<P> objects) {
		search.indexAll(appid, objects);
	}

	@Override
	public <P extends ParaObject> void unindexAll(List<P> objects) {
		unindexAll(Para.getConfig().getRootAppIdentifier(), objects);
	}

	@Override
	public <P extends ParaObject> void unindexAll(String appid, List<P> objects) {
		search.unindexAll(appid, objects);
	}

	@Override
	public void unindexAll(Map<String, ?> terms, boolean matchAll) {
		unindexAll(Para.getConfig().getRootAppIdentifier(), terms, matchAll);
	}

	@Override
	public void unindexAll(String appid, Map<String, ?> terms, boolean matchAll) {
		search.unindexAll(appid, terms, matchAll);
	}

	@Override
	public <P extends ParaObject> P findById(String id) {
		return findById(Para.getConfig().getRootAppIdentifier(), id);
	}

	@Override
	public <P extends ParaObject> P findById(String appid, String id) {
		try (Metrics.Context context = time(appid, search.getClass(), "findById")) {
			return search.findById(appid, id);
		}
	}

	@Override
	public <P extends ParaObject> List<P> findByIds(List<String> ids) {
		return findByIds(Para.getConfig().getRootAppIdentifier(), ids);
	}

	@Override
	public <P extends ParaObject> List<P> findByIds(String appid, List<String> ids) {
		try (Metrics.Context context = time(appid, search.getClass(), "findByIds")) {
			return search.findByIds(appid, ids);
		}
	}

	@Override
	public <P extends ParaObject> List<P> findNearby(String type, String query, int radius, double lat, double lng, Pager... pager) {
		return findNearby(Para.getConfig().getRootAppIdentifier(), type, query, radius, lat, lng, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findNearby(String appid, String type, String query, int radius, double lat, double lng, Pager... pager) {
		try (Metrics.Context context = time(appid, search.getClass(), "findNearby")) {
			return search.findNearby(appid, type, query, radius, lat, lng, pager);
		}
	}

	@Override
	public <P extends ParaObject> List<P> findPrefix(String type, String field, String prefix, Pager... pager) {
		return findPrefix(Para.getConfig().getRootAppIdentifier(), type, field, prefix, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findPrefix(String appid, String type, String field, String prefix, Pager... pager) {
		try (Metrics.Context context = time(appid, search.getClass(), "findPrefix")) {
			return search.findPrefix(appid, type, field, prefix, pager);
		}
	}

	@Override
	public <P extends ParaObject> List<P> findQuery(String type, String query, Pager... pager) {
		return findQuery(Para.getConfig().getRootAppIdentifier(), type, query, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findQuery(String appid, String type, String query, Pager... pager) {
		try (Metrics.Context context = time(appid, search.getClass(), "findQuery")) {
			return search.findQuery(appid, type, query, pager);
		}
	}

	@Override
	public <P extends ParaObject> List<P> findNestedQuery(String type, String field, String query, Pager... pager) {
		return findNestedQuery(Para.getConfig().getRootAppIdentifier(), type, field, query, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findNestedQuery(String appid, String type, String field, String query, Pager... pager) {
		try (Metrics.Context context = time(appid, search.getClass(), "findNestedQuery")) {
			return search.findNestedQuery(appid, type, field, query, pager);
		}
	}

	@Override
	public <P extends ParaObject> List<P> findSimilar(String type, String filterKey, String[] fields, String liketext, Pager... pager) {
		return findSimilar(Para.getConfig().getRootAppIdentifier(), type, filterKey, fields, liketext, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findSimilar(String appid, String type, String filterKey, String[] fields, String liketext, Pager... pager) {
		try (Metrics.Context context = time(appid, search.getClass(), "findSimilar")) {
			return search.findSimilar(appid, type, filterKey, fields, liketext, pager);
		}
	}

	@Override
	public <P extends ParaObject> List<P> findTagged(String type, String[] tags, Pager... pager) {
		return findTagged(Para.getConfig().getRootAppIdentifier(), type, tags, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTagged(String appid, String type, String[] tags, Pager... pager) {
		try (Metrics.Context context = time(appid, search.getClass(), "findTagged")) {
			return search.findTagged(appid, type, tags, pager);
		}
	}

	@Override
	public <P extends ParaObject> List<P> findTags(String keyword, Pager... pager) {
		return findTags(Para.getConfig().getRootAppIdentifier(), keyword, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTags(String appid, String keyword, Pager... pager) {
		try (Metrics.Context context = time(appid, search.getClass(), "findTags")) {
			return search.findTags(appid, keyword, pager);
		}
	}

	@Override
	public <P extends ParaObject> List<P> findTermInList(String type, String field, List<?> terms, Pager... pager) {
		return findTermInList(Para.getConfig().getRootAppIdentifier(), type, field, terms, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTermInList(String appid, String type, String field, List<?> terms, Pager... pager) {
		try (Metrics.Context context = time(appid, search.getClass(), "findTermInList")) {
			return search.findTermInList(appid, type, field, terms, pager);
		}
	}

	@Override
	public <P extends ParaObject> List<P> findTerms(String type, Map<String, ?> terms, boolean matchAll, Pager... pager) {
		return findTerms(Para.getConfig().getRootAppIdentifier(), type, terms, matchAll, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTerms(String appid, String type, Map<String, ?> terms, boolean matchAll, Pager... pager) {
		try (Metrics.Context context = time(appid, search.getClass(), "findTerms")) {
			return search.findTerms(appid, type, terms, matchAll, pager);
		}
	}

	@Override
	public <P extends ParaObject> List<P> findWildcard(String type, String field, String wildcard, Pager... pager) {
		return findWildcard(Para.getConfig().getRootAppIdentifier(), type, field, wildcard, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findWildcard(String appid, String type, String field, String wildcard, Pager... pager) {
		try (Metrics.Context context = time(appid, search.getClass(), "findWildcard")) {
			return search.findWildcard(appid, type, field, wildcard, pager);
		}
	}

	@Override
	public Long getCount(String type) {
		return getCount(Para.getConfig().getRootAppIdentifier(), type);
	}

	@Override
	public Long getCount(String appid, String type) {
		try (Metrics.Context context = time(appid, search.getClass(), "getCount")) {
			return search.getCount(appid, type);
		}
	}

	@Override
	public Long getCount(String type, Map<String, ?> terms) {
		return getCount(Para.getConfig().getRootAppIdentifier(), type, terms);
	}

	@Override
	public Long getCount(String appid, String type, Map<String, ?> terms) {
		try (Metrics.Context context = time(appid, search.getClass(), "getCount")) {
			return search.getCount(appid, type, terms);
		}
	}

	@Override
	public boolean rebuildIndex(DAO dao, App app, Pager... pager) {
		String appid = app != null ? app.getAppIdentifier() : Para.getConfig().getRootAppIdentifier();
		try (Metrics.Context context = time(appid, search.getClass(), "rebuildIndex")) {
			return search.rebuildIndex(dao, app, pager);
		}
	}

	@Override
	public boolean rebuildIndex(DAO dao, App app, String destinationIndex, Pager... pager) {
		String appid = app != null ? app.getAppIdentifier() : Para.getConfig().getRootAppIdentifier();
		try (Metrics.Context context = time(appid, search.getClass(), "rebuildIndex")) {
			return search.rebuildIndex(dao, app, destinationIndex, pager);
		}
	}

	@Override
	public boolean isValidQueryString(String queryString) {
		return search.isValidQueryString(queryString);
	}

	@Override
	public void createIndex(App app) {
		String appid = app != null ? app.getAppIdentifier() : Para.getConfig().getRootAppIdentifier();
		try (Metrics.Context context = time(appid, search.getClass(), "createIndex")) {
			search.createIndex(app);
		}
	}

	@Override
	public void deleteIndex(App app) {
		String appid = app != null ? app.getAppIdentifier() : Para.getConfig().getRootAppIdentifier();
		try (Metrics.Context context = time(appid, search.getClass(), "deleteIndex")) {
			search.deleteIndex(app);
		}
	}

	@Override
	public String getSearchClassName() {
		return search == null ? MeasuredSearch.class.getSimpleName() : search.getClass().getSimpleName();
	}

}
