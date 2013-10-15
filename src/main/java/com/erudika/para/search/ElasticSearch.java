/*
 * Copyright 2013 Alex Bogdanovski <albogdano@me.com>.
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

import com.erudika.para.search.Search;
import com.erudika.para.annotations.Stored;
import com.erudika.para.persistence.DAO;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Address;
import com.erudika.para.core.PObject;
import com.erudika.para.core.Tag;
import com.erudika.para.core.Vote;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.indices.alias.get.IndicesGetAliasesResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.optimize.OptimizeResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.AndFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@Singleton
public class ElasticSearch implements Search {
	
	private final Logger logger = Logger.getLogger(ElasticSearch.class.getName());
	private final String DEFAULT_SORT = DAO.CN_TIMESTAMP;
	private String INDEX_NAME;
	private static Client searchClient;
	private static Node searchNode;
	
	private DAO dao;
	
	public ElasticSearch(){
		// init search index
		if(!existsIndex(Utils.INDEX_ALIAS)){
			createIndex(getIndexName());
		}else{
			IndicesGetAliasesResponse get = getSearchClient().admin().indices().
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
	
	@Inject
	public void setDao(DAO dao) {
		this.dao = dao;
	}
	
	private Client getSearchClient(){
		if(searchClient != null) return searchClient;
		ImmutableSettings.Builder settings = ImmutableSettings.settingsBuilder();
		settings.put("client.transport.sniff", true);
		settings.put("path.data", "/var/lib/elasticsearch/data");
		settings.put("path.work", "/var/lib/elasticsearch/work");
//		settings.put("path.logs", "/var/log/elasticsearch/");

		if (Utils.IN_PRODUCTION) {
			if (!StringUtils.isBlank(Utils.AWS_ACCESSKEY) && !StringUtils.isBlank(Utils.AWS_SECRETKEY)) {
				settings.put("cloud.aws.access_key", Utils.AWS_ACCESSKEY);
				settings.put("cloud.aws.secret_key", Utils.AWS_SECRETKEY);
			}
			settings.put("cloud.aws.region", Utils.AWS_REGION);
			settings.put("network.tcp.keep_alive", true);
//			settings.put("index.number_of_shards", 5);
//			settings.put("index.number_of_replicas", 0);
//
			settings.put("discovery.type", "ec2");
			settings.put("discovery.ec2.groups", "elasticsearch");
////			settings.put("discovery.ec2.availability_zones", "eu-west-1a"); 
			searchNode = NodeBuilder.nodeBuilder().settings(settings).
					clusterName(Utils.CLUSTER_NAME).client(true).data(false).node();
			searchClient = searchNode.client();
		}else{
//			searchClient = new TransportClient(settings.put("cluster.name", Utils.CLUSTER_NAME).build());
//				((TransportClient) searchClient).addTransportAddress(
//						new InetSocketTransportAddress("localhost", 9300));
			searchNode = NodeBuilder.nodeBuilder().settings(settings).
					clusterName(Utils.CLUSTER_NAME).local(true).data(true).node();
			searchClient = searchNode.client();
		}
				
		Utils.attachShutdownHook(ElasticSearch.class, new Thread() {
			public void run() {
				System.out.println("Stopping search..."); 
				closeSearch();
			}
		});
		
		return searchClient;
	}
	
	private static void closeSearch(){
		if(searchClient != null) searchClient.close();
		if(searchNode != null) searchNode.close();
	}

	@Override
	public String getIndexName(){
		if(StringUtils.isBlank(INDEX_NAME)){
			INDEX_NAME = Utils.INDEX_ALIAS + "1";	// avoids alias/name conflict
		}
		return INDEX_NAME;
	}
	
	/********************************************
	 *				SEARCH FUNCTIONS
	********************************************/
	
	private XContentBuilder getVoteMapping() throws Exception{
		return XContentFactory.jsonBuilder().startObject().startObject(PObject.classname(Vote.class)).
					startObject("_ttl").
						field("enabled", true).
					endObject().
				endObject().
			endObject();
	}
	
	private XContentBuilder getAddressMapping() throws Exception{
		return XContentFactory.jsonBuilder().startObject().startObject(PObject.classname(Address.class)).
					startObject("properties").
						startObject("latlng").
							field("type", "geo_point").
							field("lat_lon", true).
						endObject().
					endObject().
				endObject().
			endObject();
	}
	
	private XContentBuilder getTagMapping() throws Exception{
		return XContentFactory.jsonBuilder().startObject().startObject(PObject.classname(Tag.class)).
					startObject("properties").
						startObject("tag").
							field("type", "string").
							field("index", "not_analyzed").
						endObject().
					endObject().
				endObject().
			endObject();
	}
	
	@Override
	public void index(ParaObject so, String type){
		index(so, type, 0);
	}
	
	@Override
	public void index(ParaObject so, String type, long ttl){
		if(so == null || StringUtils.isBlank(type)) return;
		Map<String, Object> data = Utils.getAnnotatedFields(so, Stored.class, null);
		try {
			IndexRequestBuilder irb = getSearchClient().prepareIndex(Utils.INDEX_ALIAS, 
					(StringUtils.isBlank(type) ? so.getClassname() : type), so.getId()).setSource(data);
			if(ttl > 0) irb.setTTL(ttl);
			irb.execute();
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
	}
	
	@Override
	public void unindex(ParaObject so, String type){
		if(so == null || StringUtils.isBlank(so.getId()) || StringUtils.isBlank(type)) return;
		try{
			getSearchClient().prepareDelete(Utils.INDEX_ALIAS, type, so.getId()).execute();
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
	}
	
	@Override
	public <P extends ParaObject> void indexAll(List<P> objects){
		if(objects == null) return ;
		BulkRequestBuilder brb = getSearchClient().prepareBulk();
		for (ParaObject pObject : objects) {
			brb.add(getSearchClient().prepareIndex(Utils.INDEX_ALIAS, pObject.getClassname(), 
						pObject.getId()).setSource(Utils.getAnnotatedFields(pObject, Stored.class, null)));
		}
		brb.execute();
	}
	
	@Override
	public <P extends ParaObject> void unindexAll(List<P> objects){
		if(objects == null) return ;
		BulkRequestBuilder brb = getSearchClient().prepareBulk();
		for (ParaObject pObject : objects) {
			brb.add(getSearchClient().prepareDelete(Utils.INDEX_ALIAS, pObject.getClassname(), pObject.getId()));
		}
		brb.execute();
	}
	
//	public void addToDocument(String key, String type, String field, Object value){
//		if(StringUtils.isBlank(key) || StringUtils.isBlank(type) || StringUtils.isBlank(field) || value == null) return ;
//		UpdateRequestBuilder urb = getSearchClient().prepareUpdate(Utils.INDEX_ALIAS, type, key);
//		field = "field_".concat(field);
//		urb.setScript("ctx._source.".concat(field).concat(" = field"));
//		urb.addScriptParam("field", value);
//		urb.setUpsertRequest(Collections.singletonMap(key, field));
//		urb.execute().actionGet();
//	}
//	
//	public void removeFromDocument(String key, String type, String field){
//		if(StringUtils.isBlank(key) || StringUtils.isBlank(type) || StringUtils.isBlank(field)) return ;
//		UpdateRequestBuilder urb = getSearchClient().prepareUpdate(Utils.INDEX_ALIAS, type, key);
//		field = "field_".concat(field);
//		urb.setScript("ctx._source.remove(\"".concat(field).concat("\");"));
//		urb.execute().actionGet();
//	}
	
	@Override
	public void createIndex(String name){
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

					
			CreateIndexRequestBuilder create = getSearchClient().admin().indices().prepareCreate(name).
					setSettings(nb.settings().build());

			create.addMapping(PObject.classname(Vote.class), getVoteMapping());
			create.addMapping(PObject.classname(Address.class), getAddressMapping());
			create.addMapping(PObject.classname(Tag.class), getTagMapping());
			create.execute().actionGet();
			
			getSearchClient().admin().indices().prepareAliases().
					addAlias(name, Utils.INDEX_ALIAS).execute();
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
	}
	
	@Override
	public void deleteIndex(String name){
		if(StringUtils.isBlank(name)) return;
		try {
			if(existsIndex(name)){
				getSearchClient().admin().indices().prepareDelete(name).execute();
			}
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
	}
	
	@Override
	public boolean existsIndex(String name){
		if(StringUtils.isBlank(name)) return false;
		boolean exists = false;
		try {
			exists = getSearchClient().admin().indices().prepareExists(name).execute().
					actionGet().isExists();
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
		return exists;
	}
	
	@Override
	public void rebuildIndex(String newIndex){
		if(StringUtils.isBlank(newIndex)) return;
		try {
			deleteIndex(getIndexName());
			if(!existsIndex(newIndex)) createIndex(newIndex);
			logger.log(Level.INFO, "rebuildIndex(): {0}", new Object[]{newIndex});
//			Map<String, PObject> all = AWSDynamoDAO.getInstance().readAll(AWSDynamoDAO.OBJECTS, READ_CAP);
//			LinkedHashMap<String, PObject> results = new LinkedHashMap<String, PObject>();
			BulkRequestBuilder brb = getSearchClient().prepareBulk();
			BulkResponse resp = null;
			String lastKey = null;
			
			List<ParaObject> list = dao.readPage(DAO.OBJECTS, null);

			if (!list.isEmpty()) {
				do{
					for (ParaObject obj : list) {
						brb.add(getSearchClient().prepareIndex(newIndex, obj.getClassname(), obj.getId()).
								setSource(Utils.getAnnotatedFields(obj, Stored.class, null)));
						lastKey = obj.getId();
					}
//					Thread.sleep(1000);
					logger.log(Level.INFO, "brb {0}", new Object[]{brb.numberOfActions()});
					// bulk index 1000 objects
					if(brb.numberOfActions() > 100){
						resp = brb.execute().actionGet();
						logger.log(Level.INFO, "rebuildIndex(): indexed {0}, hasFailures: {1}", 
								new Object[]{brb.numberOfActions(), resp.hasFailures()});
					}
				}while(!(list = dao.readPage(DAO.OBJECTS, lastKey)).isEmpty());
			}
			
			// anything left after loop? index that too
			if (brb.numberOfActions() > 0) {
				resp = brb.execute().actionGet();
				logger.log(Level.INFO, "rebuildIndex(): indexed {0}, hasFailures: {1}", 
						new Object[]{brb.numberOfActions(), resp.hasFailures()});
			}
			
			// create index alias NEW_INDEX -> INDEX_ALIAS, OLD_INDEX -> X
			getSearchClient().admin().indices().prepareAliases().
					addAlias(newIndex, Utils.INDEX_ALIAS).
					removeAlias(getIndexName(), Utils.INDEX_ALIAS).execute();
			
			// rename current index
			INDEX_NAME = newIndex;
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
	}
	
	@Override
	public boolean optimizeIndex(String name){
		if(StringUtils.isBlank(name)) return false;
		boolean result = false;
		try {
			OptimizeResponse resp = getSearchClient().admin().indices().
					prepareOptimize(name).execute().actionGet();
			
			result = resp.getFailedShards() == 0;
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
		return result;
	}
	
	@Override
	public Map<String, String> getSearchClusterMetadata(){
		NodesInfoResponse res = getSearchClient().admin().cluster().nodesInfo(new NodesInfoRequest().all()).actionGet();
		Map<String, String> md = new HashMap<String, String>();
		md.put("cluser.name", res.getClusterName().toString());
		
		for (NodeInfo nodeInfo : res) {
			md.put("node.name", nodeInfo.getNode().getName());
			md.put("node.name", nodeInfo.getNode().getAddress().toString());
			md.put("node.data", Boolean.toString(nodeInfo.getNode().isDataNode()));
			md.put("node.client", Boolean.toString(nodeInfo.getNode().isClientNode()));
			md.put("node.version", nodeInfo.getNode().getVersion().toString());
		}
		return md;
	}
	
//	public ArrayList<OMObject> readAndRepair(String clazz, ArrayList<String> keys){
//		return readAndRepair((Class<OMObject>) Utils.getClassname(clazz), keys, null);
//	}
//	
//	public ArrayList<OMObject> readAndRepair(Class<OMObject> clazz, 
//			ArrayList<String> keys, MutableLong itemcount){
//		
//		
//		if(!results.isEmpty() && results.contains(null)){
//			repairIndex(clazz.getSimpleName().toLowerCase(), results, keys, itemcount);
//		}
//		
//		return results;
//	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findTerm(String type, MutableLong page, MutableLong itemcount, 
			String field, Object term){
		return findTerm(type, page, itemcount, field, term, DEFAULT_SORT, true, Utils.MAX_ITEMS_PER_PAGE);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findTerm(String type, MutableLong page, MutableLong itemcount, 
			String field, Object term, String sortfield, boolean reverse, int max){
		return searchQuery(type, page, itemcount, null, FilterBuilders.termFilter(field, term), 
				sortfield, reverse, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findTermInList(String type, MutableLong page, MutableLong itemcount, 
			String field, List<?> terms, String sortfield, boolean reverse, int max){
		return searchQuery(type, page, itemcount, null, FilterBuilders.termsFilter(field, terms), sortfield, reverse, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findPrefix(String type, MutableLong page, MutableLong itemcount, 
			String field, String prefix){
		return findPrefix(type, page, itemcount, field, prefix, DEFAULT_SORT, true, Utils.MAX_ITEMS_PER_PAGE);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findPrefix(String type, MutableLong page, MutableLong itemcount, 
			String field, String prefix, String sortfield, boolean reverse, int max){
		return searchQuery(type, page, itemcount, QueryBuilders.prefixQuery(field, prefix), null, 
				sortfield, reverse, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findQuery(String type, MutableLong page, MutableLong itemcount, 
			String query){
		return findQuery(type, page, itemcount, query, DEFAULT_SORT, true, Utils.MAX_ITEMS_PER_PAGE);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findQuery(String type, MutableLong page, MutableLong itemcount, 
			String query, String sortfield, boolean reverse, int max){
		return searchQuery(type, page, itemcount, QueryBuilders.queryString(query), null, 
				sortfield, reverse, max);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findWildcard(String type, MutableLong page, MutableLong itemcount, 
			String field, String wildcard){
		return findWildcard(type, page, itemcount, field, wildcard, DEFAULT_SORT, true, Utils.MAX_ITEMS_PER_PAGE);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findWildcard(String type, MutableLong page, MutableLong itemcount, 
			String field, String wildcard, String sortfield, boolean reverse, int max){
		return searchQuery(type, page, itemcount, QueryBuilders.wildcardQuery(field, wildcard), null,
				sortfield, reverse, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findTagged(String type, MutableLong page, MutableLong itemcount, 
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

	@Override
	public <P extends ParaObject> ArrayList<P> findTwoTerms(String type, MutableLong page, MutableLong itemcount, 
			String field1, Object term1, String field2, Object term2){
		return findTwoTerms(type, page, itemcount, field1, term1, field2, term2, DEFAULT_SORT, true, Utils.MAX_ITEMS_PER_PAGE);
	}	
	
	@Override
	public <P extends ParaObject> ArrayList<P> findTwoTerms(String type, MutableLong page, MutableLong itemcount, 
			String field1, Object term1, String field2, Object term2, String sortfield, boolean reverse, int max){
		return findTwoTerms(type, page, itemcount, field1, term1, field2, term2, true, sortfield, reverse, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findTwoTerms(String type, MutableLong page, MutableLong itemcount, 
			String field1, Object term1, String field2, Object term2, boolean mustMatchBoth, 
			String sortfield, boolean reverse, int max){
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
		return searchQuery(type, page, itemcount, null, fb, sortfield, reverse, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findSimilar(String type, String filterKey, String[] fields, String liketext, int max){
		QueryBuilder qb = QueryBuilders.filteredQuery(QueryBuilders.moreLikeThisQuery(fields).
			likeText(liketext), FilterBuilders.notFilter(FilterBuilders.inFilter(DAO.CN_ID, filterKey)));
		SortBuilder sb = SortBuilders.scoreSort().order(SortOrder.DESC);
		return searchQuery(type, null, searchQueryRaw(type, null, null, qb, null, sb, max));
	}

	@Override
	public <P extends ParaObject> ArrayList<P> findTags(String keyword, int max){
		QueryBuilder qb = QueryBuilders.wildcardQuery(PObject.classname(Tag.class), keyword.concat("*"));
//		SortBuilder sb = SortBuilders.fieldSort("count").order(SortOrder.DESC);
		return searchQuery(PObject.classname(Tag.class), null, null, qb, null, null, true, max);
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> findNearbyObjects(String type, MutableLong page, MutableLong itemcount, 
			String query, int radius, double lat, double lng, String sortby){
		
		FieldSortBuilder sort = SortBuilders.fieldSort(sortby).order(SortOrder.DESC);
		QueryBuilder qb1 = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
				FilterBuilders.geoDistanceFilter("latlng").point(lat, lng).distance(radius, DistanceUnit.KILOMETERS));
		SearchHits hits1 = searchQueryRaw(PObject.classname(Address.class), page, itemcount, qb1, 
				null, sort, Utils.MAX_ITEMS_PER_PAGE);
		
		if(hits1 == null) return new ArrayList<P> ();
			
		String[] ridsarr = new String[(int) hits1.getTotalHits()];
		for (int i = 0; i < hits1.getTotalHits(); i++) {
			Object pid = hits1.getAt(i).getSource().get(DAO.CN_PARENTID);
			if(pid != null) ridsarr[i] = pid.toString();
		}

		QueryBuilder qb2 = QueryBuilders.filteredQuery(QueryBuilders.queryString(query),
				FilterBuilders.idsFilter(type).ids(ridsarr));
		SearchHits hits2 = searchQueryRaw(type, null, null, qb2, null, sort, Utils.MAX_ITEMS_PER_PAGE);

		return searchQuery(type, itemcount, hits2);
	}
	
	private <P extends ParaObject> ArrayList<P> searchQuery(String type, MutableLong page, MutableLong itemcount,
			QueryBuilder query, FilterBuilder filter, String sortfield, boolean reverse, int max){
		SortOrder order = reverse ? SortOrder.DESC : SortOrder.ASC;
		SortBuilder sort = StringUtils.isBlank(sortfield) ? null : SortBuilders.fieldSort(sortfield).order(order);
		return searchQuery(type, itemcount, searchQueryRaw(type, page, itemcount, query, filter, sort, max));
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> searchQuery(String type, MutableLong itemcount, SearchHits hits){
		ArrayList<P> results = new ArrayList<P> ();
		ArrayList<String> keys = new ArrayList<String>();
		
		if(hits == null || StringUtils.isBlank(type)) return new ArrayList<P> ();
		
		try{
			for (SearchHit hit : hits){
				keys.add(hit.getId());
				if(Utils.READ_FROM_INDEX){
					P pobj = fromSource(type, hit.getSource());
					results.add(pobj);
				}
			}
			
			if (!Utils.READ_FROM_INDEX) {
				Map<String, P> fromDB = dao.readAll(keys, true);
				results.addAll(fromDB.values());
//				unindexNulls(type, keys, fromDB);
			}			
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
		return results;
	}
		
	private SearchHits searchQueryRaw(String type, MutableLong page, MutableLong itemcount, 
			QueryBuilder query, FilterBuilder filter, SortBuilder sort, int max){
		if(StringUtils.isBlank(type)) return null;
		if(sort == null) sort = SortBuilders.scoreSort();
//		if(query == null) query = QueryBuilders.matchAllQuery();
		int start = (page == null || page.intValue() < 1 || 
				page.intValue() > Utils.MAX_PAGES) ? 0 : (page.intValue() - 1) * max;
		
		SearchHits hits = null;
		
		try{
			SearchResponse response = getSearchClient().prepareSearch(Utils.INDEX_ALIAS).
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
	
//	private ArrayList<String> searchQueryIds(String type, MutableLong page, MutableLong itemcount, 
//			QueryBuilder query, FilterBuilder filter, SortBuilder sort, int max){
//		ArrayList<String> keys = new ArrayList<String>();
//		SearchHits hits = searchQueryRaw(type, page, itemcount, query, filter, sort, max);
//		if (hits != null) {
//			for (SearchHit hit : hits) {
//				keys.add(hit.getId());
//			}
//		}
//		return keys;
//	}
	
//	public boolean existsTerm(String term, String value){
//		if(StringUtils.isBlank(term) || StringUtils.isBlank(value)) return false;
//		SearchHits hits = null;
//		try{
//			SearchResponse response = getSearchClient().prepareSearch(Utils.INDEX_ALIAS)
//				.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
//				.setFilter(FilterBuilders.termFilter(term, value)).execute().actionGet();
//
//			hits = response.getHits();
//		} catch (Exception e) {
//			logger.log(Level.WARNING, null, e);
//		}
//		return hits != null && hits.getTotalHits() > 0;
//	}
	
	@Override
	public <P extends ParaObject> P findById(String key, String type){
		if(StringUtils.isBlank(key)) return null;
		try {
			return fromSource(type, getSource(key, type));
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
			return null;
		}
	}
	
	@Override
	public Map<String, Object> getSource(String key, String type){
		Map<String, Object> map = new HashMap<String, Object>();
		if(StringUtils.isBlank(key) || StringUtils.isBlank(type)) return map;
		try{
			GetResponse resp = getSearchClient().prepareGet().setIndex(Utils.INDEX_ALIAS).
					setId(key).setType(type).execute().actionGet();
			map = resp.getSource();
		} catch (Exception e) {
			logger.log(Level.WARNING, null, e);
		}
		return map;
	}
	
	private <P extends ParaObject> P fromSource(String type, Map<String, Object> source) throws Exception{
		Class<P> clazz = (Class<P>) Utils.toClass(type);
		if(clazz != null && source != null && !source.isEmpty()){
			P obj = clazz.newInstance();
			BeanUtils.populate(obj, source);
			return obj;
		}else{
			return null;
		}
	}
	
	@Override
	public Long getBeanCount(String classtype){
		return getSearchClient().prepareCount(Utils.INDEX_ALIAS).
				setTypes(classtype).execute().actionGet().getCount();
	}
	
	@Override
	public Long getCount(String classtype, String field, Object term){
		return getSearchClient().prepareCount(Utils.INDEX_ALIAS).
				setTypes(classtype).setQuery(QueryBuilders.termQuery(field, term)).
				execute().actionGet().getCount();
	}
	
	@Override
	public Long getCount(String classtype, String field1, Object term1, String field2, Object term2){
		return getSearchClient().prepareCount(Utils.INDEX_ALIAS).
				setTypes(classtype).setQuery(QueryBuilders.filteredQuery(
				QueryBuilders.termQuery(field1, term1), 
				FilterBuilders.termFilter(field2, term2))).
				execute().actionGet().getCount();
	}
	
//	private void unindexNulls(final String type, final List<String> keys, final Map<String, PObject> fromDB){
//		Utils.asyncExecute(new Callable<Object>(){
//			public Object call() throws Exception {
//				Map<String, PObject> dbKeys;
//				if(fromDB == null){
//					dbKeys = AWSDynamoDAO.getInstance().readAll(keys, !Utils.READ_FROM_INDEX);
//				}else{
//					dbKeys = fromDB;
//				}			
//				// remove objects that are still indexed but not in the database
//				if (!dbKeys.isEmpty() && dbKeys.values().contains(null)) {
//					BulkRequestBuilder brb = getSearchClient().prepareBulk();
//
//					for (Iterator<Map.Entry<String, PObject>> it = dbKeys.entrySet().iterator(); it.hasNext();) {
//						Map.Entry<String, PObject> entry = it.next();
//						String key = entry.getKey();
//						PObject value = entry.getValue();
//						if (value == null) {
//							brb.add(getSearchClient().prepareDelete(Utils.INDEX_ALIAS, type, key).request());
//						}
//					}
//					brb.execute();
//				}
//				return true;
//			}
//		});
//	}
	
}
