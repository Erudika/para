/*
 * Copyright 2013-2019 Erudika. https://erudika.com
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
package com.erudika.para.email;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.erudika.para.Para;
import com.erudika.para.utils.Config;
import java.util.List;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

/**
 * An emailer that uses AWS Simple Email Service (SES).
 * By default, this implementation treats the body as HTML content.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class AWSEmailer implements Emailer {

	private final AmazonSimpleEmailService sesclient;

	/**
	 * No-args constructor.
	 */
	public AWSEmailer() {
		sesclient = AmazonSimpleEmailServiceClientBuilder.standard().
				// AWS SES is not available in all regions and it's best if we set it manually
				withRegion(Config.getConfigParam("aws_ses_region", "eu-west-1")).build();
	}

	@Override
	public boolean sendEmail(List<String> emails, String subject, String body) {
		if (emails != null && !emails.isEmpty() && !StringUtils.isBlank(body)) {
			final SendEmailRequest request = new SendEmailRequest().withSource(Config.SUPPORT_EMAIL);
			Destination dest = new Destination().withToAddresses(emails);
			request.setDestination(dest);

			Content subjContent = new Content().withData(subject);
			Message msg = new Message().withSubject(subjContent);

			// Include a body in both text and HTML formats
			Content textContent = new Content().withData(body).withCharset(Config.DEFAULT_ENCODING);
			msg.setBody(new Body().withHtml(textContent));

			request.setMessage(msg);

			Para.asyncExecute(new Runnable() {
				public void run() {
					sesclient.sendEmail(request);
				}
			});
			return true;
		}
		return false;
	}
}
