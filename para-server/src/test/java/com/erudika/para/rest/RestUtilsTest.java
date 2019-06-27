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
package com.erudika.para.rest;

import com.erudika.para.Para;
import com.erudika.para.ParaServer;
import com.erudika.para.cache.Cache;
import com.erudika.para.cache.MockCache;
import com.erudika.para.core.App;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.iot.IoTServiceFactory;
import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.MockDAO;
import com.erudika.para.queue.Queue;
import static com.erudika.para.rest.RestUtils.*;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Binder;
import com.google.inject.Module;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
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
		System.setProperty("para.cluster_name", "para-test");
		System.setProperty("para.print_logo", "false");
		ParaServer.initialize(new Module() {
			public void configure(Binder binder) {
				binder.bind(DAO.class).toInstance(new MockDAO());
				binder.bind(Cache.class).toInstance(new MockCache());
				binder.bind(Search.class).toInstance(Mockito.mock(Search.class));
				binder.bind(Queue.class).toInstance(Mockito.mock(Queue.class));
				binder.bind(IoTServiceFactory.class).toInstance(Mockito.mock(IoTServiceFactory.class));
			}
		});
	}

	@AfterClass
	public static void tearDownClass() {
		Para.destroy();
	}

	private InputStream getInputStream(Object obj) throws JsonProcessingException {
		if (obj != null) {
			return new ByteArrayInputStream(ParaObjectUtils.getJsonWriter().
					forType(obj.getClass()).writeValueAsBytes(obj));
		}
		return null;
	}

	@Test
	public void testGetReadResponse() {
		App app = new App("test");
		App root = new App(Config.getConfigParam("app_name", ""));
		assertEquals(Status.NOT_FOUND.getStatusCode(), getReadResponse(null, null).getStatus());
		assertEquals(Status.OK.getStatusCode(), getReadResponse(app, new Tag("tag")).getStatus());
		assertEquals(Status.OK.getStatusCode(), getReadResponse(root, new App("test1")).getStatus());
	}

	@Test
	public void testGetCreateUpdateDeleteResponse() throws JsonProcessingException {
		Tag t = new Tag("tag");
		App rootApp = new App(Config.getConfigParam("app_name", ""));
		App notRootApp = new App("anotherApp");
		assertEquals(Status.BAD_REQUEST.getStatusCode(), getCreateResponse(null, null, null).getStatus());
		assertEquals(Status.BAD_REQUEST.getStatusCode(), getCreateResponse(rootApp, rootApp.getType(),
				getInputStream(rootApp)).getStatus());
		assertEquals(Status.BAD_REQUEST.getStatusCode(), getCreateResponse(rootApp, rootApp.getType(),
				getInputStream(rootApp)).getStatus());
		assertEquals(Status.BAD_REQUEST.getStatusCode(), getCreateResponse(notRootApp, rootApp.getType(),
				getInputStream(rootApp)).getStatus());
		assertEquals(Status.BAD_REQUEST.getStatusCode(), getCreateResponse(rootApp, rootApp.getType(),
				getInputStream(notRootApp)).getStatus());
		assertEquals(Status.BAD_REQUEST.getStatusCode(), getUpdateResponse(notRootApp, rootApp,
				getInputStream(rootApp)).getStatus());
		assertEquals(Status.BAD_REQUEST.getStatusCode(), getCreateResponse(rootApp, notRootApp.getType(),
				getInputStream(notRootApp)).getStatus());
		assertEquals(Status.CREATED.getStatusCode(), getCreateResponse(rootApp, t.getType(),
				getInputStream(t)).getStatus());
		assertNotNull(CoreUtils.getInstance().getDao().read(t.getId()));

		Map<String, Object> map = new HashMap<>();
		assertEquals(Status.NOT_FOUND.getStatusCode(), getUpdateResponse(rootApp, null, null).getStatus());
		assertEquals(Status.OK.getStatusCode(), getUpdateResponse(rootApp, t, getInputStream(map)).getStatus());
		assertNotNull(CoreUtils.getInstance().getDao().read(t.getId()));
		assertEquals(Status.OK.getStatusCode(), getUpdateResponse(notRootApp, notRootApp,
				getInputStream(notRootApp)).getStatus());
		assertEquals(Status.OK.getStatusCode(), getUpdateResponse(rootApp, notRootApp,
				getInputStream(notRootApp)).getStatus());

		assertEquals(Status.NOT_FOUND.getStatusCode(), getDeleteResponse(rootApp, null).getStatus());
		assertEquals(Status.NOT_FOUND.getStatusCode(), getDeleteResponse(null, t).getStatus());
		assertEquals(Status.BAD_REQUEST.getStatusCode(), getDeleteResponse(notRootApp, rootApp).getStatus());
		assertEquals(Status.OK.getStatusCode(), getDeleteResponse(rootApp, t).getStatus());
		assertEquals(Status.OK.getStatusCode(), getDeleteResponse(rootApp, notRootApp).getStatus());
		assertEquals(Status.OK.getStatusCode(), getDeleteResponse(notRootApp, notRootApp).getStatus());
		assertNull(CoreUtils.getInstance().getDao().read(t.getId()));
	}

	@Test
	public void testRegisterNewTypes() {
		App app = new App("test");
		Sysprop custom = new Sysprop("ctype");
		custom.setType("cat");
		app.addDatatypes(custom);
		assertEquals("cat", app.getDatatypes().get("cats"));
	}

	@Test
	public void testGetJSONResponse() {
		assertEquals(Status.BAD_REQUEST.getStatusCode(), getStatusResponse(null).getStatus());
		assertEquals(Status.OK.getStatusCode(), getStatusResponse(Status.OK).getStatus());
	}

	@Test
	public void testGetExceptionResponse() {
		assertEquals(Status.FORBIDDEN.getStatusCode(), GenericExceptionMapper.getExceptionResponse(403, null).getStatus());
		assertEquals(MediaType.APPLICATION_JSON, GenericExceptionMapper.getExceptionResponse(403, "").getMediaType().toString());
	}

	@Test
	public void testExtractAccessKey() {
		HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
		Mockito.when(req.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
		String appid = "app:test-123";

		assertEquals("", extractAccessKey(null));
		assertEquals(null, extractAccessKey(req));

		Mockito.when(req.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Anonymous " + appid);
		assertEquals(appid, extractAccessKey(req));

		Mockito.when(req.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
		Mockito.when(req.getParameter("X-Amz-Credential")).thenReturn("AWS4-HMAC-SHA256 Credential=" + appid + "/123/");
		assertEquals(appid, extractAccessKey(req));

		Mockito.when(req.getParameter("X-Amz-Credential")).thenReturn(null);
		Mockito.when(req.getParameter("accessKey")).thenReturn(appid);
		assertEquals(appid, extractAccessKey(req));

		Mockito.when(req.getParameter("accessKey")).thenReturn(null);
		Mockito.when(req.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("AWS4-HMAC-SHA256 Credential=" + appid + "/0/");
		assertEquals(appid, extractAccessKey(req));
	}

	@Test
	public void testExtractResourcePath() {
		HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
		Mockito.when(req.getRequestURI()).thenReturn("");
		assertEquals(extractResourcePath(null), "");
		assertEquals(extractResourcePath(req), "");

		Mockito.when(req.getRequestURI()).thenReturn("/v1");
		assertEquals("", extractResourcePath(req));

		Mockito.when(req.getRequestURI()).thenReturn("/v1/");
		assertEquals("", extractResourcePath(req));

		Mockito.when(req.getRequestURI()).thenReturn("/v1/_");
		assertEquals("_", extractResourcePath(req));

		Mockito.when(req.getRequestURI()).thenReturn("/v1/_test");
		assertEquals("_test", extractResourcePath(req));

		Mockito.when(req.getRequestURI()).thenReturn("/v1/_test/path/id");
		assertEquals("_test/path/id", extractResourcePath(req));

		// new feature - specific resource paths
		Mockito.when(req.getRequestURI()).thenReturn("/v2.0/posts/123");
		assertEquals("posts/123", extractResourcePath(req));
	}

	@Test
	public void testReadResourcePath() {
		String appid = "test-app-1";
		App app = new App(appid);
		Sysprop s = new Sysprop();
		s.setAppid(appid);
		s.setName("noname");

		app.create();
		Para.getDAO().create(appid, s);

		assertNull(readResourcePath(appid, null));
		assertNull(readResourcePath(appid, ""));
		assertNull(readResourcePath(null, "/"));
		assertNull(readResourcePath(appid, "users"));
		assertNull(readResourcePath(appid, "sysprops"));
		assertNull(readResourcePath(appid, "sysprop"));
		assertNull(readResourcePath(appid, "sysprop/"));

		assertNotNull(readResourcePath(appid, "sysprop/" + s.getId()));
		assertNull(readResourcePath(appid, "/v1/sysprop/" + s.getId()));
		assertNull(readResourcePath(appid, "one/two/three/" + s.getId()));
		assertNotNull(readResourcePath(appid, "type/" + s.getId() + "/subresource/test"));
		assertEquals(s, readResourcePath(appid, "type/" + s.getId() + "/subresource/test"));
		assertNotNull(readResourcePath(appid, "/type/" + s.getId() + "/subresource/test?query=string"));

		Para.getDAO().delete(appid, s);
		assertNull(readResourcePath(appid, "sysprop/" + s.getId()));
		app.delete();
	}
}