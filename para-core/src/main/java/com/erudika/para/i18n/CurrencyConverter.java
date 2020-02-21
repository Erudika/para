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

/**
 * A currency converter service. Used for converting between currencies.
 * Requires data about current exchange rates.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public interface CurrencyConverter {

	/**
	 * Convert from one currency to another.
	 * @param amount the amount to convert
	 * @param from currency 3-letter code
	 * @param to currency 3-letter code
	 * @return the converted amount
	 */
	Double convertCurrency(Number amount, String from, String to);

}
