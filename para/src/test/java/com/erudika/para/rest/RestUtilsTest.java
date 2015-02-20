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
package com.erudika.para.rest;

import com.erudika.para.Para;
import com.erudika.para.cache.Cache;
import com.erudika.para.cache.MockCache;
import com.erudika.para.core.App;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.MockDAO;
import static com.erudika.para.rest.RestUtils.*;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Binder;
import com.google.inject.Module;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class RestUtilsTest {

	public RestUtilsTest() {
	}

	@BeforeClass
	public static void setUpClass() {
		System.setProperty("para.env", "embedded");
		System.setProperty("para.app_name", "para-test");
		System.setProperty("para.print_logo", "false");
		Para.initialize(new Module() {
			public void configure(Binder binder) {
				binder.bind(DAO.class).toInstance(new MockDAO());
				binder.bind(Cache.class).toInstance(new MockCache());
				binder.bind(Search.class).toInstance(Mockito.mock(Search.class));
			}
		});
	}

	@AfterClass
	public static void tearDownClass() {
		Para.destroy();
	}

	private InputStream getInputStream(Object obj) throws JsonProcessingException {
		if (obj != null) {
			return new ByteArrayInputStream(Utils.getJsonWriter().withType(obj.getClass()).writeValueAsBytes(obj));
		}
		return null;
	}

	@Test
	public void testGetReadResponse() {
		assertEquals(Status.NOT_FOUND.getStatusCode(), getReadResponse(null).getStatus());
		assertEquals(Status.OK.getStatusCode(), getReadResponse(new Tag("tag")).getStatus());
	}

	@Test
	public void testGetCreateUpdateDeleteResponse() throws JsonProcessingException {
		Tag t = new Tag("tag");
		App rootApp = new App(Config.APP_NAME_NS);
		assertEquals(Status.BAD_REQUEST.getStatusCode(), getCreateResponse(null, null, null).getStatus());

		assertEquals(Status.CREATED.getStatusCode(),
				getCreateResponse(rootApp, t.getType(), getInputStream(t)).getStatus());
		assertNotNull(t.getDao().read(t.getId()));

		Map<String, Object> map = new HashMap<String, Object>();
		assertEquals(Status.NOT_FOUND.getStatusCode(), getUpdateResponse(rootApp, null, null).getStatus());
		assertEquals(Status.OK.getStatusCode(), getUpdateResponse(rootApp, t, getInputStream(map)).getStatus());
		assertNotNull(t.getDao().read(t.getId()));

		assertEquals(Status.BAD_REQUEST.getStatusCode(), getDeleteResponse(rootApp, null).getStatus());
		assertEquals(Status.OK.getStatusCode(), getDeleteResponse(rootApp, t).getStatus());
		assertNull(t.getDao().read(t.getId()));
	}

	@Test
	public void testGetCoreTypes() {
		assertEquals("user", getCoreTypes().get("users"));
	}

	@Test
	public void testRegisterNewTypes() {
		App app = new App("test");
		app.setDao(new MockDAO());
		Sysprop custom = new Sysprop("ctype");
		custom.setType("cat");
		registerNewTypes(app, custom);
		assertEquals("cat", app.getDatatypes().get("cats"));
	}

	@Test
	public void testGetJSONResponse() {
		assertEquals(Status.BAD_REQUEST.getStatusCode(), getStatusResponse(null).getStatus());
		assertEquals(Status.OK.getStatusCode(), getStatusResponse(Status.OK).getStatus());
	}

	@Test
	public void testGetExceptionResponse() {
		assertEquals(Status.FORBIDDEN.getStatusCode(), getExceptionResponse(403, null).getStatus());
		assertEquals(MediaType.APPLICATION_JSON, getExceptionResponse(403, "").getMediaType().toString());
	}
}