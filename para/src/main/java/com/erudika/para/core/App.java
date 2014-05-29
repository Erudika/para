/*
 * Copyright 2013-2014 Erudika. http://erudika.com
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
 * For issues and patches go to: https://github.com/erudika
 */
package com.erudika.para.core;

import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;

/**
 * This is a representation of an application within Para. <b>This functionality is WORK IN PROGRESS!</b>
 * <br>
 * It allows the user to create separate apps running on the same infrastructure.
 * The apps are separated by name and each {@link ParaObject} belongs to an app.
 * <br>
 * There can be two ways to separate apps - dedicated and shared.
 * Shared apps use the same data store table, same search index and the same cache map.
 * Object separation is done by key prefixes which are the same as the appid.
 * Dedicated apps have their own separate data stores, indexes and caches.
 * <br>
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class App extends PObject {

	public static final String APP_ROLE = "ROLE_APP";

	private static final long serialVersionUID = 1L;
	private static final String prefix = Utils.type(App.class).concat(Config.SEPARATOR);

	@Stored @Locked private boolean shared;
	@Stored @Locked @NotBlank private String appid;
	@Stored @Locked @NotBlank private String secret;
	@Stored private Map<String, String> datatypes;
	@Stored @Locked private Boolean active;

	private Map<String, String> credentials;

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
		this.shared = false;
		this.active = true;
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

	/**
	 * Returs true if this application is active (enabled)
	 * @return true if active
	 */
	public Boolean getActive() {
		return active;
	}

	/**
	 * Sets the active flag
	 * @param active true if active
	 */
	public void setActive(Boolean active) {
		this.active = active;
	}

	/**
	 * Returns the app's secret key
	 * @return the secret key
	 */
	@JsonIgnore
	public String getSecret() {
		return secret;
	}

	/**
	 * Sets the secret key
	 * @param secret a secret key
	 */
	public void setSecret(String secret) {
		this.secret = secret;
	}

	@Override
	public final String getAppid() {
		return appid;
	}

	@Override
	public final void setAppid(String appid) {
		this.appid = Utils.stripAndTrim(Utils.noSpaces(appid, ""));
		if (!this.appid.isEmpty()) {
			setId(prefix.concat(appid));
		}
	}

	/**
	 * Returns a set of custom data types for this app.
	 * An app can have many custom types which describe its domain.
	 * @return a map of type names (plural form to singular)
	 */
	public Map<String, String> getDatatypes() {
		if (datatypes == null) {
			datatypes = new HashMap<String, String>();
		}
		return datatypes;
	}

	/**
	 * Sets the data types for this app
	 * @param datatypes a map of type names (plural form to singular)
	 */
	public void setDatatypes(Map<String, String> datatypes) {
		this.datatypes = datatypes;
	}

	/**
	 * Is this a shared app (shared db, index, etc.)
	 * @return true if shared
	 */
	public boolean isShared() {
		return shared;
	}

	/**
	 * Sets the shared flag
	 * @param shared true if shared
	 */
	public void setShared(boolean shared) {
		this.shared = shared;
	}

	/**
	 * Adds a user-defined data type to the types map.
	 * @param pluralDatatype the plural form of the type
	 * @param datatype a datatype, must not be null or empty
	 */
	public void addDatatype(String pluralDatatype, String datatype) {
		if (!StringUtils.isBlank(pluralDatatype) && !StringUtils.isBlank(datatype)) {
			getDatatypes().put(pluralDatatype, datatype);
		}
	}

	/**
	 * Removes a datatype from the types map.
	 * @param pluralDatatype a datatype, must not be null or empty
	 */
	public void removeDatatype(String pluralDatatype) {
		if (!StringUtils.isBlank(pluralDatatype)) {
			getDatatypes().remove(pluralDatatype);
		}
	}

	/**
	 * Resets the secret key by generating a new one.
	 */
	public void resetSecret() {
		secret = Utils.generateSecurityToken(40);
	}

	/**
	 * Returns the map containing the app's access key and secret key.
	 * @return a map of API keys (never null)
	 */
	public Map<String, String> getCredentials() {
		return credentials;
	}

	/**
	 * Builds a map of access key and secret key, plus some information
	 * @return a map of API keys
	 */
	public Map<String, String> credentialsMap() {
		Map<String, String> creds = new HashMap<String, String>();
		creds.put("accessKey", Utils.base64enc(getId().getBytes()));
		creds.put("secretKey", getSecret());
		creds.put("info", "Save the secret key! It is showed only once!");
		return creds;
	}

	@Override
	public String create() {
		if (getId() != null && this.exists()) {
			return null;
		}
		if (StringUtils.isBlank(secret)) {
			resetSecret();
		}
		// show credentials when viewed as JSON
		credentials = credentialsMap();
		return getDao().create(this);
	}

	/**
	 * Reads the application from the datastore.
	 * @param appWithId app with the id set
	 * @return an App instance or null
	 */
	public static App readApp(App appWithId) {
		if (appWithId == null || StringUtils.isBlank(appWithId.getId())) {
			return null;
		}
		return appWithId.getDao().read(appWithId.getId());
	}

}
