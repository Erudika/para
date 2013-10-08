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

import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.erudika.para.api.MemoryGrid;
import com.erudika.para.utils.Utils;
import com.hazelcast.config.AwsConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MapStoreConfig;
import com.hazelcast.config.MaxSizeConfig;
import static com.hazelcast.config.MaxSizeConfig.MaxSizePolicy.USED_HEAP_PERCENTAGE;
import com.hazelcast.config.MulticastConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@Singleton
public class HazelcastMemoryGrid implements MemoryGrid{

	private static final String NODE_NAME = Utils.CLUSTER_NAME + Utils.WORKER_ID;
	private static final String MAP_NAME = Utils.CLUSTER_NAME;
	
	private HazelcastInstance haze;

	public HazelcastMemoryGrid() {
		haze = Hazelcast.getHazelcastInstanceByName(NODE_NAME);
		if(haze == null){
			Config cfg = new Config();
			MapConfig mapcfg = new MapConfig(MAP_NAME);
			mapcfg.setEvictionPercentage(25);
			mapcfg.setEvictionPolicy(MapConfig.EvictionPolicy.LRU);
			mapcfg.setMapStoreConfig(new MapStoreConfig().setEnabled(false));
			mapcfg.setMaxSizeConfig(new MaxSizeConfig().setSize(25).setMaxSizePolicy(USED_HEAP_PERCENTAGE));
			cfg.addMapConfig(mapcfg);
			cfg.setInstanceName(NODE_NAME);
			cfg.setProperty("hazelcast.jmx", "true");
			if(Utils.IN_PRODUCTION){
				cfg.setNetworkConfig(new NetworkConfig().setJoin(new JoinConfig().
					setMulticastConfig(new MulticastConfig().setEnabled(false)).
						setTcpIpConfig(new TcpIpConfig().setEnabled(false)).
						setAwsConfig(new AwsConfig().setEnabled(true).
							setAccessKey(getAwsCredentials()[0]).
							setSecretKey(getAwsCredentials()[1]).
							setRegion(Utils.AWS_REGION).
							setSecurityGroupName(Utils.CLUSTER_NAME))));
			}
			
			haze = Hazelcast.newHazelcastInstance(cfg);
		}		
	}
	
	private String[] getAwsCredentials(){
		if(!StringUtils.isBlank(Utils.AWS_ACCESSKEY) && !StringUtils.isBlank(Utils.AWS_SECRETKEY)){
			return new String[]{Utils.AWS_ACCESSKEY, Utils.AWS_SECRETKEY};
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
		return haze.getMap(MAP_NAME).containsKey(id);
	}
	
	@Override
	public <T> void put(String id, T object) {
		haze.getMap(MAP_NAME).put(id, object);
	}

	@Override
	public <T> void put(String id, T object, Long ttl_seconds) {
		haze.getMap(MAP_NAME).put(id, object, ttl_seconds, TimeUnit.SECONDS);
	}

	@Override
	public <T> T get(String id) {
		return (T) haze.getMap(MAP_NAME).get(id);
	}

	@Override
	public void remove(String id) {
		haze.getMap(MAP_NAME).remove(id);
	}

	@Override
	public void removeAll() {
		haze.getMap(MAP_NAME).clear();
	}
	
}
