/*
 * Copyright 2013-2022 Erudika. http://erudika.com
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
package com.erudika.para.server.queue;

import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Webhook;
import com.erudika.para.core.annotations.Locked;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.utils.Utils;
import com.erudika.para.server.utils.HealthUtils;
import com.fasterxml.jackson.databind.ObjectReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.ws.rs.core.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pulls messages from a queue and processes them.
 * Adapted from https://github.com/albogdano/elasticsearch-river-amazonsqs
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public abstract class River implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(River.class);
	private static final int SLEEP = Para.getConfig().getConfigInt("queue.polling_sleep_seconds", 60);
	private static final int MAX_FAILED_WEBHOOK_ATTEMPTS = Para.getConfig().getConfigInt("max_failed_webhook_attempts", 10);
	private static final int MAX_INDEXING_RETRIES = Para.getConfig().getConfigInt("river.max_indexing_retries", 5);
	private static final CloseableHttpClient HTTP;
	private static ConcurrentHashMap<String, Integer> pendingIds;

	static {
		int timeout = 10;
		HTTP = HttpClientBuilder.create().
				setConnectionReuseStrategy((HttpRequest hr, HttpResponse hr1, HttpContext hc) -> false).
				setDefaultRequestConfig(RequestConfig.custom().
						setConnectTimeout(timeout, TimeUnit.SECONDS).
						setConnectionRequestTimeout(timeout, TimeUnit.SECONDS).
						build()).
				build();
	}

	/**
	 * The polling interval in seconds for this river. Polls queue ever X seconds.
	 */
	public static final int POLLING_INTERVAL = Para.getConfig().getConfigInt("queue.polling_interval_seconds",
			Para.getConfig().inProduction() ? 20 : 5);

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

		try {
			while (!Thread.interrupted()) {
				logger.debug("Waiting {}s for messages...", POLLING_INTERVAL);
				int processedHooks = 0;
				List<String> msgs = Collections.emptyList();
				if (HealthUtils.getInstance().isHealthy()) {
					try {
						msgs = pullMessages();
						logger.debug("Pulled {} messages from queue.", msgs.size());

						for (final String msg : msgs) {
							logger.debug("Message from queue: {}", msg);
							if (StringUtils.contains(msg, Config._APPID) && StringUtils.contains(msg, Config._TYPE)) {
								processedHooks += parseAndCategorizeMessage(jreader.readValue(msg),
										createList, updateList, deleteList);
							}
						}
					} catch (Exception e) {
						logger.error("Batch processing operation failed:", e);
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
						logger.debug("Queue is empty. Sleeping for {}s...", SLEEP);
						Thread.sleep(SLEEP * 1000L);
					}
				}
			}
		} catch (InterruptedException ex) {
			logger.info("River interrupted: {}", ex.getMessage());
			Thread.currentThread().interrupt();
		}
	}

	private int parseAndCategorizeMessage(Map<String, Object> parsed, List<ParaObject> createList,
			List<ParaObject> updateList, List<ParaObject> deleteList) {
		String id = parsed.containsKey(Config._ID) ? (String) parsed.get(Config._ID) : null;
		String type = (String) parsed.get(Config._TYPE);
		String appid = (String) parsed.get(Config._APPID);
		Class<?> clazz = ParaObjectUtils.toClass(type);
		boolean isWhitelistedType = clazz.equals(Sysprop.class);

		if (!StringUtils.isBlank(appid) && isWhitelistedType) {
			if ("webhookpayload".equals(type)) {
				return processWebhookPayload(appid, id, parsed);
			}
			if ("indexpayload".equals(type)) {
				return processIndexPayload(appid, id, parsed);
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
		if (!Para.getConfig().webhooksEnabled() || !parsed.containsKey("targetUrl") || StringUtils.isBlank(id) || parsed.isEmpty()) {
			return 0;
		}
		try {
			boolean urlEncoded = (boolean) parsed.get("urlEncoded");
			String targetUrl = StringUtils.trimToEmpty((String) parsed.get("targetUrl"));
			String payload = (String) parsed.get("payload");
			Integer repeatDelivery = Math.abs(NumberUtils.toInt(parsed.get("repeatedDeliveryAttempts") + "", 1));
			HttpPost postToTarget = new HttpPost(targetUrl);
			postToTarget.addHeader("User-Agent", "Para Webhook Dispacher " + Para.getVersion());
			postToTarget.setHeader(HttpHeaders.CONTENT_TYPE, urlEncoded ?
					"application/x-www-form-urlencoded" : "application/json");
			postToTarget.setHeader("X-Webhook-Signature", (String) parsed.get("signature"));
			postToTarget.setHeader("X-Para-Event", (String) parsed.get("event"));
			if (urlEncoded) {
				postToTarget.setEntity(new StringEntity("payload=".concat(Utils.urlEncode(payload)),
						Charset.forName(Para.getConfig().defaultEncoding())));
			} else {
				postToTarget.setEntity(new StringEntity(payload, Charset.forName(Para.getConfig().defaultEncoding())));
			}
			IntStream.range(0, Math.max(repeatDelivery, 100)).parallel().forEach(repeat -> {
				boolean ok = false;
				String status = "";
				try (CloseableHttpResponse resp1 = HTTP.execute(postToTarget)) {
					if (resp1 != null && Math.abs(resp1.getCode() - 200) > 10) {
						status = resp1.getReasonPhrase();
						logger.info("Webhook {} delivery failed! {} responded with code {} {} instead of 2xx.", id,
								targetUrl, resp1.getCode(), resp1.getReasonPhrase());
					} else {
						logger.debug("Webhook {} delivered to {} successfully.", id, targetUrl);
						ok = true;
					}
				} catch (Exception e) {
					logger.info("Webhook {} not delivered! {} isn't responding. {}", id, targetUrl, status);
				} finally {
					if (!ok) {
						updateFailureCount(appid, id);
					}
				}
			});
			return 1;
		} catch (Exception e) {
			logger.error("Webhook payload was not delivered:", e);
		}
		return 0;
	}

	/**
	 * Processes the incoming payload pulled from queue.
	 * @param appid appid
	 * @param opId indexing operation id
	 * @param parsed payload with metadata
	 * @return number of processed requests 1 or 0
	 */
	@SuppressWarnings("unchecked")
	protected int processIndexPayload(String appid, String opId, Map<String, Object> parsed) {
		if (!Para.getConfig().isSearchEnabled() || StringUtils.isBlank(opId) || parsed.isEmpty()) {
			return 0;
		}
		Object payload = parsed.get("payload");
		try {
			switch (opId) {
				case "index_all_op":
					indexAllWithRetry(appid, payload);
					break;
				case "unindex_all_op":
					Para.getSearch().unindexAll(appid, getPayloadObjects(appid, payload));
					break;
				case "rebuild_index_op":
					// we can't be sure if the app object exists in DB or not, so use the payload to construct the app
					App app = (App) ParaObjectUtils.setAnnotatedFields((Map) payload);
					Para.getSearch().rebuildIndex(Para.getDAO(), app, "");
					break;
				case "create_index_op":
					app = (App) ParaObjectUtils.setAnnotatedFields((Map) payload);
					Para.getSearch().createIndex(app);
					break;
				case "delete_index_op":
					app = (App) ParaObjectUtils.setAnnotatedFields((Map) payload);
					Para.getSearch().deleteIndex(app);
					break;
				default:
					break;
			}
			return 1;
		} catch (Exception e) {
			logger.error("Indexing operation " + opId + " failed for app '" + appid + "'!", e);
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

	@SuppressWarnings("unchecked")
	private void indexAllWithRetry(String appid, Object payload) {
		List<String> ids = (List<String>) Optional.ofNullable(payload).orElse(Collections.emptyList());
		Para.getCache().removeAll(appid, ids);
		Map<String, ParaObject> objs = Para.getDAO().readAll(appid, ids, true);
		Para.getSearch().indexAll(appid, objs.values().stream().filter(v -> v != null).collect(Collectors.toList()));

		if (objs.containsValue(null)) {
			if (pendingIds == null) {
				pendingIds = new ConcurrentHashMap<>();
			}
			objs.entrySet().stream().filter(entry -> (entry.getValue() == null)).forEachOrdered(entry -> {
				pendingIds.putIfAbsent(entry.getKey(), 1);
			});
//			This line below throws error, possibly ConcurrentModificationException
//			objs.keySet().stream().filter(k -> objs.get(k) == null).forEach(k -> pendingIds.putIfAbsent(k, 1));
			logger.debug("Some objects are missing from local database while performing 'index_all_op': {}", pendingIds);
			Para.asyncExecute(() -> {
				try {
					for (int i = 0; i < MAX_INDEXING_RETRIES; i++) {
						Thread.sleep(1000L * (i + 1));
						Map<String, ParaObject> pending = Para.getDAO().readAll(appid,
								new ArrayList<>(pendingIds.keySet()), true);
						int pendingCount = pendingIds.size();
						pending.entrySet().stream().filter(entry -> (entry.getValue() != null)).forEachOrdered(entry -> {
							pendingIds.remove(entry.getKey());
						});
//						pending.keySet().stream().filter(k -> pending.get(k) != null).forEach(k -> pendingIds.remove(k));
						if (pendingCount != pendingIds.size()) {
							Para.getSearch().indexAll(appid, pending.values().stream().collect(Collectors.toList()));
						}
						if (pendingIds.isEmpty()) {
							break;
						}
					}
				} catch (InterruptedException ex) {
					logger.info("Retry indexing operation interrupted: {}", ex.getMessage());
					Thread.currentThread().interrupt();
				} finally {
					if (!pendingIds.isEmpty()) {
						logger.warn("Indexing operation 'index_all_op' failed for objects {} as they "
								+ "were not found in the database for app '{}'. This may cause the index "
								+ "to become out of sync or corrupted.", pendingIds, appid);
						pendingIds.clear();
					}
				}
			});
		}
	}

	@SuppressWarnings("unchecked")
	private List<ParaObject> getPayloadObjects(String appid, Object payload) {
		List<String> ids = (List<String>) Optional.ofNullable(payload).orElse(Collections.emptyList());
		Para.getCache().removeAll(appid, ids);
		return ids.stream().map(id -> {
			Sysprop s = new Sysprop(id);
			s.setAppid(appid);
			return s;
		}).collect(Collectors.toList());
	}

	private void updateFailureCount(String appid, String id) {
		// count failed delivieries and disable that webhook object after X failed attempts
		String countId = "failed_webhook_count" + Para.getConfig().separator() + id;
		Integer count = Para.getCache().get(appid, countId);
		if (count == null) {
			count = 0;
		}
		if (count >= (MAX_FAILED_WEBHOOK_ATTEMPTS - 1)) {
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
