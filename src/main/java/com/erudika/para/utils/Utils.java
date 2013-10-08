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
package com.erudika.para.utils;

import com.erudika.para.annotations.Stored;
import com.erudika.para.annotations.Locked;
import com.erudika.para.core.PObject;
import com.erudika.para.api.ParaObject;
import com.erudika.para.impl.DefaultImplModule;
import com.erudika.para.utils.aop.AOPModule;
import com.github.rjeschke.txtmark.Configuration;
import com.github.rjeschke.txtmark.Processor;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.util.Currency;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.text.DateFormatSymbols;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Singleton;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.geonames.FeatureClass;
import org.geonames.Style;
import org.geonames.Toponym;
import org.geonames.ToponymSearchCriteria;
import org.geonames.ToponymSearchResult;
import org.geonames.WebService;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import org.jsoup.Jsoup;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@SuppressWarnings("unchecked")
@Singleton
public final class Utils {
	
	///////////////////////////////////////////////////
	private static final Map<String, Locale> COUNTRY_TO_LOCALE_MAP = new HashMap<String, Locale>();
	private static final Map<String, Locale> CURRENCY_TO_LOCALE_MAP = new HashMap<String, Locale>();
	private static final Map<String, String> CURRENCIES_MAP = new TreeMap<String, String>();
	private static final Map<String, String> INIT_PARAMS_MAP = new HashMap<String, String>();
	
	private static final Logger logger = Logger.getLogger(Utils.class.getName());
	private static final SecureRandom random = new SecureRandom();
	private static final ObjectMapper jsonMapper = new ObjectMapper();
	private static HumanTime humantime = new HumanTime();
	private static ExecutorService exec = Executors.newSingleThreadExecutor();
	private static Injector injector;
	
	// GLOBAL LIMITS	
	public static final int MAX_ITEMS_PER_PAGE = 30;
	//
	// TODO: LIMITS
	public static final int MAX_ADDRESSES = 1;
	public static final int MAX_MENUITEMS = 100;
	public static final int MAX_MENUITEMS_PRO = 10000;
	public static final int DEFAULT_RADIUS = 10;	//10km search radius
	//
	public static final int	DEFAULT_LIMIT = Integer.MAX_VALUE;
	public static final int MAX_PAGES = 10000;
	public static final int SESSION_TIMEOUT_SEC = 24 * 60 * 60;
	public static final int MAX_IMG_SIZE_PX = 800;
	public static final int MIN_PASS_LENGTH = 6;
	public static final String SEPARATOR = ":";
	public static final String DEFAULT_ENCODING = "UTF-8";
	public static final String CORE_PACKAGE = "corepackage";
	public static final String FB_PREFIX = "fb" + SEPARATOR;
	public static final String FXRATES_KEY = "fxrates";
	public static final int VOTE_LOCKED_FOR_SEC = 4 * 7 * 24 * 60 * 60; //1 month in seconds
	public static final int VOTE_LOCK_AFTER_SEC = 30; // 30 sec
	
	//////////  ID GEN VARS  ////////////// 	
	private static final long TIMER_OFFSET = 1310084584692L;
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
	/////////////////////////////////////////////
	
	static {
		initConfig();
		initLocales();
		initSnowflake();
//		initDI();
	}
	
