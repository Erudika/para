/*
 * Copyright 2013-2026 Erudika. https://erudika.com
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

import com.erudika.para.core.queue.Queue;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Para;
import java.util.ServiceLoader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The default queue module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Configuration
public class QueueModule {

	@Bean
	public Queue getQueue() {
		Queue queue;
		String selectedQueue = Para.getConfig().queuePlugin();
		if (StringUtils.isBlank(selectedQueue)) {
			queue = bindToDefault();
		} else {
			Queue queuePlugin = loadExternalQueue(selectedQueue);
			if (queuePlugin != null) {
				queue = queuePlugin;
			} else {
				// default fallback - not implemented!
				queue = bindToDefault();
			}
		}
		CoreUtils.getInstance().setQueue(queue);
		return Para.getQueue();
	}

	Queue bindToDefault() {
		return new LocalQueue();
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
