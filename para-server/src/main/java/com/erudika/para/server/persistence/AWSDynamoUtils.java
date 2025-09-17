/*
 * Copyright 2013-2022 Erudika. https://erudika.com
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

import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.Pager;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.ParaObjectUtils;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.applicationautoscaling.model.MetricType;
import software.amazon.awssdk.services.applicationautoscaling.model.PolicyType;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalableDimension;
import software.amazon.awssdk.services.applicationautoscaling.model.ServiceNamespace;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Replica;
import software.amazon.awssdk.services.dynamodb.model.ReplicaUpdate;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.StreamViewType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

/**
 * Helper utilities for connecting to AWS DynamoDB.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class AWSDynamoUtils {

	private static final String LOCAL_ENDPOINT = "http://localhost:8000";
	private static final String AWS_REGION = new DefaultAwsRegionProviderChain().getRegion().id();
	private static Map<String, DynamoDbClient> ddbClients;
	private static Map<String, ApplicationAutoScalingClient> aasClients;
	private static List<String> replicaRegions;
	private static final Logger logger = LoggerFactory.getLogger(AWSDynamoUtils.class);

	static {
		Para.addDestroyListener(() -> shutdownClient());
	}

	private AWSDynamoUtils() { }

	/**
	 * Returns a client instance for AWS DynamoDB.
	 * @return a client that talks to DynamoDB
	 */
	public static DynamoDbClient getClient() {
		return getClient(AWS_REGION);
	}

	private static DynamoDbClient getClient(String region) {
		if (ddbClients != null && ddbClients.containsKey(region)) {
			return ddbClients.get(region);
		}
		DynamoDbClient ddbClient;
		if (Para.getConfig().inProduction()) {
			ddbClient = DynamoDbClient.create();
		} else {
			ddbClient = DynamoDbClient.builder().
					endpointOverride(URI.create(LOCAL_ENDPOINT)).
					credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "null"))).
					build();
		}
		if (ddbClients == null) {
			ddbClients = new HashMap<>();
			ddbClients.put(region, ddbClient);
			getReplicaRegions().stream().filter(r -> !r.equals(region)).forEach(r -> {
				ddbClients.put(r, DynamoDbClient.builder().region(Region.of(r)).build());
			});
		}
		getAutoScalingClient(region);
		return ddbClient;
	}

	private static ApplicationAutoScalingClient getAutoScalingClient(String region) {
		if (aasClients == null) {
			aasClients = new HashMap<>();
			aasClients.put(region, ApplicationAutoScalingClient.create());
			getReplicaRegions().stream().filter(r -> !r.equals(region)).forEach(r -> {
				aasClients.put(r, ApplicationAutoScalingClient.builder().region(Region.of(r)).build());
			});
		}
		return aasClients.get(region);
	}

	/**
	 * Stops the client and releases resources.
	 * <b>There's no need to call this explicitly!</b>
	 */
	protected static void shutdownClient() {
		if (ddbClients != null) {
			ddbClients.values().stream().filter(c -> (c != null)).forEach(client -> client.close());
			ddbClients = null;
		}
		if (aasClients != null) {
			aasClients.values().stream().filter(c -> (c != null)).forEach(client -> client.close());
			aasClients = null;
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
			DescribeTableResponse res = getClient().describeTable(b -> b.tableName(getTableNameForAppid(appid)));
			return res != null;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Creates a DynamoDB table.
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @return true if created
	 */
	public static boolean createTable(String appid) {
		return createTable(appid, Para.getConfig().awsDynamoMaxInitialReadCapacity(),
				Para.getConfig().awsDynamoMaxInitialWriteCapacity());
	}

	/**
	 * Creates a table in AWS DynamoDB.
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param maxReadCapacity max read capacity for autoscaling (only applicable in PROVISIONED billing mode)
	 * @param maxWriteCapacity max write capacity for autoscaling (only applicable in PROVISIONED billing mode)
	 * @return true if created
	 */
	public static boolean createTable(String appid, int maxReadCapacity, int maxWriteCapacity) {
		if (StringUtils.isBlank(appid)) {
			return false;
		} else if (StringUtils.containsWhitespace(appid)) {
			logger.warn("DynamoDB table name contains whitespace. The name '{}' is invalid.", appid);
			return false;
		} else if (existsTable(appid)) {
			logger.warn("DynamoDB table '{}' already exists.", appid);
			return false;
		}
		String table = getTableNameForAppid(appid);
		boolean created = createTableInternal(appid, maxReadCapacity, maxWriteCapacity, AWS_REGION); // master replica
		boolean replicate = !getReplicaRegions().isEmpty() && !App.isRoot(appid);
		if (created && replicate) {
			Para.asyncExecute(() -> {
				List<Replica> replicas = new LinkedList<>();
				// add master AND secondary replicas
				replicas.add(Replica.builder().regionName(AWS_REGION).build());
				// also create secondary replica tables, skipping the region of the master replica table
				getReplicaRegions().stream().filter(r -> !r.equals(AWS_REGION)).forEach(region -> {
					logger.info("Replicating DynamoDB table '{}' in region {}...", table, region);
					replicas.add(Replica.builder().regionName(region).build());
					createTableInternal(appid, maxReadCapacity, maxWriteCapacity, region);
				});
				// link master and secondary tables together in a global table relationship (multi-master replicas)
				getClient().createGlobalTable(b -> b.globalTableName(table).replicationGroup(replicas));
			});
		}
		if (Para.getConfig().awsDynamoBackupsEnabled()) {
			logger.info("Enabling backups for table '{}'...", table);
			getClient().updateContinuousBackups((t) -> t.tableName(table).
					pointInTimeRecoverySpecification((p) -> p.pointInTimeRecoveryEnabled(true)));
		}
		return created;
	}

	private static boolean createTableInternal(String appid, int maxReadCapacity, int maxWriteCapacity, String region) {
		boolean replicate = !getReplicaRegions().isEmpty() && !App.isRoot(appid);
		try {
			String table = getTableNameForAppid(appid);
			CreateTableRequest.Builder ctr = CreateTableRequest.builder().tableName(table).
					sseSpecification(b2 -> b2.enabled(Para.getConfig().awsDynamoEncryptionEnabled())).
					keySchema(KeySchemaElement.builder().attributeName(Config._KEY).keyType(KeyType.HASH).build()).
					attributeDefinitions(AttributeDefinition.builder().
							attributeName(Config._KEY).attributeType(ScalarAttributeType.S).build());

			if (replicate) {
				ctr.streamSpecification(s -> s.streamEnabled(replicate).streamViewType(StreamViewType.NEW_AND_OLD_IMAGES));
			}

			if (Para.getConfig().awsDynamoProvisionedBillingEnabled()) {
				ctr.billingMode(BillingMode.PROVISIONED);
				ctr.provisionedThroughput(b4 -> b4.readCapacityUnits(1L).writeCapacityUnits(1L));
			} else {
				ctr.billingMode(BillingMode.PAY_PER_REQUEST);
			}

			CreateTableResponse tbl = getClient(region).createTable(ctr.build());
			waitForActive(table, region);
			logger.info("Created DynamoDB table '{}', status {}.", table, tbl.tableDescription().tableStatus());

			if (replicate && Para.getConfig().awsDynamoProvisionedBillingEnabled()) {
				logger.info("Enabling autoscaling for DynamoDB table '{}'...", table);
				ApplicationAutoScalingClient aasClient = getAutoScalingClient(region);
				aasClient.registerScalableTarget(t -> t.serviceNamespace(ServiceNamespace.DYNAMODB).
						resourceId("table/" + table).
						scalableDimension(ScalableDimension.DYNAMODB_TABLE_READ_CAPACITY_UNITS).
						minCapacity(1).maxCapacity((int) maxReadCapacity));
				aasClient.registerScalableTarget(t -> t.serviceNamespace(ServiceNamespace.DYNAMODB).
						resourceId("table/" + table).
						scalableDimension(ScalableDimension.DYNAMODB_TABLE_WRITE_CAPACITY_UNITS).
						minCapacity(1).maxCapacity((int) maxWriteCapacity));
				aasClient.putScalingPolicy(s -> s.policyName(table + "-autoscale-reads").
						resourceId("table/" + table).
						serviceNamespace(ServiceNamespace.DYNAMODB).
						scalableDimension(ScalableDimension.DYNAMODB_TABLE_READ_CAPACITY_UNITS).
						policyType(PolicyType.TARGET_TRACKING_SCALING).
						targetTrackingScalingPolicyConfiguration(t
								-> t.predefinedMetricSpecification(p -> p.
						predefinedMetricType(MetricType.DYNAMO_DB_READ_CAPACITY_UTILIZATION)).
								targetValue(70.0).scaleInCooldown(60).scaleOutCooldown(60)));
				aasClient.putScalingPolicy(s -> s.policyName(table + "-autoscale-writes").
						resourceId("table/" + table).
						serviceNamespace(ServiceNamespace.DYNAMODB).
						scalableDimension(ScalableDimension.DYNAMODB_TABLE_WRITE_CAPACITY_UNITS).
						policyType(PolicyType.TARGET_TRACKING_SCALING).
						targetTrackingScalingPolicyConfiguration(t
								-> t.predefinedMetricSpecification(p -> p.
						predefinedMetricType(MetricType.DYNAMO_DB_WRITE_CAPACITY_UTILIZATION)).
								targetValue(70.0).scaleInCooldown(60).scaleOutCooldown(60)));
				waitForActive(table, region);
			}
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
			getClient().updateTable(b -> b.tableName(table).
					provisionedThroughput(b1 -> b1.readCapacityUnits(readCapacity).writeCapacityUnits(writeCapacity)));
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
			if (!getReplicaRegions().isEmpty() && !App.isRoot(appid)) {
				List<ReplicaUpdate> replicaUpdates = new LinkedList<>();
				getReplicaRegions().stream().forEach(region -> {
					logger.info("Removing replica from global table '{}' in region {}...", table, region);
					replicaUpdates.add(ReplicaUpdate.builder().delete(d -> d.regionName(region)).build());
				});
				try {
					// this only removes the replicas for each region - it DOES NOT delete the actual replica tables
					getClient().updateGlobalTable(b -> b.globalTableName(table).replicaUpdates(replicaUpdates));
				} catch (Exception ex) {
					logger.error(null, ex);
				} finally {
					getReplicaRegions().stream().forEach(region -> {
						DynamoDbAsyncClient asyncdb = DynamoDbAsyncClient.builder().region(Region.of(region)).build();
						asyncdb.deleteTable(b -> b.tableName(table));
						logger.info("Deleted DynamoDB table '{}' in region {}.", table, region);
					});
				}
			} else {
				getClient().deleteTable(b -> b.tableName(table));
				logger.info("Deleted DynamoDB table '{}'.", table);
			}
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
		if (StringUtils.isBlank(Para.getConfig().sharedTableName()) ||
				StringUtils.containsWhitespace(Para.getConfig().sharedTableName()) ||
				existsTable(Para.getConfig().sharedTableName())) {
			return false;
		}
		String table = getTableNameForAppid(Para.getConfig().sharedTableName());
		try {
			GlobalSecondaryIndex secIndex = GlobalSecondaryIndex.builder().
					indexName(getSharedIndexName()).
					provisionedThroughput(b -> b.readCapacityUnits(1L).writeCapacityUnits(1L)).
					projection(Projection.builder().projectionType(ProjectionType.ALL).build()).
					keySchema(KeySchemaElement.builder().attributeName(Config._APPID).keyType(KeyType.HASH).build(),
							KeySchemaElement.builder().attributeName(Config._ID).keyType(KeyType.RANGE).build()).build();

			AttributeDefinition[] attributes = new AttributeDefinition[] {
				AttributeDefinition.builder().attributeName(Config._KEY).attributeType(ScalarAttributeType.S).build(),
				AttributeDefinition.builder().attributeName(Config._APPID).attributeType(ScalarAttributeType.S).build(),
				AttributeDefinition.builder().attributeName(Config._ID).attributeType(ScalarAttributeType.S).build()
			};

			CreateTableResponse tbl = getClient().createTable(b -> b.tableName(table).
					keySchema(KeySchemaElement.builder().attributeName(Config._KEY).keyType(KeyType.HASH).build()).
					sseSpecification(b2 -> b2.enabled(Para.getConfig().awsDynamoEncryptionEnabled())).
					attributeDefinitions(attributes).
					globalSecondaryIndexes(secIndex).
					provisionedThroughput(b6 -> b6.readCapacityUnits(readCapacity).writeCapacityUnits(writeCapacity)));
			logger.info("Waiting for DynamoDB table to become ACTIVE...");
			waitForActive(table, AWS_REGION);
			logger.info("Created shared table '{}', status {}.", table, tbl.tableDescription().tableStatus());
			if (Para.getConfig().awsDynamoBackupsEnabled()) {
				logger.info("Enabling backups for shared table '{}'...", table);
				getClient().updateContinuousBackups((t) -> t.tableName(table).
						pointInTimeRecoverySpecification((p) -> p.pointInTimeRecoveryEnabled(true)));
			}
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
			final TableDescription td = getClient().describeTable(b -> b.tableName(getTableNameForAppid(appid))).table();
			HashMap<String, Object> dbStatus = new HashMap<>();
			dbStatus.put("id", appid);
			dbStatus.put("status", td.tableStatus().name());
			dbStatus.put("created", td.creationDateTime().toEpochMilli());
			dbStatus.put("sizeBytes", td.tableSizeBytes());
			dbStatus.put("itemCount", td.itemCount());
			dbStatus.put("readCapacityUnits", td.provisionedThroughput().readCapacityUnits());
			dbStatus.put("writeCapacityUnits", td.provisionedThroughput().writeCapacityUnits());
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
		ListTablesResponse ltr = getClient().listTables(b -> b.limit(items));
		List<String> tables = new LinkedList<>();
		do {
			tables.addAll(ltr.tableNames());
			logger.info("Found {} tables. Total found: {}.", ltr.tableNames().size(), tables.size());
			if (ltr.lastEvaluatedTableName() == null) {
				break;
			}
			final String lastKey = ltr.lastEvaluatedTableName();
			ltr = getClient().listTables(b -> b.limit(items).exclusiveStartTableName(lastKey));
		} while (!ltr.tableNames().isEmpty());
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
				appIdentifier = Para.getConfig().sharedTableName();
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
				row.put(entry.getKey(), AttributeValue.builder().s(value.toString()).build());
			}
		}
		if (so.getVersion() != null && so.getVersion() > 0) {
			row.put(Config._VERSION, AttributeValue.builder().n(so.getVersion().toString()).build());
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
			props.put(col.getKey(), col.getValue().s());
		}
		props.put(Config._VERSION, row.getOrDefault(Config._VERSION, AttributeValue.builder().n("0").build()).n());
		return ParaObjectUtils.setAnnotatedFields(props);
	}

	/**
	 * Reads multiple items from DynamoDB, in batch.
	 * @param <P> type of object
	 * @param kna a map of row key->data
	 * @param results a map of ID->ParaObject
	 * @param backoff backoff seconds
	 */
	protected static <P extends ParaObject> void batchGet(Map<String, KeysAndAttributes> kna, Map<String, P> results,
			int backoff) {
		if (kna == null || kna.isEmpty() || results == null) {
			return;
		}
		try {
			BatchGetItemResponse result = getClient().batchGetItem(b -> b.
					returnConsumedCapacity(ReturnConsumedCapacity.TOTAL).requestItems(kna));
			if (result == null) {
				return;
			}

			List<Map<String, AttributeValue>> res = result.responses().get(kna.keySet().iterator().next());

			for (Map<String, AttributeValue> item : res) {
				P obj = fromRow(item);
				if (obj != null) {
					results.put(obj.getId(), obj);
				}
			}
			logger.debug("batchGet(): total {}, cc {}", res.size(), result.consumedCapacity());

			if (result.unprocessedKeys() != null && !result.unprocessedKeys().isEmpty()) {
				Thread.sleep((long) backoff * 1000L);
				for (Map.Entry<String, KeysAndAttributes> entry : result.unprocessedKeys().entrySet()) {
					logger.warn("UNPROCESSED DynamoDB read requests for keys {} in table {}!",
							entry.getValue().keys().stream().flatMap(r -> r.values().stream().map(v -> v.s())).
									collect(Collectors.joining(",")), entry.getKey());
				}
				batchGet(result.unprocessedKeys(), results, backoff * 2);
			}
		} catch (ProvisionedThroughputExceededException ex) {
			logger.warn("Read capacity exceeded for table '{}'. Retrying request in {} seconds.",
					kna.keySet().iterator().next(), backoff);
			try {
				Thread.sleep((long) backoff * 1000L);
				// retry forever
				batchGet(kna, results, backoff * 2);
			} catch (InterruptedException ie) {
				logger.error(null, ie);
				Thread.currentThread().interrupt();
			}
		} catch (InterruptedException ie) {
			logger.error(null, ie);
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			logger.error("Failed to execute batch read operation on table '{}'", kna.keySet().iterator().next(), e);
		}
	}

	/**
	 * Writes multiple items in batch.
	 * @param items a map of tables->write requests
	 * @param backoff backoff seconds
	 */
	protected static void batchWrite(Map<String, List<WriteRequest>> items, int backoff) {
		if (items == null || items.isEmpty()) {
			return;
		}
		try {
			logger.debug("batchWrite(): requests {}, backoff {}", items.values().iterator().next().size(), backoff);
			BatchWriteItemResponse result = getClient().batchWriteItem(b -> b.
					returnConsumedCapacity(ReturnConsumedCapacity.TOTAL).requestItems(items));
			if (result == null) {
				return;
			}
			logger.debug("batchWrite(): success - consumed capacity {}", result.consumedCapacity());

			if (result.unprocessedItems() != null && !result.unprocessedItems().isEmpty()) {
				Thread.sleep((long) backoff * 1000L);
				for (Map.Entry<String, List<WriteRequest>> entry : result.unprocessedItems().entrySet()) {
					logger.warn("UNPROCESSED DynamoDB write requests for keys {} in table {}!",
							entry.getValue().stream().map(r -> r.getValueForField(Config._KEY, String.class).orElse("")).
								collect(Collectors.joining(",")), entry.getKey());
				}
				batchWrite(result.unprocessedItems(), backoff * 2);
			}
		} catch (ProvisionedThroughputExceededException ex) {
			logger.warn("Write capacity exceeded for table '{}'. Retrying request in {} seconds.",
					items.keySet().iterator().next(), backoff);
			try {
				Thread.sleep((long) backoff * 1000L);
				// retry forever
				batchWrite(items, backoff * 2);
			} catch (InterruptedException ie) {
				logger.error(null, ie);
				Thread.currentThread().interrupt();
			}
		} catch (InterruptedException ie) {
			logger.error(null, ie);
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			logger.error("Failed to execute batch write operation on table '{}'", items.keySet().iterator().next(), e);
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
		ScanRequest.Builder scanRequest = ScanRequest.builder().
				tableName(getTableNameForAppid(appid)).
				limit(pager.getLimit()).
				returnConsumedCapacity(ReturnConsumedCapacity.TOTAL);

		if (!StringUtils.isBlank(pager.getLastKey())) {
			scanRequest.exclusiveStartKey(Collections.
					singletonMap(Config._KEY, AttributeValue.builder().s(pager.getLastKey()).build()));
		}

		ScanResponse result = getClient().scan(scanRequest.build());
		String lastKey = null;
		LinkedList<P> results = new LinkedList<>();
		for (Map<String, AttributeValue> item : result.items()) {
			P obj = fromRow(item);
			if (obj != null) {
				lastKey = item.get(Config._KEY).s();
				results.add(obj);
			}
		}

		if (result.lastEvaluatedKey() != null && !result.lastEvaluatedKey().isEmpty()) {
			pager.setLastKey(result.lastEvaluatedKey().get(Config._KEY).s());
		} else if (!results.isEmpty()) {
			// set last key to be equal to the last result - end reached.
			pager.setLastKey(lastKey);
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
		QueryResponse pages = queryGSI(appid, pager);
		if (pages != null) {
			for (Map<String, AttributeValue> item : pages.items()) {
				P obj = fromRow(item);
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

	private static QueryResponse queryGSI(String appid, Pager p) {
		Pager pager = (p != null) ? p : new Pager();
		GlobalSecondaryIndexDescription index = getSharedGlobalIndex();

		QueryRequest.Builder query = QueryRequest.builder().
				limit(pager.getLimit()).
				keyConditionExpression(Config._APPID + " = :aid").
				expressionAttributeValues(Collections.singletonMap(":aid", AttributeValue.builder().s(appid).build()));

		if (!StringUtils.isBlank(pager.getLastKey())) {
			// See https://stackoverflow.com/questions/40988397/42735813#42735813
			Map<String, AttributeValue> startKey = new HashMap<>(3);
			// HASH/PARTITION KEY
			startKey.put(Config._APPID, AttributeValue.builder().s(appid).build());
			// RANGE/SORT KEY
			startKey.put(Config._ID, AttributeValue.builder().s(pager.getLastKey()).build());
			// TABLE PRIMARY KEY
			startKey.put(Config._KEY, AttributeValue.builder().s(getKeyForAppid(pager.getLastKey(), appid)).build());
			query.exclusiveStartKey(startKey);
		}

		return index != null ? getClient().query(query.indexName(index.indexName()).
				tableName(getTableNameForAppid(Para.getConfig().sharedTableName())).build()) : null;
	}

	/**
	 * Deletes all objects in a shared table, which belong to a given appid, by scanning the GSI.
	 * @param appid app id
	 */
	public static void deleteAllFromSharedTable(String appid) {
		if (StringUtils.isBlank(appid) || !isSharedAppid(appid)) {
			return;
		}
		Pager pager = new Pager(25);
		QueryResponse pages;
		Map<String, AttributeValue> lastKey = null;
		do {
			// read all phase
			pages = queryGSI(appid, pager);
			if (pages == null) {
				break;
			}
			List<WriteRequest> deletePage = new LinkedList<>();
			for (Map<String, AttributeValue> item : pages.items()) {
				String key = item.get(Config._KEY).s();
				// only delete rows which belong to the given appid
				if (Strings.CS.startsWith(key, keyPrefix(appid))) {
					logger.debug("Preparing to delete '{}' from shared table, appid: '{}'.", key, appid);
					pager.setLastKey(item.get(Config._ID).s());
					deletePage.add(WriteRequest.builder().deleteRequest(b -> b.
							key(Collections.singletonMap(Config._KEY, AttributeValue.builder().s(key).
									build()))).build());
				}
			}
			lastKey = pages.lastEvaluatedKey();
			// delete all phase
			logger.info("Deleting {} items belonging to app '{}', from shared table...", deletePage.size(), appid);
			if (!deletePage.isEmpty()) {
				batchWrite(Collections.singletonMap(getTableNameForAppid(appid), deletePage), 1);
			}
		} while (lastKey != null && !lastKey.isEmpty());
	}

	/**
	 * Returns the Index object for the shared table.
	 * @return the Index object or null
	 */
	public static GlobalSecondaryIndexDescription getSharedGlobalIndex() {
		try {
			DescribeTableResponse t = getClient().describeTable(b ->
					b.tableName(getTableNameForAppid(Para.getConfig().sharedTableName())));
			return t.table().globalSecondaryIndexes().stream().
					filter(gsi -> gsi.indexName().equals(getSharedIndexName())).findFirst().orElse(null);
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
		return Strings.CS.startsWith(appIdentifier, " ");
	}

	/**
	 * Returns the list of regions where a table should be replicated (global table).
	 * @return a list of regions
	 */
	public static List<String> getReplicaRegions() {
		if (!StringUtils.isBlank(Para.getConfig().awsDynamoReplicaRegions()) && replicaRegions == null) {
			replicaRegions = new LinkedList<String>();
			String[] regions = Para.getConfig().awsDynamoReplicaRegions().split("\\s*,\\s*");
			if (regions != null && regions.length > 0) {
				for (String region : regions) {
					if (!StringUtils.isBlank(region)) {
						replicaRegions.add(region);
					}
				}
			}
		}
		return Optional.ofNullable(replicaRegions).orElse(Collections.emptyList());
	}

	private static String getSharedIndexName() {
		return "GSI_" + Para.getConfig().sharedTableName();
	}

	private static String keyPrefix(String appIdentifier) {
		return StringUtils.join(StringUtils.trim(appIdentifier), "_");
	}

	private static void waitForActive(String table, String region) {
		WaiterResponse<DescribeTableResponse> waiterResponse = getClient(region).waiter().
				waitUntilTableExists(r -> r.tableName(table));
		if (!waiterResponse.matched().response().isPresent()) {
			logger.warn("DynamoDB table {} did not become active!", table);
		}
	}

	protected static void throwIfNecessary(Throwable t) {
		if (t != null && Para.getConfig().exceptionOnWriteErrorsEnabled()) {
			throw new RuntimeException("DAO write operation failed! - " + t.getMessage(), t);
		}
	}

}
