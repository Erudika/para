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
package com.erudika.para.cache;

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

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public final class HazelcastUtils {
	
	private static HazelcastInstance hcInstance;

	private HazelcastUtils() {}
	
	public static HazelcastInstance getClient(){
		if(hcInstance != null) return hcInstance;
		
		com.hazelcast.config.Config cfg = new com.hazelcast.config.Config();
		MapConfig mapcfg = new MapConfig(Config.PARA);
		mapcfg.setEvictionPercentage(25);
		mapcfg.setEvictionPolicy(MapConfig.EvictionPolicy.LRU);
//			mapcfg.setMapStoreConfig(new MapStoreConfig().setEnabled(false).setClassName(NODE_NAME));
		mapcfg.setMaxSizeConfig(new MaxSizeConfig().setSize(25).setMaxSizePolicy(USED_HEAP_PERCENTAGE));
		cfg.addMapConfig(mapcfg);
		cfg.setProperty("hazelcast.jmx", "true");
		cfg.setProperty("hazelcast.logging.type", "slf4j");
		if(Config.IN_PRODUCTION){
			cfg.setNetworkConfig(new NetworkConfig().setJoin(new JoinConfig().
				setMulticastConfig(new MulticastConfig().setEnabled(false)).
					setTcpIpConfig(new TcpIpConfig().setEnabled(false)).
					setAwsConfig(new AwsConfig().setEnabled(true).
						setAccessKey(Config.AWS_ACCESSKEY).
						setSecretKey(Config.AWS_SECRETKEY).
						setRegion(Config.AWS_REGION).
						setSecurityGroupName(Config.APP_NAME_NS))));
		}

		hcInstance = Hazelcast.newHazelcastInstance(cfg);

		Para.addDestroyListener(new Para.DestroyListener() {
			public void onDestroy() {
				shutdownClient();
			}
		});
		return hcInstance;
	}
	
	public static void shutdownClient(){
		if(hcInstance != null) hcInstance.getLifecycleService().shutdown();
		hcInstance = null;
	}
	
}
