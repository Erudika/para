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
import com.erudika.para.persistence.DAO;
import com.erudika.para.search.Search;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import javax.inject.Inject;
import javax.validation.constraints.Size;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.hibernate.validator.constraints.NotBlank;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public abstract class PObject implements ParaObject, Linkable, Votable{
	
	@Stored @Locked private String id;
	@Stored @Locked private Long timestamp;
	@Stored @Locked private Long updated;
	@Stored @Locked private String classname;
	@Stored @Locked private String parentid;
	@Stored @Locked private String creatorid;
	@Stored @NotBlank @Size(min=2, max=255) private String name;
	@Stored private Integer votes;
	
	private transient PObject parent;
	private transient PObject creator;
	
	private DAO dao;
	private Search search;
	
	public DAO getDao() {
		return dao;
	}

	@Inject
	public void setDao(DAO dao) {
		this.dao = dao;
	}

	public Search getSearch() {
		return search;
	}

	@Inject
	public void setSearch(Search search) {
		this.search = search;
	}
	
	// one-to-many relationships (may return self)
	@JsonIgnore
	@Override
	public PObject getParent(){
		if(parent == null)
			parent = dao.read(parentid);
		return parent;
	}
	
	@JsonIgnore
	@Override
	public PObject getCreator(){
		if(creator == null)
			creator = dao.read(creatorid);
		return creator;
	}
	
	@Override
	public String getParentid(){
		return (parentid == null) ? this.id : parentid;
	}
	
	@Override
	public void setParentid(String parentid) {
		this.parentid = parentid;
	}
	
	@Override
	public Long getUpdated() {
		return updated;
	}

	@Override
	public void setUpdated(Long updated) {
		this.updated = updated;
	}
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}
		
	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public Long getTimestamp() {
		return timestamp;
	}

	@Override
	public void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}
	
	@Override
	public String getCreatorid(){
		return creatorid;
	}
	
	@Override
	public void setCreatorid(String creatorid){
		this.creatorid = creatorid;
	}
	
	@Override
	public String create() {
		return dao.create(this);
	}

	@Override
	public void update() {
		dao.update(this);
	}

	@Override
	public void delete() {
		dao.delete(this);
	}
	
	@Override
	public String getClassname() {
		if(classname == null) 
			classname = classname(this.getClass());
		return classname;
	}
	
	@Override
	public void setClassname(String classname) {
		this.classname = classname;
	}
	
	@Override
	public String link(Class<? extends ParaObject> c2, String id2){
		if(c2 == null) return null;
		return new Linker(this.getClass(), c2, getId(), id2).create();
	}
	
	@Override
	public void unlink(Class<? extends ParaObject> c2, String id2){
		if(c2 == null) return;
		new Linker(this.getClass(), c2, getId(), id2).delete();
	}
	
	@Override
	public void unlinkAll(){
		dao.deleteAll(search.findTwoTerms(classname(Linker.class), null, null, "id1", id, "id2", id, 
				false, null, true, Utils.DEFAULT_LIMIT));
	}
	
	@Override
	public ArrayList<Linker> getAllLinks(Class<? extends ParaObject> c2){
		return getAllLinks(c2, null, null, true, Utils.DEFAULT_LIMIT);
	}
	
	@Override
	public ArrayList<Linker> getAllLinks(Class<? extends ParaObject> c2,  MutableLong pagenum,
			MutableLong itemcount, boolean reverse, int maxItems){
		if(c2 == null) return new ArrayList<Linker>();
		Linker link = new Linker(this.getClass(), c2, null, null);
		String idField = link.getIdFieldNameFor(this.getClass());
		return search.findTwoTerms(link.getClassname(), pagenum, itemcount, 
				DAO.CN_NAME, link.getName(), idField, id, null, reverse, maxItems);
	}
	
	@Override
	public boolean isLinked(Class<? extends ParaObject> c2, String toId){
		if(c2 == null) return false;
		return new Linker(this.getClass(), c2, getId(), toId).exists();
	}
	
	@Override
	public Long countLinks(Class<? extends ParaObject> c2){
		if(id == null) return 0L;
		Linker link = new Linker(this.getClass(), c2, null, null);
		String idField = link.getIdFieldNameFor(this.getClass());
		return search.getCount(link.getClassname(), 
				DAO.CN_NAME, link.getName(), idField, id);
	}
	
	public static String classname(Class<? extends ParaObject> clazz){
		if(clazz == null) return "";
		return clazz.getSimpleName().toLowerCase();
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> getChildren(Class<? extends ParaObject> clazz){
		return getChildren(clazz, null, null, null, Utils.DEFAULT_LIMIT);
	}
	
	@Override
    public <P extends ParaObject> ArrayList<P> getChildren(Class<? extends ParaObject> clazz, 
			MutableLong page, MutableLong itemcount, String sortfield, int max){
		return search.findTerm(classname(clazz), page, itemcount, 
				DAO.CN_PARENTID, getId(), sortfield, true, max);
    }
	
	@Override
	public void deleteChildren(Class<? extends ParaObject> clazz){
		if(StringUtils.isBlank(getId())) return ;
		dao.deleteAll(search.findTerm(classname(clazz), 
				null, null, DAO.CN_PARENTID, getId()));
	}
	
	@Override
	public <P extends ParaObject> ArrayList<P> getLinkedObjects(Class<? extends ParaObject> clazz, MutableLong page,
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
		
		return new ArrayList<P>((Collection<? extends P>) dao.readAll(keys, true).values());
//		return search.findTermInList(classname(clazz), page, itemcount, DAO.CN_ID, keys, 
//				null, true, Utils.MAX_ITEMS_PER_PAGE);
	}
	
	/********************************************
	 *	    	VOTING FUNCTIONS
	********************************************/
	
	private boolean vote(String userid, Votable votable, VoteType upDown) {
		//no voting on your own stuff!
		if(votable == null || StringUtils.isBlank(userid) || userid.equals(votable.getCreatorid()) ||
				votable.getId() == null || upDown == null) return false;
		
		Vote v = new SimpleVote();
		v.setCreatorid(userid);
		v.setParentid(votable.getId());
		v.setType(upDown.toString());
		v.setTimestamp(System.currentTimeMillis());
		boolean done = v.amendVote() || v.create() != null;
		
		if(done){
			// recalculate votes
			long countPositive = search.getCount(v.getClassname(), DAO.CN_PARENTID, 
					getId(), "type", VoteType.UP.toString());
			long countNegative = search.getCount(v.getClassname(), DAO.CN_PARENTID, 
					getId(), "type", VoteType.DOWN.toString());
			
			setVotes((int) (countPositive - countNegative));
		}
		
		return done;
	}
	
	@Override
	public final boolean voteUp(String userid){
		return vote(userid, this, VoteType.UP);
	}
	
	@Override
	public final boolean voteDown(String userid){
		return vote(userid, this, VoteType.DOWN);
	}

	@Override
	public final Integer getVotes() {
		if(votes == null) votes = 0;
		return votes;
	}
	
	public final void setVotes(Integer votes){
		this.votes = votes;
	}
	
	/********************************************
	 *	    	MISC FUNCTIONS
	 ********************************************/

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 67 * hash + Objects.hashCode(this.id);
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final PObject other = (PObject) obj;
		if (!Objects.equals(this.id, other.id)) {
			return false;
		}
		return true;
	}
	
}
