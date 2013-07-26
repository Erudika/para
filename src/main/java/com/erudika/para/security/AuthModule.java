package com.erudika.para.security;

import com.erudika.para.core.User;
import com.erudika.para.utils.DAO;
import com.erudika.para.utils.Utils;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.openid.AxFetchListFactory;
import org.springframework.security.openid.OpenIDAttribute;
import org.springframework.security.openid.OpenIDAuthenticationFilter;
import org.springframework.security.openid.OpenIDAuthenticationToken;

/**
 *
 * @author alexb
 */
public class AuthModule extends OpenIDAuthenticationFilter { 

	private static final String OPENID_ACTION = "openid_auth";
	private static final String FACEBOOK_ACTION = "facebook_auth";
	private static final String PASSWORD_ACTION = "password_auth";
	private static final String PASSWORD = "password";
	private static final String EMAIL = "email";
	private static final String APP_KEY = "b8db69a24a43f2ce134909f164a45263";	// salt for token gen

	private static DAO dao = DAO.getInstance();
	private static final Logger logger = Logger.getLogger(AuthModule.class.getName());
	private static final ObjectMapper mapper = new ObjectMapper();

	public AuthModule() {
	}
	
	public static SecurityContext getAuthenticatedContext(User user) {
		if(user == null || user.getId() == null) return SecurityContextHolder.createEmptyContext();
		SecurityContext ctx = new SecurityContextImpl();
		ctx.setAuthentication(new AuthModule.UserAuthentication(user));
		return ctx;
	}
	
	public static SecurityContext getAuthenticatedContext(String uid) {
		final User user = (User) dao.read(uid);
		return getAuthenticatedContext(user);
	}

	public static String getCookieHash(String identifier, Map<String, String> authmap){
		if(StringUtils.isBlank(identifier) || authmap.isEmpty()) return null;
		return Utils.MD5(identifier.concat(Utils.SEPARATOR).
				concat(StringUtils.trimToEmpty(authmap.get(DAO.CN_AUTHSTAMP))).
				concat(Utils.SEPARATOR).
				concat(StringUtils.trimToEmpty(authmap.get(DAO.CN_AUTHTOKEN))).
				concat(Utils.SEPARATOR).concat(APP_KEY));
	}
	
