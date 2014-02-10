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
package com.erudika.para.search;

import com.erudika.para.annotations.Stored;
import com.erudika.para.persistence.DAO;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Address;
import com.erudika.para.core.PObject;
import com.erudika.para.core.Tag;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.AndFilterBuilder;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the {@link Search} interface using ElasticSearch.
 * @author Alex Bogdanovski <alex@erudika.com>
 */
@Singleton
public class ElasticSearch implements Search {

	private static final Logger logger = LoggerFactory.getLogger(ElasticSearch.class);
	private static final String DEFAULT_SORT = Config._TIMESTAMP;
	private DAO dao;

	/**
	 * Default constructor.
	 * @param dao an instance of the persistence class
	 */
	@Inject
	public ElasticSearch(DAO dao) {
		this.dao = dao;
	}

	Client client() {
		return ElasticSearchUtils.getClient();
	}

	@Override
	public void index(String appName, ParaObject so) {
		index(appName, so, 0);
	}

	@Override
	public void index(String appName, ParaObject so, long ttl) {
		if (so == null || StringUtils.isBlank(appName)) {
			return;
		}
		Map<String, Object> data = Utils.getAnnotatedFields(so, Stored.class, null);
		try {
			IndexRequestBuilder irb = client().prepareIndex(appName,
					so.getClassname(), so.getId()).setSource(data);
			if (ttl > 0) {
				irb.setTTL(ttl);
			}
			irb.execute().actionGet();
			logger.debug("Search.index() {}", so.getId());
		} catch (Exception e) {
			logger.warn(null, e);
		}
	}

	@Override
	public void unindex(String appName, ParaObject so) {
		if (so == null || StringUtils.isBlank(so.getId()) || StringUtils.isBlank(appName)) {
			return;
		}
		try {
			client().prepareDelete(appName, so.getClassname(), so.getId()).execute().actionGet();
			logger.debug("Search.unindex() {}", so.getId());
		} catch (Exception e) {
			logger.warn(null, e);
		}
	}

	@Override
	public <P extends ParaObject> void indexAll(String appName, List<P> objects) {
		if (objects == null || StringUtils.isBlank(appName)) {
			return ;
		}
		BulkRequestBuilder brb = client().prepareBulk();
		for (ParaObject pObject : objects) {
			brb.add(client().prepareIndex(appName, pObject.getClassname(),
						pObject.getId()).setSource(Utils.getAnnotatedFields(pObject, Stored.class, null)));
		}
		brb.execute().actionGet();
		logger.debug("Search.indexAll() {}", objects.size());
	}

	@Override
	public <P extends ParaObject> void unindexAll(String appName, List<P> objects) {
		if (objects == null || StringUtils.isBlank(appName)) {
			return ;
		}
		BulkRequestBuilder brb = client().prepareBulk();
		for (ParaObject pObject : objects) {
			brb.add(client().prepareDelete(appName, pObject.getClassname(), pObject.getId()));
		}
		brb.execute().actionGet();
		logger.debug("Search.unindexAll() {}", objects.size());
	}

