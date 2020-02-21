/*
 * Copyright 2013-2020 Erudika. https://erudika.com
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
package com.erudika.para.iot;

import com.erudika.para.DestroyListener;
import com.erudika.para.Para;
import com.erudika.para.core.Thing;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.microsoft.azure.eventhubs.EventData;
import com.microsoft.azure.eventhubs.EventHubClient;
import com.microsoft.azure.eventhubs.EventPosition;
import com.microsoft.azure.eventhubs.PartitionReceiver;
import com.microsoft.azure.iot.service.sdk.DeliveryAcknowledgement;
import com.microsoft.azure.iot.service.sdk.Device;
import com.microsoft.azure.iot.service.sdk.IotHubServiceClientProtocol;
import com.microsoft.azure.iot.service.sdk.RegistryManager;
import com.microsoft.azure.iot.service.sdk.ServiceClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure IoT client.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AzureIoTService implements IoTService {

	private static final Logger logger = LoggerFactory.getLogger(AzureIoTService.class);

	private static final int MAX_MESSAGES = Config.getConfigInt("azure.iot_max_messages", 10);
	private static final int PARTITIONS_COUNT = Config.getConfigInt("azure.iot_partitions", 2);
	private static final String SERVICE_HOSTNAME = Config.getConfigParam("azure.iot_hostname", "");
	private static final String SERVICE_ACCESS_KEY = Config.getConfigParam("azure.iot_access_key", "");
	private static final String SERVICE_CONN_STR = "HostName=" + SERVICE_HOSTNAME +
			";SharedAccessKeyName=iothubowner;SharedAccessKey=" + SERVICE_ACCESS_KEY;
	private static final String EVENTHUB_NAME = Config.getConfigParam("azure.iot_eventhub_name", "");
	private static final String EVENTHUB_ENDPOINT = Config.getConfigParam("azure.iot_eventhub_endpoint", "");
	private static final String EVENTHUB_CONN_STR = "Endpoint=" + EVENTHUB_ENDPOINT + ";EntityPath=" + EVENTHUB_NAME +
			";SharedAccessKeyName=iothubowner;SharedAccessKey=" + SERVICE_ACCESS_KEY;

	private ServiceClient serviceClient = null;
	private RegistryManager registryManager = null;

	/**
	 * No-args constructor.
	 */
	public AzureIoTService() {
		if (!StringUtils.isBlank(SERVICE_ACCESS_KEY)) {
			if (!StringUtils.isBlank(EVENTHUB_ENDPOINT)) {
				final ArrayList<EventHubClient> recievers = new ArrayList<>();
				for (int i = 0; i < PARTITIONS_COUNT; i++) {
					recievers.add(receiveEventsAsync(Integer.toString(i)));
				}
				Para.addDestroyListener(new DestroyListener() {
					public void onDestroy() {
						for (EventHubClient recvr : recievers) {
							recvr.close();
						}
					}
				});
			}
			try {
				registryManager = RegistryManager.createFromConnectionString(SERVICE_CONN_STR);
			} catch (Exception ex) {
				logger.warn("Couldn't initialize Azure registry manager: {}", ex.getMessage());
			}
		}
	}

	protected ServiceClient getClient() {
		try {
			if (serviceClient != null) {
				return serviceClient;
			}
			serviceClient = ServiceClient.createFromConnectionString(SERVICE_CONN_STR, IotHubServiceClientProtocol.AMQPS);
			serviceClient.open();

			Para.addDestroyListener(new DestroyListener() {
				public void onDestroy() {
					shutdownClient();
				}
			});
		} catch (Exception ex) {
			logger.warn("Couldn't create Azure IoT service client: {}", ex.getMessage());
		}
		return serviceClient;
	}

	/**
	 * Stops the client and releases resources.
	 * <b>There's no need to call this explicitly!</b>
	 */
	protected void shutdownClient() {
		if (serviceClient != null) {
			try {
				serviceClient.close();
				serviceClient = null;
			} catch (Exception ex) {
				logger.warn("Couldn't close Azure IoT service client: {}", ex.getMessage());
			}
		}
	}

	@Override
	public Thing createThing(Thing thing) {
		if (thing == null || StringUtils.isBlank(thing.getName()) || StringUtils.isBlank(thing.getAppid()) ||
				StringUtils.isBlank(SERVICE_ACCESS_KEY) || existsThing(thing)) {
			return null;
		}
		try {
			thing.setId(Utils.getNewId());
			String id = cloudIDForThing(thing);
			Device device = Device.createFromId(id, null, null);
			device = registryManager.addDevice(device);
			logger.debug("Thing {} created on Azure.", id);

			thing.setServiceBroker("Azure");
			thing.getDeviceMetadata().put("thingId", id);
			thing.getDeviceMetadata().put("thingName", thing.getName());
			thing.getDeviceMetadata().put("thingGenId", device.getGenerationId());
			thing.getDeviceMetadata().put("status", device.getStatus());
			thing.getDeviceMetadata().put("primaryKey", device.getPrimaryKey());
			thing.getDeviceMetadata().put("secondaryKey", device.getSecondaryKey());
			thing.getDeviceMetadata().put("lastActivity", device.getLastActivityTime());
			thing.getDeviceMetadata().put("connectionState", device.getConnectionState());
			thing.getDeviceMetadata().put("connectionString", "HostName=" + SERVICE_HOSTNAME + ";DeviceId=" + id +
					";SharedAccessKey=" + device.getPrimaryKey());
		} catch (Exception e) {
			logger.warn(null, e);
		}
		return thing;
	}

	@Override
	public Map<String, Object> readThing(Thing thing) {
		// no operation - we're polling for messages in the background
		// instead of updating on read as in AWSIoTService.java
		return null;
	}

	@Override
	public void updateThing(Thing thing) {
		if (thing == null || StringUtils.isBlank(thing.getId()) || StringUtils.isBlank(thing.getAppid()) ||
				StringUtils.isBlank(SERVICE_ACCESS_KEY)) {
			return;
		}
		try {
			Date now = new Date();
			com.microsoft.azure.iot.service.sdk.Message messageToSend;
			messageToSend = new com.microsoft.azure.iot.service.sdk.Message(ParaObjectUtils.getJsonWriterNoIdent().
					writeValueAsBytes(thing.getDeviceState()));

			messageToSend.setDeliveryAcknowledgement(DeliveryAcknowledgement.None);
			messageToSend.setMessageId(UUID.randomUUID().toString());
			messageToSend.setExpiryTimeUtc(new Date(now.getTime() + 24 * 60 * 60 * 1000));
			messageToSend.setCorrelationId(UUID.randomUUID().toString());
//			messageToSend.setUserId(thing.getCreatorid());
			messageToSend.clearCustomProperties();

			getClient().send(cloudIDForThing(thing), messageToSend);
		} catch (Exception e) {
			logger.warn("Couldn't create thing: {}", e.getMessage());
		}
	}

	@Override
	public void deleteThing(Thing thing) {
		if (thing == null || StringUtils.isBlank(thing.getId()) || StringUtils.isBlank(thing.getAppid()) ||
				StringUtils.isBlank(SERVICE_ACCESS_KEY)) {
			return;
		}
		try {
			String id = cloudIDForThing(thing);
			registryManager.removeDeviceAsync(id);
			logger.debug("Thing {} removed from Azure.", id);
		} catch (Exception e) {
			logger.warn("Couldn't delete thing: {}", e.getMessage());
		}
	}

	@Override
	public boolean existsThing(Thing thing) {
		if (thing == null || StringUtils.isBlank(thing.getId()) || StringUtils.isBlank(thing.getAppid()) ||
				StringUtils.isBlank(SERVICE_ACCESS_KEY)) {
			return false;
		}
		try {
			return registryManager.getDevice(cloudIDForThing(thing)) != null;
		} catch (Exception e) {
			return false;
		}
	}

	private static EventHubClient receiveEventsAsync(final String partitionId) {
		EventHubClient client = null;
		try {
			client = EventHubClient.createSync(EVENTHUB_CONN_STR, Para.getExecutorService());
			client.createReceiver(EventHubClient.DEFAULT_CONSUMER_GROUP_NAME, partitionId,
						EventPosition.fromEnqueuedTime(Instant.now())).
					thenAccept(new Receiver(partitionId));
		} catch (Exception e) {
			logger.warn("Couldn't start receiving messages from Azure cloud: {}", e.getMessage());
		}
		return client;
	}

	private String cloudIDForThing(Thing thing) {
		return thing.getAppid().concat(Config.SEPARATOR).concat(thing.getId());
	}

	private static Thing thingFromCloudID(String id) {
		if (!StringUtils.isBlank(id) && id.contains(Config.SEPARATOR)) {
			String[] parts = id.split(Config.SEPARATOR);
			Thing t = new Thing(parts[1]);
			t.setServiceBroker("Azure");
			t.setAppid(parts[0]);
			return t;
		}
		return null;
	}

	/**
	 * Receiver class.
	 */
	static class Receiver implements Consumer<PartitionReceiver> {

		private String partitionId;

		Receiver(String partitionId) {
			this.partitionId = partitionId;
		}

		@Override
		public void accept(PartitionReceiver receiver) {
			while (true) {
				try {
					Iterable<EventData> receivedEvents = receiver.receive(MAX_MESSAGES).get();
					int batchSize = 0;
					if (receivedEvents != null) {
						for (EventData receivedEvent : receivedEvents) {
							String deviceId = (String) receivedEvent.getProperties().get("iothub-connection-device-id");
							Map<String, Object> deviceState = null;
							try {
								deviceState = ParaObjectUtils.getJsonReader(Map.class).readValue(receivedEvent.getBytes());
								logger.debug("Message received from Azure: {}", deviceState);
							} catch (Exception e) {	}

							if (deviceState != null) {
								Thing t = thingFromCloudID(deviceId);
								if (t != null) {
									t.setDeviceState(deviceState);
									Para.getDAO().update(t.getAppid(), t);
								}
							}
							batchSize++;
						}
					}
					logger.debug("Received {} messages from Azure for partition {}.", batchSize, partitionId);
				} catch (Exception e) {
					logger.warn("Failed to receive messages: {}", e.getMessage());
				}
			}
		}

	}

}
