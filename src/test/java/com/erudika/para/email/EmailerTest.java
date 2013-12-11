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
package com.erudika.para.email;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@RunWith(Parameterized.class)
public class EmailerTest {
	
	@Parameterized.Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][]{
			{new MockEmailer()}, 
			{new AWSEmailer()}
		});
	}
	
	private Emailer e;
	
	public EmailerTest(Emailer e) {
		this.e = e;
	}

	@Test
	public void testSendEmail() {
		assertFalse(e.sendEmail(null, null, null));
		assertFalse(e.sendEmail(new ArrayList<String>(), null, "asd"));
		assertFalse(e.sendEmail(Collections.singletonList("test@test.com"), null, ""));
		assertTrue(e.sendEmail(Collections.singletonList("test@test.com"), null, "asd"));
	}
}