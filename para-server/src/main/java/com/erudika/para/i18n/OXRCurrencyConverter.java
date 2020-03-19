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
package com.erudika.para.i18n;

import com.erudika.para.Para;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.persistence.DAO;
import com.erudika.para.core.Sysprop;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.LoggerFactory;

/**
 * A converter that uses http://openexchangerates.org.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class OXRCurrencyConverter implements CurrencyConverter {

	private static final Logger logger = LoggerFactory.getLogger(OXRCurrencyConverter.class);
	private final CloseableHttpClient httpClient;
	private static final String FXRATES_KEY = "fxrates";
	private static final long REFRESH_AFTER = (long) 24 * 60 * 60 * 1000; // 24 hours in ms
	private static final String SERVICE_URL = "http://openexchangerates.org/api/latest.json?app_id=".
			concat(Config.getConfigParam("openx_api_key", ""));

	private DAO dao;

	/**
	 * Default constructor.
	 * @param dao dao
	 */
	@Inject
	public OXRCurrencyConverter(DAO dao) {
		this.dao = dao;
		int timeout = 30 * 1000;
		this.httpClient = HttpClientBuilder.create().
				setConnectionReuseStrategy(new NoConnectionReuseStrategy()).
				setDefaultRequestConfig(RequestConfig.custom().
						setConnectTimeout(timeout).
						setConnectionRequestTimeout(timeout).
						setSocketTimeout(timeout).
						build()).
				build();
	}

	@Override
	public Double convertCurrency(Number amount, String from, String to) {
		if (amount == null || StringUtils.isBlank(from) || StringUtils.isBlank(to)) {
			return 0.0;
		}
		Sysprop s = dao.read(FXRATES_KEY);
		if (s == null) {
			s = fetchFxRatesJSON();
		} else if ((Utils.timestamp() - s.getTimestamp()) > REFRESH_AFTER) {
			// lazy refresh fx rates
			Para.asyncExecute(new Runnable() {
				public void run() {
					fetchFxRatesJSON();
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
		Map<String, Object> map;
		Sysprop s = new Sysprop();
		ObjectReader reader = ParaObjectUtils.getJsonReader(Map.class);

		try {
			HttpGet httpGet = new HttpGet(SERVICE_URL);
			try (CloseableHttpResponse res = httpClient.execute(httpGet)) {
				HttpEntity entity = res.getEntity();
				if (entity != null && Utils.isJsonType(entity.getContentType().getValue())) {
					JsonNode jsonNode = reader.readTree(entity.getContent());
					if (jsonNode != null) {
						JsonNode rates = jsonNode.get("rates");
						if (rates != null) {
							map = reader.treeToValue(rates, Map.class);
							s.setId(FXRATES_KEY);
							s.setProperties(map);
							dao.create(s);
						}
					}
					EntityUtils.consume(entity);
				}
			}
			logger.debug("Fetched rates from OpenExchange for {}.", new Date().toString());
		} catch (Exception e) {
			logger.error("TimerTask failed: ", e);
		}
		return s;
	}

}
