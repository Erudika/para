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
import java.util.ArrayList;
import java.util.Map;
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
	private static Future<?> POLLING_THREAD;
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
		if (POLLING_THREAD == null) {
			logger.info("Starting local river (polling interval: {}s)", POLLING_INTERVAL);
			POLLING_THREAD = Para.getExecutorService().submit(new LocalRiver(queue));
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
		if (POLLING_THREAD != null) {
			logger.info("Stopping local river...");
			POLLING_THREAD.cancel(true);
		}
	}

	/**
	 * A simple river pulling one message at a time from the queue.
	 */
	static class LocalRiver implements Runnable {

		private int idleCount = 0;
		private final Queue queue;
		private final ObjectReader jreader;

		LocalRiver(Queue queue) {
			this.queue = queue;
			this.jreader = ParaObjectUtils.getJsonReader(Map.class);
		}

		@SuppressWarnings("unchecked")
		public void run() {
			ArrayList<ParaObject> createList = new ArrayList<>();
			ArrayList<ParaObject> updateList = new ArrayList<>();
			ArrayList<ParaObject> deleteList = new ArrayList<>();

			while (true) {
				logger.debug("Waiting {}s for messages...", POLLING_INTERVAL);
				String msg = queue.pull();
				logger.debug("Pulled 1 message from queue.");

				try {
					int	processedHooks = 0;
					logger.debug("SQS MESSAGE: {}", msg);
					if (StringUtils.contains(msg, Config._APPID) && StringUtils.contains(msg, Config._TYPE)) {
						processedHooks += parseAndCategorizeMessage(msg, createList, updateList, deleteList);
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
					} else if (StringUtils.isBlank(msg)) {
						idleCount++;
						// no tasks in queue => throttle down pull requests
						if (SLEEP > 0 && idleCount >= 3) {
							try {
								logger.debug("Queue is empty. Sleeping for {}s...", SLEEP);
								Thread.sleep((long) SLEEP * 1000);
							} catch (InterruptedException e) {
								logger.warn("SQS river interrupted: ", e);
								Thread.currentThread().interrupt();
								break;
							}
						}
					}
				} catch (Exception e) {
					logger.error("River operation failed: {}", e);
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

}
