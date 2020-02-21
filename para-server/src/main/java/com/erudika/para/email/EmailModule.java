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
package com.erudika.para.email;

import com.erudika.para.utils.Config;
import com.google.inject.AbstractModule;

/**
 * The default email module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class EmailModule extends AbstractModule {

	protected void configure() {
		String selectedEmailer = Config.getConfigParam("emailer", "");
		if ("aws".equals(selectedEmailer)) {
			bind(Emailer.class).to(AWSEmailer.class);
		} else if ("javamail".equals(selectedEmailer)) {
			bind(Emailer.class).to(JavaMailEmailer.class);
		} else {
			bind(Emailer.class).to(NoopEmailer.class);
		}
	}

}
