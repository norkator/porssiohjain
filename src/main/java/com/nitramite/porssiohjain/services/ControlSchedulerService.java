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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
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

        return controlTableRepository.findByControlIdAndStartTimeAfterOrderByStartTimeAsc(controlId,
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
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new IllegalArgumentException("Control not found: " + controlId));
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant endOfDay = startOfDay.plus(2, ChronoUnit.DAYS);
        generateInternal(List.of(control), startOfDay, endOfDay);
    }

    @Transactional
    public void generatePlannedForTomorrow() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant endOfDay = startOfDay.plus(2, ChronoUnit.DAYS);
        List<ControlEntity> controls = controlRepository.findAll();
        log.info("generatePlannedForTomorrow for time {} - {}", startOfDay, endOfDay.toString());
        generateInternal(controls, startOfDay, endOfDay);
        systemLogService.log("Scheduled run of function 'generatePlannedForTomorrow' completed.");
    }

    private void generateInternal(
            List<ControlEntity> controls,
            Instant startTime,
            Instant endTime
    ) {
        List<NordpoolEntity> prices = nordpoolRepository.findByDeliveryStartBetween(startTime, endTime);

        for (ControlEntity control : controls) {
            ControlMode controlMode = control.getMode();

            controlTableRepository.deleteByControlAndStartTimeBetween(control, startTime, endTime);
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
            } else if (controlMode.equals(ControlMode.CHEAPEST_HOURS)) {
                Integer dailyOnMinutes = control.getDailyOnMinutes();
                BigDecimal maxPriceSnt = control.getMaxPriceSnt();
                BigDecimal minPriceSnt = control.getMinPriceSnt();
                ZoneId controlZone = ZoneId.of(control.getTimezone());
                boolean alwaysOnBelowMinPrice = control.isAlwaysOnBelowMinPrice();
                Map<Instant, BigDecimal> combinedPriceByPeriod = new HashMap<>();
                for (NordpoolEntity p : prices) {
                    BigDecimal combinedPrice = controlPriceService.getCombinedPrice(control, p);
                    combinedPriceByPeriod.put(p.getDeliveryStart(), combinedPrice);
                }
                Map<LocalDate, List<NordpoolEntity>> pricesByDay = prices.stream().collect(Collectors.groupingBy(p -> p.getDeliveryStart().atZone(controlZone).toLocalDate()));
                for (Map.Entry<LocalDate, List<NordpoolEntity>> dayEntry : pricesByDay.entrySet()) {
                    List<NordpoolEntity> dailyPrices = dayEntry.getValue();
                    int accumulatedMinutes = 0;
                    List<NordpoolEntity> alwaysOnPeriods = Collections.emptyList();
                    if (alwaysOnBelowMinPrice) {
                        alwaysOnPeriods = dailyPrices.stream()
                                .filter(p -> combinedPriceByPeriod.get(p.getDeliveryStart()).compareTo(minPriceSnt) <= 0)
                                .sorted(Comparator.comparing(p -> combinedPriceByPeriod.get(p.getDeliveryStart())))
                                .toList();
                        for (NordpoolEntity priceEntry : alwaysOnPeriods) {
                            BigDecimal combinedPrice = combinedPriceByPeriod.get(priceEntry.getDeliveryStart());
                            int periodMinutes = (int) Duration.between(priceEntry.getDeliveryStart(), priceEntry.getDeliveryEnd()).toMinutes();
                            int minutesToUse = (periodMinutes / 15) * 15;
                            if (minutesToUse <= 0) continue;
                            Instant end = priceEntry.getDeliveryStart().plus(Duration.ofMinutes(minutesToUse));
                            controlTableRepository.save(ControlTableEntity.builder().control(control).startTime(priceEntry.getDeliveryStart()).endTime(end).priceSnt(combinedPrice).status(Status.FINAL).build());
                            accumulatedMinutes += minutesToUse;
                        }
                    }
                    if (accumulatedMinutes >= dailyOnMinutes) continue;
                    Set<NordpoolEntity> alwaysOnSet = new HashSet<>(alwaysOnPeriods);
                    List<NordpoolEntity> eligiblePrices = dailyPrices.stream()
                            .filter(p -> !alwaysOnSet.contains(p))
                            .filter(p -> combinedPriceByPeriod.get(p.getDeliveryStart()).compareTo(maxPriceSnt) <= 0)
                            .sorted(Comparator.comparing(p -> combinedPriceByPeriod.get(p.getDeliveryStart())))
                            .toList();
                    for (NordpoolEntity priceEntry : eligiblePrices) {
                        if (accumulatedMinutes >= dailyOnMinutes) break;
                        BigDecimal combinedPrice = combinedPriceByPeriod.get(priceEntry.getDeliveryStart());
                        int periodMinutes = (int) Duration.between(priceEntry.getDeliveryStart(), priceEntry.getDeliveryEnd()).toMinutes();
                        int minutesLeft = dailyOnMinutes - accumulatedMinutes;
                        int minutesToUse = Math.min(periodMinutes, minutesLeft);
                        minutesToUse = (minutesToUse / 15) * 15;
                        if (minutesToUse <= 0) continue;
                        Instant end = priceEntry.getDeliveryStart().plus(Duration.ofMinutes(minutesToUse));
                        controlTableRepository.save(ControlTableEntity.builder().control(control).startTime(priceEntry.getDeliveryStart()).endTime(end).priceSnt(combinedPrice).status(Status.FINAL).build());
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

}
