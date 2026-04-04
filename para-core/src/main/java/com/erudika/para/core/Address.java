/*
 * Copyright 2013-2026 Erudika. https://erudika.com
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

import com.erudika.para.core.annotations.Locked;
import com.erudika.para.core.annotations.Stored;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Pager;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.utils.Utils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

/**
 * This class represents an address. It enables location based search queries.
 * It can be attached to any object by creating a {@link Linker} between the two.
 * @author Alex Bogdanovski [alex@erudika.com]
 * @see Linker
 */
public class Address implements ParaObject {
	private static final long serialVersionUID = 1L;

	/**
	 * The object ID.
	 */
	@Stored @Locked private String id;
	/**
	 * The timestamp.
	 */
	@Stored @Locked private Long timestamp;
	/**
	 * The object type.
	 */
	@Stored @Locked private String type;
	/**
	 * The appid.
	 */
	@Stored @Locked private String appid;
	/**
	 * The parentid.
	 */
	@Stored @Locked private String parentid;
	/**
	 * The creatorid.
	 */
	@Stored @Locked private String creatorid;
	/**
	 * The updated timestamp.
	 */
	@Stored private Long updated;
	/**
	 * The name.
	 */
	@Stored private String name;
	/**
	 * The tags.
	 */
	@Stored private List<String> tags;
	/**
	 * The votes.
	 */
	@Stored private Integer votes;
	/**
	 * The version.
	 */
	@Stored private Long version;
	/**
	 * The stored flag.
	 */
	@Stored private Boolean stored;
	/**
	 * The indexed flag.
	 */
	@Stored private Boolean indexed;
	/**
	 * The cached flag.
	 */
	@Stored private Boolean cached;

	/**
	 * The address.
	 */
	@Stored @NotBlank @Size(min = 3, max = 255) private String address;
	/**
	 * The country.
	 */
	@Stored @NotBlank @Size(min = 2, max = 255) private String country;
	/**
	 * The city.
	 */
	@Stored @Size(min = 0, max = 255) private String city;
	/**
	 * The latitude and longitude.
	 */
	@Stored @NotBlank private String latlng;
	/**
	 * The phone.
	 */
	@Stored private String phone;

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
	 * The name of the city. Optional.
	 * @return the city
	 */
	public String getCity() {
		return city;
	}

	/**
	 * Sets the city.
	 * @param city city name
	 */
	public void setCity(String city) {
		this.city = city;
	}

	/**
	 * The Latitude and Longitude points of this address. Must not be null or empty.
	 * @return "lat,lng" as a string
	 */
	public String getLatlng() {
		return latlng;
	}

	/**
	 * Sets the coordinates in the form "LAT,LNG".
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
	 * Sets a new phone number.
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
	 * Sets a new country name.
	 * @param country the name of the country
	 */
	public void setCountry(String country) {
		this.country = country;
	}

	/**
	 * The street address and other address info.
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

	/**
	 * Returns the address ID.
	 * @return the id
	 */
	@Override
	public final String getId() {
		return id;
	}

	/**
	 * Sets the address ID.
	 * @param id the id
	 */
	@Override
	public final void setId(String id) {
		this.id = id;
	}

	/**
	 * Returns the object type.
	 * @return the type
	 */
	@Override
	public final String getType() {
		type = (type == null) ? Utils.type(this.getClass()) : type;
		return type;
	}

	/**
	 * Sets the object type.
	 * @param type the type
	 */
	@Override
	public final void setType(String type) {
		this.type = type;
	}

	/**
	 * Returns the app identifier.
	 * @return the appid
	 */
	@Override
	public String getAppid() {
		appid = (appid == null) ? Para.getConfig().getRootAppIdentifier() : appid;
		return appid;
	}

	/**
	 * Sets the app identifier.
	 * @param appid the appid
	 */
	@Override
	public void setAppid(String appid) {
		this.appid = appid;
	}

	/**
	 * Returns the object URI.
	 * @return the URI
	 */
	@Override
	public String getObjectURI() {
		return CoreUtils.getInstance().getObjectURI(this);
	}

	/**
	 * Returns the tags.
	 * @return the tags
	 */
	@Override
	public List<String> getTags() {
		return tags;
	}

	/**
	 * Returns true if the object is stored in the database.
	 * @return true if stored
	 */
	@Override
	public Boolean getStored() {
		if (stored == null) {
			stored = true;
		}
		return stored;
	}

	/**
	 * Sets the stored flag.
	 * @param stored the stored flag
	 */
	@Override
	public void setStored(Boolean stored) {
		this.stored = stored;
	}

