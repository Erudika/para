/*
 * Copyright 2013-2020 Erudika. http://erudika.com
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

import com.erudika.para.Para;
import com.erudika.para.annotations.Locked;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Thing;
import com.erudika.para.core.Webhook;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.databind.ObjectReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pulls messages from a queue and processes them.
 * Adapted from https://github.com/albogdano/elasticsearch-river-amazonsqs
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public abstract class River implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(River.class);
	private static final int SLEEP = Config.getConfigInt("queue.polling_sleep_seconds", 60);
	private static final int MAX_FAILED_WEBHOOK_ATTEMPTS = Config.getConfigInt("max_failed_webhook_attempts", 10);
	private static final CloseableHttpClient HTTP;

	static {
		int timeout = 10 * 1000;
		HTTP = HttpClientBuilder.create().
				setConnectionReuseStrategy(new NoConnectionReuseStrategy()).
				setDefaultRequestConfig(RequestConfig.custom().
						setConnectTimeout(timeout).
						setConnectionRequestTimeout(timeout).
						setCookieSpec(CookieSpecs.STANDARD).
						setSocketTimeout(timeout).
						build()).
				build();
	}

	/**
	 * The polling interval in seconds for this river. Polls queue ever X seconds.
	 */
	public static final int POLLING_INTERVAL = Config.getConfigInt("queue.polling_interval_seconds",
			Config.IN_PRODUCTION ? 20 : 5);

	/**
	 * @return a list of messages pulled from queue
	 */
	abstract List<String> pullMessages();

	/**
	 * Starts the river.
	 */
	public void run() {
		List<ParaObject> createList = new LinkedList<>();
		List<ParaObject> updateList = new LinkedList<>();
		List<ParaObject> deleteList = new LinkedList<>();
		ObjectReader jreader = ParaObjectUtils.getJsonReader(Map.class);
		int idleCount = 0;

		while (true) {
			logger.debug("Waiting {}s for messages...", POLLING_INTERVAL);
			List<String> msgs = pullMessages();
			logger.debug("Pulled {} messages from queue.", msgs.size());

			try {
				int processedHooks = 0;
				for (final String msg : msgs) {
					logger.debug("Message from queue: {}", msg);
					if (StringUtils.contains(msg, Config._APPID) && StringUtils.contains(msg, Config._TYPE)) {
						processedHooks += parseAndCategorizeMessage(jreader.readValue(msg),
								createList, updateList, deleteList);
					}
				}

				if (!createList.isEmpty() || !updateList.isEmpty() || !deleteList.isEmpty() || processedHooks > 0) {
					logger.debug("River summary: {} created, {} updated, {} deleted, {} webhooks delivered.",
							createList.size(), updateList.size(), deleteList.size(), processedHooks);
					persistChanges(createList, updateList, deleteList);
					idleCount = 0;
				} else if (msgs.isEmpty()) {
					idleCount++;
					// no tasks in queue => throttle down pull requests
					if (SLEEP > 0 && idleCount >= 3) {
						try {
							logger.debug("Queue is empty. Sleeping for {}s...", SLEEP);
							Thread.sleep((long) SLEEP * 1000);
						} catch (InterruptedException e) {
							logger.warn("River interrupted: ", e);
							Thread.currentThread().interrupt();
							break;
						}
					}
				}
			} catch (Exception e) {
				logger.error("Batch processing operation failed:", e);
			}
			if (Thread.interrupted()) {
				break;
			}
		}
	}

	private int parseAndCategorizeMessage(Map<String, Object> parsed, List<ParaObject> createList,
			List<ParaObject> updateList, List<ParaObject> deleteList) {
		String id = parsed.containsKey(Config._ID) ? (String) parsed.get(Config._ID) : null;
		String type = (String) parsed.get(Config._TYPE);
		String appid = (String) parsed.get(Config._APPID);
		Class<?> clazz = ParaObjectUtils.toClass(type);
		boolean isWhitelistedType = clazz.equals(Thing.class) || clazz.equals(Sysprop.class);

		if (!StringUtils.isBlank(appid) && isWhitelistedType) {
			if ("webhookpayload".equals(type)) {
				return processWebhookPayload(appid, id, parsed);
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

	/**
	 * Processes the incoming payload pulled from queue. Sends a POST to {@code targetUrl}.
	 * @param appid appid
	 * @param id webhook id
	 * @param parsed payload with metadata
	 * @return number of processed webhooks 1 or 0
	 */
	protected int processWebhookPayload(String appid, String id, Map<String, Object> parsed) {
		if (!parsed.containsKey("targetUrl") || software.amazon.awssdk.utils.StringUtils.isBlank(id) || parsed.isEmpty()) {
			return 0;
		}
		try {
			boolean urlEncoded = (boolean) parsed.get("urlEncoded");
			String targetUrl = (String) parsed.get("targetUrl");
			HttpPost postToTarget = new HttpPost(targetUrl);
			postToTarget.addHeader("User-Agent", "Para Webhook Dispacher " + Para.getVersion());
			postToTarget.setHeader(HttpHeaders.CONTENT_TYPE, urlEncoded ?
					"application/x-www-form-urlencoded" : "application/json");
			postToTarget.setHeader("X-Webhook-Signature", (String) parsed.get("signature"));
			postToTarget.setHeader("X-Para-Event", (String) parsed.get("event"));
			if (urlEncoded) {
				postToTarget.setEntity(new StringEntity("payload=".
						concat(Utils.urlEncode((String) parsed.get("payload"))), "UTF-8"));
			} else {
				postToTarget.setEntity(new StringEntity((String) parsed.get("payload"), "UTF-8"));
			}
			boolean ok = false;
			String status = "";
			try (CloseableHttpResponse resp1 = HTTP.execute(postToTarget)) {
				if (resp1 != null && resp1.getStatusLine().getStatusCode() != 200) {
					status = resp1.getStatusLine().getReasonPhrase();
					logger.info("Webhook {} delivery failed! {} responded with {} {} instead of 200 OK.", id,
							targetUrl, resp1.getStatusLine().getStatusCode(), resp1.getStatusLine().getReasonPhrase());
				} else {
					logger.debug("Webhook {} delivered to {} successfully.", id, targetUrl);
					ok = true;
				}
			} catch (Exception e) {
				logger.info("Webhook not delivered! {} isn't responding. {}", targetUrl, status);
			} finally {
				if (!ok) {
					// count failed delivieries and disable that webhook object after X failed attempts
					String countId = "failed_webhook_count" + Config.SEPARATOR + id;
					Integer count = Para.getCache().get(appid, countId);
					if (count == null) {
						Para.getCache().put(appid, countId, 1);
					} else if (count >= (MAX_FAILED_WEBHOOK_ATTEMPTS - 1)) {
						Webhook hook = Para.getDAO().read(appid, id);
						if (hook != null) {
							hook.setActive(false);
							hook.setTooManyFailures(true);
							Para.getDAO().update(appid, hook);
							Para.getCache().remove(appid, countId);
							logger.info("Webhook {} was disabled - a maximum of {} failed deliveries was reached.",
									id, MAX_FAILED_WEBHOOK_ATTEMPTS);
						}
					} else {
						Para.getCache().put(appid, countId, ++count);
					}
				}
			}
			return 1;
		} catch (Exception e) {
			logger.error("Webhook payload was not delivered:", e);
		}
		return 0;
	}

	private void persistChanges(List<ParaObject> createList, List<ParaObject> updateList, List<ParaObject> deleteList) {
		if (!createList.isEmpty()) {
			Para.getDAO().createAll(createList);
		}
		if (!updateList.isEmpty()) {
			Para.getDAO().updateAll(updateList);
		}
		if (!deleteList.isEmpty()) {
			Para.getDAO().deleteAll(deleteList);
		}
		createList.clear();
		updateList.clear();
		deleteList.clear();
	}
}
