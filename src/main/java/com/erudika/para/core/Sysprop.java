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

import com.erudika.para.utils.Config;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class Sysprop extends PObject{
	private static final long serialVersionUID = 1L;

	private Map<String, Object> properties; 
	
	public Sysprop(String id) {
		this();
		setId(id);
	}
	
	public Sysprop() {
		super.setName("System property");
	}

	public Sysprop addProperty(String name, Object value){
		if(!StringUtils.isBlank(name) && value != null){
			getProperties().put(getPropName(name), value);
		} 
		return this;
	}
	
	public Object getProperty(String name){
		return getProperties().get(getPropName(name));
	}
	
	public Sysprop removeProperty(String name){
		if(!StringUtils.isBlank(name)){
			getProperties().remove(getPropName(name));
		}
		return this;
	}
	
	public boolean hasProperty(String name){
		if(StringUtils.isBlank(name)) return false;
		return getProperties().containsKey(getPropName(name));
	}
	
	private String getPropName(String name){
		return getClassname().concat(Config.SEPARATOR).concat(name);
	}
	
	public static boolean isSysprop(String name){
		return StringUtils.startsWith(name, PObject.classname(Sysprop.class));
	}
	
	public Map<String, Object> getProperties() {
		if(properties == null) properties = new HashMap<String, Object>();
		return properties;
	}

	public void setProperties(Map<String, Object> properties) {
		this.properties = properties;
	}
	
	///////////////////////////////////////////////////////////////
	
	public String link(Class<? extends ParaObject> c2, String id2) {
		throw new UnsupportedOperationException();
	}

	public void unlink(Class<? extends ParaObject> c2, String id2) {
		throw new UnsupportedOperationException();
	}

	public void unlinkAll() {
		throw new UnsupportedOperationException();
	}

	public ArrayList<Linker> getAllLinks(Class<? extends ParaObject> c2) {
		throw new UnsupportedOperationException();
	}

	public ArrayList<Linker> getAllLinks(Class<? extends ParaObject> c2, MutableLong pagenum, MutableLong itemcount, boolean reverse, int maxItems) {
		throw new UnsupportedOperationException();
	}

	public boolean isLinked(Class<? extends ParaObject> c2, String toId) {
		throw new UnsupportedOperationException();
	}

	public Long countLinks(Class<? extends ParaObject> c2) {
		throw new UnsupportedOperationException();
	}

	public <P extends ParaObject> ArrayList<P> getChildren(Class<? extends ParaObject> clazz) {
		throw new UnsupportedOperationException();
	}

	public <P extends ParaObject> ArrayList<P> getChildren(Class<? extends ParaObject> clazz, MutableLong page, MutableLong itemcount, String sortfield, int max) {
		throw new UnsupportedOperationException();
	}

	public void deleteChildren(Class<? extends ParaObject> clazz) {
		throw new UnsupportedOperationException();
	}

	public <P extends ParaObject> ArrayList<P> getLinkedObjects(Class<? extends ParaObject> clazz, MutableLong page, MutableLong itemcount) {
		throw new UnsupportedOperationException();
	}

}
