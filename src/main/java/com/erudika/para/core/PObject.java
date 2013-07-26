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
import java.io.Serializable;
import java.util.ArrayList;
import javax.validation.constraints.Size;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.hibernate.validator.constraints.NotBlank;

/**
 *
 * @author alexb
 */
public abstract class PObject implements Serializable{
	
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
	
	public String link(Class<? extends PObject> c2, String id2){
		if(c2 == null) return null;
		return new Linker(this.getClass(), c2, getId(), id2).create();
	}
	
	public void unlink(Class<? extends PObject> c2, String id2){
		if(c2 == null) return;
		new Linker(this.getClass(), c2, getId(), id2).delete();
	}
	
	public void unlinkAll(){
		DAO.getInstance().deleteAll(Search.findTwoTerms(classname(Linker.class), 
				null, null, "id1", id, "id2", id, false, null, true, Utils.DEFAULT_LIMIT));
	}
	
	public ArrayList<Linker> getAllLinks(Class<? extends PObject> c2){
		return getAllLinks(c2, null, null, true, Utils.DEFAULT_LIMIT);
	}
	
	public ArrayList<Linker> getAllLinks(Class<? extends PObject> c2,  MutableLong pagenum,
			MutableLong itemcount, boolean reverse, int maxItems){
		if(c2 == null) return new ArrayList<Linker>();
		Linker link = new Linker(this.getClass(), c2, null, null);
		String idField = link.getFirstIdFieldName();
		return Search.findTwoTerms(link.getClassname(), pagenum, itemcount, DAO.CN_NAME, link.getName(), 
				idField, id, null, reverse, maxItems);
	}
	
	public boolean isLinked(Class<? extends PObject> c2, String toId){
		if(c2 == null) return false;
		return new Linker(this.getClass(), c2, getId(), toId).exists();
	}
	
	public Long countLinks(Class<? extends PObject> c2){
		if(id == null) return 0L;
		Linker link = new Linker(this.getClass(), c2, null, null);
		String idField = link.getFirstIdFieldName();
		return Search.getCount(link.getClassname(), DAO.CN_NAME, link.getName(), idField, id);
	}
	
	public static String classname(Class<? extends PObject> clazz){
		if(clazz == null) return "";
		return clazz.getSimpleName().toLowerCase();
	}
	
	public <P extends PObject> ArrayList<P> getChildren(Class<? extends PObject> clazz){
		return getChildren(clazz, null, null, null, Utils.DEFAULT_LIMIT);
	}
	
    public <P extends PObject> ArrayList<P> getChildren(Class<? extends PObject> clazz, 
			MutableLong page, MutableLong itemcount, String sortfield, int max){
		return Search.findTerm(classname(clazz), page, itemcount, DAO.CN_PARENTID, getId(), sortfield, true, max);
    }
	
	public void deleteChildren(Class<? extends PObject> clazz){
		if(StringUtils.isBlank(getId())) return ;
		DAO.getInstance().deleteAll(Search.findTerm(classname(clazz), null, null, DAO.CN_PARENTID, getId()));
	}
	
	public <P extends PObject> ArrayList<P> getLinkedObjects(Class<? extends PObject> clazz, MutableLong page,
			MutableLong itemcount){
		ArrayList<Linker> links = getAllLinks(clazz, null, null, true, Utils.MAX_ITEMS_PER_PAGE);
		ArrayList<String> keys = new ArrayList<String>();
		for (Linker link : links) {
			if(link.isFirst(clazz)){
				keys.add(link.getId1());
			}else{
				keys.add(link.getId2());
			}
		}
		return Search.findTermInList(classname(clazz), page, itemcount, DAO.CN_ID, keys, 
				null, true, Utils.MAX_ITEMS_PER_PAGE);
	}
}
