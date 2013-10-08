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

import com.erudika.para.api.DAO;
import com.erudika.para.api.MemoryGrid;
import com.erudika.para.api.ParaObject;
import com.erudika.para.api.Search;
import com.erudika.para.core.Translation;
import static com.erudika.para.core.PObject.classname;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class LanguageUtils {
	
	public static Map<String, String> DEFAULT_LANGUAGE;
	
	public static final HashMap<String, Locale> ALL_LOCALES = new HashMap<String, Locale>();
	private static final HashMap<String, HashMap<String, String>> LANG_CACHE = new HashMap<String, HashMap<String, String>>();
	private static final HashMap<String, Integer> PROGRESS_MAP = new HashMap<String, Integer>();
	
	private MemoryGrid memgrid = Utils.getInstanceOf(MemoryGrid.class);
	
	static {		
		for (Object loc : LocaleUtils.availableLocaleList()) {
			Locale locale = new Locale(((Locale) loc).getLanguage());
			String locstr = locale.getLanguage();
			if (!StringUtils.isBlank(locstr)) {
				ALL_LOCALES.put(locstr, locale);
				if ("en".equals(locstr)) {
					PROGRESS_MAP.put(locstr, 100);
				} else {
					PROGRESS_MAP.put(locstr, 0);
				}
			}
		}
	}
	
	public static Map<String, String> readLanguage(String loc, Search search){
		if(StringUtils.isBlank(loc) || loc.equals("en")) return getDefaultLanguage();
		else if(!ALL_LOCALES.containsKey(loc)) loc = "en";
		
		if(!LANG_CACHE.containsKey(loc)){
			ArrayList<ParaObject> tlist = search.findTwoTerms(classname(Translation.class), 
					null, null, "locale", loc, "approved", true, 
					null, true, DEFAULT_LANGUAGE.size());

			HashMap<String, String> lang = new HashMap<String, String>(getDefaultLanguage().size());
			lang.putAll(getDefaultLanguage());	// copy default langmap
			int approvedCount = 0;
			
			for (ParaObject trans : tlist) {
				lang.put(((Translation) trans).getThekey(), ((Translation) trans).getValue());
				approvedCount++;
			}
			LANG_CACHE.put(loc, lang);
			updateTranslationProgressMap(loc, approvedCount, search);
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
	
	public static ArrayList<ParaObject> readAllTranslationsForKey(String locale, String key,
			MutableLong pagenum, MutableLong itemcount, Search search){
		return search.findTerm(classname(Translation.class), pagenum, itemcount, 
				DAO.CN_PARENTID, key, null, true, Utils.DEFAULT_LIMIT);
	}
	
	public static Set<String> getApprovedTransKeys(String locale, Search search){
		HashSet<String> approvedTransKeys = new HashSet<String>();
		if(StringUtils.isBlank(locale)) return approvedTransKeys;
		else if("en".equals(locale)) return getDefaultLanguage().keySet();
		
		for (Map.Entry<String, String> entry : readLanguage(locale, search).entrySet()) {
			if(!getDefaultLanguage().get(entry.getKey()).equals(entry.getValue())){
				approvedTransKeys.add(entry.getKey());
			}
		}
		return approvedTransKeys;
	}

	public static Map<String, Integer> getTranslationProgressMap(){
		return PROGRESS_MAP;
	}
	
	public static void onApprove(String locale, String key, String value, Search search){
		HashMap<String, String> lang = LANG_CACHE.get(locale);
		if(lang != null) lang.put(key, value);
		updateTranslationProgressMap(locale, 1, search);
	}
	
	public static void onDisapprove(String locale, String key, Search search){
		HashMap<String, String> lang = LANG_CACHE.get(locale);
		if(lang != null) lang.put(key, getDefaultLanguage().get(key));
		updateTranslationProgressMap(locale, -1, search);
	}
	
	private static void updateTranslationProgressMap(String locale, int value, Search search){
//		Map<String, Integer> src = getTranslationProgressMap();
		int cols = (value > 1) ? value : getApprovedTransKeys(locale, search).size();
		
		if (value == 1) {
			cols = cols + 1;
		} else if (value == -1) {
			cols = (cols == 0) ? 0 : cols - 1;
		}
		
		if(cols > getDefaultLanguage().size()) cols = getDefaultLanguage().size();
		PROGRESS_MAP.put(locale, (cols / getDefaultLanguage().size()) * 100);
	}
	
}
