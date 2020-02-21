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

import com.erudika.para.utils.Pager;
import java.util.List;

/**
 * Applied to all {@link ParaObject}s by default. Allows an object to be linked to another object.
 * A link can be: 1-1, 1-* or *-*. One-to-one and one-to-many links are implemented using the {@code parentid} field.
 * Many-to-many links are implemented using {@link Linker} objects.
 * @author Alex Bogdanovski [alex@erudika.com]
 * @see Linker
 */
public interface Linkable {

	/**
	 * Count the total number of links between this object and another type of object.
	 * @param type2 the other type of object
	 * @return the number of links
	 */
	Long countLinks(String type2);

	/**
	 * Returns all links between this type object and another type of object.
	 * @param type2 the other type of object
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of {@code Linker} objects in a many-to-many relationship with this object.
	 */
	List<Linker> getLinks(String type2, Pager... pager);

	/**
	 * Similar to {@link #getChildren(java.lang.String, com.erudika.para.utils.Pager...) }
	 * but for many-to-many relationships.
	 * @param <P> type of linked objects
	 * @param type type of linked objects to look for
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of linked objects
	 */
	<P extends ParaObject> List<P> getLinkedObjects(String type, Pager... pager);

	/**
	 * Similar to {@link #findChildren(java.lang.String, java.lang.String, com.erudika.para.utils.Pager...)}
	 * but for many-to-many relationships. Searches through all linked objects connected to this via
	 * a {@link Linker} object.
	 * @param <P> type of linked objects
	 * @param type type of linked objects to look for
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @param field the name of the field to target (within a nested field "nstd")
	 * @param query a query string
	 * @return a list of linked objects matching the search query
	 */
	<P extends ParaObject> List<P> findLinkedObjects(String type, String field, String query, Pager... pager);

	/**
	 * Checks if this object is linked to another.
	 * @param type2 the other type
	 * @param id2 the other id
	 * @return true if the two are linked
	 */
	boolean isLinked(String type2, String id2);

	/**
	 * Checks if a given object is linked to this one.
	 * @param toObj the other object
	 * @return true if linked
	 */
	boolean isLinked(ParaObject toObj);

	/**
	 * Links an object to this one in a many-to-many relationship.
	 * Only a link is created. Objects are left untouched.
	 * The type of the second object is automatically determined on read.
	 * @param id2 the other id
	 * @return the id of the {@link Linker} object that is created
	 */
	String link(String id2);

	/**
	 * Unlinks an object from this one.
	 * Only a link is deleted. Objects are left untouched.
	 * @param type the other type
	 * @param id2 the other id
	 */
	void unlink(String type, String id2);

	/**
	 * Unlinks all objects that are linked to this one.
	 * Deletes all {@link Linker} objects. Only the links are deleted. Objects are left untouched.
	 */
	void unlinkAll();

	/**
	 * Count the total number of child objects for this object.
	 * @param type the other type of object
	 * @return the number of links
	 */
	Long countChildren(String type);

	/**
	 * Returns all child objects linked to this object.
	 * @param <P> the type of children
	 * @param type the type of children to look for
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of {@link ParaObject} in a one-to-many relationship with this object
	 */
	<P extends ParaObject> List<P> getChildren(String type, Pager... pager);

	/**
	 * Search through all child objects. Only searches child objects directly
	 * connected to this parent via the {@code parentid} field.
	 * @param <P> the type of children
	 * @param type the type of children to look for
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @param query a query string
	 * @return a list of {@link ParaObject} in a one-to-many relationship with this object
	 */
	<P extends ParaObject> List<P> findChildren(String type, String query, Pager... pager);

	/**
	 * Returns all child objects linked to this object.
	 * @param <P> the type of children
	 * @param type the type of children to look for
	 * @param field the field name to use as filter
	 * @param term the field value to use as filter
	 * @param pager a {@link com.erudika.para.utils.Pager}
	 * @return a list of {@link ParaObject} in a one-to-many relationship with this object
	 */
	<P extends ParaObject> List<P> getChildren(String type, String field, String term, Pager... pager);

	/**
	 * Deletes all child objects permanently.
	 * @param type the children's type.
	 */
	void deleteChildren(String type);

}
