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
package com.erudika.para;

import java.lang.reflect.Method;

/**
 * Listens for create/read/update/delete events when {@link com.erudika.para.persistence.DAO} is called.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public interface IOListener {

	/**
	 * Called before an I/O (CRUD) operation has occurred.
	 * @param method the {@code DAO} method which will be invoked after this
	 * @param args the list of arguments supplied to the {@code DAO} method called
	 */
	void onPreInvoke(Method method, Object[] args);

	/**
	 * Called after an I/O (CRUD) operation has occurred.
	 * @param method the {@code DAO} method which was invoked before this
	 * @param args the list of arguments supplied to the {@code DAO} method called
	 * @param result the result of the IO operation
	 */
	void onPostInvoke(Method method, Object[] args, Object result);

}
