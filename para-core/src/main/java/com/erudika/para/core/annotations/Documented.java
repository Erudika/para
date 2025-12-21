/*
 * Copyright 2013-2026 Erudika. http://erudika.com
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
package com.erudika.para.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A generic annotation intended to be used for attaching documentation
 * metadata to the getter methods of configuration properties.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Documented {

	/**
	 * @return the category which this property is part of.
	 */
	String category() default "main";

	/**
	 * @return basic description of the config property.
	 */
	String description() default "";

	/**
	 * @return the data type of the config value.
	 */
	Class<?> type() default String.class;

	/**
	 * @return a default or suggested value.
	 */
	String value() default "";

	/**
	 * @return the config property key (identifier).
	 */
	String identifier() default "";

	/**
	 * @return position number for sorting.
	 */
	int position() default Integer.MAX_VALUE;

	/**
	 * @return additional tags.
	 */
	String[] tags() default {};

}
