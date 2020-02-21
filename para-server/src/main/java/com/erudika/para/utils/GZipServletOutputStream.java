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
package com.erudika.para.utils;

import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.WriteListener;

/**
 * A custom {@link javax.servlet.ServletOutputStream} for use by our filters.
 * @version $Id: FilterServletOutputStream.java 744 2008-08-16 20:10:49Z gregluck $
 * @author <a href="mailto:gluck@thoughtworks.com">Greg Luck</a>
 */
public class GZipServletOutputStream extends ServletOutputStream {

	private final OutputStream stream;

	GZipServletOutputStream(OutputStream output)
			throws IOException {
		super();
		this.stream = output;
	}

	@Override
	public void close() throws IOException {
		this.stream.close();
	}

	@Override
	public void flush() throws IOException {
		this.stream.flush();
	}

	@Override
	public void write(byte[] b) throws IOException {
		this.stream.write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		this.stream.write(b, off, len);
	}

	@Override
	public void write(int b) throws IOException {
		this.stream.write(b);
	}

	@Override
	public boolean isReady() {
		return false;
	}

	@Override
	public void setWriteListener(WriteListener wl) {
	}
}
