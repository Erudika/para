/*
 * Copyright 2013-2017 Erudika. https://erudika.com
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

import static com.erudika.para.core.utils.ParaObjectUtils.*;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
	public void testGetAnnotatedFields_3args() {
	}

	@Test
	public void testSetAnnotatedFields_Map() {
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

}
