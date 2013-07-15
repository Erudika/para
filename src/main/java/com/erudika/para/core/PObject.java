/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erudika.para.core;

import com.erudika.para.utils.DAO;
import com.erudika.para.utils.Locked;
import com.erudika.para.utils.Stored;
import java.util.logging.Level;
import javax.validation.constraints.Size;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.hibernate.validator.constraints.NotBlank;

/**
 *
 * @author alexb
 */
public abstract class PObject {
	
	@Stored @Locked private String id;
	@Stored @Locked private Long timestamp;
	@Stored @Locked private Long updated;
	@Stored @Locked private String parentid;
	@Stored @Locked private String creatorid;
	@Stored @NotBlank @Size(min=2, max=255) private String name;
	
	private transient PObject parent;
	private transient PObject creator;
	
	// one-to-many relationships (may return self)
	@JsonIgnore
	public PObject getParent(){
		if(parent == null)
			parent = DAO.getInstance().read(parentid);
		return parent;
	}
	
	@JsonIgnore
	public PObject getCreator(){
		if(creator == null)
			creator = DAO.getInstance().read(creatorid);
		return creator;
	}
	
	public String getParentid(){
		return (parentid == null) ? this.id : parentid;
	}
	
	public void setParentid(String parentid) {
		this.parentid = parentid;
	}
	
	public Long getUpdated() {
		return updated;
	}

	public void setUpdated(Long updated) {
		this.updated = updated;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
		
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}
	
	public String getCreatorid(){
		return creatorid;
	}
	
	public void setCreatorid(String creatorid){
		this.creatorid = creatorid;
	}

	public String create() {
		String oid = DAO.getInstance().create(this);
		if(id != null) id = oid;
		return id;
	}

	public void update() {
		DAO.getInstance().update(this);
	}

	public void delete() {
		DAO.getInstance().delete(this);
	}
	
	public String getClassname() {
		return classname(this.getClass());
	}
	
	public static String classname(Class<? extends PObject> clazz){
		if(clazz == null) return "";
		return clazz.getSimpleName().toLowerCase();
	}

}
