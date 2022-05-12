/*
 * Copyright 2013-2022 Erudika. https://erudika.com
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

import com.erudika.para.core.ParaObject;
import com.erudika.para.core.annotations.Email;
import com.samskivert.mustache.Mustache;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.emoji.EmojiExtension;
import com.vladsch.flexmark.ext.emoji.EmojiImageType;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.media.tags.MediaTagsExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.AttributeProvider;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.IndependentAttributeProviderFactory;
import com.vladsch.flexmark.html.renderer.AttributablePart;
import com.vladsch.flexmark.html.renderer.LinkResolverContext;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.html.MutableAttributes;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.validation.constraints.NotNull;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Miscellaneous Para utilities.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@SuppressWarnings("unchecked")
public final class Utils {

	private static final Logger logger = LoggerFactory.getLogger(Utils.class);
	// maps lowercase simple names to class objects
	private static final Pattern EMAIL_PATTERN = Pattern.compile(Email.EMAIL_PATTERN);
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance();
	private static final Safelist SAFE_HTML_TAGS = getHTMLTagsWhitelist();
	private static final MutableDataHolder MD_OPTIONS = getMarkdownOptions();
	private static final Parser MD_PARSER = Parser.builder(MD_OPTIONS).build();
	private static final HtmlRenderer HTML_RENDERER_STRICT = HtmlRenderer.builder(MD_OPTIONS).build();
	private static final HtmlRenderer HTML_RENDERER_LOOSE = HtmlRenderer.
			builder(MD_OPTIONS.set(HtmlRenderer.ESCAPE_HTML, false)).build();

	private static HumanTime humantime;
	private static Utils instance;

	//////////  ID GEN VARS  //////////////
	private static final long TIMER_OFFSET = 1310084584692L; // ~July 2011
	private static final long WORKER_ID_BITS = 5L;
	private static final long DATACENTER_ID_BITS = 5L;
	private static final long MAX_WORKER_ID = -1L ^ (-1L << WORKER_ID_BITS);
	private static final long MAX_DATACENTER_ID = -1L ^ (-1L << DATACENTER_ID_BITS);
	private static final long SEQUENCE_BITS = 12L;
	private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
	private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
	private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
	private static final long SEQUENCE_MASK = -1L ^ (-1L << SEQUENCE_BITS);
	private static long lastTimestamp = -1L;
	private static long dataCenterId = 0L;	// only one datacenter atm
	private static long workerId;	// max 1024
	private static long sequence = 0L;

	static {
		initIdGenerator();
		NUMBER_FORMAT.setMinimumFractionDigits(2);
		NUMBER_FORMAT.setMaximumFractionDigits(2);
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
	 * HumanTime - a relative time formatter.
	 * @return humantime instance
	 */
	public static HumanTime getHumanTime() {
		if (humantime == null) {
			humantime = new HumanTime();
		}
		return humantime;
	}

	/////////////////////////////////////////////
	//	    	   INIT FUNCTIONS
	/////////////////////////////////////////////

	private static void initIdGenerator() {
		String workerID = Para.getConfig().workerId();
		workerId = NumberUtils.toLong(workerID, 1);

		if (workerId > MAX_WORKER_ID || workerId < 0) {
			workerId = ThreadLocalRandom.current().nextInt((int) MAX_WORKER_ID + 1);
		}

		if (dataCenterId > MAX_DATACENTER_ID || dataCenterId < 0) {
			dataCenterId =  ThreadLocalRandom.current().nextInt((int) MAX_DATACENTER_ID + 1);
		}
	}

	/////////////////////////////////////////////
	//	    	   HASH UTILS
	/////////////////////////////////////////////

	/**
	 * md5 hash function.
	 * @param s the string to be hashed
	 * @return an md5 hash
	 */
	public static String md5(String s) {
		if (s == null) {
			return "";
		}
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(s.getBytes());

			byte[] byteData = md.digest();

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
	 * Computes the HmacSHA256 hash of a message.
	 * @param message a message as UTF-8 encoded string
	 * @param secret a secret key
	 * @return base64(hmacSHA256(message, secret))
	 */
	public static String hmacSHA256(String message, String secret) {
		try {
			Mac hmac = Mac.getInstance("HmacSHA256");
			hmac.init(new SecretKeySpec(secret.getBytes(Para.getConfig().defaultEncoding()), "HmacSHA256"));
			return Utils.base64enc(hmac.doFinal(message.getBytes(Para.getConfig().defaultEncoding())));
		} catch (Exception e) {
			return "";
		}
	}

	/**
	 * bcrypt hash function implemented by Spring Security.
	 *
	 * @param s the string to be hashed
	 * @return the hash
	 */
	public static String bcrypt(String s) {
		return (s == null) ? s : BCrypt.hashpw(s, BCrypt.gensalt(12));
	}

	/**
	 * Checks if a hash matches a string.
	 *
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
	 * Escapes JavaScript.
	 * @param str a javascript string
	 * @return the escaped javascript string
	 */
	public static String escapeJavascript(String str) {
		return (str == null) ? "" : StringEscapeUtils.escapeEcmaScript(str);
	}

	/**
	 * Strips all HTML tags from a string.
	 * @param html HTML string
	 * @return just the text
	 */
	public static String stripHtml(String html) {
		return (html == null) ? "" : Jsoup.parse(html).text();
	}

	/**
	 * Converts Markdown to HTML.
	 * @param markdownString Markdown
	 * @return HTML
	 */
	public static String markdownToHtml(String markdownString) {
		return markdownToHtml(markdownString, false);
	}

	/**
	 * Converts Markdown to HTML.
	 * @param markdownString Markdown
	 * @param htmlTagsRenderingEnabled if true, basic HTML tags will be rendered instead of escaped
	 * @return HTML
	 */
	public static String markdownToHtml(String markdownString, boolean htmlTagsRenderingEnabled) {
		if (StringUtils.isBlank(markdownString)) {
			return "";
		}
		Document parsed = MD_PARSER.parse(markdownString);
		if (htmlTagsRenderingEnabled) {
			return Jsoup.clean(HTML_RENDERER_LOOSE.render(parsed), SAFE_HTML_TAGS);
		} else {
			return HTML_RENDERER_STRICT.render(parsed);
		}
	}

	/**
	 * Compiles a mustache template with a given scope (map of fields and values).
	 * @param context a map of fields and values
	 * @param template a Mustache template
	 * @return the compiled template string
	 */
	public static String compileMustache(Map<String, Object> context, String template) {
		if (context == null || StringUtils.isBlank(template)) {
			return "";
		}
		Writer writer = new StringWriter();
		try {
			Mustache.compiler().escapeHTML(false).emptyStringIsFalse(true).compile(template).execute(context, writer);
		} finally {
			try {
				writer.close();
			} catch (IOException e) {
				logger.error(null, e);
			}
		}
		return writer.toString();
	}

	/**
	 * Abbreviates a string.
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
		return stripAndTrim(str, replaceWith, false);
	}

	/**
	 * Strips all symbols, punctuation, whitespace and control chars from a string.
	 * @param str a dirty string
	 * @param replaceWith a string to replace spaces with
	 * @param asciiOnly if true, all non-ASCII characters will be stripped
	 * @return a clean string
	 */
	public static String stripAndTrim(String str, String replaceWith, boolean asciiOnly) {
		if (StringUtils.isBlank(str)) {
			return "";
		}
		String s = str;
		if (asciiOnly) {
			s = str.replaceAll("[^\\p{ASCII}]", "");
		}
		return s.replaceAll("[\\p{S}\\p{P}\\p{C}]", replaceWith).replaceAll("\\p{Z}+", " ").trim();
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
			// required by MessageFormat, single quotes break string interpolation!
			msg = StringUtils.replace(msg, "'", "''");
			return StringUtils.isBlank(msg) ? "" : MessageFormat.format(msg, params);
		} catch (IllegalArgumentException e) {
			return msg;
		}
	}

	/**
	 * Encodes a byte array to Base64.
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
	 * Decodes a string from Base64.
	 * @param str the encoded string
	 * @return a decoded string
	 */
	public static String base64dec(String str) {
		if (str == null) {
			return "";
		}
		try {
			return new String(Base64.decodeBase64(str), Para.getConfig().defaultEncoding());
		} catch (Exception ex) {
			logger.error("Failed to decode base64 string '{}'.", str, ex);
		}
		return "";
	}

	/////////////////////////////////////////////
	//	    	   MARKDOWN UTILS
	/////////////////////////////////////////////

	private static MutableDataHolder getMarkdownOptions() {
		return new MutableDataSet()
				.set(HtmlRenderer.ESCAPE_HTML, true)
				.set(HtmlRenderer.SUPPRESSED_LINKS, "(?i)javascript:.*")
				.set(HtmlRenderer.SOFT_BREAK, Para.getConfig().markdownSoftBreak())
				.set(HtmlRenderer.AUTOLINK_WWW_PREFIX, "https://")
				.set(EmojiExtension.USE_IMAGE_TYPE, EmojiImageType.UNICODE_FALLBACK_TO_IMAGE)
				// for full GFM table compatibility add the following table extension options:
				.set(TablesExtension.COLUMN_SPANS, false)
				.set(TablesExtension.APPEND_MISSING_COLUMNS, true)
				.set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
				.set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)
				.set(Parser.EXTENSIONS, Arrays.asList(
						TablesExtension.create(),
						EmojiExtension.create(),
						StrikethroughExtension.create(),
						TaskListExtension.create(),
						AutolinkExtension.create(),
						RelAttributeExtension.create(),
						MediaTagsExtension.create()));
	}

	private static Safelist getHTMLTagsWhitelist() {
		Safelist whitelist = Safelist.relaxed();
		whitelist.addTags("abbr", "hr", "del", "details", "summary", "center", "audio", "video", "source");
		whitelist.addProtocols("a", "href", "#");
		whitelist.addEnforcedAttribute("a", "rel", "nofollow noreferrer");
		whitelist.addAttributes("abbr", "title");
		whitelist.addAttributes("th", "align");
		whitelist.addAttributes("td", "align");
		whitelist.addAttributes("code", "class");
		whitelist.addAttributes("div", "class");
		whitelist.addAttributes("a", "rel");
		whitelist.addAttributes("audio", "controls", "autoplay", "muted", "loop");
		whitelist.addAttributes("video", "controls", "autoplay", "muted", "playsinline", "loop");
		whitelist.addAttributes("source", "src", "type");
		whitelist.addAttributes("details", "class", "open");
		whitelist.addAttributes("h1", "id");
		whitelist.addAttributes("h2", "id");
		whitelist.addAttributes("h3", "id");
		whitelist.addAttributes("h4", "id");
		whitelist.addAttributes("h5", "id");
		whitelist.addAttributes("h6", "id");
		whitelist.addAttributes("h7", "id");
		return whitelist;
	}

	static class RelAttributeExtension implements HtmlRenderer.HtmlRendererExtension {

		public void rendererOptions(@NotNull MutableDataHolder options) { }

		public void extend(@NotNull HtmlRenderer.Builder htmlRendererBuilder, @NotNull String rendererType) {
			htmlRendererBuilder.attributeProviderFactory(new IndependentAttributeProviderFactory() {
				public AttributeProvider apply(@NotNull LinkResolverContext context) {
					return new RelAttributeProvider();
				}
			});
		}

		static RelAttributeExtension create() {
			return new RelAttributeExtension();
		}
	}

	static class RelAttributeProvider implements AttributeProvider {
		public void setAttributes(@NotNull Node node, @NotNull AttributablePart part, @NotNull MutableAttributes attributes) {
			if (node instanceof Link && part == AttributablePart.LINK) {
				attributes.replaceValue("rel", "nofollow noreferrer");
			}
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
			format = DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT.getPattern();
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
	 * Formats the date for today, in a specific format.
	 * @param format the date format
	 * @param loc the locale instance
	 * @return today's date formatted
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
	 * Java timestamp.
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

	/**
	 * @param localeStr locale string
	 * @return a {@link Locale} instance from a locale string.
	 */
	public static Locale getLocale(String localeStr) {
		try {
			return LocaleUtils.toLocale(localeStr);
		} catch (Exception e) {
			return Locale.US;
		}
	}

	/////////////////////////////////////////////
	//	    	   NUMBER UTILS
	/////////////////////////////////////////////

	/**
	 * Rounds a float to an int.
	 * @param d a float
	 * @return a rounded int
	 */
	public static int round(float d) {
		return Math.round(d);
	}

	/**
	 * Returns the price with two fractional digits at the end.
	 * @param price a price
	 * @return $###.##
	 */
	public static String formatPrice(double price) {
		return NUMBER_FORMAT.format(price);
	}

	/**
	 * Round up a double using the "half up" method.
	 * @param d a double
	 * @return a double
	 */
	public static double roundHalfUp(double d) {
		return roundHalfUp(d, 2);
	}

	/**
	 * Round up a double using the "half up" method.
	 * @param d a double
	 * @param scale the scale
	 * @return a double
	 */
	public static double roundHalfUp(double d, int scale) {
		return BigDecimal.valueOf(d).setScale(scale, RoundingMode.HALF_UP).doubleValue();
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
		String[] abbrev = {"K", "M", "B", "T"};
		boolean done = false;
		// Go through the array backwards, so we do the largest first
		for (int i = abbrev.length - 1; i >= 0 && !done; i--) {
			// Convert array index to "1000", "1000000", etc
			int size = (int) Math.pow(10, (double) (i + 1) * 3);
			// If the number is bigger or equal do the abbreviation
			if (size <= number.intValue()) {
				// Here, we multiply by decPlaces, round, and then divide by decPlaces.
				// This gives us nice rounding to a particular decimal place.
				number = Math.round(number.intValue() * decPlaces / (float) size) / decPlaces;
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
	 * Decodes a URL-encoded string.
	 * @param s a string
	 * @return the decoded string
	 */
	public static String urlDecode(String s) {
		if (s == null) {
			return "";
		}
		String decoded = s;
		try {
			decoded = URLDecoder.decode(s, Para.getConfig().defaultEncoding());
		} catch (UnsupportedEncodingException ex) {
			logger.error(null, ex);
		}
		return decoded;
	}

	/**
	 * URL-encodes a string.
	 * @param s a string
	 * @return the encoded string
	 */
	public static String urlEncode(String s) {
		if (s == null) {
			return "";
		}
		String encoded = s;
		try {
			encoded = URLEncoder.encode(s, Para.getConfig().defaultEncoding());
		} catch (UnsupportedEncodingException ex) {
			logger.error(null, ex);
		}
		return encoded;
	}

	/**
	 * URL validation.
	 * @param url a URL
	 * @return true if the URL is valid
	 */
	public static boolean isValidURL(String url) {
		return toURL(url) != null;
	}

	/**
	 * Email validation.
	 * @param email email address
	 * @return true if the URL is valid
	 */
	public static boolean isValidEmail(String email) {
		return email != null && EMAIL_PATTERN.matcher(email).matches();
	}

	/**
	 * Returns the host part of the URL.
	 * @param url a URL
	 * @return just the host
	 */
	public static String getHostFromURL(String url) {
		URL u = toURL(url);
		String host = (u == null) ? "" : u.getHost();
		return host;
	}

	/**
	 * The basic URL without any parameters: &gt;scheme&lt;:&gt;authority&lt;.
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
	//    	        MISC UTILS
	/////////////////////////////////////////////

	/**
	 * Same as {@link java.lang.System#getProperty(java.lang.String)}.
	 * @param name the name of the property
	 * @return the property value
	 */
	public static String getSystemProperty(String name) {
		return StringUtils.isBlank(name) ? "" : System.getProperty(name);
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
	 * Quick and dirty singular to plural conversion.
	 * @param singul a word
	 * @return a guess of its plural form
	 */
	public static String singularToPlural(String singul) {
		if (!StringUtils.isAsciiPrintable(singul)) {
			return singul;
		}
		return (StringUtils.isBlank(singul) || singul.endsWith("es") || singul.endsWith("ies")) ? singul :
				(singul.endsWith("s") ? singul + "es" :
				(singul.endsWith("y") ? StringUtils.removeEndIgnoreCase(singul, "y") + "ies" :
										singul + "s"));
	}

	/**
	 * Checks if a class is primitive, String or a primitive wrapper.
	 *
	 * @param clazz a class
	 * @return true if primitive or wrapper
	 */
	public static boolean isBasicType(Class<?> clazz) {
		return (clazz == null) ? false : (clazz.isPrimitive()
				|| clazz.equals(String.class)
				|| clazz.equals(Long.class)
				|| clazz.equals(Integer.class)
				|| clazz.equals(Boolean.class)
				|| clazz.equals(Byte.class)
				|| clazz.equals(Short.class)
				|| clazz.equals(Float.class)
				|| clazz.equals(Double.class)
				|| clazz.equals(Character.class));
	}

	/**
	 * Returns the simple name of a class in lowercase (AKA the type).
	 *
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
		LinkedList<Field> fields = new LinkedList<>();
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
	public static synchronized String getNewId() {
		// unique across JVMs as long as each has a different workerID
		// based on Twitter's Snowflake algorithm
		long timestamp = timestamp();

		if (lastTimestamp == timestamp) {
			sequence = (sequence + 1) & SEQUENCE_MASK;
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
		return Long.toString(((timestamp - TIMER_OFFSET) << TIMESTAMP_LEFT_SHIFT) |
											(dataCenterId << DATACENTER_ID_SHIFT) |
													(workerId << WORKER_ID_SHIFT) |
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
