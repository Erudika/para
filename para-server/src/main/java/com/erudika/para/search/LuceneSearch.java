/*
 * Copyright 2013-2017 Erudika. http://erudika.com
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

import com.erudika.para.Para;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Tag;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.persistence.DAO;
import static com.erudika.para.search.LuceneUtils.count;
import static com.erudika.para.search.LuceneUtils.getTermsQuery;
import static com.erudika.para.search.LuceneUtils.indexDocuments;
import static com.erudika.para.search.LuceneUtils.paraObjectToDocument;
import static com.erudika.para.search.LuceneUtils.searchGeoQuery;
import static com.erudika.para.search.LuceneUtils.unindexDocuments;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.mlt.MoreLikeThisQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.erudika.para.search.LuceneUtils.searchQuery;
import static org.apache.lucene.document.LatLonPoint.newDistanceQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.util.BytesRef;

/**
 * An implementation of the {@link Search} interface using Lucene core.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class LuceneSearch implements Search {

	private static final Logger logger = LoggerFactory.getLogger(LuceneSearch.class);
	private DAO dao;

	/**
	 * No-args constructor.
	 */
	public LuceneSearch() {
		this(Para.getDAO());
	}

	/**
	 * Default constructor.
	 * @param dao an instance of the persistence class
	 */
	@Inject
	public LuceneSearch(DAO dao) {
		this.dao = dao;
	}

	@Override
	public void index(String appid, ParaObject po) {
		if (po == null || StringUtils.isBlank(appid)) {
			return;
		}
		Map<String, Object> data = ParaObjectUtils.getAnnotatedFields(po, null, false);
		indexDocuments(appid, Collections.singletonList(paraObjectToDocument(appid, data)));
	}

	@Override
	@Deprecated
	public void index(String appid, ParaObject po, long ttl) {
		index(appid, po);
	}

	@Override
	public void unindex(String appid, ParaObject po) {
		if (po == null || StringUtils.isBlank(po.getId()) || StringUtils.isBlank(appid)) {
			return;
		}
		unindexDocuments(appid, Collections.singletonList(po.getId()));
	}

	@Override
	public <P extends ParaObject> void indexAll(String appid, List<P> objects) {
		if (StringUtils.isBlank(appid) || objects == null || objects.isEmpty()) {
			return;
		}
		ArrayList<Document> docs = new ArrayList<Document>(objects.size());
		for (P po : objects) {
			Map<String, Object> data = ParaObjectUtils.getAnnotatedFields(po, null, false);
			if (!data.isEmpty()) {
				docs.add(paraObjectToDocument(appid, data));
			}
		}
		indexDocuments(appid, docs);
	}

	@Override
	public <P extends ParaObject> void unindexAll(String appid, List<P> objects) {
		if (StringUtils.isBlank(appid) || objects == null || objects.isEmpty()) {
			return;
		}
		ArrayList<String> ids = new ArrayList<String>();
		for (P po : objects) {
			if (po != null) {
				ids.add(po.getId());
			}
		}
		unindexDocuments(appid, ids);
	}

	@Override
	public void unindexAll(String appid, Map<String, ?> terms, boolean matchAll) {
		if (StringUtils.isBlank(appid)) {
			return;
		}
		Query q = (terms == null || terms.isEmpty()) ? new MatchAllDocsQuery() : getTermsQuery(terms, matchAll);
		unindexDocuments(appid, q);
	}

	@Override
	public <P extends ParaObject> P findById(String appid, String id) {
		if (StringUtils.isBlank(appid) || StringUtils.isBlank(id)) {
			return null;
		}
		List<P> results = searchQuery(dao, appid, null, new TermQuery(new Term(Config._ID, id)), new Pager(1));
		return results.isEmpty() ? null : results.get(0);
	}

	@Override
	public <P extends ParaObject> List<P> findByIds(String appid, List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return Collections.emptyList();
		}
		BooleanQuery.Builder fb = new BooleanQuery.Builder();
		for (String id : ids) {
			if (!StringUtils.isBlank(id)) {
				fb.add(new TermQuery(new Term(Config._ID, id)), BooleanClause.Occur.SHOULD);
			}
		}
		return searchQuery(dao, appid, null, fb.build(), new Pager(ids.size()));
	}

	@Override
	public <P extends ParaObject> List<P> findNearby(String appid, String type, String query,
			int radius, double lat, double lng, Pager... pager) {
		if (StringUtils.isBlank(type) || StringUtils.isBlank(appid)) {
			return Collections.emptyList();
		}
		if (StringUtils.isBlank(query)) {
			query = "*";
		}
		// searchQuery nearby Address objects (with radius in METERS)
		return searchGeoQuery(dao, appid, type, newDistanceQuery("latlng", lat, lng, radius * 1000), query, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findPrefix(String appid, String type, String field, String prefix,
			Pager... pager) {
		if (StringUtils.isBlank(field) || StringUtils.isBlank(prefix)) {
			return Collections.emptyList();
		}
		Query query = new PrefixQuery(new Term(field, prefix));
		return searchQuery(dao, appid, type, query, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findQuery(String appid, String type, String query, Pager... pager) {
		if (StringUtils.isBlank(query)) {
			return Collections.emptyList();
		}
		return searchQuery(dao, appid, type, query, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findNestedQuery(String appid, String type, String field, String query,
			Pager... pager) {
		if (StringUtils.isBlank(query) || StringUtils.isBlank(field)) {
			return Collections.emptyList();
		}
		return searchQuery(dao, appid, type, field.concat(":").concat(query), pager);
	}

	@Override
	public <P extends ParaObject> List<P> findSimilar(String appid, String type, String filterKey, String[] fields,
			String liketext, Pager... pager) {
		if (StringUtils.isBlank(liketext)) {
			return Collections.emptyList();
		}
		Query query;
		MoreLikeThisQuery q;
		if (fields == null || fields.length == 0) {
			q = new MoreLikeThisQuery(liketext, new String[]{Config._NAME}, LuceneUtils.ANALYZER, Config._NAME);
			q.setMinDocFreq(1);
			q.setMinTermFrequency(1);
		} else {
			q = new MoreLikeThisQuery(liketext, fields, LuceneUtils.ANALYZER, fields[0]);
			q.setMinDocFreq(1);
			q.setMinTermFrequency(1);
		}

		if (!StringUtils.isBlank(filterKey)) {
			query = new BooleanQuery.Builder().
						add(new TermQuery(new Term(Config._ID, filterKey)), BooleanClause.Occur.MUST_NOT).
						add(q, BooleanClause.Occur.FILTER).
						build();
			return searchQuery(dao, appid, type, query, pager);
		} else {
			return searchQuery(dao, appid, type, q, pager);
		}
	}

	@Override
	public <P extends ParaObject> List<P> findTagged(String appid, String type, String[] tags, Pager... pager) {
		if (tags == null || tags.length == 0 || StringUtils.isBlank(appid)) {
			return Collections.emptyList();
		}
		Builder query = new BooleanQuery.Builder();
		//assuming clean & safe tags here
		for (String tag : tags) {
			query.add(new TermQuery(new Term(Config._TAGS, tag)), BooleanClause.Occur.MUST);
		}
		// The filter looks like this: ("tag1" OR "tag2" OR "tag3") AND "type"
		return searchQuery(dao, appid, type, query.build(), pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTags(String appid, String keyword, Pager... pager) {
		if (StringUtils.isBlank(keyword)) {
			return Collections.emptyList();
		}
		Query query = new WildcardQuery(new Term("tag", keyword.concat("*")));
		return searchQuery(dao, appid, Utils.type(Tag.class), query, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTermInList(String appid, String type, String field,
			List<?> terms, Pager... pager) {
		if (StringUtils.isBlank(field) || terms == null) {
			return Collections.emptyList();
		}
		ArrayList<BytesRef> termsList = new ArrayList<BytesRef>();
		for (Object term : terms) {
			termsList.add(new BytesRef(term.toString()));
		}
		Query query = new TermInSetQuery(field, termsList);
		return searchQuery(dao, appid, type, query, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTerms(String appid, String type, Map<String, ?> terms,
			boolean mustMatchAll, Pager... pager) {
		if (terms == null || terms.isEmpty()) {
			return Collections.emptyList();
		}
		Query query = getTermsQuery(terms, mustMatchAll);

		if (query == null) {
			return Collections.emptyList();
		} else {
			return searchQuery(dao, appid, type, query, pager);
		}
	}

	@Override
	public <P extends ParaObject> List<P> findWildcard(String appid, String type, String field, String wildcard,
			Pager... pager) {
		if (StringUtils.isBlank(field) || StringUtils.isBlank(wildcard)) {
			return Collections.emptyList();
		}
		Query query = new WildcardQuery(new Term(field, wildcard));
		return searchQuery(dao, appid, type, query, pager);
	}

	@Override
	public Long getCount(String appid, String type) {
		if (StringUtils.isBlank(appid)) {
			return 0L;
		}
		Query query;
		if (!StringUtils.isBlank(type)) {
			query = new TermQuery(new Term(Config._TYPE, type));
		} else {
			query = new MatchAllDocsQuery();
		}
		return (long) count(appid, query);
	}

	@Override
	public Long getCount(String appid, String type, Map<String, ?> terms) {
		if (StringUtils.isBlank(appid) || terms == null || terms.isEmpty()) {
			return 0L;
		}
		Query query = getTermsQuery(terms, true);
		if (query != null && !StringUtils.isBlank(type)) {
			query = new BooleanQuery.Builder().
					add(query, BooleanClause.Occur.MUST).
					add(new TermQuery(new Term(Config._TYPE, type)), BooleanClause.Occur.FILTER).
					build();
		}
		return (long) count(appid, query);
	}

	//////////////////////////////////////////////////////////////

	@Override
	public void index(ParaObject so) {
		index(Config.APP_NAME_NS, so);
	}

	@Override
	public void unindex(ParaObject so) {
		unindex(Config.APP_NAME_NS, so);
	}

	@Override
	public <P extends ParaObject> void indexAll(List<P> objects) {
		indexAll(Config.APP_NAME_NS, objects);
	}

	@Override
	public <P extends ParaObject> void unindexAll(List<P> objects) {
		unindexAll(Config.APP_NAME_NS, objects);
	}

	@Override
	public void unindexAll(Map<String, ?> terms, boolean matchAll) {
		unindexAll(Config.APP_NAME_NS, terms, matchAll);
	}

	@Override
	public <P extends ParaObject> P findById(String id) {
		return findById(Config.APP_NAME_NS, id);
	}

	@Override
	public <P extends ParaObject> List<P> findByIds(List<String> ids) {
		return findByIds(Config.APP_NAME_NS, ids);
	}

	@Override
	public <P extends ParaObject> List<P> findNearby(String type,
			String query, int radius, double lat, double lng, Pager... pager) {
		return findNearby(Config.APP_NAME_NS, type, query, radius, lat, lng, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findPrefix(String type, String field, String prefix, Pager... pager) {
		return findPrefix(Config.APP_NAME_NS, type, field, prefix, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findQuery(String type, String query, Pager... pager) {
		return findQuery(Config.APP_NAME_NS, type, query, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findNestedQuery(String type, String field, String query, Pager... pager) {
		return findNestedQuery(Config.APP_NAME_NS, type, field, query, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findSimilar(String type, String filterKey, String[] fields,
			String liketext, Pager... pager) {
		return findSimilar(Config.APP_NAME_NS, type, filterKey, fields, liketext, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTagged(String type, String[] tags, Pager... pager) {
		return findTagged(Config.APP_NAME_NS, type, tags, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTags(String keyword, Pager... pager) {
		return findTags(Config.APP_NAME_NS, keyword, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTermInList(String type, String field,
			List<?> terms, Pager... pager) {
		return findTermInList(Config.APP_NAME_NS, type, field, terms, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findTerms(String type, Map<String, ?> terms,
			boolean mustMatchBoth, Pager... pager) {
		return findTerms(Config.APP_NAME_NS, type, terms, mustMatchBoth, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findWildcard(String type, String field, String wildcard,
			Pager... pager) {
		return findWildcard(Config.APP_NAME_NS, type, field, wildcard, pager);
	}

	@Override
	public Long getCount(String type) {
		return getCount(Config.APP_NAME_NS, type);
	}

	@Override
	public Long getCount(String type, Map<String, ?> terms) {
		return getCount(Config.APP_NAME_NS, type, terms);
	}

}
