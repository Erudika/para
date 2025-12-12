/*
 * Copyright 2013-2025 Erudika. http://erudika.com
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
package com.erudika.para.server.security;

import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;

/**
 * A protected path.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class ProtectedPath {

	private List<String> patterns;
	private List<String> roles;
	private Set<HttpMethod> methods;
	private boolean rest;

	/**
	 * Default constructor.
	 *
	 * @param patterns path patterns
	 * @param roles roles
	 * @param methods methods
	 * @param rest is it a REST resource
	 */
	public ProtectedPath(List<String> patterns, List<String> roles, Set<HttpMethod> methods, boolean rest) {
		this.patterns = patterns;
		this.roles = roles;
		this.methods = methods;
		this.rest = rest;
	}

	/**
	 * Patterns.
	 *
	 * @return patterns
	 */
	public List<String> getPatterns() {
		return patterns;
	}

	/**
	 * Patterns.
	 *
	 * @param patterns patterns
	 */
	public void setPatterns(List<String> patterns) {
		this.patterns = patterns;
	}

	/**
	 * Roles.
	 *
	 * @return roles
	 */
	public List<String> getRoles() {
		return roles;
	}

	/**
	 * Roles.
	 *
	 * @param roles roles
	 */
	public void setRoles(List<String> roles) {
		this.roles = roles;
	}

	/**
	 * Methods.
	 *
	 * @return methods
	 */
	public Set<HttpMethod> getMethods() {
		return methods;
	}

	/**
	 * Methods.
	 *
	 * @param methods methods
	 */
	public void setMethods(Set<HttpMethod> methods) {
		this.methods = methods;
	}

	/**
	 * Rest.
	 *
	 * @return true if resource path is REST
	 */
	public boolean isRest() {
		return rest;
	}

	/**
	 * Rest.
	 *
	 * @param isRest rest
	 */
	public void setRest(boolean isRest) {
		this.rest = isRest;
	}

}
