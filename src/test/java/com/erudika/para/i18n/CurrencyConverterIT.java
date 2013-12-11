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

import com.erudika.para.persistence.MockDAO;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@RunWith(Parameterized.class)
public class CurrencyConverterIT {
	
	@Parameterized.Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][]{
			{new MockCurrencyConverter()}, 
			{new OXRCurrencyConverter(new MockDAO())}
		});
	}
	
	private CurrencyConverter cc;

	public CurrencyConverterIT(CurrencyConverter cc) {
		this.cc = cc;
	}

	@Test
	public void testConvertCurrency() {
		assertTrue(cc.convertCurrency(null, null, null) == 0.0);
		assertTrue(cc.convertCurrency(1, null, null) == 0.0);
		assertTrue(cc.convertCurrency(1, "USD", "USD") == 1.0);
		assertTrue(cc.convertCurrency(1, "EUR", "EUR") == 1.0);
		assertTrue(cc.convertCurrency(1, "EUR", "JPY") > 1.0);
		assertTrue(cc.convertCurrency(-1, "xxx", "xxx") == -1.0);		
	}

}