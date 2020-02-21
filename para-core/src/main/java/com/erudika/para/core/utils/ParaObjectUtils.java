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
package com.erudika.para.core.utils;

import com.erudika.para.annotations.Stored;
import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import static com.erudika.para.utils.Utils.getAllDeclaredFields;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * Contains methods for object/grid mapping, JSON serialization, class scanning and resolution.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@SuppressWarnings("unchecked")
public final class ParaObjectUtils {

	private static final Logger logger = LoggerFactory.getLogger(ParaObjectUtils.class);
	// maps plural to singular type definitions
	private static final Map<String, String> CORE_TYPES = new DualHashBidiMap();
	private static final Map<String, String> CORE_PARA_TYPES = new DualHashBidiMap();
	// maps lowercase simple names to class objects
	private static final Map<String, Class<? extends ParaObject>> CORE_CLASSES = new DualHashBidiMap();
	private static final Map<String, Class<? extends ParaObject>> CORE_PARA_CLASSES = new DualHashBidiMap();
	private static final CoreClassScanner SCANNER = new CoreClassScanner();
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	static {
		JSON_MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		JSON_MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		JSON_MAPPER.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
		JSON_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
		JSON_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		JSON_MAPPER.registerModule(new JavaTimeModule());
		JSON_MAPPER.findAndRegisterModules();
	}

	private ParaObjectUtils() { }

	/**
	 * A Jackson {@code ObjectMapper}.
	 *
	 * @return JSON object mapper
	 */
	public static ObjectMapper getJsonMapper() {
		return JSON_MAPPER;
	}

	/**
	 * A Jackson JSON reader.
	 *
	 * @param type the type to read
	 * @return JSON object reader
	 */
	public static ObjectReader getJsonReader(Class<?> type) {
		return JSON_MAPPER.reader().forType(type);
	}

	/**
	 * A Jackson JSON writer. Pretty print is on.
	 *
	 * @return JSON object writer
	 */
	public static ObjectWriter getJsonWriter() {
		return JSON_MAPPER.writer();
	}

	/**
	 * A Jackson JSON writer. Pretty print is off.
	 *
	 * @return JSON object writer with indentation disabled
	 */
	public static ObjectWriter getJsonWriterNoIdent() {
		return JSON_MAPPER.writer().without(SerializationFeature.INDENT_OUTPUT);
	}

	/////////////////////////////////////////////
	//	     OBJECT MAPPING & CLASS UTILS
	/////////////////////////////////////////////

	/**
	 * Returns a map of the core data types.
	 * @return a map of type plural - type singular form
	 */
	public static Map<String, String> getCoreTypes() {
		if (CORE_TYPES.isEmpty()) {
			try {
				for (Class<? extends ParaObject> clazz : getCoreClassesMap().values()) {
					ParaObject p = clazz.getConstructor().newInstance();
					CORE_TYPES.put(p.getPlural(), p.getType());
				}
			} catch (Exception ex) {
				logger.error(null, ex);
			}
		}
		return Collections.unmodifiableMap(CORE_TYPES);
	}

	/**
	 * Returns a map of the core data types declared in Para Core only.
	 * @return a map of type plural - type singular form
	 */
	public static Map<String, String> getCoreParaTypes() {
		if (CORE_PARA_TYPES.isEmpty()) {
			try {
				getCoreClassesMap();
				for (Class<? extends ParaObject> clazz : CORE_PARA_CLASSES.values()) {
					ParaObject p = clazz.getConstructor().newInstance();
					CORE_PARA_TYPES.put(p.getPlural(), p.getType());
				}
			} catch (Exception ex) {
				logger.error(null, ex);
			}
		}
		return Collections.unmodifiableMap(CORE_PARA_TYPES);
	}

	/**
	 * Returns a map of all registered types.
	 * @param app the app to search for custom types
	 * @return a map of plural - singular form of type names
	 */
	public static Map<String, String> getAllTypes(App app) {
		Map<String, String> map = new LinkedHashMap<>(getCoreTypes());
		if (app != null) {
			map.putAll(app.getDatatypes());
		}
		return map;
	}

