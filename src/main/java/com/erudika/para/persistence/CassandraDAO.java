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

//import com.erudika.para.api.DAO;

import java.util.List;
import java.util.Map;
import javax.inject.Named;
import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import static com.erudika.para.persistence.DAO.OBJECTS;
import com.erudika.para.utils.Utils;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Singleton;
import me.prettyprint.cassandra.model.CqlQuery;
import me.prettyprint.cassandra.model.CqlRows;
import me.prettyprint.cassandra.serializers.SerializerTypeInferer;
import me.prettyprint.cassandra.service.CassandraHostConfigurator;
import me.prettyprint.cassandra.service.FailoverPolicy;
import me.prettyprint.cassandra.service.OperationType;
import static me.prettyprint.cassandra.service.OperationType.READ;
import static me.prettyprint.cassandra.service.OperationType.WRITE;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.ConsistencyLevelPolicy;
import me.prettyprint.hector.api.HConsistencyLevel;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.Serializer;
import me.prettyprint.hector.api.beans.HColumn;
import me.prettyprint.hector.api.beans.Row;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.mutation.Mutator;
import me.prettyprint.hector.api.query.*;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@Singleton
@Named("cassandra")
public class CassandraDAO implements DAO{
		
	private static final Logger logger = Logger.getLogger(DAO.class.getName());
	private Serializer<String> strser = getSerializer(String.class);
	private Mutator<String> mutator;
	private Keyspace keyspace;
	//////////  DB CONFIG  ////////////// 
	public static final String CLUSTER = Utils.CLUSTER_NAME;
	public static final String KEYSPACE = Utils.PRODUCT_NAME_NS;
	public static final int CASSANDRA_PORT = 9160;
	////////////////////////////////////
		
	public CassandraDAO() {
		CassandraHostConfigurator config = new CassandraHostConfigurator();
		config.setHosts(System.getProperty("dbhosts","localhost:"+CASSANDRA_PORT));
		config.setPort(CASSANDRA_PORT);
		config.setRetryDownedHosts(true);
		config.setRetryDownedHostsDelayInSeconds(60);
		config.setAutoDiscoverHosts(false);
//		config.setAutoDiscoveryDelayInSeconds(60);
//		config.setMaxActive(100);
//		config.setMaxIdle(10);
		Cluster cluster = HFactory.getOrCreateCluster(CLUSTER, config);
		keyspace = HFactory.createKeyspace(KEYSPACE, cluster,
			new ConsistencyLevelPolicy() {
				public HConsistencyLevel get(OperationType arg0) { return getLevel(arg0); }
				public HConsistencyLevel get(OperationType arg0, String arg1) { return getLevel(arg0); }
				private HConsistencyLevel getLevel(OperationType arg0){
					switch(arg0){
						case READ: return HConsistencyLevel.ONE;
						case WRITE: return HConsistencyLevel.QUORUM;
						default: return HConsistencyLevel.ONE;
					}
				}
			}, FailoverPolicy.ON_FAIL_TRY_ALL_AVAILABLE);		
		mutator = createMutator();
	}
		
	/********************************************
	 *			CORE OBJECT CRUD FUNCTIONS
	********************************************/
 
	@Override
	public <P extends ParaObject> String create(P so) {
		if(so == null) return null;
		if (!Utils.isValidObject(so)) return null;
		if(StringUtils.isBlank(so.getId())) so.setId(Utils.getNewId());
		if(so.getTimestamp() == null) so.setTimestamp(Utils.timestamp());

		createUpdateRow(so.getId(), OBJECTS, toRow(so, null));

		logger.log(Level.INFO, "DAO.create() {0}", new Object[]{so.getId()});
		return so.getId();
	}

	@Override
	public <P extends ParaObject> P read(String key) {
		if(StringUtils.isBlank(key)) return null;
		
		P so = fromRow(readRow(key, OBJECTS));
		
		logger.log(Level.INFO, "DAO.read() {0}", new Object[]{so != null ? so.getId() : key + " -> null"});
		return so != null ? so : null;
	}
	
