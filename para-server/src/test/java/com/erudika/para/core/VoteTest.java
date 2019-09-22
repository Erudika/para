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
package com.erudika.para.core;

import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.persistence.DAO;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class VoteTest {

	@Test
	public void testVotes() {
		DAO dao = CoreUtils.getInstance().getDao(); //new MockDAO();
		User u = new User("111");
		User u2 = new User("222");

		assertEquals(0, u.getVotes().intValue());
		u.voteUp(u2.getId());
		assertEquals(1, u.getVotes().intValue());
		u.voteUp(u2.getId());
		assertEquals(1, u.getVotes().intValue());
		u.voteUp(u2.getId());
		assertEquals(1, u.getVotes().intValue());

		u.voteDown(u2.getId());
		assertEquals(0, u.getVotes().intValue());

		u.voteDown(u2.getId());
		assertEquals(-1, u.getVotes().intValue());
		u.voteDown(u2.getId());
		assertEquals(-1, u.getVotes().intValue());

		u.voteUp(u2.getId());
		assertEquals(0, u.getVotes().intValue());

		// test expirations and locks
		u2.voteUp(u.getId());
		assertEquals(1, u2.getVotes().intValue());

		// isExpired() = true
		Vote v = dao.read("vote:111:222");
		v.setTimestamp(-1234L);
		dao.create(v);

		u2.voteUp(u.getId());
		assertEquals(2, u2.getVotes().intValue());

		// isExpired() = true
		v = dao.read("vote:111:222");
		v.setTimestamp(-1234L);
		dao.create(v);

		u2.voteUp(u.getId());
		assertEquals(3, u2.getVotes().intValue());

		// clear
		dao.delete(v);
		u2.setVotes(0);

		u2.voteUp(u.getId());
		assertEquals(1, u2.getVotes().intValue());

		// isAmendable() = false
		v = dao.read("vote:111:222");
		v.setExpiresAfter(0);
		v.setTimestamp(-1234L);
		dao.create(v);

		u2.voteDown(u.getId());
		assertEquals(1, u2.getVotes().intValue());

		// voting on self
		u.setVotes(0);
		u.voteDown(u.getId());
		assertEquals(0, u.getVotes().intValue());
		u.voteDown(u.getId());
		assertEquals(0, u.getVotes().intValue());
		u.voteUp(u.getId());
		assertEquals(0, u.getVotes().intValue());
		u.voteUp(u.getId());
		assertEquals(0, u.getVotes().intValue());

		Tag t = new Tag("test");
		t.voteUp(t.getId());
		assertEquals(0, t.getVotes().intValue());
	}
}