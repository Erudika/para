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
package com.erudika.para.impl;

//import com.erudika.para.api.DAO;

//import javax.enterprise.inject.Alternative;
//import com.erudika.onemenu.core.Votable;
//import com.erudika.para.annotations.Locked;
//import com.erudika.para.annotations.Stored;
//import com.erudika.para.api.ParaObject;
//import com.erudika.para.api.Search;
//import com.erudika.para.core.ParaObject;
//import com.erudika.para.utils.Utils;
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
//import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
//@Singleton
public class CassandraDAO {//implements DAO{
		
//	private static final Logger logger = Logger.getLogger(DAO.class.getName());
//	private Serializer<String> strser = getSerializer(String.class);
//	private Mutator<String> mutator;
//	//////////  DB CONFIG  ////////////// 
//	public static final String CLUSTER = Utils.ES_CLUSTER;
//	public static final String KEYSPACE = Utils.PRODUCT_NAME_NS;
//	public static final int CASSANDRA_PORT = 9160;
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
//	public CassandraDAO() {
//		CassandraHostConfigurator config = new CassandraHostConfigurator();
//		config.setHosts(System.getProperty("dbhosts","localhost:"+CASSANDRA_PORT));
//		config.setPort(CASSANDRA_PORT);
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
//	}
//		
//	/********************************************
//	 *			CORE OBJECT CRUD FUNCTIONS
//	********************************************/
// 
//	public <P extends ParaObject> String create(P so) {
//		if(so == null) return null;
//		if (!Utils.isValidObject(so)) return null;
//		if(StringUtils.isBlank(so.getId())) so.setId(Utils.getNewId());
//		if(so.getTimestamp() == null) so.setTimestamp(Utils.timestamp());
//
//		storeBean(so.getId(), so, true);
//
//		logger.log(Level.INFO, "DAO.create() {0}", new Object[]{so.getId()});
//		search.index(so, so.getClassname());
//		return so.getId();
//	}
//
//	public <P extends ParaObject> P read(String key) {
//		if(StringUtils.isBlank(key)) return null;
//		List<HColumn<String, String>> cols = readRow(key, OBJECTS);
//		T so = fromColumns(cols);
//		logger.log(Level.INFO, "DAO.read() {0}", new Object[]{so != null ? so.getId() : key + " -> null"});
//		return so != null ? so : null;
//	}
//	
//	public <P extends ParaObject> void update(P so) {
//		if(so == null) return;
//		storeBean(so.getId(), so, false);		
//		
//		logger.log(Level.INFO, "DAO.update() {0}", new Object[]{so.getId()});
//		search.index(so, so.getClassname());
//	}
//
//	public void delete(ParaObject so){
//		if(so == null) return ;
//
//		String cf = OBJECTS;
//		deleteRow(so.getId(), cf);
//		
//		logger.log(Level.INFO, "DAO.delete() {0}", new Object[]{so.getId()});
//		search.unindex(so, so.getClassname());
//	}
//
//	/********************************************
//	 *				COLUMN FUNCTIONS
//	********************************************/
//
//	public void putColumn(String key, String cf, String colName, String colValue) {
//		if(StringUtils.isBlank(key) || cf == null || colName == null || colValue == null) 
//			return;
//
//		HColumn<String, String> col = HFactory.createColumn(colName, colValue.toString(),
//				getSerializer(colName), strser);
//
////		if(ttl > 0) col.setTtl(ttl);
//
//		mutator.insert(key, cf, col);
//	}
//
//	public String getColumn(String key, String cf, String colName) {
//		if(StringUtils.isBlank(key) || cf == null || colName == null) return null;
//		HColumn<String, String> col = getHColumn(key, cf, colName);
//		return (col != null) ? col.getValue() : null;
//	}
//
//	public void removeColumn(String key, String cf, String colName) {
//		if(StringUtils.isBlank(key) || cf == null) return;
//		
//		mutator.delete(key, cf, colName, getSerializer(colName));
//	}
//	
//	public boolean existsColumn(String key, String cf, String columnName){
//		if(StringUtils.isBlank(key)) return false;
//		return getColumn(key, cf, columnName) != null;
//	}
//	
//	@SuppressWarnings({"rawtypes", "unchecked"})
//	private  void batchPut(Column ... cols){
//		batchPut(Arrays.asList(cols));
//	}
//
//	@SuppressWarnings({"rawtypes", "unchecked"})
//	private void batchPut(List<Column> cols){
//		if(cols == null || cols.isEmpty()) return;
//		Mutator<String> mut = createMutator();
//		for (Column column : cols) {
//			addInsertion(column, mut);
//		}
//		mut.execute();
//	}
//
//	@SuppressWarnings({"rawtypes", "unchecked"})
//	private void batchRemove(Column ... cols){
//		batchRemove(Arrays.asList(cols));
//	}
//
//	@SuppressWarnings({"rawtypes", "unchecked"})
//	private void batchRemove(List<Column> cols){
//		if(cols == null || cols.isEmpty()) return;
//		Mutator<String> mut = createMutator();
//		for (Column column : cols) {
//			addDeletion(column, mut);
//		}
//		mut.execute();
//	}
//
//	private <N, V> void addInsertion(Column<N, V> col, Mutator<String> mut){
//		if(mut != null && col != null){
//			HColumn<N, String> hCol = HFactory.createColumn(col.getName(), 
//					col.getValue().toString(), getSerializer(col.getName()), strser);
//
//			if(col.getTtl() > 0) hCol.setTtl(col.getTtl());
//			mut.addInsertion(col.getKey(), col.getCf(), hCol);
//		}
//	}
//
//	@SuppressWarnings({"rawtypes", "unchecked"})
//	private void addInsertions(List<Column> col, Mutator<String> mut){
//		for (Column column : col) {
//			addInsertion(column, mut);
//		}
//	}
//
//	private <N, V> void addDeletion(Column<N, V> col, Mutator<String> mut){
//		if(mut != null && col != null){			
//			mut.addDeletion(col.getKey(), col.getCf(), col.getName(),
//					getSerializer(col.getName()));
//		}
//	}
//
//	@SuppressWarnings({"rawtypes", "unchecked"})
//	private void addDeletions(List<Column> col, Mutator<String> mut){
//		for (Column column : col) {
//			addDeletion(column, mut);
//		}
//	}
//
//	private <N> HColumn<N, String> getHColumn(String key, String cf, N colName){
//		if(cf == null) return null;
//		HColumn<N, String> col = null;
//		try {
//			col = HFactory.createColumnQuery(keyspace, strser,
//					getSerializer(colName), strser)
//				.setKey(key)
//				.setColumnFamily(cf)
//				.setName(colName)
//				.execute().get();
//		} catch (Exception e) {
//			logger.log(Level.SEVERE, null, e);
//		}
//		return col;
//	}
//
//	/********************************************
//	 *				ROW FUNCTIONS
//	********************************************/
//
//	private <N> String createRow(String key, String cf, List<HColumn<N, String>> row,
//			Mutator<String> mut){
//		if(row == null || row.isEmpty() || cf == null) return null;
//
//		for (HColumn<N, String> col : row){
//			mut.addInsertion(key, cf, col);
//		}
//		
//		return key;
//	}
//
//	private List<HColumn<String, String>> readRow(String key, String cf){
//		if(StringUtils.isBlank(key) || cf == null) return null;
//		List<HColumn<String, String>> row = null;
//		try {
//			SliceQuery<String, String, String> sq = HFactory.createSliceQuery(keyspace,
//					strser, strser, strser);
//			sq.setKey(key);
//			sq.setColumnFamily(cf);
//			sq.setRange(null, null, true, Utils.DEFAULT_LIMIT);
//
//			row = sq.execute().get().getColumns();
//		} catch (Exception e) {
//			logger.log(Level.SEVERE, null, e);
//		}
//		return (row == null || row.isEmpty()) ? null : row;
//	}
//
//	private void deleteRow(String key, String cf){
//		if(StringUtils.isBlank(key) || cf == null) return;
//		mutator.addDeletion(key, cf, null, strser);
//		mutator.execute();
//	}
//	
//	public <N> boolean existsRow (String key, String cf) {
//		if(StringUtils.isBlank(key)) return false;
//		return readRow(key, cf) != null;
//	}
//
//	/********************************************
//	 *				READ ALL FUNCTIONS
//	********************************************/
//
//	public <T extends ParaObject> ArrayList<T> readAll(List<String> keys){
//		if(keys == null || keys.isEmpty()) return new ArrayList<T>();
//
//		ArrayList<T> list = new ArrayList<T>(keys.size());
//		Map<String, Integer> index = new HashMap<String, Integer>(keys.size());
//		for (int i = 0; i < keys.size(); i++) {
//			index.put(keys.get(i), i);
//			list.add(null);
//		}
//		
//		try{
//			MultigetSliceQuery<String, String, String> q = HFactory.createMultigetSliceQuery(keyspace,
//					strser, strser, strser);
//									
//			q.setColumnFamily(OBJECTS);
//			q.setKeys(keys);
//			q.setRange(null, null, false, Utils.DEFAULT_LIMIT);
//			
//			for (Row<String, String, String> row : q.execute().get()) {
//				ParaObject so = fromColumns(row.getColumnSlice().getColumns());
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
//	public List<Row<String, String, String>> readAll(String cf){
//		return readAll(null, cf);
//	}
//	
//	public List<Row<String, String, String>> readAll(String key, String cf){
//		String query = "SELECT * FROM " + cf;
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
//	/********************************************
//	 *				MISC FUNCTIONS
//	********************************************/
//	
//	private static long convertMsTimeToCasTime(Keyspace ks, long ms){
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
//	private void storeBean(String key, ParaObject so, boolean creation){
//		if(so == null) return;
//		Class<Locked> locked = creation ? null : Locked.class;
//		HashMap<String, Object> propsMap = Utils.getAnnotatedFields(so, Stored.class, locked);
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
//				mutator.addInsertion(key, OBJECTS, col);
//			}
//		}
//		mutator.execute();
//	}
//	
//	private Mutator<String> createMutator(){
//		return HFactory.createMutator(keyspace, strser);
//	}
//	
//	private <T extends ParaObject> T fromColumns(List<HColumn<String, String>> cols) {
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
//			Class<T> clazz = (Class<T>) Utils.toClass(props.get(DAO.CN_CLASSNAME));
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
//	private <T> Serializer<T> getSerializer(Class<T> clazz) {
//		return SerializerTypeInferer.getSerializer(clazz);
//	}
//	
//	private <T> Serializer<T> getSerializer(T obj){
//		return (Serializer<T>) (obj == null ? strser : getSerializer(obj.getClass()));
//	}
//
//	///////////////////
//
//	@Override
//	public <P extends ParaObject> void createAll(List<P> objects) {
//		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//		// TODO
//	}
//
//	@Override
//	public <P extends ParaObject> Map<String, P> readAll(List<String> keys, boolean getAllAtrributes) {
//		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//		// TODO
//	}
//
//	@Override
//	public <P extends ParaObject> List<P> readPage(String cf, String lastKey) {
//		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//		// TODO
//	}
//
//	@Override
//	public <P extends ParaObject> void updateAll(List<P> objects) {
//		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//		// TODO
//	}
//
//	@Override
//	public <P extends ParaObject> void deleteAll(List<P> objects) {
//		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//		// TODO
//	}

}

