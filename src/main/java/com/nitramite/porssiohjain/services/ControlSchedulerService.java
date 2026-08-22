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
                List<NordpoolEntity> tomorrowPrices = pricesByDay.getOrDefault(today.plusDays(1), List.of());
                boolean canRedistributeAcrossDays = controlMode == ControlMode.CHEAPEST_HOURS_TOMORROW_AWARE
                        && coversCompleteLocalDay(tomorrowPrices, today.plusDays(1), controlZone);

                if (canRedistributeAcrossDays) {
                    scheduleTomorrowAwarePeriods(
                            control,
                            pricesByDay.getOrDefault(today, List.of()),
                            tomorrowPrices,
                            dailyOnMinutes,
                            maxPriceSnt,
                            minPriceSnt,
                            alwaysOnBelowMinPrice,
                            combinedPriceByPeriod,
                            now
                    );
                } else {
                    for (LocalDate date : List.of(today, today.plusDays(1))) {
                        scheduleCheapestPeriods(
                                control,
                                pricesByDay.getOrDefault(date, List.of()),
                                dailyOnMinutes,
                                maxPriceSnt,
                                minPriceSnt,
                                alwaysOnBelowMinPrice,
                                combinedPriceByPeriod
                        );
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

    private void scheduleCheapestPeriods(
            ControlEntity control,
            List<NordpoolEntity> prices,
            int requiredMinutes,
            BigDecimal maxPriceSnt,
            BigDecimal minPriceSnt,
            boolean alwaysOnBelowMinPrice,
            Map<Instant, BigDecimal> combinedPriceByPeriod
    ) {
        Map<NordpoolEntity, Integer> selectedMinutes = selectCheapestBasePeriods(
                prices,
                requiredMinutes,
                maxPriceSnt,
                combinedPriceByPeriod
        );
        addAlwaysOnPeriods(
                selectedMinutes,
                prices,
                minPriceSnt,
                alwaysOnBelowMinPrice,
                combinedPriceByPeriod
        );
        saveSelectedPeriods(control, selectedMinutes, combinedPriceByPeriod);
    }

    private void scheduleTomorrowAwarePeriods(
            ControlEntity control,
            List<NordpoolEntity> todayPrices,
            List<NordpoolEntity> tomorrowPrices,
            int dailyOnMinutes,
            BigDecimal maxPriceSnt,
            BigDecimal minPriceSnt,
            boolean alwaysOnBelowMinPrice,
            Map<Instant, BigDecimal> combinedPriceByPeriod,
            Instant now
    ) {
        Map<NordpoolEntity, Integer> selectedToday = selectCheapestBasePeriods(
                todayPrices,
                dailyOnMinutes,
                maxPriceSnt,
                combinedPriceByPeriod
        );
        Map<NordpoolEntity, Integer> selectedTomorrow = selectCheapestBasePeriods(
                tomorrowPrices,
                dailyOnMinutes,
                maxPriceSnt,
                combinedPriceByPeriod
        );

        moveExpensiveTodayMinutesToCheaperTomorrowPeriods(
                selectedToday,
                selectedTomorrow,
                tomorrowPrices,
                maxPriceSnt,
                combinedPriceByPeriod,
                now
        );

        Map<NordpoolEntity, Integer> selectedMinutes = new HashMap<>(selectedToday);
        selectedTomorrow.forEach((price, minutes) -> selectedMinutes.merge(price, minutes, Math::max));
        List<NordpoolEntity> horizonPrices = new ArrayList<>(todayPrices);
        horizonPrices.addAll(tomorrowPrices);
        addAlwaysOnPeriods(
                selectedMinutes,
                horizonPrices,
                minPriceSnt,
                alwaysOnBelowMinPrice,
                combinedPriceByPeriod
        );
        saveSelectedPeriods(control, selectedMinutes, combinedPriceByPeriod);
    }

    private Map<NordpoolEntity, Integer> selectCheapestBasePeriods(
            List<NordpoolEntity> prices,
            int requiredMinutes,
            BigDecimal maxPriceSnt,
            Map<Instant, BigDecimal> combinedPriceByPeriod
    ) {
        Comparator<NordpoolEntity> byPriceThenTime = Comparator
                .comparing((NordpoolEntity price) -> combinedPriceByPeriod.get(price.getDeliveryStart()))
                .thenComparing(NordpoolEntity::getDeliveryStart);
        Map<NordpoolEntity, Integer> selectedMinutes = new HashMap<>();
        int accumulatedMinutes = 0;

        for (NordpoolEntity price : prices.stream()
                .filter(p -> combinedPriceByPeriod.get(p.getDeliveryStart()).compareTo(maxPriceSnt) <= 0)
                .sorted(byPriceThenTime)
                .toList()) {
            if (accumulatedMinutes >= requiredMinutes) break;
            int minutesLeft = requiredMinutes - accumulatedMinutes;
            int minutesToUse = Math.min(usablePeriodMinutes(price), minutesLeft);
            minutesToUse = Math.floorDiv(minutesToUse, 15) * 15;
            if (minutesToUse <= 0) continue;
            selectedMinutes.put(price, minutesToUse);
            accumulatedMinutes += minutesToUse;
        }
        return selectedMinutes;
    }

    private void moveExpensiveTodayMinutesToCheaperTomorrowPeriods(
            Map<NordpoolEntity, Integer> selectedToday,
            Map<NordpoolEntity, Integer> selectedTomorrow,
            List<NordpoolEntity> tomorrowPrices,
            BigDecimal maxPriceSnt,
            Map<Instant, BigDecimal> combinedPriceByPeriod,
            Instant now
    ) {
        List<NordpoolEntity> selectedTodayByHighestPrice = selectedToday.keySet().stream()
                .filter(price -> !price.getDeliveryStart().isBefore(now))
                .sorted(Comparator
                        .comparing((NordpoolEntity price) -> combinedPriceByPeriod.get(price.getDeliveryStart()))
                        .reversed()
                        .thenComparing(NordpoolEntity::getDeliveryStart, Comparator.reverseOrder()))
                .toList();
        List<NordpoolEntity> tomorrowCandidates = tomorrowPrices.stream()
                .filter(price -> combinedPriceByPeriod.get(price.getDeliveryStart()).compareTo(maxPriceSnt) <= 0)
                .filter(price -> selectedTomorrow.getOrDefault(price, 0) < usablePeriodMinutes(price))
                .sorted(Comparator
                        .comparing((NordpoolEntity price) -> combinedPriceByPeriod.get(price.getDeliveryStart()))
                        .thenComparing(NordpoolEntity::getDeliveryStart))
                .toList();

        int todayIndex = 0;
        for (NordpoolEntity tomorrowCandidate : tomorrowCandidates) {
            int tomorrowCapacity = usablePeriodMinutes(tomorrowCandidate)
                    - selectedTomorrow.getOrDefault(tomorrowCandidate, 0);
            while (tomorrowCapacity >= 15 && todayIndex < selectedTodayByHighestPrice.size()) {
                NordpoolEntity todaySelection = selectedTodayByHighestPrice.get(todayIndex);
                int selectedTodayMinutes = selectedToday.getOrDefault(todaySelection, 0);
                if (selectedTodayMinutes < 15) {
                    todayIndex++;
                    continue;
                }
                BigDecimal tomorrowPrice = combinedPriceByPeriod.get(tomorrowCandidate.getDeliveryStart());
                BigDecimal todayPrice = combinedPriceByPeriod.get(todaySelection.getDeliveryStart());
                if (tomorrowPrice.compareTo(todayPrice) >= 0) return;

                int minutesToMove = Math.floorDiv(
                        Math.min(tomorrowCapacity, selectedTodayMinutes),
                        15
                ) * 15;
                selectedToday.put(todaySelection, selectedTodayMinutes - minutesToMove);
                selectedTomorrow.merge(tomorrowCandidate, minutesToMove, Integer::sum);
                tomorrowCapacity -= minutesToMove;
                if (selectedToday.get(todaySelection) < 15) todayIndex++;
            }
        }
    }

    private void addAlwaysOnPeriods(
            Map<NordpoolEntity, Integer> selectedMinutes,
            List<NordpoolEntity> prices,
            BigDecimal minPriceSnt,
            boolean alwaysOnBelowMinPrice,
            Map<Instant, BigDecimal> combinedPriceByPeriod
    ) {
        if (!alwaysOnBelowMinPrice) return;
        prices.stream()
                .filter(p -> combinedPriceByPeriod.get(p.getDeliveryStart()).compareTo(minPriceSnt) <= 0)
                .forEach(price -> selectedMinutes.merge(
                        price,
                        usablePeriodMinutes(price),
                        Math::max
                ));
    }

    private void saveSelectedPeriods(
            ControlEntity control,
            Map<NordpoolEntity, Integer> selectedMinutes,
            Map<Instant, BigDecimal> combinedPriceByPeriod
    ) {
        selectedMinutes.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(NordpoolEntity::getDeliveryStart)))
                .forEach(entry -> {
                    NordpoolEntity price = entry.getKey();
                    BigDecimal combinedPrice = combinedPriceByPeriod.get(price.getDeliveryStart());
                    Instant end = price.getDeliveryStart().plus(Duration.ofMinutes(entry.getValue()));
                    controlTableRepository.save(ControlTableEntity.builder()
                            .control(control)
                            .startTime(price.getDeliveryStart())
                            .endTime(end)
                            .priceSnt(combinedPrice)
                            .status(Status.FINAL)
                            .build());
                });
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
