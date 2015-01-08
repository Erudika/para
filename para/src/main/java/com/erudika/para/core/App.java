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

import com.erudika.para.Para;
import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.persistence.DAO;
import com.erudika.para.search.ElasticSearchUtils;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;

/**
 * This is a representation of an application within Para.
 * <br>
 * It allows the user to create separate apps running on the same infrastructure.
 * The apps are separated by name and each {@link ParaObject} belongs to an app.
 * <br>
 * There can be two types of apps - dedicated and shared.
 * Shared apps use their own database table and cache, but share the same search index.
 * Dedicated apps have their own separate database tables, caches and search index.
 * Sharding is done using the shard key which is usually the same as the appid.
 * <br>
 * Usually when we have a multi-app environment there's a parent app (dedicated) and
 * lots of child apps (shared) that share the same index with the parent app.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class App implements ParaObject {

	public static final String APP_ROLE = "ROLE_APP";
	private static final long serialVersionUID = 1L;
	private static final String prefix = Utils.type(App.class).concat(Config.SEPARATOR);

	@Stored @Locked private String id;
	@Stored @Locked private Long timestamp;
	@Stored @Locked private String type;
	@Stored @Locked private String appid;
	@Stored @Locked private String parentid;
	@Stored @Locked private String creatorid;
	@Stored private Long updated;
	@Stored private String name;
	@Stored private List<String> tags;
	@Stored private Integer votes;
	@Stored @Locked private String plural;
	@Stored @Locked private String objectURI;

	@Stored @Locked private boolean shared;
	@Stored @Locked @NotBlank private String secret;
	@Stored @Locked private Boolean readOnly;
	@Stored private Map<String, String> datatypes;
	@Stored private Boolean active;
	@Stored private Long deleteOn;

	private transient DAO dao;
	private transient Search search;

	/**
	 * No-args constructor
	 */
	public App() {
		this(null);
	}

	/**
	 * Default constructor
	 * @param id the name of the app
	 */
	public App(String id) {
		this.shared = true;
		this.active = true;
		setId(id);
		setName(getName());
	}

	/**
	 * The App identifier (the id but without the prefix)
	 * @return the identifier (appid)
	 */
	public String getAppIdentifier() {
		return (getId() != null) ? getId().replaceFirst(prefix, "") : "";
	}

	/**
	 * Returns true if this application is active (enabled)
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
	 * The timestamp for when this app must be deleted.
	 * @return a timestamp
	 */
	public Long getDeleteOn() {
		return deleteOn;
	}

	/**
	 * Sets the time for deletion
	 * @param deleteOn a timestamp
	 */
	public void setDeleteOn(Long deleteOn) {
		this.deleteOn = deleteOn;
	}

	/**
	 * Returns the app's secret key
	 * @return the secret key
	 */
	@JsonIgnore
	public String getSecret() {
		if (secret == null) {
			resetSecret();
		}
		return secret;
	}

	/**
	 * Sets the secret key
	 * @param secret a secret key
	 */
	public void setSecret(String secret) {
		this.secret = secret;
	}

	/**
	 * Gets read-only mode.
	 * @return true if app is in read-only mode
	 */
	public Boolean getReadOnly() {
		return readOnly;
	}

	/**
	 * Sets read-only mode
	 * @param readOnly true if app is in read-only mode
	 */
	public void setReadOnly(Boolean readOnly) {
		this.readOnly = readOnly;
	}

	/**
	 * Returns a set of custom data types for this app.
	 * An app can have many custom types which describe its domain.
	 * @return a map of type names (plural form to singular)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, String> getDatatypes() {
		if (datatypes == null) {
			datatypes = new DualHashBidiMap();
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
		if (!StringUtils.isBlank(pluralDatatype) && !StringUtils.isBlank(datatype) &&
				!getDatatypes().containsValue(datatype)) {
			getDatatypes().putIfAbsent(pluralDatatype, datatype);
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
	@JsonIgnore
	public Map<String, String> getCredentials() {
		if (getId() == null) {
			return Collections.emptyMap();
		} else {
			return new HashMap<String, String>() {
				private static final long serialVersionUID = 1L;
				{
					put("accessKey", getId());
					put("secretKey", getSecret());
				}
			};
		}
	}

	@Override
	public String create() {
		if (getId() != null && this.exists()) {
			return null;
		}
		resetSecret();
		return getDao().create(this);
	}

	@Override
	public void delete() {
		// root app cannot be deleted
		if (!StringUtils.equals(getId(), prefix.concat(Config.APP_NAME_NS))) {
			if (!shared) {
				ElasticSearchUtils.deleteIndex(getAppIdentifier());
			}

			getDao().delete(this);
		}
	}

	@Override
	public final void setId(String id) {
		if (StringUtils.startsWith(id, prefix)) {
			this.id = prefix.concat(Utils.noSpaces(Utils.stripAndTrim(id.replaceAll(prefix, ""), " "), "-"));
		} else if (id != null) {
			this.id = prefix.concat(Utils.noSpaces(Utils.stripAndTrim(id, " "), "-"));
		}
	}

	////////////////////////////////////////////////////////

	@Override
	public final String getId() {
		return id;
	}

	@Override
	public final String getType() {
		type = (type == null) ? Utils.type(this.getClass()) : type;
		return type;
	}

	@Override
	public final void setType(String type) {
		this.type = type;
	}

	@Override
	public String getAppid() {
		appid = (appid == null) ? Config.APP_NAME_NS : appid;
		return appid;
	}

	@Override
	public void setAppid(String appid) {
		this.appid = appid;
	}

	@Override
	public String getObjectURI() {
		return CoreUtils.getObjectURI(this);
	}

	@Override
	public List<String> getTags() {
		return tags;
	}

	@Override
	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	@Override
	public Long getTimestamp() {
		return (timestamp != null && timestamp != 0) ? timestamp : null;
	}

	@Override
	public void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}

	@Override
	public String getCreatorid() {
		return creatorid;
	}

	@Override
	public void setCreatorid(String creatorid) {
		this.creatorid = creatorid;
	}

	@Override
	public final String getName() {
		return CoreUtils.getName(name, id);
	}

	@Override
	public final void setName(String name) {
		this.name = (name == null || !name.isEmpty()) ? name : this.name;
	}

	@Override
	public String getPlural() {
		return Utils.singularToPlural(getType());
	}

	@Override
	public ParaObject getParent() {
		return getDao().read(getAppid(), parentid);
	}

	@Override
	public ParaObject getCreator() {
		return getDao().read(getAppid(), creatorid);
	}

	@Override
	public String getParentid() {
		return parentid;
	}

	@Override
	public void setParentid(String parentid) {
		this.parentid = parentid;
	}

	@Override
	public Long getUpdated() {
		return (updated != null && updated != 0) ? updated : null;
	}

	@Override
	public void setUpdated(Long updated) {
		this.updated = updated;
	}

	@Override
	public void update() {
		getDao().update(getAppid(), this);
	}

	@Override
	public boolean exists() {
		return getDao().read(id) != null;
	}

	@Override
	public DAO getDao() {
		if (dao == null) {
			dao = Para.getDAO();
		}
		return dao;
	}

	@Override
	public void setDao(DAO dao) {
		this.dao = dao;
	}

	@Override
	public Search getSearch() {
		if (search == null) {
			search = Para.getSearch();
		}
		return search;
	}

	@Override
	public void setSearch(Search search) {
		this.search = search;
	}

	@Override
	public boolean voteUp(String userid) {
		return CoreUtils.vote(this, userid, VoteValue.UP);
	}

	@Override
	public boolean voteDown(String userid) {
		return CoreUtils.vote(this, userid, VoteValue.DOWN);
	}

	@Override
	public Integer getVotes() {
		return (votes == null) ? 0 : votes;
	}

	@Override
	public void setVotes(Integer votes) {
		this.votes = votes;
	}

	@Override
	public Long countLinks(String type2) {
		return CoreUtils.countLinks(this, type2);
	}

	@Override
	public List<Linker> getLinks(String type2, Pager... pager) {
		return CoreUtils.getLinks(this, type2, pager);
	}

	@Override
	public <P extends ParaObject> List<P> getLinkedObjects(String type, Pager... pager) {
		return CoreUtils.getLinkedObjects(this, type, pager);
	}

	@Override
	public boolean isLinked(String type2, String id2) {
		return CoreUtils.isLinked(this, type2, id2);
	}

	@Override
	public boolean isLinked(ParaObject toObj) {
		return CoreUtils.isLinked(this, toObj);
	}

	@Override
	public String link(String id2) {
		return CoreUtils.link(this, id2);
	}

	@Override
	public void unlink(String type, String id2) {
		CoreUtils.unlink(this, type, id2);
	}

	@Override
	public void unlinkAll() {
		CoreUtils.unlinkAll(this);
	}

	@Override
	public Long countChildren(String type) {
		return CoreUtils.countChildren(this, type);
	}

	@Override
	public <P extends ParaObject> List<P> getChildren(String type, Pager... pager) {
		return CoreUtils.getChildren(this, type, pager);
	}

	@Override
	public <P extends ParaObject> List<P> getChildren(String type, String field, String term, Pager... pager) {
		return CoreUtils.getChildren(this, type, field, term, pager);
	}

	@Override
	public void deleteChildren(String type) {
		CoreUtils.deleteChildren(this, type);
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 67 * hash + Objects.hashCode(this.id) + Objects.hashCode(this.name);
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final ParaObject other = (ParaObject) obj;
		if (!Objects.equals(this.id, other.getId())) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return Utils.toJSON(this);
	}
}
