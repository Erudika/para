/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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
import static com.erudika.para.core.Votable.VoteValue.*;
import com.erudika.para.persistence.DAO;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;

/**
 * When a user votes on an object the vote is saved as positive or negative.
 * The user has a short amount of time to amend that vote and then it's locked.
 * Votes can expire after X seconds and they get deleted.
 * This allows the voter to vote again on the same object.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class Vote implements ParaObject {
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

	@Stored @NotBlank private String value;
	@Stored @NotNull private Long expiresAfter;

	private transient DAO dao;
	private transient Search search;

	/**
	 * No-args constructor
	 */
	public Vote() {
		this(null, null, null);
	}

	/**
	 * Default constructor
	 * @param voterid the user id of the voter
	 * @param voteeid the id of the object that will receive the vote
	 * @param value up + or down -
	 */
	public Vote(String voterid, String voteeid, String value) {
		creatorid = voterid;
		parentid = voteeid;
		timestamp = Utils.timestamp();
		setName(getType());
		this.value = value;
		this.expiresAfter = Config.VOTE_EXPIRES_AFTER_SEC;
	}

	@Override
	public final String getId() {
		if (getCreatorid() != null && getParentid() != null && this.id == null) {
			this.id = getType().concat(Config.SEPARATOR).concat(getCreatorid()).
					concat(Config.SEPARATOR).concat(getParentid());
		}
		return this.id;
	}

	/**
	 * Set the vote positive.
	 * @return this
	 */
	public Vote up() {
		this.value = UP.toString();
		return this;
	}

	/**
	 * Set the vote negative.
	 * @return this
	 */
	public Vote down() {
		this.value = DOWN.toString();
		return this;
	}

	/**
	 * Returns the value of the vote.
	 * @return UP or DOWN
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Sets the value of the vote
	 * @param value UP or DOWN
	 */
	public void setValue(String value) {
		this.value = value;
	}

	/**
	 * Returns the expiration period
	 * @return time in seconds
	 */
	public Long getExpiresAfter() {
		if (expiresAfter == null) {
			expiresAfter = Config.VOTE_EXPIRES_AFTER_SEC;
		}
		return expiresAfter;
	}

	/**
	 * Sets the expiration period
	 * @param expiresAfter time in seconds
	 */
	public void setExpiresAfter(Long expiresAfter) {
		this.expiresAfter = expiresAfter;
	}

	/**
	 * Checks if expired
	 * @return true if expired
	 */
	public boolean isExpired() {
		if (getTimestamp() == null || getExpiresAfter() == 0) {
			return false;
		}
		long timestamp = getTimestamp();
		long expires = (getExpiresAfter() * 1000);
		long now = Utils.timestamp();
		return (timestamp + expires) <= now;
	}

	/**
	 * Checks if vote can still be amended.
	 * @return true if vote can still be changed
	 */
	public boolean isAmendable() {
		if (getTimestamp() == null) {
			return false;
		}
		long timestamp = getTimestamp();
		long now = Utils.timestamp();
		// check timestamp for recent correction,
		return (timestamp + (Config.VOTE_LOCKED_AFTER_SEC * 1000)) > now;
	}

	////////////////////////////////////////////////////////

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
		return ParaObjectUtils.toJSON(this);
	}
}
