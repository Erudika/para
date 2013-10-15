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
package com.erudika.para.core;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class PObjectTest {
	
	@BeforeClass
	public static void setUpClass(){
	
		// TODO CREATE MOCK OF DAO AND SET INSIDE POBJECT!!!!!
		// TODO CREATE MOCK OF DAO AND SET INSIDE POBJECT!!!!!
		// TODO CREATE MOCK OF DAO AND SET INSIDE POBJECT!!!!!
		
	}
	
	@AfterClass
	public static void tearDownClass() {
	}


	@Test
	public void testGetClassname() {
		Tag tag = new Tag();
		PObject p = new PObject() {};
		assertNotNull(tag.getClassname());
		assertNotNull(p.getClassname());
		assertEquals("tag", tag.getClassname());
		assertEquals("", p.getClassname());
	}

	@Test
	public void testSetClassname() {
		Tag tag = new Tag();
		tag.setClassname("bag");
		assertEquals("bag", tag.getClassname());		
	}

	@Test
	public void testLink() {
		User u = new User("1");
		Tag t = new Tag("tag");
		u.link(Tag.class, t.getId());
		assertTrue(t.isLinked(User.class, u.getId()));
		assertTrue(u.isLinked(Tag.class, t.getId()));
	}

	@Test
	public void testUnlink() {
	}

	@Test
	public void testUnlinkAll() {
	}

	@Test
	public void testGetAllLinks_Class() {
	}

	@Test
	public void testGetAllLinks_5args() {
	}

	@Test
	public void testIsLinked() {
	}

	@Test
	public void testCountLinks() {
	}

	@Test
	public void testClassname() {
	}

	@Test
	public void testGetChildren_Class() {
	}

	@Test
	public void testGetChildren_5args() {
	}

	@Test
	public void testDeleteChildren() {
	}

	@Test
	public void testGetLinkedObjects() {
	}

	@Test
	public void testVoteUp() {
	}

	@Test
	public void testVoteDown() {
	}

	@Test
	public void testGetVotes() {
	}

	@Test
	public void testSetVotes() {
	}

	@Test
	public void testHashCode() {
	}

	@Test
	public void testEquals() {
	}

	public class PObjectImpl extends PObject {
	}
}