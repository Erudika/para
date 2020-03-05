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

import static com.erudika.para.annotations.Email.EMAIL_PATTERN;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.Pattern;

/**
 * Annotation for email validation.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@javax.validation.constraints.Email(message = "Please provide a valid email address")
@Pattern(regexp = EMAIL_PATTERN, message = "Please provide a valid email address")
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = { })
@Documented
public @interface Email {

	/**
	 * {@value #EMAIL_PATTERN}.
	 */
	String EMAIL_PATTERN = "[A-Za-z0-9.%'+_\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z\\S]{2,20}$";

	/**
	 * Error for invalid email.
	 *
	 * @return the error message
	 */
	String message() default "Please provide a valid email address";

	/**
	 * Groups.
	 *
	 * @return the groups
	 */
	Class<?>[] groups() default { };

	/**
	 * Payload.
	 *
	 * @return the payload
	 */
	Class<? extends Payload>[] payload() default { };
}
