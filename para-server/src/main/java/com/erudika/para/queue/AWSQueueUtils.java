/*
 * Copyright 2013-2019 Erudika. https://erudika.com
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

import com.erudika.para.DestroyListener;
import com.erudika.para.Para;
import com.erudika.para.annotations.Locked;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Thing;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.webhooks.WebhookUtils;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

/**
 * Helper utilities for connecting to AWS SQS.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class AWSQueueUtils {

	private static SqsAsyncClient sqsClient;
	private static final int MAX_MESSAGES = 10;  //max in bulk
	private static final int SLEEP = Config.getConfigInt("queue.polling_sleep_seconds", 60);
	private static final Map<String, Future<?>> POLLING_THREADS = new ConcurrentHashMap<String, Future<?>>();
	private static final int POLLING_INTERVAL = Config.getConfigInt("queue.polling_interval_seconds",
			Config.IN_PRODUCTION ? 20 : 5);

	private static final String LOCAL_ENDPOINT = "http://localhost:9324";
	private static final Logger logger = LoggerFactory.getLogger(AWSQueueUtils.class);

	/**
	 * No-args constructor.
	 */
	private AWSQueueUtils() { }

	/**
	 * Returns a client instance for AWS SQS.
	 * @return a client that talks to SQS
	 */
	public static SqsAsyncClient getClient() {
		if (sqsClient != null) {
			return sqsClient;
		}
		if (Config.getConfigBoolean("aws_sqs_local", false)) {
			sqsClient = SqsAsyncClient.builder().endpointOverride(URI.create(LOCAL_ENDPOINT)).
					credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x"))).build();
		} else {
			sqsClient = SqsAsyncClient.create();
		}

		Para.addDestroyListener(new DestroyListener() {
			public void onDestroy() {
				sqsClient.close();
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
				queueURL = getClient().createQueue(b -> b.queueName(name)).get().queueUrl();
			} catch (AwsServiceException ase) {
				logException(ase);
			} catch (SdkException ace) {
				logger.error("Could not reach SQS. {0}", ace.toString());
			} catch (InterruptedException | ExecutionException ex) {
				logger.error(null, ex);
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
				getClient().deleteQueue(b -> b.queueUrl(queueURL));
			} catch (AwsServiceException ase) {
				logException(ase);
			} catch (SdkException ace) {
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
			return getClient().getQueueUrl(b -> b.queueName(name)).get().queueUrl();
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
			list = getClient().listQueues().get().queueUrls();
		} catch (AwsServiceException ase) {
			logException(ase);
		} catch (SdkException ace) {
			logger.error("Could not reach SQS. {0}", ace.toString());
		} catch (InterruptedException | ExecutionException ex) {
			logger.error(null, ex);
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
						msgs.add(SendMessageBatchRequestEntry.builder().
								messageBody(message).
								id(Integer.toString(i)).build());
					}
					if (++j >= MAX_MESSAGES || i == messages.size() - 1) {
						if (!msgs.isEmpty()) {
							getClient().sendMessageBatch(b -> b.queueUrl(queueURL).entries(msgs));
							msgs.clear();
						}
						j = 0;
					}
				}
			} catch (AwsServiceException ase) {
				logException(ase);
			} catch (SdkException ace) {
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
				final int maxForBatch;
				if ((numberOfMessages > MAX_MESSAGES)) {
					batchSteps = (numberOfMessages / MAX_MESSAGES) + ((numberOfMessages % MAX_MESSAGES > 0) ? 1 : 0);
					maxForBatch = MAX_MESSAGES;
				} else {
					maxForBatch = numberOfMessages;
				}

				for (int i = 0; i < batchSteps; i++) {
					List<Message> list = getClient().receiveMessage(b -> b.queueUrl(queueURL).
							maxNumberOfMessages(maxForBatch).
							waitTimeSeconds(POLLING_INTERVAL)).get().messages();

					if (list != null && !list.isEmpty()) {
						List<DeleteMessageBatchRequestEntry> del = new ArrayList<>();
						for (Message msg : list) {
							messages.add(msg.body());
							del.add(DeleteMessageBatchRequestEntry.builder().
									id(msg.messageId()).receiptHandle(msg.receiptHandle()).build());
						}
						getClient().deleteMessageBatch(b -> b.queueUrl(queueURL).entries(del));
					}
				}
			} catch (AwsServiceException ase) {
				logException(ase);
			} catch (SdkException ace) {
				logger.error("Could not reach SQS. {}", ace.toString());
			} catch (InterruptedException | ExecutionException ex) {
				logger.error(null, ex);
			}
		}
		return messages;
	}

	/**
	 * Starts polling for messages from SQS in a separate thread.
	 * @param queueURL a queue URL
	 */
	public static void startPollingForMessages(final String queueURL) {
		if (!StringUtils.isBlank(queueURL) && !POLLING_THREADS.containsKey(queueURL)) {
			logger.info("Starting SQS river using queue {} (polling interval: {}s)", queueURL, POLLING_INTERVAL);
			POLLING_THREADS.putIfAbsent(queueURL, Para.getExecutorService().submit(new SQSRiver(queueURL)));
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
		if (!StringUtils.isBlank(queueURL) && POLLING_THREADS.containsKey(queueURL)) {
			logger.info("Stopping SQS river on queue {} ...", queueURL);
			POLLING_THREADS.get(queueURL).cancel(true);
			POLLING_THREADS.remove(queueURL);
		}
	}

	/**
	 * An SQS river.
	 * Adapted from https://github.com/albogdano/elasticsearch-river-amazonsqs
	 */
	static class SQSRiver implements Runnable {

		private int idleCount = 0;
		private final String queueURL;
		private final ObjectReader jreader;

		SQSRiver(String queueURL) {
			this.queueURL = queueURL;
			this.jreader = ParaObjectUtils.getJsonReader(Map.class);
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
					int	processedHooks = 0;
					for (final String msg : msgs) {
						logger.debug("SQS MESSAGE: {}", msg);
						if (StringUtils.contains(msg, Config._APPID) && StringUtils.contains(msg, Config._TYPE)) {
							processedHooks += parseAndCategorizeMessage(msg, createList, updateList, deleteList);
						}
					}

					if (!createList.isEmpty() || !updateList.isEmpty() || !deleteList.isEmpty() || processedHooks > 0) {
						if (!createList.isEmpty()) {
							Para.getDAO().createAll(createList);
						}
						if (!updateList.isEmpty()) {
							Para.getDAO().updateAll(updateList);
						}
						if (!deleteList.isEmpty()) {
							Para.getDAO().deleteAll(deleteList);
						}
						logger.debug("SQS river summary: {} created, {} updated, {} deleted, {} webhooks delivered.",
								createList.size(), updateList.size(), deleteList.size(), processedHooks);
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

		private int parseAndCategorizeMessage(final String msg, ArrayList<ParaObject> createList,
				ArrayList<ParaObject> updateList, ArrayList<ParaObject> deleteList)
				throws IOException {
			Map<String, Object> parsed = jreader.readValue(msg);
			String id = parsed.containsKey(Config._ID) ? (String) parsed.get(Config._ID) : null;
			String type = (String) parsed.get(Config._TYPE);
			String appid = (String) parsed.get(Config._APPID);
			Class<?> clazz = ParaObjectUtils.toClass(type);
			boolean isWhitelistedType = clazz.equals(Thing.class) || clazz.equals(Sysprop.class);

			if (!StringUtils.isBlank(appid) && isWhitelistedType) {
				if ("webhookpayload".equals(type)) {
					return WebhookUtils.processWebhookPayload(appid, id, parsed);
				}

				if (parsed.containsKey("_delete") && "true".equals(parsed.get("_delete")) && id != null) {
					Sysprop s = new Sysprop(id);
					s.setAppid(appid);
					deleteList.add(s);
				} else {
					if (id == null || "true".equals(parsed.get("_create"))) {
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
			return 0;
		}
	}

	private static void logException(AwsServiceException ase) {
		logger.error("AmazonServiceException: error={}, statuscode={}, awserrcode={}, errmessage={}, reqid={}",
				ase.toString(), ase.statusCode(), ase.awsErrorDetails().errorCode(),
				ase.awsErrorDetails().errorMessage(), ase.requestId());
	}

}
