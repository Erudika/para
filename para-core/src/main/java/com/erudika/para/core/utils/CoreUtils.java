/*
 * Copyright 2013-2016 Erudika. http://erudika.com
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
import com.erudika.para.core.Votable.VoteValue;
import com.erudika.para.core.Vote;
import com.erudika.para.iot.IoTServiceFactory;
import com.erudika.para.persistence.DAO;
import com.erudika.para.persistence.MockDAO;
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
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides some the basic functionality for domain objects.
 * @author Alex Bogdanovski [alex@erudika.com]
 * @see ParaObject
 */
@Singleton
public final class CoreUtils implements InitializeListener {

	private static final Logger logger = LoggerFactory.getLogger(CoreUtils.class);

	@Inject private DAO dao;
	@Inject private Search search;
	@Inject private Cache cache;
	@Inject private IoTServiceFactory iotFactory;

	private static CoreUtils instance;

	/**
	 * Default constructor.
	 * @param dao a {@link com.erudika.para.persistence.DAO} object
	 * @param search a {@link com.erudika.para.search.Search} object
	 * @param cache a {@link com.erudika.para.cache.Cache} object
	 */
	public CoreUtils(DAO dao, Search search, Cache cache) {
		this.dao = dao;
		this.search = search;
		this.cache = cache;
	}

	@Override
	public void onInitialize() {
		if (dao != null && search != null && cache != null) {
			logger.info("Loaded new DAO, Search and Cache implementations - {}, {} and {}.",
					dao.getClass().getSimpleName(),
					search.getClass().getSimpleName(),
					cache.getClass().getSimpleName());
			CoreUtils.instance = new CoreUtils(dao, search, cache);
			CoreUtils.instance.setIotFactory(iotFactory);
		}
	}

	/**
	 * Provides a default instance using fake DAO and Search implementations.
	 * @return an instance of this class
	 */
	public static synchronized CoreUtils getInstance() {
		if (instance == null) {
			DAO defaultDAO = new MockDAO();
			Search defaultSearch = new MockSearch();
			Cache defaultCache = new MockCache();
			logger.debug("Using default impementations - {}, {} and {}.",
					defaultDAO.getClass().getSimpleName(),
					defaultSearch.getClass().getSimpleName(),
					defaultCache.getClass().getSimpleName());
			instance = new CoreUtils(defaultDAO, defaultSearch, defaultCache);
		}
		return instance;
	}

	public DAO getDao() {
		return dao;
	}

	public void setDao(DAO dao) {
		this.dao = dao;
	}

	public Search getSearch() {
		return search;
	}

	public void setSearch(Search search) {
		this.search = search;
	}

	public Cache getCache() {
		return cache;
	}

	public void setCache(Cache cache) {
		this.cache = cache;
	}

	public IoTServiceFactory getIotFactory() {
		return iotFactory;
	}

	public void setIotFactory(IoTServiceFactory iotFactory) {
		this.iotFactory = iotFactory;
	}

	public String getObjectURI(ParaObject obj) {
		String defurl = "/".concat(obj.getPlural());
		return (obj.getId() != null) ? defurl.concat("/").concat(obj.getId()) : defurl;
	}

	public String getName(String name, String id) {
		return (name == null) ? "ParaObject ".concat((id == null) ? System.currentTimeMillis() + "" : id) : name;
	}

	/**
	 * Creates the object again (use with caution!).
	 * Same as {@link com.erudika.para.persistence.DAO#create(com.erudika.para.core.ParaObject)}.
	 * @param obj an object
	 * @return the object id or null
	 */
	public String overwrite(ParaObject obj) {
		return overwrite(Config.APP_NAME_NS, obj);
	}

	/**
	 * Creates the object again (use with caution!).
	 * Same as {@link com.erudika.para.persistence.DAO#create(java.lang.String, com.erudika.para.core.ParaObject)}.
	 * @param appid the app id
	 * @param obj an object
	 * @return the object id or null
	 */
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

	/**
	 * Adds any number of tags to the set of tags.
	 * @param tag a tag, must not be null or empty
	 * @param objectTags the object tags
	 * @return a new list of tags
	 */
	public List<String> addTags(List<String> objectTags, String... tag) {
		if (tag != null && tag.length > 0) {
			Set<String> tagz;
			if (objectTags == null || objectTags.isEmpty()) {
				tagz = new HashSet<String>();
			} else {
				tagz = new HashSet<String>(objectTags);
			}
			for (String t : tag) {
				if (!StringUtils.isBlank(t)) {
					tagz.add(Utils.noSpaces(Utils.stripAndTrim(t), "-"));
				}
			}
			tagz.remove(null);
			tagz.remove("");
			return new ArrayList<String>(tagz);
		}
		return objectTags;
	}

	/**
	 * Removes a tag from the set of tags.
	 * @param tag a tag, must not be null or empty
	 * @param objectTags the object
	 * @return a new list of tags
	 */
	public List<String> removeTags(List<String> objectTags, String... tag) {
		if (objectTags != null && tag != null && tag.length > 0) {
			Set<String> tagz = new HashSet<String>(objectTags);
			tagz.removeAll(Arrays.asList(tag));
			return new ArrayList<String>(tagz);
		}
		return objectTags;
	}

