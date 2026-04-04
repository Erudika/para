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

import static com.erudika.para.core.Votable.VoteValue.DOWN;
import static com.erudika.para.core.Votable.VoteValue.UP;
import com.erudika.para.core.annotations.Locked;
import com.erudika.para.core.annotations.Stored;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Pager;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.utils.Utils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.Strings;

/**
 * When a user votes on an object the vote is saved as positive or negative.
 * The user has a short amount of time to amend that vote and then it's locked.
 * Votes can expire after X seconds and they get deleted.
 * This allows the voter to vote again on the same object.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class Vote implements ParaObject {
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
	 * The vote value (UP or DOWN).
	 */
	@Stored @Locked @NotBlank private String value;
	/**
	 * The expiration period in seconds.
	 */
	@Stored @Locked @NotNull private Integer expiresAfter;
	/**
	 * The lock after period in seconds.
	 */
	@Stored @Locked @NotNull private Integer lockedAfter;

	/**
	 * No-args constructor.
	 */
	public Vote() {
		this(null, null, null);
	}

	/**
	 * Default constructor.
	 * @param voterid the user id of the voter
	 * @param voteeid the id of the object that will receive the vote
	 * @param value up + or down -
	 */
	public Vote(String voterid, String voteeid, VoteValue value) {
		creatorid = voterid;
		parentid = voteeid;
		timestamp = Utils.timestamp();
		setName(getType());
		this.value = value != null ? value.toString() : null;
		this.expiresAfter = Para.getConfig().voteExpiresAfterSec();
		this.lockedAfter = Para.getConfig().voteLockedAfterSec();
	}

	@Override
	public final String getId() {
		if (getCreatorid() != null && getParentid() != null && this.id == null) {
			this.id = getType().concat(Para.getConfig().separator()).concat(getCreatorid()).
					concat(Para.getConfig().separator()).concat(getParentid());
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
	 * Returns true if the vote is positive.
	 * @return true if vote is positive
	 */
	public boolean isUpvote() {
		return Strings.CS.equals(this.value, UP.toString());
	}

	/**
	 * Returns true if the vote is negative.
	 * @return true if vote is negative
	 */
	public boolean isDownvote() {
		return Strings.CS.equals(this.value, DOWN.toString());
	}

	/**
	 * Returns the value of the vote.
	 * @return UP or DOWN
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Sets the value of the vote.
	 * @param value UP or DOWN
	 */
	public void setValue(String value) {
		this.value = value;
	}

	/**
	 * Returns the expiration period.
	 * @return time in seconds
	 */
	public Integer getExpiresAfter() {
		if (expiresAfter == null) {
			expiresAfter = Para.getConfig().voteExpiresAfterSec();
		}
		return expiresAfter;
	}

	/**
	 * Sets the expiration period.
	 * @param expiresAfter time in seconds
	 */
	public void setExpiresAfter(Integer expiresAfter) {
		this.expiresAfter = expiresAfter;
	}

	/**
	 * The period during which a vote can be amended.
	 * @return lock after period in seconds
	 */
	public Integer getLockedAfter() {
		if (lockedAfter == null) {
			lockedAfter = Para.getConfig().voteLockedAfterSec();
		}
		return lockedAfter;
	}

	/**
	 * Sets the lock after period.
	 * @param lockedAfter time in seconds
	 */
	public void setLockedAfter(Integer lockedAfter) {
		this.lockedAfter = lockedAfter;
	}

	/**
	 * Checks if expired.
	 * @return true if expired
	 */
	public boolean isExpired() {
		if (getTimestamp() == null || getExpiresAfter() == 0) {
			return false;
		}
		long expires = (getExpiresAfter() * 1000L);
		long now = Utils.timestamp();
		return (getTimestamp() + expires) <= now;
	}

	/**
	 * Checks if vote can still be amended.
	 * @return true if vote can still be changed
	 */
	public boolean isAmendable() {
		if (getTimestamp() == null) {
			return false;
		}
		long now = Utils.timestamp();
		// check timestamp for recent correction,
		return (getTimestamp() + (getLockedAfter() * 1000L)) > now;
	}

	////////////////////////////////////////////////////////

	/**
	 * Sets the object ID.
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
		return Utils.type(this.getClass());
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
	 * @return empty list
	 */
	@Override
	public List<String> getTags() {
		return Collections.emptyList();
	}

	/**
	 * Sets the tags (noop).
	 * @param tags tags
	 */
	@Override
	public void setTags(List<String> tags) {
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
	 * @param stored stored flag
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
	 * @param indexed indexed flag
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
	 * @param cached cached flag
	 */
	@Override
	public void setCached(Boolean cached) {
		this.cached = cached;
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
	 * @param timestamp timestamp
	 */
	@Override
	public void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Returns the creator ID.
	 * @return creatorid
	 */
	@Override
	public String getCreatorid() {
		return creatorid;
	}

	/**
	 * Sets the creator ID.
	 * @param creatorid creatorid
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
	 * @param name name
	 */
	@Override
	public final void setName(String name) {
		this.name = (name == null || !name.isEmpty()) ? name : this.name;
	}

	/**
	 * Returns the plural form of the object type.
	 * @return plural name
	 */
	@Override
	public String getPlural() {
		return Utils.singularToPlural(getType());
	}

	/**
	 * Returns the parent ID.
	 * @return parentid
	 */
	@Override
	public String getParentid() {
		return parentid;
	}

	/**
	 * Sets the parent ID.
	 * @param parentid parentid
	 */
	@Override
	public void setParentid(String parentid) {
		this.parentid = parentid;
	}

	/**
	 * Returns the updated timestamp.
	 * @return updated timestamp
	 */
	@Override
	public Long getUpdated() {
		return (updated != null && updated != 0) ? updated : null;
	}

	/**
	 * Sets the updated timestamp.
	 * @param updated updated timestamp
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
	 * Votes up (noop).
	 * @param userid voter ID
	 * @return false
	 */
	@Override
	public boolean voteUp(String userid) {
		return false;
	}

	/**
	 * Votes down (noop).
	 * @param userid voter ID
	 * @return false
	 */
	@Override
	public boolean voteDown(String userid) {
		return false;
	}

	/**
	 * Returns the number of votes (always 0).
	 * @return 0
	 */
	@Override
	public Integer getVotes() {
		return 0;
	}

	/**
	 * Sets the number of votes (noop).
	 * @param votes votes
	 */
	@Override
	public void setVotes(Integer votes) {
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
	 * @param version version
	 */
	@Override
	public void setVersion(Long version) {
		this.version = version;
	}

	/**
	 * Counts links (always 0).
	 * @param type2 other type
	 * @return 0
	 */
	@Override
	public Long countLinks(String type2) {
		return 0L;
	}

	/**
	 * Returns links (empty list).
	 * @param type2 other type
	 * @param pager pager
	 * @return empty list
	 */
	@Override
	public List<Linker> getLinks(String type2, Pager... pager) {
		return Collections.emptyList();
	}

	/**
	 * Returns linked objects (empty list).
	 * @param <P> type
	 * @param type type
	 * @param pager pager
	 * @return empty list
	 */
	@Override
	public <P extends ParaObject> List<P> getLinkedObjects(String type, Pager... pager) {
		return Collections.emptyList();
	}

	/**
	 * Finds linked objects (empty list).
	 * @param <P> type
	 * @param type type
	 * @param field field
	 * @param query query
	 * @param pager pager
	 * @return empty list
	 */
	@Override
	public <P extends ParaObject> List<P> findLinkedObjects(String type, String field, String query, Pager... pager) {
		return Collections.emptyList();
	}

	/**
	 * Is linked (always false).
	 * @param type2 other type
	 * @param id2 other ID
	 * @return false
	 */
	@Override
	public boolean isLinked(String type2, String id2) {
		return false;
	}

	/**
	 * Is linked (always false).
	 * @param toObj other object
	 * @return false
	 */
	@Override
	public boolean isLinked(ParaObject toObj) {
		return false;
	}

	/**
	 * Links (noop).
	 * @param id2 other ID
	 * @return null
	 */
	@Override
	public String link(String id2) {
		return null;
	}

	/**
	 * Unlinks (noop).
	 * @param type other type
	 * @param id2 other ID
	 */
	@Override
	public void unlink(String type, String id2) {
	}

	/**
	 * Unlinks all (noop).
	 */
	@Override
	public void unlinkAll() {
	}

	/**
	 * Counts children (always 0).
	 * @param type child type
	 * @return 0
	 */
	@Override
	public Long countChildren(String type) {
		return 0L;
	}

	/**
	 * Returns children (empty list).
	 * @param <P> type
	 * @param type type
	 * @param pager pager
	 * @return empty list
	 */
	@Override
	public <P extends ParaObject> List<P> getChildren(String type, Pager... pager) {
		return Collections.emptyList();
	}

	/**
	 * Returns children (empty list).
	 * @param <P> type
	 * @param type type
	 * @param field field
	 * @param term term
	 * @param pager pager
	 * @return empty list
	 */
	@Override
	public <P extends ParaObject> List<P> getChildren(String type, String field, String term, Pager... pager) {
		return Collections.emptyList();
	}

	/**
	 * Finds children (empty list).
	 * @param <P> type
	 * @param type type
	 * @param query query
	 * @param pager pager
	 * @return empty list
	 */
	@Override
	public <P extends ParaObject> List<P> findChildren(String type, String query, Pager... pager) {
		return Collections.emptyList();
	}

	/**
	 * Deletes children (noop).
	 * @param type child type
	 */
	@Override
	public void deleteChildren(String type) {
	}

	/**
	 * Returns hash code.
	 * @return hash code
	 */
	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.value);
	}

	/**
	 * Compares objects for equality.
	 * @param obj other object
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
		final Vote other = (Vote) obj;
		return Objects.equals(this.id, other.getId()) && Objects.equals(this.value, other.getValue());
	}

	/**
	 * Returns JSON string.
	 * @return JSON string
	 */
	@Override
	public String toString() {
		return ParaObjectUtils.toJSON(this);
	}
}
