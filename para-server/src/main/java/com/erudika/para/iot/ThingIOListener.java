/*
 * Copyright 2013-2016 Erudika. http://erudika.com
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
package com.erudika.para.iot;

import com.erudika.para.IOListener;
import com.erudika.para.Para;
import com.erudika.para.core.Thing;
import com.erudika.para.core.utils.CoreUtils;
import java.lang.reflect.Method;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Listens for I/O events and updates Things.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ThingIOListener implements IOListener {

	@Override
	public void onInvoke(Method method, Object result) {
		if (method != null && method.getName().equalsIgnoreCase("read")) {
			if (result instanceof Thing) {
				final Thing t = (Thing) result;
				final IoTService iot = CoreUtils.getInstance().getIotFactory().getIoTService(t.getServiceBroker());
				if (iot != null && !StringUtils.isBlank(t.getName())) {
					Para.asyncExecute(new Runnable() {
						@Override
						public void run() {
							Map<String, Object> state = iot.readThing(t.getName());
							if (!t.getDeviceState().equals(state)) {
								t.update();
							}
						}
					});
				}
			}
		}
	}

}
