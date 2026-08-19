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

import com.nitramite.porssiohjain.entity.FingridDataEntity;
import com.nitramite.porssiohjain.entity.repository.FingridDataRepository;
import com.nitramite.porssiohjain.services.models.FingridWindForecastResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import com.nitramite.porssiohjain.services.models.WindForecastChartResponse;

@Service
@RequiredArgsConstructor
@Transactional
public class FingridService {

    private final FingridDataRepository fingridDataRepository;

    public List<FingridWindForecastResponse> getFingridWindForecastData() {
        Instant now = Instant.now();

        List<FingridDataEntity> forecastEntities = fingridDataRepository.findByDatasetIdAndStartTimeAfter(245, now);

        return forecastEntities.stream()
                .map(n -> FingridWindForecastResponse.builder()
                        .startTime(n.getStartTime())
                        .endTime(n.getEndTime())
                        .value(n.getValue())
                        .build())
                .toList();
    }

    public WindForecastChartResponse getWindForecastChart(String timezone) {
        ZoneId zone = ZoneId.of(timezone == null || timezone.isBlank() ? ZoneId.systemDefault().getId() : timezone);
        LocalDate today = LocalDate.now(zone);
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(2).atStartOfDay(zone).toInstant();
        List<FingridDataEntity> rows = fingridDataRepository
                .findByDatasetIdAndStartTimeBetweenOrderByStartTimeAsc(245, start, end);
        BigDecimal todayAverage = average(rows, today, zone);
        BigDecimal tomorrowAverage = average(rows, today.plusDays(1), zone);
        BigDecimal drop = todayAverage != null && tomorrowAverage != null && todayAverage.signum() > 0
                ? todayAverage.subtract(tomorrowAverage).multiply(BigDecimal.valueOf(100))
                    .divide(todayAverage, 2, RoundingMode.HALF_UP)
                : null;
        return WindForecastChartResponse.builder().timezone(zone.getId())
                .todayAverage(todayAverage).tomorrowAverage(tomorrowAverage).tomorrowDropPercent(drop)
                .points(rows.stream().map(row -> WindForecastChartResponse.Point.builder()
                        .startTime(row.getStartTime()).endTime(row.getEndTime()).megawatts(row.getValue()).build()).toList())
                .build();
    }

    private BigDecimal average(List<FingridDataEntity> rows, LocalDate date, ZoneId zone) {
        List<BigDecimal> values = rows.stream().filter(row -> row.getStartTime().atZone(zone).toLocalDate().equals(date))
                .map(FingridDataEntity::getValue).toList();
        return values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

}
