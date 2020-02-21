/*
 * Copyright 2013-2020 Erudika. https://erudika.com
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

import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.constraints.NotBlank;

/**
 * This class represents a many-to-many relationship (link) between two objects.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class Linker implements ParaObject, Serializable {
	private static final long serialVersionUID = 1L;

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
	@Stored private Long version;
	@Stored private Boolean stored;
	@Stored private Boolean indexed;
	@Stored private Boolean cached;

	@Stored @Locked @NotBlank private String id1;
	@Stored @Locked @NotBlank private String id2;
	@Stored @Locked @NotBlank private String type1;
	@Stored @Locked @NotBlank private String type2;
	@Stored private String metadata;

	// holds both linked objects inside a nested array
	// implemented in Elasticsearch 2.3+ with the 'nested' datatype
	@Stored private List<Map<String, Object>> nstd;

	/**
	 * No-args constructor.
	 */
	public Linker() { }

	/**
	 * A link. The names of the objects are compared and sorted alphabetically.
	 * @param type1 the type of the first object
	 * @param type2 the type of the second object
	 * @param id1 the id of the first object
	 * @param id2 the id of the second object
	 */
	@SuppressWarnings("unchecked")
	public Linker(String type1, String type2, String id1, String id2) {
		if (isReversed(type1, type2)) {
			this.type1 = type2;
			this.type2 = type1;
			this.id1 = id2;
			this.id2 = id1;
		} else {
			this.type1 = type1;
			this.type2 = type2;
			this.id1 = id1;
			this.id2 = id2;
		}
		setName(this.type1 + Config.SEPARATOR + this.type2);
		setId(this.type1 + Config.SEPARATOR + this.id1 + Config.SEPARATOR + this.type2 + Config.SEPARATOR + this.id2);
	}

	/**
	 * Returns the id of the second object in the link.
	 * @return the id
	 */
	public String getId2() {
		return id2;
	}

	/**
	 * Sets the id of the second object in the link.
	 * @param id2 a new id
	 */
	public void setId2(String id2) {
		this.id2 = id2;
	}

	/**
	 * Returns the id of the first object in the link.
	 * @return the id
	 */
	public String getId1() {
		return id1;
	}

	/**
	 * Sets the id of the first object in the link.
	 * @param id1 a new id
	 */
	public void setId1(String id1) {
		this.id1 = id1;
	}

	/**
	 * Returns the type of the first object in the link.
	 * @return the type
	 */
	public String getType1() {
		return type1;
	}

	/**
	 * Sets the type of the first object in the link.
	 * @param type1 the type
	 */
	public void setType1(String type1) {
		this.type1 = type1;
	}

	/**
	 * Returns the type of the second object in the link.
	 * @return the type
	 */
	public String getType2() {
		return type2;
	}

	/**
	 * Sets the type of the second object in the link.
	 * @param type2 second type
	 */
	public void setType2(String type2) {
		this.type2 = type2;
	}

	/**
	 * Returns the additional information about the link.
	 * @return some info
	 */
	public String getMetadata() {
		return metadata;
	}

	/**
	 * Sets additional information about the link.
	 * @param metadata some info
	 */
	public void setMetadata(String metadata) {
		this.metadata = metadata;
	}

	/**
	 * Compare the names of the two linkable objects
	 * and decide if we need to swap their positions.
	 * For example: isReversed(user, tag) - true; isReversed(tag, user) - false
	 */
	private boolean isReversed(String s1, String s2) {
		if (s1 == null || s2 == null) {
			return false;
		}
		return s1.compareToIgnoreCase(s2) > 0;
	}

	/**
	 * Checks if the position of a given object is first or second.
	 * @param type2 the given class of object
	 * @return true if the object's type is equal to {@link #getType1()}
	 */
	public boolean isFirst(String type2) {
		if (type2 == null) {
			return false;
		}
		return type2.equals(type1);
	}

	/**
	 * Returns "id1" or "id2" depending on the alphabetical order of type.
	 * @param type the type
	 * @return id1 or id2
	 */
	public String getIdFieldNameFor(String type) {
		return isFirst(type) ? "id1" : "id2";
	}

	/**
	 * Get the nested objects that are linked by this link.
	 * @return a 2-element array of objects or null
	 */
	public List<Map<String, Object>> getNstd() {
		if (nstd == null) {
			nstd = new ArrayList<>();
		}
		return nstd;
	}

	/**
	 * Sets the nested array of objects that are linked by this link.
	 * @param nstd an array of 2 objects only
	 */
	public void setNstd(List<Map<String, Object>> nstd) {
		this.nstd = nstd;
	}

	/**
	 * Add an object to nest inside the linker object.
	 * Used for joining queries when searching objects in a many-to-many relationship.
	 * @param obj object
	 */
	public void addNestedObject(ParaObject obj) {
		if (obj != null) {
			getNstd().add(ParaObjectUtils.getAnnotatedFields(obj, false));
		}
	}

	////////////////////////////////////////////////////////

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public String getType() {
		type = (type == null) ? Utils.type(this.getClass()) : type;
		return type;
	}

	@Override
	public void setType(String type) {
		this.type = type;
	}

	@Override
	public String getAppid() {
		appid = (appid == null) ? Config.getRootAppIdentifier() : appid;
		return appid;
	}

	@Override
	public void setAppid(String appid) {
		this.appid = appid;
	}

	@Override
	public String getObjectURI() {
		return CoreUtils.getInstance().getObjectURI(this);
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
	public Boolean getStored() {
		if (stored == null) {
			stored = true;
		}
		return stored;
	}

	@Override
	public void setStored(Boolean stored) {
		this.stored = stored;
	}

	@Override
	public Boolean getIndexed() {
		if (indexed == null) {
			indexed = true;
		}
		return indexed;
	}

	@Override
	public void setIndexed(Boolean indexed) {
		this.indexed = indexed;
	}

	@Override
	public Boolean getCached() {
		if (cached == null) {
			cached = true;
		}
		return cached;
	}

	@Override
	public void setCached(Boolean cached) {
		this.cached = cached;
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
	public String getName() {
		return CoreUtils.getInstance().getName(name, id);
	}

	@Override
	public void setName(String name) {
		this.name = (name == null || !name.isEmpty()) ? name : this.name;
	}

	@Override
	public String getPlural() {
		return Utils.singularToPlural(getType());
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
	public String create() {
		return CoreUtils.getInstance().getDao().create(getAppid(), this);
	}

	@Override
	public void update() {
		CoreUtils.getInstance().getDao().update(getAppid(), this);
	}

	@Override
	public void delete() {
		CoreUtils.getInstance().getDao().delete(getAppid(), this);
	}

	@Override
	public boolean exists() {
		return CoreUtils.getInstance().getDao().read(getAppid(), getId()) != null;
	}

	@Override
	public boolean voteUp(String userid) {
		return CoreUtils.getInstance().vote(this, userid, VoteValue.UP);
	}

	@Override
	public boolean voteDown(String userid) {
		return CoreUtils.getInstance().vote(this, userid, VoteValue.DOWN);
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
	public Long getVersion() {
		return (version == null) ? 0 : version;
	}

	@Override
	public void setVersion(Long version) {
		this.version = version;
	}

	@Override
	public Long countLinks(String type2) {
		return CoreUtils.getInstance().countLinks(this, type2);
	}

	@Override
	public List<Linker> getLinks(String type2, Pager... pager) {
		return CoreUtils.getInstance().getLinks(this, type2, pager);
	}

	@Override
	public <P extends ParaObject> List<P> getLinkedObjects(String type, Pager... pager) {
		return CoreUtils.getInstance().getLinkedObjects(this, type, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findLinkedObjects(String type, String field, String query, Pager... pager) {
		return CoreUtils.getInstance().findLinkedObjects(this, type, field, query, pager);
	}

	@Override
	public boolean isLinked(String type2, String id2) {
		return CoreUtils.getInstance().isLinked(this, type2, id2);
	}

	@Override
	public boolean isLinked(ParaObject toObj) {
		return CoreUtils.getInstance().isLinked(this, toObj);
	}

	@Override
	public String link(String id2) {
		return CoreUtils.getInstance().link(this, id2);
	}

	@Override
	public void unlink(String type, String id2) {
		CoreUtils.getInstance().unlink(this, type, id2);
	}

	@Override
	public void unlinkAll() {
		CoreUtils.getInstance().unlinkAll(this);
	}

	@Override
	public Long countChildren(String type) {
		return CoreUtils.getInstance().countChildren(this, type);
	}

	@Override
	public <P extends ParaObject> List<P> getChildren(String type, Pager... pager) {
		return CoreUtils.getInstance().getChildren(this, type, pager);
	}

	@Override
	public <P extends ParaObject> List<P> getChildren(String type, String field, String term, Pager... pager) {
		return CoreUtils.getInstance().getChildren(this, type, field, term, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findChildren(String type, String query, Pager... pager) {
		return CoreUtils.getInstance().findChildren(this, type, query, pager);
	}

	@Override
	public void deleteChildren(String type) {
		CoreUtils.getInstance().deleteChildren(this, type);
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
		return ParaObjectUtils.toJSON(this);
	}
}
