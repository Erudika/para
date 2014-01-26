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
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
@Singleton
public class ElasticSearch implements Search {
	
	private final Logger logger = LoggerFactory.getLogger(ElasticSearch.class);
	private final String DEFAULT_SORT = DAO.CN_TIMESTAMP;
	private static Client searchClient;
	private DAO dao;

	@Inject
	public ElasticSearch(DAO dao){
		this.dao = dao;
	}

	Client client(){
		if(searchClient == null){
			searchClient = ElasticSearchUtils.getClient();
		}		
		return searchClient;
	}
	
	@Override
	public void index(String appName, ParaObject so){
		index(appName, so, 0);
	}
	
	@Override
	public void index(String appName, ParaObject so, long ttl){
		if(so == null || StringUtils.isBlank(appName)) return;
		Map<String, Object> data = Utils.getAnnotatedFields(so, Stored.class, null);
		try {
			IndexRequestBuilder irb = client().prepareIndex(appName, 
					so.getClassname(), so.getId()).setSource(data);
			if(ttl > 0) irb.setTTL(ttl);
			irb.execute().actionGet();
			logger.debug("Search.index() {}", so.getId());
		} catch (Exception e) {
			logger.warn(null, e);
		}
	}
	
	@Override
	public void unindex(String appName, ParaObject so){
		if(so == null || StringUtils.isBlank(so.getId()) || StringUtils.isBlank(appName)) return;
		try{
			client().prepareDelete(appName, so.getClassname(), so.getId()).execute().actionGet();
			logger.debug("Search.unindex() {}", so.getId());
		} catch (Exception e) {
			logger.warn(null, e);
		}
	}
	
	@Override
	public <P extends ParaObject> void indexAll(String appName, List<P> objects){
		if(objects == null || StringUtils.isBlank(appName)) return ;
		BulkRequestBuilder brb = client().prepareBulk();
		for (ParaObject pObject : objects) {
			brb.add(client().prepareIndex(appName, pObject.getClassname(), 
						pObject.getId()).setSource(Utils.getAnnotatedFields(pObject, Stored.class, null)));
		}
		brb.execute().actionGet();
		logger.debug("Search.indexAll() {}", objects.size());
	}
	
	@Override
	public <P extends ParaObject> void unindexAll(String appName, List<P> objects){
		if(objects == null || StringUtils.isBlank(appName)) return ;
		BulkRequestBuilder brb = client().prepareBulk();
		for (ParaObject pObject : objects) {
			brb.add(client().prepareDelete(appName, pObject.getClassname(), pObject.getId()));
		}
		brb.execute().actionGet();
		logger.debug("Search.unindexAll() {}", objects.size());
	}
	
