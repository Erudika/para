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
package com.erudika.para.server.pages;

import com.erudika.para.core.utils.Utils;
import com.erudika.para.server.ParaServer;
import java.util.Map;

/**
 * Implement this to override the default Para error page.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public interface ErrorPage {

	/**
	 * The default HTML content of this page.
	 * @return
	 */
	public default String getHTML(Map<String, Object> model) {
		return Utils.compileMustache(model, ParaServer.loadResource("templates/base.html"));
	}
}
