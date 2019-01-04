/*
 * Copyright 2013-2019 Erudika. https://erudika.com
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

import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class CurrencyUtilsTest {

	private CurrencyUtils cu;

	public CurrencyUtilsTest() {
		cu = CurrencyUtils.getInstance();
	}

	@Test
	public void testGetInstance() {
		assertNotNull(cu);
	}

	@Test
	public void testFormatPrice() {
		assertNotNull(cu.formatPrice(Double.NaN, null));
		assertNotNull(cu.formatPrice(null, "USD"));
		assertEquals("$5.00", cu.formatPrice(5.0, "USD"));
//		assertEquals("5,00€", cu.formatPrice(5.0, "EUR"));
	}

	@Test
	public void testGetCurrencyName() {
		assertEquals("", cu.getCurrencyName("", null));
		assertEquals("", cu.getCurrencyName(null, null));
		assertEquals("", cu.getCurrencyName("us", Locale.US));
		assertEquals("US Dollar", cu.getCurrencyName("USD", Locale.US));
		assertEquals("US Dollar", cu.getCurrencyName("USD", null));
		assertEquals(cu.getCurrencyName("usd", Locale.US), cu.getCurrencyName("USD", Locale.US));
	}

	@Test
	public void testGetLocaleForCountry() {
		assertNull(cu.getLocaleForCountry(null));
		assertNull(cu.getLocaleForCountry(""));
		assertNull(cu.getLocaleForCountry("xxx"));
		assertNull(cu.getLocaleForCountry("USD"));
		assertNotNull(cu.getLocaleForCountry("US"));
		assertEquals(cu.getLocaleForCountry("us"), cu.getLocaleForCountry("US"));
	}

	@Test
	public void testGetCurrency() {
		assertNotNull(cu.getCurrency(""));
		assertNotNull(cu.getCurrency("."));
		assertNotNull(cu.getCurrency("USD"));
		assertEquals(cu.getCurrency("usd"), cu.getCurrency("USD"));
	}

	@Test
	public void testGetCurrenciesMap() {
		assertFalse(cu.getCurrenciesMap().isEmpty());
	}

	@Test
	public void testIsValidCurrency() {
		assertFalse(cu.isValidCurrency(null));
		assertFalse(cu.isValidCurrency(""));
		assertFalse(cu.isValidCurrency("x"));
		assertFalse(cu.isValidCurrency("xxx"));
		assertTrue(cu.isValidCurrency("usd"));
		assertTrue(cu.isValidCurrency("GBP"));
	}
}