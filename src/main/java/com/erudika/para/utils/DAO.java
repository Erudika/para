/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erudika.para.utils;

import com.erudika.para.core.User;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.model.AttributeAction;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.BatchGetItemResult;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.KeysAndAttributes;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValueUpdate;
import com.amazonaws.services.dynamodbv2.model.BatchGetItemRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.erudika.para.core.PObject;
import static com.erudika.para.utils.Utils.getClassname;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author alexb
 */
public class DAO {
		
	private static final Logger logger = Logger.getLogger(DAO.class.getName());
	private static AmazonDynamoDBClient ddb;
	private static DAO dao;
	
	//////////  DB CONFIG  ////////////// 
	public static final String TABLE_PREFIX = Utils.PRODUCT_NAME.toLowerCase().concat("-");
	public static final String ENDPOINT = "dynamodb.".concat(Utils.AWS_REGION).concat(".amazonaws.com");
	////////////////////////////////////
	
	//////////  DB TABLES  ////////////// 
	public static final String OBJECTS = TABLE_PREFIX + "objects";
//	public static final String VOTES = "Votes";
//	public static final String LOCALES_TRANSLATIONS = "LocalesTranslations";
//	public static final String APPROVED_TRANSLATIONS = "ApprovedTranslations";
//	public static final String LANGUAGE = "Language";
	
	public static final int MAX_ITEMS_GET_LIMIT = 100; // Amazon DynamoDB limit
	public static final int MAX_PAGES = 10000;
	public static final int MIN_PASS_LENGTH = 6;
	public static final int VOTE_LOCKED_FOR_SEC = 4*7*24*60*60; //1 month in seconds
	public static final long VOTE_LOCK_AFTER_SEC = 30; // 30 sec
	public static final int TOKEN_EXPIRES_AFTER_SEC = 20*60; //20 minutes in sec
	public static final String CN_COUNTS_COUNT = "count";
	public static final String CN_AUTHSTAMP = "authstamp";
	public static final String CN_AUTHTOKEN = "authtoken";
	public static final String CN_PASSWORD = "password";
	public static final String CN_SALT = "salt";
	public static final String CN_TOKEN = "token";
	public static final String CN_KEY = "key";
	public static final String CN_ID = "id";
	public static final String CN_TIMESTAMP = "timestamp";
	public static final String CN_UPDATED = "updated";
	public static final String CN_CLASSTYPE = "classtype";
	public static final String SYSTEM_OBJECTS_KEY = "1";
//	public static final String SYSTEM_MESSAGE_KEY = "system-message";
	public static final String SYSTEM_TYPE = "system"; // system data type
	public static final String ADDRESS_TYPE = "address"; // default address type
	public static final String TAG_TYPE = "tag"; // default tag type
	public static final String VOTE_TYPE = "vote"; // default vote type
	public static final String SYSTEM_FXRATES_KEY = "system-fxrates";
	public static final String SYSTEM_TRANS_PROGRESS_KEY = "system-transprogress";
	
	
	private DAO() {
		if(Utils.IN_PRODUCTION){
			ddb = new AmazonDynamoDBClient();
		}else{
			ddb = new AmazonDynamoDBClient(new BasicAWSCredentials(Utils.AWS_ACCESSKEY, Utils.AWS_SECRETKEY));
		}
		ddb.setEndpoint(ENDPOINT);
	}
	
	public static DAO getInstance(){
		if(dao == null){
			Utils.initConfig();
			dao = new DAO();
		}
		return dao;
	}
	
	/********************************************
	 *			CORE FUNCTIONS
	********************************************/
 
	public String create(PObject so){
		if(so == null) return null;
		String[] errors = Utils.validateRequest(so);
		if (errors.length != 0) return null;
		if(StringUtils.isBlank(so.getId())) so.setId(Utils.getNewId());
		if(so.getTimestamp() == null) so.setTimestamp(Utils.timestamp());

		createRow(so.getId(), OBJECTS, toRow(so, null));
		
		logger.log(Level.INFO, "DAO.create() {0}", new Object[]{so.getId()});
		Search.index(so, so.getClassname());
		return so.getId();
	}
	
