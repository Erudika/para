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
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;

/**
 * This class represents a many-to-many relationship (link) between two objects.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class Linker implements ParaObject {
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
	@Stored @Locked private String plural;
	@Stored @Locked private String objectURI;

	@Stored @Locked @NotBlank private String id1;
	@Stored @Locked @NotBlank private String id2;
	@Stored @Locked @NotBlank private String type1;
	@Stored @Locked @NotBlank private String type2;
	@Stored private String metadata;

	private transient String shardKey;
	private transient DAO dao;
	private transient Search search;

	/**
	 * No-args constructor
	 */
	public Linker() { }

	/**
	 * A link. The names of the objects are compared and sorted alphabetically.
	 * @param type1 the type of the first object
	 * @param type2 the type of the second object
	 * @param id1 the id of the first object
	 * @param id2 the id of the second object
	 */
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
	 * Returns the additional information about the link
	 * @return some info
	 */
	public String getMetadata() {
		return metadata;
	}

	/**
	 * Sets additional information about the link
	 * @param metadata some info
	 */
	public void setMetadata(String metadata) {
		this.metadata = metadata;
	}

//	public void delete() {
//		ArrayList<String> keys = new ArrayList<String>();
//		for (ParaObject link : search.findTerms(getType(), null, null, "id1", id1, "id2", id2)) {
//			keys.add(link.getId());
//		}
//		AWSDynamoDAO.getInstance().deleteAll(keys);
//	}

	@Override
	public boolean exists() {
		return getDao().read(getAppid(), getId()) != null;
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
	 * Returns "id1" or "id2" depending on the alphabetical order of type
	 * @param type the type
	 * @return id1 or id2
	 */
	public String getIdFieldNameFor(String type) {
		return isFirst(type) ? "id1" : "id2";
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
	public String getName() {
		return CoreUtils.getName(name, id);
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
	public String create() {
		return getDao().create(getAppid(), this);
	}

	@Override
	public void update() {
		getDao().update(getAppid(), this);
	}

	@Override
	public void delete() {
		getDao().delete(getAppid(), this);
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
	public String getShardKey() {
		return shardKey;
	}

	@Override
	public void setShardKey(String shardKey) {
		this.shardKey = shardKey;
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
