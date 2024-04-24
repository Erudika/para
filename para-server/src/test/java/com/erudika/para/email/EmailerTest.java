/*
 * Copyright 2013-2022 Erudika. https://erudika.com
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
package com.erudika.para.email;

import com.erudika.para.core.email.Emailer;
import java.util.ArrayList;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Disabled
public abstract class EmailerTest {

	protected Emailer e;

	@Test
	public void testSendEmail() {
		assertFalse(e.sendEmail(null, null, null));
		assertFalse(e.sendEmail(new ArrayList<>(), null, "asd"));
		assertFalse(e.sendEmail(Collections.singletonList("test@test.com"), null, ""));
		assertTrue(e.sendEmail(Collections.singletonList("test@test.com"), null, "asd"));
	}
}