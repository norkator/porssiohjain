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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlHeatPumpEntity;
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.ProductionSourceHeatPumpEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.SiteWeatherEntity;
import com.nitramite.porssiohjain.entity.WeatherControlHeatPumpEntity;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.ControlHeatPumpRepository;
import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceHeatPumpRepository;
import com.nitramite.porssiohjain.entity.repository.SiteWeatherRepository;
import com.nitramite.porssiohjain.entity.repository.WeatherControlHeatPumpRepository;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerHeatPumpCommandService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerChangeNotificationService;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateHexDecoderService;
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
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ToshibaAcStateHexDecoderService TOSHIBA_DECODER = new ToshibaAcStateHexDecoderService();

    private final WeatherControlHeatPumpRepository weatherControlHeatPumpRepository;
    private final ProductionSourceHeatPumpRepository productionSourceHeatPumpRepository;
    private final ControlHeatPumpRepository controlHeatPumpRepository;
    private final DeviceAcDataRepository deviceAcDataRepository;
    private final SiteWeatherRepository siteWeatherRepository;
    private final ControlTableRepository controlTableRepository;
    private final AcCommandDispatchService acCommandDispatchService;
    private final ControlPriceService controlPriceService;
    private final HeatingPlannerHeatPumpCommandService heatingPlannerHeatPumpCommandService;
    private final HeatingPlannerChangeNotificationService changeNotificationService;

    public void runScheduledHeatPumpControls() {
        Instant now = Instant.now();
        Map<Long, HeatPumpCommandCandidate> commandsByDeviceId = new LinkedHashMap<>();

        var weatherRules = weatherControlHeatPumpRepository.findAll().stream()
                .sorted(Comparator.comparing(WeatherControlHeatPumpEntity::getId))
                .toList();

        weatherRules.stream().filter(WeatherControlHeatPumpEntity::isPriorityRule)
                .forEach(rule -> addIfMatched(commandsByDeviceId, evaluateWeatherRule(rule, now)));

        heatingPlannerHeatPumpCommandService.currentCommands(now).forEach(command -> addIfMatched(commandsByDeviceId,
                Optional.of(new HeatPumpCommandCandidate(command.device(), command.state(), 0,
                        "HEATING_PLANNER", command.sourceId(), command.reason(), command.targetTemperature()))));

        weatherRules.stream().filter(rule -> !rule.isPriorityRule())
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
                ), null
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
                ), null
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
                    ), null
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
                "control schedule currently active", null
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

        if (sameState(candidate.stateHex(), acData.getLastSentStateHex())) {
            log.info(
                    "Skipping heat pump command for deviceId={} because desired state already matches lastSentStateHex. ruleType={}, ruleId={}",
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
        String previousSentState = acData.getLastSentStateHex();
        acCommandDispatchService.dispatchHexState(acData, candidate.stateHex());
        if ("HEATING_PLANNER".equals(candidate.ruleType()) && candidate.targetTemperature() != null
                && !nullSafe(acData.getLastSentStateHex()).isBlank()
                && !samePlannerSettings(acData.getAcType(), acData.getLastSentStateHex(), previousSentState)) {
            changeNotificationService.heatPumpApplied(candidate.ruleId(), candidate.targetTemperature(), Instant.now());
        }
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
        return controlPriceService.getCurrentCombinedPrice(control, now);
    }

    private boolean isControlActive(ControlEntity control, Instant now) {
        if (control.getMode() == ControlMode.MANUAL) {
            return control.isManualOn();
        }

        if (!control.getMode().usesGeneratedControlTable()) {
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

    private boolean sameState(String requested, String sent) {
        if (requested == null || sent == null) return false;
        if (requested.equalsIgnoreCase(sent)) return true;
        if (requested.trim().startsWith("{") && sent.trim().startsWith("{")) {
            try {
                return JSON.readTree(requested).equals(JSON.readTree(sent));
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    private boolean samePlannerSettings(AcType type, String applied, String previous) {
        if (sameState(applied, previous)) return true;
        // Polled state also contains changing telemetry and command metadata. Only the
        // settings controlled by Heating Planner should trigger its change notification.
        if (type == AcType.TOSHIBA) {
            var current = TOSHIBA_DECODER.decode(applied);
            var before = TOSHIBA_DECODER.decode(previous);
            return current.isValid() && before.isValid()
                    && current.getPower().getRawUnsigned().equals(before.getPower().getRawUnsigned())
                    && current.getMode().getRawUnsigned().equals(before.getMode().getRawUnsigned())
                    && current.getTargetTemperature().getRawUnsigned()
                    .equals(before.getTargetTemperature().getRawUnsigned());
        }
        if (type == AcType.MITSUBISHI_MELCLOUD || type == AcType.MITSUBISHI_MELCLOUD_HOME) {
            try {
                var current = JSON.readTree(applied);
                var before = JSON.readTree(previous);
                boolean melCloud = type == AcType.MITSUBISHI_MELCLOUD;
                String power = melCloud ? "Power" : "power";
                String mode = melCloud ? "OperationMode" : "operationMode";
                String temperature = melCloud ? "SetTemperature" : "setTemperature";
                return current.hasNonNull(power) && current.hasNonNull(mode)
                        && current.path(temperature).isNumber() && before.path(temperature).isNumber()
                        && current.get(power).equals(before.get(power))
                        && current.get(mode).equals(before.get(mode))
                        && current.get(temperature).decimalValue()
                        .compareTo(before.get(temperature).decimalValue()) == 0;
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    private record HeatPumpCommandCandidate(
            DeviceEntity device,
            String stateHex,
            int priority,
            String ruleType,
            Long ruleId,
            String reason,
            BigDecimal targetTemperature
    ) {
    }

}
