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
import com.erudika.para.utils.Utils;
import org.hibernate.validator.constraints.NotBlank;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public abstract class Vote extends PObject{

	@Stored @NotBlank private String type;
	
	@Override
	public final String getId() {
		if(getCreatorid() != null && getParentid() != null && super.getId() == null){
			setId(getCreatorid().concat(Utils.SEPARATOR).concat(getClassname()).concat(getParentid()));
		}
		return super.getId();
	}

	@Override
	public final void setId(String id) {
		if(super.getId() == null) super.setId(id);
	}
	
	@Override
	public final String getClassname() {
		return super.getClassname();
	}

	@Override
	public final void setClassname(String classname) {
		super.setClassname(classname);
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
		
	@Override
	public final void update() {
		// NOOP
	}
	
	@Override
	public abstract String create();

	@Override
	public abstract void delete();

	/**
	 * Amends the vote after it has been cast.
	 * @return true if the vote was amended
	 */
	public abstract boolean amendVote();
	
}
