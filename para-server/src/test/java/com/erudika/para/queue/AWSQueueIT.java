/*
 * Copyright 2013-2019 Erudika. https://erudika.com
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

import static com.erudika.para.queue.QueueTest.q;
import java.util.ArrayList;
import java.util.List;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AWSQueueIT extends QueueTest {

	private static SQSRestServer sqsServer;

	static {
		System.setProperty("para.aws_access_key", "x");
		System.setProperty("para.aws_secret_key", "x");
	}

	@BeforeClass
	public static void setUpClass() throws InterruptedException {
		sqsServer = SQSRestServerBuilder.start();
		Thread.sleep(1000);
		q = new AWSQueue("testq");
	}

	@AfterClass
	public static void tearDownClass() {
		sqsServer.stopAndWait();
	}

	@Test
	public void testBatchSend() {
		AWSQueue qu = new AWSQueue("testq2");
		int n = 15;
		List<String> list = new ArrayList<>();
		for (int i = 1; i <= n; i++) {
			list.add("{\"test" + i + "\": " + i + "23 }");
		}
		AWSQueueUtils.pushMessages(qu.getUrl(), list);
		List<String> result = AWSQueueUtils.pullMessages(qu.getUrl(), n);
		assertNotNull(result);
		assertFalse(result.isEmpty());
		assertEquals(n, result.size());
		assertEquals("", qu.pull());
	}

}