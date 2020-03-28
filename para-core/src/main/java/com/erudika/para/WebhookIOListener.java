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
package com.erudika.para;

import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Webhook;
import com.erudika.para.utils.Utils;
import java.lang.reflect.Method;
import java.util.List;
import javax.inject.Singleton;

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
					Webhook.sendEventPayloadToQueue(appid, method.getName(), true, paraObjects);
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
