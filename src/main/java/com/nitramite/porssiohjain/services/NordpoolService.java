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

import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.NordpoolPriceResponse;
import com.nitramite.porssiohjain.services.models.TodayPriceChartPointResponse;
import com.nitramite.porssiohjain.services.models.TodayPriceChartResponse;
import com.nitramite.porssiohjain.services.models.TodayPriceStatsResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class NordpoolService {

    private static final int TODAY_CHART_RESOLUTION_MINUTES = 15;
    private final NordpoolRepository nordpoolRepository;
    private final ControlRepository controlRepository;

    public List<NordpoolPriceResponse> getNordpoolPricesForControl(
            Long controlId, Instant startDate, Instant endDate
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found: " + controlId));

        Instant now = Instant.now();
        Instant start = startDate != null ? startDate : now.truncatedTo(ChronoUnit.DAYS);
        Instant end = endDate != null ? endDate : start.plus(2, ChronoUnit.DAYS).minusNanos(1);

        List<NordpoolEntity> prices = nordpoolRepository.findPricesBetween(start, end);

        BigDecimal taxMultiplier = BigDecimal.ONE.add(control.getTaxPercent().divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP));

        return prices.stream()
                .map(n -> NordpoolPriceResponse.builder()
                        .deliveryStart(n.getDeliveryStart())
                        .deliveryEnd(n.getDeliveryEnd())
                        .priceFi(n.getPriceFi().multiply(BigDecimal.valueOf(0.1)))
                        .priceFiWithTax(n.getPriceFi().multiply(BigDecimal.valueOf(0.1)).multiply(taxMultiplier))
                        .build())
                .toList();
    }

    public TodayPriceStatsResponse getTodayStats(Long accountId, String timezone) {
        PricingContext pricingContext = resolvePricingContext(accountId, timezone);
        ZoneId zone = pricingContext.zone();
        LocalDate today = LocalDate.now(zone);
        ZonedDateTime startOfDay = today.atStartOfDay(zone);
        ZonedDateTime endOfDay = today.plusDays(1).atStartOfDay(zone);

        List<NordpoolEntity> prices = nordpoolRepository.findPricesBetween(
                startOfDay.toInstant(), endOfDay.toInstant()
        );

        if (prices.isEmpty()) {
            return TodayPriceStatsResponse.builder()
                    .min(BigDecimal.ZERO)
                    .avg(BigDecimal.ZERO)
                    .max(BigDecimal.ZERO)
                    .build();
        }

        List<BigDecimal> values = prices.stream()
                .map(p -> toPriceWithTax(p.getPriceFi(), pricingContext.taxMultiplier()))
                .toList();

        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal avg = values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), RoundingMode.HALF_UP);

        return TodayPriceStatsResponse.builder()
                .min(min)
                .avg(avg)
                .max(max)
                .build();
    }

    public TodayPriceChartResponse getTodayChart(Long accountId, String timezone) {
        PricingContext pricingContext = resolvePricingContext(accountId, timezone);
        ZoneId zone = pricingContext.zone();
        LocalDate today = LocalDate.now(zone);
        ZonedDateTime startOfDay = today.atStartOfDay(zone);
        ZonedDateTime endOfDay = today.plusDays(1).atStartOfDay(zone);
        Instant start = startOfDay.toInstant();
        Instant end = endOfDay.toInstant();

        List<NordpoolEntity> prices = nordpoolRepository.findPricesBetween(start, end);
        Map<Instant, BigDecimal> pointsByTimestamp = new LinkedHashMap<>();

        for (NordpoolEntity priceEntry : prices) {
            Instant slot = priceEntry.getDeliveryStart().isBefore(start)
                    ? start
                    : priceEntry.getDeliveryStart();
            Instant slotEnd = priceEntry.getDeliveryEnd().isAfter(end)
                    ? end
                    : priceEntry.getDeliveryEnd();

            if (!slot.isBefore(slotEnd)) {
                continue;
            }

            BigDecimal price = toPriceWithTax(priceEntry.getPriceFi(), pricingContext.taxMultiplier());
            while (slot.isBefore(slotEnd)) {
                pointsByTimestamp.putIfAbsent(slot, price);
                slot = slot.plus(TODAY_CHART_RESOLUTION_MINUTES, ChronoUnit.MINUTES);
            }
        }

        List<TodayPriceChartPointResponse> points = pointsByTimestamp.entrySet().stream()
                .map(entry -> TodayPriceChartPointResponse.builder()
                        .timestamp(entry.getKey())
                        .price(entry.getValue())
                        .build())
                .toList();

        List<BigDecimal> values = points.stream()
                .map(TodayPriceChartPointResponse::getPrice)
                .toList();

        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal avg = values.isEmpty()
                ? BigDecimal.ZERO
                : values.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);

        Instant now = Instant.now();
        BigDecimal current = prices.stream()
                .filter(priceEntry -> !priceEntry.getDeliveryStart().isAfter(now) && priceEntry.getDeliveryEnd().isAfter(now))
                .findFirst()
                .map(priceEntry -> toPriceWithTax(priceEntry.getPriceFi(), pricingContext.taxMultiplier()))
                .orElseGet(() -> points.isEmpty() ? BigDecimal.ZERO : points.get(points.size() - 1).getPrice());

        return TodayPriceChartResponse.builder()
                .date(today)
                .timezone(zone.getId())
                .resolutionMinutes(TODAY_CHART_RESOLUTION_MINUTES)
                .min(min)
                .avg(avg)
                .max(max)
                .current(current)
                .points(points)
                .build();
    }

    private PricingContext resolvePricingContext(Long accountId, String timezone) {
        ZoneId zone = ZoneId.systemDefault();
        BigDecimal taxMultiplier;

        Optional<ControlEntity> optionalControl = controlRepository.findFirstByAccountId(accountId);
        if (optionalControl.isPresent()) {
            ControlEntity control = optionalControl.get();
            try {
                if (control.getTimezone() != null && !control.getTimezone().isBlank()) {
                    zone = ZoneId.of(control.getTimezone());
                } else if (timezone != null && !timezone.isBlank()) {
                    zone = ZoneId.of(timezone);
                }
            } catch (Exception ignored) {
            }

            if (control.getTaxPercent() != null) {
                taxMultiplier = BigDecimal.ONE.add(
                        control.getTaxPercent().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                );
            } else {
                taxMultiplier = BigDecimal.ONE;
            }
        } else {
            taxMultiplier = BigDecimal.ONE
                    .add(BigDecimal.valueOf(25.5)
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            try {
                if (timezone != null && !timezone.isBlank()) {
                    zone = ZoneId.of(timezone);
                }
            } catch (Exception ignored) {
            }
        }

        return new PricingContext(zone, taxMultiplier);
    }

    private BigDecimal toPriceWithTax(BigDecimal priceFi, BigDecimal taxMultiplier) {
        return priceFi.multiply(BigDecimal.valueOf(0.1)).multiply(taxMultiplier);
    }

    private record PricingContext(ZoneId zone, BigDecimal taxMultiplier) {
    }

}
