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

package com.nitramite.porssiohjain.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class Utils {

    public static Instant toInterval(
            Instant instant, ZoneId zoneId, int intervalMinutes
    ) {
        ZonedDateTime zdt = instant.atZone(zoneId);
        int minute = zdt.getMinute();
        int bucket = (minute / intervalMinutes) * intervalMinutes;
        return zdt
                .withMinute(bucket)
                .withSecond(0)
                .withNano(0)
                .toInstant();
    }

    public static Instant toQuarterHour(
            Instant instant, ZoneId zoneId
    ) {
        ZonedDateTime zdt = instant.atZone(zoneId);
        int minute = zdt.getMinute();
        int quarter = (minute / 15) * 15;
        return zdt
                .withMinute(quarter)
                .withSecond(0)
                .withNano(0)
                .toInstant();
    }

}
