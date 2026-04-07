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
import com.nitramite.porssiohjain.entity.ControlHeatPumpEntity;
import com.nitramite.porssiohjain.entity.ControlTableEntity;
import com.nitramite.porssiohjain.entity.ElectricityContractEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.enums.Status;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.services.models.ControlSavingsResponse;
import com.nitramite.porssiohjain.services.models.ControlSavingsSummaryResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ControlSavingsService {

    private static final String BASELINE_METHOD = "same_energy_at_same_day_weighted_average_price";
    private static final BigDecimal SNT_PER_EUR = BigDecimal.valueOf(100);

    private final ControlRepository controlRepository;
    private final ControlTableRepository controlTableRepository;
    private final NordpoolRepository nordpoolRepository;

    @Transactional
    public ControlSavingsSummaryResponse getCurrentMonthSavings(Long accountId, String timezone) {
        ZoneId zone = resolveZone(accountId, timezone);
        YearMonth currentMonth = YearMonth.now(zone);
        ZonedDateTime from = currentMonth.atDay(1).atStartOfDay(zone);
        return getSavings(accountId, from.toInstant(), ZonedDateTime.now(zone).toInstant(), timezone);
    }

    @Transactional
    public ControlSavingsSummaryResponse getSavings(
            Long accountId,
            Instant from,
            Instant to,
            String timezone
    ) {
        ZoneId zone = resolveZone(accountId, timezone);
        Instant effectiveFrom = from != null ? from : YearMonth.now(zone).atDay(1).atStartOfDay(zone).toInstant();
        Instant effectiveTo = to != null ? to : Instant.now();

        List<ControlEntity> controls = controlRepository.findByAccountId(accountId);
        List<ControlSavingsResponse> controlResponses = new ArrayList<>();
        BigDecimal totalEstimatedPowerKw = BigDecimal.ZERO;
        BigDecimal totalUsageKwh = BigDecimal.ZERO;
        BigDecimal totalBaselineCostEur = BigDecimal.ZERO;
        BigDecimal totalControlledCostEur = BigDecimal.ZERO;
        BigDecimal totalSavingsEur = BigDecimal.ZERO;
        int totalScheduleEntries = 0;
        int controlsWithEstimatedPower = 0;

        for (ControlEntity control : controls) {
            ControlSavingsResponse response = calculateForControl(control, effectiveFrom, effectiveTo, zone);
            controlResponses.add(response);
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
                .controlCount(controls.size())
                .controlsWithEstimatedPowerCount(controlsWithEstimatedPower)
                .scheduleEntryCount(totalScheduleEntries)
                .controls(controlResponses)
                .build();
    }

    private ControlSavingsResponse calculateForControl(
            ControlEntity control,
            Instant from,
            Instant to,
            ZoneId zone
    ) {
        EstimatedPower estimatedPower = getEstimatedPower(control);
        List<ControlTableEntity> entries = controlTableRepository
                .findByControlIdAndStatusAndStartTimeBetweenOrderByStartTimeAsc(
                        control.getId(),
                        Status.FINAL,
                        from,
                        to
                );
        List<NordpoolEntity> prices = nordpoolRepository.findPricesBetween(from, to);
        Map<LocalDate, BigDecimal> baselinePriceByDay = calculateBaselinePriceByDay(control, prices, zone);

        BigDecimal usageKwh = BigDecimal.ZERO;
        BigDecimal baselineCostEur = BigDecimal.ZERO;
        BigDecimal controlledCostEur = BigDecimal.ZERO;

        if (estimatedPower.totalKw().compareTo(BigDecimal.ZERO) > 0) {
            for (ControlTableEntity entry : entries) {
                BigDecimal hours = BigDecimal.valueOf(Duration.between(entry.getStartTime(), entry.getEndTime()).toMinutes())
                        .divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP);
                BigDecimal entryUsageKwh = estimatedPower.totalKw().multiply(hours);
                BigDecimal controlledPriceSnt = entry.getPriceSnt() != null ? entry.getPriceSnt() : BigDecimal.ZERO;
                LocalDate day = entry.getStartTime().atZone(zone).toLocalDate();
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
                .orElse(ZoneId.systemDefault());
    }

    private record EstimatedPower(BigDecimal totalKw, int loadCount) {
    }
}
