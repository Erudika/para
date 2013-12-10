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
package com.erudika.para.cache;

import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.erudika.para.Para;
import com.erudika.para.utils.Config;
import com.hazelcast.config.AwsConfig;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizeConfig;
import static com.hazelcast.config.MaxSizeConfig.MaxSizePolicy.USED_HEAP_PERCENTAGE;
import com.hazelcast.config.MulticastConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@Singleton
public class HazelcastCache implements Cache {

	private static final String NODE_NAME = Config.CLUSTER_NAME + Config.WORKER_ID;
	private static final String MAP_NAME = Config.CLUSTER_NAME;
	
	private HazelcastInstance haze;

	public HazelcastCache() {
		haze = Hazelcast.getHazelcastInstanceByName(NODE_NAME);
		if(haze == null){
			com.hazelcast.config.Config cfg = new com.hazelcast.config.Config();
			MapConfig mapcfg = new MapConfig(MAP_NAME);
			mapcfg.setEvictionPercentage(25);
			mapcfg.setEvictionPolicy(MapConfig.EvictionPolicy.LRU);
//			mapcfg.setMapStoreConfig(new MapStoreConfig().setEnabled(false).setClassName(NODE_NAME));
			mapcfg.setMaxSizeConfig(new MaxSizeConfig().setSize(25).setMaxSizePolicy(USED_HEAP_PERCENTAGE));
			cfg.addMapConfig(mapcfg);
			cfg.setInstanceName(NODE_NAME);
			cfg.setProperty("hazelcast.jmx", "true");
			cfg.setProperty("hazelcast.logging.type", "slf4j");
			if(Config.IN_PRODUCTION){
				cfg.setNetworkConfig(new NetworkConfig().setJoin(new JoinConfig().
					setMulticastConfig(new MulticastConfig().setEnabled(false)).
						setTcpIpConfig(new TcpIpConfig().setEnabled(false)).
						setAwsConfig(new AwsConfig().setEnabled(true).
							setAccessKey(getAwsCredentials()[0]).
							setSecretKey(getAwsCredentials()[1]).
							setRegion(Config.AWS_REGION).
							setSecurityGroupName(Config.CLUSTER_NAME))));
			}
			
			haze = Hazelcast.newHazelcastInstance(cfg);
			
			Para.addDestroyListener(new Para.DestroyListener() {
				public void onDestroy() {
					haze.getLifecycleService().shutdown();
				}
			});
		}
	}
	
	private String[] getAwsCredentials(){
		if(!StringUtils.isBlank(Config.AWS_ACCESSKEY) && !StringUtils.isBlank(Config.AWS_SECRETKEY)){
			return new String[]{Config.AWS_ACCESSKEY, Config.AWS_SECRETKEY};
		}else{
			InstanceProfileCredentialsProvider ipcp = new InstanceProfileCredentialsProvider();
			try {
				return new String[]{ipcp.getCredentials().getAWSAccessKeyId(), ipcp.getCredentials().getAWSSecretKey()};
			} catch (Exception e) {
				return new String[]{"", ""};
			}
		}
	}

	@Override
	public boolean contains(String id) {
		if(id == null) return false;
		return haze.getMap(MAP_NAME).containsKey(id);
	}
	
	@Override
	public <T> void put(String id, T object) {
		if(id == null || object == null) return;
		haze.getMap(MAP_NAME).putAsync(id, object);
	}

	@Override
	public <T> void put(String id, T object, Long ttl_seconds) {
		if(id == null || object == null) return;
		haze.getMap(MAP_NAME).putAsync(id, object, ttl_seconds, TimeUnit.SECONDS);
	}

	@Override
	public <T> void putAll(Map<String, T> objects) {
		if(objects == null || objects.isEmpty()) return;
		haze.getMap(MAP_NAME).putAll(objects);
	}

	@Override
	public <T> T get(String id) {
		if(id == null) return null;
		return (T) haze.getMap(MAP_NAME).get(id);
	}

	@Override
	public <T> Map<String, T> getAll(List<String> ids) {
		Map<String, T> map = new TreeMap<String, T>();
		if(ids == null) return map;
		for (Entry<Object, Object> entry : haze.getMap(MAP_NAME).getAll(new TreeSet<Object>(ids)).entrySet()) {
			map.put((String) entry.getKey(), (T) entry.getValue());
		}
		return map;
	}
	
	@Override
	public void remove(String id) {
		if(id == null) return;
		haze.getMap(MAP_NAME).delete(id);
	}

	@Override
	public void removeAll() {
		haze.getMap(MAP_NAME).clear();
	}
	
	@Override
	public void removeAll(List<String> ids) {
		if(ids == null) return;
		IMap<?,?> map = haze.getMap(MAP_NAME);
		for (String id : ids) {
			map.delete(id);
		}
	}
	
}
