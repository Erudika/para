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

import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class MockQueue implements Queue {

	private ConcurrentLinkedQueue<String> q = new ConcurrentLinkedQueue<>();

	private String name;

	/**
	 * Default constructor.
	 */
	public MockQueue() {
		this("queue");
	}

	/**
	 * @param name name
	 */
	public MockQueue(String name) {
		this.name = name;
	}

	@Override
	public String pull() {
		String s = q.poll();
		return StringUtils.isBlank(s) ? "" : s;
	}

	@Override
	public void push(String task) {
		if (!StringUtils.isBlank(task)) {
			q.add(task);
		}
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void startPolling() {
		LoggerFactory.getLogger(MockQueue.class).info("Started polling (not really!)...");
	}

	@Override
	public void stopPolling() {
		LoggerFactory.getLogger(MockQueue.class).info("Stopped polling (not really!)...");
	}

}