	//////////  INITIALIZATION PARAMETERS  //////////////
	public static final String AWS_ACCESSKEY = getInitParam("awsaccesskey", "");
	public static final String AWS_SECRETKEY = getInitParam("awssecretkey", "");
	public static final String AWS_REGION = getInitParam("awsregion", "eu-west-1");
	public static final String FB_APP_ID = getInitParam("fbappid", "");
	public static final String FB_SECRET = getInitParam("fbsecret", "");
	public static final String OPENX_API_KEY = getInitParam("openxkey", "");
	public static final String GM_API_KEY = getInitParam("gmapskey", "");
	public static final String ADMIN_IDENT = getInitParam("adminident", "");
	public static final String WORKER_ID = getInitParam("workerid", "1");
	public static final String PRODUCT_NAME = getInitParam("productname", "MyApp");
	public static final String PRODUCT_NAME_NS = PRODUCT_NAME.replaceAll("\\s", "-").toLowerCase();
//	public static final String ES_HOSTS = getInitParam("eshosts", "localhost");
	public static final String CLUSTER_NAME = getInitParam("clustername", PRODUCT_NAME_NS);
	public static final String AUTH_COOKIE = getInitParam("authcookie", PRODUCT_NAME_NS.concat("-auth"));
	public static final String SUPPORT_EMAIL = getInitParam("supportemail", "support@myapp.co");
	public static final String APP_SECRET_KEY = getInitParam("appsecretkey", MD5("secret"));
	public static final String CORE_PACKAGE_NAME = getInitParam(CORE_PACKAGE, PObject.class.getPackage().getName());
	
	// read object data from index, not db
	public static final boolean READ_FROM_INDEX = Boolean.parseBoolean(getInitParam("readfromindex", "true")); 
//	public static final int READ_CAPACITY = NumberUtils.toInt(getInitParam("readcapacity", "10"));
	public static final boolean IN_PRODUCTION = Boolean.parseBoolean(getInitParam("production", "false")); 
	public static final String INDEX_ALIAS = PRODUCT_NAME_NS;
	
	public Utils() {
	}
		
	public static ObjectMapper getObjectMapper(){
		return jsonMapper;
	}
	
	public static String MD5(String s) {
		return (s == null) ? "" :  DigestUtils.md5Hex(s); 
	}
	
	public static String HMACSHA(String s, String key) {
		if(StringUtils.isBlank(s) || StringUtils.isBlank(key)) return null;
		try {
            // Get an hmac_sha1 key from the raw key bytes
            byte[] keyBytes = key.getBytes();           
            SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA256");

            // Get an hmac_sha1 Mac instance and initialize with the signing key
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);

            // Compute the hmac on input data bytes
            byte[] rawHmac = mac.doFinal(s.getBytes());

            // Convert raw bytes to Hex
            byte[] hexBytes = new Hex().encode(rawHmac);

            //  Covert array of Hex bytes to a String
            return new String(hexBytes, DEFAULT_ENCODING);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
	}
	
	public static String generateAuthToken() {
		final byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.encodeBase64URLSafeString(bytes);
	}

	public static String formatDate(Long timestamp, String format, Locale loc) {
		return DateFormatUtils.format(timestamp, format, loc);
	}

	public static String formatDate(String format, Locale loc) {
		return DateFormatUtils.format(System.currentTimeMillis(), format, loc);
	}
	
	public static String escapeJavascript(String str){
		return StringEscapeUtils.escapeEcmaScript(str);
	}
	
	public static String stripHtml(String html){
		if(html == null) return "";
		return Jsoup.parse(html).text();
	}
	
	public static String markdownToHtml(String markdownString) {
		if (StringUtils.isBlank(markdownString)) return "";
		return Processor.process(markdownString, Configuration.DEFAULT_SAFE);
	}
		
	public static <P extends ParaObject> void populate(P transObject, Map<String, String[]> paramMap) {
		if (transObject == null || paramMap.isEmpty()) return;
		Class<Locked> locked = (paramMap.containsKey("id")) ? Locked.class : null;
		HashMap<String, Object> fields = getAnnotatedFields(transObject, Stored.class, locked);
		// populate an object with converted param values from param map.
		try {
			for (Map.Entry<String, String[]> ks : paramMap.entrySet()) {
				String param = ks.getKey();
				String[] values = ks.getValue();
				String value = values[0];
				
				// filter out any params that are different from the core params
				if(fields.containsKey(param)){
					//set property WITH CONVERSION
					BeanUtils.setProperty(transObject, param, value);
				}
			}
		} catch (Exception ex) {
			logger.log(Level.SEVERE, null, ex);
		}
	}
	
	public static List<Toponym> readLocationForKeyword(String q) {
		return readLocationForKeyword(q, Style.FULL);
	}

