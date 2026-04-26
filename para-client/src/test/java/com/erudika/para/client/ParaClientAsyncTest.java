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
package com.erudika.para.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParaClientAsyncTest {

	@Test
	void asyncGuardClausesReturnDefaultValues() {
		try (ParaClient client = new ParaClient("app:async", "secret")) {
			assertNull(client.createAsync(null).join());
			assertNull(client.readAsync((String) null).join());
			assertTrue(client.listAsync("").join().isEmpty());
			assertTrue(client.findTermsAsync("test", null, true).join().isEmpty());
			assertFalse(client.voteUpAsync(null, "voter").join());
			assertNull(client.signInAsync(null, "token").join());
			assertNull(client.deleteAllAsync(Collections.emptyList()).join());
		}
	}

	@Test
	void concurrentAsyncRequestsShareSingleTokenRefresh() throws Exception {
		AtomicInteger refreshCalls = new AtomicInteger();
		AtomicInteger apiCalls = new AtomicInteger();
		List<String> authHeaders = Collections.synchronizedList(new ArrayList<>());
		CountDownLatch refreshStarted = new CountDownLatch(1);
		CountDownLatch releaseRefresh = new CountDownLatch(1);
		long now = System.currentTimeMillis();
		long refreshedExpiry = now + TimeUnit.MINUTES.toMillis(5);
		long refreshedRefreshTime = now + TimeUnit.MINUTES.toMillis(1);
		String refreshedToken = jwt(refreshedExpiry, refreshedRefreshTime);

		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		server.createContext("/jwt_auth", exchange -> {
			refreshCalls.incrementAndGet();
			refreshStarted.countDown();
			await(releaseRefresh);
			writeJson(exchange, "{\"user\":{},\"jwt\":{\"access_token\":\"" + refreshedToken
					+ "\",\"expires\":" + refreshedExpiry + ",\"refresh\":" + refreshedRefreshTime + "}}");
		});
		server.createContext("/v1/test", exchange -> {
			apiCalls.incrementAndGet();
			authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
			writeText(exchange, "ok");
		});
		server.start();

		try (ParaClient client = new ParaClient("app:async", "secret")) {
			client.setEndpoint("http://localhost:" + server.getAddress().getPort());
			client.setAccessToken(jwt(now + TimeUnit.MINUTES.toMillis(5), now - 1));

			CompletableFuture<String> first = client.invokeGetAsync("test", null, String.class);
			assertTrue(refreshStarted.await(2, TimeUnit.SECONDS));
			CompletableFuture<String> second = client.invokeGetAsync("test", null, String.class);
			releaseRefresh.countDown();

			assertEquals("ok", first.join());
			assertEquals("ok", second.join());
			assertEquals(1, refreshCalls.get());
			assertEquals(2, apiCalls.get());
			assertEquals(2, authHeaders.size());
			assertTrue(authHeaders.stream().allMatch(("Bearer " + refreshedToken)::equals));
			assertNotNull(client.getAccessToken());
			assertEquals(refreshedToken, client.getAccessToken());
		} finally {
			server.stop(0);
		}
	}

	private static String jwt(long expires, long refresh) {
		String payload = "{\"exp\":" + expires + ",\"refresh\":" + refresh + "}";
		String encoded = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
		return "header." + encoded + ".signature";
	}

	private static void await(CountDownLatch latch) {
		try {
			assertTrue(latch.await(2, TimeUnit.SECONDS));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new AssertionError(ex);
		}
	}

	private static void writeJson(HttpExchange exchange, String body) throws IOException {
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		write(exchange, body);
	}

	private static void writeText(HttpExchange exchange, String body) throws IOException {
		exchange.getResponseHeaders().add("Content-Type", "text/plain");
		write(exchange, body);
	}

	private static void write(HttpExchange exchange, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(200, bytes.length);
		try (exchange) {
			exchange.getResponseBody().write(bytes);
		}
	}
}