	@Override
	public <P extends ParaObject> void update(P so) {
		if(so == null || so.getId() == null) return;
		so.setUpdated(System.currentTimeMillis());
		
		createUpdateRow(so.getId(), OBJECTS, toRow(so, Locked.class));
		
		logger.log(Level.INFO, "DAO.update() {0}", new Object[]{so.getId()});
	}

	@Override
	public void delete(ParaObject so){
		if(so == null) return ;
		deleteRow(so.getId(), OBJECTS);
		
		logger.log(Level.INFO, "DAO.delete() {0}", new Object[]{so.getId()});
	}

	/********************************************
	 *				COLUMN FUNCTIONS
	********************************************/

	@Override
	public void putColumn(String key, String cf, String colName, String colValue) {
		if(StringUtils.isBlank(key) || cf == null || colName == null || colValue == null) return;
		HColumn<String, String> col = HFactory.createColumn(colName, 
				colValue.toString(), getSerializer(colName), strser);
//		if(ttl > 0) col.setTtl(ttl);
		mutator.insert(key, cf, col);
	}

	@Override
	public String getColumn(String key, String cf, String colName) {
		if(StringUtils.isBlank(key) || cf == null || colName == null) return null;
		HColumn<String, String> col = getHColumn(key, cf, colName);
		return (col != null) ? col.getValue() : null;
	}

	@Override
	public void removeColumn(String key, String cf, String colName) {
		if(StringUtils.isBlank(key) || cf == null) return;
		mutator.delete(key, cf, colName, getSerializer(colName));
	}
	
	@Override
	public boolean existsColumn(String key, String cf, String columnName){
		if(StringUtils.isBlank(key)) return false;
		return getColumn(key, cf, columnName) != null;
	}
	
