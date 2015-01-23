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
package com.erudika.para.persistence;

import com.erudika.para.annotations.Locked;
import com.amazonaws.services.dynamodbv2.model.AttributeAction;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.BatchGetItemResult;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.KeysAndAttributes;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValueUpdate;
import com.amazonaws.services.dynamodbv2.model.BatchGetItemRequest;
import com.amazonaws.services.dynamodbv2.model.BatchWriteItemRequest;
import com.amazonaws.services.dynamodbv2.model.BatchWriteItemResult;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutRequest;
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;
import com.erudika.para.core.ParaObject;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.lang.annotation.Annotation;
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
import static com.erudika.para.persistence.AWSDynamoUtils.*;

/**
 * An implementation of the {@link DAO} interface using AWS DynamoDB as a data store.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class AWSDynamoDAO implements DAO {

	private static final Logger logger = LoggerFactory.getLogger(AWSDynamoDAO.class);
	private static final int MAX_ITEMS_PER_BATCH = 4; // Amazon DynamoDB limit ~= WRITE CAP

	/**
	 * No-args constructor
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
		logger.debug("DAO.create() {}", so.getId());
		return so.getId();
	}

	@Override
	public <P extends ParaObject> P read(String appid, String key) {
		if (StringUtils.isBlank(key)) {
			return null;
		}
		P so = fromRow(readRow(key, appid));
		logger.debug("DAO.read() {} -> {}", key, so == null ? null : so.getType());
		return so != null ? so : null;
	}

	@Override
	public <P extends ParaObject> void update(String appid, P so) {
		if (so != null && so.getId() != null) {
			so.setUpdated(Utils.timestamp());
			updateRow(so.getId(), appid, toRow(so, Locked.class));
			logger.debug("DAO.update() {}", so.getId());
		}
	}

	@Override
	public <P extends ParaObject> void delete(String appid, P so) {
		if (so != null && so.getId() != null) {
			deleteRow(so.getId(), appid);
			logger.debug("DAO.delete() {}", so.getId());
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
			setRowKey(key, row);
			PutItemRequest putItemRequest = new PutItemRequest(getTablNameForAppid(appid), row);
			client().putItem(putItemRequest);
		} catch (Exception e) {
			logger.error(null, e);
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
			UpdateItemRequest updateItemRequest = new UpdateItemRequest(getTablNameForAppid(appid),
					Collections.singletonMap(Config._KEY, new AttributeValue(key)), rou);
			client().updateItem(updateItemRequest);
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	private Map<String, AttributeValue> readRow(String key, String appid) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(appid)) {
			return null;
		}
		Map<String, AttributeValue> row = null;
		try {
			GetItemRequest getItemRequest = new GetItemRequest(getTablNameForAppid(appid),
					Collections.singletonMap(Config._KEY, new AttributeValue(key)));
			GetItemResult res = client().getItem(getItemRequest);
			if (res != null && res.getItem() != null && !res.getItem().isEmpty()) {
				row = res.getItem();
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
		return (row == null || row.isEmpty()) ? null : row;
	}

	private void deleteRow(String key, String appid) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(appid)) {
			return;
		}
		try {
			DeleteItemRequest delItemRequest = new DeleteItemRequest(getTablNameForAppid(appid),
					Collections.singletonMap(Config._KEY, new AttributeValue(key)));
			client().deleteItem(delItemRequest);
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	/////////////////////////////////////////////
	//				READ ALL FUNCTIONS
	/////////////////////////////////////////////

	@Override
	public <P extends ParaObject> void createAll(String appid, List<P> objects) {
		writeAll(appid, objects, false);
		logger.debug("DAO.createAll() {}", (objects == null) ? 0 : objects.size());
	}

	@Override
	public <P extends ParaObject> Map<String, P> readAll(String appid, List<String> keys, boolean getAllColumns) {
		if (keys == null || keys.isEmpty() || StringUtils.isBlank(appid)) {
			return new LinkedHashMap<String, P>();
		}

		Map<String, P> results = new LinkedHashMap<String, P>();
		ArrayList<Map<String, AttributeValue>> keyz = new ArrayList<Map<String, AttributeValue>>();

		for (String key : keys) {
			results.put(key, null);
			keyz.add(Collections.singletonMap(Config._KEY, new AttributeValue(key)));
		}

		KeysAndAttributes kna = new KeysAndAttributes().withKeys(keyz);
		if (!getAllColumns) {
			kna.setAttributesToGet(Arrays.asList(Config._KEY, Config._TYPE));
		}

		batchGet(Collections.singletonMap(getTablNameForAppid(appid), kna), results);

		logger.debug("DAO.readAll() {}", results.size());
		return results;
	}

	@Override
	public <P extends ParaObject> List<P> readPage(String appid, Pager pager) {
		List<P> results = new LinkedList<P>();
		if (StringUtils.isBlank(appid)) {
			return results;
		}
		if (pager == null) {
			pager = new Pager();
		}
		try {
			ScanRequest scanRequest = new ScanRequest().
					withTableName(getTablNameForAppid(appid)).
					withLimit(pager.getLimit()).
					withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL);

			if (!StringUtils.isBlank(pager.getLastKey())) {
				scanRequest.setExclusiveStartKey(Collections.singletonMap(Config._KEY,
						new AttributeValue(pager.getLastKey())));
			}

			ScanResult result = client().scan(scanRequest);
			logger.debug("readPage() CC: {}", result.getConsumedCapacity());

			for (Map<String, AttributeValue> item : result.getItems()) {
				P obj = fromRow(item);
				if (obj != null) {
					results.add(obj);
				}
			}
			if (result.getCount() > 0 && results.isEmpty() && result.getLastEvaluatedKey() != null) {
				pager.setLastKey(result.getLastEvaluatedKey().get(Config._KEY).getS());
				return readPage(appid, pager);
			}
		} catch (Exception e) {
			logger.error(null, e);
		}

		return results;
	}

	@Override
	public <P extends ParaObject> void updateAll(String appid, List<P> objects) {
		// DynamoDB doesn't have a BatchUpdate API yet so we have to do one of the following:
		// 1. update items one by one (chosen for simplicity)
		// 2. call writeAll() - writeAll(appid, objects, true);
		String table = getTablNameForAppid(appid);
		if (objects != null) {
			for (P object : objects) {
				update(table, object);
			}
		}
		logger.debug("DAO.updateAll() {}", (objects == null) ? 0 : objects.size());
	}

	@Override
	public <P extends ParaObject> void deleteAll(String appid, List<P> objects) {
		if (objects == null || objects.isEmpty() || StringUtils.isBlank(appid)) {
			return;
		}

		List<WriteRequest> reqs = new ArrayList<WriteRequest>();
		for (ParaObject object : objects) {
			if (object != null) {
				reqs.add(new WriteRequest().withDeleteRequest(new DeleteRequest().
						withKey(Collections.singletonMap(Config._KEY, new AttributeValue(object.getId())))));
			}
		}
		batchWrite(Collections.singletonMap(getTablNameForAppid(appid), reqs));
		logger.debug("DAO.deleteAll() {}", objects.size());
	}

	private <P extends ParaObject> void batchGet(Map<String, KeysAndAttributes> kna, Map<String, P> results) {
		if (kna == null || kna.isEmpty() || results == null) {
			return;
		}
		try {
			BatchGetItemResult result = client().batchGetItem(new BatchGetItemRequest().
					withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL).withRequestItems(kna));
			if (result == null) {
				return;
			}

			List<Map<String, AttributeValue>> res = result.getResponses().get(kna.keySet().iterator().next());

			for (Map<String, AttributeValue> item : res) {
				P obj = fromRow(item);
				results.put(item.get(Config._KEY).getS(), obj);
			}
			logger.debug("batchGet() CC: {}", result.getConsumedCapacity());

			if (result.getUnprocessedKeys() != null && !result.getUnprocessedKeys().isEmpty()) {
				Thread.sleep(1000);
				logger.warn("UNPROCESSED {}", result.getUnprocessedKeys().size());
				batchGet(result.getUnprocessedKeys(), results);
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	private void batchWrite(Map<String, List<WriteRequest>> items) {
		if (items == null || items.isEmpty()) {
			return;
		}
		try {
			BatchWriteItemResult result = client().batchWriteItem(new BatchWriteItemRequest().
					withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL).withRequestItems(items));
			if (result == null) {
				return;
			}
			logger.debug("batchWrite() CC: {}", result.getConsumedCapacity());

			if (result.getUnprocessedItems() != null && !result.getUnprocessedItems().isEmpty()) {
				Thread.sleep(1000);
				logger.warn("UNPROCESSED {0}", result.getUnprocessedItems().size());
				batchWrite(result.getUnprocessedItems());
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	private <P extends ParaObject> void writeAll(String appid, List<P> objects, boolean updateOp) {
		if (objects == null || objects.isEmpty() || StringUtils.isBlank(appid)) {
			return;
		}

		List<WriteRequest> reqs = new ArrayList<WriteRequest>();
		int batchSteps = 1;
		if ((objects.size() > MAX_ITEMS_PER_BATCH)) {
			batchSteps = (objects.size() / MAX_ITEMS_PER_BATCH) +
					((objects.size() % MAX_ITEMS_PER_BATCH > 0) ? 1 : 0);
		}

		Iterator<P> it = objects.iterator();
		int j = 0;

		for (int i = 0; i < batchSteps; i++) {
			while (it.hasNext() && j < MAX_ITEMS_PER_BATCH) {
				ParaObject object = it.next();
				if (StringUtils.isBlank(object.getId())) {
					object.setId(Utils.getNewId());
				}
				if (object.getTimestamp() == null) {
					object.setTimestamp(Utils.timestamp());
				}
				if (updateOp) {
					object.setUpdated(Utils.timestamp());
				}
				object.setAppid(appid);
				Map<String, AttributeValue> row = toRow(object, null);
				setRowKey(object.getId(), row);
				reqs.add(new WriteRequest().withPutRequest(new PutRequest().withItem(row)));
				j++;
			}
			batchWrite(Collections.singletonMap(getTablNameForAppid(appid), reqs));
			reqs.clear();
			j = 0;
		}
	}

	/////////////////////////////////////////////
	//				MISC FUNCTIONS
	/////////////////////////////////////////////

	private <P extends ParaObject> Map<String, AttributeValue> toRow(P so, Class<? extends Annotation> filter) {
		HashMap<String, AttributeValue> row = new HashMap<String, AttributeValue>();
		if (so == null) {
			return row;
		}
		for (Entry<String, Object> entry : Utils.getAnnotatedFields(so, filter).entrySet()) {
			Object value = entry.getValue();
			if (value != null && !StringUtils.isBlank(value.toString())) {
				row.put(entry.getKey(), new AttributeValue(value.toString()));
			}
		}
		return row;
	}

	private <P extends ParaObject> P fromRow(Map<String, AttributeValue> row) {
		if (row == null || row.isEmpty()) {
			return null;
		}
		Map<String, Object> props = new HashMap<String, Object>();
		for (Entry<String, AttributeValue> col : row.entrySet()) {
			props.put(col.getKey(), col.getValue().getS());
		}
		return Utils.setAnnotatedFields(props);
	}

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
