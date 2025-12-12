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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Controller
public class NotFoundController {

	@Inject
	public NotFoundController() {
	}

	@GetMapping("/not-found")
	public ResponseEntity<?> get(HttpServletRequest req, HttpServletResponse res, Model model) {
		model.addAttribute("title", "Not Found - 404");
		model.addAttribute("landing", false);

		if (Para.getConfig().inProduction()) {
			return GenericExceptionMapper.getExceptionResponse(HttpStatus.NOT_FOUND.value(), "Not found");
		} else {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML).
					body(Utils.compileMustache(model.asMap(), ParaServer.loadResource("templates/base.html")));
		}
	}
}
