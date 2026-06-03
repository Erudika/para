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
package com.erudika.para.server.email;

import com.erudika.para.core.App;
import com.erudika.para.core.email.Emailer;
import com.erudika.para.core.utils.Para;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;

/**
 * A simple synchronous JavaMail implementation of {@link Emailer}.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class JavaMailEmailer implements Emailer {

	private JavaMailSender mailSender;

	/**
	 * Default constructor.
	 */
	public JavaMailEmailer() {
	}

	private JavaMailSender getEmailer(App app) {
		if (app == null) {
			if (mailSender == null) {
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
				mailSender = sender;
			}
			return mailSender;
		} else {
			JavaMailSenderImpl sender = new JavaMailSenderImpl();
			sender.setHost(Para.getConfig().getSettingForApp(app, "mail.host", ""));
			sender.setPort(NumberUtils.toInt(Para.getConfig().getSettingForApp(app, "mail.port", "965")));
			sender.setUsername(Para.getConfig().getSettingForApp(app, "mail.username", ""));
			sender.setPassword(Para.getConfig().getSettingForApp(app, "mail.password", ""));

			Properties props = sender.getJavaMailProperties();
			props.put("mail.transport.protocol", "smtp");
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.starttls.enable", Para.getConfig().getSettingForApp(app, "mail.tls", "true"));
			props.put("mail.smtp.ssl.enable", Para.getConfig().getSettingForApp(app, "mail.ssl", "true"));
			props.put("mail.debug", Para.getConfig().getSettingForApp(app, "mail.debug", "false"));
			return sender;
		}
	}

	/**
	 * Sends emails using JavaMail.
	 * @param app app
	 * @param emails emails
	 * @param subject subject
	 * @param body body
	 * @param attachment attachment
	 * @param fileName filename
	 */
	@Override
	public void sendSingleBatch(App app, List<String> emails, String subject, String body, ByteArrayDataSource attachment, String fileName) {
		MimeMessagePreparator preparator = (MimeMessage mimeMessage) -> {
			MimeMessageHelper msg = new MimeMessageHelper(mimeMessage);
			Iterator<String> emailz = emails.iterator();
			msg.setTo(emailz.next());
			while (emailz.hasNext()) {
				msg.addBcc(emailz.next());
			}
			msg.setSubject(subject);
			msg.setFrom(getFromEmail(app), getFromName(app));
			msg.setText(body, true); // body is assumed to be HTML
			if (attachment != null) {
				msg.addAttachment(fileName, attachment);
			}
		};
		try {
			logger.debug("Sending email '{}' to {} recipients, {}", subject, emails.size());
			getEmailer(app).send(preparator);
		} catch (MailException ex) {
			logger.error("Failed to send email. {}", ex.getMessage());
		}
	}
}
