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
package com.erudika.para.storage;

import java.io.InputStream;

/**
 * A file store interface. WORK IN PROGRESS.
 * @author Alex Bogdanovski <alex@erudika.com>
 */
public interface FileStore {

	/**
	 * Loads a file from a storage service.
	 * @param url the file's URL
	 * @return the file or null if not found
	 */
	InputStream load(String url);

	/**
	 * Saves a file to a storage service.
	 * @param data the file
	 * @return the URL for the created file
	 */
	String store(InputStream data);

}