	@Override
	public <P extends ParaObject> P findById(String appName, String id, String type) {
		try {
			return Utils.setAnnotatedFields(getSource(appName, id, type));
		} catch (Exception e) {
			logger.warn(null, e);
			return null;
		}
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTermInList(String appName, String type,
			String field, List<?> terms, Pager... pager) {
		if (StringUtils.isBlank(field) || terms == null) {
			return new ArrayList<P>();
		}
		QueryBuilder qb = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
				FilterBuilders.termsFilter(field, terms));
		return searchQuery(appName, type, qb, pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findPrefix(String appName, String type,
			String field, String prefix, Pager... pager) {
		if (StringUtils.isBlank(field) || StringUtils.isBlank(prefix)) {
			return new ArrayList<P>();
		}
		return searchQuery(appName, type, QueryBuilders.prefixQuery(field, prefix), pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findQuery(String appName, String type,
			String query, Pager... pager) {
		if (StringUtils.isBlank(query)) {
			return new ArrayList<P>();
		}
		QueryBuilder qb = QueryBuilders.queryString(query).allowLeadingWildcard(false);
		return searchQuery(appName, type, qb, pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findWildcard(String appName, String type,
			String field, String wildcard, Pager... pager) {
		if (StringUtils.isBlank(field) || StringUtils.isBlank(wildcard)) {
			return new ArrayList<P>();
		}
		QueryBuilder qb = QueryBuilders.wildcardQuery(field, wildcard);
		return searchQuery(appName, type, qb, pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTagged(String appName, String type,
			String[] tags, Pager... pager) {
		if (tags == null || tags.length == 0 || StringUtils.isBlank(appName)) {
			return new ArrayList<P>();
		}

		BoolFilterBuilder tagFilter = FilterBuilders.boolFilter();
		//assuming clean & safe tags here
		for (String tag : tags) {
			tagFilter.must(FilterBuilders.termFilter(Config._TAGS, tag));
		}
		QueryBuilder qb = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), tagFilter);
		// The filter looks like this: ("tag1" OR "tag2" OR "tag3") AND "type"
		return searchQuery(appName, type, qb, pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTerms(String appName, String type,
			Map<String, ?> terms, boolean mustMatchAll, Pager... pager) {
		if (terms == null || terms.isEmpty()) {
			return new ArrayList<P>();
		}
		FilterBuilder fb = null;
		boolean noop = true;
		if (terms.size() == 1) {
			String field = terms.keySet().iterator().next();
			if (!StringUtils.isBlank(field) && terms.get(field) != null) {
				fb = FilterBuilders.termFilter(field, terms.get(field));
				noop = false;
			}
		} else {
			if (mustMatchAll) {
				fb = FilterBuilders.andFilter();
				for (Map.Entry<String, ?> term : terms.entrySet()) {
					if (!StringUtils.isBlank(term.getKey()) && term.getValue() != null) {
						((AndFilterBuilder) fb).add(FilterBuilders.termFilter(term.getKey(), term.getValue()));
						noop = false;
					}
				}
			} else {
				fb = FilterBuilders.orFilter();
				for (Map.Entry<String, ?> term : terms.entrySet()) {
					if (!StringUtils.isBlank(term.getKey()) && term.getValue() != null) {
						((OrFilterBuilder) fb).add(FilterBuilders.termFilter(term.getKey(), term.getValue()));
						noop = false;
					}
				}
			}
		}
		if (noop) {
			return new ArrayList<P>();
		} else {
			QueryBuilder qb = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), fb);
			return searchQuery(appName, type, qb, pager);
		}
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findSimilar(String appName, String type, String filterKey,
			String[] fields, String liketext, Pager... pager) {
		if (StringUtils.isBlank(liketext)) {
			return new ArrayList<P>();
		}
		QueryBuilder qb;
		FilterBuilder fb;

		if (fields == null || fields.length == 0) {
			qb = QueryBuilders.moreLikeThisQuery().likeText(liketext).minDocFreq(1).minTermFreq(1);
		} else {
			qb = QueryBuilders.moreLikeThisQuery(fields).likeText(liketext).minDocFreq(1).minTermFreq(1);
		}

		if (!StringUtils.isBlank(filterKey)) {
			fb = FilterBuilders.notFilter(FilterBuilders.inFilter(Config._ID, filterKey));
			qb = QueryBuilders.filteredQuery(qb, fb);
		}
		return searchQuery(appName, searchQueryRaw(appName, type, qb, pager));
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTags(String appName, String keyword, Pager... pager) {
		if (StringUtils.isBlank(keyword)) {
			return new ArrayList<P>();
		}
		QueryBuilder qb = QueryBuilders.wildcardQuery(PObject.classname(Tag.class), keyword.concat("*"));
//		SortBuilder sb = SortBuilders.fieldSort("count").order(SortOrder.DESC);
		return searchQuery(appName, PObject.classname(Tag.class), qb, pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findNearby(String appName, String type,
		String query, int radius, double lat, double lng, Pager... pager) {

		if (StringUtils.isBlank(type) || StringUtils.isBlank(appName)) {
			return new ArrayList<P>();
		}
		if (StringUtils.isBlank(query)) {
			query = "*";
		}
		// find nearby Address objects
		QueryBuilder qb1 = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
				FilterBuilders.geoDistanceFilter("latlng").point(lat, lng).
				distance(radius, DistanceUnit.KILOMETERS));

		SearchHits hits1 = searchQueryRaw(appName, PObject.classname(Address.class), qb1, pager);

		if (hits1 == null) {
			return new ArrayList<P>();
		}

		// then find their parent objects
		String[] ridsarr = new String[(int) hits1.getTotalHits()];
		for (int i = 0; i < hits1.getTotalHits(); i++) {
			Object pid = hits1.getAt(i).getSource().get(Config._PARENTID);
			if (pid != null) {
				ridsarr[i] = pid.toString();
			}
		}

		QueryBuilder qb2 = QueryBuilders.filteredQuery(QueryBuilders.queryString(query),
				FilterBuilders.idsFilter(type).ids(ridsarr));
		SearchHits hits2 = searchQueryRaw(appName, type, qb2, pager);

		return searchQuery(appName, hits2);
	}

	private <P extends ParaObject> ArrayList<P> searchQuery(String appName, String type,
			QueryBuilder query, Pager... pager) {
		return searchQuery(appName, searchQueryRaw(appName, type, query, pager));
	}

	private <P extends ParaObject> ArrayList<P> searchQuery(String appName, SearchHits hits) {
		ArrayList<P> results = new ArrayList<P>();
		ArrayList<String> keys = new ArrayList<String>();

		if (hits == null) {
			return new ArrayList<P>();
		}
		try {
			for (SearchHit hit : hits) {
				keys.add(hit.getId());
				if (Config.READ_FROM_INDEX) {
					P pobj = Utils.setAnnotatedFields(hit.getSource());
					results.add(pobj);
				}
			}

			if (!Config.READ_FROM_INDEX) {
				Map<String, P> fromDB = dao.readAll(appName, keys, true);
				results.addAll(fromDB.values());
			}
			logger.debug("Search.searchQuery() {}", results.size());
		} catch (Exception e) {
			logger.warn(null, e);
		}
		return results;
	}

	private SearchHits searchQueryRaw(String appName, String type, QueryBuilder query, Pager... pager) {
		if (StringUtils.isBlank(type) || StringUtils.isBlank(appName)) {
			return null;
		}
		Pager page = (pager != null && pager.length > 0) ? pager[0] : new Pager();
		SortOrder order = page.isDesc() ? SortOrder.DESC : SortOrder.ASC;
		SortBuilder sort = StringUtils.isBlank(page.getSortby()) ?
				SortBuilders.scoreSort() : SortBuilders.fieldSort(page.getSortby()).order(order);

		int max = page.getLimit();
		int pageNum = (int) page.getPage();
		int start = (pageNum < 1 || pageNum > Config.MAX_PAGES) ? 0 : (pageNum - 1) * max;

		if (query == null) {
			query = QueryBuilders.matchAllQuery();
		}
		if (sort == null) {
			sort = SortBuilders.scoreSort();
		}

		SearchHits hits = null;

		try {
			SearchResponse response = client().prepareSearch(appName).
					setSearchType(SearchType.DFS_QUERY_THEN_FETCH).setTypes(type).
					setQuery(query).addSort(sort).setFrom(start).setSize(max).
					execute().actionGet();

			hits = response.getHits();
			page.setCount(hits.getTotalHits());
		} catch (Exception e) {
			logger.warn(null, e);
		}

		return hits;
	}

	/**
	 *
	 * @param appName
	 * @param key
	 * @param type
	 * @return
	 */
	protected Map<String, Object> getSource(String appName, String key, String type) {
		Map<String, Object> map = new HashMap<String, Object>();
		if (StringUtils.isBlank(key) || StringUtils.isBlank(type) || StringUtils.isBlank(appName)) {
			return map;
		}
		try {
			GetResponse resp = client().prepareGet().setIndex(appName).
					setId(key).setType(type).execute().actionGet();
			map = resp.getSource();
		} catch (Exception e) {
			logger.warn(null, e);
		}
		return map;
	}

	@Override
	public Long getCount(String appName, String type) {
		if (StringUtils.isBlank(type) || StringUtils.isBlank(appName)) {
			return 0L;
		}
		return client().prepareCount(appName).
				setTypes(type).setQuery(QueryBuilders.matchAllQuery()).
				execute().actionGet().getCount();
	}

	@Override
	public Long getCount(String appName, String type, Map<String, ?> terms) {
		if (StringUtils.isBlank(type) || StringUtils.isBlank(appName) || terms == null || terms.isEmpty()) {
			return 0L;
		}
		FilterBuilder fb;
		if (terms.size() == 1) {
			String field = terms.keySet().iterator().next();
			if (StringUtils.isBlank(field) || terms.get(field) == null) {
				return 0L;
			}
			fb = FilterBuilders.termFilter(field, terms.get(field));
		} else {
			fb = FilterBuilders.andFilter();
			for (Map.Entry<String, ?> term : terms.entrySet()) {
				((AndFilterBuilder) fb).add(FilterBuilders.termFilter(term.getKey(), term.getValue()));
			}
		}
		QueryBuilder qb = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), fb);
		return client().prepareCount(appName).setTypes(type).setQuery(qb).execute().actionGet().getCount();
	}

	//////////////////////////////////////////////////////////////

	@Override
	public void index(ParaObject so) {
		index(Config.APP_NAME_NS, so);
	}

	@Override
	public void unindex(ParaObject so) {
		unindex(Config.APP_NAME_NS, so);
	}

	@Override
	public <P extends ParaObject> void indexAll(List<P> objects) {
		indexAll(Config.APP_NAME_NS, objects);
	}

	@Override
	public <P extends ParaObject> void unindexAll(List<P> objects) {
		unindexAll(Config.APP_NAME_NS, objects);
	}

	@Override
	public <P extends ParaObject> P findById(String id, String type) {
		return findById(Config.APP_NAME_NS, id, type);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findNearby(String type,
			String query, int radius, double lat, double lng, Pager... pager) {
		return findNearby(Config.APP_NAME_NS, type, query, radius, lat, lng, pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findPrefix(String type, String field, String prefix, Pager... pager) {
		return findPrefix(Config.APP_NAME_NS, type, field, prefix, pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findQuery(String type, String query, Pager... pager) {
		return findQuery(Config.APP_NAME_NS, type, query, pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findSimilar(String type, String filterKey, String[] fields,
			String liketext, Pager... pager) {
		return findSimilar(Config.APP_NAME_NS, type, filterKey, fields, liketext, pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTagged(String type, String[] tags, Pager... pager) {
		return findTagged(Config.APP_NAME_NS, type, tags, pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTags(String keyword, Pager... pager) {
		return findTags(Config.APP_NAME_NS, keyword, pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTermInList(String type, String field,
			List<?> terms, Pager... pager) {
		return findTermInList(Config.APP_NAME_NS, type, field, terms, pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTerms(String type, Map<String, ?> terms,
			boolean mustMatchBoth, Pager... pager) {
		return findTerms(Config.APP_NAME_NS, type, terms, mustMatchBoth, pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findWildcard(String type, String field, String wildcard,
			Pager... pager) {
		return findWildcard(Config.APP_NAME_NS, type, field, wildcard, pager);
	}

	@Override
	public Long getCount(String type) {
		return getCount(Config.APP_NAME_NS, type);
	}

	@Override
	public Long getCount(String type, Map<String, ?> terms) {
		return getCount(Config.APP_NAME_NS, type, terms);
	}

}
