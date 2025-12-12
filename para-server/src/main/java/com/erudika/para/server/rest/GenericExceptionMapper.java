/*
 * Copyright 2013-2022 Erudika. https://erudika.com
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

package com.erudika.para.server.rest;

import static com.erudika.para.server.ParaServer.API_PATH;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for API v1.
 */
@RestControllerAdvice(assignableTypes = Api1.class)
@RequestMapping(path = API_PATH + "/**")
public class GenericExceptionMapper {

	private static final Logger logger = LoggerFactory.getLogger(GenericExceptionMapper.class);

	public GenericExceptionMapper() { }

	@ExceptionHandler(Throwable.class)
	public ResponseEntity<Map<String, Object>> handleException(Throwable ex) {
		logger.error("API request error: {}", ex.getMessage(), ex);
		return getExceptionResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage());
	}

	public static ResponseEntity<Map<String, Object>> getExceptionResponse(int status, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("code", status);
		body.put("message", payloadMessage(status, message));
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
	}

	private static String payloadMessage(int status, String message) {
		if (message != null && !message.trim().isEmpty()) {
			return message;
		}
		HttpStatus resolved = HttpStatus.resolve(status);
		return resolved != null ? resolved.getReasonPhrase() : "Unknown error";
	}
}
