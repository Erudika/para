/*
 * Copyright 2013-2014 Erudika. http://erudika.com
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
	protected static HazelcastInstance getClient() {
		if (hcInstance == null) {
			hcInstance = Hazelcast.getHazelcastInstanceByName(getNodeName());
			if (hcInstance != null) {
				return hcInstance;
			}
			com.hazelcast.config.Config cfg = new com.hazelcast.config.Config();
			cfg.setInstanceName(getNodeName());
			MapConfig mapcfg = new MapConfig("default");
			mapcfg.setEvictionPercentage(25);
			mapcfg.setEvictionPolicy(MapConfig.EvictionPolicy.LRU);
	//			mapcfg.setMapStoreConfig(new MapStoreConfig().setEnabled(false).setClassName(NODE_NAME));
			mapcfg.setMaxSizeConfig(new MaxSizeConfig().setSize(25).setMaxSizePolicy(USED_HEAP_PERCENTAGE));
			cfg.addMapConfig(mapcfg);
			cfg.setProperty("hazelcast.jmx", "true");
			cfg.setProperty("hazelcast.logging.type", "slf4j");
			if (Config.IN_PRODUCTION) {
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

}
