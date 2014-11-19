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
import javax.validation.constraints.Size;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;

/**
 * This class represents an address. It enables location based search queries.
 * It can be attached to any object by creating a {@link Linker} between the two.
 * @author Alex Bogdanovski [alex@erudika.com]
 * @see Linker
 */
public class Address implements ParaObject {
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

	@Stored @NotBlank @Size(min = 3, max = 255) private String address;
	@Stored @NotBlank @Size(min = 2, max = 255) private String country;
	@Stored @NotBlank private String latlng;
	@Stored private String phone;

	private transient String shardKey;
	private transient DAO dao;
	private transient Search search;

	/**
	 * No-args constructor.
	 * Same as {@code Address(null)}
	 */
	public Address() {
		this(null);
	}

	/**
	 * Default constructor.
	 * @param id the id
	 */
	public Address(String id) {
		setId(id);
		setName(getName());
	}

	/**
	 * The Latitude and Longitude points of this address. Must not be null or empty.
	 * @return "lat,lng" as a string
	 */
	public String getLatlng() {
		return latlng;
	}

	/**
	 * Sets the coordinates in the form "LAT,LNG"
	 * @param latlng the coords
	 */
	public void setLatlng(String latlng) {
		this.latlng = latlng;
	}

	/**
	 * The phone number. Multiple phones separated by comma are allowed.
	 * @return the phone number
	 */
	public String getPhone() {
		return phone;
	}

	/**
	 * Sets a new phone number
	 * @param phone the number as a string
	 */
	public void setPhone(String phone) {
		this.phone = phone;
	}

	/**
	 * The country in which the address is located. Must not be null or empty.
	 * @return the full country name
	 */
	public String getCountry() {
		return country;
	}

	/**
	 * Sets a new country name
	 * @param country the name of the country
	 */
	public void setCountry(String country) {
		this.country = country;
	}

	/**
	 * The street address and other address info
	 * @return the street address
	 */
	public String getAddress() {
		return address;
	}

	/**
	 * Sets new street address. Must not be null or empty.
	 * @param address the address info
	 */
	public void setAddress(String address) {
		this.address = address;
	}

	////////////////////////////////////////////////////////

	@Override
	public final String getId() {
		return id;
	}

	@Override
	public final void setId(String id) {
		this.id = id;
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

