/*
 * Copyright 2013 Alex Bogdanovski <albogdano@me.com>.
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

import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import static com.erudika.para.core.PObject.classname;
import com.erudika.para.utils.Config;
import org.hibernate.validator.constraints.NotBlank;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 * 
 * This class represents a many-to-many relationship (link) between two objects.
 */
public class Linker extends PObject{
	private static final long serialVersionUID = 1L;

	@Stored @Locked @NotBlank private String id1;
	@Stored @Locked @NotBlank private String id2;
	@Stored @Locked @NotBlank private String classname1;
	@Stored @Locked @NotBlank private String classname2;
	@Stored private String metadata;
	
	public Linker() {
	}

	public Linker(Class<? extends ParaObject> c1, Class<? extends ParaObject> c2, String i1, String i2) {
		if(isReversed(classname(c1), classname(c2))){
			classname1 = classname(c2);
			classname2 = classname(c1);
			this.id1 = i2;
			this.id2 = i1;
		}else{
			classname1 = classname(c1);
			classname2 = classname(c2);
			this.id1 = i1;
			this.id2 = i2;
		}
		setName(classname1 + Config.SEPARATOR + classname2);
		setId(classname1 + Config.SEPARATOR + id1 + Config.SEPARATOR + classname2 + Config.SEPARATOR + id2);
	}
	
	public String getId2() {
		return id2;
	}

	public void setId2(String id2) {
		this.id2 = id2;
	}

	public String getId1() {
		return id1;
	}

	public void setId1(String id1) {
		this.id1 = id1;
	}

	public String getClassname1() {
		return classname1;
	}

	public void setClassname1(String classname1) {
		this.classname1 = classname1;
	}

	public String getClassname2() {
		return classname2;
	}

	public void setClassname2(String classname2) {
		this.classname2 = classname2;
	}
	
	public String getMetadata() {
		return metadata;
	}

	public void setMetadata(String metadata) {
		this.metadata = metadata;
	}

//	public void delete() {
//		ArrayList<String> keys = new ArrayList<String>();
//		for (PObject link : search.findTwoTerms(getPlural(), null, null, "id1", id1, "id2", id2)) {
//			keys.add(link.getId());
//		}
//		AWSDynamoDAO.getInstance().deleteAll(keys);
//	}
	
	public boolean exists(){
//		return search.getCount(getPlural(), DAO.CN_ID, getId()) > 0;
		return getDao().read(getId()) != null;
	}
	
	private boolean isReversed(String s1, String s2){
		if(s1 == null || s2 == null) return false;
		return s1.compareToIgnoreCase(s2) > 0;
	}
	
	public boolean isFirst(Class<? extends ParaObject> c1){
		if(c1 == null) return false;
		return classname(c1).equals(classname1);
	}
//	
	public String getIdFieldNameFor(Class<? extends ParaObject> c){
		return isFirst(c) ? "id1" : "id2";
	}
}