	public static List<Toponym> readLocationForKeyword(String q, Style style) {
		List<Toponym> list = new ArrayList<Toponym> ();
		ToponymSearchResult locationSearchResult = null;
		ToponymSearchCriteria searchLocation = new ToponymSearchCriteria();
		searchLocation.setMaxRows(7);
		searchLocation.setFeatureClass(FeatureClass.P);
		searchLocation.setStyle(style);
		searchLocation.setQ(q);
		try {
			WebService.setUserName("erudika");
			locationSearchResult = WebService.search(searchLocation);
			if(locationSearchResult != null) 
				list.addAll(locationSearchResult.getToponyms());
		} catch (Exception ex) {
			logger.log(Level.SEVERE, null, ex);
		}
		return list;
	}
	
	public static int getCurrentYear() {
		return Calendar.getInstance().get(Calendar.YEAR);
	}

	public boolean typesMatch(ParaObject so){
		return (so == null) ? false : so.getClass().equals(
				Utils.toClass(so.getClassname()));
	}

	public static int round(float d) {
		return Math.round(d);
	}

	public static String stripAndTrim(String str) {
		if (StringUtils.isBlank(str)) return "";
		
		str = str.replaceAll("\\p{S}", "");
		str = str.replaceAll("\\p{P}", "");
		str = str.replaceAll("\\p{C}", "");

		return str.trim();
	}
	
	public static String spacesToDashes(String str) {
		if (StringUtils.isBlank(str)) return "";
		return stripAndTrim(str).replaceAll("\\p{Z}+","-").toLowerCase();
	}

	public static String formatMessage(String msg, Object... params){
		return MessageFormat.format(msg, params);
	}
	
	public static String formatPrice(Double price, String cur){
		String formatted = "";
		if(price != null){
			Locale locale = CURRENCY_TO_LOCALE_MAP.get(cur);
			NumberFormat f = (locale == null) ? NumberFormat.getCurrencyInstance(Locale.US) : 
					NumberFormat.getCurrencyInstance(locale);
			
			formatted = f.format(price);
		}
		return formatted;
	}
	
	public static String getCurrencyName(String cur, Locale locale){
		if(CURRENCY_TO_LOCALE_MAP.containsKey(cur.toUpperCase())){
			return com.ibm.icu.util.Currency.getInstance(cur).getName((locale == null ? Locale.US : locale), 1, new boolean[1]);
		}else{
			return "";
		}
	}
	
	public static String urlDecode(String s) {
		if (s == null) {
			return "";
		}
		String decoded = s;
		try {
			decoded = URLDecoder.decode(s, DEFAULT_ENCODING);
		} catch (UnsupportedEncodingException ex) {
			logger.log(Level.SEVERE, null, ex);
		}
		return decoded;
	}

	public static String urlEncode(String s) {
		if (s == null) {
			return "";
		}
		String encoded = s;
		try {
			encoded = URLEncoder.encode(s, DEFAULT_ENCODING);
		} catch (UnsupportedEncodingException ex) {
			logger.log(Level.SEVERE, null, ex);
		}
		return encoded;
	}

	public static String fixCSV(String s, int maxValues){
		if(StringUtils.trimToNull(s) == null) return "";
		String t = ",";
		HashSet<String> tset = new HashSet<String>();
		String[] split = s.split(",");
		int max = (maxValues == 0) ? split.length : maxValues;
		
		if(max >= split.length) max = split.length;
		
		for(int i = 0; i < max; i++) {
			String tag = split[i];
			tag = tag.replaceAll("-", " ");
			tag = stripAndTrim(tag);
			tag = tag.replaceAll(" ", "-");
			if(!tag.isEmpty() && !tset.contains(tag)){
				tset.add(tag);
				t = t.concat(tag).concat(",");
			}
		}
		return t;
	}

	// turn ,badtag, badtag  into ,cleantag,cleantag,
	public static String fixCSV(String s){
		return fixCSV(s, 0);
	}

