/*
 * Copyright 2014 Alex Bogdanovski <alex@erudika.com>.
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

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.erudika.para.Para;
import com.erudika.para.utils.Config;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public final class AWSDynamoUtils {
	
	private static AmazonDynamoDBClient ddbClient;
	private static final String LOCAL_ENDPOINT = "http://localhost:8000";
	private static final String ENDPOINT = "dynamodb.".concat(Config.AWS_REGION).concat(".amazonaws.com");
	private static final Logger logger = LoggerFactory.getLogger(AWSDynamoUtils.class);

	private AWSDynamoUtils() {}
	
	public static AmazonDynamoDBClient getClient(){
		if(ddbClient != null) return ddbClient;
		
		if(Config.IN_PRODUCTION){
			ddbClient = new AmazonDynamoDBClient();
			ddbClient.setEndpoint(ENDPOINT);
		}else{
			ddbClient = new AmazonDynamoDBClient(new BasicAWSCredentials("local", "null"));
			ddbClient.setEndpoint(LOCAL_ENDPOINT);
		}
		
		Para.addDestroyListener(new Para.DestroyListener() {
			public void onDestroy() {
				shutdownClient();
			}
		});

		return ddbClient;
	}
	
	public static void shutdownClient(){
		if(ddbClient != null) ddbClient.shutdown();
		ddbClient = null;
	}
	
	public static boolean existsTable(String appName){
		if(StringUtils.isBlank(appName)) return false;
		try {
			List<String> tables = getClient().listTables().getTableNames();
			return tables != null && tables.contains(appName);
		} catch (Exception e) {
			return false;
		}
	}
	
	public static boolean createTable(String appName){
		return createTable(appName, 2L, 1L);
	}
	
	public static boolean createTable(String appName, Long readCapacity, Long writeCapacity){
		if(StringUtils.isBlank(appName) || StringUtils.containsWhitespace(appName) || existsTable(appName)) return false;
		try {
			getClient().createTable(new CreateTableRequest().withTableName(appName).
					withKeySchema(new KeySchemaElement(DAO.CN_KEY, KeyType.HASH)).
					withAttributeDefinitions(new AttributeDefinition().withAttributeName(DAO.CN_KEY).
					withAttributeType(ScalarAttributeType.S)).
					withProvisionedThroughput(new ProvisionedThroughput(readCapacity, writeCapacity)));
		} catch (Exception e) {
			logger.error(null, e);
			return false;
		}		
		return true;
	}
	
	public static boolean deleteTable(String appName){
		if(StringUtils.isBlank(appName) || !existsTable(appName)) return false;
		try {
			getClient().deleteTable(new DeleteTableRequest().withTableName(appName));
		} catch (Exception e) {
			logger.error(null, e);
			return false;
		}		
		return true;
	}
	
}
