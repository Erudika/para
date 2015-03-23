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

import com.erudika.para.Para;
import com.erudika.para.core.ParaObject;
import com.erudika.para.persistence.DAO;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.optimize.OptimizeResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.metadata.AliasAction;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.FilterBuilders;
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
	private static Client searchClient;
	private static Node searchNode;

	private ElasticSearchUtils() { }

	/**
	 * Creates an instance of the client that talks to Elasticsearch.
	 * @return a client instance
	 */
	public static Client getClient() {
		if (searchClient != null) {
			return searchClient;
		}
		boolean localNode = Config.getConfigParamUnwrapped("es.local_node", true);
		boolean dataNode = Config.getConfigParamUnwrapped("es.data_node", true);
		boolean corsEnabled = Config.getConfigParamUnwrapped("es.cors_enabled", !Config.IN_PRODUCTION);
		String esHome = Config.getConfigParam("es.dir", "");

		ImmutableSettings.Builder settings = ImmutableSettings.settingsBuilder();
		settings.put("node.name", getNodeName());
		settings.put("client.transport.sniff", true);
		settings.put("action.disable_delete_all_indices", true);
		settings.put("cluster.name", Config.CLUSTER_NAME);
		settings.put("http.cors.enabled", corsEnabled);
		settings.put("path.data", esHome + "data");
		settings.put("path.work", esHome + "work");
		settings.put("path.logs", esHome + "logs");

		if (Config.IN_PRODUCTION) {
			settings.put("cloud.aws.access_key", Config.AWS_ACCESSKEY);
			settings.put("cloud.aws.secret_key", Config.AWS_SECRETKEY);
			settings.put("cloud.aws.region", Config.AWS_REGION);
			settings.put("network.tcp.keep_alive", true);
			settings.put("discovery.type", Config.getConfigParam("discovery_type", "ec2"));
			settings.put("discovery.ec2.groups", "elasticsearch");
			searchNode = NodeBuilder.nodeBuilder().settings(settings).local(localNode).data(dataNode).node();
			searchClient = searchNode.client();
		} else if ("embedded".equals(Config.ENVIRONMENT)) {
			// for local develoment only
			searchNode = NodeBuilder.nodeBuilder().settings(settings).local(localNode).data(dataNode).node();
			searchClient = searchNode.client();
		} else {
			searchClient = new TransportClient(settings);
				((TransportClient) searchClient).addTransportAddress(
						new InetSocketTransportAddress("localhost", 9300));
		}

		if (!existsIndex(Config.APP_NAME_NS)) {
			createIndex(Config.APP_NAME_NS);
		}

		Para.addDestroyListener(new Para.DestroyListener() {
			public void onDestroy() {
				shutdownClient();
			}
		});

		// wait for the shards to initialize to prevent NoShardAvailableActionException
		searchClient.admin().cluster().prepareHealth(Config.APP_NAME_NS).setWaitForGreenStatus().execute().actionGet();

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

	/**
	 * Creates a new search index.
	 * @param appid the index name (alias)
	 * @return true if created
	 */
	public static boolean createIndex(String appid) {
		return createIndex(appid, Integer.valueOf(Config.getConfigParam("es.shards", "5")),
				Integer.valueOf(Config.getConfigParam("es.replicas", "0")));
	}

	/**
	 * Creates a new search index.
	 * @param appid the index name (alias)
	 * @param shards number of shards
	 * @param replicas number of replicas
	 * @return true if created
	 */
	public static boolean createIndex(String appid, int shards, int replicas) {
		if (StringUtils.isBlank(appid) || StringUtils.containsWhitespace(appid) || existsIndex(appid)) {
			return false;
		}
		try {
			NodeBuilder nb = NodeBuilder.nodeBuilder();
			nb.settings().put("number_of_shards", Integer.toString(shards));
			nb.settings().put("number_of_replicas", Integer.toString(replicas));
			nb.settings().put("auto_expand_replicas", "0-all");
			nb.settings().put("analysis.analyzer.default.type", "standard");
			nb.settings().putArray("analysis.analyzer.default.stopwords",
					"arabic", "armenian", "basque", "brazilian", "bulgarian", "catalan",
					"czech", "danish", "dutch", "english", "finnish", "french", "galician",
					"german", "greek", "hindi", "hungarian", "indonesian", "italian",
					"norwegian", "persian", "portuguese", "romanian", "russian", "spanish",
					"swedish", "turkish");

			String name = appid + "1";
			CreateIndexRequestBuilder create = getClient().admin().indices().prepareCreate(name).
					setSettings(nb.settings().build());

			// default system mapping (all the rest are dynamic)
			create.addMapping("_default_", getDefaultMapping());
			create.execute().actionGet();

			addIndexAlias(name, appid);
		} catch (Exception e) {
			logger.warn(null, e);
			return false;
		}
		return true;
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
		boolean exists = false;
		try {
			exists = getClient().admin().indices().prepareExists(appid).execute().
					actionGet().isExists();
		} catch (Exception e) {
			logger.warn(null, e);
		}
		return exists;
	}

	/**
	 * Rebuilds an index. Reads objects from the data store and indexes them in batches.
	 * @param appid the index name (alias)
	 * @param dao an instance of the persistence class
	 * @return true if successful
	 */
	public static boolean rebuildIndex(String appid, DAO dao) {
		if (StringUtils.isBlank(appid) || dao == null) {
			return false;
		}
		try {
			if (!existsIndex(appid)) {
				return false;
			}
			String oldName = getIndexNameForAlias(appid);
			if (oldName == null) {
				return false;
			}
			String newName = oldName + "_" + Utils.timestamp();

			logger.info("rebuildIndex(): {}", appid);

			BulkRequestBuilder brb = getClient().prepareBulk();
			BulkResponse resp = null;
			Pager pager = new Pager();

			List<ParaObject> list = dao.readPage(appid, null);

			if (!list.isEmpty()) {
				do {
					for (ParaObject obj : list) {
						brb.add(getClient().prepareIndex(appid, obj.getType(), obj.getId()).
								setSource(Utils.getAnnotatedFields(obj)));
						pager.setLastKey(obj.getId());
					}
					// bulk index 1000 objects
					if (brb.numberOfActions() > 100) {
						resp = brb.execute().actionGet();
						logger.info("rebuildIndex(): indexed {}, hasFailures: {}",
								brb.numberOfActions(), resp.hasFailures());
					}
				} while (!(list = dao.readPage(appid, pager)).isEmpty());
			}

			// anything left after loop? index that too
			if (brb.numberOfActions() > 0) {
				resp = brb.execute().actionGet();
				logger.info("rebuildIndex(): indexed {}, hasFailures: {}",
						brb.numberOfActions(), resp.hasFailures());
			}

			// switch to alias NEW_INDEX -> ALIAS, OLD_INDEX -> X, deleting the old index
			switchIndexToAlias(oldName, newName, appid, true);
		} catch (Exception e) {
			logger.warn(null, e);
			return false;
		}
		return true;
	}

	/**
	 * Optimizes an index. This method might be deprecated in the future.
	 * @param appid the index name (alias)
	 * @return true if successful
	 */
	public static boolean optimizeIndex(String appid) {
		if (StringUtils.isBlank(appid)) {
			return false;
		}
		boolean result = false;
		try {
			OptimizeResponse resp = getClient().admin().indices().
					prepareOptimize(appid).execute().actionGet();

			result = resp.getFailedShards() == 0;
		} catch (Exception e) {
			logger.warn(null, e);
		}
		return result;
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
	public static boolean addIndexAlias(String indexName, final String alias, boolean setRouting) {
		AliasAction act = new AliasAction(AliasAction.Type.ADD, indexName, alias);
		if (setRouting) {
			act.searchRouting(alias);
			act.indexRouting(alias);
			act.filter(FilterBuilders.termFilter(Config._APPID, alias));
		}
		return getClient().admin().indices().prepareAliases().addAliasAction(act).
				execute().actionGet().isAcknowledged();
	}

	/**
	 * Removes an alias from an index.
	 * @param indexName the index name
	 * @param alias the alias
	 * @return true if acknowledged
	 */
	public static boolean removeIndexAlias(String indexName, String alias) {
		return getClient().admin().indices().prepareAliases().removeAlias(indexName, alias).
				execute().actionGet().isAcknowledged();
	}

	/**
	 * Replaces the index to which an alias points with another index.
	 * @param oldIndex the index name to be replaced
	 * @param newIndex the new index name to switch to
	 * @param alias the alias (unchanged)
	 * @param deleteOld if true will delete the old index completely
	 */
	public static void switchIndexToAlias(String oldIndex, String newIndex, String alias, boolean deleteOld) {
		getClient().admin().indices().prepareAliases().
				addAlias(newIndex, alias).
				removeAlias(oldIndex, alias).
				execute().actionGet();
		// delete the old index
		if (deleteOld) {
			deleteIndex(oldIndex);
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
		} else {
			return aliases.keysIt().next();
		}
		return null;
	}

	/**
	 * A list of default mappings that are defined upon index creation.
	 * @return a json object of default mappings
	 * @throws Exception
	 */
	private static XContentBuilder getDefaultMapping() throws Exception {
		return XContentFactory.jsonBuilder().
			startObject().
				startObject("_default_").
					startObject("properties").
						startObject("latlng").field("type", "geo_point").field("lat_lon", true).endObject().
						startObject("tag").field("type", "string").field("index", "not_analyzed").endObject().
						startObject(Config._ID).field("type", "string").field("index", "not_analyzed").endObject().
						startObject(Config._KEY).field("type", "string").field("index", "not_analyzed").endObject().
						startObject(Config._APPID).field("type", "string").field("index", "not_analyzed").endObject().
//						startObject(Config._TAGS).field("type", "string").field("index", "not_analyzed").endObject().
						startObject(Config._EMAIL).field("type", "string").field("index", "not_analyzed").endObject().
						startObject(Config._GROUPS).field("type", "string").field("index", "not_analyzed").endObject().
						startObject(Config._UPDATED).field("type", "string").field("index", "not_analyzed").endObject().
						startObject(Config._PASSWORD).field("type", "string").field("index", "not_analyzed").endObject().
						startObject(Config._PARENTID).field("type", "string").field("index", "not_analyzed").endObject().
						startObject(Config._CREATORID).field("type", "string").field("index", "not_analyzed").endObject().
						startObject(Config._TYPE).field("type", "string").field("index", "not_analyzed").endObject().
						startObject(Config._TIMESTAMP).field("type", "string").field("index", "not_analyzed").endObject().
						startObject(Config._IDENTIFIER).field("type", "string").field("index", "not_analyzed").endObject().
						startObject(Config._RESET_TOKEN).field("type", "string").field("index", "not_analyzed").endObject().
					endObject().
				endObject().
			endObject();
	}

}
