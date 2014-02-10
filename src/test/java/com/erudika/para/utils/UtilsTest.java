/*
 * Copyright 2014 Alex Bogdanovski <alex@erudika.com>.
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
package com.erudika.para.utils;

import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.core.PObject;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import com.erudika.para.persistence.MockDAO;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.erudika.para.utils.Utils.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class UtilsTest {

	public UtilsTest() {
	}

	@BeforeClass
	public static void setUpClass() {
	}

	@AfterClass
	public static void tearDownClass() {
	}

	@Before
	public void setUp() {
	}

	@After
	public void tearDown() {
	}

	@Test
	public void testMD5() {
		assertEquals("098f6bcd4621d373cade4e832627b4f6", MD5("test"));
		assertEquals("47ec2dd791e31e2ef2076caf64ed9b3d", MD5("test123456"));
		assertEquals("d41d8cd98f00b204e9800998ecf8427e", MD5(""));
		assertEquals("", MD5(null));
	}

	@Test
	public void testHMACSHA() {
		assertNotNull(HMACSHA(null, null));
		assertEquals("", HMACSHA("", ""));
		assertTrue(!HMACSHA("test", "key").isEmpty());
		assertEquals("16af7fce99086e154ee21e9e987cc780f10b53e84d19d40999eb8a450b20b621", HMACSHA("test123", "key123"));
	}

	@Test
	public void testGenerateAuthToken() {
		String tok1 = generateAuthToken();
		String tok2 = generateAuthToken();
		assertFalse(StringUtils.isBlank(tok1));
		assertFalse(StringUtils.isBlank(tok2));
		assertNotEquals(tok1, tok2);
	}

	@Test
	public void testEscapeJavascript() {
		assertNotNull(escapeJavascript(null));
		assertNotNull(escapeJavascript(""));
	}

	@Test
	public void testStripHtml() {
		assertNotNull(stripHtml(null));
		assertNotNull(stripHtml(""));
		assertEquals("text", stripHtml("<html>text</html>"));
	}

	@Test
	public void testMarkdownToHtml() {
		assertNotNull(markdownToHtml(null));
		assertNotNull(markdownToHtml(""));
		assertFalse(markdownToHtml("*bold*").isEmpty());
	}

	@Test
	public void testAbbreviate() {
		assertNotNull(abbreviate(null, 0));
		assertNotNull(abbreviate("", 0));
		assertFalse(abbreviate("this is a test", 4).isEmpty());
	}

	@Test
	public void testArrayJoin() {
		assertNotNull(arrayJoin(null, null));
		assertNotNull(arrayJoin(new ArrayList<String>(), null));
		assertEquals("one,two,three", arrayJoin(Arrays.asList("one","two","three"), ","));
		assertEquals("onetwothree", arrayJoin(Arrays.asList("one","two","three"), ""));
	}

	@Test
	public void testStripAndTrim() {
		assertNotNull(stripAndTrim(null));
		assertNotNull(stripAndTrim(""));
		assertEquals("test", stripAndTrim("  $% ^&test-?<?> § ±<_ ) (/.,"));
		assertEquals("test 123 test", stripAndTrim("  $% ^&test		-?<? 123    > § ±test<_ ) (/.,"));
	}

	@Test
	public void testSpacesToDashes() {
		assertNotNull(spacesToDashes(null));
		assertNotNull(spacesToDashes(""));
		assertEquals("test-123-456-789-000", spacesToDashes("test 123 456      789	000"));
	}

	@Test
	public void testFormatMessage() {
		assertNotNull(formatMessage(null));
		assertNotNull(formatMessage(""));
		assertNotNull(formatMessage("", null, null));
		assertEquals("test", formatMessage("test", "one"));
		assertEquals("test two", formatMessage("test {0}", "two"));
	}

	@Test
	public void testFormatDate() {
		assertNotNull(formatDate(null, null));
		assertNotNull(formatDate(null, null, null));
		assertEquals("2001", formatDate(1000000000000L, "yyyy", Locale.US));
	}

	@Test
	public void testGetCurrentYear() {
		assertTrue(getCurrentYear() > 2013);
	}

	@Test
	public void testGetMonths() {
		assertEquals("January", getMonths(null)[0]);
	}

	@Test
	public void testAbbreviateInt() {
		assertNotNull(abbreviateInt(null, 0));
		assertEquals("1K", abbreviateInt(1000, 0));
		assertEquals("1K", abbreviateInt(1000, 1));
		assertEquals("1M", abbreviateInt(1000000, 1));
	}

	@Test
	public void testUrlDecode() {
		assertNotNull(urlDecode(null));
		assertEquals(" ", urlDecode("%20"));
		assertEquals("&", urlDecode("&"));
	}

	@Test
	public void testUrlEncode() {
		assertNotNull(urlEncode(null));
		assertEquals("+", urlEncode(" "));
		assertEquals("a", urlEncode("a"));
		assertEquals(" ", urlDecode(urlEncode(" ")));
		assertEquals("?test! ", urlDecode(urlEncode("?test! ")));
	}

	@Test
	public void testIsValidURL() {
		assertFalse(isValidURL(null));
		assertFalse(isValidURL(""));
		assertTrue(isValidURL("http://test.com"));
		assertFalse(isValidURL("test.com"));
		assertFalse(isValidURL("test.com"));
	}

	@Test
	public void testGetHostFromURL() {
		assertNotNull(getHostFromURL(null));
		assertNotNull(getHostFromURL(""));
		assertEquals("test.com", getHostFromURL("http://test.com"));
	}

	@Test
	public void testGetBaseURL() {
		assertNull(getBaseURL(null));
		assertNull(getBaseURL(""));
		assertEquals("http://test.com", getBaseURL("http://test.com/index.html"));
	}

	@Test
	public void testGetSystemProperty() {
		assertEquals(null, getSystemProperty("test.test.prop"));
		System.setProperty("test.test.prop", "test123");
		assertEquals("test123", getSystemProperty("test.test.prop"));
	}

	@Test
	public void testGetMaxImgSize() {
		int max = Config.MAX_IMG_SIZE_PX;
		assertTrue(Arrays.equals(new int[]{200, 200}, getMaxImgSize(200, 200)));
		assertTrue(Arrays.equals(new int[]{max, max}, getMaxImgSize(max+100, max+100)));
	}

	@Test
	public void testGetObjectLink() {
		User u = new User("123");
		assertNotNull(getObjectURL(null, false, false));
		assertEquals(u.getObjectURL(), getObjectURL(u, false, true));
	}

	@Test
	public void testSingularToPlural() {
		assertNull(singularToPlural(null));
		assertNotNull(singularToPlural(""));
		assertEquals("users", singularToPlural("user"));
	}

	@Test
	public void testPopulate() {
		Map<String, String[]> map = new HashMap<String, String[]>();
		long timestamp = 1390052381000L;
		map.put(Config._EMAIL, new String[]{"u@test.co"});
		map.put(Config._NAME, new String[]{"User Name"});
		map.put(Config._TAGS, new String[]{"tag1", "tag2", "tag3"});
		map.put(Config._TIMESTAMP, new String[]{Long.toString(timestamp)});

		User u = new User();
		u.setActive(true);
		populate(u, null);
		assertNull(u.getEmail());
		populate(u, new HashMap<String, String[]>());
		assertNull(u.getEmail());
		populate(u, map);
		assertEquals(map.get(Config._EMAIL)[0], u.getEmail());
		assertEquals(map.get(Config._NAME)[0], u.getName());
		assertTrue(u.getTags().contains(map.get(Config._TAGS)[0]));
		assertEquals(timestamp, u.getTimestamp().longValue());
		assertEquals(true, u.getActive());
	}

	@Test
	public void testTypesMatch() {
		User u = new User();
		assertTrue(typesMatch(u));
		u.setClassname("usr");
		assertFalse(typesMatch(u));
		assertFalse(typesMatch(null));
	}

	@Test
	public void testGetAnnotatedFields() {
		User u = new User();
		assertTrue(getAnnotatedFields(null, null, null).isEmpty());
		assertTrue(getAnnotatedFields(u, null, null).isEmpty());
		Map<String, Object> fm1 = getAnnotatedFields(u, Stored.class, null);
		Map<String, Object> fm2 = getAnnotatedFields(u, Stored.class, Locked.class);
		assertFalse(fm1.isEmpty());
		assertFalse(fm2.isEmpty());
		assertTrue(fm1.containsKey(Config._ID));
		assertFalse(fm2.containsKey(Config._ID));
		assertTrue(fm1.containsKey(Config._TAGS));
		assertTrue(fm2.containsKey(Config._TAGS));
		assertTrue(fm1.containsKey(Config._EMAIL));
		assertTrue(fm2.containsKey(Config._EMAIL));
	}

	@Test
	public void testSetAnnotatedFields() {
		assertNull(setAnnotatedFields(null));
		assertNull(setAnnotatedFields(new HashMap<String, Object>()));

		Map<String, Object> map = new HashMap<String, Object>();
		long timestamp = 1390052381000L;
		map.put(Config._ID, "123");
		map.put(Config._CLASSNAME, PObject.classname(User.class));
		map.put(Config._EMAIL, "u@test.co");
		map.put(Config._NAME, "User Name");
		map.put(Config._TAGS, "[\"tag1\",\"tag2\"]");
		map.put(Config._TIMESTAMP, Long.toString(timestamp));

		User obj = setAnnotatedFields(map);
		assertNotNull(obj);
		assertEquals(map.get(Config._ID), obj.getId());
		assertEquals(map.get(Config._NAME), obj.getName());
		assertEquals(map.get(Config._EMAIL), obj.getEmail());
		assertEquals(timestamp, obj.getTimestamp().longValue());
		assertTrue(obj.getTags().contains("tag1") && obj.getTags().contains("tag2"));

		User obj2 = new User("234");
		obj2.setActive(true);
		setAnnotatedFields(obj2, map);
		assertEquals(map.get(Config._ID), obj2.getId());
		assertEquals(map.get(Config._NAME), obj2.getName());
		assertEquals(map.get(Config._EMAIL), obj2.getEmail());
		assertEquals(timestamp, obj2.getTimestamp().longValue());
		assertEquals(true, obj2.getActive());
	}

	@Test
	public void testToClass() {
		assertNull(toClass(null));
		assertNull(toClass(""));
		assertEquals(User.class, toClass(PObject.classname(User.class)));
		assertEquals(Tag.class, toClass(PObject.classname(Tag.class)));
	}

	@Test
	public void testFromJSON() {
		assertNull(fromJSON(null));
		assertNull(fromJSON(""));
		assertNotNull(fromJSON("{}"));

		ParaObject obj1 = Utils.fromJSON("{\"classname\":\"testtype\", \"name\":\"testname\", \"id\":\"123\"}");
		ParaObject obj2 = Utils.fromJSON("{\"classname\":\"user\", \"name\":\"user name\", \"id\":\"111\"}");
		ParaObject obj3 = Utils.fromJSON("{\"user\":\"one\", \"alias\":\"user1\", \"id\":\"456\", \"name\":\"name\"}");

		assertNotNull(obj1);
		assertEquals(Sysprop.class, obj1.getClass());
		assertEquals("123", obj1.getId());
		assertEquals("testname", obj1.getName());

		assertNotNull(obj2);
		assertEquals(User.class, obj2.getClass());
		assertEquals("111", obj2.getId());
		assertEquals("user name", obj2.getName());

		assertNotNull(obj3);
		assertEquals(Sysprop.class, obj3.getClass());
		assertEquals("456", obj3.getId());
		assertEquals("name", obj3.getName());
	}

	@Test
	public void testToJSON() {
		assertNotNull(toJSON(null));
		assertFalse(toJSON(new User()).isEmpty());
	}

	@Test
	public void testIsBasicType() {
		assertFalse(isBasicType(null));
		assertTrue(isBasicType(Integer.class));
		assertTrue(isBasicType(Long.class));
		assertFalse(isBasicType(Object.class));
		assertFalse(isBasicType(PObject.class));
		// etc.
	}

	@Test
	public void testIsValidObject() {
		assertFalse(isValidObject(null));
		assertFalse(isValidObject(new Tag()));
		assertTrue(isValidObject(new Tag("tag1")));
		Tag t = new Tag("");
		t.setName("");
		assertFalse(isValidObject(t));
		assertFalse(isValidObject(new User()));
		User u = new User();
		u.setId("123");
		u.setName("asd");
		assertFalse(isValidObject(u));
	}

	@Test
	public void testValidateRequest() {
		assertTrue(validateRequest(null).length > 0);
		assertEquals(0, validateRequest(new Tag("test")).length);
	}

	@Test
	public void testGetJSONValidationObject() {
		assertEquals("{}", getJSONValidationObject(null, null, null));
		assertNotEquals("{}", getJSONValidationObject("tag", null, null));

		assertNotNull(getAnnotationsMap(null, null));
		assertTrue(getAnnotationsMap(null, null).isEmpty());
		assertFalse(getAnnotationsMap(User.class, null).isEmpty());

		assertTrue(getAllDeclaredFields(null).isEmpty());
		assertTrue(getAllDeclaredFields(null).isEmpty());
		assertFalse(getAllDeclaredFields(User.class).isEmpty());

		assertNotNull(annotationToValidation(null, null));
		assertTrue(annotationToValidation(null, null).length == 0);
	}

	@Test
	public void testGetNewId() {
		assertFalse(getNewId().isEmpty());
	}

	@Test
	public void testGetReadResponse() {
		assertEquals(Status.NOT_FOUND.getStatusCode(), getReadResponse(null).getStatus());
		assertEquals(Status.OK.getStatusCode(), getReadResponse(new Tag("tag")).getStatus());
	}

	@Test
	public void testGetCreateUpdateDeleteResponse() {
		Tag t = new Tag("tag");
		t.setDao(new MockDAO());
		assertEquals(Status.BAD_REQUEST.getStatusCode(), getCreateResponse(null, null).getStatus());
		assertEquals(Status.CREATED.getStatusCode(), getCreateResponse(t, UriBuilder.fromPath("/")).getStatus());
		assertNotNull(t.getDao().read(t.getId()));

		assertEquals(Status.NOT_FOUND.getStatusCode(), getUpdateResponse(null, null).getStatus());
		assertEquals(Status.OK.getStatusCode(), getUpdateResponse(t, null).getStatus());
		assertNotNull(t.getDao().read(t.getId()));

		assertEquals(Status.BAD_REQUEST.getStatusCode(), getDeleteResponse(null).getStatus());
		assertEquals(Status.OK.getStatusCode(), getDeleteResponse(t).getStatus());
		assertNull(t.getDao().read(t.getId()));
	}

	@Test
	public void testGetJSONResponse() {
		assertEquals(Status.BAD_REQUEST.getStatusCode(), getJSONResponse(null).getStatus());
		assertEquals(Status.OK.getStatusCode(), getJSONResponse(Status.OK).getStatus());
	}
}