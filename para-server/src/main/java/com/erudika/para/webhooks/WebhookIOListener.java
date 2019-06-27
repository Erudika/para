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

import com.erudika.para.IOListener;
import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Webhook;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

/**
 * Listens for IO events and forwards them to the registered webhooks, via a queue.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class WebhookIOListener implements IOListener {


	@Override
	public void onPreInvoke(Method method, Object[] args) {
		// noop
	}

	@Override
	public void onPostInvoke(Method method, Object[] args, Object result) {
		if (method != null && !method.getName().startsWith("read")) {
			Object paraObjects = getObjectsFromArguments(args);
			// don't process webhooks for operations on webhook objects.
			if (paraObjects == null || paraObjects instanceof Webhook || (paraObjects instanceof Sysprop &&
					Utils.type(Webhook.class).equals(((Sysprop) paraObjects).getType()))) {
				return;
			}
			Para.asyncExecute(new Runnable() {
				public void run() {
					String appid = (String) args[0];
					Pager p = new Pager(10);
					p.setSortby("_docid");
					List<Webhook> webhooks;
					do {
						Map<String, Object> terms = new HashMap<>();
						terms.put(method.getName(), true);
						terms.put("active", true);
						webhooks = Para.getSearch().findTerms(appid, Utils.type(Webhook.class), terms, true, p);

						if (!webhooks.isEmpty()) {
							for (Webhook webhook : webhooks) {
								if (StringUtils.isBlank(webhook.getTypeFilter()) ||
										App.ALLOW_ALL.equals(webhook.getTypeFilter()) ||
												typeFilterMatches(webhook, paraObjects)) {
									Para.getQueue().push(WebhookUtils.buildWebhookPayload(appid,
											paraObjects, method.getName(), webhook));
								}
							}
						}
					} while (!webhooks.isEmpty());
				}

				private boolean typeFilterMatches(Webhook webhook, Object paraObjects) {
					if (paraObjects instanceof ParaObject) {
						return webhook.getTypeFilter().equalsIgnoreCase(((ParaObject) paraObjects).getType());
					} else if (paraObjects instanceof List) {
						List<?> list = (List) paraObjects;
						if (!list.isEmpty() && list.get(0) instanceof ParaObject) {
							return webhook.getTypeFilter().equalsIgnoreCase(((ParaObject) list.get(0)).getType());
						}
					}
					return false;
				}
			});
		}
	}

	private Object getObjectsFromArguments(Object[] args) {
		for (Object arg : args) {
			if (arg != null && arg instanceof ParaObject) {
				return (ParaObject) arg;
			}
		}
		for (Object arg : args) {
			if (arg != null && arg instanceof List) {
				List<?> list = (List) arg;
				if (!list.isEmpty() && list.get(0) instanceof ParaObject) {
					return list;
				}
			}
		}
		return null;
	}

}
