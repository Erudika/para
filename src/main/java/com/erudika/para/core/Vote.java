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

import com.erudika.para.annotations.Stored;
import static com.erudika.para.core.Votable.VoteType.*;
import com.erudika.para.utils.Config;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class Vote extends PObject {

	@Stored @NotBlank private String type;
	@Stored @NotNull private Long expiresAfter;	

	public Vote() {
	}

	public Vote(String voterid, String voteeid, String type) {
		setName(getClassname());
		setCreatorid(voterid);
		setParentid(voteeid);
		setTimestamp(System.currentTimeMillis());
		this.type = type;
		this.expiresAfter = Config.VOTE_EXPIRES_AFTER_SEC;
	}

	@Override
	public final String getPlural() {
		return "votes";
	}
	
	@Override
	public final String getId() {
		if(getCreatorid() != null && getParentid() != null && super.getId() == null){
			setId(getClassname().concat(Config.SEPARATOR).concat(getCreatorid()).concat(Config.SEPARATOR).concat(getParentid()));
		}
		return super.getId();
	}
		
	public Vote up(){
		this.type = UP.toString();
		return this;
	}
	
	public Vote down(){
		this.type = DOWN.toString();
		return this;
	}
	
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Long getExpiresAfter() {
		if(expiresAfter == null) 
			expiresAfter = Config.VOTE_EXPIRES_AFTER_SEC;
		return expiresAfter;
	}

	public void setExpiresAfter(Long expiresAfter) {
		this.expiresAfter = expiresAfter;
	}
	
	public boolean isExpired(){
		if(getTimestamp() == null || getExpiresAfter() == 0) return false;
		long timestamp = getTimestamp();
		long expires = (getExpiresAfter() * 1000);
		long now = System.currentTimeMillis();
		return (timestamp + expires) <= now;
	}
	
	public boolean isAmendable(){
		if(getTimestamp() == null) return false;
		long timestamp = getTimestamp();
		long now = System.currentTimeMillis();
		// check timestamp for recent correction,
		return (timestamp + (Config.VOTE_LOCKED_AFTER_SEC * 1000)) > now;
	}
}
