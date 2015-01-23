/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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
package com.erudika.para.utils;

import com.erudika.para.annotations.Email;
import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.rest.RestUtils;
import static com.erudika.para.utils.Constraint.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.constraints.AssertFalse;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Future;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Past;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper methods for validating objects and generating JSON schemas.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class ValidationUtils {

	private static final Logger logger = LoggerFactory.getLogger(ValidationUtils.class);
	private static Validator validator;

	private ValidationUtils() {
	}

	/**
	 * A Hibernate Validator.
	 *
	 * @return a validator object
	 */
	public static Validator getValidator() {
		if (validator == null) {
			validator = Validation.buildDefaultValidatorFactory().getValidator();
		}
		return validator;
	}

	/**
	 * Validates objects using Hibernate Validator.
	 *
	 * @param obj an object to be validated
	 * @return true if the object is valid (all fields are populated properly)
	 */
	public static boolean isValidObject(ParaObject obj) {
		return validateObject(obj).length == 0;
	}

	/**
	 * Validates objects using Hibernate Validator.
	 *
	 * @param content an object to be validated
	 * @return a list of error messages or empty if object is valid
	 */
	public static String[] validateObject(ParaObject content) {
		if (content == null) {
			return new String[]{"Object cannot be null."};
		}
		ArrayList<String> list = new ArrayList<String>();
		try {
			for (ConstraintViolation<ParaObject> constraintViolation : getValidator().validate(content)) {
				String prop = "'".concat(constraintViolation.getPropertyPath().toString()).concat("'");
				list.add(prop.concat(" ").concat(constraintViolation.getMessage()));
			}
		} catch (Exception e) {
			logger.error(null, e);
		}
		return list.toArray(new String[]{});
	}

	/**
	 * Validates objects.
	 *
	 * @param content an object to be validated
	 * @param app the current app
	 * @return a list of error messages or empty if object is valid
	 */
	public static String[] validateObject(App app, ParaObject content) {
		if (content == null || app == null) {
			return new String[]{"Object cannot be null."};
		}
		try {
			String type = content.getType();
			boolean isCustomType = (content instanceof Sysprop) && !type.equals(Utils.type(Sysprop.class));
			// Validate custom types and user-defined properties
			if (!app.getValidationConstraints().isEmpty() && isCustomType) {
				Map<String, Map<String, Map<String, Object>>> fieldsMap
						= app.getValidationConstraints().get(type);
				if (fieldsMap != null && !fieldsMap.isEmpty()) {
					ArrayList<String> errors = new ArrayList<String>();
					for (Map.Entry<String, Map<String, Map<String, Object>>> e : fieldsMap.entrySet()) {
						String field = e.getKey();
						Object actualValue = ((Sysprop) content).getProperty(field);
						// overriding core property validation rules is allowed
						if (actualValue == null && PropertyUtils.isReadable(content, field)) {
							actualValue = PropertyUtils.getProperty(content, field);
						}
						Map<String, Map<String, Object>> consMap = e.getValue();
						for (Map.Entry<String, Map<String, Object>> constraint : consMap.entrySet()) {
							String consName = constraint.getKey();
							Map<String, Object> vals = constraint.getValue();
							if (vals == null) {
								vals = Collections.emptyMap();
							}

							Object val = vals.get("value");
							Object min = vals.get("min");
							Object max = vals.get("max");
							Object in = vals.get("integer");
							Object fr = vals.get("fraction");

							if ("required".equals(consName) && !required().isValid(actualValue)) {
								errors.add(Utils.formatMessage("{0} is required.", field));
							} else if (matches(Min.class, consName) && !min(val).isValid(actualValue)) {
								errors.add(Utils.formatMessage("{0} must be a number larger than {1}.", field, val));
							} else if (matches(Max.class, consName) && !max(val).isValid(actualValue)) {
								errors.add(Utils.formatMessage("{0} must be a number smaller than {1}.", field, val));
							} else if (matches(Size.class, consName) && !size(min, max).isValid(actualValue)) {
								errors.add(Utils.formatMessage("{0} must be between {1} and {2}.", field, min, max));
							} else if (matches(Email.class, consName) && !email().isValid(actualValue)) {
								errors.add(Utils.formatMessage("{0} is not a valid email.", field));
							} else if (matches(Digits.class, consName) && !digits(in, fr).isValid(actualValue)) {
								errors.add(Utils.formatMessage("{0} is not a valid number or within range.", field));
							} else if (matches(Pattern.class, consName) && !pattern(val).isValid(actualValue)) {
								errors.add(Utils.formatMessage("{0} doesn't match the pattern {1}.", field, val));
							} else if (matches(AssertFalse.class, consName) && !falsy().isValid(actualValue)) {
								errors.add(Utils.formatMessage("{0} must be false.", field));
							} else if (matches(AssertTrue.class, consName) && !truthy().isValid(actualValue)) {
								errors.add(Utils.formatMessage("{0} must be true.", field));
							} else if (matches(Future.class, consName) && !future().isValid(actualValue)) {
								errors.add(Utils.formatMessage("{0} must be in the future.", field));
							} else if (matches(Past.class, consName) && !past().isValid(actualValue)) {
								errors.add(Utils.formatMessage("{0} must be in the past.", field));
							} else if (matches(URL.class, consName) && !url().isValid(actualValue)) {
								errors.add(Utils.formatMessage("{0} is not a valid URL.", field));
							}
						}
					}
					if (!errors.isEmpty()) {
						return errors.toArray(new String[0]);
					}
				}
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}
		return validateObject(content);
	}

	/**
	 * Returns the JSON Node representation of all validation constraints for a single type.
	 * @param app the app
	 * @param type a valid Para data type
	 * @return a JSON Node object
	 */
	public static Map<String, Map<String, Map<String, Object>>> getValidationConstraints(App app, String type) {
		Map<String, Map<String, Map<String, Object>>> fieldsMap
				= new HashMap<String, Map<String, Map<String, Object>>>();
		if (app != null && !StringUtils.isBlank(type)) {
			try {
				List<Field> fieldlist = Utils.getAllDeclaredFields(Utils.toClass(type));
				for (Field field : fieldlist) {
					Annotation[] annos = field.getAnnotations();
					if (annos.length > 1) {
						Map<String, Map<String, Object>> consMap = new HashMap<String, Map<String, Object>>();
						for (Annotation anno : annos) {
							if (isValidConstraintType(anno.annotationType())) {
								Constraint c = fromAnnotation(anno);
								if (c != null) {
									consMap.put(c.getName(), c.getPayload());
								}
							}
						}
						if (consMap.size() > 0) {
							fieldsMap.put(field.getName(), consMap);
						}
					}
				}
				Map<String, Map<String, Map<String, Object>>> appConstraints = app.getValidationConstraints().get(type);
				if (appConstraints != null && !appConstraints.isEmpty()) {
					fieldsMap.putAll(appConstraints);
				}
			} catch (Exception ex) {
				logger.error(null, ex);
			}
		}
		return fieldsMap;
	}

	/**
	 * Returns the JSON object containing all validation constraints for all the core Para classes and classes defined
	 * by the given app.
	 *
	 * @param app an app
	 * @return JSON string
	 */
	public static String getAllValidationConstraints(App app) {
		String json = "{}";
		try {
			ObjectNode parentNode = Utils.getJsonMapper().createObjectNode();
			for (String type : RestUtils.getAllTypes(app).values()) {
				Map<?, ?> constraintsNode = getValidationConstraints(app, type);
				if (constraintsNode.size() > 0) {
					parentNode.putPOJO(StringUtils.capitalize(type), constraintsNode);
				}
			}
			json = Utils.getJsonWriter().writeValueAsString(parentNode);
		} catch (IOException ex) {
			logger.error(null, ex);
		}
		return json;
	}
}
