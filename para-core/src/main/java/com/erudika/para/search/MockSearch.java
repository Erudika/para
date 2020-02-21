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
package com.erudika.para.search;

import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.persistence.DAO;
import com.erudika.para.utils.Pager;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class MockSearch implements Search {

	@Override
	public void index(ParaObject po) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void index(String appid, ParaObject po) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void unindex(ParaObject po) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void unindex(String appid, ParaObject po) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> void indexAll(List<P> objects) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> void indexAll(String appid, List<P> objects) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> void unindexAll(List<P> objects) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> void unindexAll(String appid, List<P> objects) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void unindexAll(Map<String, ?> terms, boolean matchAll) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public void unindexAll(String appid, Map<String, ?> terms, boolean matchAll) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> P findById(String id) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> P findById(String appid, String id) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findByIds(List<String> ids) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findByIds(String appid, List<String> ids) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findNearby(String type, String query, int radius, double lat, double lng, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findNearby(String appid, String type, String query, int radius, double lat, double lng, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findPrefix(String type, String field, String prefix, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findPrefix(String appid, String type, String field, String prefix, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findQuery(String type, String query, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findQuery(String appid, String type, String query, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findNestedQuery(String type, String field, String query, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findNestedQuery(String appid, String type, String field, String query, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findSimilar(String type, String filterKey, String[] fields, String liketext, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findSimilar(String appid, String type, String filterKey, String[] fields, String liketext, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findTagged(String type, String[] tags, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findTagged(String appid, String type, String[] tags, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findTags(String keyword, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findTags(String appid, String keyword, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findTermInList(String type, String field, List<?> terms, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findTermInList(String appid, String type, String field, List<?> terms, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findTerms(String type, Map<String, ?> terms, boolean matchAll, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findTerms(String appid, String type, Map<String, ?> terms, boolean matchAll, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findWildcard(String type, String field, String wildcard, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public <P extends ParaObject> List<P> findWildcard(String appid, String type, String field, String wildcard, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public Long getCount(String type) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public Long getCount(String appid, String type) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public Long getCount(String type, Map<String, ?> terms) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public Long getCount(String appid, String type, Map<String, ?> terms) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public boolean rebuildIndex(DAO dao, App app, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public boolean rebuildIndex(DAO dao, App app, String destinationIndex, Pager... pager) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public boolean isValidQueryString(String queryString) {
		throw new UnsupportedOperationException("Not implemented.");
	}

}
