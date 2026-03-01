/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nitramite.porssiohjain.services;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitService {

    private static class Attempt {
        int count;
        Instant firstAttempt;
    }

    private final Map<String, Attempt> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, Attempt> accountAttempts = new ConcurrentHashMap<>();

    private static final int LOGIN_LIMIT = 10;
    private static final int CREATE_LIMIT = 2;
    private static final long WINDOW_MS = 60 * 60 * 1000L; // 1 hour

    private boolean isAllowed(Map<String, Attempt> map, String key, int limit) {
        Attempt attempt = map.computeIfAbsent(key, k -> {
            Attempt a = new Attempt();
            a.firstAttempt = Instant.now();
            a.count = 0;
            return a;
        });

        synchronized (attempt) {
            if (Instant.now().isAfter(attempt.firstAttempt.plusMillis(WINDOW_MS))) {
                // reset after window expires
                attempt.firstAttempt = Instant.now();
                attempt.count = 0;
            }

            if (attempt.count >= limit) {
                return false;
            }

            attempt.count++;
            return true;
        }
    }

    public boolean allowLogin(String ip) {
        return isAllowed(loginAttempts, ip, LOGIN_LIMIT);
    }

    public boolean allowAccountCreation(String ip) {
        return isAllowed(accountAttempts, ip, CREATE_LIMIT);
    }
}
