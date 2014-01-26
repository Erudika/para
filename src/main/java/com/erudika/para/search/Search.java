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

import com.erudika.para.core.ParaObject;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.mutable.MutableLong;

/**
 *
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public interface Search {

	/********************************************
	 *			   CORE METHODS
	 ********************************************/
	
	public void index(ParaObject so);
	public void index(String appName, ParaObject so);
	public void index(String appName, ParaObject so, long ttl);
	
	public void unindex(ParaObject so);
	public void unindex(String appName, ParaObject so);

	public <P extends ParaObject> void indexAll(List<P> objects);
	public <P extends ParaObject> void indexAll(String appName, List<P> objects);

	public <P extends ParaObject> void unindexAll(List<P> objects);
	public <P extends ParaObject> void unindexAll(String appName, List<P> objects);
	
	/********************************************
	 *			    SEARCH METHODS
	 ********************************************/

	public <P extends ParaObject> P findById(String id, String type);
	public <P extends ParaObject> P findById(String appName, String id, String type);
	
	public <P extends ParaObject> ArrayList<P> findNearbyObjects(String type, MutableLong page, MutableLong itemcount, String query, int radius, double lat, double lng, String sortby);
	public <P extends ParaObject> ArrayList<P> findNearbyObjects(String appName, String type, MutableLong page, MutableLong itemcount, String query, int radius, double lat, double lng, String sortby);

	public <P extends ParaObject> ArrayList<P> findPrefix(String type, MutableLong page, MutableLong itemcount, String field, String prefix);
	public <P extends ParaObject> ArrayList<P> findPrefix(String appName, String type, MutableLong page, MutableLong itemcount, String field, String prefix);

	public <P extends ParaObject> ArrayList<P> findPrefix(String type, MutableLong page, MutableLong itemcount, String field, String prefix, String sortfield, boolean reverse, int max);
	public <P extends ParaObject> ArrayList<P> findPrefix(String appName, String type, MutableLong page, MutableLong itemcount, String field, String prefix, String sortfield, boolean reverse, int max);

	public <P extends ParaObject> ArrayList<P> findQuery(String type, MutableLong page, MutableLong itemcount, String query);
	public <P extends ParaObject> ArrayList<P> findQuery(String appName, String type, MutableLong page, MutableLong itemcount, String query);

	public <P extends ParaObject> ArrayList<P> findQuery(String type, MutableLong page, MutableLong itemcount, String query, String sortfield, boolean reverse, int max);
	public <P extends ParaObject> ArrayList<P> findQuery(String appName, String type, MutableLong page, MutableLong itemcount, String query, String sortfield, boolean reverse, int max);

	public <P extends ParaObject> ArrayList<P> findSimilar(String type, String filterKey, String[] fields, String liketext, int max);
	public <P extends ParaObject> ArrayList<P> findSimilar(String appName, String type, String filterKey, String[] fields, String liketext, int max);

	public <P extends ParaObject> ArrayList<P> findTagged(String type, MutableLong page, MutableLong itemcount, String... tags);
	public <P extends ParaObject> ArrayList<P> findTagged(String appName, String type, MutableLong page, MutableLong itemcount, String... tags);

	public <P extends ParaObject> ArrayList<P> findTags(String keyword, int max);
	public <P extends ParaObject> ArrayList<P> findTags(String appName, String keyword, int max);

	public <P extends ParaObject> ArrayList<P> findTerm(String type, MutableLong page, MutableLong itemcount, String field, Object term);
	public <P extends ParaObject> ArrayList<P> findTerm(String appName, String type, MutableLong page, MutableLong itemcount, String field, Object term);

	public <P extends ParaObject> ArrayList<P> findTerm(String type, MutableLong page, MutableLong itemcount, String field, Object term, String sortfield, boolean reverse, int max);
	public <P extends ParaObject> ArrayList<P> findTerm(String appName, String type, MutableLong page, MutableLong itemcount, String field, Object term, String sortfield, boolean reverse, int max);

	public <P extends ParaObject> ArrayList<P> findTermInList(String type, MutableLong page, MutableLong itemcount, String field, List<?> terms, String sortfield, boolean reverse, int max);
	public <P extends ParaObject> ArrayList<P> findTermInList(String appName, String type, MutableLong page, MutableLong itemcount, String field, List<?> terms, String sortfield, boolean reverse, int max);

	public <P extends ParaObject> ArrayList<P> findTwoTerms(String type, MutableLong page, MutableLong itemcount, String field1, Object term1, String field2, Object term2);
	public <P extends ParaObject> ArrayList<P> findTwoTerms(String appName, String type, MutableLong page, MutableLong itemcount, String field1, Object term1, String field2, Object term2);

	public <P extends ParaObject> ArrayList<P> findTwoTerms(String type, MutableLong page, MutableLong itemcount, String field1, Object term1, String field2, Object term2, String sortfield, boolean reverse, int max);
	public <P extends ParaObject> ArrayList<P> findTwoTerms(String appName, String type, MutableLong page, MutableLong itemcount, String field1, Object term1, String field2, Object term2, String sortfield, boolean reverse, int max);

	public <P extends ParaObject> ArrayList<P> findTwoTerms(String type, MutableLong page, MutableLong itemcount, String field1, Object term1, String field2, Object term2, boolean mustMatchBoth, String sortfield, boolean reverse, int max);
	public <P extends ParaObject> ArrayList<P> findTwoTerms(String appName, String type, MutableLong page, MutableLong itemcount, String field1, Object term1, String field2, Object term2, boolean mustMatchBoth, String sortfield, boolean reverse, int max);

	public <P extends ParaObject> ArrayList<P> findWildcard(String type, MutableLong page, MutableLong itemcount, String field, String wildcard);
	public <P extends ParaObject> ArrayList<P> findWildcard(String appName, String type, MutableLong page, MutableLong itemcount, String field, String wildcard);

	public <P extends ParaObject> ArrayList<P> findWildcard(String type, MutableLong page, MutableLong itemcount, String field, String wildcard, String sortfield, boolean reverse, int max);
	public <P extends ParaObject> ArrayList<P> findWildcard(String appName, String type, MutableLong page, MutableLong itemcount, String field, String wildcard, String sortfield, boolean reverse, int max);

	/********************************************
	 *			  HELPER METHODS
	 ********************************************/
	
	public Long getBeanCount(String type);
	public Long getBeanCount(String appName, String type);

	public Long getCount(String type, String field, Object term);
	public Long getCount(String appName, String type, String field, Object term);

	public Long getCount(String type, String field1, Object term1, String field2, Object term2);
	public Long getCount(String appName, String type, String field1, Object term1, String field2, Object term2);

}
