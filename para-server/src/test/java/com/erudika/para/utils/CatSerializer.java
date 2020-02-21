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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class CatSerializer extends StdSerializer<Cat> {

	private static final long serialVersionUID = 1L;

	public CatSerializer() {
		this(null);
	}

	public CatSerializer(Class<Cat> t) {
		super(t);
	}

	@Override
	public void serialize(Cat value, JsonGenerator gen, SerializerProvider provider) throws IOException {
		if (value != null) {
			gen.writeString(value.getName() + "::" + value.getAge());
		} else {
			gen.writeNull();
		}
	}

}
