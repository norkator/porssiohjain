/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.services.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/** Repeating local-clock targets. Intervals include their start and exclude their end. */
public record HeatingComfortSchedule(BigDecimal defaultTarget, List<Period> periods) {
    public HeatingComfortSchedule {
        validateTarget(defaultTarget);
        periods = List.copyOf(periods);
        boolean[] occupied = new boolean[24 * 60];
        for (Period period : periods) {
            validateTarget(period.target());
            if (period.from() == null || period.to() == null || period.from().equals(period.to())
                    || period.from().getSecond() != 0 || period.to().getSecond() != 0
                    || period.from().getNano() != 0 || period.to().getNano() != 0) {
                throw new IllegalArgumentException("heatingPlanner.comfort.invalidTimes");
            }
            for (int minute = 0; minute < occupied.length; minute++) {
                if (period.contains(LocalTime.of(minute / 60, minute % 60))) {
                    if (occupied[minute]) throw new IllegalArgumentException("heatingPlanner.comfort.overlap");
                    occupied[minute] = true;
                }
            }
        }
    }

    private static void validateTarget(BigDecimal target) {
        if (target == null || target.compareTo(BigDecimal.valueOf(5)) < 0
                || target.compareTo(BigDecimal.valueOf(35)) > 0 || target.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("heatingPlanner.comfort.invalidTemperature");
        }
    }

    public BigDecimal targetAt(Instant time, ZoneId zone) {
        LocalTime local = time.atZone(zone).toLocalTime();
        return periods.stream().filter(period -> period.contains(local)).map(Period::target)
                .findFirst().orElse(defaultTarget);
    }

    public BigDecimal lowestTarget() {
        return periods.stream().map(Period::target).reduce(defaultTarget, BigDecimal::min);
    }

    public record Period(LocalTime from, LocalTime to, BigDecimal target) {
        public boolean contains(LocalTime time) {
            return from.isBefore(to) ? !time.isBefore(from) && time.isBefore(to)
                    : !time.isBefore(from) || time.isBefore(to);
        }
    }
}
