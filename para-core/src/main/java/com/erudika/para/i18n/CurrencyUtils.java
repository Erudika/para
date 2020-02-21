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
public enum CurrencyUtils {

	/**
	 * Singleton.
	 */
	INSTANCE {
		private final transient Logger logger = LoggerFactory.getLogger(CurrencyUtils.class);

		private final Map<String, Locale> countryToLocaleMap = new HashMap<String, Locale>();
		private final Map<String, Locale> currencyToLocaleMap = new HashMap<String, Locale>();
		private final Map<String, String> currenciesMap = new TreeMap<String, String>();

		{
			Locale[] locales = Locale.getAvailableLocales();
			try {
				for (Locale l : locales) {
					if (!StringUtils.isBlank(l.getCountry())) {
						countryToLocaleMap.put(l.getCountry(), l);
						Currency c = getCurrency(l);
						if (c != null) {
							currencyToLocaleMap.put(c.getCurrencyCode(), l);
							currenciesMap.put(c.getCurrencyCode(), getCurrencyName(c.getCurrencyCode(),
									Locale.US).concat(" ").concat(c.getSymbol(l)));
						}
					}
				}
				// overwrite main locales
				currencyToLocaleMap.put("USD", Locale.US);
				currencyToLocaleMap.put("EUR", Locale.FRANCE);
			} catch (Exception e) {
				logger.error(null, e);
			}
		}

		@Override
		public String formatPrice(Double price, String cur) {
			String formatted = "";
			if (price != null) {
				Locale locale = currencyToLocaleMap.get(cur);
				NumberFormat f = (locale == null) ? NumberFormat.getCurrencyInstance(Locale.US) :
						NumberFormat.getCurrencyInstance(locale);
				f.setMinimumFractionDigits(2);
				formatted = f.format(price);
			}
			return formatted;
		}

		@Override
		public String getCurrencyName(String cur, Locale locale) {
			if (cur != null && currencyToLocaleMap.containsKey(cur.toUpperCase())) {
				return Currency.getInstance(cur.toUpperCase()).getDisplayName((locale == null ? Locale.US : locale));
			} else {
				return "";
			}
		}

		@Override
		public Locale getLocaleForCountry(String countryCode) {
			if (countryCode == null) {
				return null;
			}
			return countryToLocaleMap.get(countryCode.toUpperCase());
		}

		@Override
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

		@Override
		public Currency getCurrency(Locale loc) {
			if (loc != null) {
				try {
					return Currency.getInstance(loc);
				} catch (Exception e) {
					logger.debug(null, e);
				}
			}
			return null;
		}

		@Override
		public Map<String, String> getCurrenciesMap() {
			return currenciesMap;
		}

		@Override
		public boolean isValidCurrency(String cur) {
			return cur != null && currenciesMap.containsKey(cur.toUpperCase());
		}
	};

	/**
	 * Formats a price for its specific locale, depending on the currency code.
	 *
	 * @param price the amount
	 * @param cur the 3-letter currency code
	 * @return a formatted price with its currency symbol
	 */
	public abstract String formatPrice(Double price, String cur);

	/**
	 * Returns the full name of the currency in the language of the given locale. Defaults to English.
	 *
	 * @param cur the 3-letter currency code
	 * @param locale the locale
	 * @return the currency name or "" if the currency is unknown
	 */
	public abstract String getCurrencyName(String cur, Locale locale);

	/**
	 * Returns the locale for a given country code.
	 *
	 * @param countryCode the 2-letter country code
	 * @return a locale or null if countryCode is null
	 */
	public abstract Locale getLocaleForCountry(String countryCode);

	/**
	 * Returns the currency instance for a given currency code.
	 *
	 * @param cur the 3-letter currency code
	 * @return the currency
	 */
	public abstract Currency getCurrency(String cur);

	/**
	 * Returns the currency instance for a given locale.
	 *
	 * @param loc locale
	 * @return the currency instance or null if not found
	 */
	public abstract Currency getCurrency(Locale loc);

	/**
	 * Returns a map of all available currencies in the form:
	 * currency code - full currency name and symbol.
	 * @return a map of known currencies
	 */
	public abstract Map<String, String> getCurrenciesMap();

	/**
	 * Validate the currency code.
	 * @param cur a 3-letter curency code
	 * @return true if the code corresponds to a valid currency
	 */
	public abstract boolean isValidCurrency(String cur);

	/**
	 * Returns an instance of this class.
	 *
	 * @return an instance
	 */
	public static CurrencyUtils getInstance() {
		return INSTANCE;
	}
}

