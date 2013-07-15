/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erudika.para.utils;

import com.erudika.para.core.PObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.elasticsearch.action.admin.indices.alias.get.IndicesGetAliasesResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.optimize.OptimizeResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.AndFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

/**
 *
 * @author alexb
 */
public class Search {
	
	private static final Logger logger = Logger.getLogger(Search.class.getName());
	private static Search search;
	private static String INDEX_NAME;
	
	private Search(){
	}
	
	public static Search getInstance(){
		if(search == null){
			search = new Search();
		}
		return search;
	}
	
	public static String getIndexName(){
		if(StringUtils.isBlank(INDEX_NAME)){
			INDEX_NAME = Utils.INDEX_ALIAS + "1";	// avoids alias/name conflict
		}
		return INDEX_NAME;
	}
	
	/********************************************
	 *				SEARCH FUNCTIONS
	********************************************/
			
	private static XContentBuilder getVoteMapping() throws Exception{
		return XContentFactory.jsonBuilder().startObject().startObject(DAO.VOTE_TYPE).
					startObject("_ttl").
						field("enabled", true).
					endObject().
				endObject().
			endObject();
	}
	
	private static XContentBuilder getAddressMapping() throws Exception{
		return XContentFactory.jsonBuilder().startObject().startObject(DAO.ADDRESS_TYPE).
					startObject("properties").
						startObject("latlng").
							field("type", "geo_point").
							field("lat_lon", true).
						endObject().
					endObject().
				endObject().
			endObject();
	}
	
	static{
		// init search index
		if(!existsIndex(Utils.INDEX_ALIAS)){
			createIndex(getIndexName());
		}else{
			IndicesGetAliasesResponse get = AppListener.searchClient.admin().indices().
					prepareGetAliases(Utils.INDEX_ALIAS).execute().actionGet();
			Map<String, List<AliasMetaData>> aliases = get.getAliases();
			if(aliases.size() > 1){
				logger.log(Level.WARNING, "More than one index for alias {0}", new Object[]{Utils.INDEX_ALIAS});
			}else{
				for (Map.Entry<String, List<AliasMetaData>> entry : aliases.entrySet()) {
					INDEX_NAME = entry.getKey();
					break;
				}
			}
		}
	}
		
	public static void index(PObject so, String type){
		index(so.getId(), Utils.getAnnotatedFields(so, Stored.class, null), 
				StringUtils.isBlank(type) ? so.getClassname() : type);
	}
	public static void index(String key, Map<String, Object> data, String type){
		index(key, data, type, 0);
	}
	
