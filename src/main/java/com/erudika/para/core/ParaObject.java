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

import java.io.Serializable;
import org.codehaus.jackson.annotate.JsonIgnore;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public interface ParaObject extends Serializable {

	public String getId();

	public void setId(String id);
	
	public String getName();

	public void setName(String name);
	
	public String getAppname();
	
	public void setAppname(String appname);
	
	public String getParentid();

	public void setParentid(String parentid);
	
	public String getClassname();
	
	public void setClassname(String classname);
	
	public String getCreatorid();

	public void setCreatorid(String creatorid);
	
	public String getPlural();

	public String getObjectURL();

	@JsonIgnore
	public ParaObject getCreator();
	
	// one-to-many relationships (may return self)
	@JsonIgnore
	public ParaObject getParent();
	
	public Long getTimestamp();

	public void setTimestamp(Long timestamp);
	
	public Long getUpdated();

	public void setUpdated(Long updated);

	public String create();

	public void update();
	
	public void delete();
	
	public boolean exists();
	
}
