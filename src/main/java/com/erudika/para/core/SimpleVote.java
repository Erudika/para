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

import com.erudika.para.utils.Utils;
import javax.enterprise.inject.Default;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@Default
public class SimpleVote extends Vote{
	private static final long serialVersionUID = 1L;
	
	public SimpleVote() {
	}
	
	@Override
	public String create() {
		if(StringUtils.isBlank(getId()) || !Utils.isValidObject(this)) return null;
		// save new vote & set expiration date 
		// users can vote again after vote lock period is over
		getSearch().index(this, super.getClassname(), Utils.VOTE_LOCKED_FOR_SEC);
		return getId();
	}

	@Override
	public void delete() {
		getSearch().unindex(this, super.getClassname());
	}

	@Override
	public boolean amendVote() {
		SimpleVote vote = getSearch().findById(getId(), super.getClassname());
		if(vote == null || vote.getType() == null || getType() == null) return false;
		
		boolean success = false;
		boolean isUpvote = getType().equals(VoteType.UP.toString());
		boolean wasUpvote = vote.getType().equals(VoteType.UP.toString());
		boolean voteHasChanged = BooleanUtils.xor(new boolean[]{isUpvote, wasUpvote});
		long timestamp = vote.getTimestamp();
		long now = System.currentTimeMillis();
		
		// check timestamp for recent correction,
		if((timestamp + (Utils.VOTE_LOCK_AFTER_SEC * 1000)) > now && voteHasChanged) {
			// clear vote and restore votes to original count
			delete();
			success = true;
		}
		
		return success;
	}
}
