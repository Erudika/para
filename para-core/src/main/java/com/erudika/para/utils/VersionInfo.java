/*
 * Copyright 2013-2016 Erudika. http://erudika.com
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

	public static String getVersion() {
		return Version.getVersion();
	}

	public static String getGroupId() {
		return Version.getGroupId();
	}

	public static String getArtifactId() {
		return Version.getArtifactId();
	}

	public static String getRevision() {
		return Version.getRevision();
	}

	public static String getGIT() {
		return Version.getGIT();
	}

	public static String getGITBranch() {
		return Version.getGITBranch();
	}

}
