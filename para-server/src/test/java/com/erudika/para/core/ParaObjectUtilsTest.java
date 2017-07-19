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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;

/**
 * TODO!
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ParaObjectUtilsTest {

	private static final Logger logger = LoggerFactory.getLogger(ParaObjectUtilsTest.class);

	public ParaObjectUtilsTest() {
	}

	@BeforeClass
	public static void setUpClass() {
	}

	@AfterClass
	public static void tearDownClass() {
	}
	
	@Test
	public void testGetAnnotatedFields() throws IOException{
		
		boolean flattenNestedObjectsToString = Config.getConfigBoolean("flatten_nested_object_to_string", true);
        
		List<String> list = new ArrayList<String>();
		list.add("list1");
		list.add("list2");
		
        Sysprop po = new Sysprop();
        po.getProperties().put(Config._ID, "111111");
        po.getProperties().put(Config._NAME, "222222");
        po.getProperties().put(Config._TAGS, list);
        
        Map<String, Object> data = ParaObjectUtils.getAnnotatedFields(po, flattenNestedObjectsToString);
        logger.info("map string: {}", ParaObjectUtils.getJsonWriter().writeValueAsString(data));
        po = ParaObjectUtils.setAnnotatedFields(data);
        logger.info("string object: {}", po);
	}
	
	@Test
	public void testSetAnnotatedFields(){
		Map<String, Object> map = new HashMap<String, Object>();
		
		long timestamp = 1390052381000L;
		map.put(Config._ID, "123");
		map.put(Config._TYPE, Utils.type(User.class));
		map.put(Config._NAME, "test");
		map.put(Config._TAGS, "[\"111111\",\"222222\"]");	// flattened JSON string
		map.put(Config._TIMESTAMP, Long.toString(timestamp));

		User obj = ParaObjectUtils.setAnnotatedFields(map);
		logger.info("para map user: {}", obj);

		User obj2 = new User("234");
		obj2.setActive(true);
		obj2 = ParaObjectUtils.setAnnotatedFields(obj2, map, null);
		logger.info("para user: {}", obj2);
		
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
