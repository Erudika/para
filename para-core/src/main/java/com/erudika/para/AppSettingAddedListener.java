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
package com.erudika.para;

import com.erudika.para.core.App;

import java.util.EventListener;

/**
 * This listener is executed when a setting is added to an application.
 * @author Jeremy Wiesner [jswiesner@gmail.com]
 */
public interface AppSettingAddedListener extends EventListener {

	/**
	 * Code to execute right after a setting is added (could be updated or added for the first time).
	 * @param app the app object
	 * @param settingKey the key of the setting to add
	 * @param settingValue the value of the setting to add
	 */
	void onSettingAdded(App app, String settingKey, Object settingValue);
}
