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
import java.io.IOException;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

/**
 * Servlet Input Stream.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class BufferedServletInputStream extends ServletInputStream {

	private ByteArrayInputStream bais;

	BufferedServletInputStream(ByteArrayInputStream bais) {
		this.bais = bais;
	}

	@Override
	public int available() {
		return bais.available();
	}

	@Override
	public int read() {
		return bais.read();
	}

	@Override
	public int read(byte[] buf, int off, int len) {
		return bais.read(buf, off, len);
	}

	@Override
	public boolean isFinished() {
		return bais.available() <= 0;
	}

	@Override
	public boolean isReady() {
		return !isFinished();
	}

	@Override
	public void setReadListener(ReadListener rl) {
	}

	@Override
	public boolean markSupported() {
		return bais.markSupported();
	}

	@Override
	public synchronized void reset() throws IOException {
		bais.reset();
	}

	@Override
	public synchronized void mark(int readlimit) {
		bais.mark(readlimit);
	}
}
