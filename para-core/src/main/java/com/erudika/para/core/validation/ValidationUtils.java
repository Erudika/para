/*
 * Copyright 2013-2022 Erudika. https://erudika.com
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
package com.erudika.para.core.validation;

import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.User;
import com.erudika.para.core.annotations.Email;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.utils.Utils;
import static com.erudika.para.core.validation.Constraint.digits;
import static com.erudika.para.core.validation.Constraint.email;
import static com.erudika.para.core.validation.Constraint.falsy;
import static com.erudika.para.core.validation.Constraint.fromAnnotation;
import static com.erudika.para.core.validation.Constraint.future;
import static com.erudika.para.core.validation.Constraint.isValidConstraintType;
import static com.erudika.para.core.validation.Constraint.matches;
import static com.erudika.para.core.validation.Constraint.max;
import static com.erudika.para.core.validation.Constraint.min;
import static com.erudika.para.core.validation.Constraint.past;
import static com.erudika.para.core.validation.Constraint.pattern;
import static com.erudika.para.core.validation.Constraint.required;
import static com.erudika.para.core.validation.Constraint.size;
import static com.erudika.para.core.validation.Constraint.truthy;
import static com.erudika.para.core.validation.Constraint.url;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
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
import org.apache.commons.lang3.math.NumberUtils;
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
	private static final Map<String, Map<String, Map<String, Map<String, ?>>>> CORE_CONSTRAINTS =
			new HashMap<String, Map<String, Map<String, Map<String, ?>>>>();
	private static Validator validator;

	private ValidationUtils() {
	}

	/**
	 * A Hibernate Validator.
	 * @return a validator object
	 */
	public static Validator getValidator() {
		if (validator == null) {
			validator = Validation.buildDefaultValidatorFactory().getValidator();
		}
		return validator;
	}

	/**
	 * Validates objects using Hibernate Validator. Used for basic validation.
	 * @param obj an object to be validated
	 * @return true if the object is valid (all fields are populated properly)
	 */
	public static boolean isValidObject(ParaObject obj) {
		return validateObject(obj).length == 0;
	}

	/**
	 * Validates objects using Hibernate Validator. Used for full object validation.
	 * @param app the current app
	 * @param obj an object to be validated
	 * @return true if the object is valid (all fields are populated properly)
	 */
	public static boolean isValidObject(App app, ParaObject obj) {
		return validateObject(app, obj).length == 0;
	}

	/**
	 * Validates objects using Hibernate Validator.
	 * @param content an object to be validated
	 * @return a list of error messages or empty if object is valid
	 */
	public static String[] validateObject(ParaObject content) {
		if (content == null) {
			return new String[]{"Object cannot be null."};
		}
		LinkedList<String> list = new LinkedList<>();
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
				Map<String, Map<String, Map<String, ?>>> fieldsMap = app.getValidationConstraints().get(type);
				if (fieldsMap != null && !fieldsMap.isEmpty()) {
					LinkedList<String> errors = new LinkedList<>();
					for (Map.Entry<String, Map<String, Map<String, ?>>> e : fieldsMap.entrySet()) {
						String field = e.getKey();
						Object actualValue = ((Sysprop) content).getProperty(field);
						// overriding core property validation rules is allowed
						if (actualValue == null && PropertyUtils.isReadable(content, field)) {
							actualValue = PropertyUtils.getProperty(content, field);
						}
						Map<String, Map<String, ?>> consMap = e.getValue();
						for (Map.Entry<String, Map<String, ?>> constraint : consMap.entrySet()) {
							buildAndValidateConstraint(constraint, field, actualValue, errors);
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

	private static void buildAndValidateConstraint(Map.Entry<String, Map<String, ?>> constraint, String field,
			Object actualValue, LinkedList<String> errors) {
		if (constraint == null) {
			return;
		}
		String consName = constraint.getKey();
		Map<String, ?> vals = constraint.getValue();
		if (vals == null) {
			vals = Collections.emptyMap();
		}

		Object val = vals.get("value");
		long min = NumberUtils.toLong(vals.get("min") + "", 0);
		long max = NumberUtils.toLong(vals.get("max") + "", Config.DEFAULT_LIMIT);

		if (isValidSimpleConstraint(consName, field, actualValue, errors)) {
			if (matches(Min.class, consName) && !min(NumberUtils.toLong(val + "", 0)).isValid(actualValue)) {
				errors.add(Utils.formatMessage("{0} must be a number larger than {1}.", field, val));
			} else if (matches(Max.class, consName) && !max(NumberUtils.toLong(val + "",
					Config.DEFAULT_LIMIT)).isValid(actualValue)) {
				errors.add(Utils.formatMessage("{0} must be a number smaller than {1}.", field, val));
			} else if (matches(Size.class, consName) && !size(min, max).isValid(actualValue)) {
				errors.add(Utils.formatMessage("{0} must be between {1} and {2}.", field, min, max));
			} else if (matches(Digits.class, consName) && !digits(NumberUtils.toLong(vals.get("integer") + "", 0),
					NumberUtils.toLong(vals.get("fraction") + "", 0)).isValid(actualValue)) {
				errors.add(Utils.formatMessage("{0} is not a valid number or within range.", field));
			} else if (matches(Pattern.class, consName) && !pattern(val).isValid(actualValue)) {
				errors.add(Utils.formatMessage("{0} doesn't match the pattern {1}.", field, val));
			}
		}
	}

	private static boolean isValidSimpleConstraint(String cName, String field, Object actual, LinkedList<String> err) {
		if ("required".equals(cName) && !required().isValid(actual)) {
			err.add(Utils.formatMessage("{0} is required.", field));
			return false;
		} else if (matches(AssertFalse.class, cName) && !falsy().isValid(actual)) {
			err.add(Utils.formatMessage("{0} must be false.", field));
			return false;
		} else if (matches(AssertTrue.class, cName) && !truthy().isValid(actual)) {
			err.add(Utils.formatMessage("{0} must be true.", field));
			return false;
		} else if (matches(Future.class, cName) && !future().isValid(actual)) {
			err.add(Utils.formatMessage("{0} must be in the future.", field));
			return false;
		} else if (matches(Past.class, cName) && !past().isValid(actual)) {
			err.add(Utils.formatMessage("{0} must be in the past.", field));
			return false;
		} else if (matches(URL.class, cName) && !url().isValid(actual)) {
			err.add(Utils.formatMessage("{0} is not a valid URL.", field));
			return false;
		} else if (matches(Email.class, cName) && !email().isValid(actual)) {
			err.add(Utils.formatMessage("{0} is not a valid email.", field));
			return false;
		}
		return true;
	}

	/**
	 * Returns all validation constraints that are defined by Java annotation in the core classes.
	 *
	 * @return a map of all core types to all core annotated constraints. See JSR-303.
	 */
	public static Map<String, Map<String, Map<String, Map<String, ?>>>> getCoreValidationConstraints() {
		if (CORE_CONSTRAINTS.isEmpty()) {
			for (Map.Entry<String, Class<? extends ParaObject>> e : ParaObjectUtils.getCoreClassesMap().entrySet()) {
				String type = e.getKey();
				List<Field> fieldlist = Utils.getAllDeclaredFields(e.getValue());
				for (Field field : fieldlist) {
					Annotation[] annos = field.getAnnotations();
					if (annos.length > 1) {
						Map<String, Map<String, ?>> constrMap = new HashMap<>();
						for (Annotation anno : annos) {
							if (isValidConstraintType(anno.annotationType())) {
								Constraint c = fromAnnotation(anno);
								if (c != null) {
									constrMap.put(c.getName(), c.getPayload());
								}
							}
						}
						if (!constrMap.isEmpty()) {
							if (!CORE_CONSTRAINTS.containsKey(type)) {
								CORE_CONSTRAINTS.put(type, new HashMap<>());
							}
							CORE_CONSTRAINTS.get(type).put(field.getName(), constrMap);
						}
					}
				}
			}
			CORE_CONSTRAINTS.get(Utils.type(User.class)).put("password",
					Collections.singletonMap("max", Constraint.max(User.MAX_PASSWORD_LENGTH).getPayload()));
		}
		return Collections.unmodifiableMap(CORE_CONSTRAINTS);
	}
}
