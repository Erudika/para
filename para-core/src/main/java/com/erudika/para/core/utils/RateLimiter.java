/*
 * Copyright 2013-2022 Erudika. https://erudika.com
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
package com.erudika.para.core.utils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;

/**
 * A simple rate limiter implemented using the sliding window counter algorithm.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class RateLimiter {

	private int rateLimitPerMin;
	private int rateLimitPerHour;
	private int rateLimitPerDay;

	private static final String LIMITS_PREFIX = "ratelimits:";
	private static final String LIMITS_H_PREFIX = "ratelimits_h:";
	private static final String LIMITS_D_PREFIX = "ratelimits_d:";

	/**
	 * Default constructor.
	 */
	RateLimiter() {
		this(60, 10);
	}

	/**
	 * Default constructor.
	 * @param rateLimitPerMin rate limit per minute
	 * @param rateLimitPerHour rate limit per hour
	 */
	RateLimiter(int rateLimitPerMin, int rateLimitPerHour) {
		this.rateLimitPerMin = rateLimitPerMin;
		this.rateLimitPerHour = rateLimitPerHour;
		this.rateLimitPerDay =  24 * rateLimitPerHour;
	}

	/**
	 * Default constructor.
	 * @param rateLimitPerMin rate limit per minute
	 * @param rateLimitPerHour rate limit per hour
	 * @param rateLimitPerDay rate limit per day
	 */
	RateLimiter(int rateLimitPerMin, int rateLimitPerHour, int rateLimitPerDay) {
		this.rateLimitPerMin = rateLimitPerMin;
		this.rateLimitPerHour = rateLimitPerHour;
		this.rateLimitPerDay =  rateLimitPerDay;
	}

	/**
	 * Check if some action is allowed to be performed by a given user.
	 * @param appid the appid
	 * @param userId the user identifier
	 * @return true if user is allowed to perform the action
	 */
	public boolean isAllowed(String appid, String userId) {
		return isAllowed(appid, userId, Utils.timestamp());
	}

	/**
	 * Check if some action is allowed to be performed by a given user.
	 * @param appid the appid
	 * @param userId the user identifier
	 * @param reqT the time of the action
	 * @return true if user is allowed to perform the action
	 */
	protected boolean isAllowed(String appid, String userId, Long reqT) {
		if (StringUtils.isBlank(userId)) {
			return false;
		}
		if (reqT == null || reqT <= 0L) {
			reqT = Utils.timestamp();
		}
		if (StringUtils.isBlank(appid)) {
			appid = Para.getConfig().getRootAppIdentifier();
		}

		ConcurrentSkipListMap<Long, Integer> times = Para.getCache().get(appid, limitsKey(userId));
		Long truncatedMin = truncate(reqT, ChronoUnit.MINUTES); // truncate to the beginning of minute

		if (times != null) {
			Long truncatedDay = truncate(reqT, ChronoUnit.DAYS); // truncate to the beginning of the day
			Long lastTruncatedDay = truncate(times.lastKey(), ChronoUnit.DAYS); // truncate to the beginning of the day
			boolean endOfDay = !Objects.equals(truncatedDay, lastTruncatedDay);
			boolean endOfHour = !times.containsKey(truncatedMin);
			if (endOfDay || endOfHour) {
				refreshMinuteSlots(appid, userId, reqT);
				Para.getCache().put(appid, hourlyLimitsKey(userId), 0); // reset counter for hourly limits
				if (endOfDay) {
					Para.getCache().put(appid, dailyLimitsKey(userId), 0); // reset counter for daily limits
				}
			}
		} else {
			refreshMinuteSlots(appid, userId, reqT);
		}

		times = Para.getCache().get(appid, limitsKey(userId));
		int requestCounterHourly = (int) Optional.ofNullable(Para.getCache().get(appid, hourlyLimitsKey(userId))).orElse(0);
		int requestCounterDaily = (int) Optional.ofNullable(Para.getCache().get(appid, dailyLimitsKey(userId))).orElse(0);

		if ((times.getOrDefault(truncatedMin, 0) >= rateLimitPerMin) ||
				(requestCounterHourly >= rateLimitPerHour) ||
				(requestCounterDaily >= rateLimitPerDay)) {
			return false;
		}

		times.put(truncatedMin, times.getOrDefault(truncatedMin, 0) + 1);
		Para.getCache().put(appid, limitsKey(userId), times);
		Para.getCache().put(appid, hourlyLimitsKey(userId), requestCounterHourly + 1);
		Para.getCache().put(appid, dailyLimitsKey(userId), requestCounterDaily + 1);
		return true;
	}

	private Long truncate(Long time, ChronoUnit unit) {
		Instant instant = Instant.ofEpochMilli(time);
		Instant returnValue = instant.truncatedTo(unit);
		return returnValue.toEpochMilli();
	}

	private void refreshMinuteSlots(String appid, String userId, Long reqT) {
		final Long minofDay = truncate(reqT, ChronoUnit.HOURS); // start min of the hour
		ConcurrentSkipListMap<Long, Integer> times = new ConcurrentSkipListMap<>(IntStream.range(0, 60).boxed().
				collect(Collectors.toMap(k -> minofDay + (k * TimeUnit.MINUTES.toMillis(1)), v -> 0)));
		Para.getCache().put(appid, limitsKey(userId), times);
	}

	private String limitsKey(String userId) {
		return StringUtils.startsWith(userId, LIMITS_PREFIX) ? userId : LIMITS_PREFIX.concat(userId);
	}

	private String hourlyLimitsKey(String userId) {
		return StringUtils.startsWith(userId, LIMITS_H_PREFIX) ? userId : LIMITS_H_PREFIX.concat(userId);
	}

	private String dailyLimitsKey(String userId) {
		return StringUtils.startsWith(userId, LIMITS_D_PREFIX) ? userId : LIMITS_D_PREFIX.concat(userId);
	}
}
