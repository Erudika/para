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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class LocalQueue implements Queue {

	private static final Logger logger = LoggerFactory.getLogger(MockQueue.class);
	private static final int SLEEP = Config.getConfigInt("queue.polling_sleep_seconds", 60);
	private static Future<?> pollingTask;
	private static final int MAX_MESSAGES = 10;  //max in bulk
	private static final int POLLING_INTERVAL = Config.getConfigInt("queue.polling_interval_seconds",
			Config.IN_PRODUCTION ? 20 : 5);

	private ConcurrentLinkedQueue<String> q = new ConcurrentLinkedQueue<>();
	private String name;

	/**
	 * Default constructor.
	 */
	public LocalQueue() {
		this("queue");
	}

	/**
	 * @param name name
	 */
	public LocalQueue(String name) {
		this.name = name;
	}

	@Override
	public String pull() {
		String s = q.poll();
		return StringUtils.isBlank(s) ? "" : s;
	}

	@Override
	public void push(String task) {
		if (!StringUtils.isBlank(task)) {
			q.add(task);
		}
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void startPolling() {
		startPollingForMessages(this);
	}

	@Override
	public void stopPolling() {
		stopPollingForMessages();
	}

	/**
	 * Starts polling for messages from SQS in a separate thread.
	 * @param queue a queue instance
	 */
	static void startPollingForMessages(Queue queue) {
		if (pollingTask == null) {
			logger.info("Starting local river (polling interval: {}s)", POLLING_INTERVAL);
			pollingTask = Para.getExecutorService().submit(new River() {
				List<String> pullMessages() {
					String msg;
					int	count = 0;
					ArrayList<String> msgs = new ArrayList<String>(MAX_MESSAGES);
					while (!(msg = queue.pull()).isEmpty() && count <= MAX_MESSAGES) {
						msgs.add(msg);
					}
					return msgs;
				}
			});
			Para.addDestroyListener(new DestroyListener() {
				public void onDestroy() {
					stopPollingForMessages();
				}
			});
		}
	}

	/**
	 * Stops the thread that has been polling for messages.
	 */
	static void stopPollingForMessages() {
		if (pollingTask != null) {
			logger.info("Stopping local river...");
			pollingTask.cancel(true);
		}
	}

}
