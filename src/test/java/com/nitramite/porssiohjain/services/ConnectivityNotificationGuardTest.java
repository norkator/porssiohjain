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

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectivityNotificationGuardTest {

    private static final Instant STARTED_AT = Instant.parse("2026-06-09T10:00:00Z");

    @Test
    void mutesEventsInsideStartupQuietPeriod() {
        ConnectivityNotificationGuard guard = new ConnectivityNotificationGuard(
                Duration.ofMinutes(10),
                Clock.fixed(STARTED_AT, ZoneOffset.UTC)
        );

        assertTrue(guard.isMuted(STARTED_AT.plusSeconds(30)));
        assertFalse(guard.isMuted(STARTED_AT.plus(Duration.ofMinutes(10))));
    }

    @Test
    void doesNotMuteWhenQuietPeriodIsDisabled() {
        ConnectivityNotificationGuard guard = new ConnectivityNotificationGuard(
                Duration.ZERO,
                Clock.fixed(STARTED_AT, ZoneOffset.UTC)
        );

        assertFalse(guard.isMuted(STARTED_AT.plusSeconds(30)));
    }
}