	public PObject read(String key) {
		if(StringUtils.isBlank(key)) return null;
		
		PObject so = fromRow(readRow(key, OBJECTS));
		
		logger.log(Level.INFO, "DAO.read() {0} -> {1}", new Object[]{key, so});
		return so != null ? so : null;
	}

	public void update(PObject so){
		if(so == null || so.getId() == null) return;
		so.setUpdated(System.currentTimeMillis());
		
		updateRow(so.getId(), OBJECTS, toRow(so, Locked.class));
		
		logger.log(Level.INFO, "DAO.update() {0}", new Object[]{so.getId()});
		Search.index(so, so.getClassname());
	}

	public void delete(PObject so){
		if(so == null || so.getId() == null) return ;
		
		deleteRow(so.getId(), OBJECTS);
		
		logger.log(Level.INFO, "DAO.delete() {0}", new Object[]{so.getId()});
		Search.unindex(so.getId(), so.getClassname());
	}

	/********************************************
	 *				COLUMN FUNCTIONS
	********************************************/

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
			row.put(CN_KEY, new AttributeValue(key));
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
			UpdateItemRequest updateItemRequest = new UpdateItemRequest(cf, Collections.singletonMap(CN_KEY, new AttributeValue(key)), rou);
			ddb.updateItem(updateItemRequest); 
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
	}

	private Map<String, AttributeValue> readRow(String key, String cf){
		if(StringUtils.isBlank(key) || StringUtils.isBlank(cf)) return null;
		Map<String, AttributeValue> row = null;
		try {
			GetItemRequest getItemRequest = new GetItemRequest(cf, Collections.singletonMap(CN_KEY, new AttributeValue(key)));
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
			DeleteItemRequest delItemRequest = new DeleteItemRequest(cf, Collections.singletonMap(CN_KEY, new AttributeValue(key)));
			ddb.deleteItem(delItemRequest);
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
	}
	
//	private boolean existsRow (String key, String cf) {
//		if(StringUtils.isBlank(key)) return false;
//		return readRow(key, cf) != null;
//	}

	/********************************************
	 *				READ ALL FUNCTIONS
	********************************************/
	
	public Map<String, PObject> readAll(List<String> keys, boolean getAllAtrributes){
		if(keys == null || keys.isEmpty()) return new LinkedHashMap<String, PObject>();
		
		Map<String, PObject> results = new LinkedHashMap<String, PObject>();
		ArrayList<Map<String, AttributeValue>> keyz = new ArrayList<Map<String, AttributeValue>>();
		
		for (int i = 0; i < keys.size(); i++){
			String key = keys.get(i);
			results.put(key, null);
			keyz.add(Collections.singletonMap(CN_KEY, new AttributeValue(key)));
		}
		
		KeysAndAttributes kna = new KeysAndAttributes().withKeys(keyz);
		if(!getAllAtrributes) kna.setAttributesToGet(Arrays.asList(CN_KEY, CN_CLASSTYPE));
		
		batchGet(Collections.singletonMap(OBJECTS, kna), results);
		
		return results;
	}
		
	public List<PObject> readPage(String cf, String lastKey, int limit){
		List<PObject> results = new LinkedList<PObject>();
		if(StringUtils.isBlank(cf)) return results;
		
		try {
			ScanRequest scanRequest = new ScanRequest().withTableName(OBJECTS).withLimit(limit).
					withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL);
			
			if(!StringUtils.isBlank(lastKey)){
				scanRequest.setExclusiveStartKey(Collections.singletonMap(CN_KEY, new AttributeValue(lastKey)));
			}
			
			ScanResult result = ddb.scan(scanRequest);
			logger.log(Level.INFO, "readPage() CC: {0}", new Object[]{result.getConsumedCapacity()});
			
			for (Map<String, AttributeValue> item : result.getItems()) {
				PObject obj = fromRow(item);
				if(obj != null) {
					results.add(obj);
				}
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
		
		return results;
	}
	
	private void batchGet(Map<String, KeysAndAttributes> kna, Map<String, PObject> results){
		if(kna == null || kna.isEmpty() || results == null) return;
		try{
			BatchGetItemResult result = ddb.batchGetItem(new BatchGetItemRequest().
					withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL).withRequestItems(kna));
			if(result == null) return;
			
			List<Map<String, AttributeValue>> res = result.getResponses().get(OBJECTS);

			for (Map<String, AttributeValue> item : res) results.put(item.get(CN_KEY).getS(), fromRow(item));
			logger.log(Level.INFO, "batchGet() CC: {0}", new Object[]{result.getConsumedCapacity()});

			if(result.getUnprocessedKeys() != null && !result.getUnprocessedKeys().isEmpty()){
				batchGet(result.getUnprocessedKeys(), results);
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
	}
				
			
//	public List<Row<String, String, String>> readAll(CF<String> cf){
//		return readAll(null, cf);
//	}
	
//	public List<Row<String, String, String>> readAll(String key, CF<String> cf){
//		String query = "SELECT * FROM " + cf.getName();
//		if(!StringUtils.isBlank(key)){
//			query += " WHERE KEY = " + key;
//		}
//			
//		CqlQuery<String, String, String> cqlQuery = new CqlQuery<String, String, String>(
//				keyspace, strser, strser, strser);
//		cqlQuery.setQuery(query);
//
//		QueryResult<CqlRows<String, String, String>> result = cqlQuery.execute();
//		CqlRows<String, String, String> rows = result.get();
//		return rows == null ? new ArrayList<Row<String, String, String>> () : rows.getList();
//	}
	
	/********************************************
	 *				MISC FUNCTIONS
	********************************************/
	
	public static boolean voteUp(String userid, PObject votable){
		return vote(userid, votable, true, VOTE_LOCK_AFTER_SEC, VOTE_LOCKED_FOR_SEC);
	}
	
	public static boolean voteDown(String userid, PObject votable){
		return vote(userid, votable, false, VOTE_LOCK_AFTER_SEC, VOTE_LOCKED_FOR_SEC);
	}

	protected static boolean vote(String userid, PObject votable, boolean isUpvote,
			long voteLockAfterSec, int voteLockedForSec) {
		//no voting on your own stuff!
		if(votable == null || StringUtils.isBlank(userid) || userid.equals(votable.getCreatorid()) ||
				votable.getId() == null) return false;

		boolean voteSuccess = false;
		String key = userid.concat(Utils.SEPARATOR).concat(VOTE_TYPE).concat(votable.getId());
		String colName = CN_TIMESTAMP;
		
		//read vote for user & id
		Map<String, Object> vote = Search.getSource(key, VOTE_TYPE);

		if (vote != null && !vote.isEmpty()){
			long timestamp = (long) vote.get(CN_TIMESTAMP);
			long now = System.currentTimeMillis();
			boolean wasUpvote = (boolean) vote.get("up"); //up or down

			// check timestamp for recent correction,
			if((timestamp + (voteLockAfterSec * 1000)) > now && BooleanUtils.xor(new boolean[]{isUpvote, wasUpvote})) {
				// clear vote and restore votes to original count
				Search.unindex(key, VOTE_TYPE);
				voteSuccess = true;
			}
		}else{
			// save new vote & set expiration date to 1 month
			// users can vote again after vote lock period
			vote = new HashMap<String, Object>();
			vote.put(CN_TIMESTAMP, System.currentTimeMillis());
			vote.put("up", isUpvote);
			Search.index(key, vote, VOTE_TYPE, voteLockAfterSec);
			voteSuccess = true;
		}

		return voteSuccess;
	}
	
	private Map<String, AttributeValue> toRow(PObject so, Class<? extends Annotation> filter){
		if(so == null) return new HashMap<String, AttributeValue>();
		HashMap<String, Object> propsMap = Utils.getAnnotatedFields(so, Stored.class, filter);
		HashMap<String, AttributeValue> row = new HashMap<String, AttributeValue>();
		
		for (Entry<String, Object> entry : propsMap.entrySet()) {
			String field = entry.getKey();
			Object value = entry.getValue();

			if(value != null && !StringUtils.isBlank(value.toString())){
				row.put(field, new AttributeValue(value.toString()));
			}
		}
		return row;
	}

	private PObject fromRow(Map<String, AttributeValue> row) {
		if (row == null || row.isEmpty())	return null;

		PObject transObject = null;
		Map<String, String> props = new HashMap<String, String>();
		try {
			for (Entry<String, AttributeValue> col : row.entrySet()) {
				String name = col.getKey();
				AttributeValue value = col.getValue();
				props.put(name, value.getS());
			}
			Class<?> clazz = Utils.getClassname(props.get(CN_CLASSTYPE));
			if(clazz != null){
				transObject = (PObject) clazz.newInstance();
				BeanUtils.populate(transObject, props);
			}
		} catch (Exception ex) {
			logger.log(Level.SEVERE, null, ex);
		}

		return transObject;
	}
	
	public PObject getObject(String id, String classname){
		Class<? extends PObject> clazz = 
				(Class<? extends PObject>) getClassname(classname);
		PObject sobject = null;

		if(clazz != null){
			sobject = read(id);
		}
		return sobject;
	}
	
	/********************************************
	 *	    	USERS FUNCTIONS
	********************************************/
	
	public String createUser(User newUser){
		if(newUser == null || StringUtils.isBlank(newUser.getIdentifier())) return null;

		Map<String, String> authMap = new HashMap<String, String>();
		
		if (!StringUtils.isBlank(newUser.getPassword())) {
			if(newUser.getPassword().length() >= MIN_PASS_LENGTH){
				newHashAndSalt(newUser.getPassword(), authMap);
			}else{
				return null;
			}
		}
		
		// admin detected
		if (!Utils.ADMIN_IDENT.isEmpty() && Utils.ADMIN_IDENT.equals(newUser.getIdentifier()) ){
			newUser.setGroups(User.Groups.ADMINS.toString());
		}else{
			newUser.setGroups(User.Groups.USERS.toString());
		}
		
		newUser.setActive(true);	
		
		if(Utils.validateRequest(newUser).length == 0){
			create(newUser);
			authMap.put(CN_AUTHTOKEN,  Utils.generateAuthToken());
			authMap.put(CN_ID, newUser.getId());
			storeAuthMap(newUser.getIdentifier().trim(), authMap);
		}
		
		return newUser.getId();
	}
	
	public void updateUser(User user){
		update(user);
	}
	
	public void deleteUser(User user){
		if(user != null && user.getId() != null){
			String ident = user.getIdentifier();
			if(StringUtils.isBlank(ident)){
				ident = getColumn(user.getId(), OBJECTS, "identifier");
			}
			
			delete(user);
			deleteRow(ident, OBJECTS);
		}				
	}
	
	public User readUserForIdentifier (String identifier){
		if(StringUtils.isBlank(identifier)) return null;
		return (User) read(getColumn(identifier, OBJECTS, CN_ID));
    }
	
	public boolean passwordMatches(String password, String identifier){
		if(StringUtils.isBlank(password)) return false;
		String salt = getColumn(identifier, OBJECTS, CN_SALT);
		String storedHash = getColumn(identifier, OBJECTS, CN_PASSWORD);
		String givenHash = Utils.HMACSHA(password, salt);
		return StringUtils.equals(givenHash, storedHash);
	}
	
	public String generatePasswordResetToken(String identifier){
		if(StringUtils.isBlank(identifier)) return "";
		String salt = getColumn(identifier, OBJECTS, CN_SALT);
		String token = Utils.HMACSHA(Long.toString(System.currentTimeMillis()), salt);
		putColumn(identifier, OBJECTS, CN_TOKEN, token);	
		return token;
	}
	
	public boolean resetPassword(String identifier, String token, String newpass){
		if(StringUtils.isBlank(newpass) || StringUtils.isBlank(token)) return false;
		Map<String, String> authmap = loadAuthMap(identifier);
		if(!authmap.isEmpty()){
			String storedToken = authmap.get(CN_TOKEN);
			if(StringUtils.equals(storedToken, token)){
				authmap.remove(CN_TOKEN);
				newHashAndSalt(newpass, authmap);
				storeAuthMap(identifier, authmap);
				return true;
			}
		}
		return false;
	}
	
	private Map<String, String> newHashAndSalt(String pass, Map<String, String> authMap){
		if(authMap == null) authMap = new HashMap<String, String>();
		authMap.put(CN_SALT, RandomStringUtils.randomAlphanumeric(20));
		authMap.put(CN_PASSWORD, Utils.HMACSHA(pass, authMap.get(CN_SALT)));
		return authMap;
	}
	
	public Map<String, String> loadAuthMap(String identifier){
		Map<String,AttributeValue> row = readRow(identifier, OBJECTS);
		Map<String, String> map = new HashMap<String, String>();
		if(row == null) return map;
		for (Entry<String, AttributeValue> col : row.entrySet()) {
			map.put(col.getKey(), col.getValue().getS());
		}
		if (!map.containsKey(CN_AUTHTOKEN) && map.containsKey(CN_ID)) {
			map.put(CN_AUTHTOKEN, Utils.generateAuthToken());
			putColumn(identifier, OBJECTS, CN_AUTHTOKEN, map.get(CN_AUTHTOKEN));
		}
		return map;
	}
	
	public void storeAuthMap(String identifier, Map<String, String> authMap){
		if(StringUtils.isBlank(identifier) || authMap == null || authMap.isEmpty()) return;
		Map<String, AttributeValue> authmap = new HashMap<String, AttributeValue>();
		for (Entry<String, String> entry : authMap.entrySet()) {
			if(!StringUtils.isBlank(entry.getValue())){
				authmap.put(entry.getKey(), new AttributeValue(entry.getValue()));
			}
		}
		createRow(identifier, OBJECTS, authmap);
	}
	
	public void setAuthstamp(String identifier, Long authstamp){
		putColumn(identifier, OBJECTS, CN_AUTHSTAMP, Long.toString(authstamp));
	}
	
	public void changeIdentifier(String oldIdent, String newIdent){
		if(StringUtils.isBlank(oldIdent) || StringUtils.isBlank(newIdent) || oldIdent.equals(newIdent)) return;
		Map<String, AttributeValue> row = readRow(oldIdent, OBJECTS);
		if(row != null && !row.isEmpty()){
			createRow(newIdent, OBJECTS, row);
			deleteRow(oldIdent, OBJECTS);
		}
	}
	
	/********************************************
	 *	    	REST FUNCTIONS
	********************************************/
	
	public Response readREST(String id){
		PObject obj = read(id);
		if(obj != null){
			return Response.ok(obj).build();
		}else{
			return Utils.getJSONResponse(Response.Status.NOT_FOUND);
		}
	}
		
	public Response createREST(PObject content, UriInfo context){
		String[] errors = Utils.validateRequest(content);
		if (errors.length == 0) {
			String id = content.create();
			return Response.created(context.getAbsolutePathBuilder().path(id).
					build()).entity(content).build();
		}else{
			return Utils.getJSONResponse(Response.Status.BAD_REQUEST, errors);
		}
	}
	
	public Response updateREST(String id, PObject content){
		PObject c = read(id);
		if(c != null){
			HashMap<String, Object> propsMap = Utils.getAnnotatedFields(content, Stored.class, Locked.class);
			for (Map.Entry<String, Object> entry : propsMap.entrySet()) {
				try {
					if(entry.getValue() != null){
						if (entry.getKey().equals("email")) {
							String oldEmail = BeanUtils.getProperty(c, "email");
							String newEmail = entry.getValue().toString();
							// email special case							
							if(!newEmail.equals(oldEmail)){
								changeIdentifier(oldEmail, newEmail);
								BeanUtils.setProperty(c, entry.getKey(), entry.getValue());
							}
						} else {
							BeanUtils.setProperty(c, entry.getKey(), entry.getValue());
						}
					}
				} catch (Exception ex) {}
			}
			c.update();
			return Response.ok(c).build();
		}else{
			return Utils.getJSONResponse(Response.Status.NOT_FOUND);
		}
	}
	
	public Response deleteREST(String id, PObject content){
		if(content != null && content.getId() != null){
			content.delete();
			return Response.ok().build();
		}else{
			return Utils.getJSONResponse(Response.Status.BAD_REQUEST);
		}
	}
}
