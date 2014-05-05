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
package com.erudika.para.queue;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.erudika.para.Para;
import com.erudika.para.utils.Config;
import java.util.List;
import org.slf4j.Logger;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the {@link Queue} interface using the AWS Simple Queue Service.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class AWSQueue implements Queue {

	private static final int MAX_MESSAGES = 1;  //max in bulk
	private AmazonSQSAsyncClient sqs;
	private static final String SQS_ENDPOINT = "https://sqs.".concat(Config.AWS_REGION).concat(".amazonaws.com");
	private static final Logger logger = LoggerFactory.getLogger(AWSQueue.class);
	private String name;
	private String endpoint;
	private String url;

	/**
	 * No-args constructor
	 */
	public AWSQueue() {
		this("test123", SQS_ENDPOINT);
	}

	/**
	 * Default consturctor.
	 * This queue contains only messages in JSON format.
	 * @param name name of the queue
	 * @param endpoint endpoint url
	 */
	public AWSQueue(String name, String endpoint) {
		this.endpoint = StringUtils.isBlank(endpoint) ? SQS_ENDPOINT : endpoint;
		this.name = name;
		this.url = create(name);
	}

	AmazonSQSClient client() {
		if (sqs != null) {
			return sqs;
		}
		sqs = new AmazonSQSAsyncClient(new BasicAWSCredentials(Config.AWS_ACCESSKEY, Config.AWS_SECRETKEY));
		sqs.setRegion(Region.getRegion(Regions.fromName(Config.AWS_REGION)));
		sqs.setEndpoint(this.endpoint);

		Para.addDestroyListener(new Para.DestroyListener() {
			public void onDestroy() {
				sqs.shutdown();
			}
		});
		return sqs;
	}

	@Override
	public void push(String task) {
		if (!StringUtils.isBlank(url) && task != null) {
			// only allow strings - ie JSON
			if (!StringUtils.isBlank(task)) {
				// Send a message
				try {
					SendMessageRequest sendReq = new SendMessageRequest();
					sendReq.setQueueUrl(url);
					sendReq.setMessageBody(task);

					client().sendMessage(sendReq);
				} catch (AmazonServiceException ase) {
					logException(ase);
				} catch (AmazonClientException ace) {
					logger.error("Could not reach SQS. {}", ace.toString());
				}
			}
		}
	}

	@Override
	public String pull() {
		String task = "[]";
		if (!StringUtils.isBlank(url)) {
			try {
				ReceiveMessageRequest receiveReq = new ReceiveMessageRequest(url);
				receiveReq.setMaxNumberOfMessages(MAX_MESSAGES);
				List<Message> list = client().receiveMessage(receiveReq).getMessages();

				if (list != null && !list.isEmpty()) {
					Message message = list.get(0);
					client().deleteMessage(new DeleteMessageRequest(url, message.getReceiptHandle()));
					task = message.getBody();
				}
			} catch (AmazonServiceException ase) {
				logException(ase);
			} catch (AmazonClientException ace) {
				logger.error("Could not reach SQS. {}", ace.toString());
			}
		}
		return task;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	private String create(String name) {
		String u = endpoint.concat("/").concat(name);
		try {
			u = client().createQueue(new CreateQueueRequest(name)).getQueueUrl();
		} catch (AmazonServiceException ase) {
			logException(ase);
		} catch (AmazonClientException ace) {
			logger.error("Could not reach SQS. {0}", ace.toString());
		}
		return u;
	}
//
//	public void delete() {
//		try {
//			client().deleteQueue(new DeleteQueueRequest(QUEUE_URL));
//		} catch (AmazonServiceException ase) {
//			logException(ase);
//		} catch (AmazonClientException ace) {
//			logger.error("Could not reach SQS. {0}", ace.toString());
//		}
//	}
//
//	public List<String> listQueues() {
//		List<String> list = null;
//		try {
//			list = client().listQueues().getQueueUrls();
//		} catch (AmazonServiceException ase) {
//			logException(ase);
//		} catch (AmazonClientException ace) {
//			logger.error("Could not reach SQS. {0}", ace.toString());
//		}
//		return list;
//	}

	private void logException(AmazonServiceException ase) {
		logger.error("AmazonServiceException: error={}, statuscode={}, "
			+ "awserrcode={}, errtype={}, reqid={}", ase.toString(), ase.getStatusCode(),
			ase.getErrorCode(), ase.getErrorType(), ase.getRequestId());
	}
}
