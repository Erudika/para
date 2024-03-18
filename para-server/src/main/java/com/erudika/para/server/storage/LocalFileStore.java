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
package com.erudika.para.server.storage;

import com.erudika.para.core.storage.FileStore;
import com.erudika.para.core.utils.Para;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores files locally.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class LocalFileStore implements FileStore {

	private static final Logger logger = LoggerFactory.getLogger(LocalFileStore.class);
	private String folder;

	/**
	 * No-args constructor.
	 */
	public LocalFileStore() {
		this(Para.getConfig().localFileStoreFolder());
	}

	/**
	 * Consturcts a new instance based on a given folder.
	 * @param folder the folder to store files in
	 */
	public LocalFileStore(String folder) {
		if (StringUtils.endsWith(folder, File.separator)) {
			this.folder = folder;
		} else {
			this.folder = folder.concat(File.separator);
		}
	}

	@Override
	public InputStream load(String path) {
		if (StringUtils.startsWith(path, File.separator)) {
			path = path.substring(1);
		}
		if (!StringUtils.isBlank(path)) {
			try {
				File f = new File(folder + path);
				if (f.exists() && f.canRead()) {
					try	(InputStream fin = new FileInputStream(f)) {
						byte[] buf = new byte[(int) f.length()];
						IOUtils.readFully(fin, buf);
						return new ByteArrayInputStream(buf);
					}
				}
			} catch (Exception ex) {
				logger.debug(null, ex);
			}
		}
		return null;
	}

	@Override
	public String store(String path, InputStream data) {
		if (StringUtils.startsWith(path, File.separator)) {
			path = path.substring(1);
		}
		if (StringUtils.isBlank(path)) {
			return null;
		}
		int maxFileSizeMBytes = Para.getConfig().localFileStoreMaxFileSizeMb();
		try {
			File f = new File(folder + path);
			if (!f.exists() || f.canWrite()) {
				long bytesWritten = 0;
				try (FileOutputStream fos = new FileOutputStream(f)) {
					try	(BufferedOutputStream bos = new BufferedOutputStream(fos)) {
						int read = 0;
						byte[] bytes = new byte[1024];
						while ((read = data.read(bytes)) != -1) {
							bos.write(bytes, 0, read);
							bytesWritten += read;
							if (bytesWritten > (maxFileSizeMBytes * 1024 * 1024)) {
								break;
							}
						}
					}
				}
				if (bytesWritten > (maxFileSizeMBytes * 1024 * 1024)) {
					logger.warn("Failed to store file on disk because it's too large - {}, {} bytes",
							path, bytesWritten);
					f.delete();
					return null;
				}
				return f.getAbsolutePath();
			} else {
				logger.warn("Failed to write file {} to disk - no permissions.", folder + path);
			}
		} catch (FileNotFoundException ex) {
			logger.error("File {} not found.", folder + path);
		} catch (IOException e) {
			logger.error(null, e);
		} finally {
			try {
				data.close();
			} catch (IOException e) {
				logger.error(null, e);
			}
		}
		return null;
	}

	@Override
	public boolean delete(String path) {
		if (StringUtils.startsWith(path, File.separator)) {
			path = path.substring(1);
		}
		if (!StringUtils.isBlank(path)) {
			File f = new File(folder + path);
			return f.canWrite() && f.delete();
		}
		return false;
	}

}