	public static void index(String key, Map<String, Object> data, String type, long ttl){
		if(data == null || data.isEmpty() || StringUtils.isBlank(type)) return;
		try {
			IndexRequestBuilder irb = AppListener.searchClient.prepareIndex(Utils.INDEX_ALIAS, type, key).setSource(data);
			if(ttl > 0) irb.setTTL(ttl);
			irb.execute();
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
	}
	
	public static void unindex(String id, String type){
		if(StringUtils.isBlank(id) || StringUtils.isBlank(type)) return;
		try{
			AppListener.searchClient.prepareDelete(Utils.INDEX_ALIAS, type, id).setType(type).execute();
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
	}
	
//	public static void addToDocument(String key, String type, String field, Object value){
//		if(StringUtils.isBlank(key) || StringUtils.isBlank(type) || StringUtils.isBlank(field) || value == null) return ;
//		UpdateRequestBuilder urb = AppListener.searchClient.prepareUpdate(Utils.INDEX_ALIAS, type, key);
//		field = "field_".concat(field);
//		urb.setScript("ctx._source.".concat(field).concat(" = field"));
//		urb.addScriptParam("field", value);
//		urb.setUpsertRequest(Collections.singletonMap(key, field));
//		urb.execute().actionGet();
//	}
//	
//	public static void removeFromDocument(String key, String type, String field){
//		if(StringUtils.isBlank(key) || StringUtils.isBlank(type) || StringUtils.isBlank(field)) return ;
//		UpdateRequestBuilder urb = AppListener.searchClient.prepareUpdate(Utils.INDEX_ALIAS, type, key);
//		field = "field_".concat(field);
//		urb.setScript("ctx._source.remove(\"".concat(field).concat("\");"));
//		urb.execute().actionGet();
//	}
	
	public static void createIndex(String name){
		if(StringUtils.isBlank(name)) return;
		try {
			NodeBuilder nb = NodeBuilder.nodeBuilder();
			nb.settings().put("number_of_shards", "5");
			nb.settings().put("number_of_replicas", "0");
			nb.settings().put("auto_expand_replicas", "0-all");
			nb.settings().put("analysis.analyzer.default.type", "standard");
			nb.settings().putArray("analysis.analyzer.default.stopwords", 
					"arabic", "armenian", "basque", "brazilian", "bulgarian", "catalan", 
					"czech", "danish", "dutch", "english", "finnish", "french", "galician", 
					"german", "greek", "hindi", "hungarian", "indonesian", "italian", 
					"norwegian", "persian", "portuguese", "romanian", "russian", "spanish", 
					"swedish", "turkish"); 

					
			CreateIndexRequestBuilder create = AppListener.searchClient.admin().indices().prepareCreate(name).
					setSettings(nb.settings().build());

			create.addMapping(DAO.ADDRESS_TYPE, getAddressMapping());
			create.addMapping(DAO.VOTE_TYPE, getVoteMapping());
			create.execute().actionGet();
			
			AppListener.searchClient.admin().indices().prepareAliases().
					addAlias(name, Utils.INDEX_ALIAS).execute();
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
	}
	
	public static void deleteIndex(String name){
		if(StringUtils.isBlank(name)) return;
		try {
			if(existsIndex(name)){
				AppListener.searchClient.admin().indices().prepareDelete(name).execute();
			}
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
	}
	
	public static boolean existsIndex(String name){
		if(StringUtils.isBlank(name)) return false;
		boolean exists = false;
		try {
			exists = AppListener.searchClient.admin().indices().prepareExists(name).execute().
					actionGet().isExists();
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
		return exists;
	}
	
	public static void rebuildIndex(String newIndex){
		if(StringUtils.isBlank(newIndex)) return;
		try {
			deleteIndex(getIndexName());
			if(!existsIndex(newIndex)) createIndex(newIndex);
			
			logger.log(Level.INFO, "rebuildIndex(): {0}", new Object[]{newIndex});
//			Map<String, PObject> all = DAO.getInstance().readAll(DAO.OBJECTS, READ_CAP);
			LinkedHashMap<String, PObject> results = new LinkedHashMap<String, PObject>();
			BulkRequestBuilder brb = AppListener.searchClient.prepareBulk();
			BulkResponse resp = null;
			String lastKey = null;
			
			do {
				Thread.sleep(1000);
				LinkedList<PObject> list = (LinkedList<PObject>) DAO.getInstance().
						readPage(DAO.OBJECTS, lastKey, Utils.READ_CAPACITY);
				if (!list.isEmpty()) {
					for (PObject obj : list) {
						results.put(obj.getId(), obj);
						brb.add(AppListener.searchClient.prepareIndex(newIndex, obj.getClassname(), obj.getId()).
								setSource(Utils.getAnnotatedFields(obj, Stored.class, null)));
					}
					// bulk index 1000 objects
					if(brb.numberOfActions() > 1000){
						resp = brb.execute().actionGet();
						logger.log(Level.INFO, "rebuildIndex(): indexed {0}, hasFailures: {1}", 
								new Object[]{brb.numberOfActions(), resp.hasFailures()});
					}
					lastKey = list.getLast().getId();
				} else {
					lastKey = null;
				}
			} while (lastKey != null);
			
			// anything left after loop? bulk index that too
			if (brb.numberOfActions() > 0) {
				resp = brb.execute().actionGet();
				logger.log(Level.INFO, "rebuildIndex(): indexed {0}, hasFailures: {1}", 
						new Object[]{brb.numberOfActions(), resp.hasFailures()});
			}
			
			// create index alias NEW_INDEX -> INDEX_ALIAS, OLD_INDEX -> X
			AppListener.searchClient.admin().indices().prepareAliases().
					addAlias(newIndex, Utils.INDEX_ALIAS).
					removeAlias(getIndexName(), Utils.INDEX_ALIAS).execute();
			
			// rename current index
			INDEX_NAME = newIndex;
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
	}
	
	public static boolean optimizeIndex(String name){
		if(StringUtils.isBlank(name)) return false;
		boolean result = false;
		try {
			OptimizeResponse resp = AppListener.searchClient.admin().indices().
					prepareOptimize(name).execute().actionGet();
			
			result = resp.getFailedShards() == 0;
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
		return result;
	}
	
//	public static ArrayList<OMObject> readAndRepair(String clazz, ArrayList<String> keys){
//		return readAndRepair((Class<OMObject>) Utils.getClassname(clazz), keys, null);
//	}
//	
//	public static ArrayList<OMObject> readAndRepair(Class<OMObject> clazz, 
//			ArrayList<String> keys, MutableLong itemcount){
//		
//		
//		if(!results.isEmpty() && results.contains(null)){
//			repairIndex(clazz.getSimpleName().toLowerCase(), results, keys, itemcount);
//		}
//		
//		return results;
//	}
	
	public static ArrayList<PObject> findTerm(String type, MutableLong page, MutableLong itemcount, 
			String field, Object term){
		return findTerm(type, page, itemcount, field, term, null, true, Utils.MAX_ITEMS_PER_PAGE);
	}
	
	public static ArrayList<PObject> findTerm(String type, MutableLong page, MutableLong itemcount, 
			String field, Object term, String sortfield, boolean reverse, int max){
		return searchQuery(type, page, itemcount, null, FilterBuilders.termFilter(field, term), 
				sortfield, reverse, max);
	}
	
	public static ArrayList<PObject> findPrefix(String type, MutableLong page, MutableLong itemcount, 
			String field, String prefix){
		return findPrefix(type, page, itemcount, field, prefix, null, true, Utils.MAX_ITEMS_PER_PAGE);
	}
	
	public static ArrayList<PObject> findPrefix(String type, MutableLong page, MutableLong itemcount, 
			String field, String prefix, String sortfield, boolean reverse, int max){
		return searchQuery(type, page, itemcount, QueryBuilders.prefixQuery(field, prefix), null, 
				sortfield, reverse, max);
	}
	
	public static ArrayList<PObject> findQuery(String type, MutableLong page, MutableLong itemcount, 
			String query){
		return findQuery(type, page, itemcount, query, null, true, Utils.MAX_ITEMS_PER_PAGE);
	}
	
	public static ArrayList<PObject> findQuery(String type, MutableLong page, MutableLong itemcount, 
			String query, String sortfield, boolean reverse, int max){
		return searchQuery(type, page, itemcount, QueryBuilders.queryString(query), null, 
				sortfield, reverse, max);
	}

	public static ArrayList<PObject> findWildcard(String type, MutableLong page, MutableLong itemcount, 
			String field, String wildcard){
		return findWildcard(type, page, itemcount, field, wildcard, null, true, Utils.MAX_ITEMS_PER_PAGE);
	}
	
	public static ArrayList<PObject> findWildcard(String type, MutableLong page, MutableLong itemcount, 
			String field, String wildcard, String sortfield, boolean reverse, int max){
		return searchQuery(type, page, itemcount, QueryBuilders.wildcardQuery(field, wildcard), null,
				sortfield, reverse, max);
	}
	
	public static ArrayList<PObject> findTagged(String type, MutableLong page, MutableLong itemcount, 
			ArrayList<String> tags){
		OrFilterBuilder tagFilter = FilterBuilders.orFilter(
				FilterBuilders.termFilter("tags", tags.remove(0)));

		if (!tags.isEmpty()) {
			//assuming clean & safe tags here
			for (String tag : tags) {
				tagFilter.add(FilterBuilders.termFilter("tags", tag));
			}
		}
		// The filter looks like this: ("tag1" OR "tag2" OR "tag3") AND "type"
		AndFilterBuilder andFilter = FilterBuilders.andFilter(tagFilter);
		andFilter.add(FilterBuilders.termFilter("type", type));

		return searchQuery(type, page, itemcount, null, andFilter, null, true, Utils.MAX_ITEMS_PER_PAGE);
	}

	public static ArrayList<PObject> findFilteredTerm(String type, MutableLong page, MutableLong itemcount, 
			String filterField, Object filterTerm, String field, Object term){
		return findFilteredTerm(type, page, itemcount, filterField, filterTerm, field, term, 
				null, true, Utils.MAX_ITEMS_PER_PAGE);
	}
	
	
	public static ArrayList<PObject> findFilteredTerm(String type, MutableLong page, MutableLong itemcount, 
			String filterField, Object filterTerm, String field, Object term, String sortfield, boolean reverse, int max){
		QueryBuilder qb = QueryBuilders.filteredQuery(QueryBuilders.termQuery(field, term), 
				FilterBuilders.termFilter(filterField, filterTerm));
		return searchQuery(type, page, itemcount, qb, null, sortfield, reverse, max);
	}
	
	public static ArrayList<String> findFilteredTermIds(String type, MutableLong page, MutableLong itemcount, 
			String filterField, Object filterTerm, String field, Object term, String sortfield, boolean reverse, int max){
		QueryBuilder qb = QueryBuilders.filteredQuery(QueryBuilders.termQuery(field, term), 
				FilterBuilders.termFilter(filterField, filterTerm));
		return searchQueryIds(type, page, itemcount, qb, null, SortBuilders.fieldSort(DAO.CN_ID).order(SortOrder.DESC), max);
	}
	
	public static ArrayList<PObject> findSimilar(String type, String filterKey, String[] fields, String liketext, int max){
		QueryBuilder qb = QueryBuilders.filteredQuery(QueryBuilders.moreLikeThisQuery(fields).
			likeText(liketext), FilterBuilders.notFilter(FilterBuilders.inFilter(DAO.CN_ID, filterKey)));
		SortBuilder sb = SortBuilders.scoreSort().order(SortOrder.DESC);
		return searchQuery(type, null, searchQueryRaw(type, null, null, qb, null, sb, max));
	}

	public static ArrayList<PObject> findTags(String keyword, int max){
		QueryBuilder qb = QueryBuilders.wildcardQuery(DAO.TAG_TYPE, keyword.concat("*"));
//		SortBuilder sb = SortBuilders.fieldSort("count").order(SortOrder.DESC);
		return searchQuery(DAO.TAG_TYPE, null, null, qb, null, null, true, max);
	}
	
	public static ArrayList<PObject> findNearbyObjects(String type, MutableLong page, MutableLong itemcount, 
			String query, int radius, double lat, double lng, String sortby){
		
		FieldSortBuilder sort = SortBuilders.fieldSort(sortby).order(SortOrder.DESC);
		QueryBuilder qb1 = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
				FilterBuilders.geoDistanceFilter("latlng").point(lat, lng).distance(radius, DistanceUnit.KILOMETERS));
		SearchHits hits1 = Search.searchQueryRaw(DAO.ADDRESS_TYPE, page, itemcount, qb1, null, sort, Utils.MAX_ITEMS_PER_PAGE);
		
		if(hits1 == null) return new ArrayList<PObject> ();
			
		String[] ridsarr = new String[(int) hits1.getTotalHits()];
		for (int i = 0; i < hits1.getTotalHits(); i++) {
			Object pid = hits1.getAt(i).getSource().get("parentid");
			if(pid != null) ridsarr[i] = pid.toString();
		}

		QueryBuilder qb2 = QueryBuilders.filteredQuery(QueryBuilders.queryString(query),
				FilterBuilders.idsFilter(type).ids(ridsarr));
		SearchHits hits2 = Search.searchQueryRaw(type, null, null, qb2, null, sort, Utils.MAX_ITEMS_PER_PAGE);

		return Search.searchQuery(type, itemcount, hits2);
	}
	
	
	private static ArrayList<PObject> searchQuery(String type, MutableLong page, MutableLong itemcount,
			QueryBuilder query, FilterBuilder filter, String sortfield, boolean reverse, int max){
		SortOrder order = reverse ? SortOrder.DESC : SortOrder.ASC;
		SortBuilder sort = StringUtils.isBlank(sortfield) ? null : SortBuilders.fieldSort(sortfield).order(order);
		return searchQuery(type, itemcount, searchQueryRaw(type, page, itemcount, query, filter, sort, max));
	}
	
	public static ArrayList<PObject> searchQuery(String type, MutableLong itemcount, SearchHits hits){
		ArrayList<PObject> results = new ArrayList<PObject> ();
		ArrayList<String> keys = new ArrayList<String>();
		
		if(hits == null || StringUtils.isBlank(type)) return results;
		
		try{
			for (SearchHit hit : hits){
				keys.add(hit.getId());
				if(Utils.READ_FROM_INDEX){
					results.add(fromSource(type, hit.getSource()));
				}
			}
			
			if (Utils.READ_FROM_INDEX) {
				unindexNulls(type, keys, null);
			} else {
				Map<String, PObject> fromDB = DAO.getInstance().readAll(keys, true);
				results.addAll(fromDB.values());
				unindexNulls(type, keys, fromDB);
			}			
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
		return results;
	}
		
	private static SearchHits searchQueryRaw(String type, MutableLong page, MutableLong itemcount, 
			QueryBuilder query, FilterBuilder filter, SortBuilder sort, int max){
		if(StringUtils.isBlank(type)) return null;
		if(sort == null) sort = SortBuilders.fieldSort(DAO.CN_ID).order(SortOrder.DESC);
//		if(query == null) query = QueryBuilders.matchAllQuery();
		int start = (page == null || page.intValue() < 1 || 
				page.intValue() > Utils.MAX_PAGES) ? 0 : (page.intValue() - 1) * max;
		
		SearchHits hits = null;
		
		try{
			SearchResponse response = AppListener.searchClient.prepareSearch(Utils.INDEX_ALIAS).
					setSearchType(SearchType.DFS_QUERY_THEN_FETCH).setTypes(type).
					setQuery(query).setFilter(filter).addSort(sort).setFrom(start).setSize(max).
					execute().actionGet();

			hits = response.getHits();
			if(itemcount != null)	itemcount.setValue(hits.getTotalHits());
			
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
		
		return hits;
	}
	
	private static ArrayList<String> searchQueryIds(String type, MutableLong page, MutableLong itemcount, 
			QueryBuilder query, FilterBuilder filter, SortBuilder sort, int max){
		ArrayList<String> keys = new ArrayList<String>();
		SearchHits hits = searchQueryRaw(type, page, itemcount, query, filter, sort, max);
		if (hits != null) {
			for (SearchHit hit : hits) {
				keys.add(hit.getId());
			}
		}
		return keys;
	}
	
	public static boolean existsTerm(String term, String value){
		if(StringUtils.isBlank(term) || StringUtils.isBlank(value)) return false;
		SearchHits hits = null;
		try{ 
			SearchResponse response = AppListener.searchClient.prepareSearch(Utils.INDEX_ALIAS)
				.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
				.setFilter(FilterBuilders.termFilter(term, value)).execute().actionGet();

			hits = response.getHits();
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
		return hits != null && hits.getTotalHits() > 0;
	}
	
	public static Map<String, Object> getSource(String key, String type){
		Map<String, Object> map = new HashMap<String, Object>();
		if(StringUtils.isBlank(key) || StringUtils.isBlank(type)) return map;
		try{
			GetResponse resp = AppListener.searchClient.prepareGet().setIndex(Utils.INDEX_ALIAS).
					setId(key).setType(type).execute().actionGet();
			map = resp.getSource();
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
		return map;
	}
	
	private static PObject fromSource(String type, Map<String, Object> source) throws Exception{
		Class<?> clazz = Utils.getClassname(type);
		if(clazz != null){
			PObject obj = (PObject) clazz.newInstance();
			BeanUtils.populate(obj, source);
			return obj;
		}else{
			return null;
		}
	}
	
	public static Long getBeanCount(String classtype){
		return AppListener.searchClient.prepareCount(Utils.INDEX_ALIAS).
				setTypes(classtype).execute().actionGet().getCount();
	}
	
	private static void unindexNulls(final String type, final List<String> keys, final Map<String, PObject> fromDB){
		Utils.asyncExecute(new Callable<Object>(){
			public Object call() throws Exception {
				Map<String, PObject> dbKeys;
				if(fromDB == null){
					dbKeys = DAO.getInstance().readAll(keys, !Utils.READ_FROM_INDEX);
				}else{
					dbKeys = fromDB;
				}			
				// remove objects that are still indexed but not in the database
				if (!dbKeys.isEmpty() && dbKeys.values().contains(null)) {
					BulkRequestBuilder brb = AppListener.searchClient.prepareBulk();

					for (Iterator<Map.Entry<String, PObject>> it = dbKeys.entrySet().iterator(); it.hasNext();) {
						Map.Entry<String, PObject> entry = it.next();
						String key = entry.getKey();
						PObject value = entry.getValue();
						if (value == null) {
							brb.add(AppListener.searchClient.prepareDelete(Utils.INDEX_ALIAS, type, key).request());
						}
					}
					brb.execute();
				}
				return true;
			}
		});
	}
	
}
