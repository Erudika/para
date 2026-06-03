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
package com.erudika.para.core.email;

import jakarta.mail.util.ByteArrayDataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An email service. Used for sending emails.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public interface Emailer {

	static final Logger logger = LoggerFactory.getLogger(Emailer.class);
	static final int MAX_RECIPIENTS_PER_MESSAGE = 50;

	/**
	 * Sends an email.
	 * @param emails a list of email addresses (recipients)
	 * @param subject the subject of the message
	 * @param body the body of the message
	 * @return true if the message was sent
	 */
	public default boolean sendEmail(List<String> emails, String subject, String body) {
		return sendEmail(emails, subject, body, null, null, null);
	}

	/**
	 * Sends an email.
	 * @param emails a list of email addresses (recipients)
	 * @param subject the subject of the message
	 * @param body the body of the message
	 * @param attachment attachment
	 * @param mimeType attachment MIME type
	 * @param fileName attachment file name
	 * @return true if the message was sent
	 */
	public default boolean sendEmail(List<String> emails, String subject, String body,
			InputStream attachment, String mimeType, String fileName) {
		if (emails == null || emails.isEmpty()) {
			return false;
		}
		byte[] attachmentBytes = null;
		if (attachment != null) {
			try {
				attachmentBytes = attachment.readAllBytes();
			} catch (Exception e) {
				logger.error("Failed to read attachment: {}", e.getMessage());
			}
		}
		for (int i = 0; i < emails.size(); i += MAX_RECIPIENTS_PER_MESSAGE) {
			List<String> batch = new ArrayList<>(emails.subList(i,
					Math.min(i + MAX_RECIPIENTS_PER_MESSAGE, emails.size())));
			ByteArrayDataSource dataSource = null;
			if (attachmentBytes != null) {
				try {
					dataSource = new ByteArrayDataSource(new ByteArrayInputStream(attachmentBytes), mimeType);
				} catch (IOException ex) {
					logger.error("Failed to send email '{}' with attachment to {} recipients: {}",
							subject, emails.size(), ex.getMessage());
				}
			}
			sendSingleBatch(batch, subject, body, dataSource, fileName);
		}
		return true;
	}


	/**
	 * Sends a single email message to a batch of email addresses (max. 100).
	 * @param emails a list of email addresses (recipients)
	 * @param subject the subject of the message
	 * @param body the body of the message
	 * @param attachment attachment
	 * @param mimeType attachment MIME type
	 * @param fileName attachment file name
	 */
	abstract void sendSingleBatch(List<String> emails, String subject, String body,
			ByteArrayDataSource attachment, String fileName);
}
