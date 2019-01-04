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

import com.erudika.para.core.App;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import static com.erudika.para.validation.ValidationUtils.*;
import static com.erudika.para.validation.Constraint.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
		assertTrue(validateObject(app).length == 0);
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
		assertFalse(app.getValidationConstraints().get(s1.getType()).isEmpty());
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
	public void testGetCoreValidationConstraints() {
		assertTrue(getCoreValidationConstraints().containsKey("app"));
	}

	@Test
	public void testAllConstraints() {
		// null is ok, because value might not be required
		assertTrue(email().isValid(null));
		assertTrue(email().isValid("abc@de.com"));
		assertFalse(email().isValid("abc@de."));
		assertFalse(email().isValid("abc@.c"));
		assertFalse(email().isValid(123));
		assertFalse(email().isValid(" "));

		assertTrue(falsy().isValid(null));
		assertTrue(falsy().isValid("false"));
		assertTrue(falsy().isValid("FALSE"));
		assertTrue(falsy().isValid(false));
		assertTrue(falsy().isValid("fals"));
		assertTrue(falsy().isValid(" "));
		assertFalse(falsy().isValid("true"));
		assertFalse(falsy().isValid(true));

		assertTrue(truthy().isValid(null));
		assertTrue(truthy().isValid("true"));
		assertTrue(truthy().isValid("True"));
		assertTrue(truthy().isValid(true));
		assertFalse(truthy().isValid(false));
		assertFalse(truthy().isValid("a"));
		assertFalse(truthy().isValid(" "));

		assertFalse(required().isValid(null));
		assertFalse(required().isValid(" "));
		assertTrue(required().isValid("text"));
		assertTrue(required().isValid(1));
		assertTrue(required().isValid(true));

		long now = System.currentTimeMillis();
		assertTrue(future().isValid(null));
		assertTrue(future().isValid(new Date(now + 1000)));
		assertFalse(future().isValid(new Date(now - 1000)));

		assertTrue(past().isValid(null));
		assertTrue(past().isValid(new Date(now - 1000)));
		assertFalse(past().isValid(new Date(now + 1000)));

		assertTrue(url().isValid(null));
		assertTrue(url().isValid("http://abc.co"));
		assertFalse(url().isValid("htp://abc.co"));
		assertFalse(url().isValid("abc.com"));
		assertFalse(url().isValid(" "));
		assertFalse(url().isValid(false));

		assertTrue(min(3).isValid(null));
		assertTrue(min(3).isValid(3));
		assertTrue(min(3).isValid(4));
		assertFalse(min(4).isValid(3));
		assertFalse(min(2).isValid("3"));
		assertFalse(min(4).isValid(true));
		assertFalse(min(null).isValid(" "));
		assertFalse(min(null).isValid(3));

		assertTrue(max(3).isValid(null));
		assertTrue(max(3).isValid(3));
		assertTrue(max(4).isValid(3));
		assertFalse(max(3).isValid(4));
		assertFalse(max(2).isValid("3"));
		assertFalse(max(4).isValid(true));
		assertFalse(max(null).isValid(" "));
		assertFalse(max(null).isValid(3));

		assertTrue(size(2, 3).isValid(null));
		assertTrue(size(2, 3).isValid("xx"));
		assertFalse(size(3, 2).isValid("xx"));
		assertFalse(size(2, 3).isValid("xxxx"));
		assertFalse(size(2, 3).isValid("x"));
		assertTrue(size(0, 0).isValid(""));
		assertTrue(size(0, 0).isValid(new String[0]));
		assertTrue(size(1, 2).isValid(new String[]{"a", "b"}));
		assertTrue(size(1, 2).isValid(Arrays.asList(new String[]{"a", "b"})));
		assertTrue(size(1, 2).isValid(Collections.singletonMap("a", "b")));

		assertTrue(digits(2, 2).isValid(null));
		assertTrue(digits(2, 2).isValid("22.22"));
		assertFalse(digits(2, 2).isValid("22.222"));
		assertFalse(digits(1, 2).isValid("2.222"));
		assertTrue(digits(1, 2).isValid("2.22"));
		assertTrue(digits(1, 2).isValid(2.22));
		assertTrue(digits(1, 2).isValid(0));
		assertFalse(digits(1, 2).isValid(12));
		assertFalse(digits(0, 2).isValid(1));

		assertTrue(pattern(null).isValid(null));
		assertTrue(pattern("").isValid(""));
		assertTrue(pattern("[ab]+").isValid("bababa"));
		assertTrue(pattern("\\.[ab]+").isValid(".babababa"));
		assertFalse(pattern("").isValid(" "));
	}
}
