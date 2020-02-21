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
package com.erudika.para.utils;

import com.erudika.para.security.OAuth1HmacSigner;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class OAuth1HmacSignerTest {

	@BeforeClass
	public static void setUpClass() {
	}

	@AfterClass
	public static void tearDownClass() {
	}

	@Before
	public void setUp() {
	}

	@After
	public void tearDown() {
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSign() {
		Map<String, String[]> params1 = new TreeMap<>();
		params1.put("oauth_callback", new String[] {Utils.urlEncode("http://localhost/sign-in-with-twitter/")});
		params1.put("oauth_nonce", new String[] {"ea9ec8429b68d6b77cd5600adbbb0456"});
		params1.put("oauth_timestamp", new String[] {"1318467427"});

		// Tests the signature for the RFC 5849 POST reference sample + Errata ID 2550
//		Map<String, String[]> params2 = new TreeMap<String, String[]>();
//		params2.put("oauth_nonce", new String[] {"7d8f3e4a"});
//		params2.put("oauth_timestamp", new String[] {"137131201"});
//		params2.put("b5", new String[] {"=%3D"});
//		params2.put("a3", new String[] {"a", "2 q"});
//		params2.put("c@", new String[] {""});
//		params2.put("c2", new String[] {""});
//		params2.put("a2", new String[] {"r b"});

		Map<String, String[]> params3 = new TreeMap<>();
		params3.put("oauth_nonce", new String[] {"kllo9940pd9333jh"});
		params3.put("oauth_timestamp", new String[] {"1191242096"});
		params3.put("file", new String[] {"vacation.jpg"});
		params3.put("size", new String[] {"original"});


		String result1 = OAuth1HmacSigner.sign("POST", "https://api.twitter.com/oauth/request_token",
				params1, "cChZNFj6T5R0TigYB9yd1w", "L8qq9PZyRg6ieKGEKhZolGC0vJWLw8iEJ88DRdyOg", null, null);
//		String result2 = OAuth1HmacSigner.sign("POST", "http://example.com/request",
//				params2, "9djdj82h48djs9d2", "j49sk3j29djd", "kkk9d7dh3k39sjv7", "dh893hdasih9");
		String result3 = OAuth1HmacSigner.sign("GET", "http://photos.example.net/photos",
				params3, "dpf43f3p2l4k3l03", "kd94hf93k423kf44", "nnch734d00sl2jdk", "pfkkdhi9sl3r4s00");

		String expected1 = "OAuth oauth_callback=\"http%3A%2F%2Flocalhost%2Fsign-in-with-twitter%2F\", "
							+ "oauth_consumer_key=\"cChZNFj6T5R0TigYB9yd1w\", "
							+ "oauth_nonce=\"ea9ec8429b68d6b77cd5600adbbb0456\", "
							+ "oauth_signature=\"F1Li3tvehgcraF8DMJ7OyxO4w9Y%3D\", "
							+ "oauth_signature_method=\"HMAC-SHA1\", "
							+ "oauth_timestamp=\"1318467427\", "
							+ "oauth_version=\"1.0\"";

//		String expected2 = "OAuth oauth_consumer_key=\"9djdj82h48djs9d2\", "
//							+ "oauth_nonce=\"7d8f3e4a\", "
//							+ "oauth_signature=\"OB33pYjWAnf%2BxtOHN4Gmbdil168%3D\", "
//							+ "oauth_signature_method=\"HMAC-SHA1\", "
//							+ "oauth_timestamp=\"137131201\", "
//							+ "oauth_token=\"kkk9d7dh3k39sjv7\", "
//							+ "oauth_version=\"1.0\"";

		String expected3 = "OAuth oauth_consumer_key=\"dpf43f3p2l4k3l03\", "
							+ "oauth_nonce=\"kllo9940pd9333jh\", "
							+ "oauth_signature=\"tR3%2BTy81lMeYAr%2FFid0kMTYa%2FWM%3D\", "
							+ "oauth_signature_method=\"HMAC-SHA1\", "
							+ "oauth_timestamp=\"1191242096\", "
							+ "oauth_token=\"nnch734d00sl2jdk\", "
							+ "oauth_version=\"1.0\"";


		assertNull(OAuth1HmacSigner.sign(null, null, null, null, null, null, null));
		assertNull(OAuth1HmacSigner.sign("", "", Collections.EMPTY_MAP, "", "", "", ""));
		assertEquals(expected1, result1);
//		assertEquals(expected2, result2);
		assertEquals(expected3, result3);
	}

}
