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
package com.erudika.para.server.security.filters;

import com.erudika.para.core.App;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.Utils;
import com.onelogin.saml2.exception.SettingsException;
import com.onelogin.saml2.settings.Saml2Settings;
import com.onelogin.saml2.settings.SettingsBuilder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import java.io.IOException;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.GenericFilterBean;

/**
 * A filter which returns the SAML metadata for a particular app.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SAMLMetadataFilter extends GenericFilterBean {

	private static final Logger LOG = LoggerFactory.getLogger(SAMLMetadataFilter.class);

	/**
	 * The default filter mapping.
	 */
	public static final String SAML_ACTION = "/saml_metadata";

	/**
	 * Creates the filter that exposes service provider metadata.
	 */
	public SAMLMetadataFilter() {
		// default constructor
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;

		final String requestURI = request.getServletPath();
		String appid;

		if (requestURI.startsWith(SAML_ACTION)) {
			appid = Para.getConfig().getRootAppIdentifier();
			if (requestURI.startsWith(SAML_ACTION + "/")) {
				String id = Utils.urlDecode(Strings.CS.removeStart(requestURI, SAML_ACTION + "/"));
				if (!id.isEmpty()) {
					appid = id;
				} else {
					appid = Para.getConfig().getRootAppIdentifier();
				}
			}

			try {
				App app = Para.getDAO().read(App.id(appid));
				if (app != null && app.getSetting("security.saml.sp.entityid") != null) {
					SettingsBuilder builder = new SettingsBuilder();
					Saml2Settings settings = builder.fromValues(SAMLAuthFilter.getSAMLSettings(app)).build();
					settings.setSPValidationOnly(true);
					String metadata = settings.getSPMetadata();
					List<String> errors = Saml2Settings.validateMetadata(metadata);
					if (errors.isEmpty()) {
						response.setContentType(MediaType.TEXT_XML_VALUE);
						response.setCharacterEncoding(Para.getConfig().defaultEncoding());
						response.getOutputStream().println(metadata);
						response.setStatus(SC_OK);
						return;
					} else {
						response.sendError(SC_BAD_REQUEST, StringUtils.join(errors, "; "));
						response.setStatus(SC_BAD_REQUEST);
						return;
					}
				}
			} catch (SettingsException ex) {
				LOG.error("Invalid SAML settings for app {}:", appid, ex);
			} catch (Exception ex) {
				LOG.error(null, ex);
			}
			response.sendError(SC_BAD_REQUEST);
			response.setStatus(SC_BAD_REQUEST);
			return;
		}
		chain.doFilter(request, response);
	}

}
