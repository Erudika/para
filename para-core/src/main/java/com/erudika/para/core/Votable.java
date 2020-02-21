/*
 * Copyright 2013-2020 Erudika. https://erudika.com
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

/**
 * This interface enables voting on an object.
 * All core objects implement this and can be voted for.
 * @author Alex Bogdanovski [alex@erudika.com]
 * @see Vote
 */
public interface Votable {

	/**
	 * The type of vote (negative or positive).
	 */
	enum VoteValue {
		/**
		 * Positive vote +1.
		 */
		UP(1),
		/**
		 * Negative vote -1.
		 */
		DOWN(-1);

		private final int value;

		VoteValue(int v) {
			this.value = v;
		}

		/**
		 * @return -1 if DOWN, 1 if UP
		 */
		public int getValue() {
			return value;
		}

		public String toString() {
			return name().toLowerCase();
		}
	}

	/**
	 * Upvotes the object.
	 * @param userid id of voter
	 * @return true if successful
	 */
	boolean voteUp(String userid);

	/**
	 * Downvotes the object.
	 * @param userid id of voter
	 * @return true if successful
	 */
	boolean voteDown(String userid);

	/**
	 * Returns the total sum of all votes for this object.
	 * For example: (+6) + (-4) = 2
	 * @return the total sum of votes
	 */
	Integer getVotes();

	/**
	 * Sets the total votes for this object.
	 * @param votes the number of votes
	 */
	void setVotes(Integer votes);

}
