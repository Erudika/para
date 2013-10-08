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
package com.erudika.para.api;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public interface Votable extends ParaObject{
	
	public enum VoteType{
		UP, DOWN;
		public String toString() {
			return name().toLowerCase();
		}
	}
	
	public <P extends ParaObject> boolean voteUp(String userid);
	
	public <P extends ParaObject> boolean voteDown(String userid);
	
	public Integer getVotes();
	
}
