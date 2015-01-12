/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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

import java.text.NumberFormat;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper utility class for currency operations.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class CurrencyUtils {

	private static final Logger logger = LoggerFactory.getLogger(CurrencyUtils.class);

	private static final Map<String, Locale> COUNTRY_TO_LOCALE_MAP = new HashMap<String, Locale>();
	private static final Map<String, Locale> CURRENCY_TO_LOCALE_MAP = new HashMap<String, Locale>();
	private static final Map<String, String> CURRENCIES_MAP = new TreeMap<String, String>();

	private static CurrencyUtils instance;

	private CurrencyUtils() {
		Locale[] locales = Locale.getAvailableLocales();
		try {
			for (Locale l : locales) {
				if (!StringUtils.isBlank(l.getCountry())) {
					COUNTRY_TO_LOCALE_MAP.put(l.getCountry(), l);
					Currency c = Currency.getInstance(l);
					if (c != null) {
						CURRENCY_TO_LOCALE_MAP.put(c.getCurrencyCode(), l);
						CURRENCIES_MAP.put(c.getCurrencyCode(), getCurrencyName(c.getCurrencyCode(),
								Locale.US).concat(" ").concat(c.getSymbol(l)));
					}
				}
			}
			// overwrite main locales
			CURRENCY_TO_LOCALE_MAP.put("USD", Locale.US);
			CURRENCY_TO_LOCALE_MAP.put("EUR", Locale.FRANCE);
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	/**
	 * Returns an instance of this class
	 * @return an instance
	 */
	public static CurrencyUtils getInstance() {
		if (instance == null) {
			instance = new CurrencyUtils();
		}
		return instance;
	}

	/**
	 * Formats a price for its specific locale, depending on the currency code
	 * @param price the amount
	 * @param cur the 3-letter currency code
	 * @return a formatted price with its currency symbol
	 */
	public String formatPrice(Double price, String cur) {
		String formatted = "";
		if (price != null) {
			Locale locale = CURRENCY_TO_LOCALE_MAP.get(cur);
			NumberFormat f = (locale == null) ? NumberFormat.getCurrencyInstance(Locale.US) :
					NumberFormat.getCurrencyInstance(locale);

			formatted = f.format(price);
		}
		return formatted;
	}

	/**
	 * Returns the full name of the currency in the language of the given locale.
	 * Defaults to English.
	 * @param cur the 3-letter currency code
	 * @param locale the locale
	 * @return the currency name or "" if the currency is unknown
	 */
	public String getCurrencyName(String cur, Locale locale) {
		if (cur != null && CURRENCY_TO_LOCALE_MAP.containsKey(cur.toUpperCase())) {
			return Currency.getInstance(cur.toUpperCase()).getDisplayName((locale == null ? Locale.US : locale));
		} else {
			return "";
		}
	}

	/**
	 * Returns the locale for a given country code.
	 * @param countryCode the 2-letter country code
	 * @return a locale or null if countryCode is null
	 */
	public Locale getLocaleForCountry(String countryCode) {
		if (countryCode == null) {
			return null;
		}
		return COUNTRY_TO_LOCALE_MAP.get(countryCode.toUpperCase());
	}

	/**
	 * Returns the currency instance for a given currency code
	 * @param cur the 3-letter currency code
	 * @return the currency
	 */
	public Currency getCurrency(String cur) {
		Currency currency = Currency.getInstance("EUR");
		if (StringUtils.isBlank(cur) || cur.length() != 3) {
			return currency;
		}
		try {
			currency = Currency.getInstance(cur.toUpperCase());
		} catch (Exception e) {
			logger.error(null, e);
		}
		return currency;
	}

	/**
	 * Returns a map of all available currencies in the form:
	 * currency code - full currency name and symbol
	 * @return a map of known currencies
	 */
	public Map<String, String> getCurrenciesMap() {
		return CURRENCIES_MAP;
	}

	/**
	 * Validate the currency code.
	 * @param cur a 3-letter curency code
	 * @return true if the code corresponds to a valid currency
	 */
	public boolean isValidCurrency(String cur) {
		return cur != null && CURRENCIES_MAP.containsKey(cur.toUpperCase());
	}
}