	///////////////////////////////////////
	//			LINKER METHODS
	///////////////////////////////////////

	/**
	 * Links an object to this one in a many-to-many relationship.
	 * Only a link is created. Objects are left untouched.
	 * The type of the second object is automatically determined on read.
	 * @param id2 link to the object with this id
	 * @param obj the object to execute this method on
	 * @return the id of the {@link com.erudika.para.core.Linker} object that is created
	 */
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

	/**
	 * Unlinks an object from this one.
	 * Only a link is deleted. Objects are left untouched.
	 * @param type2 the other type
	 * @param obj the object to execute this method on
	 * @param id2 the other id
	 */
	public void unlink(ParaObject obj, String type2, String id2) {
		getDao().delete(obj.getAppid(), new Linker(obj.getType(), type2, obj.getId(), id2));
	}

	/**
	 * Unlinks all objects that are linked to this one.
	 * Deletes all {@link com.erudika.para.core.Linker} objects.
	 * Only the links are deleted. Objects are left untouched.
	 * @param obj the object to execute this method on
	 */
	public void unlinkAll(ParaObject obj) {
		Map<String, Object> terms = new HashMap<String, Object>();
		// delete all links where id1 == id OR id2 == id
		terms.put("id1", obj.getId());
		terms.put("id2", obj.getId());
		getDao().deleteAll(obj.getAppid(), getSearch().
				findTerms(obj.getAppid(), Utils.type(Linker.class), terms, false));
	}

	/**
	 * Returns a list of all Linker objects for a given object.
	 * @param obj the object to execute this method on
	 * @param type2 the other type
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of Linker objects
	 */
	public List<Linker> getLinks(ParaObject obj, String type2, Pager... pager) {
		if (type2 == null) {
			return Collections.emptyList();
		}
		Linker link = new Linker(obj.getType(), type2, null, null);
		String idField = link.getIdFieldNameFor(obj.getType());
		Map<String, Object> terms = new HashMap<String, Object>();
		terms.put(Config._NAME, link.getName());
		terms.put(idField, obj.getId());
		return getSearch().findTerms(obj.getAppid(), link.getType(), terms, true, pager);
	}

	/**
	 * Checks if this object is linked to another.
	 * @param type2 the other type
	 * @param id2 the other id
	 * @param obj the object to execute this method on
	 * @return true if the two are linked
	 */
	public boolean isLinked(ParaObject obj, String type2, String id2) {
		if (type2 == null) {
			return false;
		}
		return getDao().read(obj.getAppid(), new Linker(obj.getType(), type2, obj.getId(), id2).getId()) != null;
	}

	/**
	 * Checks if a given object is linked to this one.
	 * @param toObj the other object
	 * @param obj the object to execute this method on
	 * @return true if linked
	 */
	public boolean isLinked(ParaObject obj, ParaObject toObj) {
		if (toObj == null) {
			return false;
		}
		return isLinked(obj, toObj.getType(), toObj.getId());
	}

	/**
	 * Count the total number of links between this object and another type of object.
	 * @param type2 the other type of object
	 * @param obj the object to execute this method on
	 * @return the number of links for the given object
	 */
	public Long countLinks(ParaObject obj, String type2) {
		if (obj.getId() == null) {
			return 0L;
		}
		Linker link = new Linker(obj.getType(), type2, null, null);
		String idField = link.getIdFieldNameFor(obj.getType());
		Map<String, Object> terms = new HashMap<String, Object>();
		terms.put(Config._NAME, link.getName());
		terms.put(idField, obj.getId());
		return getSearch().getCount(obj.getAppid(), link.getType(), terms);
	}

	/**
	 * Count the total number of child objects for this object.
	 * @param type2 the type of the other object
	 * @param obj the object to execute this method on
	 * @return the number of links
	 */
	public Long countChildren(ParaObject obj, String type2) {
		return getSearch().getCount(obj.getAppid(), type2);
	}

	/**
	 * Returns all child objects linked to this object.
	 * @param <P> the type of children
	 * @param type2 the type of children to look for
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of {@link ParaObject} in a one-to-many relationship with this object
	 */
	public <P extends ParaObject> List<P> getChildren(ParaObject obj, String type2, Pager... pager) {
		return getChildren(obj, type2, null, null, pager);
	}

	/**
	 * Returns all child objects linked to this object.
	 * @param <P> the type of children
	 * @param type2 the type of children to look for
	 * @param field the field name to use as filter
	 * @param term the field value to use as filter
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of {@link ParaObject} in a one-to-many relationship with this object
	 */
	public <P extends ParaObject> List<P> getChildren(ParaObject obj, String type2, String field, String term,
			Pager... pager) {
		Map<String, Object> terms = new HashMap<String, Object>();
		if (!StringUtils.isBlank(field) && !StringUtils.isBlank(term)) {
			terms.put(field, term);
		}
		terms.put(Config._PARENTID, obj.getId());
		return getSearch().findTerms(obj.getAppid(), type2, terms, true, pager);
	}

