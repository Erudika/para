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
package com.erudika.para.core;

import java.util.ArrayList;
import org.apache.commons.lang3.mutable.MutableLong;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public interface Linkable extends ParaObject{

	public Long countLinks(Class<? extends ParaObject> c2);

	public void deleteChildren(Class<? extends ParaObject> clazz);

	public ArrayList<Linker> getAllLinks(Class<? extends ParaObject> c2);

	public ArrayList<Linker> getAllLinks(Class<? extends ParaObject> c2, MutableLong pagenum, MutableLong itemcount, boolean reverse, int maxItems);

	public <P extends ParaObject> ArrayList<P> getChildren(Class<? extends ParaObject> clazz);

	public <P extends ParaObject> ArrayList<P> getChildren(Class<? extends ParaObject> clazz, MutableLong page, MutableLong itemcount, String sortfield, int max);

	public <P extends ParaObject> ArrayList<P> getLinkedObjects(Class<? extends ParaObject> clazz, MutableLong page, MutableLong itemcount);

	public boolean isLinked(Class<? extends ParaObject> c2, String toId);
	
	public boolean isLinked(ParaObject toObj);

	public String link(Class<? extends ParaObject> c2, String id2);

	public void unlink(Class<? extends ParaObject> c2, String id2);

	public void unlinkAll();
	
}
