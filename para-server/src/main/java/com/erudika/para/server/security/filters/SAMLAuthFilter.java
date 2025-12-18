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
import com.erudika.para.core.User;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.Utils;
import com.erudika.para.server.security.AuthenticatedUserDetails;
import com.erudika.para.server.security.SecurityUtils;
import com.erudika.para.server.security.UserAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.Saml2Exception;
import org.springframework.security.saml2.core.Saml2ParameterNames;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.authentication.AbstractSaml2AuthenticationRequest;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationToken;
import org.springframework.security.saml2.provider.service.authentication.Saml2RedirectAuthenticationRequest;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertion;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;
import org.springframework.security.saml2.provider.service.web.RelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.Saml2AuthenticationRequestRepository;
import org.springframework.security.saml2.provider.service.web.authentication.OpenSaml5AuthenticationRequestResolver;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher.MatchResult;
import org.springframework.web.util.UriComponentsBuilder;

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

	private static final String DEFAULT_NAME_ID_FORMAT = "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified";
	private static final String PROP_SP_ENTITY_ID = "security.saml.sp.entityid";
	private static final String PROP_SP_ACS = "security.saml.sp.assertion_consumer_service.url";
	private static final String PROP_SP_NAMEID = "security.saml.sp.nameidformat";
	private static final String PROP_SP_CERT = "security.saml.sp.x509cert";
	private static final String PROP_SP_PRIVATE_KEY = "security.saml.sp.privatekey";
	private static final String PROP_IDP_METADATA_URL = "security.saml.idp.metadata_url";
	private static final String PROP_IDP_ENTITY_ID = "security.saml.idp.entityid";
	private static final String PROP_IDP_SSO_URL = "security.saml.idp.single_sign_on_service.url";
	private static final String PROP_IDP_CERT = "security.saml.idp.x509cert";
	private static final String PROP_SEC_AUTHN_SIGNED = "security.saml.security.authrequest_signed";
	private static final String PROP_SEC_SIGN_METADATA = "security.saml.security.sign_metadata";
	private final Saml2AuthenticationRequestRepository<AbstractSaml2AuthenticationRequest> requestRepo;

	/**
	 * Default constructor.
	 * @param defaultFilterProcessesUrl the url of the filter
	 */
	public SAMLAuthFilter(final String defaultFilterProcessesUrl) {
		super(defaultFilterProcessesUrl);
		this.requestRepo = new CachingSaml2AuthenticationRequestRepository();
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
		final String requestURI = request.getServletPath();
		UserAuthentication userAuth = null;

		if (!requestURI.startsWith(SAML_ACTION)) {
			return SecurityUtils.checkIfActive(userAuth, SecurityUtils.getAuthenticatedUser(userAuth), true);
		}

		String appid = SecurityUtils.getAppidFromAuthRequest(request);
		boolean samlSettingsLoaded = false;

		try {
			App app = Para.getDAO().read(App.id(appid));
			if (app == null) {
				LOG.warn("App '{}' not found for SAML authentication.", appid);
				return SecurityUtils.checkIfActive(null, null, true);
			}

			Optional<RelyingPartyRegistration> registrationOpt = buildRelyingPartyRegistration(app, appid,
					request.getParameter("entityid"));
			if (registrationOpt.isEmpty()) {
				LOG.error("Failed to create SAML relying party registration for app '{}'.", appid);
				return SecurityUtils.checkIfActive(null, null, true);
			}
			samlSettingsLoaded = true;
			RelyingPartyRegistration registration = registrationOpt.get();

			if (request.getParameter("SAMLResponse") != null) {
				Saml2AuthenticationToken token = new Saml2AuthenticationToken(registration,
						Utils.base64dec(request.getParameter("SAMLResponse")), requestRepo.loadAuthenticationRequest(request));
				Authentication authentication = getAuthenticationManager().authenticate(token);
				if (authentication instanceof Saml2Authentication saml && authentication.isAuthenticated()) {
					userAuth = getOrCreateUser(app, ((Saml2ResponseAssertion) saml.getCredentials()).getAttributes());
				} else {
					throw new AuthenticationServiceException("Authentication failed: invalid SAML response.");
				}
			} else {
				AbstractSaml2AuthenticationRequest samlRequest = buildAuthenticationRequest(request, registration);
				handleRedirectBinding(response, (Saml2RedirectAuthenticationRequest) samlRequest);
				requestRepo.saveAuthenticationRequest(samlRequest, request, response);
				return null; // redirect to IDP
			}
		} catch (Saml2Exception ex) {
			LOG.error("Failed to authenticate app '{}' with SAML: {}", appid, ex.getMessage());
		} catch (GeneralSecurityException ex) {
			LOG.error("Invalid SAML certificate or key for app '{}': {}", appid, ex.getMessage());
		} catch (Exception ex) {
			LOG.error("Failed to authenticate app '" + appid + "' with SAML: ", ex);
		} finally {
			if (!samlSettingsLoaded) {
				LOG.error("Failed to load SAML certificate for app '{}'.", appid);
			}
			requestRepo.removeAuthenticationRequest(request, response);
		}
		return SecurityUtils.checkIfActive(userAuth, SecurityUtils.getAuthenticatedUser(userAuth), true);
	}

	/**
	 * Creates or loads a Para user based on the SAML assertion attributes.
	 * @param app the app where the user will be created, use null for root app
	 * @param samlAttributes SAML attibutes from response assertion
	 * @return {@link UserAuthentication} object or null if something went wrong
	 * @throws IOException ex
	 */
	public UserAuthentication getOrCreateUser(App app, Map<String, List<Object>> samlAttributes) throws IOException {
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
				if (Utils.isValidEmail(samlUserId)) {
					email = samlUserId;
				} else if (!StringUtils.isBlank(emailDomain)) {
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
		if (!Strings.CS.equals(user.getPicture(), picture)) {
			user.setPicture(picture);
			update = true;
		}
		if (!StringUtils.isBlank(email) && !Strings.CS.equals(user.getEmail(), email)) {
			user.setEmail(email);
			update = true;
		}
		if (!StringUtils.isBlank(name) && !Strings.CS.equals(user.getName(), name)) {
			user.setName(name);
			update = true;
		}
		return update;
	}

	private static Map<String, String> populateUserData(App app, Map<String, List<Object>> attributes) {
		Map<String, String> data = new HashMap<String, String>();
		String useridIdParam = Para.getConfig().getSettingForApp(app, "security.saml.attributes.id", "UserID");
		String pictureParam = Para.getConfig().getSettingForApp(app, "security.saml.attributes.picture", "Picture");
		String emailParam = Para.getConfig().getSettingForApp(app, "security.saml.attributes.email", "EmailAddress");
		String nameParam = Para.getConfig().getSettingForApp(app, "security.saml.attributes.name", "GivenName");
		String fnameParam = Para.getConfig().getSettingForApp(app, "security.saml.attributes.firstname", "FirstName");
		String lnameParam = Para.getConfig().getSettingForApp(app, "security.saml.attributes.lastname", "LastName");
		String emailDomain = Para.getConfig().getSettingForApp(app, "security.saml.domain", "paraio.com");

		if (attributes.containsKey(useridIdParam)) {
			data.put("uid", (String) attributes.get(useridIdParam).get(0));
			data.put("domain", emailDomain);
			if (attributes.containsKey(pictureParam) && !attributes.get(pictureParam).isEmpty()) {
				data.put("pic", (String) attributes.get(pictureParam).get(0));
			}
			if (attributes.containsKey(emailParam) && !attributes.get(emailParam).isEmpty()) {
				data.put("email", (String) attributes.get(emailParam).get(0));
			} else {
				LOG.warn("Missing value for SAML attribute '{}'.", emailParam);
			}
			if (attributes.containsKey(nameParam) && !attributes.get(nameParam).isEmpty()) {
				data.put("name", (String) attributes.get(nameParam).get(0));
			}
			if (!data.containsKey("name") && attributes.containsKey(fnameParam)) {
				String fname = (String) attributes.get(fnameParam).get(0);
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

	private AbstractSaml2AuthenticationRequest buildAuthenticationRequest(HttpServletRequest request,
			RelyingPartyRegistration registration) {
		RelyingPartyRegistrationResolver resolver = (req, registrationId) -> registration;
		OpenSaml5AuthenticationRequestResolver requestResolver = new OpenSaml5AuthenticationRequestResolver(resolver);
		requestResolver.setRequestMatcher(new StaticRequestMatcher(registration.getRegistrationId()));
		AbstractSaml2AuthenticationRequest samlRequest = requestResolver.resolve(request);

		if (samlRequest == null) {
			throw new AuthenticationServiceException("Failed to build SAML authentication request.");
		}
		return samlRequest;
	}

	private void handleRedirectBinding(HttpServletResponse response, Saml2RedirectAuthenticationRequest request)
			throws IOException {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(request.getAuthenticationRequestUri());
		if (StringUtils.isNotBlank(request.getSamlRequest())) {
			builder.queryParam("SAMLRequest", Utils.urlEncode(request.getSamlRequest()));
		}
		if (StringUtils.isNotBlank(request.getRelayState())) {
			builder.queryParam("RelayState", Utils.urlEncode(request.getRelayState()));
		}
		if (StringUtils.isNotBlank(request.getSigAlg())) {
			builder.queryParam("SigAlg", Utils.urlEncode(request.getSigAlg()));
		}
		if (StringUtils.isNotBlank(request.getSignature())) {
			builder.queryParam("Signature", Utils.urlEncode(request.getSignature()));
		}
		String s2 = builder.build(false).toUriString();
		response.sendRedirect(s2);
	}

	protected static Optional<RelyingPartyRegistration> buildRelyingPartyRegistration(App app, String appid,
			String requestedEntityId) throws GeneralSecurityException {
		if (app == null) {
			return Optional.empty();
		}

		SamlSettings settings = SamlSettings.from(app);
		if (StringUtils.isBlank(settings.spEntityId)) {
			LOG.error("Missing SP entity ID configuration for app '{}'.", appid);
			return Optional.empty();
		}

		Optional<RelyingPartyRegistration> registration;
		if (StringUtils.isNotBlank(settings.idpMetadataUrl)) {
			registration = buildFromMetadata(appid, requestedEntityId, settings);
		} else {
			registration = buildFromConfig(appid, settings);
		}

		return registration.map(reg -> applyOverrides(reg, settings));
	}

	private static Optional<RelyingPartyRegistration> buildFromMetadata(String appid, String requestedEntityId,
			SamlSettings settings) {
		try {
			List<RelyingPartyRegistration.Builder> candidates =
					List.copyOf(RelyingPartyRegistrations.collectionFromMetadataLocation(settings.idpMetadataUrl));
			for (RelyingPartyRegistration.Builder candidate : candidates) {
				RelyingPartyRegistration registration = candidate.registrationId(appid)
					.entityId(settings.spEntityId).assertingPartyMetadata((aspm) -> {
						aspm.singleSignOnServiceBinding(Saml2MessageBinding.REDIRECT);
					})
					.assertionConsumerServiceLocation(settings.spAcs)
					.assertionConsumerServiceBinding(Saml2MessageBinding.REDIRECT)
					.build();
				String entityId = registration.getAssertingPartyMetadata().getEntityId();
				if (StringUtils.isBlank(requestedEntityId) || Strings.CS.equals(entityId, requestedEntityId)) {
					return Optional.of(registration);
				}
			}
			LOG.warn("No matching IDP descriptor found in metadata for app '{}'.", appid);
		} catch (Saml2Exception ex) {
			LOG.error("Failed to parse IDP metadata for app '{}': {}", appid, ex.getMessage());
		}
		return Optional.empty();
	}

	private static Optional<RelyingPartyRegistration> buildFromConfig(String appid, SamlSettings settings)
			throws GeneralSecurityException {
		if (StringUtils.isBlank(settings.idpEntityId) || StringUtils.isBlank(settings.idpSsoUrl)) {
			LOG.error("Missing IDP configuration for app '{}'.", appid);
			return Optional.empty();
		}
		RelyingPartyRegistration.Builder builder = RelyingPartyRegistration.withRegistrationId(appid)
			.entityId(settings.spEntityId)
			.assertionConsumerServiceLocation(settings.spAcs)
			.assertionConsumerServiceBinding(Saml2MessageBinding.REDIRECT);
		builder.assertingPartyMetadata(metadata -> {
			metadata.entityId(settings.idpEntityId);
			metadata.singleSignOnServiceLocation(settings.idpSsoUrl);
			metadata.singleSignOnServiceBinding(Saml2MessageBinding.REDIRECT);
			if (settings.idpCertificate != null) {
				metadata.verificationX509Credentials(creds -> {
					creds.clear();
					creds.add(Saml2X509Credential.verification(settings.idpCertificate));
				});
			}
		});
		return Optional.of(builder.build());
	}

	private static RelyingPartyRegistration applyOverrides(RelyingPartyRegistration registration,
			SamlSettings settings) {
		RelyingPartyRegistration.Builder builder = registration.mutate();
		if (StringUtils.isNotBlank(settings.spNameIdFormat)) {
			builder.nameIdFormat(settings.spNameIdFormat);
		}
		builder.authnRequestsSigned(settings.authnRequestSigned);
		configureSpCredentials(builder, settings);
		configureIdpOverrides(builder, settings);
		return builder.build();
	}

	private static void configureSpCredentials(RelyingPartyRegistration.Builder builder, SamlSettings settings) {
		if (settings.spPrivateKey != null && settings.spCertificate != null) {
			Saml2X509Credential signing = Saml2X509Credential.signing(settings.spPrivateKey, settings.spCertificate);
			Saml2X509Credential decryption = Saml2X509Credential.decryption(settings.spPrivateKey, settings.spCertificate);
			builder.signingX509Credentials(creds -> {
				creds.clear();
				creds.add(signing);
			});
			builder.decryptionX509Credentials(creds -> {
				creds.clear();
				creds.add(decryption);
			});
		}
	}

	private static void configureIdpOverrides(RelyingPartyRegistration.Builder builder, SamlSettings settings) {
		if (StringUtils.isBlank(settings.idpEntityId) && StringUtils.isBlank(settings.idpSsoUrl)
				&& settings.idpCertificate == null) {
			return;
		}
		builder.assertingPartyMetadata(metadata -> {
			if (StringUtils.isNotBlank(settings.idpEntityId)) {
				metadata.entityId(settings.idpEntityId);
			}
			if (StringUtils.isNotBlank(settings.idpSsoUrl)) {
				metadata.singleSignOnServiceLocation(settings.idpSsoUrl);
			}
			if (settings.idpCertificate != null) {
				metadata.verificationX509Credentials(creds -> {
					creds.clear();
					creds.add(Saml2X509Credential.verification(settings.idpCertificate));
				});
			}
		});
	}

	protected static boolean shouldSignMetadata(App app) {
		return getConfigPropBool(app, PROP_SEC_SIGN_METADATA, false);
	}

	private static PrivateKey parsePrivateKey(String pem) throws GeneralSecurityException {
		if (StringUtils.isBlank(pem)) {
			return null;
		}
		String normalized = pem.trim();
		if (normalized.contains("BEGIN RSA PRIVATE KEY")) {
			throw new GeneralSecurityException("PKCS#1 private keys are not supported. Convert the key to PKCS#8.");
		}
		byte[] decoded = decodePem(normalized);
		if (decoded.length == 0) {
			return null;
		}
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
		try {
			return KeyFactory.getInstance("RSA").generatePrivate(spec);
		} catch (GeneralSecurityException ex) {
			return KeyFactory.getInstance("EC").generatePrivate(spec);
		}
	}

	private static X509Certificate parseCertificate(String pem) throws GeneralSecurityException {
		if (StringUtils.isBlank(pem)) {
			return null;
		}
		byte[] decoded = decodePem(pem);
		if (decoded.length == 0) {
			return null;
		}
		try {
			CertificateFactory factory = CertificateFactory.getInstance("X.509");
			return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(decoded));
		} catch (CertificateException ex) {
			throw new GeneralSecurityException("Failed to parse certificate", ex);
		}
	}

	private static byte[] decodePem(String pem) {
		String content = pem.replaceAll("-----BEGIN(.*?)-----", "")
			.replaceAll("-----END(.*?)-----", "")
			.replaceAll("\\s", "");
		if (StringUtils.isBlank(content)) {
			return new byte[0];
		}
		return Base64.getMimeDecoder().decode(content);
	}

	private static String getConfigProp(App app, String propKey, String defaultValue) {
		return Para.getConfig().getSettingForApp(app, propKey, defaultValue);
	}

	private static boolean getConfigPropBool(App app, String propKey, boolean defaultValue) {
		return Boolean.parseBoolean(getConfigProp(app, propKey, Boolean.toString(defaultValue)));
	}

	private static class CachingSaml2AuthenticationRequestRepository implements
			Saml2AuthenticationRequestRepository<AbstractSaml2AuthenticationRequest> {

		@Override
		public AbstractSaml2AuthenticationRequest loadAuthenticationRequest(HttpServletRequest request) {
			return Para.getCache().get(request.getParameter(Saml2ParameterNames.RELAY_STATE));
		}

		@Override
		public void saveAuthenticationRequest(AbstractSaml2AuthenticationRequest authenticationRequest,
				HttpServletRequest request, HttpServletResponse response) {
			Para.getCache().put(authenticationRequest.getRelayState(), authenticationRequest);
		}

		@Override
		public AbstractSaml2AuthenticationRequest removeAuthenticationRequest(HttpServletRequest request,
				HttpServletResponse response) {
			String relayState = request.getParameter(Saml2ParameterNames.RELAY_STATE);
			AbstractSaml2AuthenticationRequest authenticationRequest = Para.getCache().get(relayState);
			Para.getCache().remove(relayState);
			return authenticationRequest;
		}
	}

	private static final class StaticRequestMatcher implements RequestMatcher {
		private final String registrationId;

		StaticRequestMatcher(String registrationId) {
			this.registrationId = registrationId;
		}

		@Override
		public boolean matches(HttpServletRequest request) {
			return true;
		}

		@Override
		public MatchResult matcher(HttpServletRequest request) {
			return MatchResult.match(Collections.singletonMap("registrationId", registrationId));
		}
	}

	private static final class SamlSettings {
		private final String spEntityId;
		private final String spAcs;
		private final String spNameIdFormat;
		private final PrivateKey spPrivateKey;
		private final X509Certificate spCertificate;
		private final String idpMetadataUrl;
		private final String idpEntityId;
		private final String idpSsoUrl;
		private final X509Certificate idpCertificate;
		private final boolean authnRequestSigned;

		private SamlSettings(String spEntityId, String spAcs, String spNameIdFormat, PrivateKey spPrivateKey,
				X509Certificate spCertificate, String idpMetadataUrl, String idpEntityId, String idpSsoUrl,
				X509Certificate idpCertificate, boolean authnRequestSigned) {
			this.spEntityId = spEntityId;
			this.spAcs = StringUtils.isBlank(spAcs) ? spEntityId : spAcs;
			this.spNameIdFormat = StringUtils.isBlank(spNameIdFormat) ? DEFAULT_NAME_ID_FORMAT : spNameIdFormat;
			this.spPrivateKey = spPrivateKey;
			this.spCertificate = spCertificate;
			this.idpMetadataUrl = idpMetadataUrl;
			this.idpEntityId = idpEntityId;
			this.idpSsoUrl = idpSsoUrl;
			this.idpCertificate = idpCertificate;
			this.authnRequestSigned = authnRequestSigned;
		}

		private static SamlSettings from(App app) throws GeneralSecurityException {
			String spEntityId = getConfigProp(app, PROP_SP_ENTITY_ID, "");
			String spAcs = getConfigProp(app, PROP_SP_ACS, spEntityId);
			String nameId = getConfigProp(app, PROP_SP_NAMEID, DEFAULT_NAME_ID_FORMAT);
			PrivateKey spPrivateKey = parsePrivateKey(Utils.base64dec(getConfigProp(app, PROP_SP_PRIVATE_KEY, "")));
			X509Certificate spCertificate = parseCertificate(Utils.base64dec(getConfigProp(app, PROP_SP_CERT, "")));
			String metadataUrl = getConfigProp(app, PROP_IDP_METADATA_URL, "");
			String idpEntityId = getConfigProp(app, PROP_IDP_ENTITY_ID, "");
			String idpSso = getConfigProp(app, PROP_IDP_SSO_URL, "");
			X509Certificate idpCertificate = parseCertificate(Utils.base64dec(getConfigProp(app, PROP_IDP_CERT, "")));
			boolean authnSigned = getConfigPropBool(app, PROP_SEC_AUTHN_SIGNED, false);
			return new SamlSettings(spEntityId, spAcs, nameId, spPrivateKey, spCertificate, metadataUrl,
				idpEntityId, idpSso, idpCertificate, authnSigned);
		}
	}
}
