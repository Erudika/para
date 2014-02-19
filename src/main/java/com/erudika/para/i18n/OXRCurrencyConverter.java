/*
 * Copyright 2013 Alex Bogdanovski <alex@erudika.com>.
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
 * You can reach the author at: https://github.com/albogdano
 */
package com.erudika.para.i18n;

import com.erudika.para.persistence.DAO;
import com.erudika.para.core.Sysprop;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.LoggerFactory;

/**
 * A converter that uses http://openexchangerates.org
 * @author Alex Bogdanovski <alex@erudika.com>
 */
@Singleton
public class OXRCurrencyConverter implements CurrencyConverter {

	private static final Logger logger = LoggerFactory.getLogger(OXRCurrencyConverter.class);
	private static final long REFRESH_AFTER = 24 * 60 * 60 * 1000; // 24 hours in ms
	private static final String SERVICE_URL = "http://openexchangerates.org/api/latest.json?app_id=".
			concat(Config.OPENX_API_KEY);

	private DAO dao;

	/**
	 * Default constructor
	 * @param dao
	 */
	@Inject
	public OXRCurrencyConverter(DAO dao) {
		this.dao = dao;
	}

	@Override
	public Double convertCurrency(Number amount, String from, String to) {
		if (amount == null || StringUtils.isBlank(from) || StringUtils.isBlank(to)) {
			return 0.0;
		}
		Sysprop s = dao.read(Config.FXRATES_KEY);
		if (s == null) {
			s = fetchFxRatesJSON();
		} else if ((Utils.timestamp() - s.getTimestamp()) > REFRESH_AFTER) {
			// lazy refresh fx rates
			Utils.asyncExecute(new Callable<Boolean>() {
				public Boolean call() throws Exception {
					fetchFxRatesJSON();
					return true;
				}
			});
		}

		double ratio = 1.0;

		if (s.hasProperty(from) && s.hasProperty(to)) {
			Double f = NumberUtils.toDouble(s.getProperty(from).toString(), 1.0);
			Double t = NumberUtils.toDouble(s.getProperty(to).toString(), 1.0);
			ratio = t / f;
		}
		return amount.doubleValue() * ratio;
	}

	@SuppressWarnings("unchecked")
	private Sysprop fetchFxRatesJSON() {
		Map<String, Object> map = new HashMap<String, Object>();
		Sysprop s = new Sysprop();
		ObjectReader reader = Utils.getJsonReader(Map.class);

		try {
			HttpClient http = getHttpClient(new DefaultHttpClient());
			HttpGet httpGet = new HttpGet(SERVICE_URL);
			HttpResponse res = http.execute(httpGet);
			HttpEntity entity = res.getEntity();

			if (entity != null && isJSON(entity.getContentType().getValue())) {
				JsonNode jsonNode = reader.readTree(entity.getContent());
				if (jsonNode != null) {
					JsonNode rates = jsonNode.get("rates");
					if (rates != null) {
						map = reader.treeToValue(rates, Map.class);
						s.setId(Config.FXRATES_KEY);
						s.setProperties(map);
//						s.addProperty("fetched", Utils.formatDate("dd MM yyyy HH:mm", Locale.UK));
						dao.create(s);
					}
				}
				EntityUtils.consume(entity);
			}
			logger.debug("Fetched rates from OpenExchange for {}.", new Date().toString());
		} catch (Exception e) {
			logger.error("TimerTask failed: {}", e);
		}
		return s;
	}

	private boolean isJSON(String type) {
		return StringUtils.startsWith(type, "application/json") ||
				StringUtils.startsWith(type, "application/javascript");
	}

	private static HttpClient getHttpClient(HttpClient base) {
		if (Config.IN_PRODUCTION) {
			return base;
		}
		try {
			SSLContext ctx = SSLContext.getInstance("TLS");
			X509TrustManager tm = new X509TrustManager() {
				public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException { }
				public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException { }
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}
			};
			X509HostnameVerifier verifier = new X509HostnameVerifier() {
				public void verify(String string, SSLSocket ssls) throws IOException { }
				public void verify(String string, X509Certificate xc) throws SSLException { }
				public void verify(String string, String[] strings, String[] strings1) throws SSLException { }
				public boolean verify(String string, SSLSession ssls) {
					return true;
				}
			};
			ctx.init(null, new TrustManager[]{tm}, null);
			SSLSocketFactory ssf = new SSLSocketFactory(ctx, verifier);
			ClientConnectionManager ccm = base.getConnectionManager();
			SchemeRegistry sr = ccm.getSchemeRegistry();
			sr.register(new Scheme("https", 443, ssf));
			return new DefaultHttpClient(ccm, base.getParams());
		} catch (Exception ex) {
			logger.error("error: {}", ex);
			return null;
		}
	}
}
