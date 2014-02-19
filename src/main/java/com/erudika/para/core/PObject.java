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

import com.erudika.para.utils.Pager;
import com.erudika.para.Para;
import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.persistence.DAO;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.validation.constraints.Size;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.validator.constraints.NotBlank;

/**
 * The basic implementation for {@link ParaObject}. Provides the basic functionality for domain objects.
 * @author Alex Bogdanovski <alex@erudika.com>
 * @see ParaObject
 */
public abstract class PObject implements ParaObject, Linkable, Votable {

	@Stored @Locked private String id;
	@Stored @Locked private Long timestamp;
	@Stored @Locked private Long updated;
	@Stored @Locked private String classname;
	@Stored @Locked private String appname;
	@Stored @Locked private String parentid;
	@Stored @Locked private String creatorid;
	@Stored private String name;
	@Stored private Set<String> tags;
	@Stored private Integer votes;
	@Stored @Locked private String plural;
	@Stored @Locked private String objectURI;

	private transient PObject parent;
	private transient PObject creator;

	private transient DAO dao;
	private transient Search search;

	/**
	 * Returns the core persistence object.
	 * @return a {@link com.erudika.para.persistence.DAO} instance
	 */
	@JsonIgnore
	public DAO getDao() {
		if (dao == null) {
			dao = Para.getDAO();
		}
		return dao;
	}

	/**
	 * Sets the core persistence object.
	 * @param dao a {@link com.erudika.para.persistence.DAO} instance
	 */
	public void setDao(DAO dao) {
		this.dao = dao;
	}

	/**
	 * Returns the core search object.
	 * @return a {@link com.erudika.para.search.Search} instance
	 */
	@JsonIgnore
	public Search getSearch() {
		if (search == null) {
			search = Para.getSearch();
		}
		return search;
	}

	/**
	 * Sets the core search object.
	 * @param search a {@link com.erudika.para.search.Search} instance
	 */
	public void setSearch(Search search) {
		this.search = search;
	}

	@Override
	public String getPlural() {
		return Utils.singularToPlural(getClassname());
	}

	@Override
	public String getObjectURI() {
		String defurl = "/".concat(getPlural());
		return (getId() != null) ? defurl.concat("/").concat(getId()) : defurl;
	}

	@Override
	public PObject getParent() {
		if (parent == null && parentid != null) {
			parent = getDao().read(getAppname(), parentid);
		}
		return parent;
	}

