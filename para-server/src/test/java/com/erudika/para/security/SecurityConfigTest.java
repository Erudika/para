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
package com.erudika.para.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erudika.para.core.utils.Para;
import com.erudika.para.server.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Verifies the default web authorization rule and configured public paths.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityConfigTest.TestConfig.class)
class SecurityConfigTest {

	@Autowired
	private WebApplicationContext context;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void nonIgnoredWebPathRequiresAuthentication() throws Exception {
		mvc.perform(get("/private-page")).andExpect(status().is3xxRedirection());
	}

	@Test
	void authenticatedWebPathIsAllowed() throws Exception {
		mvc.perform(get("/private-page").with(user("test-user")))
				.andExpect(status().isOk())
				.andExpect(content().string("private"));
	}

	@Test
	void configuredIgnoredPathIsPublic() throws Exception {
		mvc.perform(get("/images/test.svg"))
				.andExpect(status().isOk())
				.andExpect(content().string("public"));
	}

	@Test
	void configuredSignInPathIsPublic() throws Exception {
		mvc.perform(get(Para.getConfig().signinPath()))
				.andExpect(status().isOk())
				.andExpect(content().string("sign in"));
	}

	@Test
	void versionedRestPathKeepsRestAuthentication() throws Exception {
		mvc.perform(get("/v1/private-api")).andExpect(status().isUnauthorized());
	}

	@Test
	void authenticatedMutationWithoutCsrfTokenIsRejected() throws Exception {
		mvc.perform(post("/private-page").with(user("test-user")))
				.andExpect(status().isForbidden());
	}

	@Test
	void authenticatedMutationWithCsrfTokenIsAllowed() throws Exception {
		mvc.perform(post("/private-page").with(user("test-user")).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(content().string("posted"));
	}

	@Configuration
	@EnableWebMvc
	@Import(SecurityConfig.class)
	static class TestConfig {

		@Bean
		ProbeController probeController() {
			return new ProbeController();
		}
	}

	@RestController
	static class ProbeController {

		@GetMapping("/private-page")
		String privatePage() {
			return "private";
		}

		@PostMapping("/private-page")
		String privateMutation() {
			return "posted";
		}

		@GetMapping("/images/test.svg")
		String publicAsset() {
			return "public";
		}

		@GetMapping("/**")
		String signIn() {
			return "sign in";
		}

		@GetMapping("/v1/private-api")
		String privateApi() {
			return "private api";
		}
	}
}
