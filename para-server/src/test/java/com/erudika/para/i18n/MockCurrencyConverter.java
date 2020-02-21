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

import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class MockCurrencyConverter implements CurrencyConverter {

	@Override
	public Double convertCurrency(Number amount, String from, String to) {
		if (amount == null || StringUtils.isBlank(from) || StringUtils.isBlank(to)) {
			return 0.0;
		}
		Double f = rates.containsKey(from) ? rates.get(from) : 1.0;
		Double t = rates.containsKey(to) ? rates.get(to) : 1.0;
		double ratio = t / f;

		return amount.doubleValue() * ratio;
	}

	private Map<String, Double> rates = new HashMap<String, Double>() {
		private static final long serialVersionUID = 1L;
		{
		// base: USD
		put("AED", 3.67325);
		put("AFN", 58.026376);
		put("ALL", 101.895001);
		put("AMD", 408.407999);
		put("ANG", 1.78906);
		put("AOA", 97.609575);
		put("ARS", 6.271668);
		put("AUD", 1.103126);
		put("AWG", 1.78985);
		put("AZN", 0.784133);
		put("BAM", 1.41929);
		put("BBD", 2.0);
		put("BDT", 77.61918);
		put("BGN", 1.420493);
		put("BHD", 0.377047);
		put("BIF", 1542.878433);
		put("BMD", 1.0);
		put("BND", 1.249819);
		put("BOB", 6.905505);
		put("BRL", 2.333929);
		put("BSD", 1.0);
		put("BTC", 0.0011378765);
		put("BTN", 61.224787);
		put("BWP", 8.692463);
		put("BYR", 9428.173333);
		put("BZD", 1.992722);
		put("CAD", 1.060835);
		put("CDF", 924.562667);
		put("CHF", 0.886497);
		put("CLF", 0.022925);
		put("CLP", 532.717701);
		put("CNY", 6.079002);
		put("COP", 1934.053333);
		put("CRC", 497.603799);
		put("CUP", 22.682881);
		put("CVE", 79.76044);
		put("CZK", 19.90614);
		put("DJF", 178.696999);
		put("DKK", 5.410726);
		put("DOP", 42.49333);
		put("DZD", 78.73821);
		put("EEK", 11.63425);
		put("EGP", 6.887214);
		put("ERN", 15.002825);
		put("ETB", 19.05602);
		put("EUR", 0.724785);
		put("FJD", 1.870564);
		put("FKP", 0.610077);
		put("GBP", 0.610077);
		put("GEL", 1.73431);
		put("GHS", 2.315563);
		put("GIP", 0.610077);
		put("GMD", 37.92581);
		put("GNF", 6901.816667);
		put("GTQ", 7.852141);
		put("GYD", 203.481666);
		put("HKD", 7.754031);
		put("HNL", 20.46786);
		put("HRK", 5.542016);
		put("HTG", 38.874088);
		put("HUF", 219.584799);
		put("IDR", 11969.033333);
		put("ILS", 3.503391);
		put("INR", 61.30097);
		put("IQD", 1163.463341);
		put("IRR", 24768.667967);
		put("ISK", 117.397);
		put("JEP", 0.610077);
		put("JMD", 103.289001);
		put("JOD", 0.70841);
		put("JPY", 102.6801);
		put("KES", 86.58986);
		put("KGS", 49.4015);
		put("KHR", 3991.1785);
		put("KMF", 356.695301);
		put("KPW", 900.0);
		put("KRW", 1052.128341);
		put("KWD", 0.282389);
		put("KYD", 0.825978);
		put("KZT", 154.359);
		put("LAK", 8008.61);
		put("LBP", 1504.51);
		put("LKR", 130.813601);
		put("LRD", 80.925834);
		put("LSL", 10.37323);
		put("LTL", 2.504156);
		put("LVL", 0.509781);
		put("LYD", 1.235769);
		put("MAD", 8.176402);
		put("MDL", 12.86646);
		put("MGA", 2252.945);
		put("MKD", 44.74463);
		put("MMK", 980.76462);
		put("MNT", 1699.833333);
		put("MOP", 7.981708);
		put("MRO", 291.8288);
		put("MTL", 0.683602);
		put("MUR", 30.3498);
		put("MVR", 15.37917);
		put("MWK", 421.4856);
		put("MXN", 12.94759);
		put("MYR", 3.210811);
		put("MZN", 29.985125);
		put("NAD", 10.37105);
		put("NGN", 158.477899);
		put("NIO", 25.49777);
		put("NOK", 6.120103);
		put("NPR", 97.85401);
		put("NZD", 1.214632);
		put("OMR", 0.385019);
		put("PAB", 1.0);
		put("PEN", 2.793986);
		put("PGK", 2.515046);
		put("PHP", 44.18123);
		put("PKR", 107.332);
		put("PLN", 3.033196);
		put("PYG", 4490.613262);
		put("QAR", 3.641202);
		put("RON", 3.228492);
		put("RSD", 83.34056);
		put("RUB", 32.75066);
		put("RWF", 673.64692);
		put("SAR", 3.750592);
		put("SBD", 7.208284);
		put("SCR", 12.04649);
		put("SDG", 5.698948);
		put("SEK", 6.537349);
		put("SGD", 1.251103);
		put("SHP", 0.610077);
		put("SLL", 4322.333333);
		put("SOS", 1184.593433);
		put("SRD", 3.283333);
		put("STD", 17811.650667);
		put("SVC", 8.74274);
		put("SYP", 140.427499);
		put("SZL", 10.37347);
		put("THB", 32.08197);
		put("TJS", 4.7737);
		put("TMT", 2.850167);
		put("TND", 1.663444);
		put("TOP", 1.858464);
		put("TRY", 2.038743);
		put("TTD", 6.40588);
		put("TWD", 29.56965);
		put("TZS", 1610.058333);
		put("UAH", 8.252245);
		put("UGX", 2517.31);
		put("USD", 1.0);
		put("UYU", 21.23146);
		put("UZS", 2198.346673);
		put("VEF", 6.291739);
		put("VND", 21104.3);
		put("VUV", 96.095);
		put("WST", 2.308163);
		put("XAF", 476.296111);
		put("XAG", 0.04918806);
		put("XAU", 0.00079546);
		put("XCD", 2.70158);
		put("XDR", 0.64804);
		put("XOF", 476.6782);
		put("XPF", 86.624901);
		put("YER", 215.004201);
		put("ZAR", 10.36873);
		put("ZMK", 5253.075255);
		put("ZMW", 5.596537);
		put("ZWL", 322.355006);
	}};

}
