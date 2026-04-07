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
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.ElectricityContractEntity;
import com.nitramite.porssiohjain.entity.ProductionSourceHeatPumpEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.SiteWeatherEntity;
import com.nitramite.porssiohjain.entity.WeatherControlHeatPumpEntity;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.ControlHeatPumpRepository;
import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceHeatPumpRepository;
import com.nitramite.porssiohjain.entity.repository.SiteWeatherRepository;
import com.nitramite.porssiohjain.entity.repository.WeatherControlHeatPumpRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HeatPumpControlService {

    private static final long CONTROL_LOOKBACK_SECONDS = 30L * 60L;

    private final WeatherControlHeatPumpRepository weatherControlHeatPumpRepository;
    private final ProductionSourceHeatPumpRepository productionSourceHeatPumpRepository;
    private final ControlHeatPumpRepository controlHeatPumpRepository;
    private final DeviceAcDataRepository deviceAcDataRepository;
    private final SiteWeatherRepository siteWeatherRepository;
    private final NordpoolRepository nordpoolRepository;
    private final ControlTableRepository controlTableRepository;
    private final AcCommandDispatchService acCommandDispatchService;

    public void runScheduledHeatPumpControls() {
        Instant now = Instant.now();
        Map<Long, HeatPumpCommandCandidate> commandsByDeviceId = new LinkedHashMap<>();

        weatherControlHeatPumpRepository.findAll().stream()
                .sorted(Comparator.comparing(WeatherControlHeatPumpEntity::getId))
                .forEach(rule -> addIfMatched(commandsByDeviceId, evaluateWeatherRule(rule, now)));

        productionSourceHeatPumpRepository.findAll().stream()
                .sorted(Comparator.comparing(ProductionSourceHeatPumpEntity::getId))
                .forEach(rule -> addIfMatched(commandsByDeviceId, evaluateProductionRule(rule)));

        controlHeatPumpRepository.findAll().stream()
                .sorted(Comparator.comparing(ControlHeatPumpEntity::getId))
                .forEach(rule -> addIfMatched(commandsByDeviceId, evaluateControlRule(rule, now)));

        commandsByDeviceId.values().forEach(this::dispatchCandidate);
        log.info("Heat pump scheduler evaluated {} device command(s)", commandsByDeviceId.size());
    }

    private void addIfMatched(
            Map<Long, HeatPumpCommandCandidate> commandsByDeviceId,
            Optional<HeatPumpCommandCandidate> candidate
    ) {
        candidate.ifPresent(value -> commandsByDeviceId.putIfAbsent(value.device().getId(), value));
    }

    private Optional<HeatPumpCommandCandidate> evaluateWeatherRule(
            WeatherControlHeatPumpEntity rule,
            Instant now
    ) {
        DeviceEntity device = rule.getDevice();
        if (!isEligibleDevice(device)) {
            return Optional.empty();
        }

        Optional<BigDecimal> metricValue = getCurrentWeatherMetricValue(rule.getWeatherControl().getSite(), rule.getWeatherMetric(), now);
        if (metricValue.isEmpty()) {
            log.debug("Skipping weather heat pump rule {} because no current weather metric was found", rule.getId());
            return Optional.empty();
        }

        if (!matches(metricValue.get(), rule.getComparisonType(), rule.getThresholdValue())) {
            return Optional.empty();
        }

        return Optional.of(new HeatPumpCommandCandidate(
                device,
                rule.getStateHex(),
                1,
                "WEATHER",
                rule.getId(),
                String.format(
                        "%s %s %s (actual=%s)",
                        rule.getWeatherMetric(),
                        rule.getComparisonType(),
                        rule.getThresholdValue(),
                        metricValue.get()
                )
        ));
    }

    private Optional<HeatPumpCommandCandidate> evaluateProductionRule(
            ProductionSourceHeatPumpEntity rule
    ) {
        DeviceEntity device = rule.getDevice();
        if (!isEligibleDevice(device) || !rule.getProductionSource().isEnabled()) {
            return Optional.empty();
        }

        BigDecimal currentKw = rule.getProductionSource().getCurrentKw();
        if (!matches(currentKw, rule.getComparisonType(), rule.getTriggerKw())) {
            return Optional.empty();
        }

        return Optional.of(new HeatPumpCommandCandidate(
                device,
                rule.getStateHex(),
                2,
                "PRODUCTION_SOURCE",
                rule.getId(),
                String.format(
                        "production %s %s kW (actual=%s)",
                        rule.getComparisonType(),
                        rule.getTriggerKw(),
                        currentKw
                )
        ));
    }

    private Optional<HeatPumpCommandCandidate> evaluateControlRule(
            ControlHeatPumpEntity rule,
            Instant now
    ) {
        DeviceEntity device = rule.getDevice();
        if (!isEligibleDevice(device)) {
            return Optional.empty();
        }

        if (rule.getComparisonType() != null && rule.getPriceLimit() != null) {
            Optional<BigDecimal> currentPrice = getCurrentControlPrice(rule.getControl(), now);
            if (currentPrice.isEmpty()) {
                log.debug("Skipping control heat pump rule {} because no current price was found", rule.getId());
                return Optional.empty();
            }
            if (!matches(currentPrice.get(), rule.getComparisonType(), rule.getPriceLimit())) {
                return Optional.empty();
            }

            return Optional.of(new HeatPumpCommandCandidate(
                    device,
                    rule.getStateHex(),
                    3,
                    "CONTROL",
                    rule.getId(),
                    String.format(
                            "price %s %s (actual=%s)",
                            rule.getComparisonType(),
                            rule.getPriceLimit(),
                            currentPrice.get()
                    )
            ));
        }

        if (!isControlActive(rule.getControl(), now)) {
            return Optional.empty();
        }

        return Optional.of(new HeatPumpCommandCandidate(
                device,
                rule.getStateHex(),
                3,
                "CONTROL",
                rule.getId(),
                "control schedule currently active"
        ));
    }

    private void dispatchCandidate(HeatPumpCommandCandidate candidate) {
        DeviceEntity device = candidate.device();
        DeviceAcDataEntity acData = deviceAcDataRepository.findByDevice(device)
                .orElse(null);

        if (acData == null) {
            log.warn("Skipping heat pump command for deviceId={} because device AC data was not found", device.getId());
            return;
        }

        if (candidate.stateHex().equalsIgnoreCase(nullSafe(acData.getLastPolledStateHex()))) {
            log.info(
                    "Skipping heat pump command for deviceId={} because desired state already matches lastPolledStateHex. ruleType={}, ruleId={}",
                    device.getId(),
                    candidate.ruleType(),
                    candidate.ruleId()
            );
            return;
        }

        log.info(
                "Applying heat pump command for deviceId={}, priority={}, ruleType={}, ruleId={}, reason={}",
                device.getId(),
                candidate.priority(),
                candidate.ruleType(),
                candidate.ruleId(),
                candidate.reason()
        );
        acCommandDispatchService.dispatchHexState(acData, candidate.stateHex());
    }

    private Optional<BigDecimal> getCurrentWeatherMetricValue(
            SiteEntity site,
            WeatherMetricType metricType,
            Instant now
    ) {
        Optional<SiteWeatherEntity> weather = siteWeatherRepository
                .findFirstBySiteAndForecastTimeLessThanEqualOrderByForecastTimeDesc(site, now)
                .or(() -> siteWeatherRepository.findFirstBySiteAndForecastTimeGreaterThanEqualOrderByForecastTimeAsc(site, now));

        return weather.map(entity -> switch (metricType) {
            case TEMPERATURE -> entity.getTemperature();
            case HUMIDITY -> entity.getHumidity();
        });
    }

    private Optional<BigDecimal> getCurrentControlPrice(ControlEntity control, Instant now) {
        return nordpoolRepository.findFirstByDeliveryStartLessThanEqualAndDeliveryEndGreaterThan(now, now)
                .map(currentPrice -> {
                    BigDecimal taxPercent = control.getTaxPercent() != null ? control.getTaxPercent() : BigDecimal.ZERO;
                    BigDecimal taxMultiplier = BigDecimal.ONE.add(taxPercent.divide(BigDecimal.valueOf(100)));
                    BigDecimal nordpoolPrice = currentPrice.getPriceFi()
                            .multiply(BigDecimal.valueOf(0.1))
                            .multiply(taxMultiplier);
                    return nordpoolPrice.add(resolveTransferPrice(control.getTransferContract(), currentPrice.getDeliveryStart(), ZoneId.of(control.getTimezone())));
                });
    }

    private BigDecimal resolveTransferPrice(
            ElectricityContractEntity transferContract,
            Instant deliveryStart,
            ZoneId zone
    ) {
        if (transferContract == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal staticPrice = transferContract.getStaticPrice();
        BigDecimal nightPrice = transferContract.getNightPrice();
        BigDecimal dayPrice = transferContract.getDayPrice();
        BigDecimal taxAmount = transferContract.getTaxAmount() != null ? transferContract.getTaxAmount() : BigDecimal.ZERO;

        if (staticPrice != null && dayPrice == null && nightPrice == null) {
            return staticPrice.add(taxAmount);
        }

        if (dayPrice != null || nightPrice != null) {
            int hour = deliveryStart.atZone(zone).getHour();
            boolean isNight = hour >= 22 || hour < 7;
            BigDecimal selected = isNight ? nightPrice : dayPrice;
            if (selected != null) {
                return selected.add(taxAmount);
            }
        }

        return BigDecimal.ZERO;
    }

    private boolean isControlActive(ControlEntity control, Instant now) {
        if (control.getMode() == ControlMode.MANUAL) {
            return control.isManualOn();
        }

        if (control.getMode() != ControlMode.CHEAPEST_HOURS && control.getMode() != ControlMode.BELOW_MAX_PRICE) {
            return false;
        }

        ZoneId controlZone = ZoneId.of(control.getTimezone());
        ZonedDateTime nowInControlZone = now.atZone(controlZone);

        return controlTableRepository.findByControlIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
                        control.getId(),
                        Status.FINAL,
                        now.minusSeconds(CONTROL_LOOKBACK_SECONDS)
                ).stream()
                .anyMatch(controlTable -> {
                    ZonedDateTime start = controlTable.getStartTime().atZone(controlZone);
                    ZonedDateTime end = controlTable.getEndTime().atZone(controlZone);
                    return !nowInControlZone.isBefore(start) && !nowInControlZone.isAfter(end);
                });
    }

    private boolean matches(BigDecimal actualValue, ComparisonType comparisonType, BigDecimal thresholdValue) {
        if (actualValue == null || comparisonType == null || thresholdValue == null) {
            return false;
        }
        int comparison = actualValue.compareTo(thresholdValue);
        return switch (comparisonType) {
            case GREATER_THAN -> comparison > 0;
            case LESS_THAN -> comparison < 0;
        };
    }

    private boolean isEligibleDevice(DeviceEntity device) {
        return device != null && device.isEnabled() && device.getDeviceType() == DeviceType.HEAT_PUMP;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private record HeatPumpCommandCandidate(
            DeviceEntity device,
            String stateHex,
            int priority,
            String ruleType,
            Long ruleId,
            String reason
    ) {
    }

}