	private <N> HColumn<N, String> getHColumn(String key, String cf, N colName){
		if(cf == null) return null;
		HColumn<N, String> col = null;
		try {
			col = HFactory.createColumnQuery(keyspace, strser,
					getSerializer(colName), strser)
				.setKey(key)
				.setColumnFamily(cf)
				.setName(colName)
				.execute().get();
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
		return col;
	}

	/********************************************
	 *				ROW FUNCTIONS
	********************************************/

	private <N> String createUpdateRow(String key, String cf, List<HColumn<N, String>> row){
		if(StringUtils.isBlank(key) || StringUtils.isBlank(cf) || row == null || row.isEmpty()) return null;
		try {
			for (HColumn<N, String> col : row){
				mutator.addInsertion(key, cf, col);
			}
			mutator.execute();
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
		return key;
	}

	private List<HColumn<String, String>> readRow(String key, String cf){
		if(StringUtils.isBlank(key) || cf == null) return null;
		List<HColumn<String, String>> row = null;
		try {
			SliceQuery<String, String, String> sq = HFactory.createSliceQuery(keyspace,
					strser, strser, strser);
			sq.setKey(key);
			sq.setColumnFamily(cf);
			sq.setRange(null, null, true, Utils.DEFAULT_LIMIT);

			row = sq.execute().get().getColumns();
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
		return (row == null || row.isEmpty()) ? null : row;
	}

	private void deleteRow(String key, String cf){
		if(StringUtils.isBlank(key) || cf == null) return;
		try {
			mutator.delete(key, cf, null, strser);
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
	}
	
	public <N> boolean existsRow (String key, String cf) {
		if(StringUtils.isBlank(key)) return false;
		return readRow(key, cf) != null;
	}

	/********************************************
	 *				READ ALL FUNCTIONS
	********************************************/

	@Override
	public <P extends ParaObject> void createAll(List<P> objects){
		if(objects == null || objects.isEmpty()) return;
		try{
			for (P obj : objects) {
				for (HColumn<String, String> col : toRow(obj, null)) {
					mutator.addInsertion(obj.getId(), OBJECTS, col);
				}
			}
			mutator.execute();
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}		
	}
	
	@Override
	public <P extends ParaObject> void updateAll(List<P> objects){
		createAll(objects);
	}
	
	@Override
	public <T extends ParaObject> Map<String, T> readAll(List<String> keys, boolean getAllAttributes){
		if(keys == null || keys.isEmpty()) return new LinkedHashMap<String, T>();

		Map<String, T> list = new LinkedHashMap<String, T>();
		for (String key : keys) {
			list.put(key, null);
		}
		
		try{
			MultigetSliceQuery<String, String, String> q = HFactory.createMultigetSliceQuery(keyspace,
					strser, strser, strser);
									
			q.setColumnFamily(OBJECTS);
			q.setKeys(keys);
			q.setRange(null, null, false, Utils.DEFAULT_LIMIT);
			
			for (Row<String, String, String> row : q.execute().get()) {
				list.put(row.getKey(), (T) fromRow(row.getColumnSlice().getColumns()));
			}
			list.remove(null);
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
		return list;
	}

	@Override
	public <P extends ParaObject> List<P> readPage(String cf, String lastKey){
		List<P> results = new LinkedList<P>();
		if(StringUtils.isBlank(cf)) return results;
		
		String where = StringUtils.isBlank(lastKey) ? "" : "WHERE KEY > "+lastKey;
		
		try{
			String query = "SELECT * FROM " + cf + where + " LIMIT " + Utils.MAX_ITEMS_PER_PAGE ;
			
			CqlQuery<String, String, String> cqlQuery = new CqlQuery<String, String, String>(
					keyspace, strser, strser, strser);
			cqlQuery.setQuery(query);

			QueryResult<CqlRows<String, String, String>> result = cqlQuery.execute();
			CqlRows<String, String, String> rows = result.get();
			
			for (Row<String, String, String> row : rows.getList()) {
				results.add((P) fromRow(row.getColumnSlice().getColumns()));
			}
			
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
		return results;
	}
	
	@Override
	public <P extends ParaObject> void deleteAll(List<P> objects){
		if(objects == null || objects.isEmpty()) return;
		try{
			for (ParaObject object : objects) {
				if(object != null){
					mutator.addDeletion(object.getId(), OBJECTS);
				}
			}
			mutator.execute();
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}
	}
	
	/********************************************
	 *				MISC FUNCTIONS
	********************************************/

	private Mutator<String> createMutator(){
		return HFactory.createMutator(keyspace, strser);
	}
	
	private List<HColumn<String, String>> toRow(ParaObject so, Class<? extends Annotation> filter){
		if(so == null) return null;
		HashMap<String, Object> propsMap = Utils.getAnnotatedFields(so, Stored.class, filter);
		if (so instanceof Sysprop) propsMap.putAll(((Sysprop) so).getProperties());
		List<HColumn<String, String>> cols = new ArrayList<HColumn<String, String>>();
		
		for (Map.Entry<String, Object> entry : propsMap.entrySet()) {
			String field = entry.getKey();
			Object value = entry.getValue();

			if(value != null){
				cols.add(HFactory.createColumn(field, value.toString(), strser, strser));
			}
		}
		
		return cols;
	}
	
	private <T extends ParaObject> T fromRow(List<HColumn<String, String>> cols) {
		if (cols == null || cols.isEmpty())	return null;

		T transObject = null;
		Map<String, String> props = new HashMap<String, String>();
		Map<String, Object> sysprops = new HashMap<String, Object>();
		
		try {
			for (HColumn<String, String> col : cols) {
				String name = col.getName();
				String value = col.getValue();
				
				if(Sysprop.isSysprop(name)){
					sysprops.put(name, value);
				}else{
					props.put(name, value);
				}
			}
			
			Class<?> clazz = Utils.toClass(props.get(DAO.CN_CLASSNAME));
			if(clazz != null){
				transObject = (T) clazz.newInstance();
				BeanUtils.populate(transObject, props);
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

	private <T> Serializer<T> getSerializer(Class<T> clazz) {
		return SerializerTypeInferer.getSerializer(clazz);
	}
	
	private <T> Serializer<T> getSerializer(T obj){
		return (Serializer<T>) (obj == null ? strser : getSerializer(obj.getClass()));
	}

}