	/**
	 * Returns true if the object is indexed.
	 * @return true if indexed
	 */
	@Override
	public Boolean getIndexed() {
		if (indexed == null) {
			indexed = true;
		}
		return indexed;
	}

	/**
	 * Sets the indexed flag.
	 * @param indexed the indexed flag
	 */
	@Override
	public void setIndexed(Boolean indexed) {
		this.indexed = indexed;
	}

	/**
	 * Returns true if the object is cached.
	 * @return true if cached
	 */
	@Override
	public Boolean getCached() {
		if (cached == null) {
			cached = true;
		}
		return cached;
	}

	/**
	 * Sets the cached flag.
	 * @param cached the cached flag
	 */
	@Override
	public void setCached(Boolean cached) {
		this.cached = cached;
	}

	/**
	 * Sets the tags.
	 * @param tags the tags
	 */
	@Override
	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	/**
	 * Returns the timestamp.
	 * @return the timestamp
	 */
	@Override
	public Long getTimestamp() {
		return (timestamp != null && timestamp != 0) ? timestamp : null;
	}

	/**
	 * Sets the timestamp.
	 * @param timestamp the timestamp
	 */
	@Override
	public void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Returns the creator ID.
	 * @return the creatorid
	 */
	@Override
	public String getCreatorid() {
		return creatorid;
	}

	/**
	 * Sets the creator ID.
	 * @param creatorid the creatorid
	 */
	@Override
	public void setCreatorid(String creatorid) {
		this.creatorid = creatorid;
	}

	/**
	 * Returns the object name.
	 * @return the name
	 */
	@Override
	public final String getName() {
		return CoreUtils.getInstance().getName(name, id);
	}

	/**
	 * Sets the object name.
	 * @param name the name
	 */
	@Override
	public final void setName(String name) {
		this.name = (name == null || !name.isEmpty()) ? name : this.name;
	}

	/**
	 * Returns the plural form of the object type.
	 * @return the plural name
	 */
	@Override
	public String getPlural() {
		return Utils.singularToPlural(getType());
	}

	/**
	 * Returns the parent ID.
	 * @return the parentid
	 */
	@Override
	public String getParentid() {
		return parentid;
	}

	/**
	 * Sets the parent ID.
	 * @param parentid the parentid
	 */
	@Override
	public void setParentid(String parentid) {
		this.parentid = parentid;
	}

	/**
	 * Returns the updated timestamp.
	 * @return the updated timestamp
	 */
	@Override
	public Long getUpdated() {
		return (updated != null && updated != 0) ? updated : null;
	}

	/**
	 * Sets the updated timestamp.
	 * @param updated the updated timestamp
	 */
	@Override
	public void setUpdated(Long updated) {
		this.updated = updated;
	}

	/**
	 * Persists the object to the database.
	 * @return the object ID
	 */
	@Override
	public String create() {
		return CoreUtils.getInstance().getDao().create(getAppid(), this);
	}

	/**
	 * Updates the object in the database.
	 */
	@Override
	public void update() {
		CoreUtils.getInstance().getDao().update(getAppid(), this);
	}

	/**
	 * Deletes the object from the database.
	 */
	@Override
	public void delete() {
		CoreUtils.getInstance().getDao().delete(getAppid(), this);
	}

	/**
	 * Returns true if the object exists in the database.
	 * @return true if exists
	 */
	@Override
	public boolean exists() {
		return CoreUtils.getInstance().getDao().read(getAppid(), getId()) != null;
	}

	/**
	 * Votes up for the object.
	 * @param userid the voter ID
	 * @return true if successful
	 */
	@Override
	public boolean voteUp(String userid) {
		return CoreUtils.getInstance().vote(this, userid, VoteValue.UP);
	}

	/**
	 * Votes down for the object.
	 * @param userid the voter ID
	 * @return true if successful
	 */
	@Override
	public boolean voteDown(String userid) {
		return CoreUtils.getInstance().vote(this, userid, VoteValue.DOWN);
	}

	/**
	 * Returns the number of votes.
	 * @return the votes
	 */
	@Override
	public Integer getVotes() {
		return (votes == null) ? 0 : votes;
	}

	/**
	 * Sets the number of votes.
	 * @param votes the votes
	 */
	@Override
	public void setVotes(Integer votes) {
		this.votes = votes;
	}

	/**
	 * Returns the version of the object.
	 * @return the version
	 */
	@Override
	public Long getVersion() {
		return (version == null) ? 0 : version;
	}

	/**
	 * Sets the version of the object.
	 * @param version the version
	 */
	@Override
	public void setVersion(Long version) {
		this.version = version;
	}

	/**
	 * Counts the number of links to other objects of a certain type.
	 * @param type2 the other type
	 * @return the number of links
	 */
	@Override
	public Long countLinks(String type2) {
		return CoreUtils.getInstance().countLinks(this, type2);
	}

