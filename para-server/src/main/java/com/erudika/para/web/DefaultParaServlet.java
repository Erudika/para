/*
 * Copyright 2013-2015 Erudika. http://erudika.com
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
package com.erudika.para.web;

import com.erudika.para.Para;
import com.erudika.para.utils.Config;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;

/**
 * Default Para servlet - prints logo
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@WebServlet(name = "DefaultParaServlet", urlPatterns = {"/"})
public class DefaultParaServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private String version;

	@Override
	public void init() throws ServletException {
		super.init();
		version = getClass().getPackage().getImplementationVersion();
		if (version == null) {
			try {
				Properties prop = new Properties();
				prop.load(getServletContext().getResourceAsStream("/META-INF/MANIFEST.MF"));
				version = prop.getProperty("Implementation-Version");
			} catch (Exception e) {
				LoggerFactory.getLogger(DefaultParaServlet.class).debug(null, e);
			}
		}
	}

	/**
	 * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
	 *
	 * @param request servlet request
	 * @param response servlet response
	 * @throws ServletException if a servlet-specific error occurs
	 * @throws IOException if an I/O error occurs
	 */
	protected void processRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("text/plain;charset=UTF-8");
		PrintWriter out = response.getWriter();
		try {
			if (Config.getConfigBoolean("print_logo", true)) {
				out.print(Para.LOGO);
				if (version != null) {
					out.println("  v" + version);
				}
			}
			out.println("");
			out.println("  Server is running. Go to '/v1/_setup' first to get your API keys.");
		} finally {
			out.close();
		}
	}

	/**
	 * Handles the HTTP <code>GET</code> method.
	 *
	 * @param request servlet request
	 * @param response servlet response
	 * @throws ServletException if a servlet-specific error occurs
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		processRequest(request, response);
	}

	/**
	 * Returns a short description of the servlet.
	 *
	 * @return a String containing servlet description
	 */
	@Override
	public String getServletInfo() {
		return "DefaultParaServlet";
	}

}
