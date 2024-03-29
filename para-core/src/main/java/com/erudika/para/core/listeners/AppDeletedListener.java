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
package com.erudika.para.core.listeners;

import com.erudika.para.core.App;
import java.util.EventListener;

/**
 * This listener is executed when a new child {@link App} is deleted.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public interface AppDeletedListener extends EventListener {

	/**
	 * Code to execute right after app is deleted.
	 * @param app the app object
	 */
	void onAppDeleted(App app);
}
