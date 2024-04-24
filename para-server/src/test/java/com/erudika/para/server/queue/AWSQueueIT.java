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

import static com.erudika.para.server.queue.QueueTest.q;
import java.util.ArrayList;
import java.util.List;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AWSQueueIT extends QueueTest {

	private static SQSRestServer sqsServer;

	@BeforeAll
	public static void setUpClass() throws InterruptedException {
		sqsServer = SQSRestServerBuilder.start();
		Thread.sleep(1000);
		q = new AWSQueue("testq");
	}

	@AfterAll
	public static void tearDownClass() {
		sqsServer.stopAndWait();
	}

	@Test
	public void testBatchSend() throws InterruptedException {
		AWSQueue qu = new AWSQueue("testq2");
		int n = 15;
		List<String> list = new ArrayList<>();
		for (int i = 1; i <= n; i++) {
			list.add("{\"test" + i + "\": " + i + "23 }");
		}
		AWSQueueUtils.pushMessages(qu.getUrl(), list);
		Thread.sleep(1000); // AWSQueue push is async
		List<String> result = AWSQueueUtils.pullMessages(qu.getUrl(), n);
		assertNotNull(result);
		assertFalse(result.isEmpty());
		assertEquals(n, result.size());
		assertEquals("", qu.pull());
	}

}