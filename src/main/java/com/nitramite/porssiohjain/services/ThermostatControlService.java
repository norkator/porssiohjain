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

import com.nitramite.porssiohjain.entity.ControlThermostatEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.repository.ControlThermostatRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ThermostatControlService {

    private static final BigDecimal MIN_TEMPERATURE_DELTA = new BigDecimal("0.50");
    private static final Duration MIN_RESEND_INTERVAL = Duration.ofMinutes(15);

    private final ControlThermostatRepository controlThermostatRepository;
    private final ControlPriceService controlPriceService;
    private final ThermostatCurveService thermostatCurveService;
    private final MqttService mqttService;

    public void runScheduledThermostatControls() {
        Instant now = Instant.now();
        Map<String, ThermostatCommandCandidate> candidatesByTarget = new LinkedHashMap<>();

        controlThermostatRepository.findAll().stream()
                .sorted((left, right) -> left.getId().compareTo(right.getId()))
                .forEach(rule -> evaluateRule(rule, now)
                        .ifPresent(candidate -> candidatesByTarget.putIfAbsent(candidate.targetKey(), candidate)));

        candidatesByTarget.values().forEach(candidate -> dispatchCandidate(candidate, now));
        log.info("Thermostat scheduler evaluated {} thermostat command(s)", candidatesByTarget.size());
    }

    private Optional<ThermostatCommandCandidate> evaluateRule(ControlThermostatEntity rule, Instant now) {
        DeviceEntity device = rule.getDevice();
        if (!isEligibleDevice(device) || !rule.isEnabled()) {
            return Optional.empty();
        }

        BigDecimal targetTemperature = controlPriceService.getCurrentCombinedPrice(rule.getControl(), now)
                .map(price -> thermostatCurveService.evaluate(rule.getCurveJson(), price))
                .orElse(rule.getFallbackTemperature());

        if (targetTemperature == null) {
            log.debug("Skipping thermostat rule {} because current price and fallback temperature are both unavailable", rule.getId());
            return Optional.empty();
        }

        if (rule.getMinTemperature() != null && targetTemperature.compareTo(rule.getMinTemperature()) < 0) {
            targetTemperature = rule.getMinTemperature();
        }
        if (rule.getMaxTemperature() != null && targetTemperature.compareTo(rule.getMaxTemperature()) > 0) {
            targetTemperature = rule.getMaxTemperature();
        }

        return Optional.of(new ThermostatCommandCandidate(
                rule,
                device.getUuid().toString() + ":" + rule.getThermostatChannel(),
                targetTemperature
        ));
    }

    private void dispatchCandidate(ThermostatCommandCandidate candidate, Instant now) {
        ControlThermostatEntity rule = candidate.rule();
        BigDecimal lastAppliedTemperature = rule.getLastAppliedTemperature();
        Instant lastAppliedAt = rule.getLastAppliedAt();

        boolean sameTarget = lastAppliedTemperature != null
                && candidate.targetTemperature().subtract(lastAppliedTemperature).abs().compareTo(MIN_TEMPERATURE_DELTA) < 0;
        boolean tooSoon = lastAppliedAt != null && lastAppliedAt.plus(MIN_RESEND_INTERVAL).isAfter(now);

        if (sameTarget && tooSoon) {
            log.debug("Skipping thermostat command for ruleId={} because target temperature {} was already applied recently", rule.getId(), candidate.targetTemperature());
            return;
        }

        mqttService.setThermostatTemperature(
                rule.getDevice().getUuid().toString(),
                rule.getThermostatChannel(),
                candidate.targetTemperature()
        );
        rule.setLastAppliedTemperature(candidate.targetTemperature());
        rule.setLastAppliedAt(now);
        controlThermostatRepository.save(rule);
        log.info(
                "Applied thermostat target temperature for deviceId={}, channel={}, targetTemperature={}",
                rule.getDevice().getId(),
                rule.getThermostatChannel(),
                candidate.targetTemperature()
        );
    }

    private boolean isEligibleDevice(DeviceEntity device) {
        return device != null && device.isEnabled() && device.isMqttOnline() && device.getDeviceType() == DeviceType.THERMOSTAT;
    }

    private record ThermostatCommandCandidate(
            ControlThermostatEntity rule,
            String targetKey,
            BigDecimal targetTemperature
    ) {
    }
}
