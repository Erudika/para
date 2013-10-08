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
import com.erudika.para.utils.Utils;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class Tag extends PObject{
	private static final long serialVersionUID = 1L;

	@Stored private String tag;
	@Stored private Integer count;

	public Tag(){
	}
	
	public Tag(String tag) {
		setName(tag);
		setId(id(tag));
		this.count = 1;
		this.tag = tag;
	}
	
	public static String id(String tag){
		if(StringUtils.isBlank(tag)) return null;
		return PObject.classname(Tag.class).concat(Utils.SEPARATOR).concat(StringUtils.trimToEmpty(tag));
	}
	
	@Override
	public final String getClassname() {
		return super.getClassname();
	}

	@Override
	public final void setClassname(String classname) {
		super.setClassname(classname);
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
		this.tag = tag;
	}

	public void incrementCount(){
		this.count++;
	}

	public void decrementCount(){
		if(this.count <= 1)
			delete();
		else	
			this.count--;
	}
	
	public static boolean isValidTagString(String tags){
		return !StringUtils.isBlank(tags) && tags.startsWith(",") && tags.endsWith(",");
	}

	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Tag other = (Tag) obj;
		if ((this.tag == null) ? (other.tag != null) : !this.tag.equalsIgnoreCase(other.tag)) {
			return false;
		}
		return true;
	}

	public int hashCode() {
		int hash = 3;
		hash = 97 * hash + (this.tag != null ? this.tag.hashCode() : 0);
		return hash;
	}
}
