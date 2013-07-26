/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erudika.para.core;

import com.erudika.para.utils.DAO;
import com.erudika.para.utils.Locked;
import com.erudika.para.utils.Search;
import com.erudika.para.utils.Stored;
import com.erudika.para.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 * 
 * This class represents a many-to-many relationship between two objects.
 */
public class Linker extends PObject{
	private static final long serialVersionUID = 1L;

	@Stored @Locked @NotBlank private String id1;
	@Stored @Locked @NotBlank private String id2;
	@Stored private String metadata;

	private transient String classname1;
	private transient String classname2;
	
	
	public Linker() {
	}

	public Linker(Class<? extends PObject> c1, Class<? extends PObject> c2, String i1, String i2) {
		classname1 = classname(c1);
		classname2 = classname(c2);
		if(isReversed()){
			this.id1 = i2;
			this.id2 = i1;
			setName(classname2 + Utils.SEPARATOR + classname1);
			setId(classname2 + Utils.SEPARATOR + id2 + Utils.SEPARATOR + classname1 + Utils.SEPARATOR + id1);
		}else{
			this.id1 = i1;
			this.id2 = i2;
			setName(classname1 + Utils.SEPARATOR + classname2);
			setId(classname2 + Utils.SEPARATOR + id2 + Utils.SEPARATOR + classname1 + Utils.SEPARATOR + id1);
		}
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

	public String create() {
		if(StringUtils.isBlank(getId()) || StringUtils.isBlank(getName()) || 
				StringUtils.isBlank(id1) || StringUtils.isBlank(id2)) return null;
		return super.create();
	}

	public String getMetadata() {
		return metadata;
	}

	public void setMetadata(String metadata) {
		this.metadata = metadata;
	}

//	public void delete() {
//		ArrayList<String> keys = new ArrayList<String>();
//		for (PObject link : Search.findTwoTerms(getClassname(), null, null, "id1", id1, "id2", id2)) {
//			keys.add(link.getId());
//		}
//		DAO.getInstance().deleteAll(keys);
//	}
	
	public boolean exists(){
		return Search.getCount(getClassname(), DAO.CN_ID, getId()) > 0;
	}
	
	public boolean isReversed(){
		if(classname1 == null || classname2 == null) return false;
		return classname1.compareToIgnoreCase(classname2) > 0;
	}
	
	public boolean isFirst(Class<? extends PObject> c){
		return classname1.equals(classname(c));
	}
	
	public String getFirstIdFieldName(){
		return isReversed() ? "id2" : "id1";
	}
}
