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
package com.erudika.para.server.storage;

import com.erudika.para.core.storage.FileStore;
import com.erudika.para.core.utils.Para;
import com.google.inject.AbstractModule;
import java.util.ServiceLoader;
import org.apache.commons.lang3.StringUtils;

/**
 * The default storage module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class StorageModule extends AbstractModule {

	/**
	 * Creates the storage module with the default provider lookup logic.
	 */
	public StorageModule() {
		// default constructor
	}

	protected void configure() {
		String selectedFileStore = Para.getConfig().fileStoragePlugin();
		if (StringUtils.isBlank(selectedFileStore)) {
			bindToDefault();
		} else {
			if ("s3".equalsIgnoreCase(selectedFileStore) ||
					AWSFileStore.class.getSimpleName().equalsIgnoreCase(selectedFileStore)) {
				bind(FileStore.class).to(AWSFileStore.class).asEagerSingleton();
			} else {
				FileStore fsPlugin = loadExternalFileStore(selectedFileStore);
				if (fsPlugin != null) {
					bind(FileStore.class).to(fsPlugin.getClass()).asEagerSingleton();
				} else {
					// default fallback - not implemented!
					bindToDefault();
				}
			}
		}
	}

	void bindToDefault() {
		bind(FileStore.class).to(LocalFileStore.class).asEagerSingleton();
	}

	/**
	 * Scans the classpath for FileStore implementations, through the
	 * {@link ServiceLoader} mechanism and returns one.
	 * @param classSimpleName the name of the class name to look for and load
	 * @return a FileStore instance if found, or null
	 */
	final FileStore loadExternalFileStore(String classSimpleName) {
		ServiceLoader<FileStore> fsLoader = ServiceLoader.load(FileStore.class, Para.getParaClassLoader());
		for (FileStore fs : fsLoader) {
			if (fs != null && classSimpleName.equalsIgnoreCase(fs.getClass().getSimpleName())) {
				return fs;
			}
		}
		return null;
	}

}
