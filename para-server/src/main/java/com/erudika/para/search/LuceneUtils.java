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

import com.erudika.para.core.Address;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.persistence.DAO;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.analysis.bg.BulgarianAnalyzer;
import org.apache.lucene.analysis.br.BrazilianAnalyzer;
import org.apache.lucene.analysis.ca.CatalanAnalyzer;
import org.apache.lucene.analysis.cz.CzechAnalyzer;
import org.apache.lucene.analysis.da.DanishAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.el.GreekAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.eu.BasqueAnalyzer;
import org.apache.lucene.analysis.fa.PersianAnalyzer;
import org.apache.lucene.analysis.fi.FinnishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.gl.GalicianAnalyzer;
import org.apache.lucene.analysis.hi.HindiAnalyzer;
import org.apache.lucene.analysis.hu.HungarianAnalyzer;
import org.apache.lucene.analysis.hy.ArmenianAnalyzer;
import org.apache.lucene.analysis.id.IndonesianAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.no.NorwegianAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.sv.SwedishAnalyzer;
import org.apache.lucene.analysis.tr.TurkishAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldCollector;
import org.apache.lucene.spatial.geopoint.document.GeoPointField;
import org.apache.lucene.spatial.geopoint.search.GeoPointDistanceQuery;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lucene related utility methods.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class LuceneUtils {

	private static final Logger logger = LoggerFactory.getLogger(LuceneUtils.class);
	private static final String SOURCE_FIELD_NAME = "_source";
	private static final String NESTED_FIELD_NAME = "nstd";
	private static final FieldType SOURCE_FIELD;
	private static final FieldType DEFAULT_FIELD;
	private static final Set<String> NOT_ANALYZED_FIELDS;
	private static final CharArraySet STOPWORDS;
	private static final Analyzer ANALYZER;

	static {
		SOURCE_FIELD = new FieldType();
		SOURCE_FIELD.setIndexOptions(IndexOptions.NONE);
		SOURCE_FIELD.setStored(true);
		SOURCE_FIELD.setTokenized(false);

		DEFAULT_FIELD = new FieldType();
		DEFAULT_FIELD.setStored(false);
		DEFAULT_FIELD.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
		DEFAULT_FIELD.setTokenized(true);
		DEFAULT_FIELD.setStoreTermVectorPayloads(true);
		DEFAULT_FIELD.setStoreTermVectorPositions(true);
		DEFAULT_FIELD.setStoreTermVectorOffsets(true);
		DEFAULT_FIELD.setStoreTermVectors(true);
		DEFAULT_FIELD.setOmitNorms(false);
		DEFAULT_FIELD.setDocValuesType(DocValuesType.NONE);

		STOPWORDS = CharArraySet.copy(EnglishAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(ArabicAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(ArmenianAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(BasqueAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(BrazilianAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(BulgarianAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(CatalanAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(CzechAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(DanishAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(DutchAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(FinnishAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(FrenchAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(GalicianAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(GermanAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(GreekAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(HindiAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(HungarianAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(IndonesianAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(ItalianAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(NorwegianAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(PersianAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(PortugueseAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(RomanianAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(RussianAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(SpanishAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(SwedishAnalyzer.getDefaultStopSet());
		STOPWORDS.addAll(TurkishAnalyzer.getDefaultStopSet());
		ANALYZER = new StandardAnalyzer(STOPWORDS);

		NOT_ANALYZED_FIELDS = new HashSet<String>();
		NOT_ANALYZED_FIELDS.add("nstd");
		NOT_ANALYZED_FIELDS.add("latlng");
		NOT_ANALYZED_FIELDS.add("tag");
		NOT_ANALYZED_FIELDS.add("id");
		NOT_ANALYZED_FIELDS.add("key");
//		NOT_ANALYZED_FIELDS.add("name");
		NOT_ANALYZED_FIELDS.add("type");
		NOT_ANALYZED_FIELDS.add("tags");
		NOT_ANALYZED_FIELDS.add("email");
		NOT_ANALYZED_FIELDS.add("appid");
		NOT_ANALYZED_FIELDS.add("groups");
		NOT_ANALYZED_FIELDS.add("updated");
		NOT_ANALYZED_FIELDS.add("password");
		NOT_ANALYZED_FIELDS.add("parentid");
		NOT_ANALYZED_FIELDS.add("creatorid");
		NOT_ANALYZED_FIELDS.add("timestamp");
		NOT_ANALYZED_FIELDS.add("identifier");
		NOT_ANALYZED_FIELDS.add("token");
	}

	private LuceneUtils() { }

	/**
	 * Indexes documents.
	 * @param appid appid
	 * @param docs a list of documents
	 */
	public static void indexDocuments(String appid, List<Document> docs) {
		if (docs.isEmpty()) {
			return;
		}
		IndexWriter iwriter = null;
		try {
			iwriter = getIndexWriter(appid);
			if (iwriter != null) {
				// Optional: for better indexing performance, if you
				// are indexing many documents, increase the RAM
				// buffer.  But if you do this, increase the max heap
				// size to the JVM (eg add -Xmx512m or -Xmx1g):
				// iwc.setRAMBufferSizeMB(256.0);
				for (Document doc : docs) {
					if (doc.get(Config._ID) != null) {
						iwriter.updateDocument(new Term(Config._ID, doc.get(Config._ID)), doc);
					}
				}
				iwriter.commit();
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		} finally {
			closeIndexWriter(iwriter);
		}
	}

	/**
	 * Removes documents from Lucene index.
	 * @param appid appid
	 * @param ids a list of ids to remove
	 */
	public static void unindexDocuments(String appid, List<String> ids) {
		if (ids.isEmpty()) {
			return;
		}
		IndexWriter iwriter = null;
		try {
			iwriter = getIndexWriter(appid);
			if (iwriter != null) {
				ArrayList<Term> keys = new ArrayList<Term>();
				for (String id : ids) {
					if (id != null) {
						keys.add(new Term(Config._ID, id));
					}
				}
				iwriter.deleteDocuments(keys.toArray(new Term[0]));
				iwriter.commit();
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		} finally {
			closeIndexWriter(iwriter);
		}
	}

	/**
	 * Removes documents from Lucene index, matching a query.
	 * @param appid appid
	 * @param query queries which documents to remove
	 */
	public static void unindexDocuments(String appid, Query query) {
		if (query == null) {
			return;
		}
		IndexWriter iwriter = null;
		try {
			iwriter = getIndexWriter(appid);
			if (iwriter != null) {
				iwriter.deleteDocuments(query);
				iwriter.commit();
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		} finally {
			closeIndexWriter(iwriter);
		}
	}

	private static void addDocumentFields(JsonNode object, Document doc, String prefix) {
		try {
			for (Iterator<Map.Entry<String, JsonNode>> iterator = object.fields(); iterator.hasNext();) {
				Map.Entry<String, JsonNode> entry = iterator.next();
				String pre = (StringUtils.isBlank(prefix) ? "" : prefix + ".");
				String field = pre + entry.getKey();
				JsonNode value = entry.getValue();
				if (value != null) {
					switch (value.getNodeType()) {
						case OBJECT:
							addDocumentFields(value, doc, pre + field);
							break;
						case ARRAY:
							StringBuilder sb = new StringBuilder();
							for (Iterator<JsonNode> iterator1 = value.elements(); iterator1.hasNext();) {
								String val = iterator1.next().asText();
								if (!StringUtils.isBlank(val)) {
									if (sb.length() > 0) {
										sb.append(",");
									}
									sb.append(val);
									doc.add(getField(field, val));
								}
							}
							if (sb.length() > 0) {
								doc.add(new SortedDocValuesField(field, new BytesRef(sb.toString())));
							}
							break;
						default:
							String val = value.asText("null");
							Field f = getField(field, val);
							if (!(f instanceof GeoPointField)) {
								doc.add(new SortedDocValuesField(field, new BytesRef(val)));
							}
							doc.add(f);
							break;
					}
				}
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	private static Field getField(String field, String value) {
		if ("latlng".equals(field) && StringUtils.contains(value, ",")) {
			String[] latlng = value.split(",", 2);
			return new GeoPointField(field, NumberUtils.toDouble(latlng[1]),
					NumberUtils.toDouble(latlng[0]), Field.Store.NO);
		} else {
			if (NOT_ANALYZED_FIELDS.contains(field)) {
				return new StringField(field, value, Field.Store.NO);
			} else {
				return new Field(field, value, DEFAULT_FIELD);
			}
		}
	}

	/**
	 * Converts a ParaObject to Lucene Document. Takes care of nested objects and indexes them separately.
	 * Stores the original object data as JSON text inside the "_source" field.
	 * @param appid an appid
	 * @param data object data - keys and values
	 * @return a {@link Document} object
	 */
	@SuppressWarnings("unchecked")
	public static Document paraObjectToDocument(String appid, Map<String, Object> data) {
		Document doc = new Document();
		JsonNode jsonDoc = null;
		try {
			// Process nested fields first
			// Nested objects are stored as independent documents in Lucene.
			// They are not shown in search results.
			if (data != null && data.containsKey(NESTED_FIELD_NAME)) {
				Object nstd = data.get(NESTED_FIELD_NAME);
				if (nstd instanceof List) {
					LinkedList<Document> docs = new LinkedList<Document>();
					Map<String, Object> dataWithoutNestedField = new HashMap<String, Object>(data);
					dataWithoutNestedField.remove(NESTED_FIELD_NAME);
					jsonDoc = ParaObjectUtils.getJsonMapper().valueToTree(dataWithoutNestedField);
					for (Map<String, Object> obj : ((List<Map<String, Object>>) nstd)) {
						Map<String, Object> object = new HashMap<String, Object>(obj);
						object.put(Config._ID, Utils.getNewId());
						// the nested object's type is forced to be equal to its parent, otherwise breaks queries
						object.put(Config._TYPE, data.get(Config._TYPE));
						JsonNode nestedJsonDoc = ParaObjectUtils.getJsonMapper().valueToTree(object);
						Document nestedDoc = new Document();
						addDocumentFields(nestedJsonDoc, nestedDoc, "");
						addSource(jsonDoc, nestedDoc); // nested field has the source of its parent
						docs.add(nestedDoc);
					}
					indexDocuments(appid, docs);
				}
			} else {
				jsonDoc = ParaObjectUtils.getJsonMapper().valueToTree(data);
			}
			addDocumentFields(jsonDoc, doc, "");
			addSource(jsonDoc, doc);
		} catch (Exception e) {
			logger.error(null, e);
		}
		return doc;
	}

	private static void addSource(JsonNode jsonDoc, Document doc) {
		try {
			doc.add(new Field(LuceneUtils.SOURCE_FIELD_NAME,
					ParaObjectUtils.getJsonWriterNoIdent().writeValueAsString(jsonDoc), SOURCE_FIELD));
		} catch (JsonProcessingException ex) {
			logger.error(null, ex);
		}
	}

	/**
	 * Reads the JSON from "_source" field of a document and turns it into a ParaObject.
	 * @param <P> type
	 * @param doc Lucene document
	 * @return a ParaObject or null if source is missing
	 */
	public static <P extends ParaObject> P documentToParaObject(Document doc) {
		if (doc == null || StringUtils.isBlank(doc.get(SOURCE_FIELD_NAME))) {
			return null;
		}
		return ParaObjectUtils.fromJSON(doc.get(SOURCE_FIELD_NAME));
	}

	private static <P extends ParaObject> void readObjectFromIndex(Document hit, ArrayList<P> results,
			Map<String, String> keysAndSources, Pager pager) {
		P result = documentToParaObject(hit);
		if (result != null) {
			if (keysAndSources.containsKey(result.getId())) {
				pager.setCount(pager.getCount() - 1); // substract duplicates due to nested objects (nstd)
			} else {
				results.add(result);
			}
			keysAndSources.put(result.getId(), hit.get(SOURCE_FIELD_NAME));
			logger.debug("Search result from index: appid={}, id={}", result.getAppid(), result.getId());
		}
	}

	/**
	 * Geopoint distance query. Finds objects located near a center point.
	 * @param <P> object type
	 * @param dao {@link DAO}
	 * @param appid appid
	 * @param type object type to search for
	 * @param query a geopoint query
	 * @param queryString query string for filtering results
	 * @param pager a {@link Pager}
	 * @return a list of ParaObjects
	 */
	public static <P extends ParaObject> List<P> searchGeoQuery(DAO dao, String appid, String type,
			GeoPointDistanceQuery query, String queryString, Pager... pager) {
		if (StringUtils.isBlank(type) || StringUtils.isBlank(appid)) {
			return Collections.emptyList();
		}
		if (StringUtils.isBlank(queryString)) {
			queryString = "*";
		}
		DirectoryReader ireader = null;
		try {
			Pager page = getPager(pager);
			ireader = getIndexReader(appid);
			if (ireader != null) {
				Document[] hits1 = searchQueryRaw(ireader, appid, Utils.type(Address.class), query, page);

				if (hits1.length == 0) {
					return Collections.emptyList();
				}

				if (type.equals(Utils.type(Address.class))) {
					return searchQuery(dao, appid, hits1, page);
				}

				// then searchQuery their parent objects
				ArrayList<String> parentids = new ArrayList<String>(hits1.length);
				for (Document doc : hits1) {
					String pid = doc.get(Config._PARENTID);
					if (!StringUtils.isBlank(pid)) {
						parentids.add(pid);
					}
				}

				Builder qb2 = new BooleanQuery.Builder();
				qb2.add(qs(queryString, MultiFields.getIndexedFields(ireader)), BooleanClause.Occur.MUST);
				for (String id : parentids) {
					qb2.add(new TermQuery(new Term(Config._ID, id)), BooleanClause.Occur.SHOULD);
				}
				Document[] hits2 = searchQueryRaw(ireader, appid, type, qb2.build(), page);
				return searchQuery(dao, appid, hits2, page);
			}
		} catch (Exception e) {
			logger.error(null, e);
		} finally {
			closeIndexReader(ireader);
		}
		return Collections.emptyList();
	}

	/**
	 * Searches the Lucene index of a particular appid.
	 * @param <P> type
	 * @param dao {@link DAO}
	 * @param appid appid
	 * @param type type
	 * @param query a query
	 * @param pager a {@link Pager}
	 * @return a list of ParaObjects
	 */
	public static <P extends ParaObject> List<P> searchQuery(DAO dao, String appid, String type, String query, Pager... pager) {
		if (StringUtils.isBlank(appid)) {
			return Collections.emptyList();
		}
		DirectoryReader ireader = null;
		try {
			ireader = getIndexReader(appid);
			if (ireader != null) {
				Pager page = getPager(pager);
				List<P> docs = searchQuery(dao, appid, searchQueryRaw(ireader, appid, type,
						qs(query, MultiFields.getIndexedFields(ireader)), page), page);
				return docs;
			}
		} catch (Exception e) {
			logger.error(null, e);
		} finally {
			closeIndexReader(ireader);
		}
		return Collections.emptyList();
	}

	/**
	 * Searches the Lucene index of a particular appid.
	 * @param <P> type
	 * @param dao {@link DAO}
	 * @param appid appid
	 * @param type type
	 * @param query a query
	 * @param pager a {@link Pager}
	 * @return a list of ParaObjects
	 */
	public static <P extends ParaObject> List<P> searchQuery(DAO dao, String appid, String type, Query query, Pager... pager) {
		if (StringUtils.isBlank(appid)) {
			return Collections.emptyList();
		}
		DirectoryReader ireader = null;
		try {
			ireader = getIndexReader(appid);
			if (ireader != null) {
				Pager page = getPager(pager);
				List<P> docs = searchQuery(dao, appid, searchQueryRaw(ireader, appid, type, query, page), page);
				return docs;
			}
		} catch (Exception e) {
			logger.error(null, e);
		} finally {
			closeIndexReader(ireader);
		}
		return Collections.emptyList();
	}

	@SuppressWarnings("unchecked")
	private static <P extends ParaObject> List<P> searchQuery(DAO dao, String appid, Document[] hits, Pager pager) {
		if (hits == null || hits.length == 0) {
			return Collections.emptyList();
		}
		ArrayList<P> results = new ArrayList<P>(hits.length);
		LinkedHashMap<String, String> keysAndSources = new LinkedHashMap<String, String>(hits.length);
		try {
			boolean readFromIndex = Config.getConfigBoolean("read_from_index", Config.ENVIRONMENT.equals("embedded"));
			for (Document hit : hits) {
				if (readFromIndex) {
					readObjectFromIndex(hit, results, keysAndSources, pager);
				}
			}

			if (!readFromIndex && !keysAndSources.isEmpty()) {
				ArrayList<String> nullz = new ArrayList<String>(results.size());
				Map<String, P> fromDB = dao.readAll(appid, new ArrayList(keysAndSources.keySet()), true);
				for (Map.Entry<String, String> entry : keysAndSources.entrySet()) {
					String key = entry.getKey();
					P pobj = fromDB.get(key);
					if (pobj == null) {
						pobj = ParaObjectUtils.fromJSON(entry.getValue());
						// object is still in index but not in DB
						if (pobj != null && appid.equals(pobj.getAppid()) && pobj.getStored()) {
							nullz.add(key);
						}
					}
					if (pobj != null) {
						results.add(pobj);
					}
				}

				if (!nullz.isEmpty()) {
					logger.warn("Found {} objects that are indexed but not in the database: {}",
							nullz.size(), nullz);
				}
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
		return results;
	}

	private static Document[] searchQueryRaw(DirectoryReader ireader, String appid, String type, Query query, Pager pager) {
		if (StringUtils.isBlank(appid) || ireader == null) {
			return new Document[0];
		}
		if (query == null) {
			query = new MatchAllDocsQuery();
		}
		if (pager == null) {
			pager = new Pager();
		}
		try {
			IndexSearcher isearcher = new IndexSearcher(ireader);
			if (!StringUtils.isBlank(type)) {
				query = new BooleanQuery.Builder().
						add(query, BooleanClause.Occur.MUST).
						add(new TermQuery(new Term(Config._TYPE, type)), BooleanClause.Occur.FILTER).
						build();
			}
			int maxPerPage = pager.getLimit();
			int pageNum = (int) pager.getPage();
			int start = (pageNum < 1 || pageNum > Config.MAX_PAGES) ? 0 : (pageNum - 1) * maxPerPage;
			Sort sort = new Sort(new SortField(pager.getSortby(), SortField.Type.STRING, pager.isDesc()));

			TopFieldCollector collector = TopFieldCollector.create(sort, Config.DEFAULT_LIMIT, false, false, false);
			isearcher.search(query, collector);

			TopDocs topDocs = collector.topDocs(start, maxPerPage);
			ScoreDoc[] hits = topDocs.scoreDocs;
			pager.setCount(topDocs.totalHits);

			Document[] docs = new Document[hits.length];
			for (int i = 0; i < hits.length; i++) {
				docs[i] = isearcher.doc(hits[i].doc);
			}
			logger.debug("Lucene query: {} Hits: {}, Total: {}", query.toString(), hits.length, topDocs.totalHits);
			return docs;
		} catch (Exception e) {
			Throwable cause = e.getCause();
			String msg = cause != null ? cause.getMessage() : e.getMessage();
			logger.warn("No search results for type '{}' in app '{}': {}.", type, appid, msg);
		}
		return new Document[0];
	}

	/**
	 * Counts the total number of documents for a given query.
	 * @param appid appid
	 * @param query a query
	 * @return total docs found in index
	 */
	public static int count(String appid, Query query) {
		if (StringUtils.isBlank(appid) || query == null) {
			return 0;
		}
		DirectoryReader ireader = null;
		try {
			ireader = getIndexReader(appid);
			if (ireader != null) {
				IndexSearcher isearcher = new IndexSearcher(ireader);
				return isearcher.count(query);
			}
		} catch (Exception e) {
			logger.error(null, e);
		} finally {
			closeIndexReader(ireader);
		}
		return 0;
	}

	private static DirectoryReader getIndexReader(String appid) {
		String dataDir = Config.getConfigParam("lucene.dir", Paths.get(".").toAbsolutePath().normalize().toString());
		Path path = FileSystems.getDefault().getPath(dataDir, "data", getIndexName(appid));
		try {
			return DirectoryReader.open(FSDirectory.open(path));
		} catch (IOException ex) {
			logger.warn("Index '{}' does not exist.", getIndexName(appid));
		}
		return null;
	}

	private static IndexWriter getIndexWriter(String appid) {
		// Directory directory = new RAMDirectory(); // Store the index in memory:
		String dataDir = Config.getConfigParam("lucene.dir", Paths.get(".").toAbsolutePath().normalize().toString());
		Path path = FileSystems.getDefault().getPath(dataDir, "data", getIndexName(appid));
		try {
			Analyzer analyzer = new StandardAnalyzer();
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
			return new IndexWriter(FSDirectory.open(path), config);
		} catch (IOException ex) {
			logger.warn("Index '{}' does not exist.", getIndexName(appid));
		}
		return null;
	}

	private static void closeIndexReader(DirectoryReader ireader) {
		try {
			if (ireader != null) {
				ireader.close();
				if (ireader.directory() != null) {
					ireader.directory().close();
				}
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	private static void closeIndexWriter(IndexWriter iwriter) {
		try {
			if (iwriter != null) {
				iwriter.close();
				if (iwriter.getDirectory() != null) {
					iwriter.getDirectory().close();
				}
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	/**
	 * Creates a term filter for a set of terms.
	 * @param terms some terms
	 * @param mustMatchAll if true all terms must match ('AND' operation)
	 * @return the filter
	 */
	static Query getTermsQuery(Map<String, ?> terms, boolean mustMatchAll) {
		BooleanQuery.Builder fb = new BooleanQuery.Builder();
		int addedTerms = 0;
		boolean noop = true;
		Query bfb = null;

		for (Map.Entry<String, ?> term : terms.entrySet()) {
			Object val = term.getValue();
			if (!StringUtils.isBlank(term.getKey()) && val != null) {
				Matcher matcher = Pattern.compile(".*(<|>|<=|>=)$").matcher(term.getKey().trim());
				bfb = new TermQuery(new Term(term.getKey(), val.toString()));
				if (matcher.matches() && val instanceof Number) {
					String key = term.getKey().replaceAll("[<>=\\s]+$", "");

					if (">".equals(matcher.group(1))) {
						bfb = TermRangeQuery.newStringRange(key, val.toString(), null, false, false);
					} else if ("<".equals(matcher.group(1))) {
						bfb = TermRangeQuery.newStringRange(key, null, val.toString(), false, false);
					} else if (">=".equals(matcher.group(1))) {
						bfb = TermRangeQuery.newStringRange(key, val.toString(), null, true, false);
					} else if ("<=".equals(matcher.group(1))) {
						bfb = TermRangeQuery.newStringRange(key, null, val.toString(), false, true);
					}
				}
				if (mustMatchAll) {
					fb.add(bfb, BooleanClause.Occur.MUST);
				} else {
					fb.add(bfb, BooleanClause.Occur.SHOULD);
				}
				addedTerms++;
				noop = false;
			}
		}
		if (addedTerms == 1 && bfb != null) {
			return bfb;
		}
		return noop ? null : fb.build();
	}

	/**
	 * Tries to parse a query string in order to check if it is valid.
	 * @param query a Lucene query string
	 * @return the query if valid, or '*' if invalid
	 */
	static Query qs(String query, Collection<String> fields) {
		if (fields == null || fields.isEmpty()) {
			fields = Collections.singletonList(Config._NAME);
		}
		if (StringUtils.isBlank(query)) {
			query = "*";
		}
		MultiFieldQueryParser parser = new MultiFieldQueryParser(
				fields.toArray(new String[0]), new StandardAnalyzer());
		parser.setAllowLeadingWildcard(false);
		parser.setLowercaseExpandedTerms(true);
		parser.setAnalyzer(ANALYZER);
		query = query.trim();
		if (query.length() > 1 && query.startsWith("*")) {
			query = query.substring(1);
		}
		if (query.length() > 1 && query.contains(" *")) {
			query = query.replaceAll("\\s\\*", " ").trim();
		}
		if (query.length() >= 2 && query.toLowerCase().endsWith("and") ||
				query.toLowerCase().endsWith("or") || query.toLowerCase().endsWith("not")) {
			query = query.substring(0, query.length() - (query.toLowerCase().endsWith("or") ? 2 : 3));
		}
		try {
			Query q = parser.parse(query);
			return q;
		} catch (Exception ex) { }
		return new MatchAllDocsQuery();
	}

	static Pager getPager(Pager[] pager) {
		return (pager != null && pager.length > 0) ? pager[0] : new Pager();
	}

	/**
	 * A method reserved for future use. It allows to have indexes with different names than the appid.
	 *
	 * @param appid an app identifer
	 * @return the correct index name
	 */
	static String getIndexName(String appid) {
		return appid + "-lucene";
	}

}
