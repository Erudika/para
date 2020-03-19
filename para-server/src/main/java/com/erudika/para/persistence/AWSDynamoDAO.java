/*
 * Copyright 2013-2020 Erudika. https://erudika.com
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

import com.erudika.para.annotations.Locked;
import com.erudika.para.AppCreatedListener;
import com.erudika.para.AppDeletedListener;
import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import static com.erudika.para.persistence.AWSDynamoUtils.batchGet;
import static com.erudika.para.persistence.AWSDynamoUtils.batchWrite;
import static com.erudika.para.persistence.AWSDynamoUtils.fromRow;
import static com.erudika.para.persistence.AWSDynamoUtils.getKeyForAppid;
import static com.erudika.para.persistence.AWSDynamoUtils.getTableNameForAppid;
import static com.erudika.para.persistence.AWSDynamoUtils.isSharedAppid;
import static com.erudika.para.persistence.AWSDynamoUtils.readPageFromSharedTable;
import static com.erudika.para.persistence.AWSDynamoUtils.readPageFromTable;
import static com.erudika.para.persistence.AWSDynamoUtils.throwIfNecessary;
import static com.erudika.para.persistence.AWSDynamoUtils.toRow;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

/**
 * An implementation of the {@link DAO} interface using AWS DynamoDB as a data store.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class AWSDynamoDAO implements DAO {

	private static final Logger logger = LoggerFactory.getLogger(AWSDynamoDAO.class);
	private static final int MAX_ITEMS_PER_WRITE = 25;
	private static final int MAX_KEYS_PER_READ = 100;

	/**
	 * No-args constructor.
	 */
	public AWSDynamoDAO() {
		// set up automatic table creation and deletion
		App.addAppCreatedListener(new AppCreatedListener() {
			public void onAppCreated(App app) {
				if (app != null && !app.isSharingTable()) {
					AWSDynamoUtils.createTable(app.getAppIdentifier(), 1, 1);
				}
			}
		});
		App.addAppDeletedListener(new AppDeletedListener() {
			public void onAppDeleted(App app) {
				if (app != null) {
					if (app.isSharingTable()) {
						final String appid = app.getAppIdentifier();
						Para.asyncExecute(new Runnable() {
							public void run() {
								logger.info("Async deleteAllFromSharedTable({}) started.", appid);
								AWSDynamoUtils.deleteAllFromSharedTable(appid);
								logger.info("Finished deleteAllFromSharedTable({}).", appid);
							}
						});
					} else {
						AWSDynamoUtils.deleteTable(app.getAppIdentifier());
					}
				}
			}
		});
	}

	DynamoDbClient client() {
		return AWSDynamoUtils.getClient();
	}

	/////////////////////////////////////////////
	//			CORE FUNCTIONS
	/////////////////////////////////////////////

	@Override
	public <P extends ParaObject> String create(String appid, P so) {
		if (so == null) {
			return null;
		}
		if (StringUtils.isBlank(so.getId())) {
			so.setId(Utils.getNewId());
		}
		if (so.getTimestamp() == null) {
			so.setTimestamp(Utils.timestamp());
		}
		so.setAppid(appid);
		createRow(so.getId(), appid, toRow(so, null));
		logger.debug("DAO.create() {}->{}", appid, so.getId());
		return so.getId();
	}

	@Override
	public <P extends ParaObject> P read(String appid, String key) {
		if (StringUtils.isBlank(key)) {
			return null;
		}
		P so = fromRow(readRow(key, appid));
		logger.debug("DAO.read() {}->{}", appid, key);
		return so != null ? so : null;
	}

	@Override
	public <P extends ParaObject> void update(String appid, P so) {
		if (so != null && so.getId() != null) {
			so.setUpdated(Utils.timestamp());
			boolean updated = updateRow(so.getId(), appid, toRow(so, Locked.class));
			if (so.getVersion() != null && so.getVersion() > 0) {
				so.setVersion(updated ? so.getVersion() + 1 : -1);
			} else {
				so.setVersion(0L);
			}
			logger.debug("DAO.update() {}->{}", appid, so.getId());
		}
	}

	@Override
	public <P extends ParaObject> void delete(String appid, P so) {
		if (so != null && so.getId() != null) {
			deleteRow(so.getId(), appid);
			logger.debug("DAO.delete() {}->{}", appid, so.getId());
		}
	}

	/////////////////////////////////////////////
	//				ROW FUNCTIONS
	/////////////////////////////////////////////

	private String createRow(String key, String appid, Map<String, AttributeValue> row) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(appid) || row == null || row.isEmpty()) {
			return null;
		}
		String table = getTableNameForAppid(appid);
		try {
			key = getKeyForAppid(key, appid);
			setRowKey(key, row);
			client().putItem(b -> b.tableName(table).item(row));
		} catch (Exception e) {
			logger.error("Could not write row to DB - table={}, appid={}, key={}:", table, appid, key, e);
			throwIfNecessary(e);
		}
		return key;
	}

	private boolean updateRow(String key, String appid, Map<String, AttributeValue> row) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(appid) || row == null || row.isEmpty()) {
			return false;
		}
		String table = getTableNameForAppid(appid);
		try {
			UpdateItemRequest.Builder updateRequest = UpdateItemRequest.builder();
			StringBuilder updateExpression = new StringBuilder("SET ");
			Map<String, String> names = new HashMap<>(row.size() + 1);
			Map<String, AttributeValue> values = new HashMap<>(row.size() + 1);
			boolean isLockingEnabledForRow = false;
			AttributeValue version = row.remove(Config._VERSION); // ignore the version field here
			if (version == null || version.n() == null) {
				version = AttributeValue.builder().n("0").build();
			}
			if (Long.parseLong(version.n()) > 0L) {
				isLockingEnabledForRow = true;
			}
			for (Entry<String, AttributeValue> attr : row.entrySet()) {
				String name = "#" + attr.getKey();
				String value = ":" + attr.getKey();
				updateExpression.append(name).append("=").append(value).append(",");
				names.put(name, attr.getKey());
				values.put(value, attr.getValue());
			}
			updateExpression.setLength(updateExpression.length() - 1); // remove comma at the end

			if (isLockingEnabledForRow) {
				names.put("#" + Config._VERSION, Config._VERSION);
				values.put(":" + Config._VERSION, version);
				values.put(":plusOne", AttributeValue.builder().n("1").build());
				updateRequest.conditionExpression("#" + Config._VERSION + " = :" + Config._VERSION);
				updateExpression.append(" ADD #").append(Config._VERSION).append(" :plusOne");
			}

			updateRequest.tableName(table);
			updateRequest.key(rowKey(key, appid));
			updateRequest.expressionAttributeNames(names);
			updateRequest.expressionAttributeValues(values);
			updateRequest.updateExpression(updateExpression.toString());
			client().updateItem(updateRequest.build());
			return true;
		} catch (ConditionalCheckFailedException ex) {
			logger.warn("Item not updated - versions don't match. table={}, appid={}, key={}.", table, appid, key);
		} catch (Exception e) {
			logger.error("Could not update row in DB - table={}, appid={}, key={}:", table, appid, key, e);
			throwIfNecessary(e);
		}
		return false;
	}

	private Map<String, AttributeValue> readRow(String key, String appid) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(appid)) {
			return null;
		}
		Map<String, AttributeValue> row = null;
		String table = getTableNameForAppid(appid);
		try {
			GetItemResponse res = client().getItem(b -> b.tableName(table).key(rowKey(key, appid)));
			if (res != null && res.item() != null && !res.item().isEmpty()) {
				row = res.item();
			}
		} catch (Exception e) {
			logger.error("Could not read row from DB - table={}, appid={}, key={}:", table, appid, key, e);
		}
		return (row == null || row.isEmpty()) ? null : row;
	}

	private void deleteRow(String key, String appid) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(appid)) {
			return;
		}
		String table = getTableNameForAppid(appid);
		try {
			client().deleteItem(b -> b.tableName(table).key(rowKey(key, appid)));
		} catch (Exception e) {
			logger.error("Could not delete row from DB - table={}, appid={}, key={}:", table, appid, key, e);
			throwIfNecessary(e);
		}
	}

	/////////////////////////////////////////////
	//				BATCH FUNCTIONS
	/////////////////////////////////////////////

	@Override
	public <P extends ParaObject> void createAll(String appid, List<P> objects) {
		if (objects == null || objects.isEmpty() || StringUtils.isBlank(appid)) {
			return;
		}

		Map<String, WriteRequest> reqs = new LinkedHashMap<>(objects.size());
		int batchSteps = 1;
		if ((objects.size() > MAX_ITEMS_PER_WRITE)) {
			batchSteps = (objects.size() / MAX_ITEMS_PER_WRITE) +
					((objects.size() % MAX_ITEMS_PER_WRITE > 0) ? 1 : 0);
		}

		Iterator<P> it = objects.iterator();
		String tableName = getTableNameForAppid(appid);
		int j = 0;

		for (int i = 0; i < batchSteps; i++) {
			while (it.hasNext() && j < MAX_ITEMS_PER_WRITE) {
				ParaObject object = it.next();
				if (StringUtils.isBlank(object.getId())) {
					object.setId(Utils.getNewId());
				}
				if (object.getTimestamp() == null) {
					object.setTimestamp(Utils.timestamp());
				}
				//if (updateOp) object.setUpdated(Utils.timestamp());
				object.setAppid(appid);
				Map<String, AttributeValue> row = toRow(object, null);
				setRowKey(getKeyForAppid(object.getId(), appid), row);
				reqs.put(object.getId(), WriteRequest.builder().putRequest(b -> b.item(row)).build());
				j++;
			}
			batchWrite(Collections.singletonMap(tableName, reqs.values().stream().collect(Collectors.toList())), 1);
			reqs.clear();
			j = 0;
		}
		logger.debug("DAO.createAll() {}->{}", appid, objects.size());
	}

	@Override
	public <P extends ParaObject> Map<String, P> readAll(String appid, List<String> keys, boolean getAllColumns) {
		if (keys == null || keys.isEmpty() || StringUtils.isBlank(appid)) {
			return new LinkedHashMap<>();
		}

		// DynamoDB doesn't allow duplicate keys in batch requests
		Set<String> keySet = new TreeSet<>(keys);
		if (keySet.size() < keys.size() && !keySet.isEmpty()) {
			logger.debug("Duplicate keys found - readAll({})", keys);
		}

		Map<String, P> results = new LinkedHashMap<>(keySet.size(), 0.75f, true);
		ArrayList<Map<String, AttributeValue>> keyz = new ArrayList<>(MAX_KEYS_PER_READ);
		String table = getTableNameForAppid(appid);
		try {
			int batchSteps = 1;
			if ((keySet.size() > MAX_KEYS_PER_READ)) {
				batchSteps = (keySet.size() / MAX_KEYS_PER_READ)
						+ ((keySet.size() % MAX_KEYS_PER_READ > 0) ? 1 : 0);
			}

			Iterator<String> it = keySet.iterator();
			int j = 0;

			for (int i = 0; i < batchSteps; i++) {
				while (it.hasNext() && j < MAX_KEYS_PER_READ) {
					String key = it.next();
					results.put(key, null);
					keyz.add(rowKey(key, appid));
					j++;
				}

				KeysAndAttributes.Builder kna = KeysAndAttributes.builder().keys(keyz);
				if (!getAllColumns) {
					kna.attributesToGet(Arrays.asList(Config._ID, Config._KEY, Config._TYPE));
				}

				batchGet(Collections.singletonMap(table, kna.build()), results, 1);
				keyz.clear();
				j = 0;
			}
			logger.debug("DAO.readAll({}) {}", keySet, results.size());
		} catch (Exception e) {
			logger.error("Failed to readAll({}), table={}:", keys, table, e);
		}
		return results;
	}

	@Override
	public <P extends ParaObject> List<P> readPage(String appid, Pager pager) {
		if (StringUtils.isBlank(appid)) {
			return Collections.emptyList();
		}
		if (pager == null) {
			pager = new Pager();
		}
		List<P> results = new LinkedList<>();
		String table = getTableNameForAppid(appid);
		try {
			if (isSharedAppid(appid)) {
				results = readPageFromSharedTable(appid, pager);
			} else {
				results = readPageFromTable(appid, pager);
			}
			pager.setCount(pager.getCount() + results.size());
		} catch (Exception e) {
			logger.error("Failed to readPage({}), table={}:", appid, table, e);
		}
		return results;
	}

	@Override
	public <P extends ParaObject> void updateAll(String appid, List<P> objects) {
		// DynamoDB doesn't have a BatchUpdate API yet so we have to do one of the following:
		// 1. update items one by one (chosen for simplicity)
		// 2. readAll() first, then call writeAll() with updated objects (2 ops)
		if (objects != null) {
			for (P object : objects) {
				if (object != null && object.getId() != null) {
					object.setUpdated(Utils.timestamp());
					boolean updated = updateRow(object.getId(), appid, toRow(object, Locked.class));
					if (object.getVersion() != null && object.getVersion() > 0) {
						object.setVersion(updated ? object.getVersion() + 1 : -1);
					} else {
						object.setVersion(0L);
					}
				}
			}
		}
		logger.debug("DAO.updateAll() {}", (objects == null) ? 0 : objects.size());
	}

	@Override
	public <P extends ParaObject> void deleteAll(String appid, List<P> objects) {
		if (objects == null || objects.isEmpty() || StringUtils.isBlank(appid)) {
			return;
		}

		Map<String, WriteRequest> reqs = new LinkedHashMap<>(objects.size());
		int batchSteps = 1;
		if ((objects.size() > MAX_ITEMS_PER_WRITE)) {
			batchSteps = (objects.size() / MAX_ITEMS_PER_WRITE)
					+ ((objects.size() % MAX_ITEMS_PER_WRITE > 0) ? 1 : 0);
		}

		Iterator<P> it = objects.iterator();
		String tableName = getTableNameForAppid(appid);
		int j = 0;

		for (int i = 0; i < batchSteps; i++) {
			while (it.hasNext() && j < MAX_ITEMS_PER_WRITE) {
				ParaObject object = it.next();
				if (object != null) {
					reqs.put(object.getId(), WriteRequest.builder().
							deleteRequest(b -> b.key(rowKey(object.getId(), appid))).build());
					j++;
				}
			}
			batchWrite(Collections.singletonMap(tableName, reqs.values().stream().collect(Collectors.toList())), 1);
			reqs.clear();
			j = 0;
		}
		logger.debug("DAO.deleteAll() {}", objects.size());
	}

	/////////////////////////////////////////////
	//				MISC FUNCTIONS
	/////////////////////////////////////////////

	private void setRowKey(String key, Map<String, AttributeValue> row) {
		if (row.containsKey(Config._KEY)) {
			logger.warn("Attribute name conflict:  "
				+ "attribute {} will be overwritten! {} is a reserved keyword.", Config._KEY);
		}
		row.put(Config._KEY, AttributeValue.builder().s(key).build());
	}

	private Map<String, AttributeValue> rowKey(String key, String appid) {
		return Collections.singletonMap(Config._KEY, AttributeValue.builder().s(getKeyForAppid(key, appid)).build());
	}

	//////////////////////////////////////////////////////

	@Override
	public <P extends ParaObject> String create(P so) {
		return create(Config.getRootAppIdentifier(), so);
	}

	@Override
	public <P extends ParaObject> P read(String key) {
		return read(Config.getRootAppIdentifier(), key);
	}

	@Override
	public <P extends ParaObject> void update(P so) {
		update(Config.getRootAppIdentifier(), so);
	}

	@Override
	public <P extends ParaObject> void delete(P so) {
		delete(Config.getRootAppIdentifier(), so);
	}

	@Override
	public <P extends ParaObject> void createAll(List<P> objects) {
		createAll(Config.getRootAppIdentifier(), objects);
	}

	@Override
	public <P extends ParaObject> Map<String, P> readAll(List<String> keys, boolean getAllColumns) {
		return readAll(Config.getRootAppIdentifier(), keys, getAllColumns);
	}

	@Override
	public <P extends ParaObject> List<P> readPage(Pager pager) {
		return readPage(Config.getRootAppIdentifier(), pager);
	}

	@Override
	public <P extends ParaObject> void updateAll(List<P> objects) {
		updateAll(Config.getRootAppIdentifier(), objects);
	}

	@Override
	public <P extends ParaObject> void deleteAll(List<P> objects) {
		deleteAll(Config.getRootAppIdentifier(), objects);
	}

}
