/*
 * Copyright 2013 Alex Bogdanovski <alex@erudika.com>.
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

import com.erudika.para.core.PObject;
import com.erudika.para.persistence.DAO;
import com.erudika.para.core.ParaObject;
import com.erudika.para.search.Search;
import com.erudika.para.core.Translation;
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
 * @author Alex Bogdanovski <alex@erudika.com>
 */
@Singleton
public class LanguageUtils {
	
	private static final Logger logger = LoggerFactory.getLogger(LanguageUtils.class);
	
	private HashMap<String, Locale> allLocales = new HashMap<String, Locale>();
	private HashMap<String, Integer> progressMap = new HashMap<String, Integer>();
	private Map<String, String> deflang;
	private String deflangCode;
	private String keyPrefix = "language".concat(Config.SEPARATOR);
	private String progressKey = keyPrefix.concat("progress");
	
	private static final int PLUS = -1;	
	private static final int MINUS = -2;
	
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
	
	public Map<String, String> readLanguage(String appName, String langCode){
		if(StringUtils.isBlank(langCode) || !allLocales.containsKey(langCode)) return getDefaultLanguage();
		if(search == null || dao == null) return getDefaultLanguage();
		
		Sysprop s = dao.read(appName, keyPrefix.concat(langCode));
		TreeMap<String, String> lang = new TreeMap<String, String>();
		
		if(s == null || s.getProperties().isEmpty()){
			ArrayList<Translation> tlist = search.findTwoTerms(appName, PObject.classname(Translation.class), 
					null, null, "locale", langCode, "approved", true, 
					null, true, getDefaultLanguage().size());

			Sysprop saved = new Sysprop(keyPrefix.concat(langCode));
			lang.putAll(getDefaultLanguage());	// copy default langmap
			int approved = 0;
			
			for (Translation trans : tlist) {
				lang.put(trans.getThekey(), trans.getValue());
				saved.addProperty(trans.getThekey(), trans.getValue());
				approved++;
			}
			if(approved > 0){
				updateTranslationProgressMap(appName, langCode, approved);
			}			
			dao.create(appName, saved);
		}else{
			Map<String, Object> loaded = s.getProperties();
			for (String key : loaded.keySet()) {
				lang.put(key, loaded.get(key).toString());
			}
		}
		return lang;
	}
	
	public void writeLanguage(String appName, String langCode, Map<String, String> lang){
		if(lang == null || lang.isEmpty() || dao == null) return;
		if(StringUtils.isBlank(langCode) || !allLocales.containsKey(langCode)) return;
		
		// this will overwrite a saved language map!
		Sysprop s = new Sysprop(keyPrefix.concat(langCode));
		Map<String, String> dlang = getDefaultLanguage();
		int approved = 0;
		
		for (String key : dlang.keySet()) {
			if (lang.containsKey(key)) {
				s.addProperty(key, lang.get(key));
				if(!dlang.get(key).equals(lang.get(key))){
					approved++;
				}			
			} else {
				s.addProperty(key, dlang.get(key));
			}
		}
		if(approved > 0){
			updateTranslationProgressMap(appName, langCode, approved);
		}
		dao.create(appName, s);
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
			getDefaultLanguageCode();
		}
		return deflang;
	}
	
	public void setDefaultLanguage(Map<String, String> deflang){
		this.deflang = deflang;
	}
	
	public String getDefaultLanguageCode(){
		if(deflangCode == null){
			deflangCode = "en";
		}
		return deflangCode;
	}
	
	public void setDefaultLanguageCode(String langCode){
		this.deflangCode = langCode;
	}	
	
	public ArrayList<ParaObject> readAllTranslationsForKey(String appName, String locale, String key,
			MutableLong pagenum, MutableLong itemcount){
		return search.findTerm(appName, PObject.classname(Translation.class), pagenum, itemcount, 
				DAO.CN_PARENTID, key, null, true, Config.DEFAULT_LIMIT);
	}
	
	public Set<String> getApprovedTransKeys(String appName, String langCode){
		HashSet<String> approvedTransKeys = new HashSet<String>();
		if(StringUtils.isBlank(langCode)) return approvedTransKeys;
		
		for (Map.Entry<String, String> entry : readLanguage(appName, langCode).entrySet()) {
			if(!getDefaultLanguage().get(entry.getKey()).equals(entry.getValue())){
				approvedTransKeys.add(entry.getKey());
			}
		}
		return approvedTransKeys;
	}

	public Map<String, Integer> getTranslationProgressMap(String appName){
		if(dao == null) return progressMap;
		Sysprop progress = getProgressMap(appName);
		
		Map<String, Object> props = progress.getProperties();
		for (String key : props.keySet()) {
			progressMap.put(key, (Integer) props.get(key));
		}
		
		return progressMap;
	}
	
	public Map<String, Locale> getAllLocales(){
		return allLocales;
	}
	
	public boolean approveTranslation(String appName, String langCode, String key, String value){
		if(langCode == null || key == null || value == null) return false;
		if(getDefaultLanguageCode().equals(langCode)) return false;
		Sysprop s = dao.read(appName, keyPrefix.concat(langCode));
		
		if(s != null && !value.equals(s.getProperty(key))){
			s.addProperty(key, value);
			dao.update(appName, s);
			updateTranslationProgressMap(appName, langCode, PLUS);
			return true;
		}
		return false;
	}
	
	public boolean disapproveTranslation(String appName, String langCode, String key){
		if(langCode == null || key == null) return false;
		if(getDefaultLanguageCode().equals(langCode)) return false;
		Sysprop s = dao.read(appName, keyPrefix.concat(langCode));
		String defStr = getDefaultLanguage().get(key);
		
		if(s != null && !defStr.equals(s.getProperty(key))){
			s.addProperty(key, defStr);
			dao.update(appName, s);
			updateTranslationProgressMap(appName, langCode, MINUS);
			return true;
		}
		return false;
	}
	
	private void updateTranslationProgressMap(String appName, String langCode, int value){
		if(dao == null || getDefaultLanguageCode().equals(langCode)) return;
		
		double defsize = getDefaultLanguage().size();
		double approved = value;
		
		Sysprop progress = getProgressMap(appName);
		
		if(value == PLUS){
			approved = Math.round((int) progress.getProperty(langCode) * (defsize / 100) + 1);
		}else if(value == MINUS){
			approved = Math.round((int) progress.getProperty(langCode) * (defsize / 100) - 1);
		}
		
		if(approved > defsize) approved = defsize;
				
		if(defsize == 0){
			progress.addProperty(langCode, 0);
		}else{
			progress.addProperty(langCode, (int) ((approved / defsize) * 100));
		}
		dao.update(appName, progress);
	}
	
	private Sysprop getProgressMap(String appName){
		Sysprop progress = dao.read(appName, progressKey);
		if(progress == null){
			progress = new Sysprop(progressKey);
			for (String key : progressMap.keySet()) {
				progress.addProperty(key, progressMap.get(key));
			}
			progress.addProperty(getDefaultLanguageCode(), 100);
			dao.create(appName, progress);
		}
		return progress;
	}
}
