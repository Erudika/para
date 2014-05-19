/*
 * Copyright 2013-2014 Erudika. http://erudika.com
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
package com.erudika.para.utils;

import com.erudika.para.annotations.Email;
import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.rjeschke.txtmark.Configuration;
import com.github.rjeschke.txtmark.Processor;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.DateFormatSymbols;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.geonames.FeatureClass;
import org.geonames.Style;
import org.geonames.Toponym;
import org.geonames.ToponymSearchCriteria;
import org.geonames.ToponymSearchResult;
import org.geonames.WebService;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * The core utility class.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@SuppressWarnings("unchecked")
public final class Utils {

	private static final Logger logger = LoggerFactory.getLogger(Utils.class);
	private static final ExecutorService exec = Executors.newSingleThreadExecutor();
	private static final ObjectMapper jsonMapper = new ObjectMapper();
	private static final ObjectReader jsonReader;
	private static final ObjectWriter jsonWriter;
	private static HumanTime humantime;
	private static Utils instance;

	//////////  ID GEN VARS  //////////////
	private static final long TIMER_OFFSET = 1310084584692L; // ~July 2011
	private static final long workerIdBits = 5L;
	private static final long dataCenterIdBits = 5L;
	private static final long maxWorkerId = -1L ^ (-1L << workerIdBits);
	private static final long maxDataCenterId = -1L ^ (-1L << dataCenterIdBits);
	private static final long sequenceBits = 12L;
	private static final long workerIdShift = sequenceBits;
	private static final long dataCenterIdShift = sequenceBits + workerIdBits;
	private static final long timestampLeftShift = sequenceBits + workerIdBits + dataCenterIdBits;
	private static final long sequenceMask = -1L ^ (-1L << sequenceBits);
	private static long lastTimestamp = -1L;
	private static long dataCenterId = 0L;	// only one datacenter atm
	private static long workerId;	// max 1024
	private static long sequence = 0L;

	static {
		initIdGenerator();
		jsonMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		jsonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		jsonMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
		jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
		jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		jsonMapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
		jsonReader = jsonMapper.reader();
		jsonWriter = jsonMapper.writer();
	}

	private Utils() { }

	/**
	 * Returns an instance of this class.
	 * @return an instance
	 */
	public static Utils getInstance() {
		if (instance == null) {
			instance = new Utils();
		}
		return instance;
	}

	/**
	 * HumanTime - a relative time formatter
	 * @return humantime instance
	 */
	public static HumanTime getHumanTime() {
		if (humantime == null) {
			humantime = new HumanTime();
		}
		return humantime;
	}

	/**
	 * A Jackson {@code ObjectMapper}.
	 * @return JSON object mapper
	 */
	public static ObjectMapper getJsonMapper() {
		return jsonMapper;
	}

	/**
	 * A Jackson JSON reader.
	 * @param type the type to read
	 * @return JSON object reader
	 */
	public static ObjectReader getJsonReader(Class<?> type) {
		return jsonReader.withType(type);
	}

	/**
	 * A Jackson JSON writer. Pretty print is on.
	 * @return JSON object writer
	 */
	public static ObjectWriter getJsonWriter() {
		return jsonWriter;
	}

	/**
	 * A Jackson JSON writer. Pretty print is off.
	 * @return JSON object writer with indentation disabled
	 */
	public static ObjectWriter getJsonWriterNoIdent() {
		return jsonWriter.without(SerializationFeature.INDENT_OUTPUT);
	}

	/////////////////////////////////////////////
	//	    	   INIT FUNCTIONS
	/////////////////////////////////////////////

	private static void initIdGenerator() {
		String workerID = Config.WORKER_ID;
		workerId = NumberUtils.toLong(workerID, 1);

		if (workerId > maxWorkerId || workerId < 0) {
			workerId = new Random().nextInt((int) maxWorkerId + 1);
		}

		if (dataCenterId > maxDataCenterId || dataCenterId < 0) {
			dataCenterId =  new Random().nextInt((int) maxDataCenterId + 1);
		}
	}

	/////////////////////////////////////////////
	//	    	   HASH UTILS
	/////////////////////////////////////////////

	/**
	 * MD5 hash function
	 * @param s the string to be hashed
	 * @return an md5 hash
	 */
	public static String MD5(String s) {
		if (s == null) {
			return "";
		}
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(s.getBytes());

			byte byteData[] = md.digest();

			//convert the byte to hex format method 1
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < byteData.length; i++) {
				sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException ex) {
			return "";
		}
	}

	/**
	 * bcrypt hash function implemented by Spring Security
	 * @param s the string to be hashed
	 * @return the hash
	 */
	public static String bcrypt(String s) {
		return (s == null) ? s : BCrypt.hashpw(s, BCrypt.gensalt(12));
	}

	/**
	 * Checks if a hash matches a string.
	 * @param plain plain text string
	 * @param storedHash hashed string
	 * @return true if the hash matches
	 */
	public static boolean bcryptMatches(String plain, String storedHash) {
		if (StringUtils.isBlank(plain) || StringUtils.isBlank(storedHash)) {
			return false;
		}
		try {
			return BCrypt.checkpw(plain, storedHash);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Generates an authentication token - a random string encoded in Base64.
	 * @param length the length of the generated token
	 * @return a random string
	 */
	public static String generateSecurityToken(int length) {
		final byte[] bytes = new byte[length];
		SecureRandom rand;
		try {
			rand = SecureRandom.getInstance("SHA1PRNG");
		} catch (NoSuchAlgorithmException ex) {
			logger.error(null, ex);
			rand = new SecureRandom();
		}
		rand.nextBytes(bytes);
		return base64enc(bytes);
	}

	/**
	 * Generates an authentication token - a random 32 byte string encoded in Base64.
	 * @return a random string
	 */
	public static String generateSecurityToken() {
		return generateSecurityToken(32);
	}

	/////////////////////////////////////////////
	//	    	   STRING UTILS
	/////////////////////////////////////////////

	/**
	 * Escapes JavaScript
	 * @param str a javascript string
	 * @return the escaped javascript string
	 */
	public static String escapeJavascript(String str) {
		return (str == null) ? "" : StringEscapeUtils.escapeEcmaScript(str);
	}

	/**
	 * Strips all HTML tags from a string
	 * @param html HTML string
	 * @return just the text
	 */
	public static String stripHtml(String html) {
		return (html == null) ? "" : Jsoup.parse(html).text();
	}

	/**
	 * Converts Markdown to HTML
	 * @param markdownString Markdown
	 * @return HTML
	 */
	public static String markdownToHtml(String markdownString) {
		return StringUtils.isBlank(markdownString) ? "" :
				Processor.process(markdownString, Configuration.DEFAULT_SAFE);
	}

	/**
	 * Abbreviates a string
	 * @param str a string
	 * @param max max length
	 * @return a substring of that string
	 */
	public static String abbreviate(String str, int max) {
		return StringUtils.isBlank(str) ? "" : StringUtils.abbreviate(str, max);
	}

	/**
	 * Joins a list of strings to String using a separator.
	 * @param arr a list of strings
	 * @param separator a separator string
	 * @return a string
	 */
	public static String arrayJoin(List<String> arr, String separator) {
		return (arr == null || separator == null) ? "" : StringUtils.join(arr, separator);
	}

	/**
	 * Strips all symbols, punctuation, whitespace and control chars from a string.
	 * @param str a dirty string
	 * @return a clean string
	 */
	public static String stripAndTrim(String str) {
		return StringUtils.isBlank(str) ? "" :
			str.replaceAll("[\\p{S}\\p{P}\\p{C}]", "").replaceAll("\\p{Z}+", " ").trim();
	}

	/**
	 * Converts spaces to dashes.
	 * @param str a string with spaces
	 * @param replaceWith a string to replace spaces with
	 * @return a string with dashes
	 */
	public static String noSpaces(String str, String replaceWith) {
		return StringUtils.isBlank(str) ? "" : str.trim().replaceAll("[\\p{C}\\p{Z}]+",
				StringUtils.trimToEmpty(replaceWith)).toLowerCase();
	}

	/**
	 * Formats a messages containing {0}, {1}... etc. Used for translation.
	 * @param msg a message with placeholders
	 * @param params objects used to populate the placeholders
	 * @return a formatted message
	 */
	public static String formatMessage(String msg, Object... params) {
		try {
			return StringUtils.isBlank(msg) ? "" : MessageFormat.format(msg, params);
		} catch (IllegalArgumentException e) {
			return msg;
		}
	}

	/**
	 * Encodes a byte array to Base64
	 * @param str the byte array
	 * @return an encoded string
	 */
	public static String base64enc(byte[] str) {
		if (str == null) {
			return "";
		}
		return new String(Base64.getEncoder().encode(str));
	}

	/**
	 * Decodes a string from Base64
	 * @param str the encoded string
	 * @return a decoded string
	 */
	public static String base64dec(String str) {
		if (str == null) {
			return "";
		}
		try {
			return new String(Base64.getDecoder().decode(str), Config.DEFAULT_ENCODING);
		} catch (UnsupportedEncodingException ex) {
			return "";
		}
	}

	/////////////////////////////////////////////
	//	    	   DATE UTILS
	/////////////////////////////////////////////

	/**
	 * Formats a date in a specific format.
	 * @param timestamp the Java timestamp
	 * @param format the date format
	 * @param loc the locale instance
	 * @return a formatted date
	 */
	public static String formatDate(Long timestamp, String format, Locale loc) {
		if (StringUtils.isBlank(format)) {
			format = DateFormatUtils.ISO_DATETIME_FORMAT.getPattern();
		}
		if (timestamp == null) {
			timestamp = timestamp();
		}
		if (loc == null) {
			loc = Locale.US;
		}
		return DateFormatUtils.format(timestamp, format, loc);
	}

	/**
	 * Formats a date in a specific format.
	 * @param format the date format
	 * @param loc the locale instance
	 * @return a formatted date
	 */
	public static String formatDate(String format, Locale loc) {
		return formatDate(timestamp(), format, loc);
	}

	/**
	 * Returns the current year.
	 * @return this year
	 */
	public static int getCurrentYear() {
		return Calendar.getInstance().get(Calendar.YEAR);
	}

	/**
	 * Java time
	 * @return {@link java.lang.System#currentTimeMillis()}
	 */
	public static long timestamp() {
		return System.currentTimeMillis();
	}

	/**
	 * Returns an array of the months in the Gregorian calendar.
	 * @param locale the locale used for the months' names
	 * @return an array of the 12 months
	 */
	public static String[] getMonths(Locale locale) {
		if (locale == null) {
			locale = Locale.US;
		}
		DateFormatSymbols dfs = DateFormatSymbols.getInstance(locale);
		return dfs.getMonths();
	}

	/////////////////////////////////////////////
	//	    	   NUMBER UTILS
	/////////////////////////////////////////////

	/**
	 * Rounds a float to an int
	 * @param d a float
	 * @return a rounded int
	 */
	public static int round(float d) {
		return Math.round(d);
	}

	/**
	 * Abbreviates an integer by adding a letter suffix at the end.
	 * E.g. "M" for millions, "K" for thousands, etc.
	 * @param number a big integer
	 * @param decPlaces decimal places
	 * @return the rounded integer as a string
	 */
	public static String abbreviateInt(Number number, int decPlaces) {
		if (number == null) {
			return "";
		}
		String abbrevn = number.toString();
		// 2 decimal places => 100, 3 => 1000, etc
		decPlaces = (int) Math.pow(10, decPlaces);
		// Enumerate number abbreviations
		String[] abbrev = { "K", "M", "B", "T" };
		boolean done = false;
		// Go through the array backwards, so we do the largest first
		for (int i = abbrev.length - 1; i >= 0 && !done; i--) {
			// Convert array index to "1000", "1000000", etc
			int size = (int) Math.pow(10, (i + 1) * 3);
			// If the number is bigger or equal do the abbreviation
			if (size <= number.intValue()) {
				// Here, we multiply by decPlaces, round, and then divide by decPlaces.
				// This gives us nice rounding to a particular decimal place.
				number = Math.round(number.intValue() * decPlaces / size) / decPlaces;
				// Add the letter for the abbreviation
				abbrevn = number + abbrev[i];
				// We are done... stop
				done = true;
			}
		}
		return abbrevn;
	}

	/////////////////////////////////////////////
	//	    	   URL UTILS
	/////////////////////////////////////////////

	/**
	 * Decodes a URL-encoded string
	 * @param s a string
	 * @return the decoded string
	 */
	public static String urlDecode(String s) {
		if (s == null) {
			return "";
		}
		String decoded = s;
		try {
			decoded = URLDecoder.decode(s, Config.DEFAULT_ENCODING);
		} catch (UnsupportedEncodingException ex) {
			logger.error(null, ex);
		}
		return decoded;
	}

	/**
	 * URL-encodes a string
	 * @param s a string
	 * @return the encoded string
	 */
	public static String urlEncode(String s) {
		if (s == null) {
			return "";
		}
		String encoded = s;
		try {
			encoded = URLEncoder.encode(s, Config.DEFAULT_ENCODING);
		} catch (UnsupportedEncodingException ex) {
			logger.error(null, ex);
		}
		return encoded;
	}

	/**
	 * URL validation checker
	 * @param url a URL
	 * @return true if the URL is valid
	 */
	public static boolean isValidURL(String url) {
		return !StringUtils.isBlank(getHostFromURL(url));
	}

	/**
	 * Returns the host part of the URL
	 * @param url a URL
	 * @return just the host
	 */
	public static String getHostFromURL(String url) {
		URL u = toURL(url);
		String host = (u == null) ? "" : u.getHost();
		return host;
	}

	/**
	 * The basic URL without any parameters: &gt;scheme&lt;:&gt;authority&lt;
	 * @param url a full URL
	 * @return the basic URL
	 */
	public static String getBaseURL(String url) {
		URL u = toURL(url);
		String base = null;
		if (u != null) {
			try {
				base = u.toURI().getScheme().concat("://").concat(u.getAuthority());
			} catch (URISyntaxException ex) {
				base = null;
			}
		}
		return base;
	}

	private static URL toURL(String url) {
		if (StringUtils.isBlank(url)) {
			return null;
		}
		URL u;
		try {
			u = new URL(url);
		} catch (MalformedURLException e) {
			// the URL is not in a valid form
			u = null;
		}
		return u;
	}

	/**
	 * Returns the default URL for a given domain object.
	 * @param obj the domain object
	 * @param includeName true if we want to include the name of the object in the URL
	 * @param includeId true if we want to include the ID of the object in the URL
	 * @return the object's URL - e.g. /users/123-name, /users/, /users/123
	 */
	public static String getObjectURI(ParaObject obj, boolean includeName, boolean includeId) {
		if (obj == null) {
			return "/";
		}
		if (includeId && obj.getId() != null) {
			return (includeName && !StringUtils.isBlank(obj.getName())) ? obj.getObjectURI().concat("-").
					concat(urlEncode(noSpaces(obj.getName(), "-"))) : obj.getObjectURI();
		} else {
			return obj.getObjectURI();
		}
	}

	/////////////////////////////////////////////
	//    	   COOKIE & STATE UTILS
	/////////////////////////////////////////////

	/**
	 * Sets a cookie.
	 * @param name the name
	 * @param value the value
	 * @param req HTTP request
	 * @param res HTTP response
	 */
	public static void setStateParam(String name, String value, HttpServletRequest req,
			HttpServletResponse res) {
		setStateParam(name, value, req, res, false);
	}

	/**
	 * Sets a cookie.
	 * @param name the name
	 * @param value the value
	 * @param req HTTP request
	 * @param res HTTP response
	 * @param httpOnly HTTP only flag
	 */
	public static void setStateParam(String name, String value, HttpServletRequest req,
			HttpServletResponse res, boolean httpOnly) {
		setRawCookie(name, value, req, res, httpOnly, -1);
	}

	/**
	 * Reads a cookie.
	 * @param name the name
	 * @param req HTTP request
	 * @return the cookie value
	 */
	public static String getStateParam(String name, HttpServletRequest req) {
		return getCookieValue(req, name);
	}

	/**
	 * Deletes a cookie.
	 * @param name the name
	 * @param req HTTP request
	 * @param res HTTP response
	 */
	public static void removeStateParam(String name, HttpServletRequest req,
			HttpServletResponse res) {
		setRawCookie(name, "", req, res, false, 0);
	}

	/**
	 * Sets a cookie.
	 * @param name the name
	 * @param value the value
	 * @param req HTTP request
	 * @param res HTTP response
	 * @param httpOnly HTTP only flag
	 * @param maxAge max age
	 */
	public static void setRawCookie(String name, String value, HttpServletRequest req,
			HttpServletResponse res, boolean httpOnly, int maxAge) {
		if (StringUtils.isBlank(name) || StringUtils.isBlank(value) || req == null || res == null) {
			return;
		}
		Cookie cookie = new Cookie(name, value);
		cookie.setHttpOnly(httpOnly);
		cookie.setMaxAge(maxAge < 0 ? Config.SESSION_TIMEOUT_SEC.intValue() : maxAge);
		cookie.setPath("/");
		cookie.setSecure(req.isSecure());
		res.addCookie(cookie);
	}

	/**
	 * Reads a cookie.
	 * @param name the name
	 * @param req HTTP request
	 * @return the cookie value
	 */
	public static String getCookieValue(HttpServletRequest req, String name) {
		if (StringUtils.isBlank(name) || req == null) {
			return null;
		}
		Cookie cookies[] = req.getCookies();
		if (cookies == null || name == null || name.length() == 0) {
			return null;
		}
		//Otherwise, we have to do a linear scan for the cookie.
		for (Cookie cookie : cookies) {
			if (cookie.getName().equals(name)) {
				return cookie.getValue();
			}
		}
		return null;
	}

	/**
	 * Same as {@link java.lang.System#getProperty(java.lang.String)}
	 * @param name the name of the property
	 * @return the property value
	 */
	public static String getSystemProperty(String name) {
		return StringUtils.isBlank(name) ? "" : System.getProperty(name);
	}

	/////////////////////////////////////////////
	//    	        MISC UTILS
	/////////////////////////////////////////////

	/**
	 * Returns the adjusted size of an image (doesn't do any resizing)
	 * @param h an image height
	 * @param w an image width
	 * @return the adjusted width and height if they are larger than {@link Config#MAX_IMG_SIZE_PX}.
	 * @see Config#MAX_IMG_SIZE_PX
	 */
	public static int[] getMaxImgSize(int h, int w) {
		int[] size = {h, w};
		int max = Config.MAX_IMG_SIZE_PX;
		if (w == h) {
			size[0] = Math.min(h, max);
			size[1] = Math.min(w, max);
		} else if (Math.max(h, w) > max) {
			int ratio = (100 * max) / Math.max(h, w);
			if (h > w) {
				size[0] = max;
				size[1] = (w * ratio) / 100;
			} else {
				size[0] = (h * ratio) / 100;
				size[1] = max;
			}
		}
		return size;
	}

	/**
	 * Returns a list of places for a query. Data from geonames.org
	 * @param q the query
	 * @return a list of places
	 */
	public static List<Toponym> readLocationForKeyword(String q) {
		return readLocationForKeyword(q, Style.FULL);
	}

	/**
	 * Returns a list of places for a query. Data from geonames.org
	 * @param q the query
	 * @param style style
	 * @return a list of places
	 */
	public static List<Toponym> readLocationForKeyword(String q, Style style) {
		List<Toponym> list = new ArrayList<Toponym>();
		ToponymSearchResult locationSearchResult;
		ToponymSearchCriteria searchLocation = new ToponymSearchCriteria();
		searchLocation.setMaxRows(7);
		searchLocation.setFeatureClass(FeatureClass.P);
		searchLocation.setStyle(style);
		searchLocation.setQ(q);
		try {
			WebService.setUserName("erudika");
			locationSearchResult = WebService.search(searchLocation);
			if (locationSearchResult != null) {
				list.addAll(locationSearchResult.getToponyms());
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}
		return list;
	}

	/**
	 * Checks if a request comes from JavaScript.
	 * @param request HTTP request
	 * @return true if AJAX
	 */
	public static boolean isAjaxRequest(HttpServletRequest request) {
		return "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With")) ||
				"XMLHttpRequest".equalsIgnoreCase(request.getParameter("X-Requested-With"));
	}

	/**
	 * Executes a {@link java.util.concurrent.Callable} asynchronously
	 * @param callable a task
	 * @return a future
	 */
	public static Future<?> asyncExecute(Callable<?> callable) {
		try {
			return exec.submit(callable);
		} catch (Exception ex) {
			logger.warn(null, ex);
			return null;
		}
	}

	/**
	 * Quick and dirty singular to plural conversion.
	 * @param singul a word
	 * @return a guess of its plural form
	 */
	public static String singularToPlural(String singul) {
		return StringUtils.isBlank(singul) ? singul :
				(singul.endsWith("s") ? singul + "es" :
				(singul.endsWith("y") ? StringUtils.removeEndIgnoreCase(singul, "y") + "ies" :
										singul + "s"));
	}


	/////////////////////////////////////////////
	//	     OBJECT MAPPING & CLASS UTILS
	/////////////////////////////////////////////

	/**
	 * Populates an object with an array of query parameters (dangerous!).
	 * <b>This method might be deprecated in the future.</b>
	 * @param <P> the object type
	 * @param transObject an object
	 * @param paramMap a query parameters map
	 */
	public static <P extends ParaObject> void populate(P transObject, Map<String, String[]> paramMap) {
		if (transObject == null || paramMap == null || paramMap.isEmpty()) {
			return;
		}
		Class<Locked> locked = (paramMap.containsKey(Config._ID)) ? Locked.class : null;
		Map<String, Object> fields = getAnnotatedFields(transObject, locked);
		Map<String, Object> data = new HashMap<String, Object>();
		// populate an object with converted param values from param map.
		try {
			for (Map.Entry<String, String[]> ks : paramMap.entrySet()) {
				String param = ks.getKey();
				String[] values = ks.getValue();
				String value = (values.length > 1) ? getJsonWriter().writeValueAsString(values) : values[0];
				if (fields.containsKey(param)) {
					data.put(param, value);
				}
			}
			setAnnotatedFields(transObject, data, locked);
		} catch (Exception ex) {
			logger.error(null, ex);
		}
	}

	/**
	 * Checks if the type of an object matches its real Class name.
	 * @param so an object
	 * @return true if the types match
	 */
	public static boolean typesMatch(ParaObject so) {
		return (so == null) ? false : so.getClass().equals(toClass(so.getType()));
	}

	/**
	 * Returns a map of annotated fields of a domain object. Only annotated fields are returned.
	 * This method forms the basis of an Object/Grid Mapper. It converts an object
	 * to a map of key/value pairs. That map can later be persisted to a data store.
	 * <br>
	 * Field values that are objects (i.e. not primitive types or wrappers) are converted to JSON.
	 * Null is considered a primitive type. Transient fields and serialVersionUID are skipped.
	 * @param <P> the object type
	 * @param bean the object to convert to a map
	 * @return a map of fields and their values
	 */
	public static <P extends ParaObject> Map<String, Object> getAnnotatedFields(P bean) {
		return getAnnotatedFields(bean, null);
	}

	/**
	 * Returns a map of annotated fields of a domain object. Only annotated fields are returned.
	 * This method forms the basis of an Object/Grid Mapper. It converts an object
	 * to a map of key/value pairs. That map can later be persisted to a data store.
	 * <br>
	 * Field values that are objects (i.e. not primitive types or wrappers) are converted to JSON.
	 * Null is considered a primitive type. Transient fields and serialVersionUID are skipped.
	 * @param <P> the object type
	 * @param bean the object to convert to a map
	 * @param filter a filter annotation. fields that have it will be skipped
	 * @return a map of fields and their values
	 */
	public static <P extends ParaObject> Map<String, Object> getAnnotatedFields(P bean,
			Class<? extends Annotation> filter) {
		HashMap<String, Object> map = new HashMap<String, Object>();
		if (bean == null) {
			return map;
		}
		try {
			List<Field> fields = getAllDeclaredFields(bean.getClass());
			// filter transient fields and those without annotations
			for (Field field : fields) {
				boolean dontSkip = ((filter == null) ? true : !field.isAnnotationPresent(filter));
				if (field.isAnnotationPresent(Stored.class) && dontSkip) {
					Object prop = PropertyUtils.getProperty(bean, field.getName());
					if (prop == null || isBasicType(field.getType())) {
						map.put(field.getName(), prop);
					} else {
						map.put(field.getName(), getJsonWriterNoIdent().writeValueAsString(prop));
					}
				}
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}

		return map;
	}

	/**
	 * Converts a map of fields/values to a domain object. Only annotated fields are populated.
	 * This method forms the basis of an Object/Grid Mapper.
	 * <br>
	 * Map values that are JSON objects are converted to their corresponding Java types.
	 * Nulls and primitive types are preserved.
	 * @param <P> the object type
	 * @param data the map of fields/values
	 * @return the populated object
	 */
	public static <P extends ParaObject> P setAnnotatedFields(Map<String, Object> data) {
		return setAnnotatedFields(null, data, null);
	}

	/**
	 * Converts a map of fields/values to a domain object. Only annotated fields are populated.
	 * This method forms the basis of an Object/Grid Mapper.
	 * <br>
	 * Map values that are JSON objects are converted to their corresponding Java types.
	 * Nulls and primitive types are preserved.
	 * @param <P> the object type
	 * @param bean the object to populate with data
	 * @param data the map of fields/values
	 * @param filter a filter annotation. fields that have it will be skipped
	 * @return the populated object
	 */
	public static <P extends ParaObject> P setAnnotatedFields(P bean, Map<String, Object> data,
			Class<? extends Annotation> filter) {
		if (data == null || data.isEmpty()) {
			return null;
		}
		try {
			if (bean == null) {
				// try to find a declared class in the core package
				bean = (P) toClass((String) data.get(Config._TYPE)).getConstructor().newInstance();
			}
			List<Field> fields = getAllDeclaredFields(bean.getClass());
			Map<String, Object> props = new HashMap<String, Object>(data);
			for (Field field : fields) {
				boolean dontSkip = ((filter == null) ? true : !field.isAnnotationPresent(filter));
				if (field.isAnnotationPresent(Stored.class) && dontSkip) {
					String name = field.getName();
					Object value = data.get(name);
					// try to read a default value from the bean if any
					if (value == null && PropertyUtils.isReadable(bean, name)) {
						value = PropertyUtils.getProperty(bean, name);
					}
					if (value == null || isBasicType(field.getType())) {
						BeanUtils.setProperty(bean, name, value);
					} else {
						Object val = getJsonReader(field.getType()).readValue(value.toString());
						BeanUtils.setProperty(bean, name, val);
					}
					props.remove(name);
				}
			}
		} catch (Exception ex) {
			logger.error(null, ex);
			bean = null;
		}
		return bean;
	}

	/**
	 * Constructs a new instance of a core object.
	 * @param <P> the object type
	 * @param type the simple name of a class
	 * @return a new instance of a core class. Defaults to {@link com.erudika.para.core.Sysprop}.
	 * @see #toClass(java.lang.String)
	 */
	public static <P extends ParaObject> P toObject(String type) {
		try {
			return (P) toClass(type).getConstructor().newInstance();
		} catch (Exception ex) {
			logger.error(null, ex);
			return null;
		}
	}

	/**
	 * Converts a class name to a real Class object.
	 * @param type the simple name of a class
	 * @return the Class object or {@link com.erudika.para.core.Sysprop} if the class was not found.
	 * @see java.lang.Class#forName(java.lang.String)
	 */
	public static Class<? extends ParaObject> toClass(String type) {
		return toClass(type, null, Sysprop.class);
	}

	/**
	 * Converts a class name to a real {@link com.erudika.para.core.ParaObject} subclass.
	 * Defaults to {@link com.erudika.para.core.Sysprop} if the class was not found in the core package path.
	 * @param type the simple name of a class
	 * @param scanPackagePath the package path of the class
	 * @param defaultClass returns this type if the requested class was not found on the classpath.
	 * @return the Class object. Returns null if defaultClass is null.
	 * @see java.lang.Class#forName(java.lang.String)
	 * @see com.erudika.para.core.Sysprop
	 */
	public static Class<? extends ParaObject> toClass(String type, String scanPackagePath,
			Class<? extends ParaObject> defaultClass) {
		String packagename = Config.CORE_PACKAGE_NAME;
		String corepackage = StringUtils.isBlank(scanPackagePath) ? packagename : scanPackagePath;
		Class<? extends ParaObject> returnClass = defaultClass;
		if (StringUtils.isBlank(type)) {
			return returnClass;
		}
		try {
			returnClass = (Class<? extends ParaObject>) Class.forName(ParaObject.class.getPackage().getName().
					concat(".").concat(StringUtils.capitalize(type)));
		} catch (ClassNotFoundException ex) {
			try {
				returnClass = (Class<? extends ParaObject>) Class.forName(corepackage.concat(".").
						concat(StringUtils.capitalize(type)));
			} catch (ClassNotFoundException ex1) {
				// unknown type - fall back to Sysprop
				returnClass = defaultClass;
			}
		}
		return returnClass;
	}

	/**
	 * Converts a JSON string to a domain object.
	 * If we can't match the JSON to a core object, we fall back to {@link com.erudika.para.core.Sysprop}.
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

	/**
	 * Checks if a class is primitive, String or a primitive wrapper.
	 * @param clazz a class
	 * @return true if primitive or wrapper
	 */
	public static boolean isBasicType(Class<?> clazz) {
		return (clazz == null) ? false : (clazz.isPrimitive() ||
				clazz.equals(String.class) ||
				clazz.equals(Long.class) ||
				clazz.equals(Integer.class) ||
				clazz.equals(Boolean.class) ||
				clazz.equals(Byte.class) ||
				clazz.equals(Short.class) ||
				clazz.equals(Float.class) ||
				clazz.equals(Double.class) ||
				clazz.equals(Character.class));
	}

	/**
	 * Returns the simple name of a class in lowercase (AKA the type).
	 * @param clazz a core class
	 * @return just the name in lowercase or an empty string if clazz is null
	 */
	public static String type(Class<? extends ParaObject> clazz) {
		return (clazz == null) ? "" : clazz.getSimpleName().toLowerCase();
	}

	/////////////////////////////////////////////
	//	     ANNOTATIONS & VALIDATION
	/////////////////////////////////////////////

	/**
	 * Validates objects using Hibernate Validator.
	 * @param obj an object to be validated
	 * @return true if the object is valid (all fields are populated properly)
	 */
	public static boolean isValidObject(ParaObject obj) {
		return validateObject(obj).length == 0;
	}

	/**
	 * Validates objects using Hibernate Validator.
	 * @param content an object to be validated
	 * @return a list of error messages or empty if object is valid
	 */
	public static String[] validateObject(ParaObject content) {
		if (content == null) {
			return new String[]{ "Object cannot be null." };
		}
		ArrayList<String> list = new ArrayList<String>();
		try {
			ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
			Validator validator = factory.getValidator();
			for (ConstraintViolation<ParaObject> constraintViolation : validator.validate(content)) {
				String prop = "'".concat(constraintViolation.getPropertyPath().toString()).concat("'");
				list.add(prop.concat(" ").concat(constraintViolation.getMessage()));
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
		return list.toArray(new String[]{ });
	}

	/**
	 * A JSON object used for JavaScript validation. It maps Hibernate Validator annotations to JavaScript.
	 * <b>This method WILL change in the future.</b>
	 * @param type the type of object that will be validated
	 * @param fields list of fields to map
	 * @param lang language map
	 * @return the JSON validation object
	 */
	public static String getJSONValidationObject(String type, List<String> fields, Map<String, String> lang) {
		String root = "{}";
		if (fields == null) {
			fields = new ArrayList<String>();
		}
		if (!StringUtils.isBlank(type)) {
			Class<? extends ParaObject> c = toClass(type);
			ArrayList<String> rules = new ArrayList<String>();
			ArrayList<String> messages = new ArrayList<String>();
			for (Entry<String, List<Annotation>> entry : getAnnotationsMap(c, new HashSet<String>(fields)).entrySet()) {
				ArrayList<String> rs = new ArrayList<String>();
				ArrayList<String> ms = new ArrayList<String>();
				for (Annotation ano : entry.getValue()) {
					String[] rmarr = annotationToValidation(ano, lang);
					if (rmarr.length == 2) {
						String one = rmarr[0];
						String two = rmarr[1];
						if (!StringUtils.isBlank(one)) {
							rs.add(one);
						}
						if (!StringUtils.isBlank(two)) {
							ms.add(two);
						}
					}
				}
				if (!rs.isEmpty()) {
					rules.add("'" + entry.getKey() + "':{" + arrayJoin(rs, ", ") + "}");
				}
				if (!ms.isEmpty()) {
					messages.add("'" + entry.getKey() + "':{" + arrayJoin(ms, ", ") + "}");
				}
			}
			root = "{'rules':{" + arrayJoin(rules, ", ") + "}, 'messages':{" + arrayJoin(messages, ", ") + "}}";
		}
		return root;
	}

	/**
	 * Returns a map of annotations present in for all the declared fields in an object.
	 * @param clazz a class to scan
	 * @param fields only scan these fields (leave null to scan everything)
	 * @return a map of field names to the list of annotations they have
	 */
	static Map<String, List<Annotation>> getAnnotationsMap(Class<? extends ParaObject> clazz, Set<String> fields) {
		HashMap<String, List<Annotation>> map = new HashMap<String, List<Annotation>>();
		try {
			List<Field> fieldlist = getAllDeclaredFields(clazz);

			if (fields != null && !fields.isEmpty()) {
				for (Iterator<Field> it = fieldlist.iterator(); it.hasNext();) {
					Field field = it.next();
					if (!fields.contains(field.getName())) {
						it.remove();
					}
				}
			}

			for (Field field : fieldlist) {
				Annotation[] annos = field.getAnnotations();
				if (annos.length > 1) {
					ArrayList<Annotation> list = new ArrayList<Annotation>();
					for (Annotation annotation : annos) {
						if (!annotation.annotationType().equals(Stored.class)) {
							list.add(annotation);
						}
					}
					map.put(field.getName(), list);
				}
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}
		return map;
	}

	/**
	 * Returns a list of all declared fields in a class. Transient and serialVersionUID fields are skipped.
	 * This method scans parent classes as well.
	 * @param clazz a class to scan
	 * @return a list of fields including those of the parent classes excluding the Object class.
	 */
	static List<Field> getAllDeclaredFields(Class<? extends ParaObject> clazz) {
		ArrayList<Field> fields = new ArrayList<Field>();
		if (clazz == null) {
			return fields;
		}
		Class<?> parent = clazz;
		do {
			for (Field field : parent.getDeclaredFields()) {
				if (!Modifier.isTransient(field.getModifiers()) &&
						!field.getName().equals("serialVersionUID")) {
					fields.add(field);
				}
			}
			parent = parent.getSuperclass();
		} while (!parent.equals(Object.class));
		return fields;
	}

	/**
	 * A JSON object used for JavaScript validation. It maps Hibernate Validator annotations to JavaScript.
	 * This method WILL change in the future.
	 * @param ano annotation
	 * @param lang language map
	 * @return an array with two items: a rule and a message
	 */
	static String[] annotationToValidation(Annotation ano,  Map<String, String> lang) {
		if (ano == null) {
			return new String[0];
		}
		if (lang == null) {
			lang = new HashMap<String, String>();
		}
		Class<? extends Annotation> atype = ano.annotationType();
		String rule = "";
		String msg = "";

		if (atype.equals(NotBlank.class) || atype.equals(NotEmpty.class) ||
				atype.equals(NotNull.class)) {
			rule = "'required': true";
			msg = "'required': '" + lang.get("requiredfield") + "'";
		} else if (atype.equals(Size.class)) {
			try {
				Integer min = (Integer) atype.getMethod("min").invoke(ano);
				Integer max = (Integer) atype.getMethod("max").invoke(ano);
				rule = "'minlength': " + min + ", 'maxlength': " + max;
				msg = "'minlength': '" + formatMessage(lang.get("minlength"), min) + "', " +
					"'maxlength': '" + formatMessage(lang.get("maxlength"), max) + "'";
			} catch (Exception ex) {
				logger.error(null, ex);
			}

		} else if (atype.equals(Email.class)) {
			rule = "'email': true";
			msg = "'email': '" + lang.get("bademail") + "'";
		}
		return new String[]{rule, msg};
	}

	/////////////////////////////////////////////
	//	        MODIFIED SNOWFLAKE
	/////////////////////////////////////////////

	/**
	 * Distributed id generator. Relies on node/worker ids and datacenter ids to prevent collisions.
	 * @return a long unique ID string of digits
	 */
	public static String getNewId() {
		// unique across JVMs as long as each has a different workerID
		// based on Twitter's Snowflake algorithm
		long timestamp = timestamp();

		if (lastTimestamp == timestamp) {
			sequence = (sequence + 1) & sequenceMask;
			if (sequence == 0) {
				timestamp = tilNextMillis(lastTimestamp);
			}
		} else {
			sequence = 0;
		}

		if (timestamp < lastTimestamp) {
			throw new IllegalStateException(String.format("Clock moved backwards.  "
					+ "Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
		}

		lastTimestamp = timestamp;
		return Long.toString(((timestamp - TIMER_OFFSET) << timestampLeftShift) |
											(dataCenterId << dataCenterIdShift) |
													(workerId << workerIdShift) |
																	(sequence));

	}

	private static long tilNextMillis(long lastTimestamp) {
		long timestamp = timestamp();

		while (timestamp <= lastTimestamp) {
			timestamp = timestamp();
		}

		return timestamp;
	}

}
