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
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class Sysprop extends PObject{
	private static final long serialVersionUID = 1L;

	@Stored private Map<String, Object> properties;
	
	public Sysprop(String id) {
		setId(id);
		getName();
	}
	
	public Sysprop() {
		this(null);
	}

	public Sysprop addProperty(String name, Object value){
		if(!StringUtils.isBlank(name) && value != null){
			getProperties().put(name, value);
		} 
		return this;
	}
	
	public Object getProperty(String name){
		if(!StringUtils.isBlank(name)){
			return getProperties().get(name);
		}
		return null;
	}
	
	public Sysprop removeProperty(String name){
		if(!StringUtils.isBlank(name)){
			getProperties().remove(name);
		}
		return this;
	}
	
	public boolean hasProperty(String name){
		if(StringUtils.isBlank(name)) return false;
		return getProperties().containsKey(name);
	}
	
	public Map<String, Object> getProperties() {
		if(properties == null) 
			properties = new HashMap<String, Object>();
		return properties;
	}

	public void setProperties(Map<String, Object> properties) {
		this.properties = properties;
	}
}
