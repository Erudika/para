/*
 * Copyright 2013-2022 Erudika. https://erudika.com
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Disabled
public abstract class QueueTest {

	protected static Queue q;

	@Test
	public void testPushPull() throws InterruptedException {
		q.push(null);
		assertEquals("", q.pull());
		String msg1 = "{\"test1\": 123 }";
		String msg2 = "{\"test2\": 123 }";
		String msg3 = "{\"test3\": 123 }";

		q.push(msg1);
		Thread.sleep(500);
		q.push(msg2);
		Thread.sleep(500);
		q.push(msg3);
		Thread.sleep(500);
		// ORDER IS NOT GUARANTEED!
		assertEquals(msg1, q.pull());
		assertEquals(msg2, q.pull());
		assertEquals(msg3, q.pull());
		assertEquals("", q.pull());
	}

}