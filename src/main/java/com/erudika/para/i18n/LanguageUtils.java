/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erudika.para.i18n;

import com.erudika.para.core.PObject;
import com.erudika.para.utils.DAO;
import com.erudika.para.utils.Search;
import com.erudika.para.utils.Utils;
import static com.erudika.para.core.PObject.classname;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.mutable.MutableLong;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
	public class LanguageUtils {
	
	public static Map<String, String> DEFAULT_LANGUAGE;
	
	public static final HashMap<String, Locale> ALL_LOCALES = new HashMap<String, Locale>();
	private static final HashMap<String, HashMap<String, String>> LANG_CACHE = new HashMap<String, HashMap<String, String>>();
	
	static {
		for (Object loc : LocaleUtils.availableLocaleList()) {
			Locale locale = new Locale(((Locale) loc).getLanguage());
			ALL_LOCALES.put(locale.getLanguage(), locale);
		}
	}
	
	public static Map<String, String> readLanguage(String loc){
		if(StringUtils.isBlank(loc) || loc.equals("en")) return getDefaultLanguage();
		else if(!ALL_LOCALES.containsKey(loc)) loc = "en";
		
		if(!LANG_CACHE.containsKey(loc)){
			ArrayList<PObject> tlist = Search.findTwoTerms(classname(Translation.class), 
					null, null, "locale", loc, "approved", Boolean.TRUE, 
					null, true, Utils.DEFAULT_LIMIT);

			HashMap<String, String> lang = new HashMap<String, String>(getDefaultLanguage().size());
			lang.putAll(getDefaultLanguage());	// copy default langmap

			for (PObject trans : tlist) {
				lang.put(((Translation) trans).getKey(), ((Translation) trans).getValue());
			}
			LANG_CACHE.put(loc, lang);
		}
		
		return LANG_CACHE.get(loc);
	}

	public static Locale getProperLocale(String langname){
		langname = StringUtils.substring(langname, 0, 2);
		langname = (StringUtils.isBlank(langname) || !ALL_LOCALES.containsKey(langname)) ?
				"en" : langname.trim().toLowerCase();
		return ALL_LOCALES.get(langname);
	}

	public static Map<String, String> getDefaultLanguage(){
		if(DEFAULT_LANGUAGE == null) throw new IllegalStateException("Default language not set.");
		return DEFAULT_LANGUAGE;
	}
	
	public static void setDefaultLanguage(Map<String, String> deflang){
		DEFAULT_LANGUAGE = deflang;
	}
	
	public static ArrayList<PObject> readAllTranslationsForKey(String locale, String key,
			MutableLong pagenum, MutableLong itemcount){
		return Search.findTerm(classname(Translation.class), pagenum, itemcount, DAO.CN_PARENTID, key, null, true, Utils.DEFAULT_LIMIT);
	}
	
	public static Set<String> getApprovedTransKeys(String locale){
		HashSet<String> approvedTransKeys = new HashSet<String>();
		if(StringUtils.isBlank(locale)) return approvedTransKeys;
		else if("en".equals(locale)) return getDefaultLanguage().keySet();
		
		for (Map.Entry<String, String> entry : readLanguage(locale).entrySet()) {
			if(!getDefaultLanguage().get(entry.getKey()).equals(entry.getValue())){
				approvedTransKeys.add(entry.getKey());
			}
		}
		return approvedTransKeys;
	}

	public static Map<String, Object> getTranslationProgressMap(){
		Map<String, Object> src = Search.getSource(DAO.SYSTEM_TRANS_PROGRESS_KEY, DAO.SYSTEM_TYPE);
		
		if(src.isEmpty()){
			for (Object loc : LocaleUtils.availableLocaleList()) {
				Locale locale = (Locale) loc;
				String locstr = locale.getLanguage();
				if(!StringUtils.isBlank(locstr)){
					if("en".equals(locstr)){
						src.put(locstr, 100);
					}else{
						src.put(locstr, 0);
					}
				}
			}
			Search.index(DAO.SYSTEM_TRANS_PROGRESS_KEY, src, DAO.SYSTEM_TYPE);
		}
		
		return src;
	}
	
	public static void onApprove(String locale, String key, String value){
		HashMap<String, String> lang = LANG_CACHE.get(locale);
		if(lang != null) lang.put(key, value);
		updateTranslationProgressMap(locale, 1);
	}
	
	public static void onDisapprove(String locale, String key){
		HashMap<String, String> lang = LANG_CACHE.get(locale);
		if(lang != null) lang.put(key, getDefaultLanguage().get(key));
		updateTranslationProgressMap(locale, -1);
	}
	
	private static void updateTranslationProgressMap(String locale, int value){
		Map<String, Object> src = getTranslationProgressMap();
		int cols = NumberUtils.toInt(src.get(locale).toString(), 0);
		
		if (value > 0) {
			cols = cols + 1;
		} else if(value < 0) {
			cols = (cols == 0) ? 0 : cols - 1;
		}
		
		if(cols > getDefaultLanguage().size()) cols = getDefaultLanguage().size();
		src.put(locale, (cols / getDefaultLanguage().size()) * 100);
		
		Search.index(DAO.SYSTEM_TRANS_PROGRESS_KEY, src, DAO.SYSTEM_TYPE);
	}
	
}
