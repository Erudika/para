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

import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class CurrencyUtils {

	private Map<String, Locale> COUNTRY_TO_LOCALE_MAP = new HashMap<String, Locale>();
	private Map<String, Locale> CURRENCY_TO_LOCALE_MAP = new HashMap<String, Locale>();
	private Map<String, String> CURRENCIES_MAP = new TreeMap<String, String>();

	private static CurrencyUtils instance;
	
	private CurrencyUtils() {
		Locale[] locales = Locale.getAvailableLocales();
		for (Locale l : locales) {
			COUNTRY_TO_LOCALE_MAP.put(l.getCountry(), l); 
            try {
				Currency c = Currency.getInstance(l);
				if(c != null){
					CURRENCY_TO_LOCALE_MAP.put(c.getCurrencyCode(), l);
					CURRENCIES_MAP.put(c.getCurrencyCode(), 
							getCurrencyName(c.getCurrencyCode(), Locale.US).concat(" ").concat(c.getSymbol(l)));
				}
            } catch (Exception e) {}
		}
	}
	
	public static CurrencyUtils getInstance(){
		if(instance == null){
			instance = new CurrencyUtils();
		}
		return instance;
	}
	
	public String formatPrice(Double price, String cur){
		String formatted = "";
		if(price != null){
			Locale locale = CURRENCY_TO_LOCALE_MAP.get(cur);
			NumberFormat f = (locale == null) ? NumberFormat.getCurrencyInstance(Locale.US) : 
					NumberFormat.getCurrencyInstance(locale);
			
			formatted = f.format(price);
		}
		return formatted;
	}
	
	public String getCurrencyName(String cur, Locale locale){
		if(CURRENCY_TO_LOCALE_MAP.containsKey(cur.toUpperCase())){
			return com.ibm.icu.util.Currency.getInstance(cur).getName((locale == null ? Locale.US : locale), 1, new boolean[1]);
		}else{
			return "";
		}
	}
	
	public Locale getLocaleForCountry(String countryCode){
		return COUNTRY_TO_LOCALE_MAP.get(countryCode);
	}
	
	public Currency getCurrency(String cur){
		if(StringUtils.isBlank(cur)) return null;
		Currency currency = null;
		try {
			currency = Currency.getInstance(cur);
		} catch (Exception e) {}
		return currency;
	}
	
	public Locale getLocaleForCurrency(String cur){
		return (StringUtils.isBlank(cur) || !CURRENCY_TO_LOCALE_MAP.containsKey(cur)) ? 
				Locale.US : CURRENCY_TO_LOCALE_MAP.get(cur);
	}

	public Map<String, String> getCurrenciesMap(){
		return CURRENCIES_MAP;
	}
	
	public boolean isValidCurrency(String cur){
		return cur != null && CURRENCIES_MAP.containsKey(cur);
	}
}
