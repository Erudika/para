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
package com.erudika.para.client;

import com.erudika.para.Para;
import com.erudika.para.ParaServer;
import com.erudika.para.core.Address;
import com.erudika.para.core.App;
import com.erudika.para.core.App.AllowedMethods;
import static com.erudika.para.core.App.AllowedMethods.GET;
import static com.erudika.para.core.App.AllowedMethods.OWN;
import static com.erudika.para.core.App.AllowedMethods.PATCH;
import static com.erudika.para.core.App.AllowedMethods.POST;
import static com.erudika.para.core.App.AllowedMethods.PUT;
import static com.erudika.para.core.App.AllowedMethods.READ;
import static com.erudika.para.core.App.AllowedMethods.READ_AND_WRITE;
import static com.erudika.para.core.App.AllowedMethods.READ_WRITE;
import static com.erudika.para.core.App.AllowedMethods.WRITE;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import com.erudika.para.core.Votable;
import com.erudika.para.core.Vote;
import com.erudika.para.security.AuthenticatedUserDetails;
import com.erudika.para.security.filters.FacebookAuthFilter;
import com.erudika.para.security.SecurityModule;
import com.erudika.para.security.UserAuthentication;
import com.erudika.para.utils.Config;
import static com.erudika.para.validation.Constraint.*;
import com.erudika.para.utils.HumanTime;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import com.google.inject.util.Modules;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ParaClientIT {

	private static final Logger logger = LoggerFactory.getLogger(ParaClientIT.class);
	private static ParaClient pc;
	private static ParaClient pc2;
	private static ParaClient pcc;
	private static final String catsType = "cat";
	private static final String dogsType = "dog";
	private static final String batsType = "bat";
	private static final String APP_NAME = "para-test";
	private static final String APP_NAME_CHILD = "para-test-child";

	protected static Sysprop u;
	protected static Sysprop u1;
	protected static Sysprop u2;
	protected static Tag t;
	protected static Sysprop s1;
	protected static Sysprop s2;
	protected static Sysprop s3;
	protected static Address a1;
	protected static Address a2;
	protected static User fbUser;

	@BeforeClass
	public static void setUpClass() throws InterruptedException, IOException {
		System.setProperty("para.env", "embedded");
		System.setProperty("para.print_logo", "false");
		System.setProperty("para.app_name", APP_NAME);
		System.setProperty("para.cluster_name", "para-test");
		System.setProperty("para.search", "LuceneSearch");
		String endpoint = "http://localhost:8080";

		fbUser = new User("fbUser_1");
		fbUser.setEmail("test@user.com");
		fbUser.setIdentifier("fb:1234");
		fbUser.setGroups("users");
		fbUser.setActive(true);
		fbUser.setAppid(APP_NAME);

		UserAuthentication ua = new UserAuthentication(new AuthenticatedUserDetails(fbUser));
		SpringApplication app = new SpringApplication(ParaServer.class);
		app.setWebApplicationType(WebApplicationType.SERVLET);
		app.setBannerMode(Banner.Mode.OFF);
		SecurityModule secMod = new SecurityModule();
		FacebookAuthFilter fbaf = new FacebookAuthFilter("/");
		fbaf = spy(fbaf);
		when(fbaf.getOrCreateUser((App) any(), anyString())).thenReturn(ua);
		secMod.setFacebookFilter(fbaf);
		ParaServer.initialize(Modules.override(ParaServer.getCoreModules()).with(secMod));
		app.run();

		CoreUtils.getInstance().setDao(Para.getDAO());
		CoreUtils.getInstance().setSearch(Para.getSearch());

		ParaClient temp = new ParaClient("x", "x");
		temp.setEndpoint(endpoint);

		assertNull(temp.me());
		assertTrue(temp.newId().isEmpty());

		App rootApp = Para.getDAO().read(App.id(APP_NAME));
		if (rootApp == null) {
			rootApp = new App(APP_NAME);
			rootApp.setName(APP_NAME);
			rootApp.setSharingIndex(false);
			rootApp.create();
		} else {
			rootApp.resetSecret();
			rootApp.create();
		}

		Map<String, String> creds = Para.newApp(APP_NAME_CHILD, "Child app with routing", false, false);

		pc = new ParaClient(App.id(APP_NAME), rootApp.getSecret());
		pc.setEndpoint(endpoint);
		pc2 = new ParaClient(App.id(APP_NAME), rootApp.getSecret());
		pc2.setEndpoint(endpoint);
		pcc = new ParaClient(App.id(APP_NAME_CHILD), creds.get("secretKey"));
		pcc.setEndpoint(endpoint);
		logger.info("accessKey: {}, secretKey: {}", rootApp.getId(), rootApp.getSecret());

		u = new Sysprop("c111");
		u.setName("John Doe");
		u.setTimestamp(Utils.timestamp());
		u.setTags(CoreUtils.getInstance().addTags(u.getTags(), "one", "two", "three"));

		u1 = new Sysprop("c222");
		u1.setName("Joe Black");
		u1.setTimestamp(Utils.timestamp());
		u1.setTags(CoreUtils.getInstance().addTags(u1.getTags(), "two", "four", "three"));

		u2 = new Sysprop("c333");
		u2.setName("Ann Smith");
		u2.setTimestamp(Utils.timestamp());
		u2.setTags(CoreUtils.getInstance().addTags(u2.getTags(), "four", "five", "three"));

		t = new Tag("test");
		t.setCount(3);
		t.setTimestamp(Utils.timestamp());

		a1 = new Address("adr1");
		a1.setName("Place 1");
		a1.setAddress("NYC");
		a1.setCountry("US");
		a1.setLatlng("40.67,-73.94");
		a1.setParentid(u.getId());
		a1.setCreatorid(u.getId());

		a2 = new Address("adr2");
		a2.setName("Place 2");
		a2.setAddress("NYC");
		a2.setCountry("US");
		a2.setLatlng("40.69,-73.95");
		a2.setParentid(t.getId());
		a2.setCreatorid(t.getId());

		s1 = new Sysprop("s1");
		s1.addProperty("text", "This is a little test sentence. Testing, one, two, three.");
		s1.setTimestamp(Utils.timestamp());

		s2 = new Sysprop("s2");
		s2.addProperty("text", "We are testing this thing. This sentence is a test. One, two.");
		s2.setTimestamp(Utils.timestamp());

		s3 = new Sysprop("уникод");
		s3.setType("тип");
		s3.setTimestamp(Utils.timestamp());

		assertNotNull(fbUser.create());
		pc.createAll(Arrays.asList(u, u1, u2, t, s1, s2, s3, a1, a2));
//		Thread.sleep(1000);
	}

	@AfterClass
	public static void tearDownClass() {
		System.setProperty("para.clients_can_access_root_app", "false");
		Para.getDAO().delete(new App(APP_NAME_CHILD));
		Para.getDAO().delete(new App(APP_NAME));
		Para.getDAO().deleteAll(Arrays.asList(u, u1, u2, t, s1, s2, a1, a2, fbUser));
		Para.destroy();
	}

	@Test
	public void testCRUD() {
		assertNull(pc.create(null));

		Tag tag1 = new Tag("test1");
		tag1.setVersion(1L); // enable optimistic locking
		Tag t1 = pc.create(tag1);
		User ux = null;
		try {
			// validation fails
			ux = pc.create(new User("u1"));
		} catch (Exception e) {}

		assertNotNull(t1);
		assertNull(ux);

		assertNull(pc.read(null, null));
		assertNull(pc.read("", ""));

		Tag trID = pc.read(t1.getId());
		assertNotNull(trID);
		assertNotNull(trID.getTimestamp());
		assertEquals(t1.getTag(), trID.getTag());

		Tag tr = pc.read(t1.getType(), t1.getId());
		assertNotNull(tr);
		assertNotNull(tr.getTimestamp());
		assertEquals(t1.getTag(), tr.getTag());
		assertEquals(t1.getVersion(), tr.getVersion());

		// Not all DAOs support this, therefore we skip these tests
//		tr.setCount(15);
//		tr.setVersion(-1L);
//		Tag tu = pc.update(tr);
//		assertNotNull(tu);
//		assertNotEquals(Long.valueOf(-1), tu.getVersion());
//		tr.setVersion(5L);
//		tu = pc.update(tr);
//		assertNotEquals(Long.valueOf(5), tu.getVersion());

//		assertNull(pc.update(new Tag("null")));
//		assertEquals(tu.getCount(), tr.getCount());
//		assertNotNull(tu.getUpdated());
//
//		tu.setVersion(0L); // disable optimistic locking
//		assertEquals(Long.valueOf(0L), pc.create(tu).getVersion()); // overwrite to disable locking

		Sysprop s = new Sysprop();
		s.setType(dogsType);
		s.addProperty("foo", "bark!");
		s = pc.create(s);

		Sysprop dog = pc.read(dogsType, s.getId());
		assertTrue(dog.hasProperty("foo"));
		assertEquals("bark!", dog.getProperty("foo"));

		pc.delete(t1);
		pc.delete(dog);
		assertNull(pc.read(tr.getType(), tr.getId()));

		// app must not overwrite itself
		assertNull(pc.create(new App(APP_NAME)));
		// app can read itself
		assertNotNull(pc.read(Utils.type(App.class), APP_NAME));
	}

	@Test
	public void testBatchCRUD() throws InterruptedException {
		ArrayList<Sysprop> dogs = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			Sysprop s = new Sysprop();
			s.setType(dogsType);
			s.addProperty("foo", "bark!");
			dogs.add(s);
		}

		assertTrue(pc.createAll(null).isEmpty());
		List<Sysprop> l1 = pc.createAll(dogs);
		assertEquals(3, l1.size());
		assertNotNull(l1.get(0).getId());

		assertTrue(pc.readAll(null).isEmpty());
		ArrayList<String> nl = new ArrayList<>(3);
		assertTrue(pc.readAll(nl).isEmpty());
		nl.add(l1.get(0).getId());
		nl.add(l1.get(1).getId());
		nl.add(l1.get(2).getId());
		List<Sysprop> l2 = pc.readAll(nl);
		assertEquals(3, l2.size());
		assertEquals(l1.get(0).getId(), l2.get(0).getId());
		assertEquals(l1.get(1).getId(), l2.get(1).getId());
		assertTrue(l2.get(0).hasProperty("foo"));
		assertEquals("bark!", l2.get(0).getProperty("foo"));

		assertTrue(pc.updateAll(null).isEmpty());

		Sysprop part1 = new Sysprop(l1.get(0).getId());
		Sysprop part2 = new Sysprop(l1.get(1).getId());
		Sysprop part3 = new Sysprop(l1.get(2).getId());
		part1.setType(dogsType);
		part2.setType(dogsType);
		part3.setType(dogsType);

		part1.addProperty("custom", "prop");
		part1.setName("NewName1");
		part2.setName("NewName2");
		part3.setName("NewName3");

		List<Sysprop> l3 = pc.updateAll(Arrays.asList(part1, part2, part3));

		assertTrue(l3.get(0).hasProperty("custom"));
		assertEquals(dogsType, l3.get(0).getType());
		assertEquals(dogsType, l3.get(1).getType());
		assertEquals(dogsType, l3.get(2).getType());

		assertEquals(part1.getName(), l3.get(0).getName());
		assertEquals(part2.getName(), l3.get(1).getName());
		assertEquals(part3.getName(), l3.get(2).getName());

		pc.deleteAll(nl);
//		Thread.sleep(1000);

		List<Sysprop> l4 = pc.list(dogsType);
		assertTrue(l4.isEmpty());

		assertTrue(pc.getApp().getDatatypes().containsValue(dogsType));
	}

	@Test
	public void testBatchCRUDForChildApp() throws InterruptedException {
		ArrayList<Sysprop> articles = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			Sysprop s = new Sysprop();
			s.setType("article");
			// DO NOT SET appid, must be always set automatically on the server
			// depending on which app is currently in context (i.e. making the requests)
			s.addProperty("text", "a b c");
			articles.add(s);
		}

		Para.getDAO().deleteAll(pcc.findQuery("article", "*"));
		List<Sysprop> l1 = pcc.createAll(articles);
		assertEquals(3, l1.size());
		assertNotNull(l1.get(0).getId());
		assertNotNull(l1.get(1).getId());
		assertNotNull(l1.get(2).getId());
		assertTrue(l1.get(0).hasProperty("text"));
		assertEquals("a b c", l1.get(0).getProperty("text"));

		assertEquals(APP_NAME_CHILD, l1.get(0).getAppid());
		assertEquals(APP_NAME_CHILD, l1.get(1).getAppid());
		assertEquals(APP_NAME_CHILD, l1.get(2).getAppid());

		// test if appid is set on partial update
		// test if old data is not lost on partial update
		// test if new custom properties are merged with old ones in case of partial update
		Sysprop part1 = new Sysprop(l1.get(0).getId());
		Sysprop part2 = new Sysprop(l1.get(1).getId());
		Sysprop part3 = new Sysprop(l1.get(2).getId());
		// DO NOT SET appid - should work without it in partial updateAll()
		part1.setType("update_must_not_change_type");
		part2.setType("update_must_not_change_type");
		part3.setType("update_must_not_change_type");

		part1.addProperty("text2", "d e f");
		part2.addProperty("text2", "d e f");
		part2.setName("NewName2");
		part3.setName("NewName3");

		List<Sysprop> lu = pcc.updateAll(Arrays.asList(part1, part2, part3));
		assertEquals(3, lu.size());
		List<Sysprop> l2 = pcc.readAll(Arrays.asList(part1.getId(), part2.getId(), part3.getId()));
		assertEquals(3, l2.size());
		assertTrue(l2.get(0).hasProperty("text"));
		assertTrue(l2.get(1).hasProperty("text"));
		assertTrue(l2.get(2).hasProperty("text"));
		assertTrue(l2.get(0).hasProperty("text2"));
		assertTrue(l2.get(1).hasProperty("text2"));
		assertEquals(2, l2.get(0).getProperties().size());
		assertEquals("a b c", l2.get(0).getProperty("text"));
		assertEquals(part2.getName(), l2.get(1).getName());
		assertEquals(part3.getName(), l2.get(2).getName());
		assertEquals("article", l2.get(0).getType());
		assertEquals("article", l2.get(1).getType());
		assertEquals("article", l2.get(2).getType());
		assertEquals(APP_NAME_CHILD, l2.get(0).getAppid());
		assertEquals(APP_NAME_CHILD, l2.get(1).getAppid());
		assertEquals(APP_NAME_CHILD, l2.get(2).getAppid());

		// test if objects are validated on updateAll()
		pcc.addValidationConstraint("article", "text", required());
		part1.addProperty("text", "");
		part2.addProperty("text", "");
		List<Sysprop> lu2 = pcc.updateAll(Arrays.asList(part1, part2));
		assertTrue(lu2.isEmpty());
		Para.getDAO().deleteAll(l1);
		pcc.deleteAll(Arrays.asList(part1.getId(), part2.getId(), part3.getId()));
	}

	@Test
	public void testCRUDWithNonStandardIDs() throws InterruptedException {
		String id1 = "test/123/file.txt";
		String id2 = "file.txt?/!./=-+)))(*&^%+$#@><`~±_|'";
		String type1 = "type?/!./=-+)))(*&^%+$#@><`~±_|'";	// # should be removed
		String type2 = "___ type 123  +__";
		Sysprop so1 = new Sysprop(id1);
		Sysprop so2 = new Sysprop(id2);
		so1.setType(type1);
		so2.setType(type2);
		Sysprop obj1 = pc.create(so1);
		Sysprop obj2 = pc.create(so2);
		assertNotNull(obj1);
		assertNotNull(obj2);
		Sysprop sr1 = pc.read(so1.getId());
		Sysprop sr2 = pc.read(so2.getId());
		assertNotNull(sr1);
		assertNotNull(sr2);
		assertNotNull(sr1.getTimestamp());
		assertNotNull(sr2.getTimestamp());
		assertEquals(id1, sr1.getId());
		assertEquals(id2, sr2.getId());
		assertNotNull(pc.read(obj1.getType(), obj1.getId()));
		assertNotNull(pc.read(obj2.getType(), obj2.getId()));
		so1.setName("test name");
		Sysprop su = pc.update(so1);
		assertNotNull(su);
		assertEquals(so1.getName(), su.getName());
		assertNotNull(su.getUpdated());
		pc.delete(so1);
		pc.delete(so2);
		assertNull(pc.read(so1.getId()));
		assertNull(pc.read(so2.getId()));
		assertNull(pc.read(so1.getType(), so1.getId()));
		assertNull(pc.read(so2.getType(), so2.getId()));

		pc.createAll(Arrays.asList(sr1, sr2));
		List<Sysprop> srl = pc.readAll(Arrays.asList(id1, id2));
		assertEquals(2, srl.size());
		assertEquals(id1, srl.get(0).getId());
		assertEquals(id2, srl.get(1).getId());
		pc.deleteAll(Arrays.asList(id1, id2));
		assertTrue(pc.readAll(Arrays.asList(id1, id2)).isEmpty());

		// test unicode ids
		assertNotNull(pc.read(s3.getId()));
		s3.addProperty("text", "текст");
		pc.update(s3);
		assertEquals("текст", ((Sysprop) pc.read(s3.getId())).getProperty("text"));

		pc.link(s3, t.getId());
		assertTrue(pc.isLinked(s3, t.getType(), t.getId()));
		assertEquals(1, pc.countLinks(s3, t.getType()).intValue());
		pc.unlink(s3, t.getType(), t.getId());
		assertTrue(pc.getLinkedObjects(s3, t.getType()).isEmpty());
		assertTrue(pc.voteUp(s3, u1.getId()));

		pc.delete(s3);
		assertNull(pc.read(s3.getId()));
	}

	@Test
	public void testList() throws InterruptedException {
		ArrayList<ParaObject> cats = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			Sysprop s = new Sysprop(catsType + i);
			s.setType(catsType);
			cats.add(s);
		}
		pc.createAll(cats);
