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
package com.erudika.para.security;

import java.util.LinkedList;
import java.util.List;
import org.springframework.security.openid.AxFetchListFactory;
import org.springframework.security.openid.OpenIDAttribute;

/**
 * OpenID attributes list.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SimpleAxFetchListFactory implements AxFetchListFactory {
	private static final long serialVersionUID = 1L;

	/**
	 * A list of OpenID attributes to send in a request.
	 * @param identifier a user identifier
	 * @return a list of attributes
	 */
	public List<OpenIDAttribute> createAttributeList(String identifier) {
		List<OpenIDAttribute> list = new LinkedList<>();
		if (identifier != null && identifier.matches("https://www.google.com/.*")) {
			OpenIDAttribute email = new OpenIDAttribute("email", "http://axschema.org/contact/email");
			OpenIDAttribute first = new OpenIDAttribute("firstname", "http://axschema.org/namePerson/first");
			OpenIDAttribute last = new OpenIDAttribute("lastname", "http://axschema.org/namePerson/last");
			email.setCount(1);
			email.setRequired(true);
			first.setRequired(true);
			last.setRequired(true);
			list.add(email);
			list.add(first);
			list.add(last);
		}
		return list;
	}
}
