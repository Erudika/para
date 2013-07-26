/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erudika.para.utils;
//
//import com.erudika.onemenu.core.OMObject;
//import com.erudika.onemenu.core.Votable;
//import java.util.*;
//import java.util.logging.Level;
//import java.util.logging.Logger;
//import me.prettyprint.cassandra.model.CqlQuery;
//import me.prettyprint.cassandra.model.CqlRows;
//import me.prettyprint.cassandra.serializers.SerializerTypeInferer;
//import me.prettyprint.cassandra.service.CassandraHostConfigurator;
//import me.prettyprint.cassandra.service.FailoverPolicy;
//import me.prettyprint.cassandra.service.OperationType;
//import static me.prettyprint.cassandra.service.OperationType.READ;
//import static me.prettyprint.cassandra.service.OperationType.WRITE;
//import me.prettyprint.hector.api.Cluster;
//import me.prettyprint.hector.api.ConsistencyLevelPolicy;
//import me.prettyprint.hector.api.HConsistencyLevel;
//import me.prettyprint.hector.api.Keyspace;
//import me.prettyprint.hector.api.Serializer;
//import me.prettyprint.hector.api.beans.HColumn;
//import me.prettyprint.hector.api.beans.Row;
//import me.prettyprint.hector.api.factory.HFactory;
//import me.prettyprint.hector.api.mutation.Mutator;
//import me.prettyprint.hector.api.query.*;
//import org.apache.commons.beanutils.BeanUtils;
//import org.apache.commons.lang3.BooleanUtils;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.commons.lang3.math.NumberUtils;
//
///**
// *
// * @author alexb
// */
public class CasDAO {
//		
//	private static final Logger logger = Logger.getLogger(DAO.class.getName());
//	private Serializer<String> strser = getSerializer(String.class);
//	private static final int MAX_ITEMS = Utils.MAX_ITEMS_PER_PAGE;
//	private static final long TIMER_OFFSET = 1310084584692L;
//	private Keyspace keyspace;
//	private Mutator<String> mutator;
//	private long voteLockAfter;
//	
//	//////////  DB TABLES  ////////////// 
//	public static final CF<String> OBJECTS = new CF<String>("Objects");
//	// misc
//	public static final CF<String> VOTES = new CF<String>("Votes");
//	public static final CF<String> AUTH_KEYS = new CF<String>("AuthKeys");
//	// language
//	public static final CF<String> LOCALES_TRANSLATIONS = new CF<String>("LocalesTranslations");
//	public static final CF<String> APPROVED_TRANSLATIONS = new CF<String>("ApprovedTranslations");
//	public static final CF<String> LANGUAGE = new CF<String>("Language");
//	
//	//////////  DB CONFIG  ////////////// 
//	public static final String CLUSTER = "scoold";
//	public static final String KEYSPACE = "onemenu";
//	public static final int CASSANDRA_PORT = 9160;
//	////////////////////////////////////
//	
//	public static final int MAX_PAGES = 10000;
//	public static final int MIN_PASS_LENGTH = 6;
//	public static final int VOTE_LOCKED_FOR_SEC = 4*7*24*60*60; //1 month in seconds
//	public static final long VOTE_LOCK_AFTER_SEC = 2*60; //2 minutes in sec
//	public static final int TOKEN_EXPIRES_AFTER_SEC = 20*60; //20 minutes in sec
//	public static final String CN_COUNTS_COUNT = "count";
//	public static final String CN_AUTHSTAMP = "authstamp";
//	public static final String CN_AUTHTOKEN = "authtoken";
//	public static final String CN_PASSWORD = "password";
//	public static final String CN_SALT = "salt";
//	public static final String CN_TOKEN = "token";
//	public static final String DEFAULT_COUNTER_KEY = "Counter1";
//	public static final String CN_ID = "id";
//	public static final String CN_TIMESTAMP = "timestamp";
//	public static final String CN_UPDATED = "updated";
//	public static final String CN_CLASSTYPE = "classtype";
//	public static final String SYSTEM_OBJECTS_KEY = "1";
//	public static final String SYSTEM_MESSAGE_KEY = "system-message";
//	public static final String SYSTEM_FXRATES_KEY = "system-fxrates";
//	
//	//////////  ID GEN VARS  ////////////// 
//	
//	public static final long workerIdBits = 5L;
//	public static final long dataCenterIdBits = 5L;
//	public static final long maxWorkerId = -1L ^ (-1L << workerIdBits);
//	public static final long maxDataCenterId = -1L ^ (-1L << dataCenterIdBits);
//	public static final long sequenceBits = 12L;
//	public static final long workerIdShift = sequenceBits;
//	public static final long dataCenterIdShift = sequenceBits + workerIdBits;
//	public static final long timestampLeftShift = sequenceBits + workerIdBits + dataCenterIdBits;
//	public static final long sequenceMask = -1L ^ (-1L << sequenceBits);
//	private long lastTimestamp = -1L;
//	private long dataCenterId = 0L;	// only one datacenter atm
//	private long workerId;
//	private long sequence = 0L;
//	
//	////////////////////////////////////
//
//	public static void main(String[] args){
////		HashMap<String, HashMap<String, String>> mmap = new HashMap<String, HashMap<String, String>>();
////		logger.log(Level.WARNING, "mmap {0}", new Object[]{mmap});
////		HashMap<String, String> mymap = new HashMap<String, String>();
////		mymap.put("sashko", "5");
////		mymap.put("yanna", "4");
////		logger.log(Level.WARNING, "mymap {0}", new Object[]{mymap});
////		
////		mmap.put("ONE", mymap);
////		logger.log(Level.WARNING, "mmap {0}", new Object[]{mmap});
////		
////		HashMap<String, String> mymap2 = mmap.get("ONE");
////		mymap2.remove("yanna");
////		
////		logger.log(Level.WARNING, "mmap {0}", new Object[]{mmap});
//	}
//	
//	
//	public CasDAO() {
//		this(CASSANDRA_PORT);
//	}
//	
//	public CasDAO(int port) {
//		CassandraHostConfigurator config = new CassandraHostConfigurator();
//		config.setHosts(System.getProperty("dbhosts","localhost:"+port));
//		config.setPort(port);
//		config.setRetryDownedHosts(true);
//		config.setRetryDownedHostsDelayInSeconds(60);
//		config.setAutoDiscoverHosts(false);
////		config.setAutoDiscoveryDelayInSeconds(60);
////		config.setMaxActive(100);
////		config.setMaxIdle(10);
//		Cluster cluster = HFactory.getOrCreateCluster(CLUSTER, config);
//		keyspace = HFactory.createKeyspace(KEYSPACE, cluster,
//			new ConsistencyLevelPolicy() {
//				public HConsistencyLevel get(OperationType arg0) { return getLevel(arg0); }
//				public HConsistencyLevel get(OperationType arg0, String arg1) { return getLevel(arg0); }
//				private HConsistencyLevel getLevel(OperationType arg0){
//					switch(arg0){
//						case READ: return HConsistencyLevel.ONE;
//						case WRITE: return HConsistencyLevel.QUORUM;
//						default: return HConsistencyLevel.ONE;
//					}
//				}
//			}, FailoverPolicy.ON_FAIL_TRY_ALL_AVAILABLE);		
//		mutator = createMutator();
//		voteLockAfter = convertMsTimeToCasTime(keyspace, VOTE_LOCK_AFTER_SEC*1000);
//		initIdGen();
//	}
//
//	public Keyspace getKeyspace(){
//		return keyspace;
//	}
//		
//	/********************************************
//	 *			CORE OBJECT CRUD FUNCTIONS
//	********************************************/
// 
//	public Long create(OMObject so){
//		Long id = create(so, mutator);
//		mutator.execute();
//		return id;
//	}
//
//	public Long create(OMObject so, Mutator<String> mut){
//		return create(null, so, mut);
//	}
//
//	public Long create(String key, OMObject so, Mutator<String> mut){
//		if(so == null) return null;
//		String[] errors = Utils.validateRequest(so);
//		if (errors.length != 0) return null;
//		if(so.getId() == null || so.getId().longValue() == 0L) so.setId(getNewId());
//		if(so.getTimestamp() == null) so.setTimestamp(Utils.timestamp());
//		String kee = StringUtils.isBlank(key) ? so.getId().toString() : key;
//		// store
//		storeBean(kee, so, true, mut);
//		logger.log(Level.INFO, "DAO.create() {0}", new Object[]{kee});
////		if(isIndexable(so)) index(so, so.getClasstype());
//		return so.getId();
//	}
//	
//	public <T extends OMObject> T read(String key) {
//		if(StringUtils.isBlank(key)) return null;
//		CF<String> cf = OBJECTS;
//		List<HColumn<String, String>> cols = readRow(key, cf);
//		T so = fromColumns(cols);
//		logger.log(Level.INFO, "DAO.read() {0}", new Object[]{so != null ? so.getId() : key + " -> null"});
//		return so != null ? so : null;
//	}
//	
//	public void update(OMObject so){
//		update(so, mutator);
//		mutator.execute();
//	}
//
//	public void update(OMObject so, Mutator<String> mut){
//		update(null, so, mut);
//	}
//
//	public void update(String key, OMObject so, Mutator<String> mut){
//		if(so == null) return;
//		String kee = (key == null) ? so.getId().toString() : key;
//		storeBean(kee, so, false, mut);		
//		
//		logger.log(Level.INFO, "DAO.update() {0}", new Object[]{so.getId()});
////		if(isIndexable(so)) index(so, so.getClasstype());
//	}
//
//	public void delete(OMObject so){
//		delete(so, mutator);
//		mutator.execute();
//	}
//
//	public void delete(OMObject so, Mutator<String> mut){
//		if(so == null) return ;
//		delete(so.getId().toString(), so.getClasstype(), mut);
//	}
//		
////	public void delete(String key, OMObject so, Mutator<String> mut){
////		delete(key, so.getClasstype(), mut);
////	}
//	
//	public <T extends OMObject> void delete(String key, String classtype, Mutator<String> mut){
//		if(StringUtils.isBlank(key)) return ;
//
//		CF<String> cf = OBJECTS;
//		deleteRow(key, cf, mut);
////		updateBeanCount(classtype, true, mut);
//		
//		logger.log(Level.INFO, "DAO.delete() {0}", new Object[]{key});
////		if(isIndexable(classtype)) unindex(key, classtype);
//	}
//
//	/********************************************
//	 *				COLUMN FUNCTIONS
//	********************************************/
//
//	public <N, V> void putColumn(String key, CF<N> cf, N colName, V colValue){
//		putColumn(key, cf, colName, colValue, 0);
//	}
//
//	public <N, V> void putColumn(String key, CF<N> cf, N colName, V colValue, int ttl){
//		if(StringUtils.isBlank(key) || cf == null || colName == null || colValue == null) 
//			return;
//
//		HColumn<N, String> col = HFactory.createColumn(colName, colValue.toString(),
//				getSerializer(colName), strser);
//
//		if(ttl > 0) col.setTtl(ttl);
//
//		mutator.insert(key, cf.getName(), col);
//	}
//
//	public <N> String getColumn(String key, CF<N> cf, N colName) {
//		if(StringUtils.isBlank(key) || cf == null || colName == null) return null;
//		HColumn<N, String> col = getHColumn(key, cf, colName);
//		return (col != null) ? col.getValue() : null;
//	}
//	
////	public Long getCounterColumn(String key){
////		if(StringUtils.isBlank(key)) return null;
////		HCounterColumn<String> col = getHCounterColumn(key);
////		return (col != null) ? col.getValue() : 0L;
////	}
//
//	public <N> void removeColumn(String key, CF<N> cf, N colName) {
//		if(StringUtils.isBlank(key) || cf == null) return;
//		
//		mutator.delete(key, cf.getName(), colName, getSerializer(colName));
//	}
//	
//	public <N> boolean existsColumn(String key, CF<N> cf, N columnName){
//		if(StringUtils.isBlank(key)) return false;
//		return getColumn(key, cf, columnName) != null;
//	}
//	
//	@SuppressWarnings({"rawtypes", "unchecked"})
//	public void batchPut(Column ... cols){
//		batchPut(Arrays.asList(cols));
//	}
//
//	@SuppressWarnings({"rawtypes", "unchecked"})
//	public void batchPut(List<Column> cols){
//		if(cols == null || cols.isEmpty()) return;
//		Mutator<String> mut = createMutator();
//		for (Column column : cols) {
//			addInsertion(column, mut);
//		}
//		mut.execute();
//	}
//
//	@SuppressWarnings({"rawtypes", "unchecked"})
//	public void batchRemove(Column ... cols){
//		batchRemove(Arrays.asList(cols));
//	}
//
//	@SuppressWarnings({"rawtypes", "unchecked"})
//	public void batchRemove(List<Column> cols){
//		if(cols == null || cols.isEmpty()) return;
//		Mutator<String> mut = createMutator();
//		for (Column column : cols) {
//			addDeletion(column, mut);
//		}
//		mut.execute();
//	}
//
//	public <N, V> void addInsertion(Column<N, V> col, Mutator<String> mut){
//		if(mut != null && col != null){
//			HColumn<N, String> hCol = HFactory.createColumn(col.getName(), 
//					col.getValue().toString(), getSerializer(col.getName()), strser);
//
//			if(col.getTtl() > 0) hCol.setTtl(col.getTtl());
//			mut.addInsertion(col.getKey(), col.getCf().getName(), hCol);
//		}
//	}
//
//	@SuppressWarnings({"rawtypes", "unchecked"})
//	public void addInsertions(List<Column> col, Mutator<String> mut){
//		for (Column column : col) {
//			addInsertion(column, mut);
//		}
//	}
//
//	public <N, V> void addDeletion(Column<N, V> col, Mutator<String> mut){
//		if(mut != null && col != null){			
//			mut.addDeletion(col.getKey(), col.getCf().getName(), col.getName(),
//					getSerializer(col.getName()));
//		}
//	}
//
//	@SuppressWarnings({"rawtypes", "unchecked"})
//	public void addDeletions(List<Column> col, Mutator<String> mut){
//		for (Column column : col) {
//			addDeletion(column, mut);
//		}
//	}
//
//	public <N> HColumn<N, String> getHColumn(String key, CF<N> cf, N colName){
//		if(cf == null) return null;
//		HColumn<N, String> col = null;
//		try {
//			col = HFactory.createColumnQuery(keyspace, strser,
//					getSerializer(colName), strser)
//				.setKey(key)
//				.setColumnFamily(cf.getName())
//				.setName(colName)
//				.execute().get();
//		} catch (Exception e) {
//			logger.log(Level.SEVERE, null, e);
//		}
//		return col;
//	}
//	
////	public HCounterColumn<String> getHCounterColumn(String key){
////		CounterQuery<String, String> cq = null;
////		try {
////			cq = HFactory.createCounterColumnQuery(keyspace, 
////					strser, strser);
////			cq.setColumnFamily(COUNTERS.getName());
////			cq.setKey(key);
////			cq.setName(CN_COUNTS_COUNT);
////		} catch (Exception e) {
////			logger.log(Level.SEVERE, null, e);
////		}
////		return (cq == null) ? null : cq.execute().get();
////	}
//
//	/********************************************
//	 *				ROW FUNCTIONS
//	********************************************/
//
//	public <N> String createRow(String key, CF<N> cf, List<HColumn<N, String>> row,
//			Mutator<String> mut){
//		if(row == null || row.isEmpty() || cf == null) return null;
//
//		for (HColumn<N, String> col : row){
//			mut.addInsertion(key, cf.getName(), col);
//		}
//		
//		return key;
//	}
//
//	public List<HColumn<String, String>> readRow(String key, CF<String> cf){
//		if(StringUtils.isBlank(key) || cf == null) return null;
//		List<HColumn<String, String>> row = null;
//		try {
//			SliceQuery<String, String, String> sq = HFactory.createSliceQuery(keyspace,
//					strser, strser, strser);
//			sq.setKey(key);
//			sq.setColumnFamily(cf.getName());
//			sq.setRange(null, null, true, Utils.DEFAULT_LIMIT);
//
//			row = sq.execute().get().getColumns();
//		} catch (Exception e) {
//			logger.log(Level.SEVERE, null, e);
//		}
//		return (row == null || row.isEmpty()) ? null : row;
//	}
//
//	public void deleteRow(String key, CF<?> cf, Mutator<String> mut){
//		if(StringUtils.isBlank(key) || cf == null) return;
//		mut.addDeletion(key, cf.getName(), null, strser);
//	}
//	
//	public <N> boolean existsRow (String key, CF<String> cf) {
//		if(StringUtils.isBlank(key)) return false;
//		return readRow(key, cf) != null;
//	}
//
//	/********************************************
//	 *				READ ALL FUNCTIONS
//	********************************************/
//
////	public <N, T extends OMObject> ArrayList<String> readAllKeys( String classtype, String keysKey, 
////			CF<N> keysCf, Class<N> colNameClass, N startKey, MutableLong page, MutableLong itemcount, 
////			int maxItems, boolean reverse, boolean colNamesAreKeys, boolean countOnlyColumns){
////		
////		ArrayList<String> keyz = new ArrayList<String>();
////		ArrayList<HColumn<N, String>> keys = new ArrayList<HColumn<N, String>>();
////		
////		if(StringUtils.isBlank(keysKey) || keysCf == null) return keyz;
////		
////		if(itemcount != null){
////			// count keys
////			if (countOnlyColumns) {
////				itemcount.setValue(countColumns(keysKey, keysCf, colNameClass));
////			} else {
////				itemcount.setValue(getBeanCount(classtype));
////			}
////		}
////		
////		try {
////		// get keys from linker table
////			SliceQuery<String, N, String> sq = HFactory.createSliceQuery(keyspace,
////					strser,	getSerializer(colNameClass), strser);
////
////			sq.setKey(keysKey);
////			sq.setColumnFamily(keysCf.getName());
////			sq.setRange(startKey, null, reverse, maxItems + 1);
////
////			keys.addAll(sq.execute().get().getColumns());
////
////			if(keys == null || keys.isEmpty()) return keyz;
////
////			if(page != null){
////				Long lastKey = 0L;
////				if (colNamesAreKeys) {
////					lastKey = (Long) keys.get(keys.size() - 1).getName();
////				}else{
////					lastKey =  NumberUtils.toLong(keys.get(keys.size() - 1).getValue());
////				}
////
////				page.setValue(lastKey);
////	//			Long startK = Arrays.equals(startKey, new byte[0]) ? 0L :
////	//				getLongFromBytes(startKey);
////
////				if(maxItems > 1 && keys.size() > maxItems){
////					keys.remove(keys.size() - 1); // remove last
////				}
////			}
////		} catch (Exception e) {
////			logger.log(Level.SEVERE, null, e);
////		}
////			
////		for (HColumn<N, String> col : keys){
////			if (colNamesAreKeys) {
////				keyz.add(col.getName().toString());
////			} else {
////				keyz.add(col.getValue());
////			}
////		}
////
////		return keyz;
////	}
//	
////	public <N, T extends OMObject> ArrayList<T> readAll(Class<T> clazz, String classtype,
////			String keysKey, CF<N> keysCf, Class<N> colNameClass,
////			N startKey, MutableLong page, MutableLong itemcount, int maxItems,
////			boolean reverse, boolean colNamesAreKeys, boolean countOnlyColumns){
////
////		if(StringUtils.isBlank(keysKey) || keysCf == null) return new ArrayList<T>();
////		if(StringUtils.isBlank(classtype)) classtype = clazz.getSimpleName().toLowerCase();
////		
////		ArrayList<String> keyz = readAllKeys(classtype, keysKey, keysCf, colNameClass, startKey, 
////				page, itemcount, maxItems, reverse, colNamesAreKeys, countOnlyColumns);		
////		
////		return readAll(keyz);
////	}
//	
//	public <T extends OMObject> ArrayList<T> readAll(List<String> keys){
//		if(keys == null || keys.isEmpty()) return new ArrayList<T>();
//		
//		CF<String> cf = OBJECTS;
//		ArrayList<T> list = new ArrayList<T>(keys.size());
//		Map<String, Integer> index = new HashMap<String, Integer>(keys.size());
//		for (int i = 0; i < keys.size(); i++) {
//			index.put(keys.get(i), i);
//			list.add(null);
//		}
//		
//		try{
//			MultigetSliceQuery<String, String, String> q =
//					HFactory.createMultigetSliceQuery(keyspace,
//					strser, strser, strser);
//									
//			q.setColumnFamily(cf.getName());
//			q.setKeys(keys);
//			q.setRange(null, null, false, Utils.DEFAULT_LIMIT);
//			
//			for (Row<String, String, String> row : q.execute().get()) {
//				T so = fromColumns(row.getColumnSlice().getColumns());
//				if (so != null){
//					list.set(index.get(row.getKey()), so);
//				}
//			}
//			list.remove(null);
//		} catch (Exception e) {
//			logger.log(Level.SEVERE, null, e);
//		}
//		return list;
//	}
//
//	public List<Row<String, String, String>> readAll(CF<String> cf){
//		return readAll(null, cf);
//	}
//	
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
//	
//	/****************************************************
//	 *		COUNT FUNCTIONS
//	*****************************************************/
//
//	
//	public <N> int countColumns(String key, CF<N> cf, Class<N> colNameClass){
//		if(StringUtils.isBlank(key) || cf == null || colNameClass == null) return 0;
//
//		int result = 0;
//		CountQuery<String, N> cq = HFactory.createCountQuery(keyspace,
//				strser, getSerializer(colNameClass));
//
//		cq.setKey(key);
//		cq.setColumnFamily(cf.getName());
//		cq.setRange(null, null, Utils.DEFAULT_LIMIT);
//
//		result = cq.execute().get();
//		return result;
//	}
//
//	public Long getBeanCount(String classtype){
////		return getCounterColumn(classtype);
//		return AppListener.getSearchClient().prepareCount(Utils.INDEX_NAME).
//				setTypes(classtype).execute().actionGet().count();
//	}
////
////	protected void updateBeanCount(String classtype, boolean decrement, Mutator<String> mut){
////		if (decrement) {
////			mutator.decrementCounter(classtype, COUNTERS.getName(), 
////					CN_COUNTS_COUNT, 1);
////		} else {
////			mutator.incrementCounter(classtype, COUNTERS.getName(), 
////					CN_COUNTS_COUNT, 1);
////		}
////	}
//	
//	/********************************************
//	 *				MISC FUNCTIONS
//	********************************************/
//	
//	public boolean voteUp(Long userid, Votable<Long> votable){
//		return vote(userid, votable, true, voteLockAfter, VOTE_LOCKED_FOR_SEC);
//	}
//	
//	public boolean voteDown(Long userid, Votable<Long> votable){
//		return vote(userid, votable, false, voteLockAfter, VOTE_LOCKED_FOR_SEC);
//	}
//
//	protected boolean vote(Long userid, Votable<Long> votable, boolean isUpvote,
//			long voteLockAfterMs, int voteLockedForSec) {
//		//no voting on your own stuff!
//		if(votable == null || userid == null || userid.equals(votable.getUserid()) ||
//				votable.getId() == null) return false;
//
//		boolean voteSuccess = false;
////		int upOrDown = updown;
//		CF<String> cf = VOTES;
//		String key = votable.getId().toString();
//		String colName = userid.toString();
//		
//		//read vote for user & id
//		HColumn<String, String> vote = getHColumn(key, cf, colName);
//
//		// if vote exists check timestamp for recent correction,
//		// otherwise insert new vote
//		Integer votes = (votable.getVotes() == null) ? Integer.valueOf(0) : votable.getVotes();
//		Integer newVotes = votes;
//
//		if (vote != null){
//			//allow correction of vote within 2 min
//			long timestamp = vote.getClock();
//			long now = keyspace.createClock();
//			boolean wasUpvote = BooleanUtils.toBoolean(vote.getValue()); //up or down
//
//			if((timestamp + voteLockAfterMs) > now &&
//					BooleanUtils.xor(new boolean[]{isUpvote, wasUpvote})) {
//				// clear vote and restore votes to original count
//				removeColumn(key, cf, colName);
//				newVotes = wasUpvote ? --votes : ++votes;
//				votable.setVotes(newVotes);
//				voteSuccess = true;
//			}
//		}else{
//			// save new vote & set expiration date to 1 month
//			// users can vote again after vote lock period
//			putColumn(key, cf, colName, Boolean.toString(isUpvote), voteLockedForSec);
//			newVotes = isUpvote ? ++votes : --votes;
//			votable.setVotes(newVotes);
//			voteSuccess = true;
//		}
//
//		return voteSuccess;
//	}
//
//	public void setSystemColumn(String colName, String colValue, int ttl) {
//		if(StringUtils.isBlank(colName)) return ;
//		if (StringUtils.isBlank(colValue)) {
//			removeColumn(SYSTEM_OBJECTS_KEY, OBJECTS, colName);
//		} else {
//			HColumn<String, String> col = HFactory.createColumn(colName, colValue);
//			if (ttl > 0 ) col.setTtl(ttl);
//			mutator.insert(SYSTEM_OBJECTS_KEY, OBJECTS.getName(), col);
//		}
//	}
//
//	public String getSystemColumn(String colName) {
//		if(StringUtils.isBlank(colName)) return null; 
//		return getColumn(SYSTEM_OBJECTS_KEY, OBJECTS, colName);
//	}
//	
//	public Map<String, String[]> getSystemColumns(){
//		Map<String, String[]> map = new HashMap<String, String[]>();
//		List<HColumn<String, String>> row = readRow(SYSTEM_OBJECTS_KEY, OBJECTS);
//		if(row != null){
//			for (HColumn<String, String> hColumn : row) {
//				map.put(hColumn.getName(), new String[]{hColumn.getValue(), 
//					Integer.toString(hColumn.getTtl())});
//			}
//		}
//		return map;
//	}
//	
//	protected static long convertMsTimeToCasTime(Keyspace ks, long ms){
//		long t1 = ks.createClock();
//		long t2 = System.currentTimeMillis();
//		long delta = Math.abs(t2 - t1);
//
//		if (delta < 100) {
//		  return ms;
//		} else if (delta >= 1000 ) {
//		  return ms * 1000;
//		} else if (delta <= 1000) {
//		  return ms / 1000;
//		}else{
//			return ms;
//		}
//	}
//
//	private void storeBean(String key, OMObject so, boolean creation, Mutator<String> mut){
//		if(so == null) return;
//		Class<Locked> locked = creation ? null : Locked.class;
//		HashMap<String, Object> propsMap = Utils.getAnnotatedFields(so, Stored.class, locked);
//		
////		if(!creation){
////			// read-only props
////			propsMap.remove(CN_ID);
////			propsMap.remove(CN_TIMESTAMP);
////			propsMap.remove(CN_CLASSTYPE);
////		}
//		
//		propsMap.put(CN_UPDATED, System.currentTimeMillis());
//		
//		for (Map.Entry<String, Object> entry : propsMap.entrySet()) {
//			String field = entry.getKey();
//			Object value = entry.getValue();
//
//			if(value != null){
//				HColumn<String, String> col = HFactory.createColumn(
//						field, value.toString(), strser, strser);
//				mut.addInsertion(key, OBJECTS.getName(), col);
//			}
//		}
//	}
//	
//	private void initIdGen(){
//		String workerID = System.getProperty("workerid");
//		workerId = NumberUtils.toLong(workerID, 1);
//				
//		if (workerId > maxWorkerId || workerId < 0) {
//			workerId = new Random().nextInt((int) maxWorkerId + 1);
//		}
//
////		if (dataCenterId > maxDataCenterId || dataCenterId < 0) {
////			dataCenterId =  new Random().nextInt((int) maxDataCenterId+1);
////		}
//	}
//	
//	public synchronized Long getNewId() {
//		// OLD simple version - only unique for this JVM
////		return HFactory.createClockResolution(ClockResolution.MICROSECONDS_SYNC).
////				createClock() - TIMER_OFFSET - 1000;
//		
//		// NEW version - unique across JVMs as long as each has a different workerID
//		// based on Twitter's Snowflake algorithm
//		long timestamp = System.currentTimeMillis();
//
//		if (lastTimestamp == timestamp) {
//			sequence = (sequence + 1) & sequenceMask;
//			if (sequence == 0) {
//				timestamp = tilNextMillis(lastTimestamp);
//			}
//		} else {
//			sequence = 0;
//		}
//
//		if (timestamp < lastTimestamp) {
//			throw new IllegalStateException(String.format("Clock moved backwards.  "
//					+ "Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
//		}
//
//		lastTimestamp = timestamp;
//		return	 ((timestamp - TIMER_OFFSET) << timestampLeftShift) | 
//								(dataCenterId << dataCenterIdShift) | 
//										(workerId << workerIdShift) | 
//														 (sequence) ;
//		
//	}
//	
//	private long tilNextMillis(long lastTimestamp) {
//		long timestamp = System.currentTimeMillis();
//
//		while (timestamp <= lastTimestamp) {
//			timestamp = System.currentTimeMillis();
//		}
//
//		return timestamp;
//	}
////
////	public void addNumbersortColumn(String key, CF<String> cf, Long id,
////			Number number, Number oldNumber, Mutator<String> mut){
////		if((oldNumber != null && oldNumber.equals(number)) || cf == null) return;
////		if(StringUtils.isBlank(key)) key = DEFAULT_KEY;
////
////		String compositeKey = number.toString().concat(
////				Utils.SEPARATOR).concat(id.toString());
////		
////		addInsertion(new Column<String, String>(key, cf, compositeKey, id.toString()), mut);
////
////		//finally clean up old column
////		if(oldNumber != null){
////			removeNumbersortColumn(key, cf, id, oldNumber, mut);
////		}
////	}
////
////	public void removeNumbersortColumn(String key, CF<String> cf, Long id, 
////			Number number, Mutator<String> mut){
////
////		if(cf == null || id == null || number == null) return;
////		if(StringUtils.isBlank(key)) key = DEFAULT_KEY;
////
////		String compositeKey = number.toString().concat(Utils.SEPARATOR)
////				.concat(id.toString());
////		addDeletion(new Column<String, String>(key, cf, compositeKey, null), mut);
////	}
//	
//	public final Mutator<String> createMutator(){
//		return HFactory.createMutator(keyspace, strser);
//	}
//	
//	private <T extends OMObject> T fromColumns(List<HColumn<String, String>> cols) {
//		if (cols == null || cols.isEmpty())	return null;
//
//		T transObject = null;
//		Map<String, String> props = new HashMap<String, String>();
//		try {
//			for (HColumn<String, String> col : cols) {
//				String name = col.getName();
//				String value = col.getValue();
//				props.put(name, value);
//			}
//			
//			Class<T> clazz = (Class<T>) Utils.getClassname(props.get(CN_CLASSTYPE));
//			if(clazz != null){
//				transObject = clazz.newInstance();
//
//				//set property WITH CONVERSION
//				BeanUtils.populate(transObject, props);
//			}
//		} catch (Exception ex) {
//			logger.log(Level.SEVERE, null, ex);
//		}
//
//		return transObject;
//	}
//
//	public <T> Serializer<T> getSerializer(Class<T> clazz) {
//		return SerializerTypeInferer.getSerializer(clazz);
//	}
//	
//	public <T> Serializer<T> getSerializer(T obj){
//		return (Serializer<T>) (obj == null ? strser : getSerializer(obj.getClass()));
//	}
////	
////	/********************************************
////	 *	    	USERS FUNCTIONS
////	********************************************/
////	
////	public Long createUser(User newUser){
////		if(newUser == null || StringUtils.isBlank(newUser.getIdentifier())) return null;
////		
////		if (Restaurant.class.equals(newUser.getClass())) {
////			newUser.setGroups(User.Groups.RESTAURANTS.toString());
////			// save password
////			String pass = ((Restaurant) newUser).getPassword();
////			if(StringUtils.isBlank(pass)) return null;
////			storePassword(pass, newUser.getIdentifier(), mutator);
////		} else {
////			newUser.setGroups(User.Groups.CLIENTS.toString());
////		}
////		// admin detected
////		if (AuthModule.ADMIN_IDENT.equals(newUser.getIdentifier())){
////			newUser.setGroups(User.Groups.ADMINS.toString());
////		}
////		
////		newUser.setActive(true);	
////		
////		if(Utils.validateRequest(newUser).length == 0){
////			create(newUser, mutator);
////			addInsertion(new Column<String, String>(newUser.getIdentifier().trim(), 
////					AUTH_KEYS, CN_ID, newUser.getId().toString()), mutator);
////			addInsertion(new Column<String, String>(newUser.getIdentifier().trim(), 
////					AUTH_KEYS, CN_AUTHTOKEN, Utils.generateAuthToken()), mutator);
////			mutator.execute();
////		}else{
////			mutator.discardPendingMutations();
////		}
////		
////		return newUser.getId();
////	}
////	
////	public void updateUser(User user){
////		if(user != null && user.getId() != null && !StringUtils.isBlank(user.getIdentifier())){
////			if(Restaurant.class.equals(user.getClass())){
////				String newEmail = user.getEmail();
////				String storedEmail = getColumn(user.getId().toString(), OBJECTS, "email");
////				//if mail changed update identifier column
////				if (!StringUtils.equals(newEmail, storedEmail)) {
////					List<HColumn<String, String>> row = readRow(user.getIdentifier(), AUTH_KEYS);
////					if(row != null){
////						// clone row
////						createRow(newEmail, AUTH_KEYS, row, mutator);
////						deleteRow(user.getIdentifier(), AUTH_KEYS, mutator);
////					}
////				}
////			}
////			update(user, mutator);
////			mutator.execute();
////		}
////	}
////	
////	public void deleteUser(User user){
////		if(user != null && user.getId() != null){
////			String ident = user.getIdentifier();
////			if(StringUtils.isBlank(ident)){
////				ident = getColumn(user.getId().toString(), OBJECTS, "identifier");
////			}
////			delete(user, mutator);
////			deleteRow(ident, AUTH_KEYS, mutator);
////			mutator.execute();
////		}				
////	}
////	
////	
////	public User readUserForIdentifier (String identifier){
////		if(StringUtils.isBlank(identifier)) return null;
////		return read(getColumn(identifier, AUTH_KEYS, CN_ID));
////    }
////
////	
////	public void storePassword(String password, String identifier, Mutator<String> mutator){
////		if(StringUtils.isBlank(password) || password.length() < MIN_PASS_LENGTH) return;
////		String salt = RandomStringUtils.randomAlphanumeric(20);
////		String hash = Utils.HMACSHA(password, salt);
////		addInsertion(new Column<String, String>(identifier, AUTH_KEYS, CN_PASSWORD, hash), mutator);		
////		addInsertion(new Column<String, String>(identifier, AUTH_KEYS, CN_SALT, salt), mutator);
////	}
////	
////	public boolean passwordMatches(String password, String identifier){
////		if(StringUtils.isBlank(password)) return false;
////		String salt = getColumn(identifier, AUTH_KEYS, CN_SALT);
////		String storedHash = getColumn(identifier, AUTH_KEYS, CN_PASSWORD);
////		String givenHash = Utils.HMACSHA(password, salt);
////		return StringUtils.equals(givenHash, storedHash);
////	}
////	
////	public String generatePasswordResetToken(String identifier){
////		if(StringUtils.isBlank(identifier)) return "";
////		String salt = getColumn(identifier, AUTH_KEYS, CN_SALT);
////		String token = Utils.HMACSHA(Long.toString(System.currentTimeMillis()), salt);
////		putColumn(identifier, AUTH_KEYS, CN_TOKEN, token, TOKEN_EXPIRES_AFTER_SEC);	
////		return token;
////	}
////	
////	public boolean verifyPasswordResetToken(String identifier, String token){
////		if(StringUtils.isBlank(identifier) || StringUtils.isBlank(token)) return false;
////		String storedToken = getColumn(identifier, AUTH_KEYS, CN_TOKEN);
////		return StringUtils.equals(storedToken, token);
////	}
////	
////	public void resetPassword(String newpass, String identifier){
////		if(StringUtils.isBlank(identifier) || StringUtils.isBlank(identifier)) return;
////		storePassword(newpass, identifier, mutator);
////		addDeletion(new Column<String, String>(identifier, AUTH_KEYS, CN_TOKEN, null), mutator);
////		mutator.execute();
////	}
////	
////	public Map<String, String> getAuthMap(String identifier){
////		 List<HColumn<String,String>> row = readRow(identifier, DAO.AUTH_KEYS);
////		 Map<String, String> map = new TreeMap<String, String>();
////		 for (HColumn<String, String> hColumn : row) {
////			 map.put(hColumn.getName(), hColumn.getValue());
////		}
////		if(!map.containsKey(CN_AUTHTOKEN)){
////			map.put(CN_AUTHTOKEN, Utils.generateAuthToken());
////			putColumn(identifier, AUTH_KEYS, CN_AUTHTOKEN, map.get(CN_AUTHTOKEN));
////		}
////		return map;
////	}
////	
////	public void setAuthstamp(String identifier, Long authstamp){
////		putColumn(identifier, DAO.AUTH_KEYS, DAO.CN_AUTHSTAMP, authstamp);
////	}
//	
//	/********************************************
//	 *	    	DAO UTIL CLASSES
//	********************************************/
//	
//	public static final class CF<T>{
//
//		public CF() {}
//		
//		public CF(String name) {
//			this.name = name;
//		}
//		
//		private String name;
//
//		public String getName() {
//			return name;
//		}
//
//		public void setName(String name) {
//			this.name = name;
//		}
//	}
//	
//	public static final class Column<N, V> {
//
//		public Column(String key, CF<N> cf, N name, V value, int ttl){
//			this.key = key;
//			this.cf = cf;
//			this.name = name;
//			this.value = value;
//			this.ttl = ttl;
//		}
//
//		public Column(String key, CF<N> cf, N name, V value){
//			this.key = key;
//			this.cf = cf;
//			this.name = name;
//			this.value = value;
//			this.ttl = 0;
//		}
//
//		public Column(String key, CF<N> cf) {
//			this.cf = cf;
//			this.key = key;
//			this.name = null;
//			this.value = null;
//			this.ttl = 0;
//		}
//
//		private CF<N> cf;
//		private String key;
//		private int ttl;
//		private V value;
//		private N name;
//
//		public CF<N> getCf() {
//			return cf;
//		}
//
//		public void setCf(CF<N> cf) {
//			this.cf = cf;
//		}
//
//		public String getKey() {
//			return key;
//		}
//
//		public void setKey(String key) {
//			this.key = key;
//		}
//
//		public int getTtl() {
//			return ttl;
//		}
//
//		public void setTtl(int ttl) {
//			this.ttl = ttl;
//		}
//
//		public V getValue() {
//			return value;
//		}
//
//		public void setValue(V value) {
//			this.value = value;
//		}
//
//		public N getName() {
//			return name;
//		}
//
//		public void setName(N name) {
//			this.name = name;
//		}
//	}

}

