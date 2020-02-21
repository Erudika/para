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

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;

/**
 * A custom API resource handler. Handles custom resources.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public interface CustomResourceHandler {

	/**
	 * The relative path of the custom resource.
	 * @return any string (Jersey path parameters are allowed)
	 */
	String getRelativePath();

	/**
	 * This method is called when a GET request to be handled.
	 * @param ctx the context object - contains all the details of the request and context
	 * @return a response
	 */
	Response handleGet(ContainerRequestContext ctx);

	/**
	 * This method is called when a POST request to be handled.
	 * @param ctx the context object - contains all the details of the request and context
	 * @return a response
	 */
	Response handlePost(ContainerRequestContext ctx);

	/**
	 * This method is called when a PATCH request to be handled.
	 * @param ctx the context object - contains all the details of the request and context
	 * @return a response
	 */
	Response handlePatch(ContainerRequestContext ctx);

	/**
	 * This method is called when a PUT request to be handled.
	 * @param ctx the context object - contains all the details of the request and context
	 * @return a response
	 */
	Response handlePut(ContainerRequestContext ctx);

	/**
	 * This method is called when a DELETE request to be handled.
	 * @param ctx the context object - contains all the details of the request and context
	 * @return a response
	 */
	Response handleDelete(ContainerRequestContext ctx);

}
