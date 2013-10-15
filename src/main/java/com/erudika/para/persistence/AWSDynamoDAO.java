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
package com.erudika.para.persistence;

import com.erudika.para.annotations.Stored;
import com.erudika.para.annotations.Locked;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.model.AttributeAction;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.BatchGetItemResult;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.KeysAndAttributes;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValueUpdate;
import com.amazonaws.services.dynamodbv2.model.BatchGetItemRequest;
import com.amazonaws.services.dynamodbv2.model.BatchWriteItemRequest;
import com.amazonaws.services.dynamodbv2.model.BatchWriteItemResult;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutRequest;
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.utils.Utils;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Singleton;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@Singleton
public class AWSDynamoDAO implements DAO {
		
	private static final Logger logger = Logger.getLogger(AWSDynamoDAO.class.getName());
	private static AmazonDynamoDBClient ddb;
//	private static AWSDynamoDAO dao;
	private static final String ENDPOINT = "dynamodb.".concat(Utils.AWS_REGION).concat(".amazonaws.com");
	private static final String LOCAL_ENDPOINT = "http://localhost:8000";
	private static final int MAX_ITEMS_PER_BATCH = 4; // Amazon DynamoDB limit ~= WRITE CAP
	////////////////////////////////////
	
	public AWSDynamoDAO() {
		if(Utils.IN_PRODUCTION){
			ddb = new AmazonDynamoDBClient();
			ddb.setEndpoint(ENDPOINT);
		}else{
			AWSDynamoHelper.start(null);
			
			ddb = new AmazonDynamoDBClient(new BasicAWSCredentials("local", "null"));
			ddb.setEndpoint(LOCAL_ENDPOINT);
			if(!ddb.listTables().getTableNames().contains(DAO.OBJECTS)){
				ddb.createTable(new CreateTableRequest().withTableName(DAO.OBJECTS).
						withKeySchema(new KeySchemaElement(DAO.CN_KEY, KeyType.HASH)).
						withAttributeDefinitions(new AttributeDefinition().withAttributeName(CN_KEY).
						withAttributeType(ScalarAttributeType.S)).
						withProvisionedThroughput(new ProvisionedThroughput(1L, 1L)));
					
			}
		}
	}
		
	/********************************************
	 *			CORE FUNCTIONS
	********************************************/
 
	@Override
	public <P extends ParaObject> String create(P so){
		if(so == null) return null;
		if (!Utils.isValidObject(so)) return null;
		if(StringUtils.isBlank(so.getId())) so.setId(Utils.getNewId());
		if(so.getTimestamp() == null) so.setTimestamp(Utils.timestamp());

		createRow(so.getId(), OBJECTS, toRow(so, null));
		
		logger.log(Level.FINE, "DAO.create() {0}", new Object[]{so.getId()});
		return so.getId();
	}
	
	@Override
	public <P extends ParaObject> P read(String key) {
		if(StringUtils.isBlank(key)) return null;
		
		P so = fromRow(readRow(key, OBJECTS));
		
		logger.log(Level.FINE, "DAO.read() {0} -> {1}", new Object[]{key, so});
		return so != null ? so : null;
	}

	@Override
	public <P extends ParaObject> void update(P so){
		if(so == null || so.getId() == null) return;
		so.setUpdated(System.currentTimeMillis());
		
		updateRow(so.getId(), OBJECTS, toRow(so, Locked.class));
		
		logger.log(Level.FINE, "DAO.update() {0}", new Object[]{so.getId()});
	}

	@Override
	public <P extends ParaObject> void delete(P so){
		if(so == null || so.getId() == null) return ;
		
		deleteRow(so.getId(), OBJECTS);
		
		logger.log(Level.FINE, "DAO.delete() {0}", new Object[]{so.getId()});
	}

	/********************************************
	 *				COLUMN FUNCTIONS
	********************************************/

