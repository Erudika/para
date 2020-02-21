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
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.zip.GZIPOutputStream;

/**
 * Provides a wrapper for {@link javax.servlet.http.HttpServletResponseWrapper}.
 * It is used to wrap the real Response so that we can modify it after
 * that the target of the request has delivered its response.
 * It uses the Wrapper pattern.
 * @author <a href="mailto:gluck@thoughtworks.com">Greg Luck</a>
 * @version $Id: GenericResponseWrapper.java 793 2008-10-07 07:28:03Z gregluck $
 */
public class GZipServletResponseWrapper extends HttpServletResponseWrapper {

	private GZipServletOutputStream gzipOutputStream = null;
	private PrintWriter printWriter = null;
	private boolean disableFlushBuffer = false;

	/**
	 * Default Constructor.
	 * @param response response
	 * @param gzout output stream
	 * @throws IOException maybe
	 */
	public GZipServletResponseWrapper(HttpServletResponse response, GZIPOutputStream gzout)
			throws IOException {
		super(response);
		gzipOutputStream = new GZipServletOutputStream(gzout);
	}

	/**
	 * Closes the stream.
	 * @throws IOException maybe
	 */
	public void close() throws IOException {

		//PrintWriter.close does not throw exceptions. Thus, the call does not need
		//be inside a try-catch block.
		if (this.printWriter != null) {
			this.printWriter.close();
		}

		if (this.gzipOutputStream != null) {
			this.gzipOutputStream.close();
		}
	}

	/**
	 * Flush OutputStream or PrintWriter.
	 *
	 * @throws IOException ex
	 */
	@Override
	public void flushBuffer() throws IOException {

		//PrintWriter.flush() does not throw exception
		if (this.printWriter != null) {
			this.printWriter.flush();
		}

		if (this.gzipOutputStream != null) {
			this.gzipOutputStream.flush();
		}

		// doing this might leads to response already committed exception
		// when the PageInfo has not yet built but the buffer already flushed
		// Happens in Weblogic when a servlet forward to a JSP page and the forward
		// method trigger a flush before it forwarded to the JSP
		// disableFlushBuffer for that purpose is 'true' by default
		if (!disableFlushBuffer) {
			super.flushBuffer();
		}
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		if (this.printWriter != null) {
			throw new IllegalStateException(
					"PrintWriter obtained already - cannot get OutputStream");
		}

		return this.gzipOutputStream;
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		if (this.printWriter == null) {
			this.gzipOutputStream = new GZipServletOutputStream(
					getResponse().getOutputStream());

			this.printWriter = new PrintWriter(new OutputStreamWriter(
					this.gzipOutputStream, getResponse().getCharacterEncoding()), true);
		}

		return this.printWriter;
	}

	@Override
	public void setContentLength(int length) {
		//ignore, since content length of zipped content
		//does not match content length of unzipped content.
	}

	/**
	 * Flushes all the streams for this response.
	 * @throws IOException maybe
	 */
	public void flush() throws IOException {
		if (printWriter != null) {
			printWriter.flush();
		}

		if (gzipOutputStream != null) {
			gzipOutputStream.flush();
		}
	}

	/**
	 * Set if the wrapped reponse's buffer flushing should be disabled.
	 *
	 * @param disableFlushBuffer true if the wrapped reponse's buffer flushing should be disabled
	 */
	public void setDisableFlushBuffer(boolean disableFlushBuffer) {
		this.disableFlushBuffer = disableFlushBuffer;
	}
}
