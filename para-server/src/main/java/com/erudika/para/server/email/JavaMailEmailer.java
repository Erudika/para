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
package com.erudika.para.server.email;

import com.erudika.para.core.email.Emailer;
import com.erudika.para.core.utils.Para;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;

/**
 * A simple JavaMail implementation of {@link Emailer}.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class JavaMailEmailer implements Emailer {

	private static final Logger logger = LoggerFactory.getLogger(JavaMailEmailer.class);
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Para.getConfig().executorThreads());
	private final JavaMailSender mailSender;

	/**
	 * Default constructor.
	 */
	public JavaMailEmailer() {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(System.getProperty("spring.mail.host"));
		sender.setPort(NumberUtils.toInt(System.getProperty("spring.mail.port"), 965));
		sender.setUsername(System.getProperty("spring.mail.username"));
		sender.setPassword(System.getProperty("spring.mail.password"));

		Properties props = sender.getJavaMailProperties();
		props.put("mail.transport.protocol", "smtp");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", System.getProperty("spring.mail.properties.mail.smtp.starttls.enable", "true"));
		props.put("mail.smtp.ssl.enable", System.getProperty("spring.mail.properties.mail.smtp.ssl.enable", "true"));
		props.put("mail.debug", System.getProperty("spring.mail.properties.mail.debug", "false"));
		this.mailSender = sender;
	}

	@Override
	public boolean sendEmail(final List<String> emails, final String subject, final String body) {
		return sendEmail(emails, subject, body, null, null, null);
	}

	@Override
	public boolean sendEmail(List<String> emails, String subject, String body, InputStream attachment, String mimeType, String fileName) {
		if (emails == null || emails.isEmpty()) {
			return false;
		}
		asyncExecute(new Runnable() {
			public void run() {
				MimeMessagePreparator preparator = new MimeMessagePreparator() {
					public void prepare(MimeMessage mimeMessage) throws Exception {
						MimeMessageHelper msg = new MimeMessageHelper(mimeMessage);
						Iterator<String> emailz = emails.iterator();
						msg.setTo(emailz.next());
						while (emailz.hasNext()) {
							msg.addBcc(emailz.next());
						}
						msg.setSubject(subject);
						msg.setFrom(Para.getConfig().supportEmail());
						msg.setText(body, true); // body is assumed to be HTML
						if (attachment != null) {
							msg.addAttachment(fileName, new ByteArrayDataSource(attachment, mimeType));
						}
					}
				};
				try {
					mailSender.send(preparator);
					logger.debug("Email sent to {}, {}", emails, subject);
				} catch (MailException ex) {
					logger.error("Failed to send email. {}", ex.getMessage());
				}
			}
		});
		return true;
	}

	private void asyncExecute(Runnable runnable) {
		if (runnable != null) {
			try {
				EXECUTOR.execute(runnable);
			} catch (RejectedExecutionException ex) {
				logger.warn(ex.getMessage());
				try {
					runnable.run();
				} catch (Exception e) {
					logger.error(null, e);
				}
			}
		}
	}

}
