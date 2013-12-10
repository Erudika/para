/*
 * Copyright 2013 Alex Bogdanovski <albogdano@me.com>.
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
 * You can reach the author at: https://github.com/albogdano
 */
package com.erudika.para.security;

import com.erudika.para.utils.Utils;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class SecurityFilter extends GenericFilterBean{

	public SecurityFilter() {
	}

	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
			throws IOException, ServletException {
		
		final HttpServletRequest request = (HttpServletRequest) req;
		final HttpServletResponse response = (HttpServletResponse) resp;
		
		if(request.getRequestURI().startsWith("/api/")){
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

//	private void forbidden(HttpServletResponse response, String host, String address, 
//			String userAgent) throws IOException{
//		response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied!");
//		log("forbidden: "+host+"/"+address+" ("+userAgent+")");
//	}
	
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
}
