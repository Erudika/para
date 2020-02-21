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

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

/**
 * The default IoT module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class IoTModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(IoTService.class).annotatedWith(Names.named("AWSIoTService")).to(AWSIoTService.class).asEagerSingleton();
		bind(IoTService.class).annotatedWith(Names.named("AzureIoTService")).to(AzureIoTService.class).asEagerSingleton();
		bind(IoTServiceFactory.class).to(IoTServiceFactoryImpl.class).asEagerSingleton();
	}

}
