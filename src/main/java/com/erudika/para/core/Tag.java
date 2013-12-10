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

import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.utils.Config;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class Tag extends PObject{
	private static final long serialVersionUID = 1L;

	@Stored @NotBlank @Locked private String tag;
	@Stored private Integer count;

	public Tag(){
		this(null);
	}
	
	public Tag(String tag) {
		setName(tag);
		setId(id(tag));
		this.count = 0;
		this.tag = tag;
	}
	
	public static String id(String tag){
		if(StringUtils.isBlank(tag)) return null;
		return PObject.classname(Tag.class).concat(Config.SEPARATOR).concat(StringUtils.trimToEmpty(tag));
	}

	@Override
	public String getObjectURL() {
		String realid = getId();
		setId(tag);
		String url = super.getObjectURL();
		setId(realid);
		return url;
	}
			
	public Integer getCount() {
		return count;
	}

	public void setCount(Integer count) {
		this.count = count;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		if(tag == null) return;
		setId(id(tag));
		this.tag = tag;
	}

	public void incrementCount(){
		this.count++;
	}

	public void decrementCount(){
		this.count--;
		if(this.count < 1 && exists()){
			delete();
		}
	}

	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Tag other = (Tag) obj;
		if (!StringUtils.equalsIgnoreCase(tag, other.tag)) {
			return true;
		}
		return false;
	}

	public int hashCode() {
		int hash = 3;
		hash = 97 * hash + (this.tag != null ? this.tag.hashCode() : 0);
		return hash;
	}
}
