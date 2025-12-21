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
package com.erudika.para.core.storage;

import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;

/**
 * In-memory FileStore.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class MockFileStore implements FileStore {

	private ConcurrentHashMap<String, InputStream> fs = new ConcurrentHashMap<>();

	@Override
	public InputStream load(String path) {
		if (!StringUtils.isBlank(path)) {
			return fs.get(path);
		}
		return null;
	}

	@Override
	public String store(String path, InputStream data) {
		if (!StringUtils.isBlank(path) && data != null) {
			fs.put(path, data);
		}
		return path;
	}

	@Override
	public boolean delete(String path) {
		if (!StringUtils.isBlank(path)) {
			fs.remove(path);
			return true;
		}
		return false;
	}

}
