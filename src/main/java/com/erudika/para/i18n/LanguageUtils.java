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
package com.erudika.para.i18n;

import com.erudika.para.persistence.DAO;
import com.erudika.para.core.ParaObject;
import com.erudika.para.search.Search;
import com.erudika.para.core.Translation;
import static com.erudika.para.core.PObject.classname;
import com.erudika.para.core.Sysprop;
import com.erudika.para.utils.Config;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@Singleton
public class LanguageUtils {
	
	private static final Logger logger = LoggerFactory.getLogger(LanguageUtils.class);
	
	private HashMap<String, Locale> allLocales = new HashMap<String, Locale>();
	private HashMap<String, Integer> progressMap = new HashMap<String, Integer>();
	private Map<String, String> deflang;
	private String keyPrefix = "language".concat(Config.SEPARATOR);
	
	private Search search;
	private DAO dao;

	@Inject
	public LanguageUtils(Search search, DAO dao) {
		this.search = search;
		this.dao = dao;
		for (Object loc : LocaleUtils.availableLocaleList()) {
			Locale locale = new Locale(((Locale) loc).getLanguage());
			String locstr = locale.getLanguage();
			if (!StringUtils.isBlank(locstr)) {
				allLocales.put(locstr, locale);
				progressMap.put(locstr, 0);
			}
		}
	}
	
	public Map<String, String> readLanguage(String loc){
		if(StringUtils.isBlank(loc) || !allLocales.containsKey(loc)) return getDefaultLanguage();
		
		Sysprop s = dao.read(keyPrefix.concat(loc));
		TreeMap<String, String> lang = new TreeMap<String, String>();
		
		if(s == null || s.getProperties().isEmpty()){
			ArrayList<Translation> tlist = search.findTwoTerms(classname(Translation.class), 
					null, null, "locale", loc, "approved", true, 
					null, true, getDefaultLanguage().size());

			Sysprop saved = new Sysprop(keyPrefix.concat(loc));
			lang.putAll(getDefaultLanguage());	// copy default langmap
			int approvedCount = 0;
			
			for (Translation trans : tlist) {
				lang.put(trans.getThekey(), trans.getValue());
				saved.addProperty(trans.getThekey(), trans.getValue());
				approvedCount++;
			}
			dao.create(saved);
			updateTranslationProgressMap(loc, approvedCount);
		}else{
			Map<String, Object> loaded = s.getProperties();
			for (String key : loaded.keySet()) {
				lang.put(key, loaded.get(key).toString());
			}
		}
		return lang;
	}

	public Locale getProperLocale(String langname){
		langname = StringUtils.substring(langname, 0, 2);
		langname = (StringUtils.isBlank(langname) || !allLocales.containsKey(langname)) ?
				"en" : langname.trim().toLowerCase();
		return allLocales.get(langname);
	}

	public Map<String, String> getDefaultLanguage(){
		if(deflang == null){
			logger.warn("Default language not set.");
			deflang = new HashMap<String, String>();
		}
		return deflang;
	}
	
	public void setDefaultLanguage(Map<String, String> deflang){
		this.deflang = deflang;
	}
	
	public ArrayList<ParaObject> readAllTranslationsForKey(String locale, String key,
			MutableLong pagenum, MutableLong itemcount){
		return search.findTerm(classname(Translation.class), pagenum, itemcount, 
				DAO.CN_PARENTID, key, null, true, Config.DEFAULT_LIMIT);
	}
	
	public Set<String> getApprovedTransKeys(String locale){
		HashSet<String> approvedTransKeys = new HashSet<String>();
		if(StringUtils.isBlank(locale)) return approvedTransKeys;
		
		for (Map.Entry<String, String> entry : readLanguage(locale).entrySet()) {
			if(!getDefaultLanguage().get(entry.getKey()).equals(entry.getValue())){
				approvedTransKeys.add(entry.getKey());
			}
		}
		return approvedTransKeys;
	}

	public Map<String, Integer> getTranslationProgressMap(){
		return progressMap;
	}
	
	public Map<String, Locale> getAllLocales(){
		return allLocales;
	}
	
	public boolean approveTranslation(String locale, String key, String value){
		if(locale == null || key == null || value == null) return false;
		Sysprop s = dao.read(keyPrefix.concat(locale));
		if(s != null){
			s.addProperty(key, value);
			dao.update(s);
			updateTranslationProgressMap(locale, 1);
			return true;
		}
		return false;
	}
	
	public boolean disapproveTranslation(String locale, String key){
		if(locale == null || key == null) return false;
		Sysprop s = dao.read(keyPrefix.concat(locale));
		if(s != null){
			s.addProperty(key, getDefaultLanguage().get(key));
			dao.update(s);
			updateTranslationProgressMap(locale, -1);
			return true;
		}
		return false;
	}
	
	private void updateTranslationProgressMap(String locale, int value){
//		Map<String, Integer> src = getTranslationProgressMap();
		int cols = (value > 1) ? value : getApprovedTransKeys(locale).size();
		
		if (value == 1) {
			cols = cols + 1;
		} else if (value == -1) {
			cols = (cols == 0) ? 0 : cols - 1;
		}
		
		if(cols > getDefaultLanguage().size()) cols = getDefaultLanguage().size();
		if(getDefaultLanguage().isEmpty()){
			progressMap.put(locale, 0);
		}else{
			progressMap.put(locale, (cols / getDefaultLanguage().size()) * 100);
		}
	}
	
}
