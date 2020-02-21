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
 * This class stores pagination data. It limits the results for queries in the {@link com.erudika.para.persistence.DAO}
 * and {@link com.erudika.para.search.Search} objects and also counts the total number of results that are returned.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class Pager {

	private long page;
	private long count;
	private String sortby;
	private boolean desc;
	private int limit;
	private String name;
	private String lastKey;

	/**
	 * No-args constructor.
	 */
	public Pager() {
		this(1, null, true, Config.MAX_ITEMS_PER_PAGE);
	}

	/**
	 * Default constructor with limit.
	 * @param limit the results limit
	 */
	public Pager(int limit) {
		this(1, null, true, limit);
	}

	/**
	 * Default constructor with a page and count.
	 * @param page the page number
	 * @param limit the results limit
	 */
	public Pager(long page, int limit) {
		this(page, null, true, limit);
	}

	/**
	 * Default constructor with a page, count, sortby, desc and limit.
	 * @param page the page number
	 * @param sortby name of property to sort by
	 * @param desc sort order
	 * @param limit the results limit
	 */
	public Pager(long page, String sortby, boolean desc, int limit) {
		this.page = page;
		this.count = 0;
		this.sortby = sortby;
		this.desc = desc;
		this.limit = limit;
	}

	/**
	 * Returns the last key from last page. Used for scanning and pagination.
	 * @return the last key to continue from
	 */
	public String getLastKey() {
		return lastKey;
	}

	/**
	 * Sets the last key from last page. Used for scanning and pagination.
	 * @param lastKey last id
	 */
	public void setLastKey(String lastKey) {
		this.lastKey = lastKey;
	}

	/**
	 * Name of this pager object (optional). Used to distinguish between multiple pagers.
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Set the value of name.
	 * @param name the name (optional)
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * The name of the field used when sorting the results.
	 * @return the name of the field or "timestamp" as the default sorting
	 */
	public String getSortby() {
		return (sortby == null) ? Config._TIMESTAMP : sortby;
	}

	/**
	 * Sets the value of sortby.
	 * @param sortby the sort field
	 */
	public void setSortby(String sortby) {
		this.sortby = sortby;
	}

	/**
	 * The sort order. Default: descending (true)
	 * @return true if descending
	 */
	public boolean isDesc() {
		return desc;
	}

	/**
	 * Sets the value of desc.
	 * @param desc true if descending order
	 */
	public void setDesc(boolean desc) {
		this.desc = desc;
	}

	/**
	 * Limits the maximum number of results to return in one page.
	 * @return the max number of results in one page
	 */
	public int getLimit() {
		limit = Math.abs(limit);
		return limit;
	}

	/**
	 * Set the value of limit.
	 * @param limit the max number of results in one page
	 */
	public void setLimit(int limit) {
		this.limit = limit;
	}

	/**
	 * The total number of results for a query.
	 * @return total count of results
	 */
	public long getCount() {
		return count;
	}

	/**
	 * Set the value of count.
	 * @param count total count
	 */
	public void setCount(long count) {
		this.count = count;
	}

	/**
	 * Page number. Usually starts from 1...
	 * @return the page number
	 */
	public long getPage() {
		page = Math.abs(page);
		return page;
	}

	/**
	 * Set the value of page.
	 * @param page the page number
	 */
	public void setPage(long page) {
		this.page = page;
	}

	@Override
	public String toString() {
		return "Pager{" + "page=" + page + ", count=" + count + ", sortby=" + sortby + ", desc=" + desc +
				", limit=" + limit + ", name=" + name + ", lastKey=" + lastKey + '}';
	}
}
