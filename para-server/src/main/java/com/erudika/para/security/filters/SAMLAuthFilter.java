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
import com.erudika.para.core.User;
import com.erudika.para.security.AuthenticatedUserDetails;
import com.erudika.para.security.SecurityUtils;
import com.erudika.para.security.UserAuthentication;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import com.onelogin.saml2.Auth;
import com.onelogin.saml2.exception.SettingsException;
import static com.onelogin.saml2.settings.IdPMetadataParser.parseRemoteXML;
import com.onelogin.saml2.settings.Saml2Settings;
import com.onelogin.saml2.settings.SettingsBuilder;
import static com.onelogin.saml2.settings.SettingsBuilder.DEBUG_PROPERTY_KEY;
import static com.onelogin.saml2.settings.SettingsBuilder.IDP_ENTITYID_PROPERTY_KEY;
import static com.onelogin.saml2.settings.SettingsBuilder.IDP_SINGLE_SIGN_ON_SERVICE_URL_PROPERTY_KEY;
import static com.onelogin.saml2.settings.SettingsBuilder.IDP_X509CERT_PROPERTY_KEY;
import static com.onelogin.saml2.settings.SettingsBuilder.SECURITY_AUTHREQUEST_SIGNED;
import static com.onelogin.saml2.settings.SettingsBuilder.SECURITY_SIGNATURE_ALGORITHM;
import static com.onelogin.saml2.settings.SettingsBuilder.SECURITY_SIGN_METADATA;
import static com.onelogin.saml2.settings.SettingsBuilder.SECURITY_WANT_ASSERTIONS_ENCRYPTED;
import static com.onelogin.saml2.settings.SettingsBuilder.SECURITY_WANT_ASSERTIONS_SIGNED;
import static com.onelogin.saml2.settings.SettingsBuilder.SECURITY_WANT_MESSAGES_SIGNED;
import static com.onelogin.saml2.settings.SettingsBuilder.SECURITY_WANT_NAMEID_ENCRYPTED;
import static com.onelogin.saml2.settings.SettingsBuilder.SECURITY_WANT_XML_VALIDATION;
import static com.onelogin.saml2.settings.SettingsBuilder.SP_ASSERTION_CONSUMER_SERVICE_URL_PROPERTY_KEY;
import static com.onelogin.saml2.settings.SettingsBuilder.SP_ENTITYID_PROPERTY_KEY;
import static com.onelogin.saml2.settings.SettingsBuilder.SP_NAMEIDFORMAT_PROPERTY_KEY;
import static com.onelogin.saml2.settings.SettingsBuilder.SP_PRIVATEKEY_PROPERTY_KEY;
import static com.onelogin.saml2.settings.SettingsBuilder.SP_X509CERT_PROPERTY_KEY;
import static com.onelogin.saml2.settings.SettingsBuilder.STRICT_PROPERTY_KEY;
import com.onelogin.saml2.util.Constants;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

