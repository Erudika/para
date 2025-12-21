/*
 * Copyright 2013-2026 Erudika. http://erudika.com
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
package com.erudika.para.core.utils;

import java.util.ArrayList;
import java.util.HashSet;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Concurrency test for ID generation in Para.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Execution(ExecutionMode.CONCURRENT)
public class IdGenTest {
	final ArrayList<String> ids = new ArrayList<String>();

	@BeforeEach
	public void setup() throws Exception {
	}

	@Test
//	@ThreadCount(16)
	public void testGetNewId() {
		ids.add(Utils.getNewId());
	}

	@AfterEach
	public void tearDown() {
		HashSet<String> uniqueIds = new HashSet<String>(ids.size());
		for (String id : ids) {
			if (uniqueIds.contains(id)) {
				fail("Duplicate ID generated: " + id);
			}
			uniqueIds.add(id);
		}
	}
}