	public static long timestamp(){
		return System.currentTimeMillis();
	}

	public static String abbreviate(String str, int max){
		return StringUtils.abbreviate(str, max);
	}

	public static String abbreviateInt(Number number, int decPlaces){
		if(number == null) return "";
		String abbrevn = number.toString();
		// 2 decimal places => 100, 3 => 1000, etc
		decPlaces = (int) Math.pow(10, decPlaces);
		// Enumerate number abbreviations
		String[] abbrev = {"K", "M", "B", "T"};
		boolean done = false;
		// Go through the array backwards, so we do the largest first
		for (int i = abbrev.length - 1; i >= 0 && !done; i--) {
			// Convert array index to "1000", "1000000", etc
			int size = (int) Math.pow(10, (i + 1) * 3);
			// If the number is bigger or equal do the abbreviation
			if(size <= number.intValue()) {
				// Here, we multiply by decPlaces, round, and then divide by decPlaces.
				// This gives us nice rounding to a particular decimal place.
				number = Math.round(number.intValue()*decPlaces/size)/decPlaces;
				// Add the letter for the abbreviation
				abbrevn = number + abbrev[i];
				// We are done... stop
				done = true;
			}
		}
		return abbrevn;
	}

	public static Class<? extends ParaObject> toClass(String classname){
		if(StringUtils.isBlank(CORE_PACKAGE_NAME)) throw new IllegalStateException("System property '"+CORE_PACKAGE+"' not set.");
		if(StringUtils.isBlank(classname)) return null;
		Class<? extends ParaObject> clazz = null;
		try {
			clazz = (Class<? extends ParaObject>) Class.forName(CORE_PACKAGE_NAME.concat(".").
					concat(StringUtils.capitalize(classname)));
		} catch (Exception ex) {
			if(ex instanceof ClassNotFoundException){
				try {
					clazz = (Class<? extends ParaObject>) Class.forName(PObject.class.getPackage().getName().concat(".").
						concat(StringUtils.capitalize(classname)));
				} catch (Exception ex1) {
					logger.severe(ex1.toString());
				}
			}
		}

		return clazz;
	}
	
	public static HumanTime getHumanTime(){
		return humantime;
	}

	public static String[] getMonths(Locale locale) {
		DateFormatSymbols dfs = DateFormatSymbols.getInstance(locale);
		return dfs.getMonths();
	}
	
	public static boolean isBadString(String s) {
		if (StringUtils.isBlank(s)) {
			return false;
		} else if (s.contains("<") ||
				s.contains(">") ||
				s.contains("&") ||
				s.contains("/")) {
			return true;
		} else {
			return false;
		}
	}
	
	public static String cleanString(String s){
		if(StringUtils.isBlank(s)) return "";
		return s.replaceAll("<", "").
				replaceAll(">", "").
				replaceAll("&", "").
				replaceAll("/", "");
	}

	public static boolean isValidURL(String url){
		return getHostFromURL(url) != null;
	}

	public static String getHostFromURL(String url){
		URL u = toURL(url);
		String host = (u == null) ? null : u.getHost();
		return host;
	}

	/*
	 * Get <scheme>:<authority>
	 */
	public static String getBaseURL(String url){
		URL u = toURL(url);
		String base = null;
		if(u != null){
			try {
				base = u.toURI().getScheme().concat("://").concat(u.getAuthority());
			} catch (URISyntaxException ex) {
				base = null;
			}
		}
		return base;
	}

	private static URL toURL(String url){
		if(StringUtils.isBlank(url)) return null;
		URL u = null;
		try {
			u = new URL(url);
		} catch (MalformedURLException e) {
			// the URL is not in a valid form
			u = null;
		}
		return u;
	}
		
	public static void setStateParam(String name, String value, HttpServletRequest req,
			HttpServletResponse res){
		setStateParam(name, value, req, res, false);
	}
	
	public static void setStateParam(String name, String value, HttpServletRequest req,
			HttpServletResponse res, boolean httpOnly){
//		HttpSession session = useSessions ? req.getSession() : null;
		setRawCookie(name, value, req, res, httpOnly, -1);
	}
	
