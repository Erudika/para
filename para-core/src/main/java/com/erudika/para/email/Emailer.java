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

import java.util.List;

/**
 * An email service. Used for sending emails.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public interface Emailer {

	/**
	 * Sends an email.
	 * @param emails a list of email addresses (recipients)
	 * @param subject the subject of the message
	 * @param body the body of the message
	 * @return true if the message was sent
	 */
	boolean sendEmail(List<String> emails, String subject, String body);

}
