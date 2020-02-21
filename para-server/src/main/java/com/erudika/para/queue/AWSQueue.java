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

import com.erudika.para.utils.Config;
import java.util.Collections;
import java.util.List;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

/**
 * An implementation of the {@link Queue} interface using the AWS Simple Queue Service.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class AWSQueue implements Queue {

	private String name;
	private String url;

	/**
	 * No-args constructor.
	 */
	public AWSQueue() {
		this(Config.DEFAULT_QUEUE_NAME);
	}

	/**
	 * Default consturctor.
	 * This queue contains only messages in JSON format.
	 * @param name name of the queue
	 */
	public AWSQueue(String name) {
		setName(name);
	}

	@Override
	public void push(String msg) {
		AWSQueueUtils.pushMessages(getUrl(), Collections.singletonList(msg));
	}

	@Override
	public String pull() {
		List<String> msgs = AWSQueueUtils.pullMessages(getUrl(), 1);
		return msgs.isEmpty() ? "" : msgs.get(0);
	}

	@Override
	public void startPolling() {
		AWSQueueUtils.startPollingForMessages(getUrl());
	}

	@Override
	public void stopPolling() {
		AWSQueueUtils.stopPollingForMessages(getUrl());
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the queue URL on SQS.
	 * @return returns the URL of this queue
	 */
	public String getUrl() {
		if (StringUtils.isBlank(url) && !StringUtils.isBlank(name)) {
			this.url = AWSQueueUtils.createQueue(name);
		}
		return url;
	}

}
