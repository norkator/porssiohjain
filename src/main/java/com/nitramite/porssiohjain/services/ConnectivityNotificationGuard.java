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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class ConnectivityNotificationGuard {

    private final Clock clock;
    private final Duration startupQuietPeriod;
    private final Instant startedAt;

    public ConnectivityNotificationGuard(
            @Value("${app.device-connectivity.startup-notification-quiet-period:10m}")
            Duration startupQuietPeriod
    ) {
        this(startupQuietPeriod, Clock.systemUTC());
    }

    ConnectivityNotificationGuard(Duration startupQuietPeriod, Clock clock) {
        this.clock = clock;
        this.startupQuietPeriod = startupQuietPeriod == null ? Duration.ZERO : startupQuietPeriod;
        this.startedAt = clock.instant();
    }

    public boolean isMuted(Instant detectedAt) {
        if (startupQuietPeriod.isZero() || startupQuietPeriod.isNegative()) {
            return false;
        }
        Instant eventTime = detectedAt == null ? clock.instant() : detectedAt;
        return eventTime.isBefore(startedAt.plus(startupQuietPeriod));
    }
}
