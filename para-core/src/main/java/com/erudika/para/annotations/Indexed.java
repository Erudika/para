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
package com.erudika.para.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that marks methods that can modify the search index. Mainly used for weaving through AOP.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Indexed {

	/**
	 * The search action performed.
	 */
	enum Action {
		/**
		 * No operation. Do nothing.
		 */
		NOOP,
		/**
		 * Adds an object to index.
		 */
		ADD,
		/**
		 * Removes object from index.
		 */
		REMOVE,
		/**
		 * Adds many objects to index.
		 */
		ADD_ALL,
		/**
		 * Removes many objects from index.
		 */
		REMOVE_ALL;
	}

	/**
	 * @return the action
	 */
	Action action() default Action.NOOP;
}
