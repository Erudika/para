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

import com.erudika.para.Para;
import com.erudika.para.utils.Config;
import com.google.inject.AbstractModule;
import java.util.ServiceLoader;
import org.apache.commons.lang3.StringUtils;

/**
 * The default queue module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class QueueModule extends AbstractModule {

	protected void configure() {
		String selectedQueue = Config.getConfigParam("q", "");
		if (StringUtils.isBlank(selectedQueue)) {
			if ("embedded".equals(Config.ENVIRONMENT)) {
				bindToDefault();
			} else {
				bind(Queue.class).to(AWSQueue.class).asEagerSingleton();
			}
		} else {
			if ("sqs".equalsIgnoreCase(selectedQueue) ||
					AWSQueue.class.getSimpleName().equalsIgnoreCase(selectedQueue)) {
				bind(Queue.class).to(AWSQueue.class).asEagerSingleton();
			} else {
				Queue queuePlugin = loadExternalQueue(selectedQueue);
				if (queuePlugin != null) {
					bind(Queue.class).to(queuePlugin.getClass()).asEagerSingleton();
				} else {
					// default fallback - not implemented!
					bindToDefault();
				}
			}
		}
	}

	void bindToDefault() {
		bind(Queue.class).to(LocalQueue.class).asEagerSingleton();
	}

	/**
	 * Scans the classpath for Queue implementations, through the
	 * {@link ServiceLoader} mechanism and returns one.
	 * @param classSimpleName the name of the class name to look for and load
	 * @return a Queue instance if found, or null
	 */
	final Queue loadExternalQueue(String classSimpleName) {
		ServiceLoader<Queue> queueLoader = ServiceLoader.load(Queue.class, Para.getParaClassLoader());
		for (Queue queue : queueLoader) {
			if (queue != null && classSimpleName.equalsIgnoreCase(queue.getClass().getSimpleName())) {
				return queue;
			}
		}
		return null;
	}

}
