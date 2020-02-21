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
package com.erudika.para.iot;

import com.erudika.para.Para;
import com.erudika.para.utils.Config;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Returns the appropriate IoT service class.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class IoTServiceFactoryImpl implements IoTServiceFactory {

	private static final boolean IOT_ENABLED = Config.getConfigBoolean("iot_enabled", false);

	static {
		if (IOT_ENABLED) {
			Para.addIOListener(new ThingIOListener());
		}
	}

	@Inject @Named("AWSIoTService")
	private IoTService awsIoTService;

	@Inject @Named("AzureIoTService")
	private IoTService azureIoTService;

	/**
	 * No-args constructor.
	 */
	public IoTServiceFactoryImpl() { }

	@Override
	public IoTService getIoTService(String name) {
		if (IOT_ENABLED) {
			if ("aws".equalsIgnoreCase(name) || "AWSIoTService".equalsIgnoreCase(name)) {
				return awsIoTService;
			}
			if ("azure".equalsIgnoreCase(name) || "AzureIoTService".equalsIgnoreCase(name)) {
				return azureIoTService;
			}
		}
		return null;
	}

}
