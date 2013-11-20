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
package com.erudika.para.search;

import com.erudika.para.core.ParaObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.mutable.MutableLong;
import org.elasticsearch.search.SearchHits;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public interface Search {

	public void createIndex(String name);

	public void deleteIndex(String name);

	public boolean existsIndex(String name);

	public <P extends ParaObject> P findById(String key, String type);

	public <P extends ParaObject> ArrayList<P> findNearbyObjects(String type, MutableLong page, MutableLong itemcount, String query, int radius, double lat, double lng, String sortby);

	public <P extends ParaObject> ArrayList<P> findPrefix(String type, MutableLong page, MutableLong itemcount, String field, String prefix);

	public <P extends ParaObject> ArrayList<P> findPrefix(String type, MutableLong page, MutableLong itemcount, String field, String prefix, String sortfield, boolean reverse, int max);

	public <P extends ParaObject> ArrayList<P> findQuery(String type, MutableLong page, MutableLong itemcount, String query);

	public <P extends ParaObject> ArrayList<P> findQuery(String type, MutableLong page, MutableLong itemcount, String query, String sortfield, boolean reverse, int max);

	public <P extends ParaObject> ArrayList<P> findSimilar(String type, String filterKey, String[] fields, String liketext, int max);

	public <P extends ParaObject> ArrayList<P> findTagged(String type, MutableLong page, MutableLong itemcount, ArrayList<String> tags);

	public <P extends ParaObject> ArrayList<P> findTags(String keyword, int max);

	public <P extends ParaObject> ArrayList<P> findTerm(String type, MutableLong page, MutableLong itemcount, String field, Object term);

	public <P extends ParaObject> ArrayList<P> findTerm(String type, MutableLong page, MutableLong itemcount, String field, Object term, String sortfield, boolean reverse, int max);

	public <P extends ParaObject> ArrayList<P> findTermInList(String type, MutableLong page, MutableLong itemcount, String field, List<?> terms, String sortfield, boolean reverse, int max);

	public <P extends ParaObject> ArrayList<P> findTwoTerms(String type, MutableLong page, MutableLong itemcount, String field1, Object term1, String field2, Object term2);

	public <P extends ParaObject> ArrayList<P> findTwoTerms(String type, MutableLong page, MutableLong itemcount, String field1, Object term1, String field2, Object term2, String sortfield, boolean reverse, int max);

	public <P extends ParaObject> ArrayList<P> findTwoTerms(String type, MutableLong page, MutableLong itemcount, String field1, Object term1, String field2, Object term2, boolean mustMatchBoth, String sortfield, boolean reverse, int max);

	public <P extends ParaObject> ArrayList<P> findWildcard(String type, MutableLong page, MutableLong itemcount, String field, String wildcard);

	public <P extends ParaObject> ArrayList<P> findWildcard(String type, MutableLong page, MutableLong itemcount, String field, String wildcard, String sortfield, boolean reverse, int max);

	public Long getBeanCount(String classtype);

	public Long getCount(String classtype, String field, Object term);

	public Long getCount(String classtype, String field1, Object term1, String field2, Object term2);

	public String getIndexName();

	public Map<String, String> getSearchClusterMetadata();

	public Map<String, Object> getSource(String key, String type);

	public void index(ParaObject so, String type);

	public void index(ParaObject so, String type, long ttl);

	public <P extends ParaObject> void indexAll(List<P> objects);

	public boolean optimizeIndex(String name);

	public void rebuildIndex(String newIndex);

	public <P extends ParaObject> ArrayList<P> searchQuery(String type, MutableLong itemcount, SearchHits hits);

	public void unindex(ParaObject so, String type);

	public <P extends ParaObject> void unindexAll(List<P> objects);
		
}
