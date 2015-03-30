/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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

import com.erudika.para.validation.ValidationUtils;
import com.erudika.para.core.App;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import com.erudika.para.rest.RestUtils;
import static com.erudika.para.validation.ValidationUtils.*;
import static com.erudika.para.validation.Constraint.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ValidationUtilsTest {


	@Test
	public void testGetValidator() {
		assertNotNull(getValidator());
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
	public void testValidateObject() {
		assertTrue(validateObject(null).length > 0);
		assertEquals(0, validateObject(new Tag("test")).length);

		App app = new App(Config.PARA);
		assertFalse(validateObject(app).length == 0);
		app.resetSecret();
		assertTrue(validateObject(app).length == 0);

		Sysprop s1 = new Sysprop("s1");
		assertTrue(validateObject(s1).length == 0);
		assertTrue(validateObject(app, s1).length == 0);

		s1.setType("cat");
		assertTrue(validateObject(s1).length == 0);
		assertTrue(validateObject(app, s1).length == 0);

		assertTrue(app.getValidationConstraints().isEmpty());
		app.addValidationConstraint(null, null, null);
		assertTrue(app.getValidationConstraints().isEmpty());

		// required
		app.addValidationConstraint(s1.getType(), "paws", required());
		assertFalse(getValidationConstraints(app, s1.getType()).isEmpty());
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("paws", 2);
		assertTrue(validateObject(app, s1).length == 0);

		// min
		app.addValidationConstraint(s1.getType(), "paws", min(4L));
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("paws", 4);
		assertTrue(validateObject(app, s1).length == 0);

		// max
		app.addValidationConstraint(s1.getType(), "paws", max(5L));
		s1.addProperty("paws", 6);
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("paws", 5);
		assertTrue(validateObject(app, s1).length == 0);

		// size
		app.addValidationConstraint(s1.getType(), "name", size(2, 3));
		assertFalse(validateObject(app, s1).length == 0);
		s1.setName("Bob");
		assertTrue(validateObject(app, s1).length == 0);
		s1.setName("Bobsy");
		assertFalse(validateObject(app, s1).length == 0);
		s1.setName("Bob");
		// null values are allowed and treated as valid
		app.addValidationConstraint(s1.getType(), "fur", size(2, 3));
		assertTrue(validateObject(app, s1).length == 0);
		// ints are a wrong type - not valid
		s1.addProperty("fur", 3);
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("fur", "yes");
		assertTrue(validateObject(app, s1).length == 0);
		s1.addProperty("fur", new ArrayList <String>(0));
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("fur", Arrays.asList(new String[]{"one", "two", "three"}));
		assertTrue(validateObject(app, s1).length == 0);
		s1.addProperty("fur", new HashMap<String, String>(0));
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("fur", new HashMap<String, String>() {{
			put("1", "1");
			put("2", "2");
			put("3", "3");
		}});
		assertTrue(validateObject(app, s1).length == 0);
		s1.addProperty("fur", new String[0]);
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("fur", new String[]{"one", "two", "three"});
		assertTrue(validateObject(app, s1).length == 0);

		// email
		app.addValidationConstraint(s1.getType(), "eemail", email());
		assertTrue(validateObject(app, s1).length == 0);
		s1.addProperty("eemail", 2);
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("eemail", "a@..");
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("eemail", "a@bob.com");
		assertTrue(validateObject(app, s1).length == 0);

		// digits
		app.addValidationConstraint(s1.getType(), "specialnum", digits(4, 2));
		s1.addProperty("specialnum", "??");
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("specialnum", 12.34);
		assertTrue(validateObject(app, s1).length == 0);
		s1.addProperty("specialnum", 1234.567);
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("specialnum", 12345.67);
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("specialnum", "1234.5");
		assertTrue(validateObject(app, s1).length == 0);

		// pattern
		app.addValidationConstraint(s1.getType(), "regex", pattern("^test\\sok=$"));
		s1.addProperty("regex", "??");
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("regex", "test ok=");
		assertTrue(validateObject(app, s1).length == 0);

		// false
		app.addValidationConstraint(s1.getType(), "fals", falsy());
		s1.addProperty("fals", "test");
		assertTrue(validateObject(app, s1).length == 0);
		s1.addProperty("fals", "false");
		assertTrue(validateObject(app, s1).length == 0);
		s1.addProperty("fals", "NO");
		assertTrue(validateObject(app, s1).length == 0);
		s1.addProperty("fals", "0");
		assertTrue(validateObject(app, s1).length == 0);
		s1.addProperty("fals", 0);
		assertTrue(validateObject(app, s1).length == 0);
		s1.addProperty("fals", false);
		assertTrue(validateObject(app, s1).length == 0);
		s1.addProperty("fals", "true");
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("fals", true);
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("fals", false);

		// true
		app.addValidationConstraint(s1.getType(), "tru", truthy());
		s1.addProperty("tru", "test");
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("tru", "false");
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("tru", true);
		assertTrue(validateObject(app, s1).length == 0);
		s1.addProperty("tru", "true");
		assertTrue(validateObject(app, s1).length == 0);

		// future
		app.addValidationConstraint(s1.getType(), "future", future());
		s1.addProperty("future", 1234);
		assertFalse(validateObject(app, s1).length == 0);
//		s1.addProperty("future", System.currentTimeMillis());
//		assertFalse(validateObject(app, s1).length == 0);	// might fail on some machines
		s1.addProperty("future", System.currentTimeMillis() + 10000);
		assertTrue(validateObject(app, s1).length == 0);
		s1.addProperty("future", new Date(System.currentTimeMillis() + 10000));
		assertTrue(validateObject(app, s1).length == 0);

		// past
		app.addValidationConstraint(s1.getType(), "past", past());
		s1.addProperty("past", System.currentTimeMillis() + 10000);
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("past", 1234);
		assertTrue(validateObject(app, s1).length == 0);
//		s1.addProperty("past", System.currentTimeMillis());
//		assertFalse(validateObject(app, s1).length == 0);	// might fail on some machines
		s1.addProperty("past", new Date(System.currentTimeMillis()-1));
		assertTrue(validateObject(app, s1).length == 0);

		// url
		app.addValidationConstraint(s1.getType(), "url", url());
		s1.addProperty("url", 1234);
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("url", "http");
		assertFalse(validateObject(app, s1).length == 0);
		s1.addProperty("url", "http://www.a.com");
		assertTrue(validateObject(app, s1).length == 0);
	}

	@Test
	public void testGetAllValidationConstraints() {
		assertTrue(getAllValidationConstraints(null, RestUtils.getCoreTypes().values()).length() > 2);
//		assertTrue(getAllValidationConstraints(null).length() > 2);
	}

	@Test
	public void testGetValidationConstraints() {
		App app = new App(Config.PARA);
		assertTrue(ValidationUtils.getValidationConstraints(app, null).isEmpty());
		assertFalse(ValidationUtils.getValidationConstraints(app, "tag").isEmpty());
	}

	@Test
	public void testAddRemoveValidationConstraint() {
		App app = new App(Config.PARA);

		// add
		app.addValidationConstraint("testtype", "urlField", url());
		assertTrue(ValidationUtils.getValidationConstraints(app, "testtype").containsKey("urlField"));
		assertTrue(ValidationUtils.getValidationConstraints(app, "testtype").get("urlField").containsKey("url"));

		// remove
		app.removeValidationConstraint("testtype", "urlField", "url");
		assertTrue(ValidationUtils.getValidationConstraints(app, "testtype").containsKey("urlField"));
		assertTrue(ValidationUtils.getValidationConstraints(app, "testtype").get("urlField").isEmpty());
		assertFalse(ValidationUtils.getValidationConstraints(app, "testtype").get("urlField").containsKey("url"));
	}
}
