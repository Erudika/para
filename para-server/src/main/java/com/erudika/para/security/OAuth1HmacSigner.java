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
package com.erudika.para.security;

import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Signs OAuth 1.0a requests using HMAC-SHA1.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class OAuth1HmacSigner {

	private static final Logger logger = LoggerFactory.getLogger(OAuth1HmacSigner.class);

	private OAuth1HmacSigner() { }

	/**
	 * Sign a request and return the "Authorization" header.
	 *
	 * @param httpMethod the HTTP method
	 * @param url the request URL
	 * @param params the parameters map
	 * @param apiKey the API key
	 * @param apiSecret the API secret
	 * @param oauthToken the token
	 * @param tokenSecret token secret
	 * @return the signed auth header
	 */
	public static String sign(String httpMethod, String url, Map<String, String[]> params,
			String apiKey, String apiSecret, String oauthToken, String tokenSecret) {
		try {
			if (httpMethod != null && url != null && !url.trim().isEmpty() && params != null && apiSecret != null) {
				Map<String, String[]> paramMap = new TreeMap<>(params);
				String keyString = percentEncode(apiSecret) + "&" + percentEncode(tokenSecret);
				byte[] keyBytes = keyString.getBytes(Config.DEFAULT_ENCODING);

				SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA1");
				Mac mac = Mac.getInstance("HmacSHA1");
				mac.init(key);

				addRequiredParameters(paramMap, apiKey, oauthToken);

				String sbs = httpMethod.toUpperCase() + "&" + percentEncode(normalizeRequestUrl(url))
						+ "&" + percentEncode(normalizeRequestParameters(paramMap));
				logger.debug("Oatuh1 base string: {}", sbs);

				byte[] text = sbs.getBytes(Config.DEFAULT_ENCODING);
				String sig = Utils.base64enc(mac.doFinal(text)).trim();
				logger.debug("Oauth1 Signature: {}", sig);

				StringBuilder sb = new StringBuilder();
				sb.append("OAuth ");
				// add the realm parameter, if any
				if (paramMap.containsKey("realm")) {
					String val = paramMap.get("realm")[0];
					sb.append("realm=\"".concat(val).concat("\""));
					sb.append(", ");
				}

				Map<String, SortedSet<String>> oauthParams = getOAuthParameters(paramMap);
				TreeSet<String> set = new TreeSet<>();
				set.add(percentEncode(sig));
				oauthParams.put("oauth_signature", set);

				Iterator<String> iter = oauthParams.keySet().iterator();
				while (iter.hasNext()) {
					String param = iter.next();
					SortedSet<String> valSet = oauthParams.get(param);
					String value = (valSet == null || valSet.isEmpty()) ? null : valSet.first();
					String headerElem = (value == null) ? null : param + "=\"" + value + "\"";
					sb.append(headerElem);
					if (iter.hasNext()) {
						sb.append(", ");
					}
				}
				String header = sb.toString();
				logger.debug("OAuth1 signed header: {}", header);
				return header;
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
		return null;
	}

	private static String normalizeRequestUrl(String url) throws URISyntaxException {
		if (url == null || url.trim().isEmpty()) {
			return null;
		}
		URI uri = new URI(url);
		String scheme = uri.getScheme().toLowerCase();
		String authority = uri.getAuthority().toLowerCase();
		boolean dropPort = (scheme.equals("http") && uri.getPort() == 80)
				|| (scheme.equals("https") && uri.getPort() == 443);
		if (dropPort) {
			// find the last : in the authority
			int index = authority.lastIndexOf(':');
			if (index >= 0) {
				authority = authority.substring(0, index);
			}
		}
		String path = uri.getRawPath();
		if (path == null || path.length() <= 0) {
			path = "/"; // conforms to RFC 2616 section 3.2.2
		}
		// we know that there is no query and no fragment here.
		return scheme + "://" + authority + path;
	}

	private static String normalizeRequestParameters(Map<String, String[]> params) throws IOException {
		if (params == null) {
			return "";
		}

		List<ComparableParameter> paramz = new ArrayList<>(params.size());
		for (Map.Entry<String, String[]> param : params.entrySet()) {
			if (!"oauth_signature".equals(param.getKey()) || "realm".equals(param.getKey())) {
				for (String val : param.getValue()) {
					paramz.add(new ComparableParameter(param.getKey(), val));
				}
			}
		}
		Collections.sort(paramz);

		StringBuilder sb = new StringBuilder();
		Iterator<ComparableParameter> iter = paramz.iterator();
		boolean first = true;
		while (iter.hasNext()) {
			ComparableParameter p = iter.next();
			String param = p.key;
			String value = p.value;
			if (!first) {
				sb.append("&");
			}
			if (value == null) {
				sb.append(param.concat("="));
			} else {
				sb.append(param.concat("=").concat(value));
			}
			first = false;
		}
		String s = sb.toString();
		return s;
	}

	private static String percentEncode(String s) {
		if (s == null) {
			return "";
		}
		try {
			return URLEncoder.encode(s, Config.DEFAULT_ENCODING)
					.replaceAll("\\+", "%20").replaceAll("\\*", "%2A")
					.replaceAll("%7E", "~");
		} catch (UnsupportedEncodingException ex) {
			logger.error(ex.getMessage(), ex);
		}
		return "";
	}

	private static Map<String, SortedSet<String>> getOAuthParameters(Map<String, String[]> params) {
		TreeMap<String, SortedSet<String>> oauthParams = new TreeMap<>();
		for (Entry<String, String[]> param : params.entrySet()) {
			String key = param.getKey();
			if ((key.startsWith("oauth_") || key.startsWith("x_oauth_")) && param.getValue() != null) {
				TreeSet<String> set = new TreeSet<>();
				for (String val : param.getValue()) {
					if (val != null) {
						set.add(val);
					}
				}
				if (!set.isEmpty()) {
					oauthParams.put(key, set);
				}
			}
		}
		return oauthParams;
	}

	private static void addRequiredParameters(Map<String, String[]> pMap, String apiKey, String oauthToken) {
		if (pMap != null) {
			if (oauthToken != null) {
				pMap.put("oauth_token", new String[]{oauthToken});
			}
			if (pMap.get("oauth_consumer_key") == null) {
				pMap.put("oauth_consumer_key", new String[]{apiKey});
			}
			if (pMap.get("oauth_signature_method") == null) {
				pMap.put("oauth_signature_method", new String[]{"HMAC-SHA1"});
			}
			if (pMap.get("oauth_timestamp") == null) {
				pMap.put("oauth_timestamp", new String[]{Long.toString(System.currentTimeMillis() / 1000)});
			}
			if (pMap.get("oauth_nonce") == null) {
				String nonce = Utils.stripAndTrim(Utils.generateSecurityToken(32));
				pMap.put("oauth_nonce", new String[]{nonce.length() > 32 ? nonce.substring(0, 32) : nonce});
			}
			if (pMap.get("oauth_version") == null) {
				pMap.put("oauth_version", new String[]{"1.0"});
			}
		}
	}

	/**
	 * Comparable parameter.
	 */
	private static class ComparableParameter implements Comparable<ComparableParameter> {

		ComparableParameter(String key, String value) {
			this.value = value;
			String n = toString(key);
			String v = toString(value);
			this.key = percentEncode(n);
			this.encodedKey = percentEncode(n) + " " + percentEncode(v);
            // ' ' is used because it comes before any character
			// that can appear in a percentEncoded string.
		}

		private final String value;
		private final String key;
		private final String encodedKey;

		private static String toString(Object from) {
			return (from == null) ? null : from.toString();
		}

		public int compareTo(ComparableParameter that) {
			return this.encodedKey.compareTo(that.encodedKey);
		}

		@Override
		public int hashCode() {
			int hash = 3;
			hash = 19 * hash + Objects.hashCode(this.value);
			hash = 19 * hash + Objects.hashCode(this.key);
			hash = 19 * hash + Objects.hashCode(this.encodedKey);
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final ComparableParameter other = (ComparableParameter) obj;
			if (!Objects.equals(this.value, other.value)) {
				return false;
			}
			if (!Objects.equals(this.key, other.key)) {
				return false;
			}
			if (!Objects.equals(this.encodedKey, other.encodedKey)) {
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			return encodedKey;
		}
	}
}
