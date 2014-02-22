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
 * Object separation is done by key prefixes which are the same as the appid.
 * Dedicated apps have their own separate data stores, indexes and caches. 
 * </br>
 * <b>Only shared apps are currently supported.</b>
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class App extends PObject {
	private static final long serialVersionUID = 1L;
	private static final String prefix = Utils.type(App.class).concat(Config.SEPARATOR);
	
	@Stored @Locked private boolean shared;
	@Stored @Locked @NotBlank private String appid;
	@Stored private Set<String> datatypes;

	/**
	 * No-args constructor
	 */
	public App() {
		this(null);
	}

	/**
	 * Default constructor
	 * @param appid the name of the app
	 */
	public App(String appid) {
		this.shared = true;
		setAppid(appid);
		setName(getName());
	}

	@Override
	public void setId(String id) {
		if (StringUtils.isBlank(id) || StringUtils.startsWith(id, prefix)) {
			super.setId(id);
		} else {
			setAppid(id);
			super.setId(prefix.concat(getAppid()));
		}
	}
	
	@Override
	public String getObjectURI() {
		String defurl = "/".concat(getPlural());
		return (getAppid() != null) ? defurl.concat("/").concat(getAppid()) : defurl;
	}

	public final String getAppid() {
		return appid;
	}

	public final void setAppid(String appid) {
		this.appid = Utils.stripAndTrim(Utils.noSpaces(appid, ""));
		if (!this.appid.isEmpty()) {
			setId(prefix.concat(appid));
		}
	}

	public Set<String> getDatatypes() {
		return datatypes;
	}

	public void setDatatypes(Set<String> datatypes) {
		this.datatypes = datatypes;
	}

	public boolean isShared() {
		return shared;
	}

	public void setShared(boolean shared) {
		this.shared = shared;
	}

	@Override
	public String create() {
		if (getId() != null && this.exists()) {
			return null;
		}
		return getDao().create(this);
	}

}