	/**
	 * Searches through child objects in a one-to-many relationship.
	 * @param <P> the type of children
	 * @param type2 the type of children to look for
	 * @param obj the object to execute this method on
	 * @param query a query string
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of {@link ParaObject} in a one-to-many relationship with this object
	 */
	public <P extends ParaObject> List<P> findChildren(ParaObject obj, String type2, String query, Pager... pager) {
		if (StringUtils.isBlank(query)) {
			query = "*";
		}
		query = Config._PARENTID + ":" + obj.getId() + " AND " + query;
		return getSearch().findQuery(obj.getAppid(), type2, query, pager);
	}

	/**
	 * Deletes all child objects permanently.
	 * @param obj the object to execute this method on
	 * @param type2 the children's type.
	 */
	public void deleteChildren(ParaObject obj, String type2) {
		if (!StringUtils.isBlank(obj.getId())) {
			getDao().deleteAll(obj.getAppid(), getSearch().findTerms(obj.getAppid(),
					type2, Collections.singletonMap(Config._PARENTID, obj.getId()), true));
		}
	}

	/**
	 * Returns all objects linked to the given one. Only applicable to many-to-many relationships.
	 * @param <P> type of linked objects
	 * @param type2 type of linked objects to search for
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of linked objects
	 */
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> getLinkedObjects(ParaObject obj, String type2, Pager... pager) {
		List<Linker> links = getLinks(obj, type2, pager);
		LinkedList<String> keys = new LinkedList<String>();
		for (Linker link : links) {
			keys.add(link.isFirst(type2) ? link.getId1() : link.getId2());
		}
		return new ArrayList<P>((Collection<? extends P>) getDao().readAll(obj.getAppid(), keys, true).values());
	}

	/**
	 * Searches through all linked objects in many-to-many relationships.
	 * @param <P> type of linked objects
	 * @param type2 type of linked objects to search for
	 * @param obj the object to execute this method on
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @param field the name of the field to target (within a nested field "nstd")
	 * @param query a query string
	 * @return a list of linked objects matching the search query
	 */
	@SuppressWarnings("unchecked")
	public <P extends ParaObject> List<P> findLinkedObjects(ParaObject obj, String type2, String field, String query,
			Pager... pager) {
		if (StringUtils.isBlank(query)) {
			query = "*";
		}
		List<Linker> links = getSearch().findNestedQuery(obj.getAppid(), Utils.type(Linker.class), field, query, pager);
		LinkedList<String> keys = new LinkedList<String>();
		for (Linker link : links) {
			// ignore the part of the link that is equal to the given object
			// e.g. (NOT id:$obj.getId()) AND $query
			if (link.getId1().equals(obj.getId())) {
				keys.add(link.getId2());
			} else {
				keys.add(link.getId1());
			}
		}
		return new ArrayList<P>((Collection<? extends P>) getDao().readAll(obj.getAppid(), keys, true).values());
	}

	/**
	 * The parent object.
	 * @param <P> type of linked objects
	 * @param obj find the parent of this object
	 * @return the parent or null if {@code obj.getParentid()} is null
	 */
	public <P extends ParaObject> P getParent(ParaObject obj) {
		return getDao().read(obj.getAppid(), obj.getParentid());
	}

	/**
	 * The user object of the creator.
	 * @param <P> type of linked objects
	 * @param obj find the creator of this object
	 * @return the user who created this or null if {@code obj.getCreatorid()} is null
	 * @see com.erudika.para.core.User
	 */
	public <P extends ParaObject> P getCreator(ParaObject obj) {
		return getDao().read(obj.getAppid(), obj.getCreatorid());
	}

	///////////////////////////////////////
	//	    	VOTING METHODS
	///////////////////////////////////////

	/**
	 * Casts a vote on a given object.
	 * @param votable the object to vote on
	 * @param userid the voter
	 * @param upDown up or down
	 * @return true if the vote was successful
	 */
	public boolean vote(ParaObject votable, String userid, VoteValue upDown) {
		if (StringUtils.isBlank(userid) || votable == null || votable.getId() == null || upDown == null) {
			return false;
		}
		//no voting on your own stuff!
		if (userid.equals(votable.getCreatorid()) || userid.equals(votable.getId())) {
			return false;
		}

		Vote v = new Vote(userid, votable.getId(), upDown.toString());
		Vote saved = getDao().read(votable.getAppid(), v.getId());
		boolean done = false;
		int vote = (upDown == VoteValue.UP) ? 1 : -1;

		if (saved != null) {
			boolean isUpvote = upDown.equals(VoteValue.UP);
			boolean wasUpvote = VoteValue.UP.toString().equals(saved.getValue());
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
			synchronized (votable) {
				votable.setVotes(votable.getVotes() + vote);
			}
		}
		return done;
	}

}
