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
import com.erudika.para.core.annotations.Email;
import com.erudika.para.core.utils.Para;
import static com.erudika.para.core.utils.Para.getEmailer;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.utils.RateLimiter;
import com.erudika.para.server.security.SecurityUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
	private static final RateLimiter EMAIL_LIMITER_STRICT = Para.createRateLimiter(10, 100);
	private static final RateLimiter EMAIL_LIMITER_LAX = Para.createRateLimiter(100, 5000);

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
	 * @param captchaParamKey param key, i.e. g_captcha_response
	 * @param captchaParamValue param value (response)
	 * @param captchaSecretKey serverside secret key
	 * @return true if validation was successful
	 */
	@SuppressWarnings("unchecked")
	public static boolean isValidCaptchaResponse(String captchaParamKey, String captchaParamValue,
			String captchaSecretKey) {
		if (StringUtils.isBlank(captchaParamValue) || StringUtils.isBlank(captchaSecretKey)) {
			return false;
		}
		String url;
		url = switch (captchaParamKey) {
			case "g-recaptcha-response" -> "https://www.google.com/recaptcha/api/siteverify";
			case "h-captcha-response" -> "https://api.hcaptcha.com/siteverify";
			case "cf-turnstile-response" -> "https://challenges.cloudflare.com/turnstile/v0/siteverify";
			default -> "";
		};
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
	 * @param formId form ID
	 * @return OK if email was sent
	 * @throws IOException exception
	 */
	public static HttpStatus sendEmail(HttpUtils.MultipartForm formData, App app, String formId) throws IOException {
		if (app == null) {
			return org.springframework.http.HttpStatus.BAD_REQUEST;
		}

		if (!(app.isSharingTable() ? EMAIL_LIMITER_STRICT : EMAIL_LIMITER_LAX).
				isAllowed(App.identifier(app.getId()), app.getId())) {
			logger.warn("Too many email send requests for app {}, form: {}", app.getId(), formId);
			return HttpStatus.TOO_MANY_REQUESTS;
		}

		boolean isSent;
		if (formData.getFile() != null) {
			isSent = getEmailer().sendEmail(Arrays.asList(formData.getToEmails()),
					formData.getSubject(),
					formData.getMessage(),
					formData.getFile().getInputStream(),
					formData.getFile().getContentType(),
					formData.getFile().getOriginalFilename());
		} else {
			isSent = getEmailer().sendEmail(Arrays.asList(formData.getToEmails()),
					formData.getSubject(),
					formData.getMessage());
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

		@NotBlank
		private String appid;

		@Email
		private String email;

		private String[] toEmails;

		@NotBlank @Size(min = 0, max = 10000)
		private String message;

		@Size(min = 0, max = 255)
		private String subject;

		@Size(min = 0, max = 255)
		private String captchaParamKey;
		private String captchaParamValue;

		private MultipartFile file;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getAppid() {
			return appid;
		}

		public void setAppid(String appid) {
			this.appid = appid;
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
				subject = "New email from Para [" + appid + "]";
			}
			return subject;
		}

		public void setSubject(String subject) {
			this.subject = subject;
		}

		public String getCaptchaParamKey() {
			return captchaParamKey;
		}

		public void setCaptchaParamKey(String captchaParamKey) {
			this.captchaParamKey = captchaParamKey;
		}

		public String getCaptchaParamValue() {
			return captchaParamValue;
		}

		public void setCaptchaParamValue(String captchaParamValue) {
			this.captchaParamValue = captchaParamValue;
		}

		public MultipartFile getFile() {
			return file;
		}

		public void setFile(MultipartFile file) {
			this.file = file;
		}

	}
}