	@Override
	public <P extends ParaObject> P findById(String appName, String id, String type){
		try {
			return Utils.setAnnotatedFields(getSource(appName, id, type));
		} catch (Exception e) {
			logger.warn(null, e);
			return null;
		}
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findTerm(String appName, String type, MutableLong page, MutableLong itemcount, 
			String field, Object term){
		return findTerm(appName, type, page, itemcount, field, term, DEFAULT_SORT, true, Config.MAX_ITEMS_PER_PAGE);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findTerm(String appName, String type, MutableLong page, MutableLong itemcount, 
			String field, Object term, String sortfield, boolean reverse, int max){
		if(StringUtils.isBlank(field) || term == null) return new ArrayList<P> ();
		return searchQuery(appName, type, page, itemcount, null, FilterBuilders.termFilter(field, term), 
				sortfield, reverse, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findTermInList(String appName, String type, MutableLong page, MutableLong itemcount, 
			String field, List<?> terms, String sortfield, boolean reverse, int max){
		if(StringUtils.isBlank(field) || terms == null) return new ArrayList<P> ();
		return searchQuery(appName, type, page, itemcount, null, FilterBuilders.termsFilter(field, terms), sortfield, reverse, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findPrefix(String appName, String type, MutableLong page, MutableLong itemcount, 
			String field, String prefix){
		return findPrefix(appName, type, page, itemcount, field, prefix, DEFAULT_SORT, true, Config.MAX_ITEMS_PER_PAGE);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findPrefix(String appName, String type, MutableLong page, MutableLong itemcount, 
			String field, String prefix, String sortfield, boolean reverse, int max){
		if(StringUtils.isBlank(field) || StringUtils.isBlank(prefix)) return new ArrayList<P> ();
		return searchQuery(appName, type, page, itemcount, QueryBuilders.prefixQuery(field, prefix), null, 
				sortfield, reverse, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findQuery(String appName, String type, MutableLong page, MutableLong itemcount, 
			String query){
		return findQuery(appName, type, page, itemcount, query, DEFAULT_SORT, true, Config.MAX_ITEMS_PER_PAGE);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findQuery(String appName, String type, MutableLong page, MutableLong itemcount, 
			String query, String sortfield, boolean reverse, int max){
		if(StringUtils.isBlank(query)) return new ArrayList<P> ();
		return searchQuery(appName, type, page, itemcount, QueryBuilders.queryString(query).allowLeadingWildcard(false), null, 
				sortfield, reverse, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findWildcard(String appName, String type, MutableLong page, MutableLong itemcount, 
			String field, String wildcard){
		return findWildcard(appName, type, page, itemcount, field, wildcard, DEFAULT_SORT, true, Config.MAX_ITEMS_PER_PAGE);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findWildcard(String appName, String type, MutableLong page, MutableLong itemcount, 
			String field, String wildcard, String sortfield, boolean reverse, int max){
		if(StringUtils.isBlank(field) || StringUtils.isBlank(wildcard)) return new ArrayList<P> ();
		return searchQuery(appName, type, page, itemcount, QueryBuilders.wildcardQuery(field, wildcard), null,
				sortfield, reverse, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findTagged(String appName, String type, MutableLong page, MutableLong itemcount, 
			String... tags){
		if(tags == null || tags.length == 0 || StringUtils.isBlank(appName)) return new ArrayList<P>();
		
		BoolFilterBuilder tagFilter = FilterBuilders.boolFilter();
		//assuming clean & safe tags here
		for (String tag : tags) {
			tagFilter.must(FilterBuilders.termFilter(DAO.CN_TAGS, tag));
		}
		// The filter looks like this: ("tag1" OR "tag2" OR "tag3") AND "type"
		return searchQuery(appName, type, page, itemcount, null, tagFilter, null, true, Config.MAX_ITEMS_PER_PAGE);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTwoTerms(String appName, String type, MutableLong page, MutableLong itemcount, 
			String field1, Object term1, String field2, Object term2){
		return findTwoTerms(appName, type, page, itemcount, field1, term1, field2, term2, DEFAULT_SORT, true, Config.MAX_ITEMS_PER_PAGE);
	}	
	
	@Override
	public <P extends ParaObject> ArrayList<P> findTwoTerms(String appName, String type, MutableLong page, MutableLong itemcount, 
			String field1, Object term1, String field2, Object term2, String sortfield, boolean reverse, int max){
		return findTwoTerms(appName, type, page, itemcount, field1, term1, field2, term2, true, sortfield, reverse, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findTwoTerms(String appName, String type, MutableLong page, MutableLong itemcount, 
			String field1, Object term1, String field2, Object term2, boolean mustMatchBoth, 
			String sortfield, boolean reverse, int max){
		if(StringUtils.isBlank(field1) || StringUtils.isBlank(field2) || term1 == null || term2 == null) 
			return new ArrayList<P> ();
		FilterBuilder fb;
		if (mustMatchBoth) {
			fb = FilterBuilders.andFilter(
					FilterBuilders.termFilter(field1, term1), 
					FilterBuilders.termFilter(field2, term2));
		} else {
			fb = FilterBuilders.orFilter(
					FilterBuilders.termFilter(field1, term1), 
					FilterBuilders.termFilter(field2, term2));
		}
		return searchQuery(appName, type, page, itemcount, null, fb, sortfield, reverse, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findSimilar(String appName, String type, String filterKey, 
			String[] fields, String liketext, int max){
		if(StringUtils.isBlank(liketext)) return new ArrayList<P> ();
		QueryBuilder qb;
		FilterBuilder fb = null;
		
		if(fields == null || fields.length == 0){
			qb = QueryBuilders.moreLikeThisQuery().likeText(liketext).minDocFreq(1).minTermFreq(1);
		}else{
			qb = QueryBuilders.moreLikeThisQuery(fields).likeText(liketext).minDocFreq(1).minTermFreq(1);
		}

		if (!StringUtils.isBlank(filterKey)) {
			fb = FilterBuilders.notFilter(FilterBuilders.inFilter(DAO.CN_ID, filterKey));
		}
		
		SortBuilder sb = SortBuilders.scoreSort().order(SortOrder.DESC);
		return searchQuery(appName, searchQueryRaw(appName, type, null, null, qb, fb, sb, max));
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTags(String appName, String keyword, int max){
		if(StringUtils.isBlank(keyword)) return new ArrayList<P> ();
		QueryBuilder qb = QueryBuilders.wildcardQuery(PObject.classname(Tag.class), keyword.concat("*"));
//		SortBuilder sb = SortBuilders.fieldSort("count").order(SortOrder.DESC);
		return searchQuery(appName, PObject.classname(Tag.class), null, null, qb, null, null, true, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findNearbyObjects(String appName, String type, 
		MutableLong page, MutableLong itemcount, String query, int radius, double lat, double lng, String sortby){
		
		if(StringUtils.isBlank(type) || StringUtils.isBlank(appName)) return new ArrayList<P>();
		if(StringUtils.isBlank(query)) query = "*";
		
		// find nearby Address objects
		SortBuilder sort = StringUtils.isBlank(sortby) ? SortBuilders.scoreSort() : 
				SortBuilders.fieldSort(sortby).order(SortOrder.DESC);
		QueryBuilder qb1 = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
				FilterBuilders.geoDistanceFilter("latlng").point(lat, lng).distance(radius, DistanceUnit.KILOMETERS));
		SearchHits hits1 = searchQueryRaw(appName, PObject.classname(Address.class), null, null, qb1, 
				null, sort, Config.MAX_ITEMS_PER_PAGE);
		
		if(hits1 == null) return new ArrayList<P> ();
			
		// then find their parent objects
		String[] ridsarr = new String[(int) hits1.getTotalHits()];
		for (int i = 0; i < hits1.getTotalHits(); i++) {
			Object pid = hits1.getAt(i).getSource().get(DAO.CN_PARENTID);
			if(pid != null) ridsarr[i] = pid.toString();
		}

		QueryBuilder qb2 = QueryBuilders.filteredQuery(QueryBuilders.queryString(query),
				FilterBuilders.idsFilter(type).ids(ridsarr));
		SearchHits hits2 = searchQueryRaw(appName, type, page, itemcount, qb2, null, sort, Config.MAX_ITEMS_PER_PAGE);

		return searchQuery(appName, hits2);
	}
	
	private <P extends ParaObject> ArrayList<P> searchQuery(String appName, String type, MutableLong page, MutableLong itemcount,
			QueryBuilder query, FilterBuilder filter, String sortfield, boolean reverse, int max){
		SortOrder order = reverse ? SortOrder.DESC : SortOrder.ASC;
		SortBuilder sort = StringUtils.isBlank(sortfield) ? null : SortBuilders.fieldSort(sortfield).order(order);
		return searchQuery(appName, searchQueryRaw(appName, type, page, itemcount, query, filter, sort, max));
	}
	
	private <P extends ParaObject> ArrayList<P> searchQuery(String appName, SearchHits hits){
		ArrayList<P> results = new ArrayList<P> ();
		ArrayList<String> keys = new ArrayList<String>();
		
		if(hits == null) return new ArrayList<P> ();
		
		try{
			for (SearchHit hit : hits){
				keys.add(hit.getId());
				if(Config.READ_FROM_INDEX){
					P pobj = Utils.setAnnotatedFields(hit.getSource());
					results.add(pobj);
				}
			}
			
			if (!Config.READ_FROM_INDEX) {
				Map<String, P> fromDB = dao.readAll(appName, keys, true);
				results.addAll(fromDB.values());
//				unindexNulls(type, keys, fromDB);
			}
			logger.debug("Search.searchQuery() {}", results.size());
		} catch (Exception e) {
			logger.warn(null, e);
		}
		return results;
	}
		
	private SearchHits searchQueryRaw(String appName, String type, MutableLong page, MutableLong itemcount, 
			QueryBuilder query, FilterBuilder filter, SortBuilder sort, int max){
		if(StringUtils.isBlank(type) || StringUtils.isBlank(appName)) return null;
		if(query == null) query = QueryBuilders.matchAllQuery();
		if(filter == null) filter = FilterBuilders.matchAllFilter();
		if(sort == null) sort = SortBuilders.scoreSort();
		int start = (page == null || page.intValue() < 1 || 
				page.intValue() > Config.MAX_PAGES) ? 0 : (page.intValue() - 1) * max;
		
		SearchHits hits = null;
		
		try{
			SearchResponse response = client().prepareSearch(appName).
					setSearchType(SearchType.DFS_QUERY_THEN_FETCH).setTypes(type).
					setQuery(query).setFilter(filter).addSort(sort).setFrom(start).setSize(max).
					execute().actionGet();
			
			hits = response.getHits();
			if(itemcount != null)	itemcount.setValue(hits.getTotalHits());
			
		} catch (Exception e) {
			logger.warn(null, e);
		}
		
		return hits;
	}
		
	protected Map<String, Object> getSource(String appName, String key, String type){
		Map<String, Object> map = new HashMap<String, Object>();
		if(StringUtils.isBlank(key) || StringUtils.isBlank(type) || StringUtils.isBlank(appName)) return map;
		try{
			GetResponse resp = client().prepareGet().setIndex(appName).
					setId(key).setType(type).execute().actionGet();
			map = resp.getSource();
		} catch (Exception e) {
			logger.warn(null, e);
		}
		return map;
	}
	
	@Override
	public Long getBeanCount(String appName, String type){
		if(StringUtils.isBlank(type) || StringUtils.isBlank(appName)) return 0L;
		return client().prepareCount(appName).
				setTypes(type).setQuery(QueryBuilders.matchAllQuery()).
				execute().actionGet().getCount();
	}
	
	@Override
	public Long getCount(String appName, String type, String field, Object term){
		if(StringUtils.isBlank(type) || StringUtils.isBlank(appName) || 
				StringUtils.isBlank(field) || term == null) return 0L;
		return client().prepareCount(appName).
				setTypes(type).setQuery(QueryBuilders.termQuery(field, term)).
				execute().actionGet().getCount();
	}
	
	@Override
	public Long getCount(String appName, String type, String field1, Object term1, String field2, Object term2){
		if(StringUtils.isBlank(type) || StringUtils.isBlank(appName) || 
				StringUtils.isBlank(field1) || StringUtils.isBlank(field2)) return 0L;
		return client().prepareCount(appName).
				setTypes(type).setQuery(QueryBuilders.filteredQuery(QueryBuilders.termQuery(field1, term1), 
				FilterBuilders.termFilter(field2, term2))).
				execute().actionGet().getCount();
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
	public <P extends ParaObject> ArrayList<P> findNearbyObjects(String type, MutableLong page, MutableLong itemcount, 
			String query, int radius, double lat, double lng, String sortby) {
		return findNearbyObjects(Config.APP_NAME_NS, type, page, itemcount, query, radius, lat, lng, sortby);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findPrefix(String type, MutableLong page, MutableLong itemcount, 
			String field, String prefix) {
		return findPrefix(Config.APP_NAME_NS, type, page, itemcount, field, prefix);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findPrefix(String type, MutableLong page, MutableLong itemcount, 
			String field, String prefix, String sortfield, boolean reverse, int max) {
		return findPrefix(Config.APP_NAME_NS, type, page, itemcount, field, prefix, sortfield, reverse, max);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findQuery(String type, MutableLong page, MutableLong itemcount, String query) {
		return findQuery(Config.APP_NAME_NS, type, page, itemcount, query);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findQuery(String type, MutableLong page, MutableLong itemcount, 
			String query, String sortfield, boolean reverse, int max) {
		return findQuery(Config.APP_NAME_NS, type, page, itemcount, query, sortfield, reverse, max);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findSimilar(String type, String filterKey, String[] fields, 
			String liketext, int max) {
		return findSimilar(Config.APP_NAME_NS, type, filterKey, fields, liketext, max);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTagged(String type, MutableLong page, MutableLong itemcount, 
			String... tags) {
		return findTagged(Config.APP_NAME_NS, type, page, itemcount, tags);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTags(String keyword, int max) {
		return findTags(Config.APP_NAME_NS, keyword, max);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTerm(String type, MutableLong page, MutableLong itemcount, 
			String field, Object term) {
		return findTerm(Config.APP_NAME_NS, type, page, itemcount, field, term);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTerm(String type, MutableLong page, MutableLong itemcount, 
			String field, Object term, String sortfield, boolean reverse, int max) {
		return findTerm(Config.APP_NAME_NS, type, page, itemcount, field, term, sortfield, reverse, max);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTermInList(String type, MutableLong page, MutableLong itemcount, 
			String field, List<?> terms, String sortfield, boolean reverse, int max) {
		return findTermInList(Config.APP_NAME_NS, type, page, itemcount, field, terms, sortfield, reverse, max);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTwoTerms(String type, MutableLong page, MutableLong itemcount, 
			String field1, Object term1, String field2, Object term2) {
		return findTwoTerms(Config.APP_NAME_NS, type, page, itemcount, field1, term1, field2, term2);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTwoTerms(String type, MutableLong page, MutableLong itemcount, 
			String field1, Object term1, String field2, Object term2, String sortfield, boolean reverse, int max) {
		return findTwoTerms(Config.APP_NAME_NS, type, page, itemcount, field1, term1, field2, term2, sortfield, reverse, max);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTwoTerms(String type, MutableLong page, MutableLong itemcount, 
			String field1, Object term1, String field2, Object term2, boolean mustMatchBoth, String sortfield, boolean reverse, int max) {
		return findTwoTerms(Config.APP_NAME_NS, type, page, itemcount, field1, term1, field2, term2, mustMatchBoth, sortfield, reverse, max);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findWildcard(String type, MutableLong page, MutableLong itemcount, 
			String field, String wildcard) {
		return findWildcard(Config.APP_NAME_NS, type, page, itemcount, field, wildcard);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findWildcard(String type, MutableLong page, MutableLong itemcount, 
			String field, String wildcard, String sortfield, boolean reverse, int max) {
		return findWildcard(Config.APP_NAME_NS, type, page, itemcount, field, wildcard, sortfield, reverse, max);
	}

	@Override
	public Long getBeanCount(String type) {
		return getBeanCount(Config.APP_NAME_NS, type);
	}

	@Override
	public Long getCount(String type, String field, Object term) {
		return getCount(Config.APP_NAME_NS, type, field, term);
	}

	@Override
	public Long getCount(String type, String field1, Object term1, String field2, Object term2) {
		return getCount(Config.APP_NAME_NS, type, field1, term1, field2, term2);
	}
	
}
