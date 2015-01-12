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
package com.erudika.para.search;

import com.erudika.para.core.Address;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Tag;
import com.erudika.para.persistence.DAO;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.deletebyquery.DeleteByQueryRequestBuilder;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.AndFilterBuilder;
import org.elasticsearch.index.query.BaseFilterBuilder;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeFilterBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the {@link Search} interface using ElasticSearch.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class ElasticSearch implements Search {

	private static final Logger logger = LoggerFactory.getLogger(ElasticSearch.class);
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
	public void index(String appid, ParaObject po) {
		index(appid, po, 0);
	}

	@Override
	public void index(String appid, ParaObject po, long ttl) {
		if (po == null || StringUtils.isBlank(appid)) {
			return;
		}
		Map<String, Object> data = Utils.getAnnotatedFields(po, null, false);
		try {
			IndexRequestBuilder irb = client().prepareIndex(getIndexName(appid), po.getType(), po.getId()).
					setSource(data);
			if (ttl > 0) {
				irb.setTTL(ttl);
			}
			if (isAsyncEnabled()) {
				irb.execute();
			} else {
				irb.execute().actionGet();
			}
			logger.debug("Search.index() {}", po.getId());
		} catch (Exception e) {
			logger.warn(null, e);
		}
	}

	@Override
	public void unindex(String appid, ParaObject po) {
		if (po == null || StringUtils.isBlank(po.getId()) || StringUtils.isBlank(appid)) {
			return;
		}
		try {
			DeleteRequestBuilder drb = client().prepareDelete(getIndexName(appid), po.getType(), po.getId());
			if (isAsyncEnabled()) {
				drb.execute();
			} else {
				drb.execute().actionGet();
			}
			logger.debug("Search.unindex() {}", po.getId());
		} catch (Exception e) {
			logger.warn(null, e);
		}
	}

	@Override
	public <P extends ParaObject> void indexAll(String appid, List<P> objects) {
		if (StringUtils.isBlank(appid) || objects == null || objects.isEmpty()) {
			return;
		}
		BulkRequestBuilder brb = client().prepareBulk();
		for (ParaObject po : objects) {
			brb.add(client().prepareIndex(getIndexName(appid), po.getType(), po.getId()).
					setSource(Utils.getAnnotatedFields(po, null, false)));
		}
		if (isAsyncEnabled()) {
			brb.execute();
		} else {
			brb.execute().actionGet();
		}
		logger.debug("Search.indexAll() {}", objects.size());
	}

	@Override
	public <P extends ParaObject> void unindexAll(String appid, List<P> objects) {
		if (StringUtils.isBlank(appid) || objects == null || objects.isEmpty()) {
			return;
		}
		BulkRequestBuilder brb = client().prepareBulk();
		for (ParaObject po : objects) {
			brb.add(client().prepareDelete(getIndexName(appid), po.getType(), po.getId()));
		}
		if (isAsyncEnabled()) {
			brb.execute();
		} else {
			brb.execute().actionGet();
		}
		logger.debug("Search.unindexAll() {}", objects.size());
	}

	@Override
	public void unindexAll(String appid, Map<String, ?> terms, boolean matchAll) {
		if (StringUtils.isBlank(appid) || terms == null || terms.isEmpty()) {
			return;
		}
		DeleteByQueryRequestBuilder dqrb = client().prepareDeleteByQuery(appid);
		dqrb.setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), getTermsFilter(terms, matchAll)));
		if (isAsyncEnabled()) {
			dqrb.execute();
		} else {
			dqrb.execute().actionGet();
		}
		logger.debug("Search.unindexAll() {}", terms.size());
	}

	@Override
	public <P extends ParaObject> P findById(String appid, String id) {
		try {
			return Utils.setAnnotatedFields(getSource(appid, id, null));
		} catch (Exception e) {
			logger.warn(null, e);
			return null;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> findByIds(String appid, List<String> ids) {
		List<P> list = new ArrayList<P>();
		if (ids == null || ids.isEmpty()) {
			return list;
		}
		try {
			MultiGetRequestBuilder mgr = client().prepareMultiGet();
			for (String id : ids) {
				MultiGetRequest.Item i = new MultiGetRequest.Item(getIndexName(appid), null, id);
				mgr.add(i);
			}

			MultiGetResponse response = mgr.execute().actionGet();
			for (MultiGetItemResponse multiGetItemResponse : response.getResponses()) {
				GetResponse res = multiGetItemResponse.getResponse();
				if (res.isExists() && !res.isSourceEmpty()) {
					list.add((P) Utils.setAnnotatedFields(res.getSource()));
				}
			}
		} catch (Exception e) {
			logger.warn(null, e);
		}
		return list;
	}

	@Override
	public <P extends ParaObject> List<P> findTermInList(String appid, String type,
			String field, List<?> terms, Pager... pager) {
		if (StringUtils.isBlank(field) || terms == null) {
			return new ArrayList<P>();
		}
		QueryBuilder qb = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
				FilterBuilders.termsFilter(field, terms));
		return searchQuery(appid, type, qb, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findPrefix(String appid, String type,
			String field, String prefix, Pager... pager) {
		if (StringUtils.isBlank(field) || StringUtils.isBlank(prefix)) {
			return new ArrayList<P>();
		}
		return searchQuery(appid, type, QueryBuilders.prefixQuery(field, prefix), pager);
	}

	@Override
	public <P extends ParaObject> List<P> findQuery(String appid, String type,
			String query, Pager... pager) {
		if (StringUtils.isBlank(query)) {
			return new ArrayList<P>();
		}
		QueryBuilder qb = QueryBuilders.queryString(query).allowLeadingWildcard(false);
		return searchQuery(appid, type, qb, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findWildcard(String appid, String type,
			String field, String wildcard, Pager... pager) {
		if (StringUtils.isBlank(field) || StringUtils.isBlank(wildcard)) {
			return new ArrayList<P>();
		}
		QueryBuilder qb = QueryBuilders.wildcardQuery(field, wildcard);
		return searchQuery(appid, type, qb, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTagged(String appid, String type,
			String[] tags, Pager... pager) {
		if (tags == null || tags.length == 0 || StringUtils.isBlank(appid)) {
			return new ArrayList<P>();
		}

		BoolFilterBuilder tagFilter = FilterBuilders.boolFilter();
		//assuming clean & safe tags here
		for (String tag : tags) {
			tagFilter.must(FilterBuilders.termFilter(Config._TAGS, tag));
		}
		QueryBuilder qb = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), tagFilter);
		// The filter looks like this: ("tag1" OR "tag2" OR "tag3") AND "type"
		return searchQuery(appid, type, qb, pager);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> findTerms(String appid, String type,
			Map<String, ?> terms, boolean mustMatchAll, Pager... pager) {
		if (terms == null || terms.isEmpty()) {
			return new ArrayList<P>();
		}

		FilterBuilder fb = getTermsFilter(terms, mustMatchAll);

		if (fb == null) {
			return new ArrayList<P>();
		} else {
			QueryBuilder qb = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), fb);
			return searchQuery(appid, type, qb, pager);
		}
	}

	@Override
	public <P extends ParaObject> List<P> findSimilar(String appid, String type, String filterKey,
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
		return searchQuery(appid, searchQueryRaw(appid, type, qb, pager));
	}

	@Override
	public <P extends ParaObject> List<P> findTags(String appid, String keyword, Pager... pager) {
		if (StringUtils.isBlank(keyword)) {
			return new ArrayList<P>();
		}
		QueryBuilder qb = QueryBuilders.wildcardQuery("tag", keyword.concat("*"));
//		SortBuilder sb = SortBuilders.fieldSort("count").order(SortOrder.DESC);
		return searchQuery(appid, Utils.type(Tag.class), qb, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findNearby(String appid, String type,
		String query, int radius, double lat, double lng, Pager... pager) {

		if (StringUtils.isBlank(type) || StringUtils.isBlank(appid)) {
			return new ArrayList<P>();
		}
		if (StringUtils.isBlank(query)) {
			query = "*";
		}
		// find nearby Address objects
		QueryBuilder qb1 = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
				FilterBuilders.geoDistanceFilter("latlng").point(lat, lng).
				distance(radius, DistanceUnit.KILOMETERS));

		SearchHits hits1 = searchQueryRaw(appid, Utils.type(Address.class), qb1, pager);

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
		SearchHits hits2 = searchQueryRaw(appid, type, qb2, pager);

		return searchQuery(appid, hits2);
	}

	private <P extends ParaObject> List<P> searchQuery(String appid, String type,
			QueryBuilder query, Pager... pager) {
		return searchQuery(appid, searchQueryRaw(appid, type, query, pager));
	}

	/**
	 * Processes the results of searcQueryRaw() and fetches the results from the data store (can be disabled).
	 * @param <P> type of object
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param hits the search results from a query
	 * @return the list of object found
	 */
	private <P extends ParaObject> List<P> searchQuery(String appid, SearchHits hits) {
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
				Map<String, P> fromDB = dao.readAll(appid, keys, true);
				results.addAll(fromDB.values());
			}
			logger.debug("Search.searchQuery() {}", results.size());
		} catch (Exception e) {
			logger.warn(null, e);
		}
		return results;
	}

	/**
	 * Executes an ElasticSearch query. This is the core method of the class.
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param type type of object
	 * @param query the search query builder
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of search results
	 */
	private SearchHits searchQueryRaw(String appid, String type, QueryBuilder query, Pager... pager) {
		if (StringUtils.isBlank(appid)) {
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
			SearchRequestBuilder srb = client().prepareSearch(getIndexName(appid)).
				setSearchType(SearchType.DFS_QUERY_THEN_FETCH).
				setQuery(query).addSort(sort).setFrom(start).setSize(max);

			if (!StringUtils.isBlank(type)) {
				srb.setTypes(type);
			}

			hits = srb.execute().actionGet().getHits();
			page.setCount(hits.getTotalHits());
		} catch (Exception e) {
			logger.warn(null, e);
		}

		return hits;
	}

	/**
	 * Returns the source (a map of fields and values) for and object.
	 * The source is extracted from the index directly not the data store.
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param key the object id
	 * @param type type of object
	 * @return a map representation of the object
	 */
	protected Map<String, Object> getSource(String appid, String key, String type) {
		Map<String, Object> map = new HashMap<String, Object>();
		if (StringUtils.isBlank(key) || StringUtils.isBlank(appid)) {
			return map;
		}

		try {
			GetRequestBuilder grb = client().prepareGet().
					setIndex(getIndexName(appid)).setId(key);

			if (!StringUtils.isBlank(type)) {
				grb.setType(type);
			}

			map = grb.execute().actionGet().getSource();
		} catch (Exception e) {
			logger.warn(null, e);
		}
		return map;
	}

	@Override
	public Long getCount(String appid, String type) {
		if (StringUtils.isBlank(appid)) {
			return 0L;
		}
		SearchRequestBuilder crb = client().prepareSearch(appid).
				setSearchType(SearchType.COUNT).setQuery(QueryBuilders.matchAllQuery());

		if (!StringUtils.isBlank(type)) {
			crb.setTypes(type);
		}

		return crb.execute().actionGet().getHits().getTotalHits();
	}

	@Override
	public Long getCount(String appid, String type, Map<String, ?> terms) {
		if (StringUtils.isBlank(appid) || terms == null || terms.isEmpty()) {
			return 0L;
		}
		FilterBuilder fb = getTermsFilter(terms, true);
		if (fb == null) {
			return 0L;
		} else {
			QueryBuilder qb = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), fb);
			SearchRequestBuilder crb = client().prepareSearch(getIndexName(appid)).
					setSearchType(SearchType.COUNT).
					setQuery(qb);

			if (!StringUtils.isBlank(type)) {
				crb.setTypes(type);
			}

			return crb.execute().actionGet().getHits().getTotalHits();
		}
	}

	/**
	 * A method reserved for future use.
	 * It allows to have indexes with different names than the appid.
	 * @param appid an app identifer
	 * @return the correct index name
	 */
	private String getIndexName(String appid) {
		return appid;
	}

	/**
	 * @return true if asynchronous indexing/unindexing is enabled.
	 */
	private boolean isAsyncEnabled() {
		return Config.getConfigParamUnwrapped("es.async_enabled", false);
	}

	/**
	 * Creates a term filter for a set of terms.
	 * @param terms some terms
	 * @param mustMatchAll if true all terms must match ('AND' operation)
	 * @return the filter
	 */
	private FilterBuilder getTermsFilter(Map<String, ?> terms, boolean mustMatchAll) {
		FilterBuilder fb = mustMatchAll ? FilterBuilders.andFilter() : FilterBuilders.orFilter();
		boolean noop = true;
		boolean one = terms.size() == 1;

		for (Map.Entry<String, ?> term : terms.entrySet()) {
			if (!StringUtils.isBlank(term.getKey()) && term.getValue() != null) {
				Matcher matcher = Pattern.compile(".*(<|>|<=|>=)$").matcher(term.getKey().trim());
				BaseFilterBuilder bfb = FilterBuilders.termFilter(term.getKey(), term.getValue());
				if (matcher.matches()) {
					String key = term.getKey().replaceAll("[<>=\\s]+$", "");
					RangeFilterBuilder rfb = FilterBuilders.rangeFilter(key);
					if (">".equals(matcher.group(1))) {
						bfb = rfb.gt(term.getValue());
					} else if ("<".equals(matcher.group(1))) {
						bfb = rfb.lt(term.getValue());
					} else if (">=".equals(matcher.group(1))) {
						bfb = rfb.gte(term.getValue());
					} else if ("<=".equals(matcher.group(1))) {
						bfb = rfb.lte(term.getValue());
					}
				}
				if (one) {
					fb = bfb;
				} else if (mustMatchAll) {
					((AndFilterBuilder) fb).add(bfb);
				} else {
					((OrFilterBuilder) fb).add(bfb);
				}
				noop = false;
			}
		}
		return noop ? null : fb;
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
	public void unindexAll(Map<String, ?> terms, boolean matchAll) {
		unindexAll(Config.APP_NAME_NS, terms, matchAll);
	}

	@Override
	public <P extends ParaObject> P findById(String id) {
		return findById(Config.APP_NAME_NS, id);
	}

	@Override
	public <P extends ParaObject> List<P> findByIds(List<String> ids) {
		return findByIds(Config.APP_NAME_NS, ids);
	}

	@Override
	public <P extends ParaObject> List<P> findNearby(String type,
			String query, int radius, double lat, double lng, Pager... pager) {
		return findNearby(Config.APP_NAME_NS, type, query, radius, lat, lng, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findPrefix(String type, String field, String prefix, Pager... pager) {
		return findPrefix(Config.APP_NAME_NS, type, field, prefix, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findQuery(String type, String query, Pager... pager) {
		return findQuery(Config.APP_NAME_NS, type, query, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findSimilar(String type, String filterKey, String[] fields,
			String liketext, Pager... pager) {
		return findSimilar(Config.APP_NAME_NS, type, filterKey, fields, liketext, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTagged(String type, String[] tags, Pager... pager) {
		return findTagged(Config.APP_NAME_NS, type, tags, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTags(String keyword, Pager... pager) {
		return findTags(Config.APP_NAME_NS, keyword, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTermInList(String type, String field,
			List<?> terms, Pager... pager) {
		return findTermInList(Config.APP_NAME_NS, type, field, terms, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTerms(String type, Map<String, ?> terms,
			boolean mustMatchBoth, Pager... pager) {
		return findTerms(Config.APP_NAME_NS, type, terms, mustMatchBoth, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findWildcard(String type, String field, String wildcard,
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
