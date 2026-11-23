/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.WaterMeterEntity;
import com.nitramite.porssiohjain.entity.enums.WaterMeterProvider;
import com.nitramite.porssiohjain.entity.repository.WaterMeasurementRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Read-time projections only: never persisted as readings or used by notifications. */
public final class WaterMeterEstimation {
    public static final ZoneId ZONE = ZoneId.of("Europe/Helsinki");

    private WaterMeterEstimation() { }

    public static Result estimate(WaterMeterEntity meter, Long accountId,
                                  WaterMeasurementRepository measurements, Instant now) {
        LocalDate through = meter.getConsumptionThrough();
        if (meter.getProvider() != WaterMeterProvider.TAMPEREEN_VESI || !meter.isEnabled()
                || through == null || meter.getBaselineDate() == null || meter.getLatestTotalCubicMeters() == null
                || meter.getObservedAt() == null || meter.getObservedAt().isAfter(now)) return null;
        Instant start = through.plusDays(1).atStartOfDay(ZONE).toInstant();
        LocalDate today = now.atZone(ZONE).toLocalDate();
        // Cover normal reporting delays without indefinitely projecting an abandoned source.
        if (!start.isBefore(now) || ChronoUnit.DAYS.between(through, today) > 7) return null;
        var rows = measurements.findByWaterMeterIdAndWaterMeterAccountIdAndConsumptionDateBetweenOrderByConsumptionDateAsc(
                meter.getId(), accountId, through.minusDays(6), today);
        Map<LocalDate, BigDecimal> daily = new TreeMap<>();
        for (var row : rows) {
            LocalDate date = row.getConsumptionDate();
            if (date == null || date.isBefore(meter.getBaselineDate())) continue;
            // A known day beyond an internal gap must not be replaced by an average.
            if (date.isAfter(through)) return null;
            if (row.getConsumptionCubicMeters() != null) daily.put(date, row.getConsumptionCubicMeters());
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (LocalDate day = through; count < 7; day = day.minusDays(1)) {
            BigDecimal value = daily.get(day);
            if (value == null || value.signum() < 0) break;
            sum = sum.add(value);
            count++;
        }
        if (count < 2) return null;
        BigDecimal average = sum.divide(BigDecimal.valueOf(count), 12, RoundingMode.HALF_UP);
        List<Point> totals = new ArrayList<>();
        List<Point> consumption = new ArrayList<>();
        BigDecimal total = meter.getLatestTotalCubicMeters();
        totals.add(new Point(start, total, through));
        consumption.add(new Point(start, daily.get(through), through));
        for (LocalDate day = through.plusDays(1); !day.isAfter(today); day = day.plusDays(1)) {
            Instant dayStart = day.atStartOfDay(ZONE).toInstant();
            if (!dayStart.isBefore(now)) break;
            Instant dayEnd = day.plusDays(1).atStartOfDay(ZONE).toInstant();
            Instant end = dayEnd.isBefore(now) ? dayEnd : now;
            // Each calendar day gets the same mean usage, including 23/25-hour days.
            BigDecimal fraction = BigDecimal.valueOf(Duration.between(dayStart, end).toMillis())
                    .divide(BigDecimal.valueOf(Duration.between(dayStart, dayEnd).toMillis()), 12, RoundingMode.HALF_UP);
            BigDecimal usage = average.multiply(fraction);
            total = total.add(usage);
            totals.add(new Point(end, rounded(total), day));
            consumption.add(new Point(end, rounded(usage), day));
        }
        return new Result(new Estimate(rounded(total), now, rounded(average), count),
                new Estimate(consumption.getLast().value(), now, rounded(average), count),
                List.copyOf(totals), List.copyOf(consumption));
    }

    private static BigDecimal rounded(BigDecimal value) { return value.setScale(6, RoundingMode.HALF_UP); }

    public record Estimate(BigDecimal value, Instant at, BigDecimal averageDailyCubicMeters, int basedOnDays) { }
    public record Point(Instant at, BigDecimal value, LocalDate consumptionDate) { }
    public record Result(Estimate total, Estimate daily, List<Point> totalPoints, List<Point> dailyPoints) { }
}
