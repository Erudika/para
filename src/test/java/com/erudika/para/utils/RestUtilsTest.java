/*
 * Copyright 2013-2014 Erudika. http://erudika.com
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

import com.erudika.para.core.Tag;
import com.erudika.para.persistence.MockDAO;
import org.junit.Test;
import static org.junit.Assert.*;
import javax.ws.rs.core.Response.Status;
import static com.erudika.para.rest.RestUtils.*;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class RestUtilsTest {

	public RestUtilsTest() {
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
		assertEquals(Status.CREATED.getStatusCode(), getCreateResponse(t).getStatus());
		assertNotNull(t.getDao().read(t.getId()));

		Map<String, Object> map = new HashMap<String, Object>();
		assertEquals(Status.NOT_FOUND.getStatusCode(), getUpdateResponse(null, map).getStatus());
		assertEquals(Status.OK.getStatusCode(), getUpdateResponse(t, map).getStatus());
		assertNotNull(t.getDao().read(t.getId()));

		assertEquals(Status.BAD_REQUEST.getStatusCode(), getDeleteResponse(null).getStatus());
		assertEquals(Status.OK.getStatusCode(), getDeleteResponse(t).getStatus());
		assertNull(t.getDao().read(t.getId()));
	}

//	@Test
//	public void testGetJSONResponse() {
//		assertEquals(Status.BAD_REQUEST.getStatusCode(), getStatusResponse(null).getStatus());
//		assertEquals(Status.OK.getStatusCode(), getStatusResponse(Status.OK).getStatus());
//	}
}