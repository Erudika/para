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
package com.erudika.para.core;

import com.erudika.para.annotations.Stored;
import static com.erudika.para.core.utils.ParaObjectUtils.getAnnotatedFields;
import static com.erudika.para.core.utils.ParaObjectUtils.getAppidFromAuthHeader;
import static com.erudika.para.core.utils.ParaObjectUtils.getCoreTypes;
import static com.erudika.para.core.utils.ParaObjectUtils.setAnnotatedFields;
import com.erudika.para.utils.Cat;
import com.erudika.para.utils.CatDeserializer;
import com.erudika.para.utils.CatSerializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * TODO!
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ParaObjectUtilsTest {

	public ParaObjectUtilsTest() {
	}

	@BeforeClass
	public static void setUpClass() {
	}

	@AfterClass
	public static void tearDownClass() {
	}

	@Test
	public void testGetJsonMapper() {
	}

	@Test
	public void testGetJsonReader() {
	}

	@Test
	public void testGetJsonWriter() {
	}

	@Test
	public void testGetJsonWriterNoIdent() {
	}

	@Test
	public void testPopulate() {
	}

	@Test
	public void testGetCoreTypes() {
		assertEquals("user", getCoreTypes().get("users"));
	}

	@Test
	public void testGetAppidFromAuthHeader() {
		assertNotNull(getAppidFromAuthHeader(null));
		assertNotNull(getAppidFromAuthHeader(""));
		assertTrue(getAppidFromAuthHeader(null).isEmpty());
		assertTrue(getAppidFromAuthHeader("").isEmpty());
		assertEquals("para", getAppidFromAuthHeader("Anonymous app:para"));
		assertEquals("para", getAppidFromAuthHeader("AWS4-HMAC-SHA256 "
				+ "Credential=app:para/20171218/us-east-1/para/aws4_request, "
				+ "SignedHeaders=content-type;host;x-amz-date, "
				+ "Signature=1ad38af911ecb712ca8f4eab0d4afc29cc301a4dbd398bb929bd47a67d1900a6"));
		assertEquals("para", getAppidFromAuthHeader("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
				+ "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkFwcCBQYXJhIiwiYXBwaWQiOiJwYXJhIn0."
				+ "GmghGts8Hwod2zl4e0ZV8kEqY0Ey-7N4rJV9g-KYqeI"));


		String jwtGood1 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhcHBpZCI6ImFwcDpteWFwcCJ9."
				+ "M4uitKDuclLuZzadxNzL_3fjeShKBxPdncsNKkA-rfY";
		String jwtGood2 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhcHBpZCI6Im15YXBwIn0."
				+ "rChFKBeaKvlV9p_dkMveh1v85YT144IHilaeMpuVhx8";
		String jwtBad1 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
		String jwtBad2 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzIjoibXlhcHAifQ."
				+ "0nqax4hqUIRGhtmPIhLbUgCrnKJFC1q3eIeRkgQX8F0";
		String signature = "AWS4-HMAC-SHA256 Credential=app:myapp/20171103/us-east-1/para/aws4_request, "
				+ "SignedHeaders=content-type;host;x-amz-date, Signature=d60fd1be560d3ed14ff383061772055";

		assertEquals("", getAppidFromAuthHeader(null));
		assertEquals("", getAppidFromAuthHeader(" "));
		assertEquals("", getAppidFromAuthHeader("Bearer " + jwtBad1));
		assertEquals("", getAppidFromAuthHeader("Bearer " + jwtBad2));
		assertEquals("myapp", getAppidFromAuthHeader("Bearer " + jwtGood1));
		assertEquals("myapp", getAppidFromAuthHeader("Bearer " + jwtGood2));
		assertEquals("myapp", getAppidFromAuthHeader(signature));
	}

	@Test
	public void testGetAllTypes() {
	}

	@Test
	public void testTypesMatch() {
	}

	@Test
	public void testGetAnnotatedFields_GenericType() {
	}

	@Test
	public void testGetAnnotatedFields_GenericType_Class() {
	}

	@Test
	public void testGetAnnotatedFields() {
		Custom c1 = new Custom();
		c1.setId("custom1");
		c1.setNotStored("not stored");
		c1.setDateTime(ZonedDateTime.now());
		c1.setUri(URI.create("https://test.com"));
		c1.setCat(new Cat(5, "Whiskers"));
		c1.setNested(Collections.singletonMap("key", "value"));

		Map<String, Object> dataFull = getAnnotatedFields(c1, false);
		assertFalse(dataFull.containsKey("notStored"));
		assertTrue(dataFull.containsKey("cat"));
		assertEquals("Whiskers::5", dataFull.get("cat"));
		assertEquals(Collections.singletonMap("key", "value"), dataFull.get("nested"));

		Map<String, Object> dataFlat = getAnnotatedFields(c1, true);
		assertFalse(dataFlat.containsKey("notStored"));
		assertTrue(dataFlat.containsKey("cat"));
		assertEquals("Whiskers::5", dataFlat.get("cat"));
		assertEquals("{\"key\":\"value\"}", dataFlat.get("nested"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSetAnnotatedFields() {
		assertNull(setAnnotatedFields(null));
		assertNull(setAnnotatedFields(Collections.emptyMap()));
		Map<String, Object> data1 = new HashMap<String, Object>();
		data1.put("missing", "123");
		data1.put("type", "custom");
		Custom c = setAnnotatedFields(data1);
		assertNull(c.getaBool());
		assertNull(c.getaLong());
		data1.put("aLong", 10L);
		data1.put("aBool", true);
		data1.put("dateTime", ZonedDateTime.now());
		data1.put("uri", URI.create("https://test.com"));
		Custom c1 = setAnnotatedFields(data1);
		assertEquals(Long.valueOf(10L), c1.getaLong());
		assertTrue(c1.getaBool());
		assertNotNull(c1.getDateTime());
		assertEquals("https://test.com", c1.getUri().toString());

		Custom c2 = new Custom();
		c2.setId("custom1");
		c2.setDateTime(ZonedDateTime.now());
		c2.setUri(URI.create("https://test.com"));
		c2.setCat(new Cat(5, "Whiskers"));
		c2.setColor(Color.BLACK);
		c2.setColorMap(Collections.singletonMap(Color.WHITE, "brother cat"));

		Map<String, Object> dataFull = getAnnotatedFields(c2, false);
		Map<String, Object> dataFlat = getAnnotatedFields(c2, true);
		Custom k1 = setAnnotatedFields(dataFull);
		Custom k2 = setAnnotatedFields(dataFlat);
		assertEquals(k1.getCat(), k2.getCat());
		assertEquals(c2.getCat(), k1.getCat());
		assertEquals(c2.getCat(), k2.getCat());
		assertEquals(c2.getUri(), k1.getUri());
		assertEquals(c2.getUri(), k2.getUri());
		assertEquals(Color.BLACK, k1.getColor());
		assertEquals(Color.BLACK, k2.getColor());
		assertNotNull(k1.getColorMap());
		assertNotNull(k2.getColorMap());
		assertFalse(k1.getColorMap().isEmpty());
		assertFalse(k2.getColorMap().isEmpty());
		assertTrue(k1.getColorMap().containsKey(Color.WHITE));
		assertTrue(k2.getColorMap().containsKey(Color.WHITE));

		// test properties field conflict for types other than Sysprop
		Custom c3 = new Custom();
		c3.setId("custom3");
		c3.setProperties("string");

		dataFull = getAnnotatedFields(c3, false);
		dataFlat = getAnnotatedFields(c3, true);
		Custom k3 = setAnnotatedFields(dataFull);
		Custom k4 = setAnnotatedFields(dataFlat);
		assertEquals(c3.getProperties(), k3.getProperties());
		assertEquals(c3.getProperties(), k4.getProperties());

		c3.setProperties(Arrays.asList("string1", "string2"));
		dataFull = getAnnotatedFields(c3, false);
		dataFlat = getAnnotatedFields(c3, true);
		k3 = setAnnotatedFields(dataFull);
		k4 = setAnnotatedFields(dataFlat);
		assertEquals(c3.getProperties(), k3.getProperties());
		assertEquals(c3.getProperties(), k4.getProperties());
		assertTrue(((List<String>) c3.getProperties()).get(0).equals("string1"));

		c3.setProperties(false);
		dataFull = getAnnotatedFields(c3, false);
		dataFlat = getAnnotatedFields(c3, true);
		k3 = setAnnotatedFields(dataFull);
		k4 = setAnnotatedFields(dataFlat);
		assertEquals(c3.getProperties(), k3.getProperties());
		assertEquals(c3.getProperties(), k4.getProperties());
		assertFalse(((Boolean) c3.getProperties()));
	}

	@Test
	public void testSetAnnotatedFields_3args() {
	}

	@Test
	public void testToObject() {
	}

	@Test
	public void testToClass_String() {
	}

	@Test
	public void testToClass_String_Class() {
	}

	@Test
	public void testGetCoreClassesMap() {
	}

	@Test
	public void testFromJSON() {
	}

	@Test
	public void testToJSON() {
	}

	public static enum Color {
		BLACK, WHITE
	}

	public static class Custom extends Tag { // don't extend Sysprop in order to test custom properties field
		private static final long serialVersionUID = 1L;

		private String notStored;
		@Stored Map<String, Object> nested;
		@Stored Long aLong;
		@Stored Boolean aBool;
		@Stored private ZonedDateTime dateTime;
		@Stored private URI uri;
		@Stored private Color color;
		@Stored private Map<Color, String> colorMap;
		@Stored private Object properties; // CONFLICT! must not clash with Sysprop's properties field

		@JsonSerialize(using = CatSerializer.class)
		@JsonDeserialize(using = CatDeserializer.class)
		@Stored private Cat cat;

		public Object getProperties() {
			return properties;
		}

		public void setProperties(Object properties) {
			this.properties = properties;
		}

		public Color getColor() {
			return color;
		}

		public void setColor(Color color) {
			this.color = color;
		}

		public Map<Color, String> getColorMap() {
			return colorMap;
		}

		public void setColorMap(Map<Color, String> colorMap) {
			this.colorMap = colorMap;
		}

		public Cat getCat() {
			return cat;
		}

		public void setCat(Cat cat) {
			this.cat = cat;
		}

		public ZonedDateTime getDateTime() {
			return dateTime;
		}

		public void setDateTime(ZonedDateTime dateTime) {
			this.dateTime = dateTime;
		}

		public URI getUri() {
			return uri;
		}

		public void setUri(URI uri) {
			this.uri = uri;
		}

		public Long getaLong() {
			return aLong;
		}

		public void setaLong(Long aLong) {
			this.aLong = aLong;
		}

		public Boolean getaBool() {
			return aBool;
		}

		public void setaBool(Boolean aBool) {
			this.aBool = aBool;
		}

		public String getNotStored() {
			return notStored;
		}

		public void setNotStored(String notStored) {
			this.notStored = notStored;
		}

		public Map<String, Object> getNested() {
			return nested;
		}

		public void setNested(Map<String, Object> nested) {
			this.nested = nested;
		}

	}

}
