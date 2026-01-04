/*
 * Copyright 2013-2026 Erudika. https://erudika.com
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

import com.erudika.para.core.App;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.server.ParaServer;
import com.erudika.para.server.rest.GenericExceptionMapper;
import static com.erudika.para.server.rest.RestUtils.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class RestUtilsTest {

	public RestUtilsTest() {
	}

	@BeforeAll
	public static void setUpClass() {
		System.setProperty("para.env", "embedded");
		System.setProperty("para.app_name", "para-test");
		System.setProperty("para.cluster_name", "para-test");
		System.setProperty("para.print_logo", "false");

		System.setProperty("para.search", "LuceneSearch");
		//		ParaServer.initialize(dao, new MockCache(), new LuceneSearch(dao), new LocalQueue(), new LocalFileStore());
		SpringApplication app = new SpringApplication(ParaServer.class);
		app.setWebApplicationType(WebApplicationType.SERVLET);
		app.setBannerMode(Banner.Mode.OFF);
		app.run();
	}

	@AfterAll
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
		App root = new App(Para.getConfig().appName());
		assertEquals(HttpStatus.NOT_FOUND, getReadResponse(null, null).getStatusCode());
		assertEquals(HttpStatus.OK, getReadResponse(app, new Tag("tag")).getStatusCode());
		assertEquals(HttpStatus.OK, getReadResponse(root, new App("test1")).getStatusCode());
	}

	@Test
	public void testGetCreateUpdateDeleteResponse() throws JsonProcessingException {
		Tag t = new Tag("tag");
		App rootApp = new App(Para.getConfig().appName());
		App notRootApp = new App("anotherApp");
		assertEquals(HttpStatus.BAD_REQUEST, getCreateResponse(null, null, null).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST, getCreateResponse(rootApp, rootApp.getType(),
				getInputStream(rootApp)).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST, getCreateResponse(rootApp, rootApp.getType(),
				getInputStream(rootApp)).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST, getCreateResponse(notRootApp, rootApp.getType(),
				getInputStream(rootApp)).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST, getCreateResponse(rootApp, rootApp.getType(),
				getInputStream(notRootApp)).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST, getUpdateResponse(notRootApp, rootApp,
				getInputStream(rootApp)).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST, getCreateResponse(rootApp, notRootApp.getType(),
				getInputStream(notRootApp)).getStatusCode());
		assertEquals(HttpStatus.CREATED, getCreateResponse(rootApp, t.getType(),
				getInputStream(t)).getStatusCode());
		assertNotNull(CoreUtils.getInstance().getDao().read(t.getId()));

		Map<String, Object> map = new HashMap<>();
		assertEquals(HttpStatus.NOT_FOUND, getUpdateResponse(rootApp, null, null).getStatusCode());
		assertEquals(HttpStatus.OK, getUpdateResponse(rootApp, t, getInputStream(map)).getStatusCode());
		assertNotNull(CoreUtils.getInstance().getDao().read(t.getId()));
		assertEquals(HttpStatus.OK, getUpdateResponse(notRootApp, notRootApp,
				getInputStream(notRootApp)).getStatusCode());
		assertEquals(HttpStatus.OK, getUpdateResponse(rootApp, notRootApp,
				getInputStream(notRootApp)).getStatusCode());

		assertEquals(HttpStatus.NOT_FOUND, getDeleteResponse(rootApp, null).getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, getDeleteResponse(null, t).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST, getDeleteResponse(notRootApp, rootApp).getStatusCode());
		assertEquals(HttpStatus.OK, getDeleteResponse(rootApp, t).getStatusCode());
		assertEquals(HttpStatus.OK, getDeleteResponse(rootApp, notRootApp).getStatusCode());
		assertEquals(HttpStatus.OK, getDeleteResponse(notRootApp, notRootApp).getStatusCode());
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
		assertEquals(HttpStatus.BAD_REQUEST, getStatusResponse(null).getStatusCode());
		assertEquals(HttpStatus.OK, getStatusResponse(HttpStatus.OK).getStatusCode());
	}

	@Test
	public void testGetExceptionResponse() {
		assertEquals(HttpStatus.FORBIDDEN, GenericExceptionMapper.getExceptionResponse(403, null).getStatusCode());
		assertEquals(MediaType.APPLICATION_JSON, GenericExceptionMapper.getExceptionResponse(403, "").getHeaders().getContentType());
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