//		Thread.sleep(1000);

		assertTrue(pc.list(null).isEmpty());
		assertTrue(pc.list("").isEmpty());

		List<Sysprop> list1 = pc.list(catsType);
		assertFalse(list1.isEmpty());
		assertEquals(3, list1.size());
		assertEquals(Sysprop.class, list1.get(0).getClass());

		List<Sysprop> list2 = pc.list(catsType, new Pager(2));
		assertFalse(list2.isEmpty());
		assertEquals(2, list2.size());

		ArrayList<String> nl = new ArrayList<>(3);
		nl.add(cats.get(0).getId());
		nl.add(cats.get(1).getId());
		nl.add(cats.get(2).getId());
		pc.deleteAll(nl);

		assertTrue(pc.getApp().getDatatypes().containsValue(catsType));
	}


	@Test
	public void testSearch() throws InterruptedException {
		assertNull(pc.findById(null));
		assertNull(pc.findById(""));
		assertNotNull(pc.findById(u.getId()));
		assertNotNull(pc.findById(t.getId()));

		assertTrue(pc.findByIds(null).isEmpty());
		List<?> res1 = pc.findByIds(Arrays.asList(u.getId(), u1.getId(), u2.getId()));
		assertEquals(3, res1.size());

		Sysprop withRouting1 = new Sysprop("routed_object1");
		Sysprop withRouting2 = new Sysprop("routed_object2");
		Para.getDAO().deleteAll(Arrays.asList(withRouting1, withRouting2));
		withRouting1.setAppid(APP_NAME_CHILD);
		withRouting2.setAppid(APP_NAME_CHILD);
		pcc.createAll(Arrays.asList(withRouting1, withRouting2));

//		Thread.sleep(1000);

		assertEquals(2, pcc.findByIds(Arrays.asList(withRouting1.getId(), withRouting2.getId())).size());
		Para.getDAO().deleteAll(APP_NAME_CHILD, Arrays.asList(withRouting1, withRouting2));

		assertTrue(pc.findNearby(null, null, 100, 1, 1).isEmpty());
		assertFalse(pc.findNearby(u.getType(), "*", 10, 40.60, -73.90).isEmpty());
		assertFalse(pc.findNearby(t.getType(), "*", 10, 40.62, -73.91).isEmpty());

		assertTrue(pc.findPrefix(null, null, "").isEmpty());
		assertTrue(pc.findPrefix("", "null", "xx").isEmpty());
		assertFalse(pc.findPrefix(u.getType(), Config._NAME, "Ann").isEmpty());

		assertFalse(pc.findQuery(null, null).isEmpty());
		assertFalse(pc.findQuery("", "*").isEmpty());
		assertEquals(2, pc.findQuery(a1.getType(), "country:US").size());
		//assertFalse(pc.findQuery(u.getType(), "Ann*").isEmpty());
		assertTrue(pc.findQuery(null, "*").size() > 4);

		Pager p = new Pager();
		assertEquals(0, p.getCount());
		List<?> res = pc.findQuery(u.getType(), "*", p);
		assertEquals(res.size(), p.getCount());
		assertTrue(p.getCount() > 0);

		assertTrue(pc.findSimilar(t.getType(), "", null, null).isEmpty());
		assertTrue(pc.findSimilar(t.getType(), "", new String[0], "").isEmpty());
		res = pc.findSimilar(s1.getType(), s1.getId(), new String[]{"properties.text"}, (String) s1.getProperty("text"));
		assertFalse(res.isEmpty());
		assertEquals(s2, res.get(0));

		int i0 = pc.findTagged(u.getType(), null).size();
		int i1 = pc.findTagged(u.getType(), new String[]{"two"}).size();
		int i2 = pc.findTagged(u.getType(), new String[]{"one", "two"}).size();
		int i3 = pc.findTagged(u.getType(), new String[]{"three"}).size();
		int i4 = pc.findTagged(u.getType(), new String[]{"four", "three"}).size();
		int i5 = pc.findTagged(u.getType(), new String[]{"five", "three"}).size();
		int i6 = pc.findTagged(t.getType(), new String[]{"four", "three"}).size();

		assertEquals(0, i0);
		assertEquals(2, i1);
		assertEquals(1, i2);
		assertEquals(3, i3);
		assertEquals(2, i4);
		assertEquals(1, i5);
		assertEquals(0, i6);

		assertFalse(pc.findTags(null).isEmpty());
		assertFalse(pc.findTags("").isEmpty());
		assertTrue(pc.findTags("unknown").isEmpty());
		assertTrue(pc.findTags(t.getTag()).size() >= 1);

		assertEquals(3, pc.findTermInList(u.getType(), Config._ID,
				Arrays.asList(u.getId(), u1.getId(), u2.getId(), "xxx", "yyy")).size());

		// many terms
		Map<String, Object> terms = new HashMap<>();
//		terms.put(Config._TYPE, u.getType());
		terms.put(Config._ID, u.getId());

		Map<String, Object> terms1 = new HashMap<>();
		terms1.put(Config._TYPE, null);
		terms1.put(Config._ID, " ");

		Map<String, Object> terms2 = new HashMap<>();
		terms2.put(" ", "bad");
		terms2.put("", "");

		assertEquals(1, pc.findTerms(u.getType(), terms, true).size());
		assertTrue(pc.findTerms(u.getType(), terms1, true).isEmpty());
		assertTrue(pc.findTerms(u.getType(), terms2, true).isEmpty());

		// single term
		assertTrue(pc.findTerms(null, null, true).isEmpty());
		assertTrue(pc.findTerms(u.getType(), Collections.singletonMap("", null), true).isEmpty());
		assertTrue(pc.findTerms(u.getType(), Collections.singletonMap("", ""), true).isEmpty());
		assertTrue(pc.findTerms(u.getType(), Collections.singletonMap("term", null), true).isEmpty());
		assertTrue(pc.findTerms(u.getType(), Collections.singletonMap(Config._TYPE, u.getType()), true).size() >= 2);
		assertTrue(pc.findTerms(u.getType(), Collections.singletonMap(Config._NAME, "Ann Smith"), true).size() >= 1);
		// "name" field is not analyzed, see https://github.com/Erudika/para/issues/13
		assertTrue(pc.findTerms(u.getType(), Collections.singletonMap(Config._NAME, "ann smith"), true).isEmpty());
//		assertFalse(pc.findQuery(u.getType(), "\"Ann Smith\"").isEmpty());

		assertTrue(pc.findWildcard(u.getType(), null, null).isEmpty());
		assertTrue(pc.findWildcard(u.getType(), "", "").isEmpty());
		assertFalse(pc.findWildcard(u.getType(), Config._NAME, "An*").isEmpty());

		assertTrue(pc.getCount(null).intValue() > 4);
		assertNotEquals(0, pc.getCount("").intValue());
		assertEquals(0, pc.getCount("test").intValue());
		assertTrue(pc.getCount(u.getType()).intValue() >= 3);

		assertEquals(0L, pc.getCount(null, null).intValue());
		assertEquals(0L, pc.getCount(u.getType(), Collections.singletonMap(Config._ID, " ")).intValue());
		assertEquals(1L, pc.getCount(u.getType(), Collections.singletonMap(Config._ID, u.getId())).intValue());
		assertTrue(pc.getCount(null, Collections.singletonMap(Config._TYPE, u.getType())).intValue() > 1);
	}

	@Test
	public void testLinks() throws InterruptedException {
		assertNotNull(pc.link(u, t.getId()));
		assertNotNull(pc.link(u, u2.getId()));

		assertFalse(pc.isLinked(u, null));
		assertTrue(pc.isLinked(u, t));
		assertTrue(pc.isLinked(u, u2));

//		Thread.sleep(1000);

		assertEquals(1, pc.getLinkedObjects(u, Utils.type(Tag.class)).size());
		assertEquals(1, pc.getLinkedObjects(u, Utils.type(Sysprop.class)).size());

		assertEquals(0, pc.countLinks(u, null).intValue());
		assertEquals(1, pc.countLinks(u, Utils.type(Tag.class)).intValue());
		assertEquals(1, pc.countLinks(u, Utils.type(Sysprop.class)).intValue());

		pc.unlinkAll(u);

		assertFalse(pc.isLinked(u, t));
		assertFalse(pc.isLinked(u, u2));

		Sysprop second1 = new Sysprop("secondLink1");
		Sysprop second2 = new Sysprop("secondLink2");
		Sysprop second3 = new Sysprop("secondLink3");
		second1.addProperty("text", "hello from the other side");
		second2.addProperty("text", "hello kitty");
		second3.setName("gordon");

		Sysprop child1 = new Sysprop("child1");
		Sysprop child2 = new Sysprop("child2");
		Sysprop child3 = new Sysprop("child3");
		child1.setParentid(u.getId());
		child2.setParentid(u.getId());
		child3.setParentid(u.getId());
		child1.addProperty("text", "hello from the other side");
		child2.addProperty("text", "hello kitty");
		child3.setName("gordon");

		pc.createAll(Arrays.asList(second1, second2, second3, child1, child2, child3));

//		Thread.sleep(1000);

		assertNotNull(pc.link(u, second1.getId()));
		assertNotNull(pc.link(u, second2.getId()));
		assertNotNull(pc.link(u, second3.getId()));

//		Thread.sleep(1000);

		// test linked objects search
		assertTrue(pc.findLinkedObjects(u, second1.getType(), Config._NAME, null).size() >= 3);

		List<Sysprop> found1 = pc.findLinkedObjects(u, second1.getType(), Config._NAME, "gord*");
		assertFalse(found1.isEmpty());
		assertTrue(found1.get(0).getId().equals(second3.getId()));

		List<Sysprop> found2 = pc.findLinkedObjects(u, second1.getType(), "properties.text", "kitt*");
		assertFalse(found2.isEmpty());
		assertTrue(found2.get(0).getId().equals(second2.getId()));

		List<Sysprop> found3 = pc.findLinkedObjects(u, second1.getType(), "properties.text", "hello");
		assertEquals(2, found3.size());
		assertTrue(found3.get(0).getId().equals(second1.getId()) || found3.get(1).getId().equals(second1.getId()));
		assertTrue(found3.get(0).getId().equals(second2.getId()) || found3.get(1).getId().equals(second2.getId()));

		// test children search
		assertEquals(3, pc.findChildren(u, child1.getType(), null).size());

		List<Sysprop> result1 = pc.findChildren(u, child1.getType(), "gord*");
		assertFalse(result1.isEmpty());
		assertTrue(result1.get(0).getId().equals(child3.getId()));

		List<Sysprop> result2 = pc.findChildren(u, child1.getType(), "kitt*");
		assertFalse(result2.isEmpty());
		assertTrue(result2.get(0).getId().equals(child2.getId()));

		List<Sysprop> result3 = pc.findChildren(u, child1.getType(), "hello");
		assertEquals(2, result3.size());
		assertTrue(result3.get(0).getId().equals(child1.getId()) || result3.get(1).getId().equals(child1.getId()));
		assertTrue(result3.get(0).getId().equals(child2.getId()) || result3.get(1).getId().equals(child2.getId()));

		pc.unlinkAll(u);
		pc.deleteAll(Arrays.asList(second1.getId(), second2.getId(), second3.getId(),
				child1.getId(), child2.getId(), child3.getId()));
	}

	@Test
	public void testUtils() {
		String id1 = pc.newId();
		String id2 = pc.newId();
		assertNotNull(id1);
		assertFalse(id1.isEmpty());
		assertNotEquals(id1, id2);

		final Long ts = pc.getTimestamp();
		assertNotNull(ts);
		assertNotEquals(0, ts.intValue());

		String date1 = pc.formatDate("MM dd yyyy", Locale.US);
		String date2 = Utils.formatDate("MM dd yyyy", Locale.US);
		assertEquals(date1, date2);

		String ns1 = pc.noSpaces(" test  123		test ", "");
		String ns2 = Utils.noSpaces(" test  123		test ", "");
		assertEquals(ns1, ns2);

		String st1 = pc.stripAndTrim(" %^&*( cool )		@!");
		String st2 = Utils.stripAndTrim(" %^&*( cool )		@!");
		assertEquals(st1, st2);

		String md1 = pc.markdownToHtml("**test** #hello");
		String md2 = Utils.markdownToHtml("**test** #hello");
		assertEquals(md1, md2);

		String ht1 = pc.approximately(15000);
		String ht2 = HumanTime.approximately(15000);
		assertEquals(ht1, ht2);
	}

	@Test
	public void testMisc() {
		Map<String, String> types = pc.types();
		assertNotNull(types);
		assertFalse(types.isEmpty());
		assertTrue(types.containsKey(new User().getPlural()));

		assertEquals(App.id(APP_NAME), pc.me().getId());
	}

	@Test
	public void testValidationConstraints() {
		// Validations
		String kittenType = "kitten";
		Map<String, ?> constraints = pc.validationConstraints();
		assertNotNull(constraints);
		assertFalse(constraints.isEmpty());
		assertTrue(constraints.containsKey("app"));
		assertTrue(constraints.containsKey("user"));

		Map<String, Map<String, Map<String, Map<String, ?>>>> constraint = pc.validationConstraints("app");
		assertFalse(constraint.isEmpty());
		assertTrue(constraint.containsKey("app"));
		assertEquals(1, constraint.size());

 		pc.addValidationConstraint(kittenType, "paws", required());
		constraint = pc.validationConstraints(kittenType);
		assertNotNull(constraint);
		assertNotNull(constraint.get(kittenType));
		assertTrue(constraint.get(kittenType).containsKey("paws"));

		Sysprop ct = new Sysprop("felix");
		pc.delete(ct);
		pc.delete(new Vote(u.getId(), ct.getId(), Votable.VoteValue.UP));
		pc.delete(new Vote(u.getId(), ct.getId(), Votable.VoteValue.DOWN));
		ct.setType(kittenType);
		Sysprop ct2 = null;
		try {
			// validation fails
			ct2 = pc.create(ct);
		} catch (Exception e) {}

		assertNull(ct2);
		ct.addProperty("paws", "4");
		assertNotNull(pc.create(ct));

		pc.removeValidationConstraint(kittenType, "paws", "required");
		constraint = pc.validationConstraints(kittenType);
		assertFalse(constraint.containsKey(kittenType));

		Integer votes = ct.getVotes() + 1;
		assertTrue(pc.voteUp(ct, u.getId()));
		assertEquals(votes, pc.read(ct.getId()).getVotes());
		assertFalse(pc.voteUp(ct, u.getId()));
		votes -= 1;
		assertTrue(pc.voteDown(ct, u.getId()));
		assertEquals(votes, pc.read(ct.getId()).getVotes());
		votes -= 1;
		assertTrue(pc.voteDown(ct, u.getId()));
		assertFalse(pc.voteDown(ct, u.getId()));
		assertEquals(votes, pc.read(ct.getId()).getVotes());
		Para.getDAO().delete(ct);
		Para.getDAO().delete(new Vote(u.getId(), ct.getId(), Votable.VoteValue.UP));
	}

	@Test
	public void testResourcePermissions() {
		// Permissions
		Map<String, Map<String, List<String>>> permits = pc.resourcePermissions();
		assertNotNull(permits);

		assertTrue(pc.grantResourcePermission(null, dogsType, EnumSet.noneOf(AllowedMethods.class)).isEmpty());
		assertTrue(pc.grantResourcePermission(" ", "", EnumSet.noneOf(AllowedMethods.class)).isEmpty());

		pc.grantResourcePermission(u1.getId(), dogsType, READ);
		permits = pc.resourcePermissions(u1.getId());
		assertTrue(permits.containsKey(u1.getId()));
		assertTrue(permits.get(u1.getId()).containsKey(dogsType));
		assertTrue(pc.isAllowedTo(u1.getId(), dogsType, GET.toString()));
		assertFalse(pc.isAllowedTo(u1.getId(), dogsType, POST.toString()));

		permits = pc.resourcePermissions();
		assertTrue(permits.containsKey(u1.getId()));
		assertTrue(permits.get(u1.getId()).containsKey(dogsType));

		pc.revokeResourcePermission(u1.getId(), dogsType);
		permits = pc.resourcePermissions(u1.getId());
		assertFalse(permits.get(u1.getId()).containsKey(dogsType));
		assertFalse(pc.isAllowedTo(u1.getId(), dogsType, GET.toString()));
		assertFalse(pc.isAllowedTo(u1.getId(), dogsType, POST.toString()));

		pc.grantResourcePermission(u2.getId(), App.ALLOW_ALL, WRITE);
		assertTrue(pc.isAllowedTo(u2.getId(), dogsType, PUT.toString()));
		assertTrue(pc.isAllowedTo(u2.getId(), dogsType, PATCH.toString()));

		pc.revokeAllResourcePermissions(u2.getId());
		permits = pc.resourcePermissions();
		assertFalse(pc.isAllowedTo(u2.getId(), dogsType, PUT.toString()));
		assertFalse(permits.containsKey(u2.getId()));
//		assertTrue(permits.get(u2.getId()).isEmpty());

		pc.grantResourcePermission(u1.getId(), dogsType, WRITE);
		pc.grantResourcePermission(App.ALLOW_ALL, catsType, WRITE);
		pc.grantResourcePermission(App.ALLOW_ALL, App.ALLOW_ALL, READ);
		// user-specific permissions are in effect
		assertTrue(pc.isAllowedTo(u1.getId(), dogsType, PUT.toString()));
		assertFalse(pc.isAllowedTo(u1.getId(), dogsType, GET.toString()));
		assertTrue(pc.isAllowedTo(u1.getId(), catsType, PUT.toString()));
		assertTrue(pc.isAllowedTo(u1.getId(), catsType, GET.toString()));

		pc.revokeAllResourcePermissions(u1.getId());
		// user-specific permissions not found so check wildcard
		assertFalse(pc.isAllowedTo(u1.getId(), dogsType, PUT.toString()));
		assertTrue(pc.isAllowedTo(u1.getId(), dogsType, GET.toString()));
		assertTrue(pc.isAllowedTo(u1.getId(), catsType, PUT.toString()));
		assertTrue(pc.isAllowedTo(u1.getId(), catsType, GET.toString()));

		pc.revokeResourcePermission(App.ALLOW_ALL, catsType);
		// resource-specific permissions not found so check wildcard
		assertFalse(pc.isAllowedTo(u1.getId(), dogsType, PUT.toString()));
		assertFalse(pc.isAllowedTo(u1.getId(), catsType, PUT.toString()));
		assertTrue(pc.isAllowedTo(u1.getId(), dogsType, GET.toString()));
		assertTrue(pc.isAllowedTo(u1.getId(), catsType, GET.toString()));
		assertTrue(pc.isAllowedTo(u2.getId(), dogsType, GET.toString()));
		assertTrue(pc.isAllowedTo(u2.getId(), catsType, GET.toString()));

		pc.revokeAllResourcePermissions(App.ALLOW_ALL);
		pc.revokeAllResourcePermissions(u1.getId());
	}

	@Test
	public void testAppSettings() {
		Map<String, Object> settings = pc.appSettings();
		assertNotNull(settings);
		assertTrue(settings.isEmpty());

		pc.addAppSetting("", null);
		pc.addAppSetting(" ", " ");
		pc.addAppSetting(null, " ");
		pc.addAppSetting("prop1", 1);
		pc.addAppSetting("prop2", true);
		pc.addAppSetting("prop3", "string");

		settings = pc.appSettings();
		assertEquals(3, settings.size());
		assertEquals(settings, pc.appSettings(null));
		assertEquals(1, settings.get("prop1"));
		assertEquals(true, settings.get("prop2"));
		assertEquals("string", settings.get("prop3"));

		pc.removeAppSetting("prop3");
		pc.removeAppSetting(" ");
		pc.removeAppSetting(null);

		settings = pc.appSettings();
		assertFalse(settings.containsKey("prop3"));
		assertEquals(2, settings.size());
		pc.removeAppSetting("prop2");
		pc.removeAppSetting("prop1");

		pc.addAppSetting("propZ", 1);
		Map<String, Object> newSettings = new HashMap<>();
		newSettings.put("propX", "X");
		newSettings.put("propY", "Y");
		pc.setAppSettings(newSettings);
		settings = pc.appSettings();
		assertEquals(2, settings.size());
		assertFalse(settings.containsKey("propZ"));
		assertTrue(settings.containsKey("propX"));
		assertTrue(settings.containsKey("propY"));
		newSettings.clear();
		pc.setAppSettings(newSettings);
		settings = pc.appSettings();
		assertTrue(settings.isEmpty());
	}

	@Test
	public void testAccessTokens() throws IOException, InterruptedException {
		assertNotNull(fbUser);
		assertNull(pc2.getAccessToken());

		// fails with google+ - service not mocked
		User failsNotMocked = pc2.signIn("google", "test_token");
		assertNull(failsNotMocked);

		// should fail to create user for root app
		System.setProperty("para.clients_can_access_root_app", "false");
		User notSignedIn = pc2.signIn("facebook", "test_token");
//		Thread.sleep(500);
		logger.info(pc2.getAccessToken());
		assertNull(notSignedIn);
		assertNull(pc2.getAccessToken());

		// then allow clients to modify root app
		System.setProperty("para.clients_can_access_root_app", "true");
		User signedIn = pc2.signIn("facebook", "test_token");
		logger.info(pc2.getAccessToken());
		assertNotNull(signedIn);
		assertNotNull(pc2.getAccessToken());
		assertEquals(fbUser.getId(), signedIn.getId());
		assertTrue(signedIn.getActive());

		// test without permissions - signed in but you can't access anything yet
		pc2.revokeAllResourcePermissions(fbUser.getId());
		ParaObject me = pc2.me();
		assertNotNull(me);
		assertEquals("user", me.getType());
		assertTrue(pc2.newId().isEmpty());
		assertTrue(pc2.getTimestamp() == 0L);

		// test with permissions - logout first to use app credentials (full access)
		pc2.signOut();
		pc2.grantResourcePermission(fbUser.getId(), App.ALLOW_ALL, READ_AND_WRITE);
		signedIn = pc2.signIn("facebook", "test_token");
		logger.info(pc2.getAccessToken());
//		Thread.sleep(800);
		assertNotNull(signedIn);
		assertNotNull(pc2.getAccessToken());
		me = pc2.me();
		assertNotNull(me);
		assertFalse(pc2.newId().isEmpty());
		assertEquals(signedIn.getName(), me.getName());
//		Thread.sleep(500);

		// now switch back to App access
		pc2.signOut();
		assertNull(pc2.getAccessToken());
		me = pc2.me(); // app
		assertNotNull(me);
		assertEquals("app", me.getType());
		assertFalse(pc2.newId().isEmpty());
		signedIn = pc2.signIn("facebook", "test_token");
		logger.info(pc2.getAccessToken());
//		Thread.sleep(500);
		me = pc2.me(); // user
		assertNotNull(me);
		assertEquals("user", me.getType());
		assertEquals(signedIn.getId(), me.getId());

		assertNull(pc2.newKeys()); // users can't change API keys!

		// test revoke tokens
		pc2.revokeAllTokens();
		assertTrue(pc2.newId().isEmpty());
		assertTrue(pc2.getTimestamp() == 0L);
		assertNull(pc2.me());

		pc2.signOut();

		// test anonymous permissions
		String utilsPath = "utils/timestamp";
		ParaClient guest = new ParaClient(App.id(APP_NAME), null);
		guest.setEndpoint(pc2.getEndpoint());
		assertFalse(guest.getTimestamp() > 0);
		assertFalse(guest.isAllowedTo(App.ALLOW_ALL, utilsPath, GET.toString()));
		pc2.grantResourcePermission(App.ALLOW_ALL, utilsPath, READ, true);
		assertTrue(guest.getTimestamp() > 0);
	}

	@Test
	public void testOwnersPermissions() throws InterruptedException {
		// test user should be able to login twice - first time the object is created, second time password is checked
		String emailInactive = "test2@user.com";
		String emailPassFail = emailInactive + "::123456";
		String emailPassPass = "test3@user.com::123456";
		String emailPassPass2 = "test4@user.com::123456";
		assertNull(pc2.signIn("password", emailPassFail)); // unverified email - user is created but not active
		List<User> failed = pc2.findTerms(fbUser.getType(), Collections.singletonMap(Config._EMAIL, emailInactive), true);
		assertFalse(failed.isEmpty());
		assertEquals(emailInactive, failed.get(0).getEmail());
		pc2.delete(failed.get(0));

		System.setProperty("para.security.allow_unverified_emails", "true"); // allow it
		User newUser = pc2.signIn("password", emailPassPass);
		User newUser2 = pc2.signIn("password", emailPassPass2);
		assertNotNull(newUser);
		assertNotNull(newUser2);
		pc2.signOut();
		assertNotNull(pc2.signIn("password", emailPassPass));
		pc2.signOut();

		// test permissions with/without signed in user
		assertTrue(pc2.isAllowedTo(newUser.getId(), newUser.getObjectURI(), GET.toString()));
		assertNotNull(pc2.signIn("password", emailPassPass));
		assertTrue(pc2.isAllowedTo(newUser.getId(), newUser.getObjectURI(), GET.toString()));
		assertFalse(pc2.isAllowedTo(newUser.getId(), newUser.getObjectURI() + "x", GET.toString()));
		assertNotNull(pc2.read(newUser.getId())); // can read self
		pc2.signOut();

		// test implicit user permissions - read/update/delete own object (children)
		assertFalse(pc2.isAllowedTo(newUser.getId(), "todo", POST.toString())); // can't create yet
		pc2.grantResourcePermission(newUser.getId(), "todo", EnumSet.of(READ_WRITE, OWN)); // can only manage own TODOs
		pc2.grantResourcePermission(newUser.getId(), "todo/*", EnumSet.of(READ_WRITE, OWN)); // can only manage own TODOs
		pc2.signIn("password", emailPassPass);
		assertTrue(pc2.isAllowedTo(newUser.getId(), "todo", POST.toString()));
		Sysprop todo = new Sysprop("todo_id");
		todo.setType("todo");
		// test if creatorid is set correctly
		todo.setCreatorid("invalid_user_id"); // must be corrected by the server
		todo.setName("[] buy milk");
		todo = pc2.create(todo);
//		Thread.sleep(1000);
		assertNotNull(todo);
		assertFalse(todo.getId().equals("todo_id"));
		assertNotNull(pc2.read(todo.getType(), todo.getId()));
		assertEquals(1, pc2.findQuery("todo", "*").size()); // user only sees own TODO
		assertEquals(newUser.getId(), todo.getCreatorid());
		pc2.signOut();

		// one user must not be able to overwrite another user's TODOs (custom ids)
		pc2.grantResourcePermission(newUser2.getId(), "todo", EnumSet.of(READ_WRITE, OWN)); // can only manage own TODOs
		pc2.grantResourcePermission(newUser2.getId(), "todo/*", EnumSet.of(READ_WRITE, OWN)); // can only manage own TODOs
		pc2.signIn("password", emailPassPass2);
		assertTrue(pc2.list("todo").isEmpty());
		assertNull(pc2.read(todo.getId()));
		Sysprop todo2 = new Sysprop("todo_id2");
		todo2.setType("todo");
		todo2.setName("[] buy eggs");
		todo2 = pc2.create(todo2);
//		Thread.sleep(1000);
		assertNotNull(todo2);
		assertFalse(todo2.getId().equals("todo_id2"));
		assertNotNull(pc2.read(todo2.getType(), todo2.getId()));
		assertEquals(1, pc2.findQuery("todo", "*").size());
		assertEquals(newUser2.getId(), todo2.getCreatorid());
		pc2.signOut();
		// app can see all TODOs
		assertEquals(2, pc2.list("todo").size());

		pc.delete(todo);
		pc.delete(todo2);
		pc.delete(newUser);
		pc.delete(newUser2);

		// an app should be able to update and delete itself
		String appId = "para-child-app-test";
		Map<String, String> creds = Para.newApp(appId, "Child app", false, false);
		ParaClient pclient = new ParaClient(App.id(appId), creds.get("secretKey"));
		pclient.setEndpoint(pc.getEndpoint());

		App app = pclient.me();
		assertNotNull(app);
		assertEquals(appId, app.getAppIdentifier());

		app.setName("Child application");
		pclient.update(app);
		app = pclient.me();
		assertEquals("Child application", app.getName());

		pclient.delete(app);
		assertNull(pclient.read(App.id(appId)));
	}
}
