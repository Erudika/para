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
package com.erudika.para.client;

import com.erudika.para.Para;
import com.erudika.para.ParaServer;
import com.erudika.para.core.Address;
import com.erudika.para.core.App;
import com.erudika.para.core.App.AllowedMethods;
import com.erudika.para.core.CoreUtils;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.Tag;
import com.erudika.para.core.User;
import com.erudika.para.search.ElasticSearchUtils;
import com.erudika.para.utils.Config;
import static com.erudika.para.validation.Constraint.*;
import com.erudika.para.utils.HumanTime;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ParaClientIT {

	private static final Logger logger = LoggerFactory.getLogger(ParaClientIT.class);
	private static ParaClient pc;
	private static final String catsType = "cat";
	private static final String dogsType = "dog";
	private static final String batsType = "bat";

	protected static Sysprop u;
	protected static Sysprop u1;
	protected static Sysprop u2;
	protected static Tag t;
	protected static Sysprop s1;
	protected static Sysprop s2;
	protected static Address a1;
	protected static Address a2;

	@BeforeClass
	public static void setUpClass() throws InterruptedException {
		System.setProperty("para.env", "embedded");
		System.setProperty("para.app_name", "para-client-test");
		System.setProperty("para.cluster_name", "para-test");
		String endpoint = "http://localhost:8080";
		ParaServer.main(new String[0]);
		ParaClient temp = new ParaClient("x", "x");
		temp.setEndpoint(endpoint);
		Map<String, String> credentials = temp.setup();
		if (credentials != null && credentials.containsKey("accessKey")) {
			String accessKey = credentials.get("accessKey");
			String secretKey = credentials.get("secretKey");
			pc = new ParaClient(accessKey, secretKey);
			pc.setEndpoint(endpoint);
			logger.info("accessKey: {}, secretKey: {}", accessKey, secretKey);
		}

		u = new Sysprop("111");
		u.setName("John Doe");
		u.setTimestamp(Utils.timestamp());
		u.setTags(CoreUtils.addTags(u.getTags(), "one", "two", "three"));

		u1 = new Sysprop("222");
		u1.setName("Joe Black");
		u1.setTimestamp(Utils.timestamp());
		u1.setTags(CoreUtils.addTags(u1.getTags(), "two", "four", "three"));

		u2 = new Sysprop("333");
		u2.setName("Ann Smith");
		u2.setTimestamp(Utils.timestamp());
		u2.setTags(CoreUtils.addTags(u2.getTags(), "four", "five", "three"));

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
		s1.setName("This is a little test sentence. Testing, one, two, three.");
		s1.setTimestamp(Utils.timestamp());

		s2 = new Sysprop("s2");
		s2.setName("We are testing this thing. This sentence is a test. One, two.");
		s2.setTimestamp(Utils.timestamp());

		pc.createAll(Arrays.asList(u, u1, u2, t, s1, s2, a1, a2));
		Thread.sleep(1000);
	}

	@AfterClass
	public static void tearDownClass() {
		Para.getDAO().delete(new App(Config.APP_NAME_NS));
		ElasticSearchUtils.deleteIndex(Config.APP_NAME_NS);
		Para.destroy();
	}

	@Test
	public void testCRUD() {
		assertNull(pc.create(null));

		Tag t1 = pc.create(new Tag("test1"));
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

		tr.setCount(15);
		Tag tu = pc.update(tr);
		assertNull(pc.update(new Tag("null")));
		assertNotNull(tu);
		assertEquals(tu.getCount(), tr.getCount());
		assertNotNull(tu.getUpdated());

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
	}

	@Test
	public void testBatchCRUD() throws InterruptedException {
		ArrayList<Sysprop> dogs = new ArrayList<Sysprop>();
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
		ArrayList<String> nl = new ArrayList<String>(3);
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
		Thread.sleep(1000);

		List<Sysprop> l4 = pc.list(dogsType);
		assertTrue(l4.isEmpty());

		assertTrue(pc.getApp().getDatatypes().containsValue(dogsType));
	}

	@Test
	public void testList() throws InterruptedException {
		ArrayList<ParaObject> cats = new ArrayList<ParaObject>();
		for (int i = 0; i < 3; i++) {
			Sysprop s = new Sysprop(catsType + i);
			s.setType(catsType);
			cats.add(s);
		}
		pc.createAll(cats);
		Thread.sleep(1000);

		assertTrue(pc.list(null).isEmpty());
		assertTrue(pc.list("").isEmpty());

		List<Sysprop> list1 = pc.list(catsType);
		assertFalse(list1.isEmpty());
		assertEquals(3, list1.size());
		assertEquals(Sysprop.class, list1.get(0).getClass());

		List<Sysprop> list2 = pc.list(catsType, new Pager(2));
		assertFalse(list2.isEmpty());
		assertEquals(2, list2.size());

		ArrayList<String> nl = new ArrayList<String>(3);
		nl.add(cats.get(0).getId());
		nl.add(cats.get(1).getId());
		nl.add(cats.get(2).getId());
		pc.deleteAll(nl);

		assertTrue(pc.getApp().getDatatypes().containsValue(catsType));
	}


	@Test
	public void testSearch() throws InterruptedException {
//		ArrayList<Sysprop> bats = new ArrayList<Sysprop>();
//		for (int i = 0; i < 5; i++) {
//			Sysprop s = new Sysprop(batsType + i);
//			s.setType(batsType);
//			s.addProperty("foo", "bat");
//			bats.add(s);
//		}
//		pc.createAll(bats);
//		Thread.sleep(1000);

		assertNull(pc.findById(null));
		assertNull(pc.findById(""));
		assertNotNull(pc.findById(u.getId()));
		assertNotNull(pc.findById(t.getId()));

		assertTrue(pc.findByIds(null).isEmpty());
		assertEquals(3, pc.findByIds(Arrays.asList(u.getId(), u1.getId(), u2.getId())).size());

		assertTrue(pc.findNearby(null, null, 100, 1, 1).isEmpty());
		List<User> l1 = pc.findNearby(u.getType(), "*", 10, 40.60, -73.90);
		assertFalse(l1.isEmpty());

		assertTrue(pc.findNearby(null, null, 100, 1, 1).isEmpty());
		l1 = pc.findNearby(u.getType(), "*", 10, 40.60, -73.90);
		assertFalse(l1.isEmpty());

		assertTrue(pc.findPrefix(null, null, "").isEmpty());
		assertTrue(pc.findPrefix("", "null", "xx").isEmpty());
		assertFalse(pc.findPrefix(u.getType(), Config._NAME, "ann").isEmpty());

		assertFalse(pc.findQuery(null, null).isEmpty());
		assertFalse(pc.findQuery("", "*").isEmpty());
		assertEquals(2, pc.findQuery(a1.getType(), "country:US").size());
		assertFalse(pc.findQuery(u.getType(), "ann").isEmpty());
		assertFalse(pc.findQuery(u.getType(), "Ann").isEmpty());
		assertTrue(pc.findQuery(null, "*").size() > 4);

		Pager p = new Pager();
		assertEquals(0, p.getCount());
		List<?> res = pc.findQuery(u.getType(), "*", p);
		assertEquals(res.size(), p.getCount());
		assertTrue(p.getCount() > 0);

		assertTrue(pc.findSimilar(t.getType(), "", null, null).isEmpty());
		assertTrue(pc.findSimilar(t.getType(), "", new String[0], "").isEmpty());
		res = pc.findSimilar(s1.getType(), s1.getId(), new String[]{Config._NAME}, s1.getName());
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
		Map<String, Object> terms = new HashMap<String, Object>();
//		terms.put(Config._TYPE, u.getType());
		terms.put(Config._ID, u.getId());

		Map<String, Object> terms1 = new HashMap<String, Object>();
		terms1.put(Config._TYPE, null);
		terms1.put(Config._ID, " ");

		Map<String, Object> terms2 = new HashMap<String, Object>();
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

		assertTrue(pc.findWildcard(u.getType(), null, null).isEmpty());
		assertTrue(pc.findWildcard(u.getType(), "", "").isEmpty());
		assertFalse(pc.findWildcard(u.getType(), Config._NAME, "an*").isEmpty());

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

		Thread.sleep(1000);

		assertEquals(1, pc.getLinkedObjects(u, Utils.type(Tag.class)).size());
		assertEquals(1, pc.getLinkedObjects(u, Utils.type(Sysprop.class)).size());

		assertEquals(0, pc.countLinks(u, null).intValue());
		assertEquals(1, pc.countLinks(u, Utils.type(Tag.class)).intValue());
		assertEquals(1, pc.countLinks(u, Utils.type(Sysprop.class)).intValue());

		pc.unlinkAll(u);

		assertFalse(pc.isLinked(u, t));
		assertFalse(pc.isLinked(u, u2));
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
		String kittenType = "kitten";
		Map<String, String> cred = pc.setup();
		assertFalse(cred.containsKey("accessKey"));

		Map<String, String> types = pc.types();
		assertFalse(types.isEmpty());
		assertTrue(types.containsKey(new User().getPlural()));

		assertTrue(pc.me().getId().equals(App.id(System.getProperty("para.app_name"))));
	}

	@Test
	public void testValidationConstraints() {
		// Validations
		String kittenType = "kitten";
		Map<String, ?> constraints = pc.validationConstraints();
		assertFalse(constraints.isEmpty());
		assertTrue(constraints.containsKey("app"));
		assertTrue(constraints.containsKey("user"));

		Map<String, Map<String, Map<String, Map<String, ?>>>> constraint = pc.validationConstraints("app");
		assertFalse(constraint.isEmpty());
		assertTrue(constraint.containsKey("app"));
		assertEquals(1, constraint.size());

 		pc.addValidationConstraint(kittenType, "paws", required());
		constraint = pc.validationConstraints(kittenType);
		assertTrue(constraint.get(kittenType).containsKey("paws"));

		Sysprop ct = new Sysprop("felix");
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
		assertFalse(constraint.get(kittenType).isEmpty());
	}

	@Test
	public void testResourcePermissions() {
		// Permissions
		Map<String, Map<String, List<String>>> permits = pc.resourcePermissions();
		assertTrue(permits.isEmpty());

		assertNull(pc.grantResourcePermission(null, dogsType, EnumSet.noneOf(AllowedMethods.class)));
		assertNull(pc.grantResourcePermission(" ", "", EnumSet.noneOf(AllowedMethods.class)));

		pc.grantResourcePermission(u1.getId(), dogsType, AllowedMethods.READ);
		permits = pc.resourcePermissions(u1.getId());
		assertTrue(permits.containsKey(u1.getId()));
		assertTrue(permits.get(u1.getId()).containsKey(dogsType));
		assertTrue(pc.isAllowedTo(u1.getId(), dogsType, AllowedMethods.GET.toString()));
		assertFalse(pc.isAllowedTo(u1.getId(), dogsType, AllowedMethods.POST.toString()));

		permits = pc.resourcePermissions();
		assertTrue(permits.containsKey(u1.getId()));
		assertTrue(permits.get(u1.getId()).containsKey(dogsType));

		pc.revokeResourcePermission(u1.getId(), dogsType);
		permits = pc.resourcePermissions(u1.getId());
		assertFalse(permits.get(u1.getId()).containsKey(dogsType));
		assertFalse(pc.isAllowedTo(u1.getId(), dogsType, AllowedMethods.GET.toString()));
		assertFalse(pc.isAllowedTo(u1.getId(), dogsType, AllowedMethods.POST.toString()));

		pc.grantResourcePermission(u2.getId(), App.ALLOW_ALL, AllowedMethods.WRITE);
		assertTrue(pc.isAllowedTo(u2.getId(), dogsType, AllowedMethods.PUT.toString()));
		assertTrue(pc.isAllowedTo(u2.getId(), dogsType, AllowedMethods.PATCH.toString()));

		pc.revokeAllResourcePermissions(u2.getId());
		permits = pc.resourcePermissions();
		assertFalse(pc.isAllowedTo(u2.getId(), dogsType, AllowedMethods.PUT.toString()));
		assertTrue(permits.containsKey(u2.getId()));
		assertTrue(permits.get(u2.getId()).isEmpty());

		pc.grantResourcePermission(u1.getId(), dogsType, AllowedMethods.WRITE);
		pc.grantResourcePermission(App.ALLOW_ALL, catsType, AllowedMethods.WRITE);
		pc.grantResourcePermission(App.ALLOW_ALL, App.ALLOW_ALL, AllowedMethods.READ);
		// user-specific permissions are in effect
		assertTrue(pc.isAllowedTo(u1.getId(), dogsType, AllowedMethods.PUT.toString()));
		assertFalse(pc.isAllowedTo(u1.getId(), dogsType, AllowedMethods.GET.toString()));
		assertTrue(pc.isAllowedTo(u1.getId(), catsType, AllowedMethods.PUT.toString()));
		assertTrue(pc.isAllowedTo(u1.getId(), catsType, AllowedMethods.GET.toString()));

		pc.revokeAllResourcePermissions(u1.getId());
		// user-specific permissions not found so check wildcard
		assertFalse(pc.isAllowedTo(u1.getId(), dogsType, AllowedMethods.PUT.toString()));
		assertTrue(pc.isAllowedTo(u1.getId(), dogsType, AllowedMethods.GET.toString()));
		assertTrue(pc.isAllowedTo(u1.getId(), catsType, AllowedMethods.PUT.toString()));
		assertTrue(pc.isAllowedTo(u1.getId(), catsType, AllowedMethods.GET.toString()));

		pc.revokeResourcePermission(App.ALLOW_ALL, catsType);
		// resource-specific permissions not found so check wildcard
		assertFalse(pc.isAllowedTo(u1.getId(), dogsType, AllowedMethods.PUT.toString()));
		assertFalse(pc.isAllowedTo(u1.getId(), catsType, AllowedMethods.PUT.toString()));
		assertTrue(pc.isAllowedTo(u1.getId(), dogsType, AllowedMethods.GET.toString()));
		assertTrue(pc.isAllowedTo(u1.getId(), catsType, AllowedMethods.GET.toString()));
		assertTrue(pc.isAllowedTo(u2.getId(), dogsType, AllowedMethods.GET.toString()));
		assertTrue(pc.isAllowedTo(u2.getId(), catsType, AllowedMethods.GET.toString()));
	}

	@Test
	public void testAccessTokens() {

	}
}
