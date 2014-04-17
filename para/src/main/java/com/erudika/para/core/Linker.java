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

import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import org.hibernate.validator.constraints.NotBlank;

/**
 * This class represents a many-to-many relationship (link) between two objects.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class Linker extends PObject {
	private static final long serialVersionUID = 1L;

	@Stored @Locked @NotBlank private String id1;
	@Stored @Locked @NotBlank private String id2;
	@Stored @Locked @NotBlank private String type1;
	@Stored @Locked @NotBlank private String type2;
	@Stored private String metadata;

	/**
	 * No-args constructor
	 */
	public Linker() { }

	/**
	 * A link. The names of the objects are compared and sorted alphabetically.
	 * @param c1 the type of the first object
	 * @param c2 the type of the second object
	 * @param i1 the id of the first object
	 * @param i2 the id of the second object
	 */
	public Linker(Class<? extends ParaObject> c1, Class<? extends ParaObject> c2, String i1, String i2) {
		if (isReversed(Utils.type(c1), Utils.type(c2))) {
			type1 = Utils.type(c2);
			type2 = Utils.type(c1);
			this.id1 = i2;
			this.id2 = i1;
		} else {
			type1 = Utils.type(c1);
			type2 = Utils.type(c2);
			this.id1 = i1;
			this.id2 = i2;
		}
		setName(type1 + Config.SEPARATOR + type2);
		setId(type1 + Config.SEPARATOR + id1 + Config.SEPARATOR + type2 + Config.SEPARATOR + id2);
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
//		for (PObject link : search.findTerms(getType(), null, null, "id1", id1, "id2", id2)) {
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
	 * @param c1 the given class of object
	 * @return true if the object's type is equal to {@link #getType1()}
	 */
	public boolean isFirst(Class<? extends ParaObject> c1) {
		if (c1 == null) {
			return false;
		}
		return Utils.type(c1).equals(type1);
	}
//
	/**
	 * Returns the name of the id field (id1 or id2) for a given type.
	 * @param c the type to check
	 * @return "id1" if the given type is first in the link, otherwise "id2"
	 */
	public String getIdFieldNameFor(Class<? extends ParaObject> c) {
		return isFirst(c) ? "id1" : "id2";
	}
}
