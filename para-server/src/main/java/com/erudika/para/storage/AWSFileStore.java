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

import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.StorageClass;

/**
 * An implementation of the {@link FileStore} interface using AWS S3.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AWSFileStore implements FileStore {

	private static final Logger logger = LoggerFactory.getLogger(AWSFileStore.class);
	private static final String S3_URL = "https://s3-{0}.amazonaws.com/{1}/{2}";
	private S3Client s3;
	private String bucket;

	/**
	 * No-args constructor.
	 */
	public AWSFileStore() {
		this(Config.getConfigParam("para.s3.bucket", "org.paraio"));
	}

	/**
	 * Creates a new instance based on the bucket provided.
	 * @param bucket the name of the S3 bucket
	 */
	public AWSFileStore(String bucket) {
		this.bucket = bucket;
		this.s3 = S3Client.create();
	}

	@Override
	public InputStream load(String path) {
		if (StringUtils.startsWith(path, "/")) {
			path = path.substring(1);
		}
		if (!StringUtils.isBlank(path)) {
			final String key = path;
			return s3.getObject(b -> b.bucket(bucket).key(key));
		}
		return null;
	}

	@Override
	public String store(String path, InputStream data) {
		if (StringUtils.startsWith(path, "/")) {
			path = path.substring(1);
		}
		if (StringUtils.isBlank(path) || data == null) {
			return null;
		}
		int maxFileSizeMBytes = Config.getConfigInt("para.s3.max_filesize_mb", 10);
		try {
			if (data.available() > 0 && data.available() <= (maxFileSizeMBytes * 1024 * 1024)) {
				Map<String, String> om = new HashMap<String, String>(3);
				om.put(HttpHeaders.CACHE_CONTROL, "max-age=15552000, must-revalidate");	// 180 days
				if (path.endsWith(".gz")) {
					om.put(HttpHeaders.CONTENT_ENCODING, "gzip");
					path = path.substring(0, path.length() - 3);
				}
				PutObjectRequest por = PutObjectRequest.builder().
						bucket(bucket).key(path).
						metadata(om).
						acl(ObjectCannedACL.PUBLIC_READ).
						storageClass(StorageClass.REDUCED_REDUNDANCY).build();
				s3.putObject(por, RequestBody.fromInputStream(data, data.available())); //.bucket, path, data, om
				return Utils.formatMessage(S3_URL, new DefaultAwsRegionProviderChain().getRegion().id(), bucket, path);
			}
		} catch (IOException e) {
			logger.error(null, e);
		} finally {
			try {
				data.close();
			} catch (IOException ex) {
				logger.error(null, ex);
			}
		}
		return null;
	}

	@Override
	public boolean delete(String path) {
		if (StringUtils.startsWith(path, "/")) {
			path = path.substring(1);
		}
		if (!StringUtils.isBlank(path)) {
			final String key = path;
			s3.deleteObject(b -> b.bucket(bucket).key(key));
			return true;
		}
		return false;
	}

}
