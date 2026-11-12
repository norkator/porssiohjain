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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlHeatPumpEntity;
import com.nitramite.porssiohjain.entity.ControlSavingsMonthEntity;
import com.nitramite.porssiohjain.entity.ControlTableEntity;
import com.nitramite.porssiohjain.entity.ElectricityContractEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.enums.Status;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ControlSavingsMonthRepository;
import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.services.models.ControlSavingsResponse;
import com.nitramite.porssiohjain.services.models.ControlSavingsSummaryResponse;
import com.nitramite.porssiohjain.services.nordpool.NordpoolMarket;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ControlSavingsService {

    private static final String BASELINE_METHOD = "same_energy_at_same_day_weighted_average_price";
    private static final BigDecimal SNT_PER_EUR = BigDecimal.valueOf(100);
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final int MAX_CACHE_ENTRIES = 512;
    private static final Duration CONTROL_HISTORY_RETENTION = Duration.ofDays(45);

    private final AccountRepository accountRepository;
    private final ControlRepository controlRepository;
    private final ControlTableRepository controlTableRepository;
    private final NordpoolRepository nordpoolRepository;
    private final ControlSavingsMonthRepository monthlySavingsRepository;
    private final ObjectMapper objectMapper;
    private final Map<SavingsCacheKey, SavingsCacheEntry> savingsCache = new ConcurrentHashMap<>();

    @Transactional
    public ControlSavingsSummaryResponse getCurrentMonthSavings(Long accountId, String timezone) {
        return getSavings(accountId, null, null, timezone);
    }

    @Transactional
    public ControlSavingsSummaryResponse getSavings(
            Long accountId,
            Instant from,
            Instant to,
            String timezone
    ) {
        return getSavings(accountId, from, to, timezone, Instant.now());
    }

    ControlSavingsSummaryResponse getSavings(Long accountId, Instant from, Instant to, String timezone, Instant now) {
        ZoneId zone = resolveZone(accountId, timezone);
        Instant effectiveFrom = from != null ? from : now.atZone(zone).toLocalDate().withDayOfMonth(1)
                .atStartOfDay(zone).toInstant();
        Instant effectiveTo = to != null && to.isBefore(now) ? to : now;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Savings period ends before it starts");
        }
        SavingsCacheKey cacheKey = new SavingsCacheKey(accountId, effectiveFrom, effectiveTo, zone.getId());
        SavingsCacheEntry cached = savingsCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.response();
        }

        AccountEntity account = accountRepository.findById(accountId).orElseThrow();
        List<ControlSavingsSummaryResponse> parts = new ArrayList<>();
        Instant cursor = effectiveFrom;
        while (cursor.isBefore(effectiveTo)) {
            LocalDate month = cursor.atZone(zone).toLocalDate().withDayOfMonth(1);
            Instant monthStart = month.atStartOfDay(zone).toInstant();
            Instant monthEnd = month.plusMonths(1).atStartOfDay(zone).toInstant();
            Instant partEnd = effectiveTo.isBefore(monthEnd) ? effectiveTo : monthEnd;
            if (cursor.equals(monthStart) && partEnd.equals(monthEnd) && !monthEnd.isAfter(now)) {
                parts.add(completedMonth(account, month, zone, now));
            } else {
                requireRawHistory(account, cursor);
                parts.add(calculateSavings(accountId, cursor, partEnd, zone, false));
            }
            cursor = partEnd;
        }
        ControlSavingsSummaryResponse response = parts.size() == 1 ? parts.getFirst()
                : combine(parts, effectiveFrom, effectiveTo, zone);
        cleanupSavingsCache(now);
        savingsCache.put(cacheKey, new SavingsCacheEntry(response, now.plus(CACHE_TTL)));
        return response;
    }

    /** Each account commits its reports and source deletion together; failures leave raw periods intact. */
    @Transactional
    public int archiveAndCleanupAccount(Long accountId, Instant now) {
        AccountEntity account = accountRepository.findWithLockById(accountId).orElseThrow();
        List<ControlEntity> controls = controlRepository.findAllByAccountOrderByIdAsc(account);
        if (controls.isEmpty()) return 0;
        var zones = new LinkedHashSet<String>();
        controls.forEach(control -> zones.add(control.getTimezone()));
        zones.addAll(monthlySavingsRepository.findTimezonesByAccountId(accountId));
        Instant earliest = controlTableRepository.findEarliestStartByAccountId(accountId).orElse(now);
        for (String timezone : zones) {
            ZoneId zone = ZoneId.of(timezone);
            LocalDate currentMonth = now.atZone(zone).toLocalDate().withDayOfMonth(1);
            // Seed the existing six-month client chart as well as all older, still available source months.
            LocalDate firstMonth = earliest.atZone(zone).toLocalDate().withDayOfMonth(1);
            if (firstMonth.isAfter(currentMonth.minusMonths(5))) firstMonth = currentMonth.minusMonths(5);
            for (LocalDate month = firstMonth; month.isBefore(currentMonth); month = month.plusMonths(1)) {
                Instant start = month.atStartOfDay(zone).toInstant();
                if (account.getControlHistoryDeletedBefore() == null
                        || !start.isBefore(account.getControlHistoryDeletedBefore())) {
                    completedMonth(account, month, zone, now);
                }
            }
        }
        Instant cutoff = now.minus(CONTROL_HISTORY_RETENTION);
        int deleted = controlTableRepository.deleteArchivedEndedBefore(accountId, cutoff);
        if (account.getControlHistoryDeletedBefore() == null
                || cutoff.isAfter(account.getControlHistoryDeletedBefore())) {
            account.setControlHistoryDeletedBefore(cutoff);
        }
        savingsCache.keySet().removeIf(key -> key.accountId().equals(accountId));
        return deleted;
    }

    private ControlSavingsSummaryResponse completedMonth(AccountEntity account, LocalDate month, ZoneId zone, Instant now) {
        Instant start = month.atStartOfDay(zone).toInstant();
        Instant end = month.plusMonths(1).atStartOfDay(zone).toInstant();
        if (!account.getCreatedAt().isBefore(end)) {
            // The client asks for six months even for a new account; do not store pre-registration zero reports.
            return summarize(List.of(), start, end, zone);
        }
        var saved = monthlySavingsRepository.findByAccountIdAndMonthStartAndTimezone(account.getId(), month, zone.getId());
        if (saved.isPresent()) return readMonth(saved.get());
        // Serialize concurrent first requests/finalization through the owning account.
        account = accountRepository.findWithLockById(account.getId()).orElseThrow();
        saved = monthlySavingsRepository.findByAccountIdAndMonthStartAndTimezone(account.getId(), month, zone.getId());
        if (saved.isPresent()) return readMonth(saved.get());
        requireRawHistory(account, start);
        ControlSavingsSummaryResponse summary = calculateSavings(account.getId(), start, end, zone, true);
        try {
            monthlySavingsRepository.saveAndFlush(ControlSavingsMonthEntity.builder()
                    .account(account).monthStart(month).timezone(zone.getId()).periodStart(start).periodEnd(end)
                    .summaryJson(objectMapper.writeValueAsString(summary)).calculatedAt(now).build());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store monthly control savings", exception);
        }
        return summary;
    }

    private ControlSavingsSummaryResponse readMonth(ControlSavingsMonthEntity month) {
        try {
            return objectMapper.readValue(month.getSummaryJson(), ControlSavingsSummaryResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read monthly control savings", exception);
        }
    }

    private void requireRawHistory(AccountEntity account, Instant from) {
        if (account.getControlHistoryDeletedBefore() != null && from.isBefore(account.getControlHistoryDeletedBefore())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Detailed control timings have expired; request a saved full calendar month in its original timezone");
        }
    }

    private ControlSavingsSummaryResponse combine(List<ControlSavingsSummaryResponse> parts, Instant from, Instant to, ZoneId zone) {
        Map<Long, ControlSavingsResponse> controls = new LinkedHashMap<>();
        for (ControlSavingsSummaryResponse part : parts) {
            for (ControlSavingsResponse row : part.getControls()) {
                ControlSavingsResponse sum = controls.computeIfAbsent(row.getControlId(), id -> ControlSavingsResponse.builder()
                        .controlId(id).estimatedUsageKwh(BigDecimal.ZERO).baselineCostEur(BigDecimal.ZERO)
                        .controlledCostEur(BigDecimal.ZERO).estimatedSavingsEur(BigDecimal.ZERO).build());
                sum.setControlName(row.getControlName());
                sum.setEstimatedPowerKw(row.getEstimatedPowerKw());
                sum.setEstimatedLoadCount(row.getEstimatedLoadCount());
                sum.setEstimatedUsageKwh(sum.getEstimatedUsageKwh().add(row.getEstimatedUsageKwh()));
                sum.setBaselineCostEur(sum.getBaselineCostEur().add(row.getBaselineCostEur()));
                sum.setControlledCostEur(sum.getControlledCostEur().add(row.getControlledCostEur()));
                sum.setEstimatedSavingsEur(sum.getEstimatedSavingsEur().add(row.getEstimatedSavingsEur()));
                sum.setScheduleEntryCount(sum.getScheduleEntryCount() + row.getScheduleEntryCount());
            }
        }
        return summarize(new ArrayList<>(controls.values()), from, to, zone);
    }

    private ControlSavingsSummaryResponse calculateSavings(
            Long accountId,
            Instant effectiveFrom,
            Instant effectiveTo,
            ZoneId zone,
            boolean finalizingMonth
    ) {
        List<ControlSavingsResponse> controlResponses = controlRepository.findByAccountId(accountId).stream()
                .map(control -> calculateForControl(control, effectiveFrom, effectiveTo, finalizingMonth)).toList();
        return summarize(controlResponses, effectiveFrom, effectiveTo, zone);
    }

    private ControlSavingsSummaryResponse summarize(List<ControlSavingsResponse> controlResponses,
                                                     Instant effectiveFrom, Instant effectiveTo, ZoneId zone) {
        BigDecimal totalEstimatedPowerKw = BigDecimal.ZERO;
        BigDecimal totalUsageKwh = BigDecimal.ZERO;
        BigDecimal totalBaselineCostEur = BigDecimal.ZERO;
        BigDecimal totalControlledCostEur = BigDecimal.ZERO;
        BigDecimal totalSavingsEur = BigDecimal.ZERO;
        int totalScheduleEntries = 0;
        int controlsWithEstimatedPower = 0;

        for (ControlSavingsResponse response : controlResponses) {
            totalEstimatedPowerKw = totalEstimatedPowerKw.add(response.getEstimatedPowerKw());
            totalUsageKwh = totalUsageKwh.add(response.getEstimatedUsageKwh());
            totalBaselineCostEur = totalBaselineCostEur.add(response.getBaselineCostEur());
            totalControlledCostEur = totalControlledCostEur.add(response.getControlledCostEur());
            totalSavingsEur = totalSavingsEur.add(response.getEstimatedSavingsEur());
            totalScheduleEntries += response.getScheduleEntryCount();
            if (response.getEstimatedPowerKw().compareTo(BigDecimal.ZERO) > 0) {
                controlsWithEstimatedPower++;
            }
        }

        return ControlSavingsSummaryResponse.builder()
                .from(effectiveFrom)
                .to(effectiveTo)
                .timezone(zone.getId())
                .baselineMethod(BASELINE_METHOD)
                .estimatedPowerKw(totalEstimatedPowerKw.setScale(3, RoundingMode.HALF_UP))
                .estimatedUsageKwh(totalUsageKwh.setScale(3, RoundingMode.HALF_UP))
                .baselineCostEur(totalBaselineCostEur.setScale(2, RoundingMode.HALF_UP))
                .controlledCostEur(totalControlledCostEur.setScale(2, RoundingMode.HALF_UP))
                .estimatedSavingsEur(totalSavingsEur.setScale(2, RoundingMode.HALF_UP))
                .controlCount(controlResponses.size())
                .controlsWithEstimatedPowerCount(controlsWithEstimatedPower)
                .scheduleEntryCount(totalScheduleEntries)
                .controls(controlResponses)
                .build();
    }

    private void cleanupSavingsCache(Instant now) {
        if (savingsCache.size() < MAX_CACHE_ENTRIES) {
            return;
        }

        Iterator<Map.Entry<SavingsCacheKey, SavingsCacheEntry>> iterator = savingsCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SavingsCacheKey, SavingsCacheEntry> entry = iterator.next();
            if (!entry.getValue().expiresAt().isAfter(now)) {
                iterator.remove();
            }
        }

        if (savingsCache.size() >= MAX_CACHE_ENTRIES) {
            savingsCache.clear();
        }
    }

    private ControlSavingsResponse calculateForControl(
            ControlEntity control,
            Instant from,
            Instant to,
            boolean finalizingMonth
    ) {
        EstimatedPower estimatedPower = getEstimatedPower(control);
        List<ControlTableEntity> entries = controlTableRepository
                .findActivePeriodsOverlapping(
                        control.getId(),
                        Status.FINAL,
                        from,
                        to
                );
        ZoneId zone = ZoneId.of(control.getTimezone());
        Instant dayStart = from.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        Instant dayEnd = to.minusNanos(1).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
        List<NordpoolEntity> prices = entries.isEmpty() ? List.of() : nordpoolRepository.findByMarketIndexNameAndDeliveryStartGreaterThanEqualAndDeliveryStartLessThan(
                NordpoolMarket.normalize(control.getAccount().getMarketIndexName()),
                dayStart,
                dayEnd
        );
        Map<LocalDate, BigDecimal> baselinePriceByDay = calculateBaselinePriceByDay(control, prices, zone);

        BigDecimal usageKwh = BigDecimal.ZERO;
        BigDecimal baselineCostEur = BigDecimal.ZERO;
        BigDecimal controlledCostEur = BigDecimal.ZERO;

        if (estimatedPower.totalKw().compareTo(BigDecimal.ZERO) > 0) {
            if (finalizingMonth) requireDailyPrices(prices, entries, from, to, zone);
            for (ControlTableEntity entry : entries) {
                Instant start = entry.getStartTime().isBefore(from) ? from : entry.getStartTime();
                Instant end = entry.getEndTime().isAfter(to) ? to : entry.getEndTime();
                BigDecimal hours = BigDecimal.valueOf(Duration.between(start, end).toSeconds())
                        .divide(BigDecimal.valueOf(3600), 8, RoundingMode.HALF_UP);
                BigDecimal entryUsageKwh = estimatedPower.totalKw().multiply(hours);
                BigDecimal controlledPriceSnt = entry.getPriceSnt() != null ? entry.getPriceSnt() : BigDecimal.ZERO;
                LocalDate day = start.atZone(zone).toLocalDate();
                BigDecimal baselinePriceSnt = baselinePriceByDay.getOrDefault(day, controlledPriceSnt);

                usageKwh = usageKwh.add(entryUsageKwh);
                controlledCostEur = controlledCostEur.add(entryUsageKwh.multiply(controlledPriceSnt).divide(SNT_PER_EUR, 8, RoundingMode.HALF_UP));
                baselineCostEur = baselineCostEur.add(entryUsageKwh.multiply(baselinePriceSnt).divide(SNT_PER_EUR, 8, RoundingMode.HALF_UP));
            }
        }

        BigDecimal savingsEur = baselineCostEur.subtract(controlledCostEur);
        return ControlSavingsResponse.builder()
                .controlId(control.getId())
                .controlName(control.getName())
                .estimatedPowerKw(estimatedPower.totalKw().setScale(3, RoundingMode.HALF_UP))
                .estimatedUsageKwh(usageKwh.setScale(3, RoundingMode.HALF_UP))
                .baselineCostEur(baselineCostEur.setScale(2, RoundingMode.HALF_UP))
                .controlledCostEur(controlledCostEur.setScale(2, RoundingMode.HALF_UP))
                .estimatedSavingsEur(savingsEur.setScale(2, RoundingMode.HALF_UP))
                .scheduleEntryCount(entries.size())
                .estimatedLoadCount(estimatedPower.loadCount())
                .build();
    }

    private void requireDailyPrices(List<NordpoolEntity> prices, List<ControlTableEntity> entries,
                                    Instant from, Instant to, ZoneId zone) {
        var requiredDates = new LinkedHashSet<LocalDate>();
        for (ControlTableEntity entry : entries) {
            Instant start = entry.getStartTime().isBefore(from) ? from : entry.getStartTime();
            Instant end = entry.getEndTime().isAfter(to) ? to : entry.getEndTime();
            for (LocalDate date = start.atZone(zone).toLocalDate();
                 !date.isAfter(end.minusNanos(1).atZone(zone).toLocalDate()); date = date.plusDays(1)) {
                requiredDates.add(date);
            }
        }
        List<NordpoolEntity> sorted = prices.stream().sorted(Comparator.comparing(NordpoolEntity::getDeliveryStart)).toList();
        for (LocalDate date : requiredDates) {
            Instant cursor = date.atStartOfDay(zone).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
            for (NordpoolEntity price : sorted) {
                if (price.getDeliveryEnd().isAfter(cursor) && !price.getDeliveryStart().isAfter(cursor)) {
                    cursor = price.getDeliveryEnd();
                }
                if (!cursor.isBefore(dayEnd)) break;
            }
            if (cursor.isBefore(dayEnd)) {
                throw new IllegalStateException("Cannot finalize savings: incomplete Nord Pool prices for " + date + " in " + zone);
            }
        }
    }

    private Map<LocalDate, BigDecimal> calculateBaselinePriceByDay(
            ControlEntity control,
            List<NordpoolEntity> prices,
            ZoneId zone
    ) {
        BigDecimal taxMultiplier = BigDecimal.ONE.add(
                control.getTaxPercent().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
        );
        Map<LocalDate, BigDecimal> weightedPriceSums = new HashMap<>();
        Map<LocalDate, BigDecimal> durationHoursByDay = new HashMap<>();
        for (NordpoolEntity price : prices) {
            BigDecimal nordpoolPriceSnt = price.getPriceFi()
                    .multiply(BigDecimal.valueOf(0.1))
                    .multiply(taxMultiplier);
            BigDecimal combinedPriceSnt = nordpoolPriceSnt.add(getTransferPriceSnt(control.getTransferContract(), price, zone));
            BigDecimal hours = BigDecimal.valueOf(Duration.between(price.getDeliveryStart(), price.getDeliveryEnd()).toMinutes())
                    .divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP);
            LocalDate day = price.getDeliveryStart().atZone(zone).toLocalDate();
            weightedPriceSums.merge(day, combinedPriceSnt.multiply(hours), BigDecimal::add);
            durationHoursByDay.merge(day, hours, BigDecimal::add);
        }

        Map<LocalDate, BigDecimal> result = new HashMap<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : weightedPriceSums.entrySet()) {
            BigDecimal durationHours = durationHoursByDay.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (durationHours.compareTo(BigDecimal.ZERO) > 0) {
                result.put(entry.getKey(), entry.getValue().divide(durationHours, 8, RoundingMode.HALF_UP));
            }
        }
        return result;
    }

    private BigDecimal getTransferPriceSnt(
            ElectricityContractEntity transferContract,
            NordpoolEntity price,
            ZoneId zone
    ) {
        if (transferContract == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal transferPrice = null;
        boolean hasStatic = transferContract.getStaticPrice() != null;
        boolean hasDayNight = transferContract.getDayPrice() != null || transferContract.getNightPrice() != null;
        if (hasStatic && !hasDayNight) {
            transferPrice = transferContract.getStaticPrice();
        } else if (hasDayNight) {
            int hour = price.getDeliveryStart().atZone(zone).getHour();
            boolean isNight = hour >= 22 || hour < 7;
            transferPrice = isNight ? transferContract.getNightPrice() : transferContract.getDayPrice();
        }
        if (transferPrice == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal taxAmount = transferContract.getTaxAmount() != null ? transferContract.getTaxAmount() : BigDecimal.ZERO;
        return transferPrice.add(taxAmount);
    }

    private EstimatedPower getEstimatedPower(ControlEntity control) {
        Map<String, BigDecimal> loads = new HashMap<>();
        if (control.getControlDevices() != null) {
            control.getControlDevices().forEach(link -> {
                if (link.getEstimatedPowerKw() != null && link.getEstimatedPowerKw().compareTo(BigDecimal.ZERO) > 0) {
                    String key = "device:" + link.getDevice().getId() + ":" + link.getDeviceChannel();
                    loads.merge(key, link.getEstimatedPowerKw(), BigDecimal::max);
                }
            });
        }
        if (control.getControlHeatPumps() != null) {
            for (ControlHeatPumpEntity link : control.getControlHeatPumps()) {
                if (link.getEstimatedPowerKw() != null && link.getEstimatedPowerKw().compareTo(BigDecimal.ZERO) > 0) {
                    String key = "heat-pump:" + link.getDevice().getId();
                    loads.merge(key, link.getEstimatedPowerKw(), BigDecimal::max);
                }
            }
        }
        BigDecimal totalKw = loads.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new EstimatedPower(totalKw, loads.size());
    }

    private ZoneId resolveZone(Long accountId, String timezone) {
        if (timezone != null && !timezone.isBlank()) {
            try {
                return ZoneId.of(timezone);
            } catch (Exception ignored) {
            }
        }
        return controlRepository.findFirstByAccountId(accountId)
                .map(ControlEntity::getTimezone)
                .map(zone -> {
                    try {
                        return ZoneId.of(zone);
                    } catch (Exception ignored) {
                        return ZoneId.systemDefault();
                    }
                })
                .orElseGet(() -> monthlySavingsRepository.findTimezonesByAccountId(accountId).stream()
                        .findFirst().map(ZoneId::of).orElse(ZoneId.systemDefault()));
    }

    private record EstimatedPower(BigDecimal totalKw, int loadCount) {
    }

    private record SavingsCacheKey(Long accountId, Instant from, Instant to, String timezone) {
    }

    private record SavingsCacheEntry(ControlSavingsSummaryResponse response, Instant expiresAt) {
    }
}
