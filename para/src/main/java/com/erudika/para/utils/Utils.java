/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
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
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.elasticsearch.common.mustache.DefaultMustacheFactory;
import org.elasticsearch.common.mustache.MustacheFactory;
import org.geonames.FeatureClass;
import org.geonames.Style;
import org.geonames.Toponym;
import org.geonames.ToponymSearchCriteria;
import org.geonames.ToponymSearchResult;
import org.geonames.WebService;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.util.ClassUtils;

/**
 * Misc. Para utilities.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@SuppressWarnings("unchecked")
public final class Utils {

	private static final Logger logger = LoggerFactory.getLogger(Utils.class);
	// maps lowercase simple names to class objects
	private static final Map<String, Class<? extends ParaObject>> coreClasses = new DualHashBidiMap();
	private static final CoreClassScanner scanner = new CoreClassScanner();
	private static final ExecutorService exec = Executors.newFixedThreadPool(Config.EXECUTOR_THREADS);
	private static final ObjectMapper jsonMapper = new ObjectMapper();
	private static final Pattern emailz = Pattern.compile(Email.EMAIL_PATTERN);
	private static final ObjectReader jsonReader;
	private static final ObjectWriter jsonWriter;
	private static final MustacheFactory mustache;
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
		mustache = new DefaultMustacheFactory();
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
	 * @param urlSafe switches to a URL safe encoding
	 * @return a random string
	 */
	public static String generateSecurityToken(int length, boolean urlSafe) {
		final byte[] bytes = new byte[length];
		SecureRandom rand;
		try {
			rand = SecureRandom.getInstance("SHA1PRNG");
		} catch (NoSuchAlgorithmException ex) {
			logger.error(null, ex);
			rand = new SecureRandom();
		}
		rand.nextBytes(bytes);
		return urlSafe ? base64encURL(bytes) : base64enc(bytes);
	}

	/**
	 * Generates an authentication token - a random string encoded in Base64.
	 * @param length the length of the generated token
	 * @return a random string
	 */
	public static String generateSecurityToken(int length) {
		return generateSecurityToken(length, false);
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
	 * Compiles a mustache template with a given scope (map of fields and values).
	 * @param scope a map of fields and values
	 * @param template a Mustache template
	 * @return the compiled template string
	 */
	public static String compileMustache(Map<String, Object> scope, String template) {
		if (scope == null || StringUtils.isBlank(template)) {
			return "";
		}
		Writer writer = new StringWriter();
		try {
			mustache.compile(new StringReader(template), MD5(template)).execute(writer, scope);
		} finally {
			try	{
				writer.close();
			} catch (IOException e) {
				logger.error(null, e);
			}
		}
		return writer.toString();
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
		return stripAndTrim(str, "");
	}

	/**
	 * Strips all symbols, punctuation, whitespace and control chars from a string.
	 * @param str a dirty string
	 * @param replaceWith a string to replace spaces with
	 * @return a clean string
	 */
	public static String stripAndTrim(String str, String replaceWith) {
		return StringUtils.isBlank(str) ? "" :
			str.replaceAll("[\\p{S}\\p{P}\\p{C}]", replaceWith).replaceAll("\\p{Z}+", " ").trim();
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
		return new String(Base64.encodeBase64(str));
	}

	/**
	 * Encodes a byte array to Base64. URL safe.
	 * @param str the byte array
	 * @return an encoded string
	 */
	public static String base64encURL(byte[] str) {
		if (str == null) {
			return "";
		}
		return new String(Base64.encodeBase64URLSafe(str));
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
			return new String(Base64.decodeBase64(str), Config.DEFAULT_ENCODING);
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
	 * URL validation
	 * @param url a URL
	 * @return true if the URL is valid
	 */
	public static boolean isValidURL(String url) {
		return toURL(url) != null;
	}

	/**
	 * Email validation
	 * @param url a URL
	 * @return true if the URL is valid
	 */
	public static boolean isValidEmail(String url) {
		return emailz.matcher(url).matches();
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
		List<Toponym> list = new LinkedList<Toponym>();
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
	 * Checks if a response is of type JSON.
	 * @param contentType the value of "Content-Type" header
	 * @return true if JSON
	 */
	public static boolean isJsonType(String contentType) {
		return StringUtils.startsWith(contentType, "application/json") ||
				StringUtils.startsWith(contentType, "application/javascript") ||
				StringUtils.startsWith(contentType, "text/javascript");	// F U facebook!
	}

	/**
	 * Executes a {@link java.util.concurrent.Callable} asynchronously
	 * @param runnable a task
	 */
	public static void asyncExecute(Runnable runnable) {
		try {
			exec.execute(runnable);
		} catch (Exception ex) {
			logger.warn(null, ex);
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

	public static <P extends ParaObject> Map<String, Object> getAnnotatedFields(P pojo) {
		return getAnnotatedFields(pojo, null);
	}

	public static <P extends ParaObject> Map<String, Object> getAnnotatedFields(P pojo,
			Class<? extends Annotation> filter) {
		return getAnnotatedFields(pojo, filter, true);
	}

	/**
	 * Returns a map of annotated fields of a domain object. Only annotated fields are returned.
	 * This method forms the basis of an Object/Grid Mapper. It converts an object
	 * to a map of key/value pairs. That map can later be persisted to a data store.
	 * <br>
	 * If {@code convertNestedToJsonString} is true all field values that are objects
	 * (i.e. not primitive types or wrappers) are converted to a JSON string otherwise they are left as they are
	 * and will be serialized as regular JSON objects later (structure is preserved).
	 * Null is considered a primitive type. Transient fields and serialVersionUID are skipped.
	 * @param <P> the object type
	 * @param pojo the object to convert to a map
	 * @param filter a filter annotation. fields that have it will be skipped
	 * @param convertNestedToJsonString true if you want to flatten the nested objects to a JSON string.
	 * @return a map of fields and their values
	 */
	public static <P extends ParaObject> Map<String, Object> getAnnotatedFields(P pojo,
			Class<? extends Annotation> filter, boolean convertNestedToJsonString) {
		HashMap<String, Object> map = new HashMap<String, Object>();
		if (pojo == null) {
			return map;
		}
		try {
			List<Field> fields = getAllDeclaredFields(pojo.getClass());
			// filter transient fields and those without annotations
			for (Field field : fields) {
				boolean dontSkip = ((filter == null) ? true : !field.isAnnotationPresent(filter));
				if (field.isAnnotationPresent(Stored.class) && dontSkip) {
					String name = field.getName();
					Object value = PropertyUtils.getProperty(pojo, name);
					if (!isBasicType(field.getType()) && convertNestedToJsonString) {
						value = getJsonWriterNoIdent().writeValueAsString(value);
					}
					map.put(name, value);
				}
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}

		return map;
	}

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
			Map<String, Object> props = new HashMap<String, Object>(data);
			for (Field field : fields) {
				boolean dontSkip = ((filter == null) ? true : !field.isAnnotationPresent(filter));
				String name = field.getName();
				Object value = data.get(name);
				if (field.isAnnotationPresent(Stored.class) && dontSkip) {
					// try to read a default value from the bean if any
					if (value == null && PropertyUtils.isReadable(pojo, name)) {
						value = PropertyUtils.getProperty(pojo, name);
					}
					// handle complex JSON objects deserialized to Maps, Arrays, etc.
					if (!isBasicType(field.getType()) && value instanceof String) {
						// in this case the object is a flattened JSON string coming from the DB
						value = getJsonReader(field.getType()).readValue(value.toString());
					}
					field.setAccessible(true);
					BeanUtils.setProperty(pojo, name, value);
				}
				props.remove(name);
			}
			// handle unknown (user-defined) fields
			if (!props.isEmpty() && pojo instanceof Sysprop) {
				for (Entry<String, Object> entry : props.entrySet()) {
					String name = entry.getKey();
					Object value = entry.getValue();
					// handle the case where we have custom user-defined properties
					// which are not defined as Java class fields
					if (!PropertyUtils.isReadable(pojo, name)) {
						if (value == null) {
							((Sysprop) pojo).removeProperty(name);
						} else {
							((Sysprop) pojo).addProperty(name, value);
						}
					}
				}
			}
		} catch (Exception ex) {
			logger.error(null, ex);
			pojo = null;
		}
		return pojo;
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
		return toClass(type, Sysprop.class);
	}

	/**
	 * Converts a class name to a real {@link com.erudika.para.core.ParaObject} subclass.
	 * Defaults to {@link com.erudika.para.core.Sysprop} if the class was not found in the core package path.
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
	 * Searches through the Para core package and {@code Config.CORE_PACKAGE_NAME} package
	 * for {@link ParaObject} subclasses and adds their names them to the map.
	 * @return a map of simple class names (lowercase) to class objects
	 */
	public static Map<String, Class<? extends ParaObject>> getCoreClassesMap() {
		if (coreClasses.isEmpty()) {
			try {
				Set<Class<? extends ParaObject>> s = scanner.getComponentClasses(ParaObject.class.getPackage().getName());
				if (!Config.CORE_PACKAGE_NAME.isEmpty()) {
					Set<Class<? extends ParaObject>> s2 = scanner.getComponentClasses(Config.CORE_PACKAGE_NAME);
					s.addAll(s2);
				}

				for (Class<? extends ParaObject> coreClass : s) {
					boolean isAbstract = Modifier.isAbstract(coreClass.getModifiers());
					boolean isInterface = Modifier.isInterface(coreClass.getModifiers());
					boolean isCoreObject = ParaObject.class.isAssignableFrom(coreClass);
					if (isCoreObject && !isAbstract && !isInterface) {
						coreClasses.put(coreClass.getSimpleName().toLowerCase(), coreClass);
					}
				}
				logger.debug("Found {} ParaObject classes: {}", coreClasses.size(), coreClasses);
			} catch (Exception ex) {
				logger.error(null, ex);
			}
		}
		return Collections.unmodifiableMap(coreClasses);
	}

	/**
	 * Helper class that lists all classes contained in a given package.
	 */
	private static class CoreClassScanner extends ClassPathScanningCandidateComponentProvider {

		public CoreClassScanner() {
			super(false);
			addIncludeFilter(new AssignableTypeFilter(ParaObject.class));
		}

		public final Set<Class<? extends ParaObject>> getComponentClasses(String basePackage) {
			basePackage = (basePackage == null) ? "" : basePackage;
			Set<Class<? extends ParaObject>> classes = new HashSet<Class<? extends ParaObject>>();
			for (BeanDefinition candidate : findCandidateComponents(basePackage)) {
				try {
					Class<? extends ParaObject> cls = (Class<? extends ParaObject>) ClassUtils.
							resolveClassName(candidate.getBeanClassName(), ClassUtils.getDefaultClassLoader());
					classes.add(cls);
				} catch (Exception ex) {
					logger.error(null, ex);
				}
			}
			return classes;
		}
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
	//				ANNOTATIONS
	/////////////////////////////////////////////

	/**
	 * Returns a list of all declared fields in a class. Transient and serialVersionUID fields are skipped.
	 * This method scans parent classes as well.
	 * @param clazz a class to scan
	 * @return a list of fields including those of the parent classes excluding the Object class.
	 */
	public static List<Field> getAllDeclaredFields(Class<? extends ParaObject> clazz) {
		LinkedList<Field> fields = new LinkedList<Field>();
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
