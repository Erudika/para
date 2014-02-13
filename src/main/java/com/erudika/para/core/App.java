/*
 * Copyright 2014 Alex Bogdanovski <alex@erudika.com>.
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
import com.erudika.para.utils.Utils;
import java.util.Set;
import javax.validation.constraints.Size;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;

/**
 * This is a representation of an application within Para. <b>This functionality is WORK IN PROGRESS!</b>
 * <br>
 * It allows the user to create separate apps running on the same infrastructure.
 * The apps are separated by name and each {@link ParaObject} belongs to an app.
 * </br>
 * There can be two ways to separate apps - dedicated and shared.
 * Shared apps use the same data store table, same search index and the same cache map. 
 * Partitioning is done by key prefixes which are the same as the name of the app.
 * Dedicated apps have their own separate data stores, indexes and caches. 
 * </br>
 * <b>Only shared apps are currently supported.</b>
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class App extends PObject {
	private static final long serialVersionUID = 1L;

	@Stored @Locked @NotBlank @Size(min = 3, max = 50) private String appname;
	@Stored @Locked private boolean isShared;
	@Stored private Set<String> datatypes;

	/**
	 * No-args constructor
	 */
	public App() {
		this(null);
	}

	/**
	 * Default constructor
	 * @param appname the name of the app
	 */
	public App(String appname) {
		this.appname = appname;
		this.isShared = true;
		setId(id(appname));
		setName(appname);
	}

	static String id(String appname) {
		if (StringUtils.isBlank(appname)) {
			appname = Utils.getNewId();
		}
		return PObject.classname(App.class).concat(Config.SEPARATOR).concat(StringUtils.trimToEmpty(appname));
	}

	public Set<String> getDatatypes() {
		return datatypes;
	}

	public void setDatatypes(Set<String> datatypes) {
		this.datatypes = datatypes;
	}

	public boolean isIsShared() {
		return isShared;
	}

	public void setIsShared(boolean isShared) {
		this.isShared = isShared;
	}

	@Override
	public String getAppname() {
		if (appname == null) {
			appname = Config.APP_NAME_NS;
		}
		return appname;
	}

	@Override
	public void setAppname(String appname) {
		this.appname = appname;
	}

	@Override
	public String create() {
		if (getId() != null && this.exists()) {
			return null;
		}
		return getDao().create(this);
	}

}
