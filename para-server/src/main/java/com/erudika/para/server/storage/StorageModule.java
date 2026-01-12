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
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.Para;
import java.util.ServiceLoader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The default storage module.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Configuration
public class StorageModule {

	@Bean
	public FileStore getFileStore() {
		FileStore store;
		String selectedFileStore = Para.getConfig().fileStoragePlugin();
		if (StringUtils.isBlank(selectedFileStore)) {
			store = bindToDefault();
		} else {
			FileStore fsPlugin = loadExternalFileStore(selectedFileStore);
			if (fsPlugin != null) {
				store = fsPlugin;
			} else {
				// default fallback - not implemented!
				store = bindToDefault();
			}
		}
		CoreUtils.getInstance().setFileStore(store);
		return Para.getFileStore();
	}

	FileStore bindToDefault() {
		return new LocalFileStore();
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