	public static void clearSession(HttpServletRequest req, HttpServletResponse res){
//		req.getSession().invalidate();
		Utils.removeStateParam(Utils.AUTH_COOKIE, req, res);
		try {
			req.logout();
		} catch (ServletException ex) {
			logger.log(Level.WARNING, null, ex);
		}
		SecurityContextHolder.clearContext();		
	}
	
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) 
			throws AuthenticationException, IOException {
		String requestURI = request.getRequestURI();
		UserAuthentication userAuth = null;
		User user;
		
		if(requestURI.endsWith(FACEBOOK_ACTION)){
			//Facebook Connect Authentication 
			String fbSig = request.getParameter("fbsig");
			String fbEmail = request.getParameter("fbemail");
			String fbName = request.getParameter("fbname");
			String fbID = verifiedFacebookID(fbSig);
			
			if (fbID != null) {
				//success!
				user = dao.readUserForIdentifier(fbID);
				if(user == null){
					//user is new
					user = new User();
					user.setEmail(StringUtils.isBlank(fbEmail) ? "email@domain.com" : fbEmail);
					user.setName(StringUtils.isBlank(fbName) ? "Your Name" : fbName);
					user.setIdentifier(fbID);
					DAO.getInstance().createUser(user);
				}
				userAuth = new UserAuthentication(user);
			}
		}else if(requestURI.endsWith(PASSWORD_ACTION)){
			String email = request.getParameter(EMAIL);
			String pass = request.getParameter(PASSWORD);
			
			if(DAO.getInstance().passwordMatches(pass, email)){
				//success!
				user = dao.readUserForIdentifier(email);
				userAuth = new UserAuthentication(user);
			}
		}else if(requestURI.endsWith(OPENID_ACTION)){
			Authentication oidAuth = super.attemptAuthentication(request, response);
			
			if(oidAuth == null){
				// hang on... redirecting to openid provider
				return null;	
			}else{
				//success!
				userAuth = new UserAuthentication((User) oidAuth.getPrincipal());
			}
		}
		
		onAuthSuccess(userAuth, request, response);
		return userAuth;
	}
	
	private void onAuthSuccess(Authentication userAuth, HttpServletRequest request, HttpServletResponse response){
		if(userAuth == null || userAuth.getPrincipal() == null || !((User) userAuth.getPrincipal()).isEnabled()) {
			throw new BadCredentialsException("Bad credentials.");
		}else{
			User user = (User) userAuth.getPrincipal();
			long authstamp = System.currentTimeMillis();
			DAO.getInstance().setAuthstamp(user.getIdentifier(), authstamp);
			Utils.setStateParam(Utils.AUTH_COOKIE, Base64.encodeBase64URLSafeString(user.getIdentifier().getBytes()).trim().
					concat(Utils.SEPARATOR).concat(getCookieHash(user, authstamp)), request, response, true);
		}
	}
	
	private String getCookieHash(User user, Long authstamp){
		Map<String, String> map = new TreeMap<String, String>();
		map.put(DAO.CN_AUTHSTAMP, authstamp.toString());
		map.put(DAO.CN_AUTHTOKEN, user.getAuthtoken());
		return getCookieHash(user.getIdentifier(), map);
	}
	
	private String verifiedFacebookID(String fbSig){
		if(!StringUtils.contains(fbSig, ".")) return null;
		String fbid = null;
		
		try {
			String[] parts = fbSig.split("\\.");
			byte[] sig = Base64.decodeBase64(parts[0]);
			byte[] json = Base64.decodeBase64(parts[1]);
			byte[] encodedJSON = parts[1].getBytes();	// careful, we compute against the base64 encoded version
			String decodedJSON = new String(json);
			JsonNode root = mapper.readTree(decodedJSON);
			
			if(StringUtils.contains(decodedJSON, "HMAC-SHA256")){
				SecretKey secret = new SecretKeySpec(Utils.FB_SECRET.getBytes(), "HMACSHA256");
				Mac mac = Mac.getInstance("HMACSHA256");
				mac.init(secret);
				byte[] digested = mac.doFinal(encodedJSON);
				if(Arrays.equals(sig, digested)){
					fbid = root.get("user_id").getTextValue();
				}
			}
		} catch (Exception ex) {
			logger.log(Level.INFO, "Failed to decode FB sig: {0}", ex);
		}

		return fbid;
	}
	
	public static class UserAuthentication implements Authentication {

		private static final long serialVersionUID = 1L;
		private final User principal;
		private final Object details;
		private boolean authenticated;

		public UserAuthentication(User principal) {
			this.principal = principal;
			this.details = principal;
			this.authenticated = true;
		}

		public Collection<GrantedAuthority> getAuthorities() {
			return new HashSet<GrantedAuthority>(principal.getAuthorities());
		}

		public Object getCredentials() {
			return principal.getIdentifier();
		}

		public Object getDetails() {
			return details;
		}

		public Object getPrincipal() {
			return principal;
		}

		public boolean isAuthenticated() {
			return authenticated;
		}

		public void setAuthenticated(boolean isAuthenticated) {
			throw new UnsupportedOperationException();
		}

		public String getName() {
			return principal.getIdentifier();
		}
	}

	public static class DefaultAxFetchListFactory implements AxFetchListFactory {
		private static final long serialVersionUID = 1L;
		public List<OpenIDAttribute> createAttributeList(String identifier) {
			List<OpenIDAttribute> list = new ArrayList<OpenIDAttribute>();
			if(identifier != null && identifier.matches("https://www.google.com/.*")){
				OpenIDAttribute email = new OpenIDAttribute("email", "http://axschema.org/contact/email");
				OpenIDAttribute first = new OpenIDAttribute("firstname", "http://axschema.org/namePerson/first");
				OpenIDAttribute last = new OpenIDAttribute("lastname", "http://axschema.org/namePerson/last");
				email.setCount(1);
				email.setRequired(true);
				first.setRequired(true);
				last.setRequired(true);
				list.add(email);
				list.add(first);
				list.add(last);
			}
			return list;
		}
	}
	
	public static class StandardUserService implements UserDetailsService, 
			AuthenticationUserDetailsService<OpenIDAuthenticationToken> {
		
		public UserDetails loadUserByUsername(String ident) throws UsernameNotFoundException {
			User user = dao.readUserForIdentifier(ident);

			if (user == null) {
				throw new UsernameNotFoundException(ident);
			}

			return user;
		}
		
		public UserDetails loadUserDetails(OpenIDAuthenticationToken token) {
			if(token == null) return null;

			String identifier = token.getIdentityUrl();
			User user = dao.readUserForIdentifier(identifier);

			if(user == null){
				// create new OpenID user
				String email = "email@domain.com";
				String firstName = null, lastName = null, fullName = null;
				List<OpenIDAttribute> attributes = token.getAttributes();

				for (OpenIDAttribute attribute : attributes) {
					if (attribute.getName().equals("email")) email = attribute.getValues().get(0);
					if (attribute.getName().equals("firstname")) firstName = attribute.getValues().get(0);
					if (attribute.getName().equals("lastname")) lastName = attribute.getValues().get(0);
					if (attribute.getName().equals("fullname")) fullName = attribute.getValues().get(0);
				}

				if (fullName == null) {
					if (firstName == null) firstName = "Your";
					if (lastName == null) lastName = "Name";
					fullName = firstName.concat(" ").concat(lastName);
				}

				user = new User();
				user.setEmail(email);
				user.setName(fullName);
				user.setIdentifier(identifier);
				DAO.getInstance().createUser(user);
			}

			return user;
		}
	}
}
