/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Generic exception mapper.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

	public GenericExceptionMapper() { }

	/**
	 * @param ex exception
	 * @return a response
	 */
	public Response toResponse(final Exception ex) {
		if (ex instanceof WebApplicationException) {
			return RestUtils.getExceptionResponse(((WebApplicationException) ex).
					getResponse().getStatus(), ex.getMessage());
		} else {
			return RestUtils.getExceptionResponse(Response.Status.INTERNAL_SERVER_ERROR.
					getStatusCode(), ex.getMessage());
		}
	}
}
