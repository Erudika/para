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
package com.erudika.para.persistence;

import com.erudika.para.annotations.Locked;
import com.amazonaws.services.dynamodbv2.model.AttributeAction;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.KeysAndAttributes;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValueUpdate;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;
import com.erudika.para.core.ParaObject;
import static com.erudika.para.persistence.AWSDynamoUtils.batchGet;
import static com.erudika.para.persistence.AWSDynamoUtils.batchWrite;
import static com.erudika.para.persistence.AWSDynamoUtils.fromRow;
import static com.erudika.para.persistence.AWSDynamoUtils.getKeyForAppid;
import static com.erudika.para.persistence.AWSDynamoUtils.getTableNameForAppid;
import static com.erudika.para.persistence.AWSDynamoUtils.isSharedAppid;
import static com.erudika.para.persistence.AWSDynamoUtils.readPageFromSharedTable;
import static com.erudika.para.persistence.AWSDynamoUtils.readPageFromTable;
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

/**
 * An implementation of the {@link DAO} interface using AWS DynamoDB as a data store.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class AWSDynamoDAO implements DAO {

	private static final Logger logger = LoggerFactory.getLogger(AWSDynamoDAO.class);
	private static final int MAX_ITEMS_PER_WRITE = 20;
	private static final int MAX_KEYS_PER_READ = 100;

	/**
	 * No-args constructor.
	 */
	public AWSDynamoDAO() { }

	AmazonDynamoDBClient client() {
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
			updateRow(so.getId(), appid, toRow(so, Locked.class));
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
		try {
			key = getKeyForAppid(key, appid);
			setRowKey(key, row);
			PutItemRequest putItemRequest = new PutItemRequest(getTableNameForAppid(appid), row);
			client().putItem(putItemRequest);
		} catch (Exception e) {
			logger.error("Could not write row to DB - appid={}, key={}", appid, key, e);
		}
		return key;
	}

	private void updateRow(String key, String appid, Map<String, AttributeValue> row) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(appid) || row == null || row.isEmpty()) {
			return;
		}
		Map<String, AttributeValueUpdate> rou = new HashMap<String, AttributeValueUpdate>();
		try {
			for (Entry<String, AttributeValue> attr : row.entrySet()) {
				rou.put(attr.getKey(), new AttributeValueUpdate(attr.getValue(), AttributeAction.PUT));
			}
			UpdateItemRequest updateItemRequest = new UpdateItemRequest(getTableNameForAppid(appid),
					Collections.singletonMap(Config._KEY, new AttributeValue(getKeyForAppid(key, appid))), rou);
			client().updateItem(updateItemRequest);
		} catch (Exception e) {
			logger.error("Could not update row in DB - appid={}, key={}", appid, key, e);
		}
	}

	private Map<String, AttributeValue> readRow(String key, String appid) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(appid)) {
			return null;
		}
		Map<String, AttributeValue> row = null;
		try {
			GetItemRequest getItemRequest = new GetItemRequest(getTableNameForAppid(appid),
					Collections.singletonMap(Config._KEY, new AttributeValue(getKeyForAppid(key, appid))));
			GetItemResult res = client().getItem(getItemRequest);
			if (res != null && res.getItem() != null && !res.getItem().isEmpty()) {
				row = res.getItem();
			}
		} catch (Exception e) {
			logger.error("Could not read row from DB - appid={}, key={}", appid, key, e);
		}
		return (row == null || row.isEmpty()) ? null : row;
	}

	private void deleteRow(String key, String appid) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(appid)) {
			return;
		}
		try {
			DeleteItemRequest delItemRequest = new DeleteItemRequest(getTableNameForAppid(appid),
					Collections.singletonMap(Config._KEY, new AttributeValue(getKeyForAppid(key, appid))));
			client().deleteItem(delItemRequest);
		} catch (Exception e) {
			logger.error("Could not delete row from DB - appid={}, key={}", appid, key, e);
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

		List<WriteRequest> reqs = new ArrayList<WriteRequest>(objects.size());
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
				reqs.add(new WriteRequest().withPutRequest(new PutRequest().withItem(row)));
				j++;
			}
			batchWrite(Collections.singletonMap(tableName, reqs));
			reqs.clear();
			j = 0;
		}
		logger.debug("DAO.createAll() {}->{}", appid, (objects == null) ? 0 : objects.size());
	}

	@Override
	public <P extends ParaObject> Map<String, P> readAll(String appid, List<String> keys, boolean getAllColumns) {
		if (keys == null || keys.isEmpty() || StringUtils.isBlank(appid)) {
			return new LinkedHashMap<String, P>();
		}

		// DynamoDB doesn't allow duplicate keys in batch requests
		Set<String> keySet = new TreeSet<String>(keys);
		if (keySet.size() < keys.size() && !keySet.isEmpty()) {
			logger.debug("Duplicate keys found - readAll({})", keys);
		}

		Map<String, P> results = new LinkedHashMap<String, P>(keySet.size(), 0.75f, true);
		ArrayList<Map<String, AttributeValue>> keyz = new ArrayList<Map<String, AttributeValue>>(MAX_KEYS_PER_READ);

		try {
			int batchSteps = 1;
			if ((keySet.size() > MAX_KEYS_PER_READ)) {
				batchSteps = (keySet.size() / MAX_KEYS_PER_READ)
						+ ((keySet.size() % MAX_KEYS_PER_READ > 0) ? 1 : 0);
			}

			Iterator<String> it = keySet.iterator();
			String tableName = getTableNameForAppid(appid);
			int j = 0;

			for (int i = 0; i < batchSteps; i++) {
				while (it.hasNext() && j < MAX_KEYS_PER_READ) {
					String key = it.next();
					results.put(key, null);
					keyz.add(Collections.singletonMap(Config._KEY, new AttributeValue(getKeyForAppid(key, appid))));
					j++;
				}

				KeysAndAttributes kna = new KeysAndAttributes().withKeys(keyz);
				if (!getAllColumns) {
					kna.setAttributesToGet(Arrays.asList(Config._KEY, Config._TYPE));
				}

				batchGet(Collections.singletonMap(tableName, kna), results);
				keyz.clear();
				j = 0;
			}
			logger.debug("DAO.readAll({}) {}", keySet, results.size());
		} catch (Exception e) {
			logger.error("Failed to readAll({}): {}", keys, e);
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
		List<P> results = new LinkedList<P>();
		try {
			if (isSharedAppid(appid)) {
				results = readPageFromSharedTable(appid, pager);
			} else {
				results = readPageFromTable(appid, pager);
			}
			pager.setCount(pager.getCount() + results.size());
		} catch (Exception e) {
			logger.error("Failed to readPage({}): {}", appid, e);
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
				update(appid, object);
			}
		}
		logger.debug("DAO.updateAll() {}", (objects == null) ? 0 : objects.size());
	}

	@Override
	public <P extends ParaObject> void deleteAll(String appid, List<P> objects) {
		if (objects == null || objects.isEmpty() || StringUtils.isBlank(appid)) {
			return;
		}

		List<WriteRequest> reqs = new ArrayList<WriteRequest>(objects.size());
		for (ParaObject object : objects) {
			if (object != null) {
				reqs.add(new WriteRequest().withDeleteRequest(new DeleteRequest().
						withKey(Collections.singletonMap(Config._KEY,
								new AttributeValue(getKeyForAppid(object.getId(), appid))))));
			}
		}
		batchWrite(Collections.singletonMap(getTableNameForAppid(appid), reqs));
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
		row.put(Config._KEY, new AttributeValue(key));
	}

	//////////////////////////////////////////////////////

	@Override
	public <P extends ParaObject> String create(P so) {
		return create(Config.APP_NAME_NS, so);
	}

	@Override
	public <P extends ParaObject> P read(String key) {
		return read(Config.APP_NAME_NS, key);
	}

	@Override
	public <P extends ParaObject> void update(P so) {
		update(Config.APP_NAME_NS, so);
	}

	@Override
	public <P extends ParaObject> void delete(P so) {
		delete(Config.APP_NAME_NS, so);
	}

	@Override
	public <P extends ParaObject> void createAll(List<P> objects) {
		createAll(Config.APP_NAME_NS, objects);
	}

	@Override
	public <P extends ParaObject> Map<String, P> readAll(List<String> keys, boolean getAllColumns) {
		return readAll(Config.APP_NAME_NS, keys, getAllColumns);
	}

	@Override
	public <P extends ParaObject> List<P> readPage(Pager pager) {
		return readPage(Config.APP_NAME_NS, pager);
	}

	@Override
	public <P extends ParaObject> void updateAll(List<P> objects) {
		updateAll(Config.APP_NAME_NS, objects);
	}

	@Override
	public <P extends ParaObject> void deleteAll(List<P> objects) {
		deleteAll(Config.APP_NAME_NS, objects);
	}

}
