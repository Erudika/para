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
package com.erudika.para.utils;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public class DynamoDBHelper {
	
	private static volatile boolean called = false;
	
	/**
	 * Start a new one at this port.
	 *
	 * @param port The port to start at
	 * @throws IOException If fails to start
	 */
	public static void start(File dir) {
		if(called) return;
		try {
			if(dir == null) dir = new File(System.getProperty("dynamodir"));
			final Process proc = new ProcessBuilder().command(new String[]{
				String.format("%s%sbin%<sjava", System.getProperty("java.home"), System.getProperty("file.separator")), 
				String.format("-Djava.library.path=%s", dir.getAbsolutePath()), "-jar", "DynamoDBLocal.jar"}).
					directory(dir).redirectErrorStream(false).start();
			called = true;
			Thread.sleep(2000);
			System.out.println("------ DynamoDB START ------");
			Utils.attachShutdownHook(DynamoDBHelper.class, new Thread(){
				public void run() {
					System.out.println("------ DynamoDB STOP ------");
					proc.destroy();
				}			
			});
		} catch (Exception e) {
			System.out.println(e);
		}
	}
//
//	/**
//	 * Stop a running one at this port.
//	 *
//	 * @param port The port to stop at
//	 */
//	public void stop(final int port) {
//		final Process proc = this.processes.get(port);
//		if (proc == null) {
//			throw new IllegalArgumentException(
//					String.format(
//					"No DynamoDB Local instances running on port %d", port));
//		}
//		proc.destroy();
//	}
	
}
