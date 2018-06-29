/*
 * Copyright 2013-2018 Erudika. https://erudika.com
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

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.KeyAttribute;
import com.amazonaws.services.dynamodbv2.document.Page;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.BatchGetItemRequest;
import com.amazonaws.services.dynamodbv2.model.BatchGetItemResult;
import com.amazonaws.services.dynamodbv2.model.BatchWriteItemRequest;
import com.amazonaws.services.dynamodbv2.model.BatchWriteItemResult;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.KeysAndAttributes;
import com.amazonaws.services.dynamodbv2.model.ListTablesResult;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.model.UpdateTableRequest;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;
import com.erudika.para.DestroyListener;
import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper utilities for connecting to AWS DynamoDB.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class AWSDynamoUtils {

	private static AmazonDynamoDB ddbClient;
	private static DynamoDB ddb;
	private static final String LOCAL_ENDPOINT = "http://localhost:8000";
	private static final Logger logger = LoggerFactory.getLogger(AWSDynamoUtils.class);

	/**
	 * The name of the shared table. Default is "0".
	 */
	public static final String SHARED_TABLE = Config.getConfigParam("shared_table_name", "0");

	private AWSDynamoUtils() { }

	/**
	 * Returns a client instance for AWS DynamoDB.
	 * @return a client that talks to DynamoDB
	 */
	public static AmazonDynamoDB getClient() {
		if (ddbClient != null) {
			return ddbClient;
		}

		if (Config.IN_PRODUCTION) {
			ddbClient = AmazonDynamoDBClientBuilder.standard().build();
		} else {
			ddbClient = AmazonDynamoDBClientBuilder.standard().
					withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "null"))).
					withEndpointConfiguration(new EndpointConfiguration(LOCAL_ENDPOINT, "")).build();
		}

		if (!existsTable(Config.getRootAppIdentifier())) {
			createTable(Config.getRootAppIdentifier());
		}

		ddb = new DynamoDB(ddbClient);

		Para.addDestroyListener(new DestroyListener() {
			public void onDestroy() {
				shutdownClient();
			}
		});

		return ddbClient;
	}

	/**
	 * Stops the client and releases resources.
	 * <b>There's no need to call this explicitly!</b>
	 */
	protected static void shutdownClient() {
		if (ddbClient != null) {
			ddbClient.shutdown();
			ddbClient = null;
			ddb.shutdown();
			ddb = null;
		}
	}

	/**
	 * Checks if the main table exists in the database.
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @return true if the table exists
	 */
	public static boolean existsTable(String appid) {
		if (StringUtils.isBlank(appid)) {
			return false;
		}
		try {
			DescribeTableResult res = getClient().describeTable(getTableNameForAppid(appid));
			return res != null;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Creates a DynamoDB table with 1 read/s, 1 write/s.
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @return true if created
	 */
	public static boolean createTable(String appid) {
		return createTable(appid, 1L, 1L);
	}

	/**
	 * Creates a table in AWS DynamoDB.
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param readCapacity read capacity
	 * @param writeCapacity write capacity
	 * @return true if created
	 */
	public static boolean createTable(String appid, long readCapacity, long writeCapacity) {
		if (StringUtils.isBlank(appid)) {
			return false;
		} else if (StringUtils.containsWhitespace(appid)) {
			logger.warn("DynamoDB table name contains whitespace. The name '{}' is invalid.", appid);
			return false;
		} else if (existsTable(appid)) {
			logger.warn("DynamoDB table '{}' already exists.", appid);
			return false;
		}
		try {
			String table = getTableNameForAppid(appid);
			getClient().createTable(new CreateTableRequest().withTableName(table).
					withKeySchema(new KeySchemaElement(Config._KEY, KeyType.HASH)).
					withAttributeDefinitions(new AttributeDefinition(Config._KEY, ScalarAttributeType.S)).
					withProvisionedThroughput(new ProvisionedThroughput(readCapacity, writeCapacity)));
			logger.info("Created DynamoDB table '{}'.", table);
		} catch (Exception e) {
			logger.error(null, e);
			return false;
		}
		return true;
	}

	/**
	 * Updates the table settings (read and write capacities).
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param readCapacity read capacity
	 * @param writeCapacity write capacity
	 * @return true if updated
	 */
	public static boolean updateTable(String appid, long readCapacity, long writeCapacity) {
		if (StringUtils.isBlank(appid) || StringUtils.containsWhitespace(appid)) {
			return false;
		}
		String table = getTableNameForAppid(appid);
		try {
			// AWS throws an exception if the new read/write capacity values are the same as the current ones
			getClient().updateTable(new UpdateTableRequest().withTableName(table).
					withProvisionedThroughput(new ProvisionedThroughput(readCapacity, writeCapacity)));
			return true;
		} catch (Exception e) {
			logger.error("Could not update table '{}' - table is not active or no change to capacity: {}",
					table, e.getMessage());
		}
		return false;
	}

	/**
	 * Deletes the main table from AWS DynamoDB.
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @return true if deleted
	 */
	public static boolean deleteTable(String appid) {
		if (StringUtils.isBlank(appid) || !existsTable(appid)) {
			return false;
		}
		try {
			String table = getTableNameForAppid(appid);
			getClient().deleteTable(new DeleteTableRequest().withTableName(table));
			logger.info("Deleted DynamoDB table '{}'.", table);
		} catch (Exception e) {
			logger.error(null, e);
			return false;
		}
		return true;
	}

	/**
	 * Creates a table in AWS DynamoDB which will be shared between apps.
	 * @param readCapacity read capacity
	 * @param writeCapacity write capacity
	 * @return true if created
	 */
	public static boolean createSharedTable(long readCapacity, long writeCapacity) {
		if (StringUtils.isBlank(SHARED_TABLE) || StringUtils.containsWhitespace(SHARED_TABLE) ||
				existsTable(SHARED_TABLE)) {
			return false;
		}
		try {
			GlobalSecondaryIndex secIndex = new GlobalSecondaryIndex().
					withIndexName(getSharedIndexName()).
					withProvisionedThroughput(new ProvisionedThroughput().
							withReadCapacityUnits(1L).
							withWriteCapacityUnits(1L)).
					withProjection(new Projection().withProjectionType(ProjectionType.ALL)).
					withKeySchema(new KeySchemaElement().withAttributeName(Config._APPID).withKeyType(KeyType.HASH),
							new KeySchemaElement().withAttributeName(Config._ID).withKeyType(KeyType.RANGE));

			getClient().createTable(new CreateTableRequest().withTableName(getTableNameForAppid(SHARED_TABLE)).
					withKeySchema(new KeySchemaElement(Config._KEY, KeyType.HASH)).
					withAttributeDefinitions(new AttributeDefinition(Config._KEY, ScalarAttributeType.S),
							new AttributeDefinition(Config._APPID, ScalarAttributeType.S),
							new AttributeDefinition(Config._ID, ScalarAttributeType.S)).
					withGlobalSecondaryIndexes(secIndex).
					withProvisionedThroughput(new ProvisionedThroughput(readCapacity, writeCapacity)));
		} catch (Exception e) {
			logger.error(null, e);
			return false;
		}
		return true;
	}

	/**
	 * Gives basic information about a DynamoDB table (status, creation date, size).
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @return a map
	 */
	public static Map<String, Object> getTableStatus(final String appid) {
		if (StringUtils.isBlank(appid)) {
			return Collections.emptyMap();
		}
		try {
			final TableDescription td = getClient().describeTable(getTableNameForAppid(appid)).getTable();
			HashMap<String, Object> dbStatus = new HashMap<>();
			dbStatus.put("id", appid);
			dbStatus.put("status", td.getTableStatus());
			dbStatus.put("created", td.getCreationDateTime().getTime());
			dbStatus.put("sizeBytes", td.getTableSizeBytes());
			dbStatus.put("itemCount", td.getItemCount());
			dbStatus.put("readCapacityUnits", td.getProvisionedThroughput().getReadCapacityUnits());
			dbStatus.put("writeCapacityUnits", td.getProvisionedThroughput().getWriteCapacityUnits());
			return dbStatus;
		} catch (Exception e) {
			logger.error(null, e);
		}
		return Collections.emptyMap();
	}

	/**
	 * Lists all table names for this account.
	 * @return a list of DynamoDB tables
	 */
	public static List<String> listAllTables() {
		int items = 100;
		ListTablesResult ltr = getClient().listTables(items);
		List<String> tables = new LinkedList<>();
		String lastKey;
		do {
			tables.addAll(ltr.getTableNames());
			lastKey = ltr.getLastEvaluatedTableName();
			logger.info("Found {} tables. Total found: {}.", ltr.getTableNames().size(), tables.size());
			if (lastKey == null) {
				break;
			}
			ltr = getClient().listTables(lastKey, items);
		} while (!ltr.getTableNames().isEmpty());
		return tables;
	}

	/**
	 * Returns the table name for a given app id. Table names are usually in the form 'prefix-appid'.
	 * @param appIdentifier app id
	 * @return the table name
	 */
	public static String getTableNameForAppid(String appIdentifier) {
		if (StringUtils.isBlank(appIdentifier)) {
			return null;
		} else {
			if (isSharedAppid(appIdentifier)) {
				// app is sharing a table with other apps
				appIdentifier = SHARED_TABLE;
			}
			return (App.isRoot(appIdentifier) || appIdentifier.startsWith(Config.PARA.concat("-"))) ?
					appIdentifier : Config.PARA + "-" + appIdentifier;
		}
	}

	/**
	 * Returns the correct key for an object given the appid (table name).
	 * @param key a row id
	 * @param appIdentifier appid
	 * @return the key
	 */
	public static String getKeyForAppid(String key, String appIdentifier) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(appIdentifier)) {
			return key;
		}
		if (isSharedAppid(appIdentifier)) {
			// app is sharing a table with other apps, key is composite "appid_key"
			return keyPrefix(appIdentifier) + key;
		} else {
			return key;
		}
	}

	/**
	 * Converts a {@link ParaObject} to DynamoDB row.
	 * @param <P> type of object
	 * @param so an object
	 * @param filter used to filter out fields on update.
	 * @return a row representation of the given object.
	 */
	protected static <P extends ParaObject> Map<String, AttributeValue> toRow(P so, Class<? extends Annotation> filter) {
		HashMap<String, AttributeValue> row = new HashMap<>();
		if (so == null) {
			return row;
		}
		for (Map.Entry<String, Object> entry : ParaObjectUtils.getAnnotatedFields(so, filter).entrySet()) {
			Object value = entry.getValue();
			if (value != null && !StringUtils.isBlank(value.toString())) {
				row.put(entry.getKey(), new AttributeValue(value.toString()));
			}
		}
		if (so.getVersion() != null && so.getVersion() > 0) {
			row.put(Config._VERSION, new AttributeValue().withN(so.getVersion().toString()));
		} else {
			row.remove(Config._VERSION);
		}
		return row;
	}

	/**
	 * Converts a DynamoDB row to a {@link ParaObject}.
	 * @param <P> type of object
	 * @param row a DynamoDB row
	 * @return a populated Para object.
	 */
	protected static <P extends ParaObject> P fromRow(Map<String, AttributeValue> row) {
		if (row == null || row.isEmpty()) {
			return null;
		}
		Map<String, Object> props = new HashMap<>();
		for (Map.Entry<String, AttributeValue> col : row.entrySet()) {
			props.put(col.getKey(), col.getValue().getS());
		}
		props.put(Config._VERSION, row.getOrDefault(Config._VERSION, new AttributeValue().withN("0")).getN());
		return ParaObjectUtils.setAnnotatedFields(props);
	}

	/**
	 * Reads multiple items from DynamoDB, in batch.
	 * @param <P> type of object
	 * @param kna a map of row key->data
	 * @param results a map of ID->ParaObject
	 */
	protected static <P extends ParaObject> void batchGet(Map<String, KeysAndAttributes> kna, Map<String, P> results) {
		if (kna == null || kna.isEmpty() || results == null) {
			return;
		}
		try {
			BatchGetItemResult result = getClient().batchGetItem(new BatchGetItemRequest().
					withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL).withRequestItems(kna));
			if (result == null) {
				return;
			}

			List<Map<String, AttributeValue>> res = result.getResponses().get(kna.keySet().iterator().next());

			for (Map<String, AttributeValue> item : res) {
				P obj = fromRow(item);
				if (obj != null) {
					results.put(obj.getId(), obj);
				}
			}
			logger.debug("batchGet(): total {}, cc {}", res.size(), result.getConsumedCapacity());

			if (result.getUnprocessedKeys() != null && !result.getUnprocessedKeys().isEmpty()) {
				Thread.sleep(1000);
				logger.warn("{} UNPROCESSED read requests!", result.getUnprocessedKeys().size());
				batchGet(result.getUnprocessedKeys(), results);
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	/**
	 * Writes multiple items in batch.
	 * @param items a map of tables->write requests
	 */
	protected static void batchWrite(Map<String, List<WriteRequest>> items) {
		if (items == null || items.isEmpty()) {
			return;
		}
		try {
			BatchWriteItemResult result = getClient().batchWriteItem(new BatchWriteItemRequest().
					withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL).withRequestItems(items));
			if (result == null) {
				return;
			}
			logger.debug("batchWrite(): total {}, cc {}", items.size(), result.getConsumedCapacity());

			if (result.getUnprocessedItems() != null && !result.getUnprocessedItems().isEmpty()) {
				Thread.sleep(1000);
				logger.warn("{} UNPROCESSED write requests!", result.getUnprocessedItems().size());
				batchWrite(result.getUnprocessedItems());
			}
		} catch (Exception e) {
			logger.error(null, e);
			throwIfNecessary(e);
		}
	}

	/**
	 * Reads a page from a standard DynamoDB table.
	 * @param <P> type of object
	 * @param appid the app identifier (name)
	 * @param p a {@link Pager}
	 * @return the last row key of the page, or null.
	 */
	public static <P extends ParaObject> List<P> readPageFromTable(String appid, Pager p) {
		Pager pager = (p != null) ? p : new Pager();
		ScanRequest scanRequest = new ScanRequest().
				withTableName(getTableNameForAppid(appid)).
				withLimit(pager.getLimit()).
				withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL);

		if (!StringUtils.isBlank(pager.getLastKey())) {
			scanRequest = scanRequest.withExclusiveStartKey(Collections.
					singletonMap(Config._KEY, new AttributeValue(pager.getLastKey())));
		}

		ScanResult result = getClient().scan(scanRequest);
		LinkedList<P> results = new LinkedList<>();
		for (Map<String, AttributeValue> item : result.getItems()) {
			P obj = fromRow(item);
			if (obj != null) {
				results.add(obj);
			}
		}

		if (result.getLastEvaluatedKey() != null) {
			pager.setLastKey(result.getLastEvaluatedKey().get(Config._KEY).getS());
		} else if (!results.isEmpty()) {
			// set last key to be equal to the last result - end reached.
			pager.setLastKey(results.peekLast().getId());
		}
		return results;
	}

	/**
	 * Reads a page from a "shared" DynamoDB table. Shared tables are tables that have global secondary indexes
	 * and can contain the objects of multiple apps.
	 * @param <P> type of object
	 * @param appid the app identifier (name)
	 * @param pager a {@link Pager}
	 * @return the id of the last object on the page, or null.
	 */
	public static <P extends ParaObject> List<P> readPageFromSharedTable(String appid, Pager pager) {
		LinkedList<P> results = new LinkedList<>();
		if (StringUtils.isBlank(appid)) {
			return results;
		}
		Page<Item, QueryOutcome> items = queryGSI(appid, pager);
		if (items != null) {
			for (Item item : items) {
				P obj = ParaObjectUtils.setAnnotatedFields(item.asMap());
				if (obj != null) {
					results.add(obj);
				}
			}
		}
		if (!results.isEmpty() && pager != null) {
			pager.setLastKey(results.peekLast().getId());
		}
		return results;
	}

	private static Page<Item, QueryOutcome> queryGSI(String appid, Pager p) {
		Pager pager = (p != null) ? p : new Pager();
		Index index = getSharedIndex();
		QuerySpec spec = new QuerySpec().
				withMaxPageSize(pager.getLimit()).
				withMaxResultSize(pager.getLimit()).
				withKeyConditionExpression(Config._APPID + " = :aid").
				withValueMap(new ValueMap().withString(":aid", appid));

		if (!StringUtils.isBlank(pager.getLastKey())) {
			spec = spec.withExclusiveStartKey(new KeyAttribute(Config._APPID, appid),	// HASH/PARTITION KEY
					new KeyAttribute(Config._ID, pager.getLastKey()), // RANGE/SORT KEY
					new KeyAttribute(Config._KEY, getKeyForAppid(pager.getLastKey(), appid))); // TABLE PRIMARY KEY
		}
		return index != null ? index.query(spec).firstPage() : null;
	}

	/**
	 * Deletes all objects in a shared table, which belong to a given appid, by scanning the GSI.
	 * @param appid app id
	 */
	public static void deleteAllFromSharedTable(String appid) {
		if (StringUtils.isBlank(appid) || !isSharedAppid(appid)) {
			return;
		}
		Pager pager = new Pager(50);
		List<WriteRequest> allDeletes = new LinkedList<>();
		Page<Item, QueryOutcome> items;
		// read all phase
		do {
			items = queryGSI(appid, pager);
			if (items == null) {
				break;
			}
			for (Item item : items) {
				String key = item.getString(Config._KEY);
				// only delete rows which belong to the given appid
				if (StringUtils.startsWith(key, appid.trim())) {
					logger.debug("Preparing to delete '{}' from shared table, appid: '{}'.", key, appid);
					pager.setLastKey(item.getString(Config._ID));
					allDeletes.add(new WriteRequest().withDeleteRequest(new DeleteRequest().
							withKey(Collections.singletonMap(Config._KEY, new AttributeValue(key)))));
				}
			}
		} while (items.iterator().hasNext());

		// delete all phase
		final int maxItems = 20;
		int batchSteps = (allDeletes.size() > maxItems) ? (allDeletes.size() / maxItems) + 1 : 1;
		List<WriteRequest> reqs = new LinkedList<>();
		Iterator<WriteRequest> it = allDeletes.iterator();
		String tableName = getTableNameForAppid(appid);
		for (int i = 0; i < batchSteps; i++) {
			while (it.hasNext() && reqs.size() < maxItems) {
				reqs.add(it.next());
			}
			if (reqs.size() > 0) {
				logger.info("Deleting {} items belonging to app '{}', from shared table (page {}/{})...",
						reqs.size(), appid, i + 1, batchSteps);
				batchWrite(Collections.singletonMap(tableName, reqs));
			}
			reqs.clear();
		}
	}

	/**
	 * Returns the Index object for the shared table.
	 * @return the Index object or null
	 */
	public static Index getSharedIndex() {
		if (ddb == null) {
			getClient();
		}
		try {
			Table t = ddb.getTable(getTableNameForAppid(SHARED_TABLE));
			if (t != null) {
				return t.getIndex(getSharedIndexName());
			}
		} catch (Exception e) {
			logger.info("Could not get shared index: {}.", e.getMessage());
		}
		return null;
	}

	/**
	 * Returns true if appid starts with a space " ".
	 * @param appIdentifier appid
	 * @return true if appid starts with " "
	 */
	public static boolean isSharedAppid(String appIdentifier) {
		return StringUtils.startsWith(appIdentifier, " ");
	}

	private static String getSharedIndexName() {
		return "GSI_" + SHARED_TABLE;
	}

	private static String keyPrefix(String appIdentifier) {
		return StringUtils.join(StringUtils.trim(appIdentifier), "_");
	}

	protected static void throwIfNecessary(Throwable t) {
		if (t != null && Config.getConfigBoolean("fail_on_write_errors", false)) {
			throw new RuntimeException("DAO write operation failed!", t);
		}
	}

}
