/*
 * Copyright 2013 Alex Bogdanovski <alex@erudika.com>.
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

import com.erudika.para.annotations.Stored;
import static com.erudika.para.core.Votable.VoteValue.*;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;

/**
 * When a user votes on an object the vote is saved as positive or negative. 
 * The user has a short amount of time to amend that vote and then it's locked.
 * Votes can expire after X seconds and they get deleted. 
 * This allows the voter to vote again on the same object.
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class Vote extends PObject {
	private static final long serialVersionUID = 1L;

	@Stored @NotBlank private String value;
	@Stored @NotNull private Long expiresAfter;

	/**
	 * No-args constructor
	 */
	public Vote() {
		this(null, null, null);
	}

	/**
	 * Default constructor
	 * @param voterid the user id of the voter
	 * @param voteeid the id of the object that will receive the vote
	 * @param value up + or down -
	 */
	public Vote(String voterid, String voteeid, String value) {
		setCreatorid(voterid);
		setParentid(voteeid);
		setTimestamp(Utils.timestamp());
		setName(getType());
		this.value = value;
		this.expiresAfter = Config.VOTE_EXPIRES_AFTER_SEC;
	}

	@Override
	public final String getId() {
		if (getCreatorid() != null && getParentid() != null && super.getId() == null) {
			setId(getType().concat(Config.SEPARATOR).concat(getCreatorid()).concat(Config.SEPARATOR).concat(getParentid()));
		}
		return super.getId();
	}

	/**
	 * Set the vote positive.
	 * @return this
	 */
	public Vote up() {
		this.value = UP.toString();
		return this;
	}

	/**
	 * Set the vote negative.
	 * @return this
	 */
	public Vote down() {
		this.value = DOWN.toString();
		return this;
	}

	/**
	 * Returns the value of the vote.
	 * @return UP or DOWN
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Sets the value of the vote
	 * @param value UP or DOWN
	 */
	public void setValue(String value) {
		this.value = value;
	}

	/**
	 * Returns the expiration period
	 * @return time in seconds
	 */
	public Long getExpiresAfter() {
		if (expiresAfter == null) {
			expiresAfter = Config.VOTE_EXPIRES_AFTER_SEC;
		}
		return expiresAfter;
	}

	/**
	 * Sets the expiration period
	 * @param expiresAfter time in seconds
	 */
	public void setExpiresAfter(Long expiresAfter) {
		this.expiresAfter = expiresAfter;
	}

	/**
	 * Checks if expired
	 * @return true if expired
	 */
	public boolean isExpired() {
		if (getTimestamp() == null || getExpiresAfter() == 0) {
			return false;
		}
		long timestamp = getTimestamp();
		long expires = (getExpiresAfter() * 1000);
		long now = Utils.timestamp();
		return (timestamp + expires) <= now;
	}

	/**
	 * Checks if vote can still be amended.
	 * @return true if vote can still be changed
	 */
	public boolean isAmendable() {
		if (getTimestamp() == null) {
			return false;
		}
		long timestamp = getTimestamp();
		long now = Utils.timestamp();
		// check timestamp for recent correction,
		return (timestamp + (Config.VOTE_LOCKED_AFTER_SEC * 1000)) > now;
	}
}
