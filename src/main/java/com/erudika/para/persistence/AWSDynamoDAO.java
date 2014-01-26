/*
 * Copyright 2013 Alex Bogdanovski <alex@erudika.com>.
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
package com.erudika.para.persistence;

import com.erudika.para.annotations.Stored;
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
import com.erudika.para.utils.Utils;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.Map.Entry;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
@Singleton
public class AWSDynamoDAO implements DAO {
		
	private static final Logger logger = LoggerFactory.getLogger(AWSDynamoDAO.class);
	private static final int MAX_ITEMS_PER_BATCH = 4; // Amazon DynamoDB limit ~= WRITE CAP
	private static AmazonDynamoDBClient ddbClient;
	
	public AWSDynamoDAO() {
	}
	
	AmazonDynamoDBClient client(){
		if(ddbClient == null){
			ddbClient = AWSDynamoUtils.getClient();
		}		
		return ddbClient;
	}
		
	/********************************************
	 *			CORE FUNCTIONS
	********************************************/
 
	@Override
	public <P extends ParaObject> String create(String appName, P so){
		if(so == null) return null;
		if(StringUtils.isBlank(so.getId())) so.setId(Utils.getNewId());
		if(so.getTimestamp() == null) so.setTimestamp(Utils.timestamp());

		createRow(so.getId(), appName, toRow(so, null));
		
		logger.debug("DAO.create() {}", so.getId());
		return so.getId();
	}
	
	@Override
	public <P extends ParaObject> P read(String appName, String key) {
		if(StringUtils.isBlank(key)) return null;
		
		P so = fromRow(readRow(key, appName));
		
		logger.debug("DAO.read() {} -> {}", key, so);
		return so != null ? so : null;
	}

	@Override
	public <P extends ParaObject> void update(String appName, P so){
		if(so == null || so.getId() == null) return;
		so.setUpdated(System.currentTimeMillis());
		
		updateRow(so.getId(), appName, toRow(so, Locked.class));
		
		logger.debug("DAO.update() {}", so.getId());
	}

	@Override
	public <P extends ParaObject> void delete(String appName, P so){
		if(so == null || so.getId() == null) return ;
		
		deleteRow(so.getId(), appName);
		
		logger.debug("DAO.delete() {}", so.getId());
	}

	/********************************************
	 *				COLUMN FUNCTIONS
	********************************************/

	@Override
	public void putColumn(String appName, String key, String colName, String colValue){
		if(StringUtils.isBlank(key) || StringUtils.isBlank(appName) || 
				StringUtils.isBlank(colName)|| StringUtils.isBlank(colValue)) return;
		
		Map<String, AttributeValueUpdate> row = new HashMap<String, AttributeValueUpdate>();
		try {
			row.put(colName, new AttributeValueUpdate(new AttributeValue(colValue), AttributeAction.PUT));
			UpdateItemRequest updateItemRequest = new UpdateItemRequest(appName, Collections.singletonMap(CN_KEY, new AttributeValue(key)), row);
			client().updateItem(updateItemRequest); 
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	@Override
	public String getColumn(String appName, String key, String colName) {
		if(StringUtils.isBlank(key) || StringUtils.isBlank(appName) || StringUtils.isBlank(colName)) return null;
		String result = null;
		try {
			GetItemRequest getItemRequest = new GetItemRequest(appName, Collections.singletonMap(CN_KEY, new AttributeValue(key)))
					.withAttributesToGet(Collections.singletonList(colName));
			GetItemResult res = client().getItem(getItemRequest);
			if(res != null && res.getItem() != null && !res.getItem().isEmpty()){
				result = res.getItem().get(colName).getS();
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
		return result;
	}
	
	@Override
	public void removeColumn(String appName, String key, String colName) {
		if(StringUtils.isBlank(key) || StringUtils.isBlank(appName) || StringUtils.isBlank(colName)) return;

		Map<String, AttributeValueUpdate> row = new HashMap<String, AttributeValueUpdate>();
		try {
			row.put(colName, new AttributeValueUpdate().withAction(AttributeAction.DELETE));
			UpdateItemRequest updateItemRequest = new UpdateItemRequest(appName, Collections.singletonMap(CN_KEY, new AttributeValue(key)), row);
			client().updateItem(updateItemRequest); 
		} catch (Exception e) {
			logger.error(null, e);
		}
	}
	
	@Override
	public boolean existsColumn(String appName, String key, String columnName){
		if(StringUtils.isBlank(key)) return false;
		try {
			return getColumn(appName, key, columnName) != null;
		} catch (Exception e) {
			return false;
		}
	}

	/********************************************
	 *				ROW FUNCTIONS
	********************************************/

	private String createRow(String key, String cf, Map<String, AttributeValue> row){
		if(StringUtils.isBlank(key) || StringUtils.isBlank(cf) || row == null || row.isEmpty()) return null;
		try {
			setRowKey(key, row);
			PutItemRequest putItemRequest = new PutItemRequest(cf, row);
			client().putItem(putItemRequest);
		} catch (Exception e) {
			logger.error(null, e);
		}		
		return key;
	}
	
	private void updateRow(String key, String cf, Map<String, AttributeValue> row){
		if(StringUtils.isBlank(key) || StringUtils.isBlank(cf) || row == null || row.isEmpty()) return;
		Map<String, AttributeValueUpdate> rou = new HashMap<String, AttributeValueUpdate>();
		try {
			for (Entry<String, AttributeValue> attr : row.entrySet()) {
				rou.put(attr.getKey(), new AttributeValueUpdate(attr.getValue(), AttributeAction.PUT));
			}
			UpdateItemRequest updateItemRequest = new UpdateItemRequest(cf, 
					Collections.singletonMap(CN_KEY, new AttributeValue(key)), rou);
			client().updateItem(updateItemRequest); 
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	private Map<String, AttributeValue> readRow(String key, String cf){
		if(StringUtils.isBlank(key) || StringUtils.isBlank(cf)) return null;
		Map<String, AttributeValue> row = null;
		try {
			GetItemRequest getItemRequest = new GetItemRequest(cf,
					Collections.singletonMap(CN_KEY, new AttributeValue(key)));
			GetItemResult res = client().getItem(getItemRequest);
			if(res != null && res.getItem() != null && !res.getItem().isEmpty()){
				row = res.getItem();
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
		return (row == null || row.isEmpty()) ? null : row;
	}

	private void deleteRow(String key, String cf){
		if(StringUtils.isBlank(key) || StringUtils.isBlank(cf)) return;
		try {
			DeleteItemRequest delItemRequest = new DeleteItemRequest(cf, 
					Collections.singletonMap(CN_KEY, new AttributeValue(key)));
			client().deleteItem(delItemRequest);
		} catch (Exception e) {
			logger.error(null, e);
		}
	}
	
	/********************************************
	 *				READ ALL FUNCTIONS
	********************************************/
	
	@Override
	public <P extends ParaObject> void createAll(String appName, List<P> objects){
		writeAll(appName, objects, false);
		logger.debug("DAO.createAll() {}", objects.size());
	}
	
	@Override
	public <P extends ParaObject> Map<String, P> readAll(String appName, List<String> keys, boolean getAllAtrributes){
		if(keys == null || keys.isEmpty() || StringUtils.isBlank(appName)) return new LinkedHashMap<String, P>();
		
		Map<String, P> results = new LinkedHashMap<String, P>();
		ArrayList<Map<String, AttributeValue>> keyz = new ArrayList<Map<String, AttributeValue>>();
		
		for (String key : keys) {
			results.put(key, null);
			keyz.add(Collections.singletonMap(CN_KEY, new AttributeValue(key)));
		}
		
		KeysAndAttributes kna = new KeysAndAttributes().withKeys(keyz);
		if(!getAllAtrributes) kna.setAttributesToGet(Arrays.asList(CN_KEY, CN_CLASSNAME));
		
		batchGet(Collections.singletonMap(appName, kna), results);
		
		logger.debug("DAO.readAll() {}", results.size());
		return results;
	}
	
	@Override
	public <P extends ParaObject> List<P> readPage(String appName, String lastKey){
		List<P> results = new LinkedList<P>();
		if(StringUtils.isBlank(appName)) return results;
		
		try {
			ScanRequest scanRequest = new ScanRequest().withTableName(appName).withLimit(Config.MAX_ITEMS_PER_PAGE).
					withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL);
			
			if(!StringUtils.isBlank(lastKey)){
				scanRequest.setExclusiveStartKey(Collections.singletonMap(CN_KEY, new AttributeValue(lastKey)));
			}
			
			ScanResult result = client().scan(scanRequest);
			logger.debug("readPage() CC: {}", result.getConsumedCapacity());
			
			for (Map<String, AttributeValue> item : result.getItems()) {
				P obj = fromRow(item);
				if(obj != null) {
					results.add(obj);
				}
			}
			if(result.getCount() > 0 && results.isEmpty() && result.getLastEvaluatedKey() != null){
				return readPage(appName, result.getLastEvaluatedKey().get(CN_KEY).getS());
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
		
		return results;
	}
	
	@Override
	public <P extends ParaObject> void updateAll(String appName, List<P> objects){
		writeAll(appName, objects, true);
		logger.debug("DAO.updateAll() {}", objects.size());
	}
	
	@Override
	public <P extends ParaObject> void deleteAll(String appName, List<P> objects){
		if(objects == null || objects.isEmpty() || StringUtils.isBlank(appName)) return;
		
		List<WriteRequest> reqs = new ArrayList<WriteRequest>();
		for (ParaObject object : objects) {
			if(object != null){
				reqs.add(new WriteRequest().withDeleteRequest(new DeleteRequest().
						withKey(Collections.singletonMap(CN_KEY, new AttributeValue(object.getId())))));
			}
		}
		batchWrite(Collections.singletonMap(appName, reqs));
		logger.debug("DAO.deleteAll() {}", objects.size());
	}
	
	private <P extends ParaObject> void writeAll(String appName, List<P> objects, boolean isUpdate){
		if(objects == null || objects.isEmpty() || StringUtils.isBlank(appName)) return;
		
		List<WriteRequest> reqs = new ArrayList<WriteRequest>();
		int batchSteps = 1;
		long now = System.currentTimeMillis();
		if((objects.size() > MAX_ITEMS_PER_BATCH)){
			batchSteps = (objects.size() / MAX_ITEMS_PER_BATCH) + 
					((objects.size() % MAX_ITEMS_PER_BATCH > 0) ? 1 : 0);
		}
				
		Iterator<P> it = objects.iterator();
		
		for (int i = 0, j = 0; i < batchSteps; i++, j = 0) {
			while (it.hasNext() && j < MAX_ITEMS_PER_BATCH) {
				ParaObject object = it.next();
				if(isUpdate) object.setUpdated(now);
				Map<String, AttributeValue> row = toRow(object, (isUpdate ? Locked.class : null));
				setRowKey(object.getId(), row);
				reqs.add(new WriteRequest().withPutRequest(new PutRequest().withItem(row)));
				j++;
			}
			batchWrite(Collections.singletonMap(appName, reqs));
			reqs.clear();
		}
	}
	
	private <P extends ParaObject> void batchGet(Map<String, KeysAndAttributes> kna, Map<String, P> results){
		if(kna == null || kna.isEmpty() || results == null) return;
		try{
			BatchGetItemResult result = client().batchGetItem(new BatchGetItemRequest().
					withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL).withRequestItems(kna));
			if(result == null) return;
			
			List<Map<String, AttributeValue>> res = result.getResponses().get(kna.keySet().iterator().next());

			for (Map<String, AttributeValue> item : res){
				results.put(item.get(CN_KEY).getS(), (P) fromRow(item));
			}
			logger.debug("batchGet() CC: {}", result.getConsumedCapacity());
			
			if(result.getUnprocessedKeys() != null && !result.getUnprocessedKeys().isEmpty()){
				Thread.sleep(1000);
				logger.warn("UNPROCESSED {}", result.getUnprocessedKeys().size());
				batchGet(result.getUnprocessedKeys(), results);
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
	}
	
	private void batchWrite(Map<String, List<WriteRequest>> items){
		if(items == null || items.isEmpty()) return;
		try{						
			BatchWriteItemResult result = client().batchWriteItem(new BatchWriteItemRequest().
					withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL).withRequestItems(items));
			if(result == null) return;
			logger.debug("batchWrite() CC: {}", result.getConsumedCapacity());

			Thread.sleep(1000);
			
			if(result.getUnprocessedItems() != null && !result.getUnprocessedItems().isEmpty()){
				logger.warn("UNPROCESSED {0}", result.getUnprocessedItems().size());
				batchWrite(result.getUnprocessedItems());
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
	}
	
	/********************************************
	 *				MISC FUNCTIONS
	********************************************/
	
	private <P extends ParaObject> Map<String, AttributeValue> toRow(P so, Class<? extends Annotation> filter){
		HashMap<String, AttributeValue> row = new HashMap<String, AttributeValue>();
		if(so == null) return row;		
		for (Entry<String, Object> entry : Utils.getAnnotatedFields(so, Stored.class, filter).entrySet()) {
			Object value = entry.getValue();
			if(value != null && !StringUtils.isBlank(value.toString())){
				row.put(entry.getKey(), new AttributeValue(value.toString()));
			}
		}
		return row;
	}

	private <P extends ParaObject> P fromRow(Map<String, AttributeValue> row) {
		if (row == null || row.isEmpty()) return null;
		Map<String, Object> props = new HashMap<String, Object>();
		for (Entry<String, AttributeValue> col : row.entrySet()) {
			props.put(col.getKey(), col.getValue().getS());
		}
		return Utils.setAnnotatedFields(props);
	}
	
	private void setRowKey(String key, Map<String, AttributeValue> row){
		if(row.containsKey(CN_KEY)) logger.warn("Attribute name conflict:  "
			+ "attribute '{}' will be overwritten! '{}' is a reserved keyword.", CN_KEY);
		row.put(CN_KEY, new AttributeValue(key));
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
	public String getColumn(String key, String colName) {
		return getColumn(Config.APP_NAME_NS, key, colName);
	}

	@Override
	public void putColumn(String key, String colName, String colValue) {
		putColumn(Config.APP_NAME_NS, key, colName, colValue);
	}

	@Override
	public void removeColumn(String key, String colName) {
		removeColumn(Config.APP_NAME_NS, key, colName);
	}

	@Override
	public boolean existsColumn(String key, String columnName) {
		return existsColumn(Config.APP_NAME_NS, key, columnName);
	}

	@Override
	public <P extends ParaObject> void createAll(List<P> objects) {
		createAll(Config.APP_NAME_NS, objects);
	}

	@Override
	public <P extends ParaObject> Map<String, P> readAll(List<String> keys, boolean getAllAtrributes) {
		return readAll(Config.APP_NAME_NS, keys, getAllAtrributes);
	}

	@Override
	public <P extends ParaObject> List<P> readPage(String lastKey) {
		return readPage(Config.APP_NAME_NS, lastKey);
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
