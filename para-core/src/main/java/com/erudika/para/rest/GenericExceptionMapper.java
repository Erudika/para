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

package com.erudika.para.rest;

import java.util.LinkedHashMap;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic exception mapper.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

	private static final Logger logger = LoggerFactory.getLogger(GenericExceptionMapper.class);

	/**
	 * No-args constructor.
	 */
	public GenericExceptionMapper() { }

	/**
	 * @param ex exception
	 * @return a response
	 */
	public Response toResponse(final Exception ex) {
		if (ex instanceof WebApplicationException) {
			WebApplicationException e = (WebApplicationException) ex;
			if (e.getResponse().getStatus() != Response.Status.NOT_FOUND.getStatusCode()) {
				logger.error("API request error: {}", e.getMessage());
			}
			return getExceptionResponse(e.getResponse().getStatus(), ex.getMessage());
		} else {
			logger.error("API request error: {}", ex.getMessage());
			return getExceptionResponse(Response.Status.INTERNAL_SERVER_ERROR.
					getStatusCode(), ex.getMessage());
		}
	}

	/**
	 * Returns an exception/error response as a JSON object.
	 * @param status HTTP status code
	 * @param msg message
	 * @return a JSON object
	 */
	public static Response getExceptionResponse(final int status, final String msg) {
		return Response.status(status).entity(new LinkedHashMap<String, Object>() {
			private static final long serialVersionUID = 1L;
			{
				put("code", status);
				put("message", msg);
			}
		}).type(MediaType.APPLICATION_JSON).build();
	}
}
