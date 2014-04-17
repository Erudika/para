/*
 * Copyright 2013-2014 Erudika. http://erudika.com
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
		logger.debug("DAO.read() {} -> {}", key, so);
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

	private String createRow(String key, String cf, Map<String, AttributeValue> row) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(cf) || row == null || row.isEmpty()) {
			return null;
		}
		try {
			setRowKey(key, row);
			PutItemRequest putItemRequest = new PutItemRequest(cf, row);
			client().putItem(putItemRequest);
		} catch (Exception e) {
			logger.error(null, e);
		}
		return key;
	}

	private void updateRow(String key, String cf, Map<String, AttributeValue> row) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(cf) || row == null || row.isEmpty()) {
			return;
		}
		Map<String, AttributeValueUpdate> rou = new HashMap<String, AttributeValueUpdate>();
		try {
			for (Entry<String, AttributeValue> attr : row.entrySet()) {
				rou.put(attr.getKey(), new AttributeValueUpdate(attr.getValue(), AttributeAction.PUT));
			}
			UpdateItemRequest updateItemRequest = new UpdateItemRequest(cf,
					Collections.singletonMap(Config._KEY, new AttributeValue(key)), rou);
			client().updateItem(updateItemRequest);
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	private Map<String, AttributeValue> readRow(String key, String cf) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(cf)) {
			return null;
		}
		Map<String, AttributeValue> row = null;
		try {
			GetItemRequest getItemRequest = new GetItemRequest(cf,
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

	private void deleteRow(String key, String cf) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(cf)) {
			return;
		}
		try {
			DeleteItemRequest delItemRequest = new DeleteItemRequest(cf,
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
		logger.debug("DAO.createAll() {}", objects.size());
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

		batchGet(Collections.singletonMap(appid, kna), results);

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
					withTableName(appid).
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
		writeAll(appid, objects, true);
		logger.debug("DAO.updateAll() {}", objects.size());
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
		batchWrite(Collections.singletonMap(appid, reqs));
		logger.debug("DAO.deleteAll() {}", objects.size());
	}

	private <P extends ParaObject> void writeAll(String appid, List<P> objects, boolean isUpdate) {
		if (objects == null || objects.isEmpty() || StringUtils.isBlank(appid)) {
			return;
		}

		List<WriteRequest> reqs = new ArrayList<WriteRequest>();
		int batchSteps = 1;
		long now = Utils.timestamp();
		if ((objects.size() > MAX_ITEMS_PER_BATCH)) {
			batchSteps = (objects.size() / MAX_ITEMS_PER_BATCH) +
					((objects.size() % MAX_ITEMS_PER_BATCH > 0) ? 1 : 0);
		}

		Iterator<P> it = objects.iterator();
		int j = 0;

		for (int i = 0; i < batchSteps; i++) {
			while (it.hasNext() && j < MAX_ITEMS_PER_BATCH) {
				ParaObject object = it.next();
				if (isUpdate) {
					object.setUpdated(now);
				}
				Map<String, AttributeValue> row = toRow(object, (isUpdate ? Locked.class : null));
				setRowKey(object.getId(), row);
				reqs.add(new WriteRequest().withPutRequest(new PutRequest().withItem(row)));
				j++;
			}
			batchWrite(Collections.singletonMap(appid, reqs));
			reqs.clear();
			j = 0;
		}
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

			Thread.sleep(1000);

			if (result.getUnprocessedItems() != null && !result.getUnprocessedItems().isEmpty()) {
				logger.warn("UNPROCESSED {0}", result.getUnprocessedItems().size());
				batchWrite(result.getUnprocessedItems());
			}
		} catch (Exception e) {
			logger.error(null, e);
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
				+ "attribute '{}' will be overwritten! '{}' is a reserved keyword.", Config._KEY);
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