	@Override
	public void putColumn(String key, String cf, String colName, String colValue){
		if(StringUtils.isBlank(key) || StringUtils.isBlank(cf) || 
				StringUtils.isBlank(colName)|| StringUtils.isBlank(colValue)) return;
		
		Map<String, AttributeValueUpdate> row = new HashMap<String, AttributeValueUpdate>();
		try {
			row.put(colName, new AttributeValueUpdate(new AttributeValue(colValue), AttributeAction.PUT));
			UpdateItemRequest updateItemRequest = new UpdateItemRequest(cf, Collections.singletonMap(CN_KEY, new AttributeValue(key)), row);
			ddb.updateItem(updateItemRequest); 
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
	}

	@Override
	public String getColumn(String key, String cf, String colName) {
		if(StringUtils.isBlank(key) || StringUtils.isBlank(cf) || StringUtils.isBlank(colName)) return null;
		String result = null;
		try {
			GetItemRequest getItemRequest = new GetItemRequest(cf, Collections.singletonMap(CN_KEY, new AttributeValue(key)))
					.withAttributesToGet(Collections.singletonList(colName));
			GetItemResult res = ddb.getItem(getItemRequest);
			if(res != null && res.getItem() != null && !res.getItem().isEmpty()){
				result = res.getItem().get(colName).getS();
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
		return result;
	}
	
	@Override
	public void removeColumn(String key, String cf, String colName) {
		if(StringUtils.isBlank(key) || StringUtils.isBlank(cf) || StringUtils.isBlank(colName)) return;

		Map<String, AttributeValueUpdate> row = new HashMap<String, AttributeValueUpdate>();
		try {
			row.put(colName, new AttributeValueUpdate().withAction(AttributeAction.DELETE));
			UpdateItemRequest updateItemRequest = new UpdateItemRequest(cf, Collections.singletonMap(CN_KEY, new AttributeValue(key)), row);
			ddb.updateItem(updateItemRequest); 
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
	}
	
	@Override
	public boolean existsColumn(String key, String cf, String columnName){
		if(StringUtils.isBlank(key)) return false;
		return getColumn(key, cf, columnName) != null;
	}

	/********************************************
	 *				ROW FUNCTIONS
	********************************************/

	private String createRow(String key, String cf, Map<String, AttributeValue> row){
		if(StringUtils.isBlank(key) || StringUtils.isBlank(cf) || row == null || row.isEmpty()) return null;
		try {
			setRowKey(key, row);
			PutItemRequest putItemRequest = new PutItemRequest(cf, row);
			ddb.putItem(putItemRequest); 
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
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
			ddb.updateItem(updateItemRequest); 
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
	}

	private Map<String, AttributeValue> readRow(String key, String cf){
		if(StringUtils.isBlank(key) || StringUtils.isBlank(cf)) return null;
		Map<String, AttributeValue> row = null;
		try {
			GetItemRequest getItemRequest = new GetItemRequest(cf,
					Collections.singletonMap(CN_KEY, new AttributeValue(key)));
			GetItemResult res = ddb.getItem(getItemRequest);
			if(res != null && res.getItem() != null && !res.getItem().isEmpty()){
				row = res.getItem();
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
		return (row == null || row.isEmpty()) ? null : row;
	}

	private void deleteRow(String key, String cf){
		if(StringUtils.isBlank(key) || StringUtils.isBlank(cf)) return;
		try {
			DeleteItemRequest delItemRequest = new DeleteItemRequest(cf, 
					Collections.singletonMap(CN_KEY, new AttributeValue(key)));
			ddb.deleteItem(delItemRequest);
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
	}
	
	/********************************************
	 *				READ ALL FUNCTIONS
	********************************************/
	
	@Override
	public <P extends ParaObject> void createAll(List<P> objects){
		writeAll(objects, false);
	}
	
	@Override
	public <P extends ParaObject> Map<String, P> readAll(List<String> keys, boolean getAllAtrributes){
		if(keys == null || keys.isEmpty()) return new LinkedHashMap<String, P>();
		
		Map<String, P> results = new LinkedHashMap<String, P>();
		ArrayList<Map<String, AttributeValue>> keyz = new ArrayList<Map<String, AttributeValue>>();
		
		for (String key : keys) {
			results.put(key, null);
			keyz.add(Collections.singletonMap(CN_KEY, new AttributeValue(key)));
		}
		
		KeysAndAttributes kna = new KeysAndAttributes().withKeys(keyz);
		if(!getAllAtrributes) kna.setAttributesToGet(Arrays.asList(CN_KEY, CN_CLASSNAME));
		
		batchGet(Collections.singletonMap(OBJECTS, kna), results);
		
		return results;
	}
	
	@Override
	public <P extends ParaObject> List<P> readPage(String cf, String lastKey){
		List<P> results = new LinkedList<P>();
		if(StringUtils.isBlank(cf)) return results;
		
		try {
			ScanRequest scanRequest = new ScanRequest().withTableName(OBJECTS).withLimit(Utils.MAX_ITEMS_PER_PAGE).
					withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL);
			
			if(!StringUtils.isBlank(lastKey)){
				scanRequest.setExclusiveStartKey(Collections.singletonMap(CN_KEY, new AttributeValue(lastKey)));
			}
			
			ScanResult result = ddb.scan(scanRequest);
			logger.log(Level.FINE, "readPage() CC: {0}", new Object[]{result.getConsumedCapacity()});
			
			for (Map<String, AttributeValue> item : result.getItems()) {
				P obj = fromRow(item);
				if(obj != null) {
					results.add(obj);
				}
			}
			if(result.getCount() > 0 && results.isEmpty() && result.getLastEvaluatedKey() != null){
				return readPage(cf, result.getLastEvaluatedKey().get(CN_KEY).getS());
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
		
		return results;
	}
	
	@Override
	public <P extends ParaObject> void updateAll(List<P> objects){
		writeAll(objects, true);
	}
	
	@Override
	public <P extends ParaObject> void deleteAll(List<P> objects){
		if(objects == null || objects.isEmpty()) return;
		
		List<WriteRequest> reqs = new ArrayList<WriteRequest>();
		for (ParaObject object : objects) {
			if(object != null){
				reqs.add(new WriteRequest().withDeleteRequest(new DeleteRequest().
						withKey(Collections.singletonMap(CN_KEY, new AttributeValue(object.getId())))));
			}
		}
		batchWrite(Collections.singletonMap(OBJECTS, reqs));
	}
	
	private  <P extends ParaObject> void writeAll(List<P> objects, boolean isUpdate){
		if(objects == null || objects.isEmpty()) return;
		
		List<WriteRequest> reqs = new ArrayList<WriteRequest>();
		int batchSteps = 1;
		if((objects.size() > MAX_ITEMS_PER_BATCH)){
			batchSteps = (objects.size() / MAX_ITEMS_PER_BATCH) + 
					((objects.size() % MAX_ITEMS_PER_BATCH > 0) ? 1 : 0);
		}
				
		Iterator<P> it = objects.iterator();
		
		for (int i = 0, j = 0; i < batchSteps; i++, j = 0) {
			while (it.hasNext() && j < MAX_ITEMS_PER_BATCH) {
				ParaObject object = it.next();
				Map<String, AttributeValue> row = toRow(object, (isUpdate ? Locked.class : null));
				setRowKey(object.getId(), row);
				reqs.add(new WriteRequest().withPutRequest(new PutRequest().withItem(row)));
				j++;
			}
			batchWrite(Collections.singletonMap(OBJECTS, reqs));
			reqs.clear();
		}
	}
	
	private <P extends ParaObject> void batchGet(Map<String, KeysAndAttributes> kna, Map<String, P> results){
		if(kna == null || kna.isEmpty() || results == null) return;
		try{
			BatchGetItemResult result = ddb.batchGetItem(new BatchGetItemRequest().
					withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL).withRequestItems(kna));
			if(result == null) return;
			
			List<Map<String, AttributeValue>> res = result.getResponses().get(OBJECTS);

			for (Map<String, AttributeValue> item : res) results.put(item.get(CN_KEY).getS(), (P) fromRow(item));
			logger.log(Level.FINE, "batchGet() CC: {0}", new Object[]{result.getConsumedCapacity()});
			
			if(result.getUnprocessedKeys() != null && !result.getUnprocessedKeys().isEmpty()){
				Thread.sleep(1000);
				logger.log(Level.WARNING, "UNPROCESSED {0}", result.getUnprocessedKeys().size());
				batchGet(result.getUnprocessedKeys(), results);
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
	}
	
	private void batchWrite(Map<String, List<WriteRequest>> items){
		if(items == null || items.isEmpty()) return;
		try{						
			BatchWriteItemResult result = ddb.batchWriteItem(new BatchWriteItemRequest().
					withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL).withRequestItems(items));
			if(result == null) return;
			logger.log(Level.FINE, "batchWrite() CC: {0}", new Object[]{result.getConsumedCapacity()});

			Thread.sleep(1000);
			
			if(result.getUnprocessedItems() != null && !result.getUnprocessedItems().isEmpty()){
				logger.log(Level.WARNING, "UNPROCESSED {0}", result.getUnprocessedItems().size());
				batchWrite(result.getUnprocessedItems());
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
	}
	
	/********************************************
	 *				MISC FUNCTIONS
	********************************************/
	
	private <P extends ParaObject> Map<String, AttributeValue> toRow(P so, Class<? extends Annotation> filter){
		if(so == null) return new HashMap<String, AttributeValue>();
		HashMap<String, Object> propsMap = Utils.getAnnotatedFields(so, Stored.class, filter);
		HashMap<String, AttributeValue> row = new HashMap<String, AttributeValue>();
		if (so instanceof Sysprop) propsMap.putAll(((Sysprop) so).getProperties());
		
		for (Entry<String, Object> entry : propsMap.entrySet()) {
			String field = entry.getKey();
			Object value = entry.getValue();

			if(value != null && !StringUtils.isBlank(value.toString())){
				row.put(field, new AttributeValue(value.toString()));
			}
		}
		return row;
	}

	private <P extends ParaObject> P fromRow(Map<String, AttributeValue> row) {
		if (row == null || row.isEmpty())	return null;

		P transObject = null;
		Map<String, String> props = new HashMap<String, String>();
		Map<String, Object> sysprops = new HashMap<String, Object>();
		
		try {
			for (Entry<String, AttributeValue> col : row.entrySet()) {
				String name = col.getKey();
				String value = col.getValue().getS();
				
				if(Sysprop.isSysprop(name)){
					sysprops.put(name, value);
				}else{
					props.put(name, value);
				}
			}
			Class<?> clazz = Utils.toClass(props.get(CN_CLASSNAME));
			if(clazz != null){
				transObject = (P) clazz.newInstance();
				BeanUtils.populate(transObject, props);
				if(transObject instanceof Sysprop && !sysprops.isEmpty()){
					((Sysprop) transObject).setProperties(sysprops);
				}
			}
		} catch (Exception ex) {
			logger.log(Level.SEVERE, null, ex);
		}

		return transObject;
	}
	
	private void setRowKey(String key, Map<String, AttributeValue> row){
		if(row.containsKey(CN_KEY)) logger.log(Level.WARNING, "Attribute name conflict:  "
			+ "attribute '{0}' will be overwritten! '{0}' is a reserved keyword.", new Object[]{CN_KEY});
		row.put(CN_KEY, new AttributeValue(key));
	}
	
}
