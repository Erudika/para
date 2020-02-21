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
package com.erudika.para.queue;

import com.erudika.para.DestroyListener;
import com.erudika.para.Para;
import com.erudika.para.utils.Config;
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
	private static final Map<String, Future<?>> POLLING_THREADS = new ConcurrentHashMap<String, Future<?>>();

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
	protected static String createQueue(String name) {
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
				Thread.currentThread().interrupt();
			}
		}
		return queueURL;
	}

	/**
	 * Deletes an SQS queue on AWS.
	 * @param queueURL URL of the SQS queue
	 */
	protected static void deleteQueue(String queueURL) {
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
	protected static String getQueueURL(String name) {
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
	protected static List<String> listQueues() {
		List<String> list = new ArrayList<>();
		try {
			list = getClient().listQueues().get().queueUrls();
		} catch (AwsServiceException ase) {
			logException(ase);
		} catch (SdkException ace) {
			logger.error("Could not reach SQS. {0}", ace.toString());
		} catch (InterruptedException | ExecutionException ex) {
			logger.error(null, ex);
			Thread.currentThread().interrupt();
		}
		return list;
	}

	/**
	 * Pushes a number of messages in batch to an SQS queue.
	 * @param queueURL the URL of the SQS queue
	 * @param messages the massage bodies
	 */
	protected static void pushMessages(String queueURL, List<String> messages) {
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
	protected static List<String> pullMessages(String queueURL, int numberOfMessages) {
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
							waitTimeSeconds(River.POLLING_INTERVAL)).get().messages();

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
			} catch (ExecutionException ee) {
				logger.error("SQS Execution exception. {}", ee.toString());
			} catch (InterruptedException ex) {
				logger.error("Interrupted while pulling messages from queue!", ex);
				Thread.currentThread().interrupt();
			}
		}
		return messages;
	}

	/**
	 * Starts polling for messages from SQS in a separate thread.
	 * @param queueURL a queue URL
	 */
	protected static void startPollingForMessages(final String queueURL) {
		if (!StringUtils.isBlank(queueURL) && !POLLING_THREADS.containsKey(queueURL)) {
			logger.info("Starting SQS river using queue {} (polling interval: {}s)", queueURL, River.POLLING_INTERVAL);
			POLLING_THREADS.putIfAbsent(queueURL, Para.getExecutorService().submit(new River() {
				List<String> pullMessages() {
					return AWSQueueUtils.pullMessages(queueURL, MAX_MESSAGES);
				}
			}));
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
	protected static void stopPollingForMessages(String queueURL) {
		if (!StringUtils.isBlank(queueURL) && POLLING_THREADS.containsKey(queueURL)) {
			logger.info("Stopping SQS river on queue {} ...", queueURL);
			POLLING_THREADS.get(queueURL).cancel(true);
			POLLING_THREADS.remove(queueURL);
		}
	}

	private static void logException(AwsServiceException ase) {
		logger.error("AmazonServiceException: error={}, statuscode={}, awserrcode={}, errmessage={}, reqid={}",
				ase.toString(), ase.statusCode(), ase.awsErrorDetails().errorCode(),
				ase.awsErrorDetails().errorMessage(), ase.requestId());
	}

}