/**
 * A filter that handles authentication requests to a SAML 2.0 identity provider (IDP).
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class SAMLAuthFilter extends AbstractAuthenticationProcessingFilter {

	private static final Logger LOG = LoggerFactory.getLogger(SAMLAuthFilter.class);

	/**
	 * The default filter mapping.
	 */
	public static final String SAML_ACTION = "/saml_auth"; // saml_auth/{appid}

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public SAMLAuthFilter(final String defaultFilterProcessesUrl) {
		super(defaultFilterProcessesUrl);
	}

	/**
	 * Handles an authentication request.
	 * @param request HTTP request
	 * @param response HTTP response
	 * @return an authentication object that contains the principal object if successful.
	 * @throws IOException ex
	 */
	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		final String requestURI = request.getRequestURI();
		UserAuthentication userAuth = null;

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
				App app = Para.getDAO().read(App.id(appid == null ? Config.getRootAppIdentifier() : appid));
				if (app != null) {
					SettingsBuilder builder = new SettingsBuilder();
					Map<String, Object> samlSettings;
					String idpMetaUrl = SecurityUtils.getSettingForApp(app, "security.saml.idp.metadata_url", "");

					if (StringUtils.isBlank(idpMetaUrl)) {
						samlSettings = getSAMLSettings(app);
					} else {
						samlSettings = parseRemoteXML(new URL(idpMetaUrl), request.getParameter("entityid"));
						samlSettings.putAll(getSAMLSettings(app)); // override IDP meta with config values
					}

					Saml2Settings settings = builder.fromValues(samlSettings).build();

					Auth auth = new Auth(settings, request, response);

					if (request.getParameter("SAMLResponse") != null) {
						auth.processResponse();
						if (auth.isAuthenticated()) {
							List<String> errors = auth.getErrors();
							if (errors.isEmpty()) {
								userAuth = getOrCreateUser(app, auth.getAttributes());
							} else {
								throw new AuthenticationServiceException(StringUtils.join(errors, "; "));
							}
						}
					} else {
						auth.login(settings.getSpAssertionConsumerServiceUrl().toString());
						return null; // redirect to IDP
					}
				}
			} catch (SettingsException ex) {
				LOG.error("Failed to authenticate app '{}' with SAML: {}", appid, ex.getMessage());
			} catch (Exception ex) {
				LOG.error(null, ex);
			}
		}

		return SecurityUtils.checkIfActive(userAuth, SecurityUtils.getAuthenticatedUser(userAuth), true);
	}

	/**
	 * @param app the app where the user will be created, use null for root app
	 * @param samlAttributes SAML attibutes from response assertion
	 * @return {@link UserAuthentication} object or null if something went wrong
	 * @throws IOException ex
	 */
	public UserAuthentication getOrCreateUser(App app, Map<String, List<String>> samlAttributes) throws IOException {
		UserAuthentication userAuth = null;
		User user = new User();
		Map<String, String> userData = populateUserData(app, samlAttributes);

		if (!userData.isEmpty()) {
			String samlUserId = userData.get("uid");
			String pic = userData.getOrDefault("pic", null);
			String email = userData.getOrDefault("email", null);
			String name = userData.getOrDefault("name", "");
			String emailDomain = userData.get("domain");

			if (StringUtils.isBlank(email)) {
				if (!StringUtils.isBlank(emailDomain)) {
					email = samlUserId.concat("@").concat(emailDomain);
				} else {
					LOG.warn("Blank email attribute for SAML user '{}'.", samlUserId);
					email = samlUserId + "@scoold.com";
				}
			}

			user.setAppid(getAppid(app));
			user.setIdentifier(Config.SAML_PREFIX.concat(samlUserId));
			user.setEmail(email);
			user = User.readUserForIdentifier(user);
			if (user == null) {
				//user is new
				user = new User();
				user.setActive(true);
				user.setAppid(getAppid(app));
				user.setEmail(email);
				user.setName(StringUtils.isBlank(name) ? "Anonymous" : name);
				user.setPassword(Utils.generateSecurityToken());
				user.setPicture(getPicture(pic));
				user.setIdentifier(Config.SAML_PREFIX.concat(samlUserId));
				String id = user.create();
				if (id == null) {
					throw new AuthenticationServiceException("Authentication failed: cannot create new user.");
				}
			} else {
				if (updateUserInfo(user, pic, email, name)) {
					user.update();
				}
			}
			userAuth = new UserAuthentication(new AuthenticatedUserDetails(user));
		}
		return SecurityUtils.checkIfActive(userAuth, user, false);
	}

	private boolean updateUserInfo(User user, String pic, String email, String name) {
		String picture = getPicture(pic);
		boolean update = false;
		if (!StringUtils.equals(user.getPicture(), picture)) {
			user.setPicture(picture);
			update = true;
		}
		if (!StringUtils.isBlank(email) && !StringUtils.equals(user.getEmail(), email)) {
			user.setEmail(email);
			update = true;
		}
		if (!StringUtils.isBlank(name) && !StringUtils.equals(user.getName(), name)) {
			user.setName(name);
			update = true;
		}
		return update;
	}

	private static Map<String, String> populateUserData(App app, Map<String, List<String>> attributes) {
		Map<String, String> data = new HashMap<String, String>();
		String useridIdParam = SecurityUtils.getSettingForApp(app, "security.saml.attributes.id", "UserID");
		String pictureParam = SecurityUtils.getSettingForApp(app, "security.saml.attributes.picture", "Picture");
		String emailParam = SecurityUtils.getSettingForApp(app, "security.saml.attributes.email", "EmailAddress");
		String nameParam = SecurityUtils.getSettingForApp(app, "security.saml.attributes.name", "GivenName");
		String fnameParam = SecurityUtils.getSettingForApp(app, "security.saml.attributes.firstname", "FirstName");
		String lnameParam = SecurityUtils.getSettingForApp(app, "security.saml.attributes.lastname", "LastName");
		String emailDomain = SecurityUtils.getSettingForApp(app, "security.saml.domain", "paraio.com");

		if (attributes.containsKey(useridIdParam)) {
			data.put("uid", attributes.get(useridIdParam).get(0));
			data.put("domain", emailDomain);
			if (attributes.containsKey(pictureParam) && !attributes.get(pictureParam).isEmpty()) {
				data.put("pic", attributes.get(pictureParam).get(0));
			}
			if (attributes.containsKey(emailParam) && !attributes.get(emailParam).isEmpty()) {
				data.put("email", attributes.get(emailParam).get(0));
			} else {
				LOG.warn("Missing value for SAML attribute '{}'.", emailParam);
			}
			if (attributes.containsKey(nameParam) && !attributes.get(nameParam).isEmpty()) {
				data.put("name", attributes.get(nameParam).get(0));
			}
			if (!data.containsKey("name") && attributes.containsKey(fnameParam)) {
				String fname = attributes.get(fnameParam).get(0);
				if (attributes.containsKey(lnameParam)) {
					data.put("name", StringUtils.trimToEmpty(fname + " " + attributes.get(lnameParam).get(0)));
				}
			}
			if (!data.containsKey("name") || StringUtils.isBlank(data.get("name"))) {
				LOG.warn("Missing values for SAML attributes '{}', '{}', '{}'.", nameParam, fnameParam, lnameParam);
			}
		} else {
			LOG.error("Incorrect SAML attibute mapping - couldn't find user id value for key '{}'.", useridIdParam);
		}
		return data;
	}

	private static String getPicture(String pic) {
		if (pic != null) {
			if (pic.contains("?")) {
				// user picture migth contain size parameters - remove them
				return pic.substring(0, pic.indexOf('?'));
			} else {
				return pic;
			}
		}
		return null;
	}

	private String getAppid(App app) {
		return (app == null) ? null : app.getAppIdentifier();
	}

	protected static Map<String, Object> getSAMLSettings(App app) {
		if (app == null) {
			return Collections.emptyMap();
		}
		Map<String, Object> conf = new HashMap<>();
		conf.put(STRICT_PROPERTY_KEY, true);
		conf.put(DEBUG_PROPERTY_KEY, !Config.IN_PRODUCTION);

		// SP
		String spEntityId = getConfigProp(app, SP_ENTITYID_PROPERTY_KEY, "");
		String spACS = getConfigProp(app, SP_ASSERTION_CONSUMER_SERVICE_URL_PROPERTY_KEY, spEntityId);
		conf.put(SP_ENTITYID_PROPERTY_KEY, spEntityId);
		conf.put(SP_ASSERTION_CONSUMER_SERVICE_URL_PROPERTY_KEY, StringUtils.isBlank(spACS) ? spEntityId : spACS);
		conf.put(SP_NAMEIDFORMAT_PROPERTY_KEY,
				getConfigProp(app, SP_NAMEIDFORMAT_PROPERTY_KEY, Constants.NAMEID_UNSPECIFIED));
		conf.put(SP_X509CERT_PROPERTY_KEY, Utils.base64dec(getConfigProp(app, SP_X509CERT_PROPERTY_KEY, "")));
		conf.put(SP_PRIVATEKEY_PROPERTY_KEY, Utils.base64dec(getConfigProp(app, SP_PRIVATEKEY_PROPERTY_KEY, "")));

		// IDP
		String entityId = getConfigProp(app, IDP_ENTITYID_PROPERTY_KEY, "");
		String ssoServiceUrl = getConfigProp(app, IDP_SINGLE_SIGN_ON_SERVICE_URL_PROPERTY_KEY, "");
		String idpCert = Utils.base64dec(getConfigProp(app, IDP_X509CERT_PROPERTY_KEY, ""));
		if (!StringUtils.isBlank(entityId)) {
			conf.put(IDP_ENTITYID_PROPERTY_KEY, entityId);
		}
		if (!StringUtils.isBlank(ssoServiceUrl)) {
			conf.put(IDP_SINGLE_SIGN_ON_SERVICE_URL_PROPERTY_KEY, ssoServiceUrl);
		}
		if (!StringUtils.isBlank(idpCert)) {
			conf.put(IDP_X509CERT_PROPERTY_KEY, idpCert);
		}

		// Security
		conf.put(SECURITY_AUTHREQUEST_SIGNED, getConfigPropBool(app, SECURITY_AUTHREQUEST_SIGNED, false));
		conf.put(SECURITY_WANT_MESSAGES_SIGNED, getConfigPropBool(app, SECURITY_WANT_MESSAGES_SIGNED, false));
		conf.put(SECURITY_WANT_ASSERTIONS_SIGNED, getConfigPropBool(app, SECURITY_WANT_ASSERTIONS_SIGNED, false));
		conf.put(SECURITY_WANT_ASSERTIONS_ENCRYPTED, getConfigPropBool(app, SECURITY_WANT_ASSERTIONS_ENCRYPTED, false));
		conf.put(SECURITY_WANT_NAMEID_ENCRYPTED, getConfigPropBool(app, SECURITY_WANT_NAMEID_ENCRYPTED, false));
		conf.put(SECURITY_SIGN_METADATA, getConfigPropBool(app, SECURITY_SIGN_METADATA, false));
		conf.put(SECURITY_WANT_XML_VALIDATION, getConfigPropBool(app, SECURITY_WANT_XML_VALIDATION, true));
		conf.put(SECURITY_SIGNATURE_ALGORITHM, getConfigProp(app, SECURITY_SIGNATURE_ALGORITHM, ""));

		return conf;
	}

	private static String getConfigProp(App app, String propKey, String defaultValue) {
		return SecurityUtils.getSettingForApp(app, "security.saml" +
				StringUtils.removeStart(propKey, "onelogin.saml2"), defaultValue);
	}

	private static boolean getConfigPropBool(App app, String propKey, boolean defaultValue) {
		return Boolean.parseBoolean(getConfigProp(app, propKey, Boolean.toString(defaultValue)));
	}
}
