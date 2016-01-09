/*
 * Copyright 2013-2016 Erudika. http://erudika.com
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
package com.erudika.para.validation;

import com.erudika.para.annotations.Email;
import com.erudika.para.utils.Utils;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.constraints.AssertFalse;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Future;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Past;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.URL;

/**
 * Represents a validation constraint.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public abstract class Constraint {

	/**
	 * Validates the given value against the this constraint.
	 * @param actualValue a value / property of an object
	 * @return true if the value satisfies the constraint
	 */
	public abstract boolean isValid(Object actualValue);

	private String name;
	private Map<String, Object> payload;

	static Map<Class<? extends Annotation>, String> validators = new HashMap<Class<? extends Annotation>, String>() {
		private static final long serialVersionUID = 1L;

		{
			put(Min.class, "min");
			put(Max.class, "max");
			put(Size.class, "size");
			put(Email.class, "email");
			put(Digits.class, "digits");
			put(Pattern.class, "pattern");
			put(NotNull.class, "required");
			put(NotEmpty.class, "required");
			put(NotBlank.class, "required");
			put(AssertFalse.class, "false");
			put(AssertTrue.class, "true");
			put(Future.class, "future");
			put(Past.class, "past");
			put(URL.class, "url");
		}
	};

	private Constraint(String name, Map<String, Object> payload) {
		this.name = name;
		this.payload = payload;
	}

	/**
	 * The constraint name.
	 * @return a name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of the constraint.
	 * @param name a name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * The payload (a map)
	 * @return a map
	 */
	public Map<String, Object> getPayload() {
		if (payload == null) {
			payload = new LinkedHashMap<String, Object>();
		}
		return payload;
	}

	/**
	 * Sets the payload.
	 * @param payload a map
	 */
	public void setPayload(Map<String, Object> payload) {
		this.payload = payload;
	}

	/**
	 * Verifies that the given annotation type corresponds to a known constraint.
	 * @param anno annotation type
	 * @param consName constraint name
	 * @return true if known
	 */
	public static boolean matches(Class<? extends Annotation> anno, String consName) {
		return validators.get(anno).equals(consName);
	}

	/**
	 * Builds a new constraint from the annotation data.
	 * @param anno JSR-303 annotation instance
	 * @return a new constraint
	 */
	public static Constraint fromAnnotation(Annotation anno) {
		if (anno instanceof Min) {
			return min(((Min) anno).value());
		} else if (anno instanceof Max) {
			return max(((Max) anno).value());
		} else if (anno instanceof Size) {
			return size(((Size) anno).min(), ((Size) anno).max());
		} else if (anno instanceof Digits) {
			return digits(((Digits) anno).integer(), ((Digits) anno).fraction());
		} else if (anno instanceof Pattern) {
			return pattern(((Pattern) anno).regexp());
		} else {
			return new Constraint(validators.get(anno.annotationType()),
					simplePayload(validators.get(anno.annotationType()))) {
						public boolean isValid(Object actualValue) {
							return true;
						}
					};
		}
	}

	/**
	 * Creates a new map representing a simple validation constraint.
	 * @param name the name of the constraint
	 * @return a map
	 */
	static Map<String, Object> simplePayload(final String name) {
		if (name == null) {
			return null;
		}
		return new LinkedHashMap<String, Object>() {
			{
				put("message", "messages." + name);
			}
		};
	}

	/**
	 * Creates a new map representing a {@link Min} validation constraint.
	 * @param min the minimum value
	 * @return a map
	 */
	static Map<String, Object> minPayload(final Object min) {
		if (min == null) {
			return null;
		}
		return new LinkedHashMap<String, Object>() {
			{
				put("value", min);
				put("message", "messages." + validators.get(Min.class));
			}
		};
	}

	/**
	 * Creates a new map representing a {@link Max} validation constraint.
	 * @param max the maximum value
	 * @return a map
	 */
	static Map<String, Object> maxPayload(final Object max) {
		if (max == null) {
			return null;
		}
		return new LinkedHashMap<String, Object>() {
			{
				put("value", max);
				put("message", "messages." + validators.get(Max.class));
			}
		};
	}

	/**
	 * Creates a new map representing a {@link Size} validation constraint.
	 * @param min the minimum length
	 * @param max the maximum length
	 * @return a map
	 */
	static Map<String, Object> sizePayload(final Object min, final Object max) {
		if (min == null || max == null) {
			return null;
		}
		return new LinkedHashMap<String, Object>() {
			{
				put("min", min);
				put("max", max);
				put("message", "messages." + validators.get(Size.class));
			}
		};
	}

	/**
	 * Creates a new map representing a {@link Digits} validation constraint.
	 * @param i the max size of the integral part of the number
	 * @param f the max size of the fractional part of the number
	 * @return a map
	 */
	static Map<String, Object> digitsPayload(final Object i, final Object f) {
		if (i == null || f == null) {
			return null;
		}
		return new LinkedHashMap<String, Object>() {
			{
				put("integer", i);
				put("fraction", f);
				put("message", "messages." + validators.get(Digits.class));
			}
		};
	}

	/**
	 * Creates a new map representing a {@link Pattern} validation constraint.
	 * @param regex a regular expression
	 * @return a map
	 */
	static Map<String, Object> patternPayload(final Object regex) {
		if (regex == null) {
			return null;
		}
		return new LinkedHashMap<String, Object>() {
			{
				put("value", regex);
				put("message", "messages." + validators.get(Pattern.class));
			}
		};
	}

	/**
	 * Returns true if that validator is the list of known validators.
	 * @param name a name
	 * @return true if validator is known
	 */
	public static boolean isValidConstraintName(String name) {
		return name != null && validators.containsValue(name.toLowerCase());
	}

	/**
	 * Returns true if that validator is the list of known validators.
	 * @param type annotation class type
	 * @return true if validator is known
	 */
	public static boolean isValidConstraintType(Class<? extends Annotation> type) {
		return type != null && validators.containsKey(type);
	}

	/**
	 * The 'required' constraint - marks a field as required.
	 * @return constraint
	 */
	public static Constraint required() {
		return new Constraint("required", simplePayload("required")) {
			public boolean isValid(Object actualValue) {
				return !(actualValue == null || StringUtils.isBlank(actualValue.toString()));
			}
		};
	}

	/**
	 * The 'min' constraint - field must contain a number larger than or equal to min.
	 * @param min the minimum value
	 * @return constraint
	 */
	public static Constraint min(final Object min) {
		return new Constraint("min", minPayload(min)) {
			public boolean isValid(Object actualValue) {
				if (actualValue != null) {
					if (!(actualValue instanceof Number)) {
						return false;
					} else {
						if (min != null && min instanceof Number) {
							if (((Number) min).longValue() > ((Number) actualValue).longValue()) {
								return false;
							}
						}
					}
				}
				return true;
			}
		};
	}

	/**
	 * The 'max' constraint - field must contain a number smaller than or equal to max.
	 * @param max the maximum value
	 * @return constraint
	 */
	public static Constraint max(final Object max) {
		return new Constraint("max", maxPayload(max)) {
			public boolean isValid(Object actualValue) {
				if (actualValue != null) {
					if (!(actualValue instanceof Number)) {
						return false;
					} else {
						if (max != null && max instanceof Number) {
							if (((Number) max).longValue() < ((Number) actualValue).longValue()) {
								return false;
							}
						}
					}
				}
				return true;
			}
		};
	}

	/**
	 * The 'size' constraint - field must be a {@link String}, {@link Map}, {@link Collection} or array
	 * with a given minimum and maximum length.
	 * @param min the minimum length
	 * @param max the maximum length
	 * @return constraint
	 */
	public static Constraint size(final Object min, final Object max) {
		return new Constraint("size", sizePayload(min, max)) {
			public boolean isValid(Object actualValue) {
				if (actualValue != null) {
					if (min != null && max != null && min instanceof Number && max instanceof Number) {
						if (actualValue instanceof String) {
							if (((String) actualValue).length() < ((Number) min).longValue()
									|| ((String) actualValue).length() > ((Number) max).longValue()) {
								return false;
							}
						} else if (actualValue instanceof Collection) {
							if (((Collection) actualValue).size() < ((Number) min).longValue()
									|| ((Collection) actualValue).size() > ((Number) max).longValue()) {
								return false;
							}
						} else if (actualValue instanceof Map) {
							if (((Map) actualValue).size() < ((Number) min).longValue()
									|| ((Map) actualValue).size() > ((Number) max).longValue()) {
								return false;
							}
						} else if (actualValue.getClass().isArray()) {
							int len = ArrayUtils.getLength(actualValue);
							if (len < ((Number) min).longValue() || len > ((Number) max).longValue()) {
								return false;
							}
						} else {
							return false;
						}
					}
				}
				return true;
			}
		};
	}

	/**
	 * The 'digits' constraint - field must be a {@link Number} or {@link String} containing digits where the
	 * number of digits in the integral part is limited by 'integer', and the
	 * number of digits for the fractional part is limited
	 * by 'fraction'.
	 * @param integer the max number of digits for the integral part
	 * @param fraction the max number of digits for the fractional part
	 * @return constraint
	 */
	public static Constraint digits(final Object integer, final Object fraction) {
		return new Constraint("digits", digitsPayload(integer, fraction)) {
			public boolean isValid(Object actualValue) {
				if (actualValue != null) {
					if (!((actualValue instanceof Number) || (actualValue instanceof String))) {
						return false;
					} else {
						if (integer != null && fraction != null && integer instanceof Number && fraction instanceof Number) {
							String val = actualValue.toString();
							String[] split = val.split("[,.]");
							if (!NumberUtils.isDigits(split[0])) {
								return false;
							}
							if (((Number) integer).intValue() < split[0].length()) {
								return false;
							}
							if (split.length > 1 && ((Number) fraction).intValue() < split[1].length()) {
								return false;
							}
						}
					}
				}
				return true;
			}
		};
	}

	/**
	 * The 'pattern' constraint - field must contain a value matching a regular expression.
	 * @param regex a regular expression
	 * @return constraint
	 */
	public static Constraint pattern(final Object regex) {
		return new Constraint("pattern", patternPayload(regex)) {
			public boolean isValid(Object actualValue) {
				if (actualValue != null) {
					if (regex != null && regex instanceof String) {
						if (!(actualValue instanceof String) || !((String) actualValue).matches((String) regex)) {
							return false;
						}
					}
				}
				return true;
			}
		};
	}

	/**
	 * The 'email' constraint - field must contain a valid email.
	 * @return constraint
	 */
	public static Constraint email() {
		return new Constraint("email", simplePayload("email")) {
			public boolean isValid(Object actualValue) {
				if (actualValue != null) {
					if (!(actualValue instanceof String) || !Utils.isValidEmail((String) actualValue)) {
						return false;
					}
				}
				return true;
			}
		};
	}

	/**
	 * The 'falsy' constraint - field value must not be equal to 'true'.
	 * @return constraint
	 */
	public static Constraint falsy() {
		return new Constraint("false", simplePayload("false")) {
			public boolean isValid(Object actualValue) {
				if (actualValue != null) {
					if ((actualValue instanceof Boolean && ((Boolean) actualValue))
							|| (actualValue instanceof String && Boolean.parseBoolean((String) actualValue))) {
						return false;
					}
				}
				return true;
			}
		};
	}

	/**
	 * The 'truthy' constraint - field value must be equal to 'true'.
	 * @return constraint
	 */
	public static Constraint truthy() {
		return new Constraint("true", simplePayload("true")) {
			public boolean isValid(Object actualValue) {
				if (actualValue != null) {
					if ((actualValue instanceof Boolean && !((Boolean) actualValue))
							|| (actualValue instanceof String && !Boolean.parseBoolean((String) actualValue))) {
						return false;
					}
				}
				return true;
			}
		};
	}

	/**
	 * The 'future' constraint - field value must be a {@link Date} or a timestamp in the future.
	 * @return constraint
	 */
	public static Constraint future() {
		return new Constraint("future", simplePayload("future")) {
			public boolean isValid(Object actualValue) {
				if (actualValue != null) {
					long now = System.currentTimeMillis();
					if ((actualValue instanceof Date && ((Date) actualValue).getTime() <= now)
							|| (actualValue instanceof Number && ((Number) actualValue).longValue() <= now)) {
						return false;
					}
				}
				return true;
			}
		};
	}

	/**
	 * The 'past' constraint - field value must be a {@link Date} or a timestamp in the past.
	 * @return constraint
	 */
	public static Constraint past() {
		return new Constraint("past", simplePayload("past")) {
			public boolean isValid(Object actualValue) {
				if (actualValue != null) {
					long now = System.currentTimeMillis();
					if ((actualValue instanceof Date && ((Date) actualValue).getTime() >= now)
							|| (actualValue instanceof Number && ((Number) actualValue).longValue() >= now)) {
						return false;
					}
				}
				return true;
			}
		};
	}

	/**
	 * The 'url' constraint - field value must be a valid URL.
	 * @return constraint
	 */
	public static Constraint url() {
		return new Constraint("url", simplePayload("url")) {
			public boolean isValid(Object actualValue) {
				if (actualValue != null) {
					if (!Utils.isValidURL(actualValue.toString())) {
						return false;
					}
				}
				return true;
			}
		};
	}

	/**
	 * Builds a new constraint from a given name and payload
	 * @param cname the constraint name
	 * @param payload the payload
	 * @return constraint
	 */
	public static Constraint build(String cname, Map<String, Object> payload) {
		if (cname != null && payload != null) {
			if ("required".equals(cname)) {
				return required();
			} else if ("email".equals(cname)) {
				return email();
			} else if ("false".equals(cname)) {
				return falsy();
			} else if ("true".equals(cname)) {
				return truthy();
			} else if ("future".equals(cname)) {
				return future();
			} else if ("past".equals(cname)) {
				return past();
			} else if ("url".equals(cname)) {
				return url();
			} else if ("min".equals(cname) && payload.containsKey("value")) {
				return min(payload.get("value"));
			} else if ("max".equals(cname) && payload.containsKey("value")) {
				return max(payload.get("value"));
			} else if ("size".equals(cname) && payload.containsKey("min") && payload.containsKey("max")) {
				return size(payload.get("min"), payload.get("max"));
			} else if ("digits".equals(cname) && payload.containsKey("integer") && payload.containsKey("fraction")) {
				return digits(payload.get("integer"), payload.get("fraction"));
			} else if ("pattern".equals(cname) && payload.containsKey("value")) {
				return pattern(payload.get("value"));
			}
		}
		return null;
	}
}
