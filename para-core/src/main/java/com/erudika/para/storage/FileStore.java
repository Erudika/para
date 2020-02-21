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
package com.erudika.para.storage;

import java.io.InputStream;

/**
 * A file store interface.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public interface FileStore {

	/**
	 * Loads a file from a storage service.
	 * @param path the relative file path
	 * @return the file stream or null if not found
	 */
	InputStream load(String path);

	/**
	 * Saves a file to a storage service.
	 * @param path the relative file path
	 * @param data the contents of the file
	 * @return the full path to the file or the URL, null if unsuccessful
	 */
	String store(String path, InputStream data);


	/**
	 * Deletes a file.
	 * @param path the relative file path
	 * @return true if successful
	 */
	boolean delete(String path);

}
