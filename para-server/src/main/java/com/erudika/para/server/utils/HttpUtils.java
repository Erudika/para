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
package com.erudika.para.server.utils;

import com.erudika.para.core.App;
import com.erudika.para.core.Form;
import com.erudika.para.core.utils.Para;
import static com.erudika.para.core.utils.Para.getEmailer;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.utils.RateLimiter;
import com.erudika.para.core.utils.Utils;
import com.erudika.para.server.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

/**
 * Various utilities for HTTP stuff - cookies, AJAX, etc.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class HttpUtils {

	private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);
	private static CloseableHttpClient httpclient;
	private static final RateLimiter EMAIL_LIMITER_STRICT = Para.createRateLimiter(10, 100, 200);
	private static final RateLimiter EMAIL_LIMITER_LAX = Para.createRateLimiter(100, 500, 1000);

	/**
	 * Default private constructor.
	 */
	private HttpUtils() { }

	/**
	 * HTTP client.
	 * @return client instance
	 */
	static CloseableHttpClient getHttpClient() {
		if (httpclient == null) {
			int timeout = 5;
			httpclient = HttpClientBuilder.create().
					setDefaultRequestConfig(RequestConfig.custom().
							setConnectionRequestTimeout(timeout, TimeUnit.SECONDS).
							build()).
					build();
		}
		return httpclient;
	}

	/**
	 * Checks if a request comes from JavaScript.
	 * @param request HTTP request
	 * @return true if AJAX
	 */
	public static boolean isAjaxRequest(HttpServletRequest request) {
		return "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With")) ||
				"XMLHttpRequest".equalsIgnoreCase(request.getParameter("X-Requested-With"));
	}

	/////////////////////////////////////////////
	//    	   COOKIE & STATE UTILS
	/////////////////////////////////////////////

	/**
	 * Sets a cookie.
	 * @param name the name
	 * @param value the value
	 * @param req HTTP request
	 * @param res HTTP response
	 */
	public static void setStateParam(String name, String value, HttpServletRequest req, HttpServletResponse res) {
		setStateParam(name, value, req, res, false);
	}

	/**
	 * Sets a cookie.
	 * @param name the name
	 * @param value the value
	 * @param req HTTP request
	 * @param res HTTP response
	 * @param httpOnly HTTP only flag
	 */
	public static void setStateParam(String name, String value, HttpServletRequest req,
			HttpServletResponse res, boolean httpOnly) {
		setRawCookie(name, value, req, res, httpOnly, -1);
	}

	/**
	 * Reads a cookie.
	 * @param name the name
	 * @param req HTTP request
	 * @return the cookie value
	 */
	public static String getStateParam(String name, HttpServletRequest req) {
		return getCookieValue(req, name);
	}

	/**
	 * Deletes a cookie.
	 * @param name the name
	 * @param req HTTP request
	 * @param res HTTP response
	 */
	public static void removeStateParam(String name, HttpServletRequest req,
			HttpServletResponse res) {
		setRawCookie(name, "", req, res, false, 0);
	}

	/**
	 * Sets a cookie.
	 * @param name the name
	 * @param value the value
	 * @param req HTTP request
	 * @param res HTTP response
	 * @param httpOnly HTTP only flag
	 * @param maxAge max age
	 */
	public static void setRawCookie(String name, String value, HttpServletRequest req,
			HttpServletResponse res, boolean httpOnly, int maxAge) {
		if (StringUtils.isBlank(name) || value == null || req == null || res == null) {
			return;
		}
		Cookie cookie = new Cookie(name, value);
		cookie.setHttpOnly(httpOnly);
		cookie.setMaxAge(maxAge < 0 ? Para.getConfig().sessionTimeoutSec() : maxAge);
		cookie.setPath("/");
		cookie.setSecure(req.isSecure());
		res.addCookie(cookie);
	}

	/**
	 * Reads a cookie.
	 * @param name the name
	 * @param req HTTP request
	 * @return the cookie value
	 */
	public static String getCookieValue(HttpServletRequest req, String name) {
		if (StringUtils.isBlank(name) || req == null) {
			return null;
		}
		Cookie[] cookies = req.getCookies();
		if (cookies == null) {
			return null;
		}
		//Otherwise, we have to do a linear scan for the cookie.
		for (Cookie cookie : cookies) {
			if (cookie.getName().equals(name)) {
				return cookie.getValue();
			}
		}
		return null;
	}

	/**
	 * Sets the auth cookie.
	 * @param name name
	 * @param value value
	 * @param maxAge maxAge
	 * @param request request
	 * @param response response
	 */
	public static void setAuthCookie(String name, String value, int maxAge,
			HttpServletRequest request, HttpServletResponse response) {
		setAuthCookie(name, value, true, maxAge, "Lax", request, response);
	}

	/**
	 * Sets the auth cookie.
	 * @param name name
	 * @param value value
	 * @param httpOnly HTTP only flag
	 * @param maxAge cookie validity in seconds
	 * @param sameSite SameSite value
	 * @param request request
	 * @param response response
	 */
	public static void setAuthCookie(String name, String value, boolean httpOnly, int maxAge, String sameSite,
			HttpServletRequest request, HttpServletResponse response) {
		String expires = DateFormatUtils.format(System.currentTimeMillis() + (maxAge * 1000),
				"EEE, dd-MMM-yyyy HH:mm:ss z", TimeZone.getTimeZone("GMT"));
		String contextPath = request.getContextPath();
		String path = contextPath.length() > 0 ? contextPath : "/";
		StringBuilder sb = new StringBuilder();
		sb.append(name).append("=").append(value).append(";");
		sb.append("Path=").append(path).append(";");
		sb.append("Expires=").append(expires).append(";");
		sb.append("Max-Age=").append(maxAge).append(";");
		if (httpOnly) {
			sb.append("HttpOnly;");
		}
		if (Strings.CI.startsWith(SecurityUtils.getRedirectUrl(request), "https://") || request.isSecure()) {
			sb.append("Secure;");
		}
		if (!StringUtils.isBlank(sameSite)) {
			sb.append("SameSite=").append(sameSite);
		}
		response.addHeader(HttpHeaders.SET_COOKIE, sb.toString());
	}

	/**
	 * Captcha validation method for reCAPTCHA v3, Turnstile and hCaptcha.
	 * @param request HTTP request
	 * @param captchaSecretKey serverside secret key
	 * @return true if validation was successful
	 */
	@SuppressWarnings("unchecked")
	public static boolean isValidCaptchaResponse(String captchaSecretKey, HttpServletRequest request) {
		String gRecaptcha = request.getParameter("g-recaptcha-response");
		String hCaptcha = request.getParameter("h-captcha-response");
		String cfTurnstile = request.getParameter("cf-turnstile-response");
		if (StringUtils.isBlank(gRecaptcha) && StringUtils.isBlank(hCaptcha) && StringUtils.isBlank(cfTurnstile)) {
			return false;
		}
		String captchaParamKey = "";
		String captchaParamValue = "";
		if (!StringUtils.isBlank(gRecaptcha)) {
			captchaParamKey = "g-recaptcha-response";
			captchaParamValue = gRecaptcha;
		} else if (!StringUtils.isBlank(hCaptcha)) {
			captchaParamKey = "h-captcha-response";
			captchaParamValue = hCaptcha;
		} else if (!StringUtils.isBlank(cfTurnstile)) {
			captchaParamKey = "cf-turnstile-response";
			captchaParamValue = cfTurnstile;
		}
		return isValidCaptchaResponse(captchaParamKey, captchaParamValue, captchaSecretKey);
	}

	/**
	 * Captcha validation method for reCAPTCHA v3, Turnstile and hCaptcha.
	 * @param captchaParamKey param key, i.e. g_captcha_response
	 * @param captchaParamValue param value (response)
	 * @param captchaSecretKey serverside secret key
	 * @return true if validation was successful
	 */
	@SuppressWarnings("unchecked")
	public static boolean isValidCaptchaResponse(String captchaParamKey, String captchaParamValue, String captchaSecretKey) {
		String url;
		url = switch (captchaParamKey) {
			case "g-recaptcha-response" -> "https://www.google.com/recaptcha/api/siteverify";
			case "h-captcha-response" -> "https://api.hcaptcha.com/siteverify";
			case "cf-turnstile-response" -> "https://challenges.cloudflare.com/turnstile/v0/siteverify";
			default -> "";
		};
		if (StringUtils.isBlank(url)) {
			return false;
		}
		List<NameValuePair> params = new ArrayList<>();
		params.add(new BasicNameValuePair("secret", captchaSecretKey));
		params.add(new BasicNameValuePair("response", captchaParamValue));
		HttpPost post = new HttpPost(url);
		post.setEntity(new UrlEncodedFormEntity(params));
		List<String> errors = new LinkedList<>();
		try {
			return getHttpClient().execute(post, (resp) -> {
				if (resp.getEntity() != null) {
					Map<String, Object> data = ParaObjectUtils.getJsonReader(Map.class).
							readValue(resp.getEntity().getContent());
					if (data != null && data.containsKey("success")) {
						if (data.get("error-codes") != null) {
							errors.addAll((List<String>) data.get("error-codes"));
						}
						return (boolean) data.getOrDefault("success", false);
					}
				}
				return false;
			});
		} catch (Exception ex) {
			logger.info("Failed to verify CAPTCHA: {} {} {}", captchaParamKey, errors, ex.getMessage());
		}
		return false;
	}

	/**
	 * Sends emails with rate limiting enabled.
	 * @param formData form data
	 * @param app app
	 * @param form form object
	 * @return OK if email was sent
	 * @throws IOException exception
	 */
	public static HttpStatus sendEmail(HttpUtils.MultipartForm formData, App app, Form form) throws IOException {
		if (app == null) {
			return org.springframework.http.HttpStatus.BAD_REQUEST;
		}

		String formId = (form != null) ? form.getId() : "";
		String subject;
		String body;
		boolean isSent;

		if (!(app.isSharingTable() ? EMAIL_LIMITER_STRICT : EMAIL_LIMITER_LAX).
				isAllowed(App.identifier(app.getId()), app.getId())) {
			logger.warn("Too many email send requests for app {}, form: {}",
					app.getId(), formId);
			return HttpStatus.TOO_MANY_REQUESTS;
		}

		if (form != null) {
			subject = form.getName() + ": " + formData.getSubject();
			body = form.isPlaintextOnly()
					? Utils.stripHtml(formData.getMessage())
					: (form.isMarkdownEnabled() ? Utils.markdownToHtml(formData.getMessage()) : formData.getMessage());
		} else {
			subject = formData.getSubject();
			body = formData.isPlaintextOnly()
					? Utils.stripHtml(formData.getMessage())
					: (formData.isMarkdownEnabled() ? Utils.markdownToHtml(formData.getMessage()) : formData.getMessage());
		}

		if (formData.getFile() != null) {
			isSent = getEmailer().sendEmail(Arrays.asList(formData.getToEmails()), subject, body,
					formData.getFile().getInputStream(),
					formData.getFile().getContentType(),
					formData.getFile().getOriginalFilename());
		} else {
			isSent = getEmailer().sendEmail(Arrays.asList(formData.getToEmails()), subject, body);
		}
		if (!isSent) {
			logger.warn("Email not sent via API request for app {}, form: {}", app.getId(), formId);
			return org.springframework.http.HttpStatus.BAD_REQUEST;
		}
		return org.springframework.http.HttpStatus.OK;
	}

	/**
	 * A class for handling the form data from form submissions.
	 */
	public static class MultipartForm {

		@NotBlank
		@Size(min = 0, max = 255)
		private String name;

		@Size(min = 0, max = 255)
		private String email;

		private String[] toEmails;

		@NotBlank @Size(min = 0, max = 10000)
		private String message;

		@Size(min = 0, max = 255)
		private String subject;

		private boolean plaintextOnly;
		private boolean markdownEnabled;

		private MultipartFile file;

		public MultipartForm() {
			this.plaintextOnly = true;
			this.markdownEnabled = false;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
		}

		public String[] getToEmails() {
			if (toEmails == null) {
				toEmails = new String[0];
			}
			if (toEmails.length > Form.MAX_RECIPIENTS_PER_FORM) {
				toEmails = Arrays.stream(toEmails).limit(Form.MAX_RECIPIENTS_PER_FORM).toArray(String[]::new);
			}
			return toEmails;
		}

		public void setToEmails(String[] toEmails) {
			this.toEmails = toEmails;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public String getSubject() {
			if (StringUtils.isBlank(subject)) {
				subject = "New message from " + name + (StringUtils.isBlank(email) ? "" : " <" + email + ">");
			}
			return subject;
		}

		public void setSubject(String subject) {
			this.subject = subject;
		}

		public boolean isPlaintextOnly() {
			return plaintextOnly;
		}

		public void setPlaintextOnly(boolean plaintextOnly) {
			this.plaintextOnly = plaintextOnly;
		}

		public boolean isMarkdownEnabled() {
			return plaintextOnly ? false : markdownEnabled;
		}

		public void setMarkdownEnabled(boolean markdownEnabled) {
			this.markdownEnabled = markdownEnabled;
		}

		public MultipartFile getFile() {
			return file;
		}

		public void setFile(MultipartFile file) {
			this.file = file;
		}

		abstract class MultipartFormMixin {
			@JsonDeserialize(using = MultipartFileDeserializer.class)
			abstract MultipartFile getFile();
		}

		static class MultipartFileDeserializer extends JsonDeserializer<MultipartFile> {

			MultipartFileDeserializer() { }

			@Override
			public MultipartFile deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
				String n = p.currentName();
				String f = p.getValueAsString();
				if (f.indexOf(";base64,") > 255) {
					return null;
				}
				String filename = Utils.getNewId();
				String contentType = StringUtils.substringBetween(f, "data:", ";");
				String base64 = StringUtils.substringAfter(f, ";base64,");
				byte[] content = Base64.getDecoder().decode(base64);

				return new MultipartFile() {
					public String getName() {
						return n;
					}
					public String getOriginalFilename() {
						return filename;
					}
					public String getContentType() {
						return contentType;
					}
					public boolean isEmpty() {
						return content.length == 0;
					}
					public long getSize() {
						return content.length;
					}
					public byte[] getBytes() throws IOException {
						return content;
					}
					public InputStream getInputStream() throws IOException {
						return new ByteArrayInputStream(content);
					}
					public void transferTo(File dest) throws IOException, IllegalStateException {
						throw new UnsupportedOperationException("Not supported yet.");
					}
				};
			}
		}

		public static MultipartForm fromJson(InputStream json) {
			ObjectMapper mapper = new ObjectMapper();
			mapper.addMixIn(MultipartForm.class, MultipartFormMixin.class);
			try {
				MultipartForm formData = mapper.readValue(json, MultipartForm.class);
				return formData;
			} catch (Exception e) {
				logger.error("Failed to deserialize MultipartForm: {}", e.getMessage());
			}
			return null;
		}

	}
}
