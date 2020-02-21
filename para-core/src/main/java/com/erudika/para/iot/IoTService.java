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
package com.erudika.para.iot;

import com.erudika.para.core.Thing;
import java.util.Map;

/**
 * An IoT service interface for connecting to the cloud.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public interface IoTService {

	/**
	 * Create a thing in the cloud (not Para).
	 * @param thing the thing object
	 * @return the thing with extra metadata attached
	 */
	Thing createThing(Thing thing);

	/**
	 * Returns the state of the thing, read from the cloud (not Para).
	 * @param thing the thing object
	 * @return the state properties map
	 */
	Map<String, Object> readThing(Thing thing);

	/**
	 * Updates the state of a thing in the cloud (not Para).
	 * @param thing the thing object
	 */
	void updateThing(Thing thing);

	/**
	 * Deletes a thing from the cloud (not Para).
	 * @param thing a thing object
	 */
	void deleteThing(Thing thing);

	/**
	 * Checks if a thing exists in the cloud (not on Para).
	 * @param thing a thing object
	 * @return true if thing exists
	 */
	boolean existsThing(Thing thing);

}
