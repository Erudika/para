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
package com.erudika.para.search;

import com.erudika.para.utils.Pager;
import com.erudika.para.core.ParaObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The core search interface. Does indexing and searching for all domain objects.
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public interface Search {

	/////////////////////////////////////////////
	//			   CORE METHODS
	/////////////////////////////////////////////

	/**
	 * Indexes an object. Only fields marked with {@link com.erudika.para.annotations.Stored} are indexed.
	 * @param so the domain object the object to index
	 */
	void index(ParaObject so);
	
	/**
	 * Indexes an object. Only fields marked with {@link com.erudika.para.annotations.Stored} are indexed.
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param so the domain object
	 */
	void index(String appName, ParaObject so);
	
	/**
	 * Indexes an object. Only fields marked with {@link com.erudika.para.annotations.Stored} are indexed.
	 * Automatically removes the object from the index after TTL milliseconds.
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param so the domain object
	 * @param ttl time to live in milliseconds before the object is removed from the index.
	 */
	void index(String appName, ParaObject so, long ttl);

	/**
	 * Removes an object from the index.
	 * @param so the domain object
	 */
	void unindex(ParaObject so);
	
	/**
	 * Removes an object from the index.
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param so the domain object
	 */
	void unindex(String appName, ParaObject so);

	/**
	 * Indexes multiple objects in a batch operation.
	 * @param <P> type of the object
	 * @param objects a list of objects
	 */
	<P extends ParaObject> void indexAll(List<P> objects);

	/**
	 * Indexes multiple objects in a batch operation.
	 * @param <P> type of the object
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param objects a list of objects
	 */
	<P extends ParaObject> void indexAll(String appName, List<P> objects);

	/**
	 * Removes multiple objects from the index in a batch operation.
	 * @param <P> type of the object
	 * @param objects a list of objects
	 */
	<P extends ParaObject> void unindexAll(List<P> objects);

	/**
	 * Removes multiple objects from the index in a batch operation.
	 * @param <P> type of the object
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param objects a list of objects
	 */
	<P extends ParaObject> void unindexAll(String appName, List<P> objects);

	/////////////////////////////////////////////
	//			    SEARCH METHODS
	/////////////////////////////////////////////

	/**
	 * Simple id search.
	 * @param <P> type of the object
	 * @param id the id
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @return the object if found or null
	 */
	<P extends ParaObject> P findById(String id, String type);

	/**
	 * Simple id search.
	 * @param <P> type of the object
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param id the id
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @return the object if found or null
	 */
	<P extends ParaObject> P findById(String appName, String id, String type);

	/**
	 * Search for {@link com.erudika.para.core.Address} objects in a radius of X km from a given point.
	 * @param <P> type of the object
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param query the query string
	 * @param radius the radius of the search circle
	 * @param lat latitude
	 * @param lng longitude
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findNearby(String type, String query, int radius, double lat, double lng, Pager... pager);

	/**
	 * Search for {@link com.erudika.para.core.Address} objects in a radius of X km from a given point.
	 * @param <P> type of the object
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param query the query string
	 * @param radius the radius of the search circle
	 * @param lat latitude
	 * @param lng longitude
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findNearby(String appName, String type, String query, int radius, double lat, double lng, Pager... pager);

	/**
	 * Searches for objects that have a property which value starts with a given prefix.
	 * @param <P> type of the object
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param field the property name of an object
	 * @param prefix the prefix
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findPrefix(String type, String field, String prefix, Pager... pager);

	/**
	 * Searches for objects that have a property which value starts with a given prefix.
	 * @param <P> type of the object
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param field the property name of an object
	 * @param prefix the prefix
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findPrefix(String appName, String type, String field, String prefix, Pager... pager);

	/**
	 * Simple query string search. A general purpose search method.
	 * @param <P> type of the object
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param query the query string
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findQuery(String type, String query, Pager... pager);

	/**
	 * Simple query string search. A general purpose search method.
	 * @param <P> type of the object
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param query the query string
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findQuery(String appName, String type, String query, Pager... pager);

	/**
	 * Searches for objects that have similar property values to a given text. A "find like this" query.
	 * @param <P> type of the object
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param filterKey exclude an object with this key from the results (optional)
	 * @param fields a list of property names
	 * @param liketext text to compare to
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findSimilar(String type, String filterKey, String[] fields, String liketext, Pager... pager);

	/**
	 * Searches for objects that have similar property values to a given text. A "find like this" query.
	 * @param <P> type of the object
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param filterKey exclude an object with this key from the results (optional)
	 * @param fields a list of property names
	 * @param liketext text to compare to
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findSimilar(String appName, String type, String filterKey, String[] fields, String liketext, Pager... pager);

	/**
	 * Searches for objects tagged with one or more tags.
	 * @param <P> type of the object
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param tags the list of tags
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findTagged(String type, String[] tags, Pager... pager);

	/**
	 * Searches for objects tagged with one or more tags.
	 * @param <P> type of the object
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param tags the list of tags
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findTagged(String appName, String type, String[] tags, Pager... pager);

	/**
	 * Searches for {@link com.erudika.para.core.Tag} objects. 
	 * This method might be deprecated in the future.
	 * @param <P> type of the object
	 * @param keyword the tag keyword to search for
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findTags(String keyword, Pager... pager);

	/**
	 * Searches for {@link com.erudika.para.core.Tag} objects. 
	 * This method might be deprecated in the future.
	 * @param <P> type of the object
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param keyword the tag keyword to search for
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findTags(String appName, String keyword, Pager... pager);

	/**
	 * Searches for objects having a property value that is in list of possible values.
	 * @param <P> type of the object
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param field the property name of an object
	 * @param terms a list of terms (property values)
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findTermInList(String type, String field, List<?> terms, Pager... pager);

	/**
	 * Searches for objects having a property value that is in list of possible values.
	 * @param <P> type of the object
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param field the property name of an object
	 * @param terms a list of terms (property values)
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findTermInList(String appName, String type, String field, List<?> terms, Pager... pager);

	/**
	 * Searches for objects that have properties matching some given values. A terms query.
	 * @param <P> type of the object
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param terms a map of fields (property names) to terms (property values)
	 * @param matchAll match all terms. If true - AND search, if false - OR search
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findTerms(String type, Map<String, ?> terms, boolean matchAll, Pager... pager);

	/**
	 * Searches for objects that have properties matching some given values. A terms query.
	 * @param <P> type of the object
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param terms a map of fields (property names) to terms (property values)
	 * @param matchAll match all terms. If true - AND search, if false - OR search
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findTerms(String appName, String type, Map<String, ?> terms, boolean matchAll, Pager... pager);

	/**
	 * Searches for objects that have a property with a value matching a wildcard query.
	 * @param <P> type of the object
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param field the property name of an object
	 * @param wildcard wildcard query string. For example "cat*".
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findWildcard(String type, String field, String wildcard, Pager... pager);

	/**
	 * Searches for objects that have a property with a value matching a wildcard query.
	 * @param <P> type of the object
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param field the property name of an object
	 * @param wildcard wildcard query string. For example "cat*".
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of objects found
	 */
	<P extends ParaObject> ArrayList<P> findWildcard(String appName, String type, String field, String wildcard, Pager... pager);

	/////////////////////////////////////////////
	//			  HELPER METHODS
	/////////////////////////////////////////////

	/**
	 * Counts indexed objects.
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @return the number of results found
	 */
	Long getCount(String type);

	/**
	 * Counts indexed objects.
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @return the number of results found
	 */
	Long getCount(String appName, String type);

	/**
	 * Counts indexed objects matching a set of terms/values.
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param terms a list of terms (property values)
	 * @return the number of results found
	 */
	Long getCount(String type, Map<String, ?> terms);

	/**
	 * Counts indexed objects matching a set of terms/values.
	 * @param appName name of the {@link com.erudika.para.core.App}
	 * @param type the type of object. See {@link com.erudika.para.core.ParaObject#getClassname()}
	 * @param terms a map of fields (property names) to terms (property values)
	 * @return the number of results found
	 */
	Long getCount(String appName, String type, Map<String, ?> terms);

}
