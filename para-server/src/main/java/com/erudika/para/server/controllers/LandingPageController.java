/*
 * Copyright 2013-2026 Erudika. https://erudika.com
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
import com.erudika.para.server.pages.LandingPage;
import com.erudika.para.server.rest.GenericExceptionMapper;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * A controller that serves the landing page.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Controller
@ConditionalOnProperty(value = "para.landing_page_enabled", havingValue = "true")
public class LandingPageController {

	private LandingPage page;

	/**
	 * No-args constructor.
	 */
	public LandingPageController(@Nullable LandingPage page) {
		if (page == null) {
			this.page = new LandingPage() {
				public String getHTML(Map<String, Object> model) {
					return Utils.compileMustache(model, ParaServer.loadResource("templates/base.html"));
				}
			};
		} else {
			this.page = page;
		}
	}

	/**
	 * Handles GET requests for the landing page.
	 * @param req request
	 * @param res response
	 * @param model model
	 * @return response
	 */
	@GetMapping({"", "/", "/index.html", "/index.htm"})
	public ResponseEntity<?> get(HttpServletRequest req, HttpServletResponse res, Model model) {
		model.addAttribute("landing", true);
		model.addAttribute("title", "Para &mdash; the backend for <i>busy</i> developers!");
		if (Para.getConfig().inProduction()) {
			return GenericExceptionMapper.getExceptionResponse(200, "Hello from Para!");
		} else {
			return ResponseEntity.
					status(HttpStatus.OK).
					contentType(MediaType.TEXT_HTML).
					body(page.getHTML(model.asMap()));
		}
	}
}
