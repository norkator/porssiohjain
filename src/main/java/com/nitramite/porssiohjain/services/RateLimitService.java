/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
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

    private Attempt getAttempt(Map<String, Attempt> map, String key) {
        return map.computeIfAbsent(key, k -> {
            Attempt a = new Attempt();
            a.firstAttempt = Instant.now();
            a.count = 0;
            return a;
        });
    }

    private void resetIfWindowExpired(Attempt attempt) {
        if (Instant.now().isAfter(attempt.firstAttempt.plusMillis(WINDOW_MS))) {
            attempt.firstAttempt = Instant.now();
            attempt.count = 0;
        }
    }

    private boolean isAllowed(Map<String, Attempt> map, String key, int limit) {
        Attempt attempt = getAttempt(map, key);

        synchronized (attempt) {
            resetIfWindowExpired(attempt);

            if (attempt.count >= limit) {
                return false;
            }

            attempt.count++;
            return true;
        }
    }

    public boolean isLoginAllowed(String ip) {
        Attempt attempt = loginAttempts.get(ip);
        if (attempt == null) {
            return true;
        }

        synchronized (attempt) {
            resetIfWindowExpired(attempt);
            return attempt.count < LOGIN_LIMIT;
        }
    }

    public void recordFailedLogin(String ip) {
        Attempt attempt = getAttempt(loginAttempts, ip);
        synchronized (attempt) {
            resetIfWindowExpired(attempt);
            attempt.count++;
        }
    }

    public void resetLoginFailures(String ip) {
        Attempt attempt = loginAttempts.get(ip);
        if (attempt == null) {
            return;
        }

        synchronized (attempt) {
            attempt.firstAttempt = Instant.now();
            attempt.count = 0;
        }
    }

    public boolean allowAccountCreation(String ip) {
        return isAllowed(accountAttempts, ip, CREATE_LIMIT);
    }
}
