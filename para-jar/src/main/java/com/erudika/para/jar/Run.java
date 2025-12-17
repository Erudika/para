/*
 * Copyright 2013-2022 Erudika. http://erudika.com
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
package com.erudika.para.jar;

import com.erudika.para.server.ParaServer;
import org.apache.commons.lang3.Strings;

/**
 * Main entry point of Para JAR.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class Run {


	static {
		if (!Strings.CS.equalsAny("false", System.getProperty("para.landing_page_enabled"),
				System.getenv("para_landing_page_enabled"))) {
			System.setProperty("para.landing_page_enabled", "true");
		}
	}

	/**
	 * Default constructor.
	 */
	private Run() { }

	/**
	 * Main method.
	 * @param args args
	 */
	public static void main(String[] args) {
		ParaServer.main(args);
	}

}
