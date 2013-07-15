/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erudika.para.utils;

import com.erudika.para.core.User;
import java.io.IOException;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

/**
 *
 * @author alexb
 */
public class SecurityFilter extends GenericFilterBean{

//	private static final boolean debug = false;

	public SecurityFilter() {
	}

	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
			throws IOException, ServletException {
		
		final HttpServletRequest request = (HttpServletRequest) req;
		final HttpServletResponse response = (HttpServletResponse) resp;
		
		String authCookie = Utils.getStateParam(Utils.AUTH_COOKIE, request, response);
		SecurityContext ctx = AuthModule.getAuthenticatedContext(getUserIdFromCookie(authCookie));
		if(ctx != null)	SecurityContextHolder.setContext(ctx);
		
		boolean isAuthenticated = SecurityContextHolder.getContext().getAuthentication() != null && 
				SecurityContextHolder.getContext().getAuthentication().isAuthenticated();
		
		// anti-CSRF token validation
		if(request.getMethod().equals("POST") && isAuthenticated && !request.getRequestURI().startsWith("/api")){
			User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			String storedCSRFToken = getCSRFtoken(user);
			String givenCSRFToken = request.getParameter("stoken");
			
			if(StringUtils.equals(storedCSRFToken, givenCSRFToken)){
				chain.doFilter(request, response);
			}else{
				badrequest(response, request.getRemoteHost(), request.getRemoteAddr(), 
						request.getHeader("User-Agent"), Utils.isAjaxRequest(request));
			}
		}else if(request.getRequestURI().startsWith("/api")){
			if(!StringUtils.equals(request.getHeader("Content-Type"), MediaType.APPLICATION_JSON)){
				String err = "'Content-Type' should be 'application/json'.";
				Response res = Utils.getJSONResponse(Response.Status.UNSUPPORTED_MEDIA_TYPE, err);
				response.getWriter().write(res.getEntity().toString());
				response.setContentType(MediaType.APPLICATION_JSON);
				response.setStatus(res.getStatus());
			}else{
				chain.doFilter(request, response);
			}
		}else{
			chain.doFilter(request, response);
		}
	}
	
	public static String getCSRFtoken(User user){
		if(user == null) return "";
		return Utils.MD5(user.getIdentifier().concat(Utils.SEPARATOR).
				concat(user.getAuthtoken()).concat(Utils.SEPARATOR).
				concat(user.getAuthstamp().toString()));
	}
	
	private void forbidden(HttpServletResponse response, String host, String address, 
			String userAgent) throws IOException{
		response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied!");
//		log("forbidden: "+host+"/"+address+" ("+userAgent+")");
	}
	
	private void badrequest(HttpServletResponse response, String host, String address, 
			String userAgent, boolean isAjax) throws IOException{
//		log("badrequest: "+host+"/"+address+" ("+userAgent+")");
		String path = getFilterConfig().getServletContext().getContextPath();
		if(isAjax){
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request.");
		}else{
			response.sendRedirect(path + "/" + HttpServletResponse.SC_BAD_REQUEST);
		}
	}
	
	private String getUserIdFromCookie(String cookie){
		if(StringUtils.isBlank(cookie) || !cookie.contains(Utils.SEPARATOR)) return null;
		String[] tuparts = cookie.split(Utils.SEPARATOR);
		String identifier = new String(Base64.decodeBase64(tuparts[0]));
		String hash = tuparts[1];
		
		Map<String, String> authmap = DAO.getInstance().loadAuthMap(identifier);
	 	
		if(authmap.isEmpty()) return null;
		String uid = authmap.get(DAO.CN_ID);
		String h = AuthModule.getCookieHash(identifier, authmap);
		return StringUtils.equals(hash, h) ? uid : null; 
	}
}
