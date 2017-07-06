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
package com.erudika.para.search;

import com.erudika.para.DestroyListener;
import com.erudika.para.Para;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.cluster.metadata.AliasAction;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper utilities for connecting to an Elasticsearch cluster.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class ElasticSearchUtils {

	private static final Logger logger = LoggerFactory.getLogger(ElasticSearchUtils.class);
	private static final StandardQueryParser QUERY_PARSER = new StandardQueryParser();
	private static Client searchClient;
	private static Node searchNode;
	/**
	 * A list of default mappings that are defined upon index creation.
	 */
	private static final String DEFAULT_MAPPING =
			"{\n" +
			"  \"_default_\": {\n" +
			"    \"properties\": {\n" +
			"      \"nstd\": {\"type\": \"nested\"},\n" +
			"      \"latlng\": {\"type\": \"geo_point\"},\n" +
			"      \"tag\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"id\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"key\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"name\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"type\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"tags\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"email\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"appid\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"groups\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"updated\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"password\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"parentid\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"creatorid\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"timestamp\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"identifier\": {\"type\": \"string\", \"index\": \"not_analyzed\"},\n" +
			"      \"token\": {\"type\": \"string\", \"index\": \"not_analyzed\"}\n" +
			"    }\n" +
			"  }\n" +
			"}";

	private ElasticSearchUtils() { }

	/**
	 * Creates an instance of the client that talks to Elasticsearch.
	 * @return a client instance
	 */
	public static Client getClient() {
		if (searchClient != null) {
			return searchClient;
		}
		boolean localNode = Config.getConfigBoolean("es.local_node", true);
		boolean dataNode = Config.getConfigBoolean("es.data_node", true);
		boolean corsEnabled = Config.getConfigBoolean("es.cors_enabled", !Config.IN_PRODUCTION);
		String corsAllowOrigin = Config.getConfigParam("es.cors_allow_origin", "/https?:\\/\\/localhost(:[0-9]+)?/");
		String esHome = Config.getConfigParam("es.dir", Paths.get(".").toAbsolutePath().normalize().toString());
		String esHost = Config.getConfigParam("es.transportclient_host", "localhost");
		int esPort = Config.getConfigInt("es.transportclient_port", 9300);
		boolean useTransportClient = Config.getConfigBoolean("es.use_transportclient", false);

		Settings.Builder settings = Settings.builder();
		settings.put("node.name", getNodeName());
		settings.put("client.transport.sniff", true);
		settings.put("action.disable_delete_all_indices", true);
		settings.put("cluster.name", Config.CLUSTER_NAME);
		settings.put("http.cors.enabled", corsEnabled);
		settings.put("http.cors.allow-origin", corsAllowOrigin);
		settings.put("path.home", esHome);
		settings.put("path.data", esHome + File.separator + "data");
		settings.put("path.work", esHome + File.separator + "work");
		settings.put("path.logs", esHome + File.separator + "logs");

		if (Config.IN_PRODUCTION) {
			String discoveryType = Config.getConfigParam("es.discovery_type", "ec2");
			settings.put("cloud.aws.access_key", Config.AWS_ACCESSKEY);
			settings.put("cloud.aws.secret_key", Config.AWS_SECRETKEY);
			settings.put("cloud.aws.region", Config.AWS_REGION);
			settings.put("network.tcp.keep_alive", true);
			settings.put("discovery.type", discoveryType);
			settings.put("discovery.ec2.ping_timeout", "10s");
			if ("ec2".equals(discoveryType)) {
				settings.put("discovery.ec2.groups", Config.getConfigParam("es.discovery_group", "elasticsearch"));
			}
		}

		if (useTransportClient) {
			searchClient = TransportClient.builder().settings(settings).build();
			InetSocketTransportAddress addr;
			try {
				addr = new InetSocketTransportAddress(InetAddress.getByName(esHost), esPort);
			} catch (UnknownHostException ex) {
				addr = new InetSocketTransportAddress(InetAddress.getLoopbackAddress(), esPort);
				logger.warn("Unknown host: " + esHost, ex);
			}
			((TransportClient) searchClient).addTransportAddress(addr);
		} else {
			searchNode = NodeBuilder.nodeBuilder().settings(settings).local(localNode).data(dataNode).node().start();
			searchClient = searchNode.client();
		}

		Para.addDestroyListener(new DestroyListener() {
			public void onDestroy() {
				shutdownClient();
			}
		});
		// wait for the shards to initialize - prevents NoShardAvailableActionException!
		String timeout = Config.IN_PRODUCTION ? "1m" : "5s";
		searchClient.admin().cluster().prepareHealth(Config.APP_NAME_NS).
				setWaitForGreenStatus().setTimeout(timeout).execute().actionGet();

		if (!existsIndex(Config.APP_NAME_NS)) {
			createIndex(Config.APP_NAME_NS);
		}
		return searchClient;
	}

	/**
	 * Stops the client instance and releases resources.
	 */
	protected static void shutdownClient() {
		if (searchClient != null) {
			searchClient.close();
			searchClient = null;
		}
		if (searchNode != null) {
			searchNode.close();
			searchNode = null;
		}
	}

	private static String getNodeName() {
		return Config.PARA.concat("-es-").concat(Config.WORKER_ID);
	}

	private static boolean createIndexWithoutAlias(String name, int shards, int replicas) {
		if (StringUtils.isBlank(name) || StringUtils.containsWhitespace(name) || existsIndex(name)) {
			return false;
		}
		if (shards <= 0) {
			shards = Config.getConfigInt("es.shards", 5);
		}
		if (replicas < 0) {
			replicas = Config.getConfigInt("es.replicas", 0);
		}
		try {
			NodeBuilder nb = NodeBuilder.nodeBuilder();
			nb.settings().put("number_of_shards", Integer.toString(shards));
			nb.settings().put("number_of_replicas", Integer.toString(replicas));
			nb.settings().put("auto_expand_replicas", Config.getConfigParam("es.auto_expand_replicas", "0-1"));
			nb.settings().put("analysis.analyzer.default.type", "standard");
			nb.settings().putArray("analysis.analyzer.default.stopwords",
					"arabic", "armenian", "basque", "brazilian", "bulgarian", "catalan",
					"czech", "danish", "dutch", "english", "finnish", "french", "galician",
					"german", "greek", "hindi", "hungarian", "indonesian", "italian",
					"norwegian", "persian", "portuguese", "romanian", "russian", "spanish",
					"swedish", "turkish");

			CreateIndexRequestBuilder create = getClient().admin().indices().prepareCreate(name).
					setSettings(nb.settings().build());

			// default system mapping (all the rest are dynamic)
			create.addMapping("_default_", DEFAULT_MAPPING);
			create.execute().actionGet();
			logger.info("Created a new index '{}' with {} shards, {} replicas.", name, shards, replicas);
		} catch (Exception e) {
			logger.warn(null, e);
			return false;
		}
		return true;
	}

	/**
	 * Creates a new search index.
	 * @param appid the index name (alias)
	 * @return true if created
	 */
	public static boolean createIndex(String appid) {
		return createIndex(appid, Config.getConfigInt("es.shards", 5), Config.getConfigInt("es.replicas", 0));
	}

	/**
	 * Creates a new search index.
	 * @param appid the index name (alias)
	 * @param shards number of shards
	 * @param replicas number of replicas
	 * @return true if created
	 */
	public static boolean createIndex(String appid, int shards, int replicas) {
		if (StringUtils.isBlank(appid)) {
			return false;
		}
		String name = appid + "_1";
		boolean created = createIndexWithoutAlias(name, shards, replicas);
		if (created) {
			boolean aliased = addIndexAlias(name, appid);
			if (!aliased) {
				logger.warn("Index '{}' was created but not aliased to '{}'.", name, appid);
			}
		}
		return created;
	}

	/**
	 * Deletes an existing search index.
	 * @param appid the index name (alias)
	 * @return true if deleted
	 */
	public static boolean deleteIndex(String appid) {
		if (StringUtils.isBlank(appid) || !existsIndex(appid)) {
			return false;
		}
		try {
			logger.info("Deleted index '{}'.", appid);
			getClient().admin().indices().prepareDelete(appid).execute().actionGet();
		} catch (Exception e) {
			logger.warn(null, e);
			return false;
		}
		return true;
	}

	/**
	 * Checks if the index exists.
	 * @param appid the index name (alias)
	 * @return true if exists
	 */
	public static boolean existsIndex(String appid) {
		if (StringUtils.isBlank(appid)) {
			return false;
		}
		boolean exists = true;
		try {
			exists = getClient().admin().indices().prepareExists(appid).execute().
					actionGet().isExists();
		} catch (Exception e) {
			logger.warn(null, e);
		}
		return exists;
	}

	/**
	 * Rebuilds an index.
	 * Reads objects from the data store and indexes them in batches.
	 * Works on one DB table and index only.
	 * @param appid the index name (alias)
	 * @param isShared is the app shared, controls index aliases and index switching
	 * @param pager a Pager instance
	 * @return true if successful, false if index doesn't exist or failed.
	 */
	public static boolean rebuildIndex(String appid, boolean isShared, Pager... pager) {
		if (StringUtils.isBlank(appid)) {
			return false;
		}
		try {
			if (!existsIndex(appid)) {
				logger.warn("Can't rebuild '{}' - index doesn't exist.", appid);
				return false;
			}
			String oldName = getIndexNameForAlias(appid);
			String newName = appid;

			if (oldName == null) {
				return false;
			}
			if (!isShared) {
				newName = oldName.substring(0, oldName.indexOf('_')) + "_" + Utils.timestamp();
				createIndexWithoutAlias(newName, -1, -1);
			}

			logger.info("rebuildIndex(): {}", appid);

			BulkRequestBuilder brb = getClient().prepareBulk();
			BulkResponse resp;
			int queueSize = 50;
			int count = 0;
			Pager p = getPager(pager);
			p.setLimit(100);

			List<ParaObject> list;
			do {
				list = Para.getDAO().readPage(appid, p);
				logger.debug("rebuildIndex(): Read {} objects from table {}.", list.size(), appid);
				for (ParaObject obj : list) {
					if (obj != null) {
						// put objects from DB into the newly created index
						brb.add(getClient().prepareIndex(newName, obj.getType(), obj.getId()).
								setSource(ParaObjectUtils.getAnnotatedFields(obj, null, false)).request());
						// index in batches of ${queueSize} objects
						if (brb.numberOfActions() >= queueSize) {
							count += brb.numberOfActions();
							resp = brb.execute().actionGet();
							logger.info("rebuildIndex(): indexed {}, failures: {}",
									brb.numberOfActions(), resp.hasFailures() ? resp.buildFailureMessage() : "false");
							brb = getClient().prepareBulk();
						}
					}
				}
			} while (!list.isEmpty());

			// anything left after loop? index that too
			if (brb.numberOfActions() > 0) {
				count += brb.numberOfActions();
				resp = brb.execute().actionGet();
				logger.info("rebuildIndex(): indexed {}, failures: {}",
						brb.numberOfActions(), resp.hasFailures() ? resp.buildFailureMessage() : "false");
			}

			if (!isShared) {
				// switch to alias NEW_INDEX -> ALIAS, OLD_INDEX -> DELETE old index
				switchIndexToAlias(oldName, newName, appid, true);
			}
			logger.info("rebuildIndex(): Done. {} objects reindexed.", count);
		} catch (Exception e) {
			logger.warn(null, e);
			return false;
		}
		return true;
	}

	protected static Pager getPager(Pager[] pager) {
		return (pager != null && pager.length > 0) ? pager[0] : new Pager();
	}

	/**
	 * Returns information about a cluster.
	 * @return a map of key value pairs containing cluster information
	 */
	public static Map<String, String> getSearchClusterInfo() {
		Map<String, String> md = new HashMap<String, String>();
		NodesInfoResponse res = getClient().admin().cluster().nodesInfo(new NodesInfoRequest().all()).actionGet();
		md.put("cluser.name", res.getClusterName().toString());

		for (NodeInfo nodeInfo : res) {
			md.put("node.name", nodeInfo.getNode().getName());
			md.put("node.address", nodeInfo.getNode().getAddress().toString());
			md.put("node.data", Boolean.toString(nodeInfo.getNode().isDataNode()));
			md.put("node.client", Boolean.toString(nodeInfo.getNode().isClientNode()));
			md.put("node.version", nodeInfo.getNode().getVersion().toString());
		}
		return md;
	}

	/**
	 * Adds a new alias to an existing index.
	 * @param indexName the index name
	 * @param alias the alias
	 * @return true if acknowledged
	 */
	public static boolean addIndexAlias(String indexName, String alias) {
		return addIndexAlias(indexName, alias, false);
	}

	/**
	 * Adds a new alias to an existing index.
	 * @param indexName the index name
	 * @param alias the alias
	 * @param setRouting if true will route by appid (alias)
	 * @return true if acknowledged
	 */
	public static boolean addIndexAlias(String indexName, String alias, boolean setRouting) {
		if (!existsIndex(indexName)) {
			return false;
		}
		try {
			AliasAction act = new AliasAction(AliasAction.Type.ADD, indexName, alias);
			if (setRouting) {
				act.searchRouting(alias);
				act.indexRouting(alias);
				act.filter(QueryBuilders.termQuery(Config._APPID, alias));
			}
			return getClient().admin().indices().prepareAliases().addAliasAction(act).
					execute().actionGet().isAcknowledged();
		} catch (Exception e) {
			logger.error(null, e);
			return false;
		}
	}

	/**
	 * Removes an alias from an index.
	 * @param indexName the index name
	 * @param alias the alias
	 * @return true if acknowledged
	 */
	public static boolean removeIndexAlias(String indexName, String alias) {
		if (!existsIndex(indexName)) {
			return false;
		}
		return getClient().admin().indices().prepareAliases().removeAlias(indexName, alias).
				execute().actionGet().isAcknowledged();
	}

	/**
	 * Checks if an index has a registered alias.
	 * @param indexName the index name
	 * @param alias the alias
	 * @return true if alias is set on index
	 */
	public static boolean existsIndexAlias(String indexName, String alias) {
		return getClient().admin().indices().prepareAliasesExist(indexName).addAliases(alias).
				execute().actionGet().exists();
	}

	/**
	 * Replaces the index to which an alias points with another index.
	 * @param oldIndex the index name to be replaced
	 * @param newIndex the new index name to switch to
	 * @param alias the alias (unchanged)
	 * @param deleteOld if true will delete the old index completely
	 */
	public static void switchIndexToAlias(String oldIndex, String newIndex, String alias, boolean deleteOld) {
		logger.info("Switching index aliases {}->{}, deleting index '{}': {}", alias, newIndex, oldIndex, deleteOld);
		try {
			getClient().admin().indices().prepareAliases().
					addAlias(newIndex, alias).
					removeAlias(oldIndex, alias).
					execute().actionGet();
			// delete the old index
			if (deleteOld) {
				deleteIndex(oldIndex);
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	/**
	 * Returns the real index name for a given alias.
	 * @param appid the index name (alias)
	 * @return the real index name (not alias)
	 */
	public static String getIndexNameForAlias(String appid) {
		if (StringUtils.isBlank(appid)) {
			return null;
		}
		GetAliasesResponse get = getClient().admin().indices().
				prepareGetAliases(appid).execute().actionGet();
		ImmutableOpenMap<String, List<AliasMetaData>> aliases = get.getAliases();
		if (aliases.size() > 1) {
			logger.warn("More than one index for alias {}", appid);
		} else if (!aliases.isEmpty()) {
			return aliases.keysIt().next();
		}
		return null;
	}

	/**
	 * Check if cluster status is green or yellow.
	 * @return false if status is red
	 */
	public static boolean isClusterOK() {
		return !getClient().admin().cluster().prepareClusterStats().execute().actionGet().
				getStatus().equals(ClusterHealthStatus.RED);
	}

	/**
	 * @return true if asynchronous indexing/unindexing is enabled.
	 */
	static boolean isAsyncEnabled() {
		return Config.getConfigBoolean("es.async_enabled", false);
	}

	/**
	 * Creates a term filter for a set of terms.
	 * @param terms some terms
	 * @param mustMatchAll if true all terms must match ('AND' operation)
	 * @return the filter
	 */
	static QueryBuilder getTermsQuery(Map<String, ?> terms, boolean mustMatchAll) {
		BoolQueryBuilder fb = QueryBuilders.boolQuery();
		int addedTerms = 0;
		boolean noop = true;
		QueryBuilder bfb = null;

		for (Map.Entry<String, ?> term : terms.entrySet()) {
			Object val = term.getValue();
			if (!StringUtils.isBlank(term.getKey()) && val != null) {
				if (val instanceof String && StringUtils.isBlank((String) val)) {
					continue;
				}
				Matcher matcher = Pattern.compile(".*(<|>|<=|>=)$").matcher(term.getKey().trim());
				bfb = QueryBuilders.termQuery(term.getKey(), val);
				if (matcher.matches()) {
					String key = term.getKey().replaceAll("[<>=\\s]+$", "");
					RangeQueryBuilder rfb = QueryBuilders.rangeQuery(key);
					if (">".equals(matcher.group(1))) {
						bfb = rfb.gt(val);
					} else if ("<".equals(matcher.group(1))) {
						bfb = rfb.lt(val);
					} else if (">=".equals(matcher.group(1))) {
						bfb = rfb.gte(val);
					} else if ("<=".equals(matcher.group(1))) {
						bfb = rfb.lte(val);
					}
				}
				if (mustMatchAll) {
					fb.must(bfb);
				} else {
					fb.should(bfb);
				}
				addedTerms++;
				noop = false;
			}
		}
		if (addedTerms == 1 && bfb != null) {
			return bfb;
		}
		return noop ? null : fb;
	}

	/**
	 * Tries to parse a query string in order to check if it is valid.
	 * @param query a Lucene query string
	 * @return the query if valid, or '*' if invalid
	 */
	static String qs(String query) {
		if (query == null) {
			query = "*";
		}
		query = query.trim();
		if (query.length() > 1 && query.startsWith("*")) {
			query = query.substring(1);
		}
		if (query.length() > 1 && query.contains(" *")) {
			query = query.replaceAll("\\s\\*", " ").trim();
		}
		if (query.length() >= 2 && query.toLowerCase().endsWith("and") ||
				query.toLowerCase().endsWith("or") || query.toLowerCase().endsWith("not")) {
			query = query.substring(0, query.length() - (query.toLowerCase().endsWith("or") ? 2 : 3));
		}
		try {
			QUERY_PARSER.setAllowLeadingWildcard(false);
			QUERY_PARSER.parse(query, "");
		} catch (Exception ex) {
			query = "*";
		}
		return query.trim();
	}

	/**
	 * A method reserved for future use. It allows to have indexes with different names than the appid.
	 *
	 * @param appid an app identifer
	 * @return the correct index name
	 */
	protected static String getIndexName(String appid) {
		return appid;
	}

}
