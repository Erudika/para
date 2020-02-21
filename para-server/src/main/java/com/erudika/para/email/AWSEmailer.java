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
package com.erudika.para.email;

import com.erudika.para.Para;
import com.erudika.para.utils.Config;
import java.util.Iterator;
import java.util.List;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

/**
 * An emailer that uses AWS Simple Email Service (SES).
 * By default, this implementation treats the body as HTML content.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Singleton
public class AWSEmailer implements Emailer {

	private final SesClient sesclient;

	/**
	 * No-args constructor.
	 */
	public AWSEmailer() {
		sesclient = SesClient.builder().
				// AWS SES is not available in all regions and it's best if we set it manually
				region(Region.of(Config.getConfigParam("aws_ses_region", "eu-west-1"))).build();
	}

	@Override
	public boolean sendEmail(List<String> emails, String subject, String body) {
		if (emails != null && !emails.isEmpty() && !StringUtils.isBlank(body)) {
			SendEmailRequest.Builder request = SendEmailRequest.builder();
			request.source(Config.SUPPORT_EMAIL).build();
			Iterator<String> emailz = emails.iterator();
			Destination.Builder dest = Destination.builder();
			dest.toAddresses(emailz.next());
			while (emailz.hasNext()) {
				dest.bccAddresses(emailz.next());
			}
			request.destination(dest.build());

			Message.Builder msg = Message.builder();
			msg.subject(Content.builder().data(subject).build());

			// Include a body in both text and HTML formats
			msg.body(Body.builder().html(Content.builder().data(body).charset(Config.DEFAULT_ENCODING).build()).build());

			request.message(msg.build());

			Para.asyncExecute(new Runnable() {
				public void run() {
					sesclient.sendEmail(request.build());
				}
			});
			return true;
		}
		return false;
	}
}