	public static String getStateParam(String name, HttpServletRequest req, 
			HttpServletResponse res){
//		HttpSession session = useSessions ? req.getSession() : null;
		String param = getCookieValue(req, name);
		return param;
	}
	
	public static void removeStateParam(String name, HttpServletRequest req, 
			HttpServletResponse res){
//		HttpSession session = useSessions ? req.getSession() : null;
		setRawCookie(name, "", req, res, false, 0);
	}
	
	public static void setRawCookie(String name, String value, HttpServletRequest req, 
			HttpServletResponse res, boolean httpOnly, int maxAge){
//		long now = System.currentTimeMillis();
//		String date = (expire < 0) ? "Thu, 01-Jan-1970 00:00:01" : 
//				DateFormatUtils.format((expire == 0L) ? now + (SESSION_TIMEOUT_SEC * 1000) : now + expire, 
//				"EEE, dd-MMM-yyyy HH:mm:ss", TimeZone.getTimeZone("GMT"));
//		String httponly = httpOnly ? "; HttpOnly" : "";
//		String cookie = name+"="+value+"; Path=/; Expires="+date+" GMT"+httponly;
//		res.setHeader("Set-Cookie", cookie);
		if(StringUtils.isBlank(name)) return;
        Cookie cookie = new Cookie(name, value);
		cookie.setHttpOnly(httpOnly);
		cookie.setMaxAge(maxAge < 0 ? SESSION_TIMEOUT_SEC : maxAge);
		cookie.setPath("/");
		cookie.setSecure(req.isSecure());
		res.addCookie(cookie);
	}
	