	/**
	 * Returns a list of links to other objects of a certain type.
	 * @param type2 the other type
	 * @param pager a pager
	 * @return a list of links
	 */
	@Override
	public List<Linker> getLinks(String type2, Pager... pager) {
		return CoreUtils.getInstance().getLinks(this, type2, pager);
	}

	/**
	 * Returns a list of linked objects of a certain type.
	 * @param <P> the type of linked objects
	 * @param type the other type
	 * @param pager a pager
	 * @return a list of linked objects
	 */
	@Override
	public <P extends ParaObject> List<P> getLinkedObjects(String type, Pager... pager) {
		return CoreUtils.getInstance().getLinkedObjects(this, type, pager);
	}

	/**
	 * Finds linked objects of a certain type that match a specific field and query.
	 * @param <P> the type of linked objects
	 * @param type the other type
	 * @param field the field to search in
	 * @param query the search query
	 * @param pager a pager
	 * @return a list of linked objects
	 */
	@Override
	public <P extends ParaObject> List<P> findLinkedObjects(String type, String field, String query, Pager... pager) {
		return CoreUtils.getInstance().findLinkedObjects(this, type, field, query, pager);
	}

	/**
	 * Returns true if the object is linked to another object of a certain type and ID.
	 * @param type2 the other type
	 * @param id2 the other ID
	 * @return true if linked
	 */
	@Override
	public boolean isLinked(String type2, String id2) {
		return CoreUtils.getInstance().isLinked(this, type2, id2);
	}

	/**
	 * Returns true if the object is linked to another object.
	 * @param toObj the other object
	 * @return true if linked
	 */
	@Override
	public boolean isLinked(ParaObject toObj) {
		return CoreUtils.getInstance().isLinked(this, toObj);
	}

	/**
	 * Links the object to another object of a certain type and ID.
	 * @param id2 the other ID
	 * @return the link ID
	 */
	@Override
	public String link(String id2) {
		return CoreUtils.getInstance().link(this, id2);
	}

	/**
	 * Unlinks the object from another object of a certain type and ID.
	 * @param type the other type
	 * @param id2 the other ID
	 */
	@Override
	public void unlink(String type, String id2) {
		CoreUtils.getInstance().unlink(this, type, id2);
	}

	/**
	 * Unlinks the object from all other objects.
	 */
	@Override
	public void unlinkAll() {
		CoreUtils.getInstance().unlinkAll(this);
	}

	/**
	 * Counts the number of children of a certain type.
	 * @param type the child type
	 * @return the number of children
	 */
	@Override
	public Long countChildren(String type) {
		return CoreUtils.getInstance().countChildren(this, type);
	}

	/**
	 * Returns a list of children of a certain type.
	 * @param <P> the type of children
	 * @param type the child type
	 * @param pager a pager
	 * @return a list of children
	 */
	@Override
	public <P extends ParaObject> List<P> getChildren(String type, Pager... pager) {
		return CoreUtils.getInstance().getChildren(this, type, pager);
	}

	/**
	 * Returns a list of children of a certain type that match a specific field and term.
	 * @param <P> the type of children
	 * @param type the child type
	 * @param field the field to search in
	 * @param term the search term
	 * @param pager a pager
	 * @return a list of children
	 */
	@Override
	public <P extends ParaObject> List<P> getChildren(String type, String field, String term, Pager... pager) {
		return CoreUtils.getInstance().getChildren(this, type, field, term, pager);
	}

	/**
	 * Finds children of a certain type that match a specific query.
	 * @param <P> the type of children
	 * @param type the child type
	 * @param query the search query
	 * @param pager a pager
	 * @return a list of children
	 */
	@Override
	public <P extends ParaObject> List<P> findChildren(String type, String query, Pager... pager) {
		return CoreUtils.getInstance().findChildren(this, type, query, pager);
	}

	/**
	 * Deletes all children of a certain type.
	 * @param type the child type
	 */
	@Override
	public void deleteChildren(String type) {
		CoreUtils.getInstance().deleteChildren(this, type);
	}

	/**
	 * Returns the hash code for this object.
	 * @return hash code
	 */
	@Override
	public int hashCode() {
		int hash = 7;
		hash = 67 * hash + Objects.hashCode(this.id) + Objects.hashCode(this.name);
		return hash;
	}

	/**
	 * Returns true if this object is equal to another.
	 * @param obj the other object
	 * @return true if equal
	 */
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

	/**
	 * Returns a JSON string representation of this object.
	 * @return JSON string
	 */
	@Override
	public String toString() {
		return ParaObjectUtils.toJSON(this);
	}
}

