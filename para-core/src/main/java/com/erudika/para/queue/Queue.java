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

/**
 * The core queue interface. Pushes messages to a queue and pulls them for processing.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public interface Queue {

	/**
	 * Pulls one or more messages from a queue.
	 * @return the message
	 */
	String pull();

	/**
	 * Pushes a message to a queue.
	 * @param task the message
	 */
	void push(String task);

	/**
	 * Returns the name of the queue.
	 * @return the queue name
	 */
	String getName();

	/**
	 * Sets the name of the queue.
	 * @param name a name
	 */
	void setName(String name);

	/**
	 * Start a new async task which polls the queue and automatically
	 * processes the messages. This was inspired by Elasticsearch rivers.
	 */
	void startPolling();

	/**
	 * Stops the polling async task manually.
	 * This method should always be called  on shutdown.
	 */
	void stopPolling();
}
