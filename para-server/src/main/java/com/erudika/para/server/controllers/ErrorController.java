
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
package com.erudika.para.server.controllers;

import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.Utils;
import com.erudika.para.server.ParaServer;
import com.erudika.para.server.rest.GenericExceptionMapper;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Controller
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {

	@Inject
	public ErrorController() {
	}

	@GetMapping({"/error", "/error/{code}"})
	public ResponseEntity<?> get(@PathVariable(required = false) String code,
			HttpServletRequest req, HttpServletResponse res, Model model) throws IOException {
		String error = StringUtils.trimToEmpty((String) req.getAttribute("jakarta.servlet.error.message"));
		int statusCode = NumberUtils.toInt(code, 400);
		model.addAttribute("title", "Something went wrong... <small><tt>" + error + "</tt></small>");
		model.addAttribute("message", "Error code: " + Optional.ofNullable(req.getAttribute("jakarta.servlet.error.status_code")).
				orElse(statusCode) + " " + HttpStatus.valueOf(statusCode).getReasonPhrase());
		model.addAttribute("landing", false);

		if (Para.getConfig().inProduction() || Strings.CS.startsWith((CharSequence)
				req.getAttribute("jakarta.servlet.forward.request_uri"), ParaServer.API_PATH + "/")) {
			res.setContentType(MediaType.APPLICATION_JSON_VALUE);
			return GenericExceptionMapper.getExceptionResponse(statusCode, error);
		} else {
			return ResponseEntity.status(statusCode).contentType(MediaType.TEXT_HTML).
					body(Utils.compileMustache(model.asMap(), ParaServer.loadResource("templates/base.html")));
		}
	}
}
