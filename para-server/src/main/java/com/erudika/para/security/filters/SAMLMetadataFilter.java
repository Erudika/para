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
package com.erudika.para.security.filters;

import com.erudika.para.Para;
import com.erudika.para.core.App;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.onelogin.saml2.exception.SettingsException;
import com.onelogin.saml2.settings.Saml2Settings;
import com.onelogin.saml2.settings.SettingsBuilder;
import java.io.IOException;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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


	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;

		final String requestURI = request.getRequestURI();
		String appid;

		if (requestURI.startsWith(SAML_ACTION)) {
			appid = Config.getRootAppIdentifier();
			if (requestURI.startsWith(SAML_ACTION + "/")) {
				String id = Utils.urlDecode(StringUtils.removeStart(requestURI, SAML_ACTION + "/"));
				if (!id.isEmpty()) {
					appid = id;
				} else {
					appid = Config.getRootAppIdentifier();
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
						response.setContentType(MediaType.TEXT_XML);
						response.setCharacterEncoding(Config.DEFAULT_ENCODING);
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
