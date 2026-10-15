/*
 * Copyright 2013-2026 Erudika. http://erudika.com
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
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.utils.Utils;
import com.erudika.para.server.ParaServer;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.Strings;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Controller
public class ApiDocsController {

	@GetMapping("/apidocs")
	public ResponseEntity<?> apidocs(HttpServletRequest req, HttpServletResponse res, Model model) {
		if (!Para.getConfig().apiEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		model.addAttribute("title", "Para &mdash; API documentation");
		model.addAttribute("scriptslink", Para.getConfig().serverContextPath() + "/scripts");
		model.addAttribute("styleslink", Para.getConfig().serverContextPath() + "/styles");
		return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.TEXT_HTML).
				body(Utils.compileMustache(model.asMap(), ParaServer.loadResource("templates/apidocs.html")));
	}

	@ResponseBody
	@GetMapping(path = "/api.json", produces = "text/javascript")
	public ResponseEntity<String> json(HttpServletRequest req, HttpServletResponse res) throws JsonProcessingException {
		if (!Para.getConfig().apiEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		Yaml yaml = new Yaml();
		String yml = ParaServer.loadResource("templates/api.yaml");
		yml = Strings.CS.replaceOnce(yml, "{{serverUrl}}", "http://localhost:" + Para.getConfig().serverPort());
		yml = Strings.CS.replaceOnce(yml, "{{contextPath}}", Para.getConfig().serverContextPath());
		String result = ParaObjectUtils.getJsonWriter().writeValueAsString(yaml.load(yml));
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS)).eTag(Utils.md5(result)).body(result);
	}

}
