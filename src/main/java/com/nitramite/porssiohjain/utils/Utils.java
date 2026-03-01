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
