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
package com.erudika.para.utils;

import com.erudika.para.annotations.Locked;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import com.erudika.para.core.Votable;
import static com.erudika.para.utils.Utils.*;
import static com.erudika.para.core.utils.ParaObjectUtils.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class UtilsTest {

	public UtilsTest() {
	}

	@Test
	public void testMD5() {
		assertEquals("098f6bcd4621d373cade4e832627b4f6", md5("test"));
		assertEquals("47ec2dd791e31e2ef2076caf64ed9b3d", md5("test123456"));
		assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5(""));
		assertEquals("", md5(null));
	}

	@Test
	public void testBcrypt() {
		assertNull(bcrypt(null));
		assertFalse(bcrypt("").isEmpty());
		assertFalse(bcrypt("test").isEmpty());
		assertNotEquals(bcrypt("testpass"), bcrypt("testpass")); // bcrypt hashes are salted i.e. !=
	}
	@Test
	public void testBcryptMatches() {
		assertFalse(bcryptMatches(null, null));
		assertFalse(bcryptMatches(null, "test"));
		assertFalse(bcryptMatches("", "test"));
		assertFalse(bcryptMatches("test", "test"));
		assertTrue(bcryptMatches("testpass", "$2a$12$OQXURSOiBPvDHZc0xzSn.erVlBGChnY8hi.OLLZVBczquUaOTJTg."));
	}

	@Test
	public void testGenerateSecurityToken() {
		String tok1 = generateSecurityToken();
		String tok2 = generateSecurityToken();
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
	public void testCompileMustache() {
		assertNotNull(compileMustache(null, ""));
		assertNotNull(compileMustache(new HashMap<>(), "test"));
		Map<String, Object> map = new HashMap<>();
		map.put("test", "string");
		assertEquals("<html>string</html>", compileMustache(map, "<html>{{test}}</html>"));
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
		assertNotNull(arrayJoin(new ArrayList<>(), null));
		assertEquals("one,two,three", arrayJoin(Arrays.asList("one","two","three"), ","));
		assertEquals("onetwothree", arrayJoin(Arrays.asList("one","two","three"), ""));
	}

	@Test
	public void testStripAndTrim() {
		assertNotNull(stripAndTrim(null));
		assertNotNull(stripAndTrim(""));
		assertEquals("test", stripAndTrim("  $% ^&test-?<?> § ±<_ ) (/.,"));
		assertEquals("test 123 test", stripAndTrim("  $% ^&test		-?<? 123    > § ±test<_ ) (/.,"));
		assertEquals("тест asd 123", stripAndTrim("тест asd 123 ©"));
		assertEquals("asd--123", stripAndTrim("тест asd()123 ©", "-", true));
	}

	@Test
	public void testNoSpaces() {
		assertNotNull(noSpaces(null, "-"));
		assertNotNull(noSpaces("", "-"));
		assertEquals("test-123-456-789-000", noSpaces("test 123 456      789	000", "-"));
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
	public void testBase64enc() {
		assertNotNull(base64enc(null));
		assertNotNull(base64enc(new byte[0]));
		assertEquals("dGVzdDEyMyBzdHJpbmc=", base64enc("test123 string".getBytes()));
		assertEquals("dGVzdDEyMyBzdHJpbmc", base64encURL("test123 string".getBytes()));
		assertEquals("ICAg", base64enc("   ".getBytes()));
		assertEquals("MTIz", base64enc(base64dec("MTIz").getBytes()));
	}

	@Test
	public void testBase64dec() {
		assertNotNull(base64dec(null));
		assertNotNull(base64dec(""));
		assertEquals("test123 string", base64dec("dGVzdDEyMyBzdHJpbmc="));
		assertEquals("   ", base64dec("ICAg"));
		assertEquals("123", base64dec(base64enc("123".getBytes())));
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
	public void testGetObjectURI() {
		User u = new User("123");
		assertNotNull(getObjectURI(null, false, false));
		assertEquals(u.getObjectURI(), getObjectURI(u, false, true));
	}

	@Test
	public void testSingularToPlural() {
		assertNull(singularToPlural(null));
		assertNotNull(singularToPlural(""));
		assertEquals("users", singularToPlural("user"));
		assertEquals("recipes", singularToPlural("recipe"));
		assertEquals("recipes", singularToPlural("recipes"));
		assertEquals("cities", singularToPlural("city"));
		assertEquals("cities", singularToPlural("cities"));
	}

//	@Test
//	public void testPopulate() {
//		Map<String, String[]> map = new HashMap<String, String[]>();
//		long timestamp = 1390052381000L;
//		map.put(Config._EMAIL, new String[]{"u@test.co"});
//		map.put(Config._NAME, new String[]{"User Name"});
//		map.put(Config._TAGS, new String[]{"tag1", "tag2", "tag3"});
//		map.put(Config._TIMESTAMP, new String[]{Long.toString(timestamp)});
//
//		User u = new User();
//		u.setActive(true);
//		populate(u, null);
//		assertNull(u.getEmail());
//		populate(u, new HashMap<String, String[]>());
//		assertNull(u.getEmail());
//		populate(u, map);
//		assertEquals(map.get(Config._EMAIL)[0], u.getEmail());
//		assertEquals(map.get(Config._NAME)[0], u.getName());
//		assertTrue(u.getTags().contains(map.get(Config._TAGS)[0]));
//		assertEquals(timestamp, u.getTimestamp().longValue());
//		assertEquals(true, u.getActive());
//	}

	@Test
	public void testTypesMatch() {
		User u = new User();
		assertTrue(typesMatch(u));
		u.setType("usr");
		assertFalse(typesMatch(u));
		assertFalse(typesMatch(null));
	}

	@Test
	public void testGetAnnotatedFields() {
		User u = new User();
		assertTrue(getAnnotatedFields(null).isEmpty());
		assertFalse(getAnnotatedFields(u).isEmpty());
		Map<String, Object> fm1 = getAnnotatedFields(u);
		Map<String, Object> fm2 = getAnnotatedFields(u, Locked.class);
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
	@SuppressWarnings("unchecked")
	public void testSetAnnotatedFields() {
		assertNull(setAnnotatedFields(null));
		assertNull(setAnnotatedFields(new HashMap<>()));

		Map<String, Object> map = new HashMap<>();
		long timestamp = 1390052381000L;
		map.put(Config._ID, "123");
		map.put(Config._TYPE, Utils.type(User.class));
		map.put(Config._EMAIL, "u@test.co");
		map.put(Config._NAME, "User Name");
		map.put(Config._TAGS, "[\"tag1\",\"tag2\"]");	// flattened JSON string
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
		setAnnotatedFields(obj2, map, null);
		assertEquals(map.get(Config._ID), obj2.getId());
		assertEquals(map.get(Config._NAME), obj2.getName());
		assertEquals(map.get(Config._EMAIL), obj2.getEmail());
		assertEquals(timestamp, obj2.getTimestamp().longValue());
		assertEquals(true, obj2.getActive());

		// complex nested objects coming from Jackson
		Map<String, Object> map1 = new HashMap<>();
		Map<String, Object> props = new HashMap<String, Object>(){{
			put("testprop1", "test");
			put("testprop2", true);
			put("testprop3", 5);
			put("testprop4", Collections.singletonList("list"));
		}};

		map1.put(Config._ID, "123");
		map1.put(Config._TYPE, Utils.type(Sysprop.class));
		map1.put(Config._NAME, "Sysprop");
		map1.put(Config._TIMESTAMP, timestamp);
		map1.put("properties", props);

		Sysprop sys = setAnnotatedFields(map1);
		assertNotNull(sys);
		assertEquals(map1.get(Config._ID), sys.getId());
		assertEquals(map1.get(Config._TYPE), sys.getType());
		assertEquals(map1.get(Config._NAME), sys.getName());
		assertEquals(map1.get(Config._TIMESTAMP), sys.getTimestamp());
		assertEquals(props.size(), sys.getProperties().size());
		assertEquals(props.get("testprop1"), sys.getProperties().get("testprop1"));
		assertEquals(props.get("testprop2"), sys.getProperties().get("testprop2"));
		assertEquals(props.get("testprop3"), sys.getProperties().get("testprop3"));
		assertEquals(props.get("testprop4"), sys.getProperties().get("testprop4"));
		assertEquals(((List<String>) props.get("testprop4")).get(0),
				((List<String>) sys.getProperties().get("testprop4")).get(0));

		// unknown fields and custom types test
		map1.put(Config._TYPE, "customtype");
		map1.put("animal", "cat");
		map1.put("hair", "long");
		map1.put("colour", "white");
		map1.put("legs", 4);

		Sysprop sys2 = setAnnotatedFields(map1);
		assertNotNull(sys2);
		assertEquals(map1.get(Config._ID), sys2.getId());
		assertEquals(map1.get(Config._TYPE), sys2.getType());
		assertEquals(map1.get(Config._NAME), sys2.getName());
		assertEquals(map1.get(Config._TIMESTAMP), sys2.getTimestamp());
		assertEquals(8, sys2.getProperties().size());
		assertEquals(map1.get("animal"), sys2.getProperties().get("animal"));
		assertEquals(map1.get("hair"), sys2.getProperties().get("hair"));
		assertEquals(map1.get("colour"), sys2.getProperties().get("colour"));
		assertEquals(map1.get("legs"), sys2.getProperties().get("legs"));

		map1.put("animal", null);
		map1.put("hair", null);
		map1.put("colour", null);
		map1.put("legs", null);
		// update
		setAnnotatedFields(sys2, map1, Locked.class);
		assertEquals(4, sys2.getProperties().size());
	}

	@Test
	public void testToObject() {
		assertNotNull(toObject(null));
		assertNotNull(toObject(""));
		assertNotNull(toObject("test123"));
		assertEquals(Sysprop.class, toObject("test123").getClass());
		assertEquals(User.class, toObject(Utils.type(User.class)).getClass());
		assertEquals(Tag.class, toObject(Utils.type(Tag.class)).getClass());
	}

	@Test
	public void testToClass() {
		assertNotNull(toClass(null));
		assertNotNull(toClass(""));
		assertEquals(Sysprop.class, toClass("test123"));
		assertEquals(User.class, toClass(Utils.type(User.class)));
		assertEquals(Tag.class, toClass(Utils.type(Tag.class)));
	}

	@Test
	public void testGetCoreClassesMap() {
		Set<Class<? extends ParaObject>> set = new HashSet<>();
		set.addAll(getCoreClassesMap().values());
		assertFalse(set.isEmpty());
		assertTrue(set.contains(Tag.class));
		assertFalse(set.contains(Votable.class));
		assertFalse(set.contains(ParaObject.class));
	}

	@Test
	public void testFromJSON() {
		assertNull(fromJSON(""));
		assertNull(fromJSON("{}")); // depending on how Jackson is configured this may be null or not

		ParaObject obj1 = fromJSON("{\"type\":\"testtype\", \"name\":\"testname\", \"id\":\"123\"}");
		ParaObject obj2 = fromJSON("{\"type\":\"user\", \"name\":\"user name\", \"id\":\"111\"}");
		ParaObject obj3 = fromJSON("{\"user\":\"one\", \"alias\":\"user1\", \"id\":\"456\", \"name\":\"name\"}");

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
		assertFalse(isBasicType(ParaObject.class));
		// etc.
	}

	@Test
	public void testGetNewId() {
		assertFalse(getNewId().isEmpty());
	}

	@Test
	public void testGetAllDeclaredFields() {
		assertTrue(getAllDeclaredFields(null).isEmpty());
		assertTrue(getAllDeclaredFields(null).isEmpty());
		assertFalse(getAllDeclaredFields(User.class).isEmpty());
	}
}