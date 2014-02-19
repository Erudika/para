/*
 * Copyright 2013 Alex Bogdanovski <alex@erudika.com>.
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
import org.hibernate.validator.constraints.NotBlank;

/**
 * This class represents an address. It enables location based search queries. 
 * It can be attached to any object by creating a {@link Linker} between the two.
 * @author Alex Bogdanovski <alex@erudika.com>
 * @see Linker
 */
public class Address extends PObject {
	private static final long serialVersionUID = 1L;

	@Stored @NotBlank @Size(min = 3, max = 255) private String address;
	@Stored @NotBlank @Size(min = 2, max = 255) private String country;
	@Stored @NotBlank private String latlng;
	@Stored private String phone;

	/**
	 * No-args constructor.
	 * Same as {@code Address(null)} 
	 */
	public Address() {
		this(null);
	}

	/**
	 * Default constructor.
	 * @param id
	 */
	public Address(String id) {
		setId(id);
		setName(getName());
	}

	/**
	 * The Latitude and Longitude points of this address. Must not be null or empty.
	 * @return "lat,lng" as a string 
	 */
	public String getLatlng() {
		return latlng;
	}

	/**
	 * Sets the coordinates in the form "LAT,LNG"
	 * @param latlng the coords
	 */
	public void setLatlng(String latlng) {
		this.latlng = latlng;
	}

	/**
	 * The phone number. Multiple phones separated by comma are allowed.
	 * @return the phone number
	 */
	public String getPhone() {
		return phone;
	}

	/**
	 * Sets a new phone number
	 * @param phone the number as a string
	 */
	public void setPhone(String phone) {
		this.phone = phone;
	}

	/**
	 * The country in which the address is located. Must not be null or empty.
	 * @return the full country name
	 */
	public String getCountry() {
		return country;
	}

	/**
	 * Sets a new country name
	 * @param country the name of the country
	 */
	public void setCountry(String country) {
		this.country = country;
	}

	/**
	 * The street address and other address info
	 * @return the street address
	 */
	public String getAddress() {
		return address;
	}

	/**
	 * Sets new street address. Must not be null or empty.
	 * @param address the address info
	 */
	public void setAddress(String address) {
		this.address = address;
	}
}