	@Override
	public PObject getCreator() {
		if (creator == null && creatorid != null) {
			creator = getDao().read(getAppname(), creatorid);
		}
		return creator;
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
	public final Long getUpdated() {
		return (updated != null && updated != 0) ? updated : null;
	}

	@Override
	public final void setUpdated(Long updated) {
		this.updated = updated;
	}

	@Override
	@NotBlank @Size(min = 2, max = 255)
	public final String getName() {
		if (name == null && id != null) {
			name = getClassname().concat(id);
		}
		return name;
	}

	@Override
	public final void setName(String name) {
		if (name == null || !name.isEmpty()) {
			this.name = name;
		}
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public final Long getTimestamp() {
		return (timestamp != null && timestamp != 0) ? timestamp : null;
	}

	@Override
	public final void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}

	@Override
	public final String getCreatorid() {
		return creatorid;
	}

	@Override
	public final void setCreatorid(String creatorid) {
		this.creatorid = creatorid;
	}

	@Override
	public String create() {
		return getDao().create(getAppname(), this);
	}

	@Override
	public void update() {
		getDao().update(getAppname(), this);
	}

	@Override
	public void delete() {
		getDao().delete(getAppname(), this);
	}

	@Override
	public boolean exists() {
		return getDao().existsColumn(getAppname(), id, Config._ID);
	}

	@Override
	public final String getClassname() {
		if (classname == null) {
			classname = Utils.classname(this.getClass());
		}
		return classname;
	}

	@Override
	public final void setClassname(String classname) {
		this.classname = classname;
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

	///////////////////////////////////////
	//	    	TAGGING METHODS
	///////////////////////////////////////

	@Override
	public Set<String> getTags() {
		if (tags == null) {
			tags = new LinkedHashSet<String>();
		}
		return tags;
	}

	@Override
	public void setTags(Set<String> tags) {
		if (tags == null || tags.isEmpty()) {
			this.tags = tags;
		} else {
			addTags(tags.toArray(new String[]{ }));
		}
	}

	/**
	 * Adds any number of tags to the set of tags.
	 * @param tag a tag, must not be null or empty
	 */
	public void addTags(String... tag) {
		if (tag != null && tag.length > 0) {
			for (String t : tag) {
				if (!StringUtils.isBlank(t)) {
					getTags().add(t);
				}
			}
		}
	}

	/**
	 * Removes a tag from the set of tags.
	 * @param tag a tag, must not be null or empty
	 */
	public void removeTags(String... tag) {
		if (tag != null && tag.length > 0) {
			getTags().removeAll(Arrays.asList(tag));
		}
	}

	///////////////////////////////////////
	//			LINKER METHODS
	///////////////////////////////////////

	@Override
	public String link(Class<? extends ParaObject> c2, String id2) {
		return getDao().create(getAppname(), new Linker(this.getClass(), c2, getId(), id2));
	}

	@Override
	public void unlink(Class<? extends ParaObject> c2, String id2) {
		getDao().delete(getAppname(), new Linker(this.getClass(), c2, getId(), id2));
	}

	@Override
	public void unlinkAll() {
		Map<String, Object> terms = new HashMap<String, Object>();
		terms.put("id1", id);
		terms.put("id2", id);
		getDao().deleteAll(getAppname(), getSearch().findTerms(getAppname(), Utils.classname(Linker.class), terms, false));
	}

	@Override
	public ArrayList<Linker> getLinks(Class<? extends ParaObject> c2, Pager... pager) {
		if (c2 == null) {
			return new ArrayList<Linker>();
		}
		Linker link = new Linker(this.getClass(), c2, null, null);
		String idField = link.getIdFieldNameFor(this.getClass());
		Map<String, Object> terms = new HashMap<String, Object>();
		terms.put(Config._NAME, link.getName());
		terms.put(idField, id);
		return getSearch().findTerms(getAppname(), link.getClassname(), terms, true, pager);
	}

	@Override
	public boolean isLinked(Class<? extends ParaObject> c2, String toId) {
		if (c2 == null) {
			return false;
		}
		return getDao().read(getAppname(), new Linker(this.getClass(), c2, getId(), toId).getId()) != null;
	}

	@Override
	public boolean isLinked(ParaObject toObj) {
		if (toObj == null) {
			return false;
		}
		return isLinked(toObj.getClass(), toObj.getId());
	}

	@Override
	public Long countLinks(Class<? extends ParaObject> c2) {
		if (id == null) {
			return 0L;
		}
		Linker link = new Linker(this.getClass(), c2, null, null);
		String idField = link.getIdFieldNameFor(this.getClass());
		Map<String, Object> terms = new HashMap<String, Object>();
		terms.put(Config._NAME, link.getName());
		terms.put(idField, id);
		return getSearch().getCount(getAppname(), link.getClassname(), terms);
	}

	@Override
	public Long countChildren(Class<? extends ParaObject> clazz) {
		return getSearch().getCount(getAppname(), Utils.classname(clazz));
	}

	@Override
	public <P extends ParaObject> ArrayList<P> getChildren(Class<P> clazz, Pager... pager) {
		return getChildren(clazz, null, null, pager);
	}

	@Override
	public <P extends ParaObject> ArrayList<P> getChildren(Class<P> clazz, String field, String term, Pager... pager) {
		Map<String, Object> terms = new HashMap<String, Object>();
		if (StringUtils.isBlank(field) && StringUtils.isBlank(term)) {
			terms.put(field, term);
		}
		terms.put(Config._PARENTID, getId());
		return getSearch().findTerms(getAppname(), Utils.classname(clazz), terms, true, pager);
	}

	@Override
	public void deleteChildren(Class<? extends ParaObject> clazz) {
		if (!StringUtils.isBlank(getId())) {
			getDao().deleteAll(getAppname(), getSearch().findTerms(getAppname(),
					Utils.classname(clazz), Collections.singletonMap(Config._PARENTID, getId()), true));
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> ArrayList<P> getLinkedObjects(Class<P> clazz, Pager... pager) {
		ArrayList<Linker> links = getLinks(clazz, pager);
		ArrayList<String> keys = new ArrayList<String>();
		for (Linker link : links) {
			if (link.isFirst(clazz)) {
				keys.add(link.getId1());
			} else {
				keys.add(link.getId2());
			}
		}
		return new ArrayList<P>((Collection<? extends P>) getDao().readAll(getAppname(), keys, true).values());
	}

	///////////////////////////////////////
	//	    	VOTING METHODS
	///////////////////////////////////////

	private boolean vote(String userid, Votable votable, VoteType upDown) {
		if (StringUtils.isBlank(userid) || votable == null || votable.getId() == null || upDown == null) {
			return false;
		}
		//no voting on your own stuff!
		if (userid.equals(votable.getCreatorid()) || userid.equals(votable.getId())) {
			return false;
		}

		Vote v = new Vote(userid, votable.getId(), upDown.toString());
		Vote saved = getDao().read(getAppname(), v.getId());
		boolean done = false;
		int vote = (upDown == VoteType.UP) ? 1 : -1;

		if (saved != null) {
			boolean isUpvote = upDown.equals(VoteType.UP);
			boolean wasUpvote = VoteType.UP.toString().equals(saved.getType());
			boolean voteHasChanged = BooleanUtils.xor(new boolean[]{isUpvote, wasUpvote});

			if (saved.isExpired()) {
				done = getDao().create(getAppname(), v) != null;
			} else if (saved.isAmendable() && voteHasChanged) {
				getDao().delete(getAppname(), saved);
				done = true;
			}
		} else {
			done = getDao().create(getAppname(), v) != null;
		}

		if (done) {
			synchronized (this) {
				setVotes(getVotes() + vote);
			}
		}

		return done;
	}

	@Override
	public final boolean voteUp(String userid) {
		return vote(userid, this, VoteType.UP);
	}

	@Override
	public final boolean voteDown(String userid) {
		return vote(userid, this, VoteType.DOWN);
	}

	@Override
	public final Integer getVotes() {
		if (votes == null) {
			votes = 0;
		}
		return votes;
	}

	@Override
	public final void setVotes(Integer votes) {
		this.votes = votes;
	}

	//////////////////////////////////////
	//			OBJECT METHODS
	//////////////////////////////////////

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 67 * hash + Objects.hashCode(this.id);
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
		final PObject other = (PObject) obj;
		if (!Objects.equals(this.id, other.id)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return Utils.toJSON(this);
	}
}
