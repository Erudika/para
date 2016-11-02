/*
 * Copyright 2013-2016 Erudika. https://erudika.com
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

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.StorageClass;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the {@link FileStore} interface using AWS S3.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AWSFileStore implements FileStore {

	private static final Logger logger = LoggerFactory.getLogger(AWSFileStore.class);
	private final String baseUrl = "https://s3-{0}.amazonaws.com/{1}/{2}";
	private final BasicAWSCredentials awsCredentials;
	private AmazonS3Client s3;
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
		this.awsCredentials = new BasicAWSCredentials(Config.AWS_ACCESSKEY, Config.AWS_SECRETKEY);
		this.s3 = new AmazonS3Client(awsCredentials);
	}

	@Override
	public InputStream load(String path) {
		if (StringUtils.startsWith(path, "/")) {
			path = path.substring(1);
		}
		if (!StringUtils.isBlank(path)) {
			S3Object file = s3.getObject(bucket, path);
			return file.getObjectContent();
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
				ObjectMetadata om = new ObjectMetadata();
				om.setCacheControl("max-age=15552000, must-revalidate");	// 180 days
				if (path.endsWith(".gz")) {
					om.setContentEncoding("gzip");
					path = path.substring(0, path.length() - 3);
				}
				path = System.currentTimeMillis() + "." + path;
				PutObjectRequest por = new PutObjectRequest(bucket, path, data, om);
				por.setCannedAcl(CannedAccessControlList.PublicRead);
				por.setStorageClass(StorageClass.ReducedRedundancy);
				s3.putObject(por);
				return Utils.formatMessage(baseUrl, Config.AWS_REGION, bucket, path);
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
			s3.deleteObject(bucket, path);
			return true;
		}
		return false;
	}

}