	public static String getCookieValue(HttpServletRequest req, String name){
		Cookie cookies[] = req.getCookies();
        if (cookies == null || name == null || name.length() == 0) return null;
        //Otherwise, we have to do a linear scan for the cookie.
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                return cookie.getValue();
            }
        }
        return null;
	}
	
	public static String getSystemProperty(String name){
		return StringUtils.isBlank(name) ? "" : System.getProperty(name);
	}
	
	public static Map<String, String> getInitParamsMap(){
		return INIT_PARAMS_MAP;
	}
	
	public static String getInitParam(String key, String def){
		if(INIT_PARAMS_MAP.isEmpty()) initConfig();
		return INIT_PARAMS_MAP.containsKey(key) ? INIT_PARAMS_MAP.get(key) : System.getProperty(key, def);
	}
	
	protected static void initConfig(){
		try {
			Properties props = new Properties();
			InputStream in = Utils.class.getClassLoader().getResourceAsStream("init.properties");
			if(in != null){
				props.load(in);
				INIT_PARAMS_MAP.putAll((Map) props);
			}
		} catch (Exception ex) {
			logger.log(Level.SEVERE, null, ex);
		}
	}
		
	public static String arrayJoin(ArrayList<String> arr, String separator){
		return StringUtils.join(arr, separator);
	}

	public static Long toLong(MutableLong page){
		return (page != null && page.longValue() > 1) ?	page.longValue() : null;
	}
	
	public static int[] getMaxImgSize(int h, int w){
		int[] size = {h, w};
		int max = MAX_IMG_SIZE_PX;
		if(Math.max(h, w) > max){
			int ratio = (100 * max) / Math.max(h, w);
			if(h > w){
				size[0] = max;
				size[1] = (w * ratio) / 100;
			}else{
				size[0] = (h * ratio) / 100;
				size[1] = max;
			}
		}
		return size;
	}
	
	public static boolean isAjaxRequest(HttpServletRequest request) {
        return "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With")) ||
				"XMLHttpRequest".equalsIgnoreCase(request.getParameter("X-Requested-With"));
    }
	
	public static String toIndexableJSON(ParaObject so, String type, boolean hasData){
		String json = "";
		if(so == null || StringUtils.isBlank(type)) return json;
		
		JsonNode rootNode = jsonMapper.createObjectNode(); // will be of type ObjectNode
		
		try {
			((ObjectNode) rootNode).put("_id", so.getId());
			((ObjectNode) rootNode).put("_type", type);
			((ObjectNode) rootNode).put("_index", INDEX_ALIAS);
			
			if(hasData){
				((ObjectNode) rootNode).putPOJO("_data", getAnnotatedFields(so, Stored.class, null));
			}
			json = jsonMapper.writeValueAsString(rootNode);
		} catch (Exception ex) {
			logger.log(Level.SEVERE, null, ex);
		}
		
		return json;
	}
		
	public static Response getJSONResponse(final Status status, final String... errors){
		String json = "{}";
		try {
			LinkedHashMap<Object, Object> map = new LinkedHashMap<Object, Object>(){
				private static final long serialVersionUID = 1L;{
				put("code", status.getStatusCode());
				put("message", status.getReasonPhrase().concat(". ").
						concat(StringUtils.join(errors, "; ")));
			}};
			json = jsonMapper.writeValueAsString(map);
		} catch (Exception ex) {}
		
		return Response.status(status).entity(json).type(MediaType.APPLICATION_JSON).build();
	}
	
	public static boolean isValidObject(ParaObject obj){
		return validateRequest(obj).length == 0;
	}
	
	public static String[] validateRequest(ParaObject content){
		if(content == null) return new String[]{ "Object cannot be null." };
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
		ArrayList<String> list = new ArrayList<String>();
		for (ConstraintViolation<ParaObject> constraintViolation : validator.validate(content)) {
			String prop = "'".concat(constraintViolation.getPropertyPath().toString()).concat("'");
			list.add(prop.concat(" ").concat(constraintViolation.getMessage()));
		}
		return list.toArray(new String[list.size()]);
	}
	
	public static boolean isFacebookUser(String identifier){
		return NumberUtils.isDigits(identifier);
	}
	
	public static boolean isOpenidUser(String identifier){
		return StringUtils.startsWith(identifier, "http");
	}
	
	public static boolean isNormalUser(String identifier){
		return StringUtils.contains(identifier, "@");
	}
	
	public static HashMap<String, Object> getAnnotatedFields(ParaObject bean,
			Class<? extends Annotation> anno, Class<? extends Annotation> filter) {
		HashMap<String, Object> map = new HashMap<String, Object>();
		try {
			if(bean != null || bean.getClass().getSuperclass() != null){
				ArrayList<Field> fields = getAllDeclaredFields(bean.getClass());
				// filter transient fields and those without annotations
				for (Field field : fields) {
					if(!Modifier.isTransient(field.getModifiers())){
						if(field.isAnnotationPresent(anno) && ((filter == null) ? true : !field.isAnnotationPresent(filter))){
							map.put(field.getName(), PropertyUtils.getProperty(bean, field.getName()));
						}
					}
				}
			}
		} catch (Exception ex) {
			logger.log(Level.SEVERE, null, ex);
		}

		return map;
	}
	
	public static String getJSONValidationObject(String classname, List<String> fields, Map<String, String> lang){
		String root = "{}";
		if (!StringUtils.isBlank(classname)) {
			Class<? extends ParaObject> c = toClass(classname);
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
						if (!StringUtils.isBlank(one)) rs.add(one);
						if (!StringUtils.isBlank(two)) ms.add(two);
					}
				}
				if(!rs.isEmpty()){
					rules.add("'"+entry.getKey()+"':{"+arrayJoin(rs, ", ")+"}");
				}
				if(!ms.isEmpty()){
					messages.add("'"+entry.getKey()+"':{"+arrayJoin(ms, ", ")+"}");
				}
			}
			root = "{'rules':{"+arrayJoin(rules, ", ")+"}, 'messages':{"+arrayJoin(messages, ", ")+"}}";
		}
		return root;
	}
	
	private static Map<String, List<Annotation>> getAnnotationsMap(Class<? extends ParaObject> clazz, Set<String> fields){
		HashMap<String, List<Annotation>> map = new HashMap<String, List<Annotation>>();
		try {
			ArrayList<Field> fieldlist = getAllDeclaredFields(clazz);
			
			if (fields != null && fields.size() > 0) {
				for (Iterator<Field> it = fieldlist.iterator(); it.hasNext();) {
					Field field = it.next();
					if(!fields.contains(field.getName())){
						it.remove();
					}
				}
			}
			
			for (Field field : fieldlist) {
				Annotation[] annos = field.getAnnotations();
				if(annos.length > 1){
					ArrayList<Annotation> list = new ArrayList<Annotation>();
					for (Annotation annotation : annos) {
						if(!annotation.annotationType().equals(Stored.class)){
							list.add(annotation);
						}
					}
					map.put(field.getName(), list);
				}
			}
		} catch (Exception ex) {
			logger.log(Level.SEVERE, null, ex);
		}
		return map;
	}
	
	private static ArrayList<Field> getAllDeclaredFields(Class<? extends ParaObject> clazz){
		ArrayList<Field> fields = new ArrayList<Field>();
		fields.addAll(Arrays.asList(ParaObject.class.getDeclaredFields()));
		fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
		Class<?> parent = clazz.getSuperclass();
		// traverse back to superclass adding all the subclasses' declared fields 
		while(!parent.equals(Object.class)){
			fields.addAll(Arrays.asList(parent.getDeclaredFields()));
			parent = parent.getSuperclass();
		}
		return fields;
	}
	
	private static String[] annotationToValidation(Annotation ano,  Map<String, String> lang){
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
				logger.log(Level.SEVERE, null, ex);
			}
			
		} else if (atype.equals(Email.class)) {
			rule = "'email': true";
			msg = "'email': '" + lang.get("bademail") + "'";
		}
		return new String[]{rule, msg};
	}
	
	public static Future<?> asyncExecute(Callable<?> callable){
		try {
			return exec.submit(callable);
		} catch (Exception ex) {
			logger.log(Level.WARNING, null, ex);
			return null;
		}
	}
	
	// rubbish but quick
	public static String pluralToSingular(String plural){
		return StringUtils.isBlank(plural) ? plural : 
				(plural.endsWith("ses") ? StringUtils.removeEnd(plural, "es") : 
				(plural.endsWith("ies") ? StringUtils.removeEnd(plural, "y") : 
										  StringUtils.removeEnd(plural, "s") ) );
	}
	
	public static String singularToPlural(String singul){
		return StringUtils.isBlank(singul) ? singul : 
				(singul.endsWith("s") ? singul + "es" : 
				(singul.endsWith("y") ? singul + "ies" : singul + "s" ) );
	}
	
	public static List<String> csvToKeys(String csv){
		if(StringUtils.isBlank(csv)) return new ArrayList<String> ();
		ArrayList<String> list = new ArrayList<String>();
		
		for (String str : csv.split(",")) {
			list.add(str.trim());
		}
		
		return list;
	}
	
	public static Locale getLocaleForCountry(String countryCode){
		return COUNTRY_TO_LOCALE_MAP.get(countryCode);
	}
	
	private static void initLocales(){
		Locale[] locales = Locale.getAvailableLocales();
		for (Locale l : locales) {
			COUNTRY_TO_LOCALE_MAP.put(l.getCountry(), l); 
            try {
				Currency c = Currency.getInstance(l);
				if(c != null){
					CURRENCY_TO_LOCALE_MAP.put(c.getCurrencyCode(), l);
					CURRENCIES_MAP.put(c.getCurrencyCode(), 
							getCurrencyName(c.getCurrencyCode(), Locale.US).concat(" ").concat(c.getSymbol(l)));
				}
            } catch (Exception e) {}
		}
	}
	
	public static Currency getCurrency(String cur){
		if(StringUtils.isBlank(cur)) return null;
		Currency currency = null;
		try {
			currency = Currency.getInstance(cur);
		} catch (Exception e) {}
		return currency;
	}
	
	public static Locale getLocaleForCurrency(String cur){
		return (StringUtils.isBlank(cur) || !CURRENCY_TO_LOCALE_MAP.containsKey(cur)) ? 
				Locale.US : CURRENCY_TO_LOCALE_MAP.get(cur);
	}

	public static Map<String, String> getCurrenciesMap(){
		return CURRENCIES_MAP;
	}
	
	public static boolean isValidCurrency(String cur){
		return cur != null && CURRENCIES_MAP.containsKey(cur);
	}
	
	private static void initSnowflake(){
		String workerID = Utils.WORKER_ID;
		workerId = NumberUtils.toLong(workerID, 1);
				
		if (workerId > maxWorkerId || workerId < 0) {
			workerId = new Random().nextInt((int) maxWorkerId + 1);
		}

//		if (dataCenterId > maxDataCenterId || dataCenterId < 0) {
//			dataCenterId =  new Random().nextInt((int) maxDataCenterId+1);
//		}
	}
	
	public static synchronized String getNewId() {
		// NEW version - unique across JVMs as long as each has a different workerID
		// based on Twitter's Snowflake algorithm
		long timestamp = System.currentTimeMillis();

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
																	 (sequence) );
		
	}
	
	private static long tilNextMillis(long lastTimestamp) {
		long timestamp = System.currentTimeMillis();

		while (timestamp <= lastTimestamp) {
			timestamp = System.currentTimeMillis();
		}

		return timestamp;
	}
	
	public static void attachShutdownHook(final Class<?> clazz, final Thread run){
		try {
			Runtime.getRuntime().addShutdownHook(run);
			logger.log(Level.INFO, "Shutdown hook added: {0}", clazz.getCanonicalName());
		} catch (Exception e) {
			logger.log(Level.WARNING, "Failed to attach hook: {0}", new Object[]{e});
		}
	}
	
	private static void initDI(){
		try {
			injector = Guice.createInjector(new DefaultImplModule(), new AOPModule());
		} catch (Exception e) {
			logger.log(Level.SEVERE, null, e);
		}		
	}
	
	public static <T> T getInstanceOf(Class<T> clazz){
		if(injector == null) initDI();
		return injector.getInstance(clazz);
	}
	
	/********************************************
	 *	    	REST FUNCTIONS
	********************************************/
	
	public static <P extends ParaObject> Response getReadResponse(ParaObject content){
		if(content != null){
			return Response.ok(content).build();
		}else{
			return Utils.getJSONResponse(Response.Status.NOT_FOUND);
		}
	}
		
	public static <P extends ParaObject> Response getCreateResponse(P content, UriInfo context){
		String[] errors = Utils.validateRequest(content);
		if (errors.length == 0) {
			String id = content.create();
			return Response.created(context.getAbsolutePathBuilder().path(id).
					build()).entity(content).build();
		}else{
			return Utils.getJSONResponse(Response.Status.BAD_REQUEST, errors);
		}
	}
	
	public static <P extends ParaObject> Response getUpdateResponse(P object, P newContent){
		if(object != null){
			HashMap<String, Object> propsMap = Utils.getAnnotatedFields(newContent, Stored.class, Locked.class);
			try {
				for (Map.Entry<String, Object> entry : propsMap.entrySet()) {
					if(entry.getValue() != null){
						BeanUtils.setProperty(object, entry.getKey(), entry.getValue());
					}
				}
			} catch (Exception ex) { /* who cares? */ }
			
			String[] errors = Utils.validateRequest(object);
			if (errors.length == 0) {
				object.update();
				return Response.ok(object).build();
			} else {
				return Utils.getJSONResponse(Response.Status.BAD_REQUEST, errors);
			}
		}else{
			return Utils.getJSONResponse(Response.Status.NOT_FOUND);
		}
	}
	
	public static <P extends ParaObject> Response getDeleteResponse(ParaObject content){
		if(content != null && content.getId() != null){
			content.delete();
			return Response.ok().build();
		}else{
			return Utils.getJSONResponse(Response.Status.BAD_REQUEST);
		}
	}
}
