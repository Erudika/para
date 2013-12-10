/*
 * Copyright 2013 Alex Bogdanovski <albogdano@me.com>.
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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@Singleton
public class OXRConverter implements CurrencyConverter {

	private static final Logger logger = LoggerFactory.getLogger(OXRConverter.class);
	private static final long REFRESH_AFTER = 24 * 60 * 60 * 1000; // 24 hours in ms
	private static final String SERVICE_URL = "http://openexchangerates.org/api/latest.json?app_id=".
			concat(Config.OPENX_API_KEY);
	
	private DAO dao;
	
	@Inject
	public OXRConverter(DAO dao) {
		this.dao = dao;
	}
	
	public Double convertCurrency(Number amount, String from, String to){
		if(amount == null || StringUtils.isBlank(from) || StringUtils.isBlank(to)) return 0.0;
		Sysprop s = dao.read(Config.FXRATES_KEY);
		if(s == null){
			s = fetchFxRatesJSON();
		}else if((System.currentTimeMillis() - s.getTimestamp()) > REFRESH_AFTER){
			// lazy refresh fx rates
			Utils.asyncExecute(new Callable<Boolean>() {
				public Boolean call() throws Exception {
					fetchFxRatesJSON();
					return true;
				}
			});
		}
		
		double ratio = 1.0;
		
		if(s.hasProperty(from) && s.hasProperty(to)){
			Double f = NumberUtils.toDouble(s.getProperty(from).toString(), 1.0);
			Double t = NumberUtils.toDouble(s.getProperty(to).toString(), 1.0);
			ratio = t / f;
		}
		return amount.doubleValue() * ratio;
	}
	
	private Sysprop fetchFxRatesJSON(){
		Map<String, Object> map = new HashMap<String, Object>();
		Sysprop s = new Sysprop();
		ObjectMapper mapper = Utils.getInstance().getObjectMapper();
		
		try {
			final HttpClient http = new DefaultHttpClient();
			final HttpGet httpGet = new HttpGet(SERVICE_URL);
			HttpResponse res = http.execute(httpGet);
			HttpEntity entity = res.getEntity();
			
			if (entity != null) {
				JsonNode jsonNode = mapper.readTree(entity.getContent());
				if(jsonNode != null){
					JsonNode rates = jsonNode.get("rates");
					if(rates != null){
						map = mapper.treeToValue(rates, map.getClass());
						
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
	
}
