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
package com.erudika.para.core;

import com.erudika.para.annotations.Stored;
import javax.validation.constraints.Size;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.hibernate.validator.constraints.NotBlank;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class Address extends PObject{
	private static final long serialVersionUID = 1L;
	
	@Stored @NotBlank @Size(min=3, max=255) private String address;
	@Stored @NotBlank @Size(min=2, max=255) private String country;
	@Stored @NotBlank private String latlng;
	@Stored private String phone;
	
	public Address() {
		this(null);
	}

	public Address(String id) {
		setId(id);
		setName(getClassname());
	}
	
	public String getLatlng() {
		return latlng;
	}

	public void setLatlng(String latlng) {
		this.latlng = latlng;
	}
	
	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}
}