	/**
	 * Returns the app identifier by parsing the Authorization.
	 * @param authorization Authorization header
	 * @return appid
	 */
	public static String getAppidFromAuthHeader(String authorization) {
		if (StringUtils.isBlank(authorization)) {
			return "";
		}
		String appid = "";
		// JWT token
		if (StringUtils.startsWith(authorization, "Bearer")) {
			try {
				String[] parts = StringUtils.split(authorization, '.');
				if (parts.length > 1) {
					Map<String, Object> jwt = getJsonReader(Map.class).readValue(Utils.base64dec(parts[1]));
					if (jwt != null && jwt.containsKey(Config._APPID)) {
						appid = (String) jwt.get(Config._APPID);
					}
				}
			} catch (Exception e) { }
		} else if (StringUtils.startsWith(authorization, "Anonymous")) {
			// Anonymous request - no signature or JWT
			appid = StringUtils.substringAfter(authorization, "Anonymous").trim();
		} else {
			// Amazon Signature v4
			appid = StringUtils.substringBetween(authorization, "=", "/");
		}
		if (StringUtils.isBlank(appid)) {
			return "";
		}
		return App.id(appid).substring(4);
	}

	/**
	 * Checks if the type of an object matches its real Class name.
	 *
	 * @param so an object
	 * @return true if the types match
	 */
	public static boolean typesMatch(ParaObject so) {
		return (so == null) ? false : so.getClass().equals(toClass(so.getType()));
	}

	/**
	 * @see #getAnnotatedFields(com.erudika.para.core.ParaObject, java.lang.Class, boolean)
	 * @param <P> the object type
	 * @param pojo the object to convert to a map
	 * @return a map of fields and their values
	 */
	public static <P extends ParaObject> Map<String, Object> getAnnotatedFields(P pojo) {
		return getAnnotatedFields(pojo, null);
	}

	/**
	 * @see #getAnnotatedFields(com.erudika.para.core.ParaObject, java.lang.Class, boolean)
	 * @param <P> the object type
	 * @param pojo the object to convert to a map
	 * @param filter a filter annotation. fields that have it will be skipped
	 * @return a map of fields and their values
	 */
	public static <P extends ParaObject> Map<String, Object> getAnnotatedFields(P pojo,
			Class<? extends Annotation> filter) {
		return getAnnotatedFields(pojo, filter, true);
	}

	/**
	 * @see #getAnnotatedFields(com.erudika.para.core.ParaObject, java.lang.Class, boolean)
	 * @param <P> the object type
	 * @param pojo the object to convert to a map
	 * @param flattenNestedObjectsToString flattens nested objects to a JSON string, true by default.
	 * @return a map of fields and their values
	 */
	public static <P extends ParaObject> Map<String, Object> getAnnotatedFields(P pojo,
			boolean flattenNestedObjectsToString) {
		return getAnnotatedFields(pojo, null, flattenNestedObjectsToString);
	}

