/*
 * Copyright 2013-2014 Erudika. http://erudika.com
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
package com.erudika.para.web;

import com.erudika.para.Para;
import org.slf4j.Logger;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.slf4j.LoggerFactory;

/**
 * The default context listener.
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public class ParaContextListener implements ServletContextListener {

	private static final Logger logger = LoggerFactory.getLogger(ParaContextListener.class);

	/**
	 * Initialization.
	 * @param sce context
	 */
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		Para.initialize();
	}

	/**
	 * Clean up.
	 * @param sce context
	 */
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		Para.destroy();
	}

}
