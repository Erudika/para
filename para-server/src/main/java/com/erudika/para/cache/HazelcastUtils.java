/*
 * Copyright 2013-2016 Erudika. https://erudika.com
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
 * For issues and patches go to: https://github.com/erudika
 */
package com.erudika.para.cache;

import com.erudika.para.DestroyListener;
import com.erudika.para.Para;
import com.erudika.para.utils.Config;
import com.hazelcast.config.AwsConfig;
import com.hazelcast.config.EvictionPolicy;
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
 * Helper functions for {@link HazelcastCache}.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 * @see HazelcastCache
 */
public final class HazelcastUtils {

	private static HazelcastInstance hcInstance;

	private HazelcastUtils() { }

	/**
	 * Initializes a new Hazelcast instance with default settings.
	 * @return a Hazelcast instance
	 */
	public static HazelcastInstance getClient() {
		if (hcInstance == null) {
			hcInstance = Hazelcast.getHazelcastInstanceByName(getNodeName());
			if (hcInstance != null) {
				return hcInstance;
			}
			com.hazelcast.config.Config cfg = new com.hazelcast.config.Config();
			cfg.setInstanceName(getNodeName());
			cfg.getGroupConfig().setName(Config.CLUSTER_NAME);
			MapConfig mapcfg = new MapConfig("default");
			mapcfg.setEvictionPolicy(getEvictionPolicy());
			mapcfg.setTimeToLiveSeconds(Config.getConfigInt("hc.ttl_seconds", 3600));
	//			mapcfg.setMapStoreConfig(new MapStoreConfig().setEnabled(false).setClassName(NODE_NAME));
			mapcfg.setMaxSizeConfig(getMaxSize());
			cfg.addMapConfig(mapcfg);
			cfg.setProperty("hazelcast.jmx", Boolean.toString(isJMXOn()));
			cfg.setProperty("hazelcast.logging.type", "slf4j");
			cfg.setProperty("hazelcast.health.monitoring.level", "OFF");
			if (Config.IN_PRODUCTION && Config.getConfigBoolean("hc.ec2_discovery_enabled", true)) {
				cfg.setNetworkConfig(new NetworkConfig().setJoin(new JoinConfig().
					setMulticastConfig(new MulticastConfig().setEnabled(false)).
						setTcpIpConfig(new TcpIpConfig().setEnabled(false)).
						setAwsConfig(new AwsConfig().setEnabled(true).
							setAccessKey(Config.AWS_ACCESSKEY).
							setSecretKey(Config.AWS_SECRETKEY).
							setRegion(Config.AWS_REGION).
							setHostHeader("ec2.amazonaws.com").
							setSecurityGroupName(Config.getConfigParam("hc.discovery_group", "hazelcast")))));
			}

			hcInstance = Hazelcast.newHazelcastInstance(cfg);

			Para.addDestroyListener(new DestroyListener() {
				public void onDestroy() {
					shutdownClient();
				}
			});
		}

		return hcInstance;
	}

	/**
	 * This method stops the Hazelcast instance if it is running.
	 * <b>There's no need to call this explicitly!</b>
	 */
	protected static void shutdownClient() {
		if (hcInstance != null) {
			hcInstance.shutdown();
			hcInstance = null;
		}
	}

	private static String getNodeName() {
		return Config.PARA.concat("-hc-").concat(Config.WORKER_ID);
	}

	private static EvictionPolicy getEvictionPolicy() {
		return "LFU".equals(Config.getConfigParam("hc.eviction_policy", "LRU")) ?
				EvictionPolicy.LFU : EvictionPolicy.LRU;
	}

	private static MaxSizeConfig getMaxSize() {
		return new MaxSizeConfig().
				setSize(Config.getConfigInt("hc.max_size", 25)).
				setMaxSizePolicy(USED_HEAP_PERCENTAGE);
	}

	private static boolean isJMXOn() {
		return Config.getConfigBoolean("hc.jmx_enabled", true);
	}

}
