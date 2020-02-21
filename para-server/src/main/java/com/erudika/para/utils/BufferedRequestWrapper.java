/*
 * Copyright 2013-2020 Erudika. http://erudika.com
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
package com.erudika.para.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.slf4j.LoggerFactory;

/**
 * HttpServletRequest wrapper.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class BufferedRequestWrapper extends HttpServletRequestWrapper {

	private ByteArrayInputStream bais;
	private ByteArrayOutputStream baos;
	private BufferedServletInputStream bsis;
	private byte[] buffer;

	/**
	 * Default constructor.
	 * @param req {@link HttpServletRequest}
	 * @throws IOException error
	 */
	public BufferedRequestWrapper(HttpServletRequest req) throws IOException {
		super(req);
		if (req != null) {
			InputStream is = req.getInputStream();
			baos = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
			int length;
			while ((length = is.read(buf)) > 0) {
				baos.write(buf, 0, length);
			}
			buffer = baos.toByteArray();
		}
	}

	@Override
	public ServletInputStream getInputStream() {
		try {
			bais = new ByteArrayInputStream(buffer);
			bsis = new BufferedServletInputStream(bais);
		} catch (Exception ex) {
			LoggerFactory.getLogger(BufferedRequestWrapper.class).error(null, ex);
		}
		return bsis;
	}

}
