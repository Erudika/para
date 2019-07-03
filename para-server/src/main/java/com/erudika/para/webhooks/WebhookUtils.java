/*
 * Copyright 2013-2019 Erudika. http://erudika.com
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
package com.erudika.para.webhooks;

import com.erudika.para.Para;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Webhook;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.nimbusds.jose.crypto.impl.HMAC;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import software.amazon.awssdk.utils.StringUtils;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class WebhookUtils {

	private static final Logger logger = LoggerFactory.getLogger(WebhookUtils.class);
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
	 * No-args constructor.
	 */
	private WebhookUtils() { }

	/**
	 * Builds the payload object.
	 * @param appid appid
	 * @param payload payload object to convert to JSON
	 * @param method Para.DAO method name
	 * @param webhook webhook object
	 * @return the payload + metadata object as JSON string
	 */
	public static String buildWebhookPayload(String appid, Object payload, String method, Webhook webhook) {
		if (webhook == null) {
			return "";
		}
		Map<String, Object> data = new HashMap<>();
		data.put(Config._ID, webhook.getId());
		data.put(Config._APPID, appid);
		data.put(Config._TYPE, "webhookpayload");
		data.put("targetUrl", webhook.getTargetUrl());
		data.put("urlEncoded", webhook.getUrlEncoded());
		data.put("method", method);

		Map<String, Object> payloadObject = new HashMap<>();
		payloadObject.put(Config._TIMESTAMP, System.currentTimeMillis());
		payloadObject.put(Config._APPID, appid);
		payloadObject.put("event", method);
		if (payload instanceof List) {
			payloadObject.put("items", payload);
		} else if (payload instanceof ParaObject) {
			payloadObject.put("items", Collections.singletonList(payload));
		}
		try {
			String payloadString = ParaObjectUtils.getJsonWriterNoIdent().writeValueAsString(payloadObject);
			data.put("payload", payloadString);
			data.put("signature", Utils.base64enc(HMAC.compute("HmacSHA256",
					webhook.getSecret().getBytes(Config.DEFAULT_ENCODING),
					payloadString.getBytes(Config.DEFAULT_ENCODING), null)));
			return ParaObjectUtils.getJsonWriterNoIdent().writeValueAsString(data);
		} catch (Exception e) {
			logger.error(null, e);
		}
		return "";
	}

	/**
	 * Processes the incoming payload pulled from queue. Sends a POST to {@code targetUrl}.
	 * @param appid appid
	 * @param id webhook id
	 * @param parsed payload with metadata
	 * @return number of processed webhooks 1 or 0
	 */
	public static int processWebhookPayload(String appid, String id, Map<String, Object> parsed) {
		if (!parsed.containsKey("targetUrl") || StringUtils.isBlank(id) || parsed.isEmpty()) {
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
			postToTarget.setHeader("X-Para-Event", (String) parsed.get("method"));
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

}
