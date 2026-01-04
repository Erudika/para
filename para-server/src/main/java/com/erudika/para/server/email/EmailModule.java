/*
 * Copyright 2013-2026 Erudika. https://erudika.com
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
package com.erudika.para.server.email;

import com.erudika.para.core.email.Emailer;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Para;
import java.util.ServiceLoader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The default email module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Configuration
public class EmailModule {

	@Bean
	public Emailer getEmailer() {
		Emailer emailer;
		String selectedEmailer = Para.getConfig().emailerPlugin();
		if (StringUtils.isBlank(selectedEmailer)) {
			emailer = bindToDefault();
		} else {
			if ("aws".equalsIgnoreCase(selectedEmailer) ||
					AWSEmailer.class.getSimpleName().equalsIgnoreCase(selectedEmailer)) {
				emailer = new AWSEmailer();
			} else if ("javamail".equalsIgnoreCase(selectedEmailer) ||
					JavaMailEmailer.class.getSimpleName().equalsIgnoreCase(selectedEmailer)) {
				emailer = new JavaMailEmailer();
			} else {
				Emailer emailerPlugin = loadExternalFileStore(selectedEmailer);
				if (emailerPlugin != null) {
					emailer = emailerPlugin;
				} else {
					// default fallback - not implemented!
					emailer = bindToDefault();
				}
			}
		}
		CoreUtils.getInstance().setEmailer(emailer);
		return Para.getEmailer();
	}

	Emailer bindToDefault() {
		return new NoopEmailer();
	}

	/**
	 * Scans the classpath for Emailer implementations, through the
	 * {@link ServiceLoader} mechanism and returns one.
	 * @param classSimpleName the name of the class name to look for and load
	 * @return a Emailer instance if found, or null
	 */
	final Emailer loadExternalFileStore(String classSimpleName) {
		ServiceLoader<Emailer> fsLoader = ServiceLoader.load(Emailer.class, Para.getParaClassLoader());
		for (Emailer fs : fsLoader) {
			if (fs != null && classSimpleName.equalsIgnoreCase(fs.getClass().getSimpleName())) {
				return fs;
			}
		}
		return null;
	}

}
