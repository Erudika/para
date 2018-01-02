/*
 * Copyright 2013-2018 Erudika. https://erudika.com
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
package com.erudika.para.queue;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.erudika.para.DestroyListener;
import com.erudika.para.Para;
import com.erudika.para.annotations.Locked;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Thing;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.utils.Config;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper utilities for connecting to AWS SQS.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class AWSQueueUtils {

	private static AmazonSQS sqsClient;
	private static final int MAX_MESSAGES = 10;  //max in bulk
	private static final int SLEEP = Config.getConfigInt("queue.polling_sleep_seconds", 60);
	private static final int POLLING_INTERVAL = Config.getConfigInt("queue.polling_interval_seconds",
			Config.IN_PRODUCTION ? 20 : 0);

	private static final String LOCAL_ENDPOINT = "http://localhost:9324";
	private static final Logger logger = LoggerFactory.getLogger(AWSQueueUtils.class);

	private static volatile Map<String, Future<?>> pollingThreads = new HashMap<String, Future<?>>();

	/**
	 * No-args constructor.
	 */
	private AWSQueueUtils() { }

	/**
	 * Returns a client instance for AWS SQS.
	 * @return a client that talks to SQS
	 */
	public static AmazonSQS getClient() {
		if (sqsClient != null) {
			return sqsClient;
		}
		if (Config.IN_PRODUCTION) {
			sqsClient = AmazonSQSClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(
				new BasicAWSCredentials(Config.AWS_ACCESSKEY, Config.AWS_SECRETKEY))).
					withRegion(Config.AWS_REGION).build();
		} else {
			sqsClient = AmazonSQSClientBuilder.standard().
					withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x"))).
					withEndpointConfiguration(new EndpointConfiguration(LOCAL_ENDPOINT, "")).build();
		}

		Para.addDestroyListener(new DestroyListener() {
			public void onDestroy() {
				sqsClient.shutdown();
			}
		});
		return sqsClient;
	}

	/**
	 * Creates a new SQS queue on AWS.
	 * @param name queue name
	 * @return the queue URL or null
	 */
	public static String createQueue(String name) {
		if (StringUtils.isBlank(name)) {
			return null;
		}
		String queueURL = getQueueURL(name);
		if (queueURL == null) {
			try {
				queueURL = getClient().createQueue(new CreateQueueRequest(name)).getQueueUrl();
			} catch (AmazonServiceException ase) {
				logException(ase);
			} catch (AmazonClientException ace) {
				logger.error("Could not reach SQS. {0}", ace.toString());
			}
		}
		return queueURL;
	}

	/**
	 * Deletes an SQS queue on AWS.
	 * @param queueURL URL of the SQS queue
	 */
	public static void deleteQueue(String queueURL) {
		if (!StringUtils.isBlank(queueURL)) {
			try {
				getClient().deleteQueue(new DeleteQueueRequest(queueURL));
			} catch (AmazonServiceException ase) {
				logException(ase);
			} catch (AmazonClientException ace) {
				logger.error("Could not reach SQS. {0}", ace.toString());
			}
		}
	}

	/**
	 * Returns the SQS queue URL.
	 * @param name queue name
	 * @return the URL of the queue
	 */
	public static String getQueueURL(String name) {
		try {
			return getClient().getQueueUrl(name).getQueueUrl();
		} catch (Exception e) {
			logger.info("Queue '{}' could not be found: {}", name, e.getMessage());
			return null;
		}
	}

	/**
	 * Returns a list of URLs for all available queues on SQS.
	 * @return a list or queue URLs
	 */
	public static List<String> listQueues() {
		List<String> list = new ArrayList<>();
		try {
			list = getClient().listQueues().getQueueUrls();
		} catch (AmazonServiceException ase) {
			logException(ase);
		} catch (AmazonClientException ace) {
			logger.error("Could not reach SQS. {0}", ace.toString());
		}
		return list;
	}

	/**
	 * Pushes a number of messages in batch to an SQS queue.
	 * @param queueURL the URL of the SQS queue
	 * @param messages the massage bodies
	 */
	public static void pushMessages(String queueURL, List<String> messages) {
		if (!StringUtils.isBlank(queueURL) && messages != null) {
			// only allow strings - ie JSON
			try {
				int	j = 0;
				List<SendMessageBatchRequestEntry> msgs = new ArrayList<>(MAX_MESSAGES);
				for (int i = 0; i < messages.size(); i++) {
					String message = messages.get(i);
					if (!StringUtils.isBlank(message)) {
						msgs.add(new SendMessageBatchRequestEntry().
								withMessageBody(message).
								withId(Integer.toString(i)));
					}
					if (++j >= MAX_MESSAGES || i == messages.size() - 1) {
						if (!msgs.isEmpty()) {
							getClient().sendMessageBatch(queueURL, msgs);
							msgs.clear();
						}
						j = 0;
					}
				}
			} catch (AmazonServiceException ase) {
				logException(ase);
			} catch (AmazonClientException ace) {
				logger.error("Could not reach SQS. {}", ace.toString());
			}
		}
	}

	/**
	 * Pulls a number of messages from an SQS queue.
	 * @param queueURL the URL of the SQS queue
	 * @param numberOfMessages the number of messages to pull
	 * @return a list of messages
	 */
	public static List<String> pullMessages(String queueURL, int numberOfMessages) {
		List<String> messages = new ArrayList<>();
		if (!StringUtils.isBlank(queueURL)) {
			try {
				int batchSteps = 1;
				int maxForBatch = numberOfMessages;
				if ((numberOfMessages > MAX_MESSAGES)) {
					batchSteps = (numberOfMessages / MAX_MESSAGES) + ((numberOfMessages % MAX_MESSAGES > 0) ? 1 : 0);
					maxForBatch = MAX_MESSAGES;
				}

				for (int i = 0; i < batchSteps; i++) {
					List<Message> list = getClient().receiveMessage(new ReceiveMessageRequest(queueURL).
							withMaxNumberOfMessages(maxForBatch).withWaitTimeSeconds(POLLING_INTERVAL)).getMessages();
					if (list != null && !list.isEmpty()) {
						List<DeleteMessageBatchRequestEntry> del = new ArrayList<>();
						for (Message msg : list) {
							messages.add(msg.getBody());
							del.add(new DeleteMessageBatchRequestEntry(msg.getMessageId(), msg.getReceiptHandle()));
						}
						getClient().deleteMessageBatch(queueURL, del);
					}
				}
			} catch (AmazonServiceException ase) {
				logException(ase);
			} catch (AmazonClientException ace) {
				logger.error("Could not reach SQS. {}", ace.toString());
			}
		}
		return messages;
	}

	/**
	 * Starts polling for messages from SQS in a separate thread.
	 * @param queueURL a queue URL
	 */
	public static void startPollingForMessages(final String queueURL) {
		if (!StringUtils.isBlank(queueURL) && !pollingThreads.containsKey(queueURL)) {
			logger.info("Starting SQS river using queue {} (polling interval: {}s)", queueURL, POLLING_INTERVAL);
			pollingThreads.put(queueURL, Para.getExecutorService().submit(new SQSRiver(queueURL)));
			Para.addDestroyListener(new DestroyListener() {
				public void onDestroy() {
					stopPollingForMessages(queueURL);
				}
			});
		}
	}

	/**
	 * Stops the thread that has been polling for messages.
	 * @param queueURL the queue URL
	 */
	public static void stopPollingForMessages(String queueURL) {
		if (!StringUtils.isBlank(queueURL) && pollingThreads.containsKey(queueURL)) {
			logger.info("Stopping SQS river on queue {} ...", queueURL);
			pollingThreads.get(queueURL).cancel(true);
			pollingThreads.remove(queueURL);
		}
	}

	/**
	 * An SQS river.
	 * Adapted from https://github.com/albogdano/elasticsearch-river-amazonsqs
	 */
	static class SQSRiver implements Runnable {

		private int idleCount = 0;
		private final String queueURL;

		SQSRiver(String queueURL) {
			this.queueURL = queueURL;
		}

		@SuppressWarnings("unchecked")
		public void run() {
			ArrayList<ParaObject> createList = new ArrayList<>();
			ArrayList<ParaObject> updateList = new ArrayList<>();
			ArrayList<ParaObject> deleteList = new ArrayList<>();

			while (true) {
				logger.debug("Waiting {}s for messages...", POLLING_INTERVAL);
				List<String> msgs = pullMessages(queueURL, MAX_MESSAGES);
				logger.debug("Pulled {} messages from queue.", msgs.size());

				try {
					for (final String msg : msgs) {
						logger.debug("SQS MESSAGE: {}", msg);
						if (StringUtils.contains(msg, Config._APPID) && StringUtils.contains(msg, Config._TYPE)) {
							parseAndCategorizeMessage(msg, createList, updateList, deleteList);
						}
					}

					if (!createList.isEmpty() || !updateList.isEmpty() || !deleteList.isEmpty()) {
						Para.getDAO().createAll(createList);
						Para.getDAO().updateAll(updateList);
						Para.getDAO().deleteAll(deleteList);
						logger.debug("Objects pulled from SQS queue: {} created, {} updated, {} deleted.",
								createList.size(), updateList.size(), deleteList.size());
						createList.clear();
						updateList.clear();
						deleteList.clear();
						idleCount = 0;
					} else if (msgs.isEmpty()) {
						idleCount++;
						// no tasks in queue => throttle down pull requests
						if (SLEEP > 0 && idleCount >= 3) {
							try {
								logger.debug("Queue {} is empty. Sleeping for {}s...", queueURL, SLEEP);
								Thread.sleep((long) SLEEP * 1000);
							} catch (InterruptedException e) {
								logger.warn("SQS river interrupted: ", e);
								Thread.currentThread().interrupt();
								break;
							}
						}
					}
				} catch (Exception e) {
					logger.error("Batch processing operation failed: {}", e);
				}
			}
		}
	}

	private static void parseAndCategorizeMessage(final String msg, ArrayList<ParaObject> createList,
			ArrayList<ParaObject> updateList, ArrayList<ParaObject> deleteList) throws IOException {
		Map<String, Object> parsed = ParaObjectUtils.getJsonReader(Map.class).readValue(msg);
		String id = parsed.containsKey(Config._ID) ? (String) parsed.get(Config._ID) : null;
		String type = (String) parsed.get(Config._TYPE);
		String appid = (String) parsed.get(Config._APPID);
		Class<?> clazz = ParaObjectUtils.toClass(type);
		boolean isWhitelistedType = clazz.equals(Thing.class) || clazz.equals(Sysprop.class);

		if (!StringUtils.isBlank(appid) && isWhitelistedType) {
			if (parsed.containsKey("_delete") && "true".equals(parsed.get("_delete"))) {
				Sysprop s = new Sysprop(id);
				s.setAppid(appid);
				deleteList.add(s);
			} else {
				if (id == null) {
					ParaObject obj = ParaObjectUtils.setAnnotatedFields(parsed);
					if (obj != null) {
						createList.add(obj);
					}
				} else {
					updateList.add(ParaObjectUtils.setAnnotatedFields(Para.getDAO().
							read(appid, id), parsed, Locked.class));
				}
			}
		}
	}

	private static void logException(AmazonServiceException ase) {
		logger.error("AmazonServiceException: error={}, statuscode={}, awserrcode={}, errtype={}, reqid={}",
				ase.toString(), ase.getStatusCode(), ase.getErrorCode(), ase.getErrorType(), ase.getRequestId());
	}

}
