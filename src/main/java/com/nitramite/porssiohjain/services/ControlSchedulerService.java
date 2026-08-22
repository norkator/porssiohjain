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

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.ControlMode;
import com.nitramite.porssiohjain.entity.enums.Status;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.services.models.ControlTableResponse;
import com.nitramite.porssiohjain.services.nordpool.NordpoolMarket;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ControlSchedulerService {

    private final NordpoolRepository nordpoolRepository;
    private final ControlRepository controlRepository;
    private final ControlTableRepository controlTableRepository;
    private final SystemLogService systemLogService;
    private final ControlPriceService controlPriceService;

    public List<ControlTableResponse> findByControlId(
            Long controlId
    ) {
        Optional<ControlEntity> controlEntity = controlRepository.findById(controlId);
        ZoneId controlZone = ZoneId.of(controlEntity.get().getTimezone());

        ZonedDateTime startOfDayLocal = LocalDate.now(controlZone).atStartOfDay(controlZone);
        Instant cutoffUtc = startOfDayLocal.toInstant();

        return controlTableRepository.findByControlIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(controlId,
                        cutoffUtc).stream()
                .map(this::toResponse)
                .toList();
    }

    private ControlTableResponse toResponse(ControlTableEntity e) {
        return ControlTableResponse.builder()
                .id(e.getId())
                .controlId(e.getControl().getId())
                .priceSnt(e.getPriceSnt())
                .status(e.getStatus())
                .startTime(e.getStartTime())
                .endTime(e.getEndTime())
                .build();
    }

    @Transactional
    public void generateForControl(
            Long controlId
    ) {
        generateForControl(controlId, Instant.now());
    }

    void generateForControl(
            Long controlId,
            Instant now
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new IllegalArgumentException("Control not found: " + controlId));
        generateInternal(List.of(control), now);
    }

    @Transactional
    public void generatePlannedForTomorrow() {
        Instant now = Instant.now();
        List<ControlEntity> controls = controlRepository.findAll();
        log.info("generatePlannedForTomorrow at {}", now);
        generateInternal(controls, now);
        systemLogService.log("Scheduled run of function 'generatePlannedForTomorrow' completed.");
    }

    private void generateInternal(
            List<ControlEntity> controls,
            Instant now
    ) {
        for (ControlEntity control : controls) {
            ZoneId controlZone = ZoneId.of(control.getTimezone());
            LocalDate today = now.atZone(controlZone).toLocalDate();
            Instant startTime = today.atStartOfDay(controlZone).toInstant();
            Instant endTime = today.plusDays(2).atStartOfDay(controlZone).toInstant();
            List<NordpoolEntity> prices = nordpoolRepository
                    .findByMarketIndexNameAndDeliveryStartGreaterThanEqualAndDeliveryStartLessThan(
                            NordpoolMarket.normalize(control.getAccount().getMarketIndexName()),
                            startTime,
                            endTime
                    );
            ControlMode controlMode = control.getMode();

            controlTableRepository.deleteByControlAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                    control,
                    startTime,
                    endTime
            );
            controlTableRepository.flush();

            if (controlMode.equals(ControlMode.BELOW_MAX_PRICE)) {
                for (NordpoolEntity priceEntry : prices) {
                    BigDecimal combinedPrice = controlPriceService.getCombinedPrice(control, priceEntry);
                    if (combinedPrice.compareTo(control.getMaxPriceSnt()) <= 0) {
                        ControlTableEntity entry = ControlTableEntity.builder()
                                .control(control)
                                .startTime(priceEntry.getDeliveryStart())
                                .endTime(priceEntry.getDeliveryEnd())
                                .priceSnt(combinedPrice)
                                .status(Status.FINAL)
                                .build();
                        controlTableRepository.save(entry);
                    }
                }
            } else if (controlMode.isCheapestHours()) {
                Integer dailyOnMinutes = control.getDailyOnMinutes();
                BigDecimal maxPriceSnt = control.getMaxPriceSnt();
                BigDecimal minPriceSnt = control.getMinPriceSnt();
                boolean alwaysOnBelowMinPrice = control.isAlwaysOnBelowMinPrice();
                Map<Instant, BigDecimal> combinedPriceByPeriod = new HashMap<>();
                for (NordpoolEntity p : prices) {
                    BigDecimal combinedPrice = controlPriceService.getCombinedPrice(control, p);
                    combinedPriceByPeriod.put(p.getDeliveryStart(), combinedPrice);
                }
                Map<LocalDate, List<NordpoolEntity>> pricesByDay = prices.stream().collect(Collectors.groupingBy(
                        p -> p.getDeliveryStart().atZone(controlZone).toLocalDate()
                ));
                int tomorrowSurplusMinutes = calculateTomorrowSurplusMinutes(
                        controlMode,
                        alwaysOnBelowMinPrice,
                        pricesByDay.getOrDefault(today.plusDays(1), List.of()),
                        combinedPriceByPeriod,
                        minPriceSnt,
                        dailyOnMinutes,
                        today.plusDays(1),
                        controlZone
                );
                for (LocalDate date : List.of(today, today.plusDays(1))) {
                    List<NordpoolEntity> dailyPrices = pricesByDay.getOrDefault(date, List.of());
                    int accumulatedMinutes = 0;
                    List<NordpoolEntity> alwaysOnPeriods = Collections.emptyList();
                    if (alwaysOnBelowMinPrice) {
                        alwaysOnPeriods = dailyPrices.stream()
                                .filter(p -> combinedPriceByPeriod.get(p.getDeliveryStart()).compareTo(minPriceSnt) <= 0)
                                .sorted(Comparator.comparing(p -> combinedPriceByPeriod.get(p.getDeliveryStart())))
                                .toList();
                        for (NordpoolEntity priceEntry : alwaysOnPeriods) {
                            BigDecimal combinedPrice = combinedPriceByPeriod.get(priceEntry.getDeliveryStart());
                            int minutesToUse = usablePeriodMinutes(priceEntry);
                            if (minutesToUse <= 0) continue;
                            Instant end = priceEntry.getDeliveryStart().plus(Duration.ofMinutes(minutesToUse));
                            controlTableRepository.save(ControlTableEntity.builder()
                                    .control(control)
                                    .startTime(priceEntry.getDeliveryStart())
                                    .endTime(end)
                                    .priceSnt(combinedPrice)
                                    .status(Status.FINAL)
                                    .build());
                            accumulatedMinutes += minutesToUse;
                        }
                    }
                    int requiredMinutes = date.equals(today)
                            ? Math.max(0, dailyOnMinutes - tomorrowSurplusMinutes)
                            : dailyOnMinutes;
                    if (accumulatedMinutes >= requiredMinutes) continue;
                    Set<NordpoolEntity> alwaysOnSet = new HashSet<>(alwaysOnPeriods);
                    List<NordpoolEntity> eligiblePrices = dailyPrices.stream()
                            .filter(p -> !alwaysOnSet.contains(p))
                            .filter(p -> combinedPriceByPeriod.get(p.getDeliveryStart()).compareTo(maxPriceSnt) <= 0)
                            .sorted(Comparator.comparing(p -> combinedPriceByPeriod.get(p.getDeliveryStart())))
                            .toList();
                    for (NordpoolEntity priceEntry : eligiblePrices) {
                        if (accumulatedMinutes >= requiredMinutes) break;
                        BigDecimal combinedPrice = combinedPriceByPeriod.get(priceEntry.getDeliveryStart());
                        int minutesLeft = requiredMinutes - accumulatedMinutes;
                        int minutesToUse = Math.min(usablePeriodMinutes(priceEntry), minutesLeft);
                        minutesToUse = Math.floorDiv(minutesToUse, 15) * 15;
                        if (minutesToUse <= 0) continue;
                        Instant end = priceEntry.getDeliveryStart().plus(Duration.ofMinutes(minutesToUse));
                        controlTableRepository.save(ControlTableEntity.builder()
                                .control(control)
                                .startTime(priceEntry.getDeliveryStart())
                                .endTime(end)
                                .priceSnt(combinedPrice)
                                .status(Status.FINAL)
                                .build());
                        accumulatedMinutes += minutesToUse;
                    }
                }
            } else if (controlMode.equals(ControlMode.MANUAL)) {
                for (NordpoolEntity priceEntry : prices) {
                    BigDecimal priceSnt = controlPriceService.getCombinedPrice(control, priceEntry);
                    ControlTableEntity entry = ControlTableEntity.builder()
                            .control(control)
                            .startTime(priceEntry.getDeliveryStart())
                            .endTime(priceEntry.getDeliveryEnd())
                            .priceSnt(priceSnt)
                            .status(Status.FINAL)
                            .build();
                    controlTableRepository.save(entry);
                }
            }

        }
    }

    private int calculateTomorrowSurplusMinutes(
            ControlMode controlMode,
            boolean alwaysOnBelowMinPrice,
            List<NordpoolEntity> tomorrowPrices,
            Map<Instant, BigDecimal> combinedPriceByPeriod,
            BigDecimal minPriceSnt,
            int dailyOnMinutes,
            LocalDate tomorrow,
            ZoneId controlZone
    ) {
        if (controlMode != ControlMode.CHEAPEST_HOURS_TOMORROW_AWARE
                || !alwaysOnBelowMinPrice
                || !coversCompleteLocalDay(tomorrowPrices, tomorrow, controlZone)) {
            return 0;
        }

        int guaranteedMinutes = tomorrowPrices.stream()
                .filter(p -> combinedPriceByPeriod.get(p.getDeliveryStart()).compareTo(minPriceSnt) <= 0)
                .mapToInt(this::usablePeriodMinutes)
                .sum();
        return Math.max(0, guaranteedMinutes - dailyOnMinutes);
    }

    private boolean coversCompleteLocalDay(
            List<NordpoolEntity> prices,
            LocalDate date,
            ZoneId zone
    ) {
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        Instant coveredUntil = dayStart;

        for (NordpoolEntity price : prices.stream()
                .sorted(Comparator.comparing(NordpoolEntity::getDeliveryStart))
                .toList()) {
            if (!price.getDeliveryEnd().isAfter(coveredUntil)) continue;
            if (price.getDeliveryStart().isAfter(coveredUntil)) return false;
            coveredUntil = price.getDeliveryEnd();
            if (!coveredUntil.isBefore(dayEnd)) return true;
        }
        return false;
    }

    private int usablePeriodMinutes(NordpoolEntity priceEntry) {
        int periodMinutes = (int) Duration.between(
                priceEntry.getDeliveryStart(),
                priceEntry.getDeliveryEnd()
        ).toMinutes();
        return (periodMinutes / 15) * 15;
    }

}
