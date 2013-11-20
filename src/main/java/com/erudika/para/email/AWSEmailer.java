/*
 * Copyright 2013 Alex Bogdanovski <albogdano@me.com>.
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
 * You can reach the author at: https://github.com/albogdano
 */
package com.erudika.para.email;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@Singleton
public class AWSEmailer implements Emailer{

	private static AmazonSimpleEmailServiceClient sesclient;
	
	public AWSEmailer() {
		if (StringUtils.isBlank(Config.AWS_ACCESSKEY) || StringUtils.isBlank(Config.AWS_SECRETKEY)) {
			sesclient = new AmazonSimpleEmailServiceClient();
		} else {
			sesclient = new AmazonSimpleEmailServiceClient(
					new BasicAWSCredentials(Config.AWS_ACCESSKEY, Config.AWS_SECRETKEY));
		}
	}
	
	public boolean sendEmail(List<String> emails, String subject, String body){
		if (emails == null || emails.isEmpty() && !StringUtils.isBlank(body)) {
			final SendEmailRequest request = new SendEmailRequest().withSource(Config.SUPPORT_EMAIL);
			Destination dest = new Destination().withToAddresses(emails);
			request.setDestination(dest);

			Content subjContent = new Content().withData(subject);
			Message msg = new Message().withSubject(subjContent);

			// Include a body in both text and HTML formats
			Content textContent = new Content().withData(body);
			msg.setBody(new Body().withText(textContent));

			request.setMessage(msg);

			Utils.asyncExecute(new Callable<Object>(){
				public Object call() throws Exception {
					return sesclient.sendEmail(request);
				}
			});
			return true;
		}
		return false;
	}
}
