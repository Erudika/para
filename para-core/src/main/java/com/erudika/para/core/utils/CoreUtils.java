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
package com.erudika.para.core.utils;

import com.erudika.para.InitializeListener;
import com.erudika.para.cache.Cache;
import com.erudika.para.cache.MockCache;
import com.erudika.para.core.Linker;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Votable;
import com.erudika.para.core.Votable.VoteValue;
import com.erudika.para.core.Vote;
import com.erudika.para.iot.IoTServiceFactory;
import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.MockDAO;
import com.erudika.para.queue.MockQueue;
import com.erudika.para.queue.Queue;
import com.erudika.para.search.MockSearch;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides some the basic functionality for domain objects.
 * @author Alex Bogdanovski [alex@erudika.com]
 * @see ParaObject
 */
public enum CoreUtils implements InitializeListener {

	/**
	 * Singleton.
	 */
	INSTANCE {
		private final transient Logger logger = LoggerFactory.getLogger(CoreUtils.class);

		private transient DAO dao;
		private transient Search search;
		private transient Cache cache;
		private transient Queue queue;
		private transient IoTServiceFactory iotFactory;

		{
			dao = new MockDAO();
			search = new MockSearch();
			cache = new MockCache();
			queue = new MockQueue();
			logger.debug("Using default impementations - {}, {} and {}.",
					dao.getClass().getSimpleName(),
					search.getClass().getSimpleName(),
					cache.getClass().getSimpleName());
		}

		@Override
		public void onInitialize() {
			// switch to the real DAO, Search and Cache implementations at runtime
			if (dao != null && search != null && cache != null) {
				logger.info("Loaded new DAO, Search and Cache implementations - {}, {} and {}.",
						stripGuiceMarkerFromClassname(dao.getClass()),
						stripGuiceMarkerFromClassname(search.getClass()),
						stripGuiceMarkerFromClassname(cache.getClass()));
				setIotFactory(iotFactory);
			}
		}

		private String stripGuiceMarkerFromClassname(Class<?> clazz) {
			if (clazz.getSimpleName().contains("$EnhancerByGuice$")) {
				return clazz.getSuperclass().getSimpleName();
			}
			return clazz.getSimpleName();
		}

		@Override
		public DAO getDao() {
			return dao;
		}

		@Inject
		@Override
		public void setDao(DAO dao) {
			this.dao = dao;
		}

		@Override
		public Search getSearch() {
			return search;
		}

		@Inject
		@Override
		public void setSearch(Search search) {
			this.search = search;
		}

		@Override
		public Cache getCache() {
			return cache;
		}

		@Inject
		@Override
		public void setCache(Cache cache) {
			this.cache = cache;
		}

		@Override
		public Queue getQueue() {
			return queue;
		}

		@Inject
		@Override
		public void setQueue(Queue queue) {
			this.queue = queue;
		}

		@Override
		public IoTServiceFactory getIotFactory() {
			return iotFactory;
		}

		@Inject
		@Override
		public void setIotFactory(IoTServiceFactory iotFactory) {
			this.iotFactory = iotFactory;
		}

		@Override
		public String getObjectURI(ParaObject obj) {
			return StringUtils.isBlank(obj.getId()) ?
					"/".concat(Utils.urlEncode(obj.getType())) :
					"/".concat(Utils.urlEncode(obj.getType())).concat("/").concat(Utils.urlEncode(obj.getId()));
		}

		@Override
		public String getName(String name, String id) {
			return (name == null) ? "ParaObject ".concat((id == null) ?
					Long.toString(System.currentTimeMillis()) : id) : name;
		}

		@Override
		public String overwrite(ParaObject obj) {
			return overwrite(Config.getRootAppIdentifier(), obj);
		}

		@Override
		public String overwrite(String appid, ParaObject obj) {
			if (obj != null && obj.getId() != null) {
				if (obj.getUpdated() == null) {
					obj.setUpdated(System.currentTimeMillis());
				}
				return getDao().create(appid, obj);
			}
			return null;
		}

		///////////////////////////////////////
		//	    	TAGGING METHODS
		///////////////////////////////////////

		@Override
		public List<String> addTags(List<String> objectTags, String... tag) {
			if (tag != null && tag.length > 0) {
				Set<String> tagz;
				if (objectTags == null || objectTags.isEmpty()) {
					tagz = new HashSet<>();
				} else {
					tagz = new HashSet<>(objectTags);
				}
				for (String t : tag) {
					if (!StringUtils.isBlank(t)) {
						tagz.add(Utils.noSpaces(Utils.stripAndTrim(t), "-"));
					}
				}
				tagz.remove(null);
				tagz.remove("");
				return new ArrayList<>(tagz);
			}
			return objectTags;
		}

		@Override
		public List<String> removeTags(List<String> objectTags, String... tag) {
			if (objectTags != null && tag != null && tag.length > 0) {
				Set<String> tagz = new HashSet<>(objectTags);
				tagz.removeAll(Arrays.asList(tag));
				return new ArrayList<>(tagz);
			}
			return objectTags;
		}

		///////////////////////////////////////
		//			LINKER METHODS
		///////////////////////////////////////

		@Override
		public String link(ParaObject obj, String id2) {
			ParaObject second = getDao().read(obj.getAppid(), id2);
			if (second == null || obj.getId() == null) {
				return null;
			}
			// auto correct the second type
			Linker link = new Linker(obj.getType(), second.getType(), obj.getId(), id2);
			link.addNestedObject(obj);
			link.addNestedObject(second);
			return getDao().create(obj.getAppid(), link);
		}

		@Override
		public void unlink(ParaObject obj, String type2, String id2) {
			getDao().delete(obj.getAppid(), new Linker(obj.getType(), type2, obj.getId(), id2));
		}

		@Override
		public void unlinkAll(ParaObject obj) {
			Map<String, Object> terms = new HashMap<>();
			// delete all links where id1 == id OR id2 == id
			terms.put("id1", obj.getId());
			terms.put("id2", obj.getId());
			getDao().deleteAll(obj.getAppid(), getSearch().
					findTerms(obj.getAppid(), Utils.type(Linker.class), terms, false));
		}

		@Override
		public List<Linker> getLinks(ParaObject obj, String type2, Pager... pager) {
			if (type2 == null) {
				return Collections.emptyList();
			}
			Linker link = new Linker(obj.getType(), type2, null, null);
			String idField = link.getIdFieldNameFor(obj.getType());
			Map<String, Object> terms = new HashMap<>();
			terms.put(Config._NAME, link.getName());
			terms.put(idField, obj.getId());
			return getSearch().findTerms(obj.getAppid(), link.getType(), terms, true, pager);
		}

		@Override
		public boolean isLinked(ParaObject obj, String type2, String id2) {
			if (type2 == null) {
				return false;
			}
			return getDao().read(obj.getAppid(), new Linker(obj.getType(), type2, obj.getId(), id2).getId()) != null;
		}

		@Override
		public boolean isLinked(ParaObject obj, ParaObject toObj) {
			if (toObj == null) {
				return false;
			}
			return isLinked(obj, toObj.getType(), toObj.getId());
		}

		@Override
		public Long countLinks(ParaObject obj, String type2) {
			if (obj.getId() == null) {
				return 0L;
			}
			Linker link = new Linker(obj.getType(), type2, null, null);
			String idField = link.getIdFieldNameFor(obj.getType());
			Map<String, Object> terms = new HashMap<>();
			terms.put(Config._NAME, link.getName());
			terms.put(idField, obj.getId());
			return getSearch().getCount(obj.getAppid(), link.getType(), terms);
		}

		@Override
		public Long countChildren(ParaObject obj, String type2) {
			return getSearch().getCount(obj.getAppid(), type2);
		}

		@Override
		public <P extends ParaObject> List<P> getChildren(ParaObject obj, String type2, Pager... pager) {
			return getChildren(obj, type2, null, null, pager);
		}

		@Override
		public <P extends ParaObject> List<P> getChildren(ParaObject obj, String type2, String field, String term,
				Pager... pager) {
			Map<String, Object> terms = new HashMap<>();
			if (!StringUtils.isBlank(field) && !StringUtils.isBlank(term)) {
				terms.put(field, term);
			}
			terms.put(Config._PARENTID, obj.getId());
			return getSearch().findTerms(obj.getAppid(), type2, terms, true, pager);
		}

		@Override
		public <P extends ParaObject> List<P> findChildren(ParaObject obj, String type2, String query, Pager... pager) {
			if (StringUtils.isBlank(query)) {
				query = "*";
			}
			String suffix = "*".equals(query.trim()) ? "" : " AND " + query;
			query = Config._PARENTID + ":" + obj.getId() + suffix;
			return getSearch().findQuery(obj.getAppid(), type2, query, pager);
		}

		@Override
		public void deleteChildren(ParaObject obj, String type2) {
			if (!StringUtils.isBlank(obj.getId())) {
				getDao().deleteAll(obj.getAppid(), getSearch().findTerms(obj.getAppid(),
						type2, Collections.singletonMap(Config._PARENTID, obj.getId()), true));
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		public <P extends ParaObject> List<P> getLinkedObjects(ParaObject obj, String type2, Pager... pager) {
			List<Linker> links = getLinks(obj, type2, pager);
			LinkedList<String> keys = new LinkedList<>();
			for (Linker link : links) {
				keys.add(link.isFirst(type2) ? link.getId1() : link.getId2());
			}
			return new ArrayList<>((Collection<? extends P>) getDao().readAll(obj.getAppid(), keys, true).values());
		}

		@SuppressWarnings("unchecked")
		@Override
		public <P extends ParaObject> List<P> findLinkedObjects(ParaObject obj, String type2, String field, String query,
				Pager... pager) {
			if (StringUtils.isBlank(query)) {
				query = "*";
			}
			List<Linker> links = getSearch().findNestedQuery(obj.getAppid(), Utils.type(Linker.class), field, query, pager);
			LinkedList<String> keys = new LinkedList<>();
			for (Linker link : links) {
				// ignore the part of the link that is equal to the given object
				// e.g. (NOT id:$obj.getId()) AND $query
				if (link.getId1().equals(obj.getId())) {
					keys.add(link.getId2());
				} else {
					keys.add(link.getId1());
				}
			}
			return new ArrayList<>((Collection<? extends P>) getDao().readAll(obj.getAppid(), keys, true).values());
		}

		@Override
		public <P extends ParaObject> P getParent(ParaObject obj) {
			return getDao().read(obj.getAppid(), obj.getParentid());
		}

		@Override
		public <P extends ParaObject> P getCreator(ParaObject obj) {
			return getDao().read(obj.getAppid(), obj.getCreatorid());
		}

		///////////////////////////////////////
		//	    	VOTING METHODS
		///////////////////////////////////////

		@Override
		public boolean vote(ParaObject votable, String userid, VoteValue upDown) {
			return vote(votable, userid, upDown, null, null);
		}

		@Override
		public boolean vote(ParaObject votable, String userid, VoteValue upDown, Integer expiresAfter, Integer lockedAfter) {
			if (StringUtils.isBlank(userid) || votable == null || votable.getId() == null || upDown == null) {
				return false;
			}
			//no voting on your own stuff!
			if (userid.equals(votable.getCreatorid()) || userid.equals(votable.getId())) {
				return false;
			}

			Vote v = new Vote(userid, votable.getId(), upDown);
			if (expiresAfter != null) {
				v.setExpiresAfter(expiresAfter);
			}
			if (lockedAfter != null) {
				v.setLockedAfter(lockedAfter);
			}
			Vote saved = getDao().read(votable.getAppid(), v.getId());
			boolean done = false;

			if (saved != null) {
				boolean isUpvote = upDown.equals(VoteValue.UP);
				boolean wasUpvote = saved.isUpvote();
				boolean voteHasChanged = isUpvote ^ wasUpvote;

				if (saved.isExpired()) {
					done = getDao().create(votable.getAppid(), v) != null;
				} else if (saved.isAmendable() && voteHasChanged) {
					getDao().delete(votable.getAppid(), saved);
					done = true;
				}
			} else {
				done = getDao().create(votable.getAppid(), v) != null;
			}

			if (done) {
				votable.setVotes(votable.getVotes() + upDown.getValue());
			}
			return done;
		}
	};

	/**
	 * Provides a default instance using fake DAO, Search and Cache implementations.
	 * @return an instance of this class
	 */
	public static synchronized CoreUtils getInstance() {
		return INSTANCE;
	}

	/**
	 * Returns the DAO object.
	 * @return a {@link DAO} object
	 */
	public abstract DAO getDao();

	/**
	 * Sets the DAO object.
	 * @param dao {@link DAO}
	 */
	public abstract void setDao(DAO dao);

	/**
	 * Returns the Search object.
	 * @return {@link Search} object
	 */
	public abstract Search getSearch();

	/**
	 * Sets the Search object.
	 * @param search {@link Search}
	 */
	public abstract void setSearch(Search search);

	/**
	 * Returns the Cache object.
	 * @return {@link Cache} object
	 */
	public abstract Cache getCache();

	/**
	 * Sets the Cache object.
	 * @param cache {@link Cache}
	 */
	public abstract void setCache(Cache cache);

	/**
	 * Returns the Queue object.
	 * @return {@link Queue} object
	 */
	public abstract Queue getQueue();

	/**
	 * Sets the Queue object.
	 * @param queue {@link Queue}
	 */
	public abstract void setQueue(Queue queue);

	/**
	 * Returns the default IoT factory.
	 * @return factory instance
	 */
	public abstract IoTServiceFactory getIotFactory();

	/**
	 * Sets the IoT factory.
	 * @param iotFactory factory instance
	 */
	public abstract void setIotFactory(IoTServiceFactory iotFactory);

	///////////////////////////////////////
	//	    	TAGGING METHODS
	///////////////////////////////////////

	/**
	 * Adds any number of tags to the set of tags.
	 *
	 * @param tag a tag, must not be null or empty
	 * @param objectTags the object tags
	 * @return a new list of tags
	 */
	public abstract List<String> addTags(List<String> objectTags, String... tag);

	/**
	 * Removes a tag from the set of tags.
	 *
	 * @param tag a tag, must not be null or empty
	 * @param objectTags the object
	 * @return a new list of tags
	 */
	public abstract List<String> removeTags(List<String> objectTags, String... tag);

	///////////////////////////////////////
	//			LINKER METHODS
	///////////////////////////////////////

	/**
	 * Count the total number of child objects for this object.
	 *
	 * @param type2 the type of the other object
	 * @param obj the object to execute this method on
	 * @return the number of links
	 */
	public abstract Long countChildren(ParaObject obj, String type2);

	/**
	 * Count the total number of links between this object and another type of object.
	 *
	 * @param type2 the other type of object
	 * @param obj the object to execute this method on
	 * @return the number of links for the given object
	 */
	public abstract Long countLinks(ParaObject obj, String type2);

	/**
	 * Deletes all child objects permanently.
	 *
	 * @param obj the object to execute this method on
	 * @param type2 the children's type.
	 */
	public abstract void deleteChildren(ParaObject obj, String type2);

	/**
	 * Searches through child objects in a one-to-many relationship.
	 *
	 * @param <P> the type of children
	 * @param type2 the type of children to look for
	 * @param obj the object to execute this method on
	 * @param query a query string
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of {@link ParaObject} in a one-to-many relationship with this object
	 */
	public abstract <P extends ParaObject> List<P> findChildren(ParaObject obj, String type2, String query, Pager... pager);

	/**
	 * Searches through all linked objects in many-to-many relationships.
	 *
	 * @param <P> type of linked objects
	 * @param type2 type of linked objects to search for
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @param field the name of the field to target (within a nested field "nstd")
	 * @param query a query string
	 * @return a list of linked objects matching the search query
	 */
	public abstract <P extends ParaObject> List<P> findLinkedObjects(ParaObject obj, String type2, String field,
			String query, Pager... pager);

	/**
	 * Returns all child objects linked to this object.
	 *
	 * @param <P> the type of children
	 * @param type2 the type of children to look for
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of {@link ParaObject} in a one-to-many relationship with this object
	 */
	public abstract <P extends ParaObject> List<P> getChildren(ParaObject obj, String type2, Pager... pager);

	/**
	 * Returns all child objects linked to this object.
	 *
	 * @param <P> the type of children
	 * @param type2 the type of children to look for
	 * @param field the field name to use as filter
	 * @param term the field value to use as filter
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of {@link ParaObject} in a one-to-many relationship with this object
	 */
	public abstract <P extends ParaObject> List<P> getChildren(ParaObject obj, String type2, String field,
			String term, Pager... pager);

	/**
	 * The user object of the creator.
	 *
	 * @param <P> type of linked objects
	 * @param obj find the creator of this object
	 * @return the user who created this or null if {@code obj.getCreatorid()} is null
	 * @see com.erudika.para.core.User
	 */
	public abstract <P extends ParaObject> P getCreator(ParaObject obj);

	/**
	 * Returns all objects linked to the given one. Only applicable to many-to-many relationships.
	 *
	 * @param <P> type of linked objects
	 * @param type2 type of linked objects to search for
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of linked objects
	 */
	public abstract <P extends ParaObject> List<P> getLinkedObjects(ParaObject obj, String type2, Pager... pager);

	/**
	 * Returns a list of all Linker objects for a given object.
	 *
	 * @param obj the object to execute this method on
	 * @param type2 the other type
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of Linker objects
	 */
	public abstract List<Linker> getLinks(ParaObject obj, String type2, Pager... pager);

	/**
	 * Returns the default name property of an object.
	 *
	 * @param name a name
	 * @param id an id
	 * @return a combination of name and id, unless this.name is set
	 */
	public abstract String getName(String name, String id);

	/**
	 * Returns the relative path to the object, e.g. /user/1234
	 *
	 * @param obj an object
	 * @return a relative path
	 */
	public abstract String getObjectURI(ParaObject obj);

	/**
	 * The parent object.
	 *
	 * @param <P> type of linked objects
	 * @param obj find the parent of this object
	 * @return the parent or null if {@code obj.getParentid()} is null
	 */
	public abstract <P extends ParaObject> P getParent(ParaObject obj);

	/**
	 * Checks if this object is linked to another.
	 *
	 * @param type2 the other type
	 * @param id2 the other id
	 * @param obj the object to execute this method on
	 * @return true if the two are linked
	 */
	public abstract boolean isLinked(ParaObject obj, String type2, String id2);

	/**
	 * Checks if a given object is linked to this one.
	 *
	 * @param toObj the other object
	 * @param obj the object to execute this method on
	 * @return true if linked
	 */
	public abstract boolean isLinked(ParaObject obj, ParaObject toObj);

	/**
	 * Links an object to this one in a many-to-many relationship. Only a link is created. Objects are left untouched.
	 * The type of the second object is automatically determined on read.
	 *
	 * @param id2 link to the object with this id
	 * @param obj the object to execute this method on
	 * @return the id of the {@link com.erudika.para.core.Linker} object that is created
	 */
	public abstract String link(ParaObject obj, String id2);

	/**
	 * Creates the object again (use with caution!). Same as
	 * {@link com.erudika.para.persistence.DAO#create(com.erudika.para.core.ParaObject)}.
	 *
	 * @param obj an object
	 * @return the object id or null
	 */
	public abstract String overwrite(ParaObject obj);

	/**
	 * Creates the object again (use with caution!). Same as
	 * {@link com.erudika.para.persistence.DAO#create(java.lang.String, com.erudika.para.core.ParaObject)}.
	 *
	 * @param appid the app id
	 * @param obj an object
	 * @return the object id or null
	 */
	public abstract String overwrite(String appid, ParaObject obj);

	/**
	 * Unlinks an object from this one. Only a link is deleted. Objects are left untouched.
	 *
	 * @param type2 the other type
	 * @param obj the object to execute this method on
	 * @param id2 the other id
	 */
	public abstract void unlink(ParaObject obj, String type2, String id2);

	/**
	 * Unlinks all objects that are linked to this one. Deletes all {@link com.erudika.para.core.Linker} objects. Only
	 * the links are deleted. Objects are left untouched.
	 *
	 * @param obj the object to execute this method on
	 */
	public abstract void unlinkAll(ParaObject obj);

	///////////////////////////////////////
	//	    	VOTING METHODS
	///////////////////////////////////////

	/**
	 * Casts a vote on a given object.
	 *
	 * @param votable the object to vote on
	 * @param userid the voter
	 * @param upDown up or down
	 * @return true if the vote was successful
	 */
	public abstract boolean vote(ParaObject votable, String userid, Votable.VoteValue upDown);

	/**
	 * Casts a vote on a given object.
	 *
	 * @param votable the object to vote on
	 * @param userid the voter
	 * @param upDown up or down
	 * @param expiresAfter expires after seconds
	 * @param lockedAfter locked after seconds
	 * @return true if the vote was successful
	 */
	public abstract boolean vote(ParaObject votable, String userid, Votable.VoteValue upDown,
			Integer expiresAfter, Integer lockedAfter);
}
