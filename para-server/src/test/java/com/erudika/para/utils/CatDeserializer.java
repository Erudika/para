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
package com.erudika.para.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class CatDeserializer extends StdDeserializer<Cat> {

	private static final long serialVersionUID = 1L;

	public CatDeserializer() {
		this(null);
	}

	public CatDeserializer(Class<?> vc) {
		super(vc);
	}

	@Override
	public Cat deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		if (StringUtils.contains(p.getText(), "::")) {
			String[] vals = StringUtils.split(p.getText(), "::");
			String name = vals[0];
			int age = NumberUtils.toInt(vals[1], 0);
			return new Cat(age, name);
		} else {
			return null;
		}
	}

}
