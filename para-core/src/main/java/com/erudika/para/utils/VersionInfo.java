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
package com.erudika.para.utils;

/**
 * Uses the generated class Version.java to display version information.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class VersionInfo {

	private VersionInfo() {
	}

	/**
	 * Returns the current version from Maven.
	 * @return version string
	 */
	public static String getVersion() {
		return Version.getVersion();
	}

	/**
	 * Returns the current group id from Maven.
	 * @return id string
	 */
	public static String getGroupId() {
		return Version.getGroupId();
	}

	/**
	 * Returns the current artifact id from Maven.
	 * @return id string
	 */
	public static String getArtifactId() {
		return Version.getArtifactId();
	}

	/**
	 * Returns the current revision from Git.
	 * @return version string
	 */
	public static String getRevision() {
		return Version.getRevision();
	}

	/**
	 * Returns the Git repo starting with "scm:git:git" from Maven.
	 * @return repo string
	 */
	public static String getGIT() {
		return Version.getGIT();
	}

	/**
	 * Returns the Git branch.
	 * @return git branch name
	 */
	public static String getGITBranch() {
		return Version.getGITBranch();
	}

}
