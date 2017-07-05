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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.index.mapper.core.StringFieldMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lucene related utility methods.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class LuceneUtils {

	private static final Logger logger = LoggerFactory.getLogger(LuceneUtils.class);
	private static final String SOURCE_FIELD_NAME = "_source";
	private static final String ID_FIELD_NAME = "_id"; // id field of the parent of a nested object (nstd)
	private static final String NESTED_FIELD_NAME = "nstd";
//	private static final FieldType INT_FIELD;
//	private static final FieldType LONG_FIELD;
//	private static final FieldType DOUBLE_FIELD;
//	private static final FieldType SORTED_FIELD;
	private static final FieldType SOURCE_FIELD;
	private static final FieldType DEFAULT_FIELD;
	private static final Set<String> NOT_ANALYZED_FIELDS;

	static {
//		SORTED_FIELD = new StringFieldMapper.StringFieldType();
//		SORTED_FIELD.setTokenized(true);
//		SORTED_FIELD.setStored(true);
//		SORTED_FIELD.setDocValuesType(DocValuesType.SORTED);

		SOURCE_FIELD = new StringFieldMapper.StringFieldType();
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

//		INT_FIELD = new FieldType();
//		INT_FIELD.setStored(true);
//		INT_FIELD.setTokenized(false);
//		INT_FIELD.setDocValuesType(DocValuesType.SORTED_NUMERIC);
//		INT_FIELD.setNumericType(FieldType.NumericType.INT);
//		LONG_FIELD = new FieldType();
//		LONG_FIELD.setStored(true);
//		LONG_FIELD.setTokenized(false);
//		LONG_FIELD.setDocValuesType(DocValuesType.SORTED_NUMERIC);
//		LONG_FIELD.setNumericType(FieldType.NumericType.LONG);
//		DOUBLE_FIELD = new FieldType();
//		DOUBLE_FIELD.setStored(true);
//		DOUBLE_FIELD.setTokenized(false);
//		DOUBLE_FIELD.setDocValuesType(DocValuesType.SORTED_NUMERIC);
//		DOUBLE_FIELD.setNumericType(FieldType.NumericType.DOUBLE);

		NOT_ANALYZED_FIELDS = new HashSet<String>() {
			{
				add("nstd");
				add("latlng");
				add("tag");
				add("id");
				add("key");
//				add("name");
				add("type");
				add("tags");
				add("email");
				add("appid");
				add("groups");
				add("updated");
				add("password");
				add("parentid");
				add("creatorid");
				add("timestamp");
				add("identifier");
				add("token");
			}
		};
	}

	private LuceneUtils() { }

	static void indexDocuments(String appid, List<Document> docs) {
		if (docs.isEmpty()) {
			return;
		}
		try {
			IndexWriter iwriter = getIndexWriter(appid);
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
				iwriter.close();
				iwriter.getDirectory().close();
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}
	}

	static void unindexDocuments(String appid, List<String> ids) {
		if (ids.isEmpty()) {
			return;
		}
		try {
			IndexWriter iwriter = getIndexWriter(appid);
			if (iwriter != null) {
				ArrayList<Term> keys = new ArrayList<Term>();
				for (String id : ids) {
					if (id != null) {
						keys.add(new Term(Config._ID, id));
					}
				}
				iwriter.deleteDocuments(keys.toArray(new Term[0]));
				iwriter.commit();
				iwriter.close();
				iwriter.getDirectory().close();
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}
	}

	static void unindexDocuments(String appid, Query query) {
		if (query == null) {
			return;
		}
		try {
			IndexWriter iwriter = getIndexWriter(appid);
			if (iwriter != null) {
				iwriter.deleteDocuments(query);
				iwriter.commit();
				iwriter.close();
				iwriter.getDirectory().close();
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}
	}

	static void addDocumentFields(JsonNode object, Document doc, String prefix) {
		try {
//			if (object.isValueNode()) {
//				String value = object.asText("null");
//				doc.add(new SortedDocValuesField(prefix, new BytesRef(value)));
//				doc.add(new StringField(prefix, value, Field.Store.NO));
//			} else {
				for (Iterator<Map.Entry<String, JsonNode>> iterator = object.fields(); iterator.hasNext();) {
					Map.Entry<String, JsonNode> entry = iterator.next();
					String pre = (StringUtils.isBlank(prefix) ? "" : prefix + ".");
					String field = pre + entry.getKey();
					JsonNode value = entry.getValue();
					if (value != null) {
	//					logger.info(">> {} {} {}", field, value, value.getNodeType());
						switch (value.getNodeType()) {
		//					case STRING:
		//						doc.add(new StringField(field, value.textValue(), Field.Store.NO));
		//						break;
		//					case BOOLEAN:
		//						doc.add(new StringField(field, Boolean.toString(value.booleanValue()), Field.Store.NO));
		//						break;
		//					case NUMBER:
		//						switch (value.numberType()) {
		//							case LONG:
		//								doc.add(new LongField(field, value.longValue(), LONG_FIELD));
		//								break;
		//							case INT:
		//								doc.add(new IntField(field, value.intValue(), INT_FIELD));
		//								break;
		//							case BIG_DECIMAL:
		//							case BIG_INTEGER:
		//							case FLOAT:
		//							case DOUBLE:
		//								doc.add(new DoubleField(field, value.doubleValue(), DOUBLE_FIELD));
		//
		//						}
		//						break;
							case OBJECT:
								addDocumentFields(value, doc, pre + field);
								break;
							case ARRAY:
								StringBuilder sb = new StringBuilder();
								for (Iterator<JsonNode> iterator1 = value.elements(); iterator1.hasNext();) {
//									addDocumentFields(iterator1.next(), doc, field);
									String val = iterator1.next().asText();
									if (!StringUtils.isBlank(val)) {
										if (sb.length() > 0) {
											sb.append(",");
										}
										sb.append(val);
//										if (NOT_ANALYZED_FIELDS.contains(field)) {
											doc.add(new StringField(field, val, Field.Store.NO));
//										} else {
//											doc.add(new Field(field, value.asText("null"), DEFAULT_FIELD));
//										}
									}
								}
								if (sb.length() > 0) {
									doc.add(new SortedDocValuesField(field, new BytesRef(sb.toString())));
								}
								break;
		//					case NULL:
		//						doc.add(new StringField(field, "null", Field.Store.NO));
		//						break;
							default:
								doc.add(new SortedDocValuesField(field, new BytesRef(value.asText("null"))));
								if (NOT_ANALYZED_FIELDS.contains(field)) {
									doc.add(new StringField(field, value.asText("null"), Field.Store.NO));
								} else {
									doc.add(new Field(field, value.asText("null"), DEFAULT_FIELD));
								}
								break;
						}
					}
//				}
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
	}

	@SuppressWarnings("unchecked")
	static Document paraObjectToDocument(String appid, Map<String, Object> data) {
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
						object.put(ID_FIELD_NAME, data.get(Config._ID));
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

	static void addSource(JsonNode jsonDoc, Document doc) {
		try {
			doc.add(new Field(LuceneUtils.SOURCE_FIELD_NAME,
					ParaObjectUtils.getJsonWriterNoIdent().writeValueAsString(jsonDoc), SOURCE_FIELD));
		} catch (JsonProcessingException ex) {
			logger.error(null, ex);
		}
	}

	static <P extends ParaObject> P documentToParaObject(Document doc) {
		if (doc == null || StringUtils.isBlank(doc.get(SOURCE_FIELD_NAME))) {
			return null;
		}
		return ParaObjectUtils.fromJSON(doc.get(SOURCE_FIELD_NAME));
//
//		Map<String, Object> data = new HashMap<String, Object>();
//		for (IndexableField field : doc) {
//			if (field.name().contains(".")) {
//				Object nested = data.get(field.name());
//
//
//			} else {
//				Object array = data.get(field.name());
//				if (array instanceof List) {
//					((List) array).add(field.stringValue());
//				} else if (array != null) {
//					ArrayList<String> list = new ArrayList<String>();
//					list.add((String) array);
//					list.add(field.stringValue());
//					data.put(field.name(), list);
//				} else {
//					data.put(field.name(), field.stringValue());
//				}
//			}
//		}
//		return data;
	}

//	static <P extends ParaObject> P findOne(String appid, String id) {
//		if (StringUtils.isBlank(appid) || StringUtils.isBlank(id)) {
//			return null;
//		}
//		try {
////			Analyzer analyzer = new StandardAnalyzer();
//			DirectoryReader ireader = getIndexReader(appid);
//			if (ireader != null) {
//				IndexSearcher isearcher = new IndexSearcher(ireader);
//				// Parse a simple query that searches for "text":
//	//			QueryParser parser = new QueryParser("id", analyzer);
//	//			StandardQueryParser parser = new StandardQueryParser(analyzer);
//				Query query = new TermQuery(new Term(Config._ID, id));
//	//			Query query = parser.parse("id:DAA or name:sys", "id");
//				ScoreDoc[] hits = isearcher.search(query, 1).scoreDocs;
//				logger.info("FOUND {}", hits.length);
//
//				Document hit = null;
//				if (hits.length > 0) {
//					hit = isearcher.doc(hits[0].doc);
//				}
//				ireader.close();
//				ireader.directory().close();
//				if (hit != null) {
//					return ParaObjectUtils.setAnnotatedFields(documentToParaObject(hit));
//				}
//			}
//		} catch (Exception e) {
//			logger.error(null, e);
//		}
//		return null;
//	}

	static String getDocumentIdField(Document doc) {
		String id = doc.get(ID_FIELD_NAME);
		if (StringUtils.isBlank(id)) {
			return doc.get(Config._ID);
		} else {
			return id;
		}
	}

	@SuppressWarnings("unchecked")
	static <P extends ParaObject> List<P> find(DAO dao, String appid, String type, String query, Pager... pager) {
		try {
			DirectoryReader ireader = getIndexReader(appid);
			if (ireader != null) {
				return find(dao, appid, type, qs(query, MultiFields.getIndexedFields(ireader)), pager);
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
		return Collections.emptyList();
	}

	@SuppressWarnings("unchecked")
	static <P extends ParaObject> List<P> find(DAO dao, String appid, String type, Query query, Pager... pager) {
		if (StringUtils.isBlank(appid) || query == null) {
			return Collections.emptyList();
		}
		Pager page = getPager(pager);
		ArrayList<P> results = new ArrayList<P>(page.getLimit());
		LinkedHashMap<String, String> keysAndSources = new LinkedHashMap<String, String>(page.getLimit());
		boolean readFromIndex = Config.getConfigBoolean("read_from_index", Config.ENVIRONMENT.equals("embedded"));
		try {
			DirectoryReader ireader = getIndexReader(appid);
			if (ireader != null) {
				IndexSearcher isearcher = new IndexSearcher(ireader);

				ScoreDoc[] hits1 = isearcher.search(query, page.getLimit()).scoreDocs;
				logger.info("FOUND MANY BEFORE {}", hits1.length);

				if (!StringUtils.isBlank(type)) {
					query = new BooleanQuery.Builder().
							add(query, BooleanClause.Occur.MUST).
							add(new TermQuery(new Term(Config._TYPE, type)), BooleanClause.Occur.FILTER).
							build();
				}

				TopDocs res = isearcher.search(query, page.getLimit(),
							new Sort(new SortField(page.getSortby(), SortField.Type.STRING, page.isDesc())));
//				TopDocs res = isearcher.search(query, page.getLimit());
				ScoreDoc[] hits = res.scoreDocs;
				int totalHits = res.totalHits;
				logger.info("FOUND MANY {}", hits.length);

				for (ScoreDoc doc : hits) {
					Document hit = isearcher.doc(doc.doc);
					if (readFromIndex) {
						P result = documentToParaObject(hit);
						if (result != null) {
							if (keysAndSources.containsKey(result.getId())) {
								totalHits--; // substract duplicates due to nested objects (nstd)
							} else {
								results.add(result);
							}
						}
					}
					keysAndSources.put(getDocumentIdField(hit), hit.get(SOURCE_FIELD_NAME));
					logger.debug("Search result: appid={}, {}->{}", appid, hit.get(Config._APPID), hit.get(Config._ID));
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


				page.setCount(totalHits);
				ireader.close();
				ireader.directory().close();
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
		return results;
	}

	static int count(String appid, Query query) {
		if (StringUtils.isBlank(appid) || query == null) {
			return 0;
		}
		try {
			DirectoryReader ireader = getIndexReader(appid);
			if (ireader != null) {
				IndexSearcher isearcher = new IndexSearcher(ireader);
				int count = isearcher.count(query);
				ireader.close();
				ireader.directory().close();
				return count;
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
		return 0;
	}

	static DirectoryReader getIndexReader(String appid) {
		// Directory directory = new RAMDirectory(); // Store the index in memory:
		String dataDir = Config.getConfigParam("lucene.dir", Paths.get(".").toAbsolutePath().normalize().toString());
		Path path = FileSystems.getDefault().getPath(dataDir, "data", getIndexName(appid));
		try {
			return DirectoryReader.open(FSDirectory.open(path));
		} catch (IOException ex) {
			logger.warn("Index '{}' does not exist.", getIndexName(appid));
		}
		return null;
	}

	static IndexWriter getIndexWriter(String appid) {
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
//				if (val instanceof String && StringUtils.isBlank((String) val)) {
//					continue;
//				}
				Matcher matcher = Pattern.compile(".*(<|>|<=|>=)$").matcher(term.getKey().trim());
				bfb = new TermQuery(new Term(term.getKey(), val.toString()));
				if (matcher.matches() && val instanceof Number) {
					String key = term.getKey().replaceAll("[<>=\\s]+$", "");
//					RangeQueryBuilder rfb = QueryBuilders.rangeQuery(key);

					if (">".equals(matcher.group(1))) {
//						if (val instanceof Long || val instanceof Integer) {
//							bfb = NumericRangeQuery.newLongRange(key, (Long) val, null, false, false);
//						} else {
							bfb = TermRangeQuery.newStringRange(key, val.toString(), null, false, false);
//						}
					} else if ("<".equals(matcher.group(1))) {
//						if (val instanceof Long || val instanceof Integer) {
//							bfb = NumericRangeQuery.newLongRange(key, null, (Long) val, false, false);
//						} else {
							bfb = TermRangeQuery.newStringRange(key, null, val.toString(), false, false);
//						}
					} else if (">=".equals(matcher.group(1))) {
//						if (val instanceof Long || val instanceof Integer) {
//							bfb = NumericRangeQuery.newLongRange(key, (Long) val, null, true, false);
//						} else {
							bfb = TermRangeQuery.newStringRange(key, val.toString(), null, true, false);
//						}
					} else if ("<=".equals(matcher.group(1))) {
//						if (val instanceof Long || val instanceof Integer) {
//							bfb = NumericRangeQuery.newLongRange(key, null, (Long) val, true, false);
//						} else {
							bfb = TermRangeQuery.newStringRange(key, null, val.toString(), true, false);
//						}
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
		if (query != null) {
			if (fields == null || fields.isEmpty()) {
				fields = Collections.singletonList(Config._NAME);
			}
			MultiFieldQueryParser parser = new MultiFieldQueryParser(
					fields.toArray(new String[0]), new StandardAnalyzer());
//			StandardQueryParser parser = new StandardQueryParser();
			parser.setAllowLeadingWildcard(false);
			parser.setLowercaseExpandedTerms(true);
//			parser.setAnalyzer(new StandardAnalyzer()); // TODO: add stopwords
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
			} catch (Exception ex) {}
		}
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
		return appid + "-lucene-" + Config.ENVIRONMENT;
	}

}
