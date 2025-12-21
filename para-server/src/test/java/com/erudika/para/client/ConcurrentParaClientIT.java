/*
 * Copyright 2013-2026 Erudika. http://erudika.com
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
package com.erudika.para.client;

import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.utils.Para;
import com.erudika.para.server.ParaServer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ConcurrentParaClientIT {

	private static final Logger logger = LoggerFactory.getLogger(ConcurrentParaClientIT.class);
	private static final String ROOT_APP_NAME = "para-concurrent-test";
	private static final String TEST_APP_NAME = "para-concurrent-test-app";
	private static final String catsType = "cat";
	private static final int TOTAL_THREADS = 20;
	private static final int BATCH_SIZE = 1000;
	private static final int TOTAL = TOTAL_THREADS * BATCH_SIZE;
	private static ParaClient pc;

	@BeforeAll
	public static void setUpClass() throws InterruptedException, IOException {
		System.setProperty("para.env", "embedded");
		System.setProperty("para.print_logo", "false");
		System.setProperty("para.app_name", ROOT_APP_NAME);
		System.setProperty("para.search", "LuceneSearch");
		System.setProperty("para.port", "8181");
		String endpoint = "http://localhost:8181";
		ParaServer.main(new String[0]);

		App rootApp = Para.getDAO().read(App.id(ROOT_APP_NAME));
		if (rootApp == null) {
			rootApp = new App(ROOT_APP_NAME);
			rootApp.setName(ROOT_APP_NAME);
			rootApp.setSharingIndex(false);
		} else {
			rootApp.resetSecret();
		}
		rootApp.create();

		Map<String, String> creds = Para.newApp(TEST_APP_NAME, "Child app with routing", false, false);

		pc = new ParaClient(App.id(TEST_APP_NAME), creds.get("secretKey"));
		pc.setEndpoint(endpoint);
		assertNotNull(pc.me());
	}

	@Test
	public void testBatchWrite() throws InterruptedException {
		ExecutorService executorService = Executors.newFixedThreadPool(TOTAL_THREADS);
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (int j = 0; j < TOTAL_THREADS; j++) {
			futures.add(CompletableFuture.runAsync(() -> {
				Deque<ParaObject> cats = new ConcurrentLinkedDeque<ParaObject>();
				for (int i = 0; i < BATCH_SIZE; i++) {
					Sysprop s = new Sysprop();
					s.setType(catsType);
					s.addProperty("createTime", System.currentTimeMillis());
					cats.add(s);
				}
				List<ParaObject> created = pc.createAll(cats.stream().collect(Collectors.toList()));
				logger.info("Created {} objects from thread {}.", created.size(), Thread.currentThread().getName());
			}, executorService));
		}
		CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
		executorService.shutdown();
	}

	@AfterEach
	public void tearDown() {
		int total = pc.getCount(catsType).intValue();
		assertEquals(TOTAL, total);
		logger.info("Total concurrently created objects: {}", total);
	}

	@AfterAll
	public static void tearDownClass() throws InterruptedException {
		System.setProperty("server.port", "8080");
		new App(TEST_APP_NAME).delete();
		Para.destroy();
		pc.close();
	}
}
