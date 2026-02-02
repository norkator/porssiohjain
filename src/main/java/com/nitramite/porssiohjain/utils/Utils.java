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
