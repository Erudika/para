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

import java.io.Serializable;
import org.codehaus.jackson.annotate.JsonIgnore;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public interface ParaObject extends Serializable {

	public String create();

	public void delete();

	public String getClassname();

	@JsonIgnore
	public ParaObject getCreator();

	public String getCreatorid();

	public String getId();

	public String getName();

	// one-to-many relationships (may return self)
	@JsonIgnore
	public ParaObject getParent();

	public String getParentid();

	public Long getTimestamp();

	public Long getUpdated();

	public void setClassname(String classname);

	public void setCreatorid(String creatorid);

	public void setId(String id);

	public void setName(String name);

	public void setParentid(String parentid);

	public void setTimestamp(Long timestamp);

	public void setUpdated(Long updated);

	public void update();
	
}