	/**
	 * Returns a map of annotated fields of a domain object. Only annotated fields are returned. This method forms the
	 * basis of an Object/Grid Mapper. It converts an object to a map of key/value pairs. That map can later be
	 * persisted to a data store.
	 * <br>
	 * If {@code flattenNestedObjectsToString} is true all field values that are objects (i.e. not primitive types or
	 * wrappers) are converted to a JSON string otherwise they are left as they are and will be serialized as regular
	 * JSON objects later (structure is preserved). Null is considered a primitive type. Transient fields and
	 * serialVersionUID are skipped.
	 *
	 * @param <P> the object type
	 * @param pojo the object to convert to a map
	 * @param filter a filter annotation. fields that have it will be skipped
	 * @param flattenNestedObjectsToString true if you want to flatten the nested objects to a JSON string.
	 * @return a map of fields and their values
	 */
	public static <P extends ParaObject> Map<String, Object> getAnnotatedFields(P pojo,
			Class<? extends Annotation> filter, boolean flattenNestedObjectsToString) {
		LinkedHashMap<String, Object> map = new LinkedHashMap<>();
		if (pojo == null) {
			return map;
		}
		try {
			List<Field> fields = getAllDeclaredFields(pojo.getClass());
			Set<String> annotatedFields = new HashSet<String>(fields.size());
			boolean hasJsonAnnotations = false;
			// filter transient fields and those without annotations
			for (Field field : fields) {
				if (field.isAnnotationPresent(Stored.class) && !isIgnoredField(field, filter)) {
					String name = field.getName();
					Object value = PropertyUtils.getProperty(pojo, name);
					if (!Utils.isBasicType(field.getType()) && flattenNestedObjectsToString) {
						value = getJsonWriterNoIdent().writeValueAsString(value);
					}
					if (Arrays.stream(field.getAnnotations()).
							map((a) -> a.annotationType().getSimpleName()).
							anyMatch((a) -> StringUtils.startsWithIgnoreCase(a, "Json"))) {
						annotatedFields.add(name);
						hasJsonAnnotations = true;
					}
					map.put(name, value);
				}
			}
			if (hasJsonAnnotations) {
				// if @Json* annotations are present - overwrite serialized values with the those from Jackson
				JsonNode dataNode = getJsonMapper().valueToTree(pojo);
				Map<String, Object> props = getJsonMapper().convertValue(dataNode, Map.class);
				for (Map.Entry<String, Object> entry : props.entrySet()) {
					if (annotatedFields.contains(entry.getKey())) {
						map.put(entry.getKey(), entry.getValue());
					}
				}
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}

		return Collections.unmodifiableMap(map);
	}

	/**
	 * @see #setAnnotatedFields(com.erudika.para.core.ParaObject, java.util.Map, java.lang.Class)
	 * @param <P> the object type
	 * @param data the map of fields/values
	 * @return the populated object
	 */
	public static <P extends ParaObject> P setAnnotatedFields(Map<String, Object> data) {
		return setAnnotatedFields(null, data, null);
	}

	/**
	 * Converts a map of fields/values to a domain object. Only annotated fields are populated. This method forms the
	 * basis of an Object/Grid Mapper.
	 * <br>
	 * Map values that are JSON objects are converted to their corresponding Java types. Nulls and primitive types are
	 * preserved.
	 *
	 * @param <P> the object type
	 * @param pojo the object to populate with data
	 * @param data the map of fields/values
	 * @param filter a filter annotation. fields that have it will be skipped
	 * @return the populated object
	 */
	public static <P extends ParaObject> P setAnnotatedFields(P pojo, Map<String, Object> data,
			Class<? extends Annotation> filter) {
		if (data == null || data.isEmpty()) {
			return null;
		}
		try {
			if (pojo == null) {
				// try to find a declared class in the core package
				pojo = (P) toClass((String) data.get(Config._TYPE)).getConstructor().newInstance();
			}
			List<Field> fields = getAllDeclaredFields(pojo.getClass());
			Map<String, Object> unknownProps = new LinkedHashMap<>(data);
			Map<String, Object> props = new LinkedHashMap<>(data.size());
			for (Field field : fields) {
				String name = field.getName();
				Object value = data.get(name);
				if (field.isAnnotationPresent(Stored.class) && !isIgnoredField(field, filter)) {
					// try to read a default value from the bean if any
					if (value == null && PropertyUtils.isReadable(pojo, name)) {
						value = PropertyUtils.getProperty(pojo, name);
					}
					// handle complex JSON objects deserialized to Maps, Arrays, etc.
					if (!Utils.isBasicType(field.getType()) && value instanceof String) {
						value = parseFlattenedObject(field, value.toString(), props);
					}
					setAnnotatedField(pojo, props, name, value);
				}
				unknownProps.remove(name); // filter known props
				if (isPropertiesFieldOfDifferentType(name, value)) {
					unknownProps.put("properties", value);
				}
			}
			if (!props.isEmpty()) {
				JsonNode dataNode = getJsonMapper().convertValue(props, JsonNode.class);
				getJsonMapper().readerForUpdating(pojo).readValue(dataNode);
			}
			// handle unknown (user-defined) fields
			setUserDefinedProperties(pojo, unknownProps);
		} catch (Exception ex) {
			logger.error(null, ex);
			pojo = null;
		}
		return pojo;
	}

	private static Object parseFlattenedObject(Field field, String stringValue, Map<String, Object> props) {
		try {
			return getJsonMapper().readValue(stringValue, getJsonMapper().constructType(field.getGenericType()));
		} catch (Exception e) {
			if (!isPropertiesField(field.getName())) {
				props.put(field.getName(), stringValue);
			}
		}
		return stringValue;
	}

	private static <P> void setAnnotatedField(P pojo, Map<String, Object> props, String name, Object value) {
		if (value != null && !props.containsKey(name)) {
			try {
				// perform micro conversion to the correct field type
				BeanUtils.setProperty(pojo, name, value);
			} catch (Exception e) {
				if (!isPropertiesField(name)) {
					props.put(name, value);
				}
			}
		}
	}

	private static boolean isPropertiesField(String name) {
		return Config._PROPERTIES.equals(name);
	}

	private static boolean isPropertiesFieldOfDifferentType(String name, Object value) {
		return isPropertiesField(name) && !(value instanceof Map);
	}

	private static boolean isIgnoredField(Field field, Class<? extends Annotation> filter) {
		return ((filter == null) ? false : field.isAnnotationPresent(filter));
	}

	/**
	 * Handles "unknown" or user-defined fields. The Para object is populated with custom fields
	 * which are stored within the "properties" field of {@link Sysprop}. Unknown or user-defined properties are
	 * those which are not declared inside a Java class, but come from an API request.
	 * @param pojo a Para object
	 * @param props properties to apply to the object.
	 */
	private static <P> void setUserDefinedProperties(P pojo, Map<String, Object> props) {
		if (props != null && pojo instanceof Sysprop) {
			for (Map.Entry<String, Object> entry : props.entrySet()) {
				String name = entry.getKey();
				Object value = entry.getValue();
				// handle the case where we have custom user-defined properties
				// which are not defined as Java class fields
				if (!PropertyUtils.isReadable(pojo, name) || isPropertiesFieldOfDifferentType(name, value)) {
					if (value == null) {
						((Sysprop) pojo).removeProperty(name);
					} else {
						((Sysprop) pojo).addProperty(name, value);
					}
				}
			}
		}
	}

	/**
	 * Constructs a new instance of a core object.
	 *
	 * @param <P> the object type
	 * @param app an app object
	 * @param type the simple name of a class
	 * @return a new instance of a core class. Defaults to {@link com.erudika.para.core.Sysprop}.
	 * @see #toClass(java.lang.String)
	 */
	public static <P extends ParaObject> P toObject(App app, String type) {
		try {
			if (app == null) {
				return (P) toClass(type).getConstructor().newInstance();
			} else {
				Map<String, String> alltypes = getAllTypes(app);
				String typ = alltypes.containsKey(type) ? alltypes.get(type) : type;
				P p = (P) toClass(type).getConstructor().newInstance();
				p.setType(typ);
				return p;
			}
		} catch (Exception ex) {
			logger.error(null, ex);
			return null;
		}
	}

	/**
	 * Converts a class name to a real Class object.
	 *
	 * @param type the simple name of a class
	 * @return the Class object or {@link com.erudika.para.core.Sysprop} if the class was not found.
	 * @see java.lang.Class#forName(java.lang.String)
	 */
	public static Class<? extends ParaObject> toClass(String type) {
		return toClass(type, Sysprop.class);
	}

	/**
	 * Converts a class name to a real {@link com.erudika.para.core.ParaObject} subclass. Defaults to
	 * {@link com.erudika.para.core.Sysprop} if the class was not found in the core package path.
	 *
	 * @param type the simple name of a class
	 * @param defaultClass returns this type if the requested class was not found on the classpath.
	 * @return the Class object. Returns null if defaultClass is null.
	 * @see java.lang.Class#forName(java.lang.String)
	 * @see com.erudika.para.core.Sysprop
	 */
	public static Class<? extends ParaObject> toClass(String type, Class<? extends ParaObject> defaultClass) {
		Class<? extends ParaObject> returnClass = defaultClass;
		if (StringUtils.isBlank(type) || !getCoreClassesMap().containsKey(type)) {
			return returnClass;
		}
		return getCoreClassesMap().get(type);
	}

	/**
	 * Searches through the Para core package and {@code Config.CORE_PACKAGE_NAME} package for {@link ParaObject}
	 * subclasses and adds their names them to the map.
	 *
	 * @return a map of simple class names (lowercase) to class objects
	 */
	public static Map<String, Class<? extends ParaObject>> getCoreClassesMap() {
		if (CORE_CLASSES.isEmpty()) {
			try {
				CORE_PARA_CLASSES.putAll(SCANNER.getComponentClasses(ParaObject.class.getPackage().getName()));
				CORE_CLASSES.putAll(CORE_PARA_CLASSES);
				if (!Config.CORE_PACKAGE_NAME.isEmpty()) {
					CORE_CLASSES.putAll(SCANNER.getComponentClasses(Config.CORE_PACKAGE_NAME));
				}
				logger.debug("Found {} ParaObject classes: {}", CORE_CLASSES.size(), CORE_CLASSES);
			} catch (Exception ex) {
				logger.error(null, ex);
			}
		}
		return Collections.unmodifiableMap(CORE_CLASSES);
	}

	/**
	 * Helper class that lists all classes contained in a given package.
	 */
	private static class CoreClassScanner extends ClassPathScanningCandidateComponentProvider {

		private static final Logger LOG = LoggerFactory.getLogger(CoreClassScanner.class);

		CoreClassScanner() {
			super(false);
			addIncludeFilter(new AssignableTypeFilter(ParaObject.class));
		}

		final Map<String, Class<? extends ParaObject>> getComponentClasses(String basePackage) {
			basePackage = (basePackage == null) ? "" : basePackage;
			Map<String, Class<? extends ParaObject>> classes = new LinkedHashMap<>();
			for (BeanDefinition candidate : findCandidateComponents(basePackage)) {
				try {
					Class<? extends ParaObject> clazz = (Class<? extends ParaObject>)
							ClassUtils.resolveClassName(candidate.getBeanClassName(),
									Thread.currentThread().getContextClassLoader());
					boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());
					boolean isInterface = Modifier.isInterface(clazz.getModifiers());
					boolean isCoreObject = ParaObject.class.isAssignableFrom(clazz);
					if (isCoreObject && !isAbstract && !isInterface) {
						classes.put(clazz.getSimpleName().toLowerCase(), clazz);
					}
				} catch (Exception ex) {
					LOG.error(null, ex);
				}
			}
			return classes;
		}
	}

	/**
	 * Converts a JSON string to a domain object. If we can't match the JSON to a core object, we fall back to
	 * {@link com.erudika.para.core.Sysprop}.
	 *
	 * @param <P> type of object to convert
	 * @param json the JSON string
	 * @return a core domain object or null if the string was blank
	 */
	public static <P extends ParaObject> P fromJSON(String json) {
		if (StringUtils.isBlank(json)) {
			return null;
		}
		try {
			Map<String, Object> map = getJsonReader(Map.class).readValue(json);
			return setAnnotatedFields(map);
		} catch (Exception e) {
			logger.error(null, e);
		}
		return null;
	}

	/**
	 * Converts a domain object to JSON.
	 *
	 * @param <P> type of object to convert
	 * @param obj a domain object
	 * @return the JSON representation of that object
	 */
	public static <P extends ParaObject> String toJSON(P obj) {
		if (obj == null) {
			return "{}";
		}
		try {
			return getJsonWriter().writeValueAsString(obj);
		} catch (Exception e) {
			logger.error(null, e);
		}
		return "{}";
	}

}
