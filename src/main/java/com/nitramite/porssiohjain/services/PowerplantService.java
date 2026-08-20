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

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.PowerplantElementEntity;
import com.nitramite.porssiohjain.entity.PowerplantRuleEntity;
import com.nitramite.porssiohjain.entity.PowerplantSettingsEntity;
import com.nitramite.porssiohjain.entity.ZigbeeDeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.PowerplantComparisonType;
import com.nitramite.porssiohjain.entity.enums.PowerplantElementType;
import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.PowerplantElementRepository;
import com.nitramite.porssiohjain.entity.repository.PowerplantRuleRepository;
import com.nitramite.porssiohjain.entity.repository.PowerplantSettingsRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeDeviceMeasurementRepository;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.PowerplantElementResponse;
import com.nitramite.porssiohjain.services.models.PowerplantMeasurementOptionResponse;
import com.nitramite.porssiohjain.services.models.PowerplantRuleResponse;
import com.nitramite.porssiohjain.services.models.PowerplantSettingsResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PowerplantService {

    private static final int MIN_CHANNEL = 0;
    private static final int MAX_CHANNEL = 3;
    private static final int MIN_BOARD_WIDTH = 800;
    private static final int MIN_BOARD_HEIGHT = 500;
    private static final int MAX_BOARD_WIDTH = 4000;
    private static final int MAX_BOARD_HEIGHT = 2400;
    private static final Duration MEASUREMENT_SELECTOR_LOOKBACK = Duration.ofDays(30);
    private static final Duration MEASUREMENT_FRESHNESS = Duration.ofMinutes(60);

    private final AccountRepository accountRepository;
    private final DeviceRepository deviceRepository;
    private final PowerplantElementRepository powerplantElementRepository;
    private final PowerplantRuleRepository powerplantRuleRepository;
    private final PowerplantSettingsRepository powerplantSettingsRepository;
    private final ZigbeeDeviceMeasurementRepository measurementRepository;
    private final DemoAccountGuard demoAccountGuard;
    private final ControlService controlService;

    @Transactional(readOnly = true)
    public List<PowerplantElementResponse> getElements(Long accountId) {
        validateAccount(accountId);
        return powerplantElementRepository.findByAccountIdOrderByIdAsc(accountId).stream()
                .map(this::mapElement)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PowerplantRuleResponse> getRules(Long accountId) {
        validateAccount(accountId);
        return powerplantRuleRepository.findByAccountIdOrderByIdAsc(accountId).stream()
                .map(this::mapRule)
                .toList();
    }

    @Transactional
    public PowerplantSettingsResponse getOrCreateSettings(Long accountId) {
        AccountEntity account = validateAccount(accountId);
        PowerplantSettingsEntity settings = powerplantSettingsRepository.findByAccountId(accountId)
                .orElseGet(() -> powerplantSettingsRepository.save(PowerplantSettingsEntity.builder()
                        .account(account)
                        .boardWidth(1600)
                        .boardHeight(900)
                        .build()));
        return mapSettings(settings);
    }

    @Transactional
    public PowerplantSettingsResponse saveSettings(Long accountId, int boardWidth, int boardHeight) {
        demoAccountGuard.assertWritable(accountId);
        AccountEntity account = validateAccount(accountId);
        PowerplantSettingsEntity settings = powerplantSettingsRepository.findByAccountId(accountId)
                .orElseGet(() -> PowerplantSettingsEntity.builder().account(account).build());
        settings.setAccount(account);
        settings.setBoardWidth(clamp(boardWidth, MIN_BOARD_WIDTH, MAX_BOARD_WIDTH));
        settings.setBoardHeight(clamp(boardHeight, MIN_BOARD_HEIGHT, MAX_BOARD_HEIGHT));
        return mapSettings(powerplantSettingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public List<PowerplantMeasurementOptionResponse> getMeasurementOptions(Long accountId) {
        validateAccount(accountId);
        Instant after = Instant.now().minus(MEASUREMENT_SELECTOR_LOOKBACK);
        return measurementRepository.findLatestDistinctMeasurements(accountId, after).stream()
                .map(this::mapMeasurementOption)
                .toList();
    }

    @Transactional
    public PowerplantElementResponse saveElement(
            Long accountId,
            Long elementId,
            String name,
            PowerplantElementType elementType,
            String iconName,
            BigDecimal displayValue,
            String displayUnit,
            Long deviceId,
            Integer deviceChannel,
            ZigbeeMeasurementType measurementType,
            String measurementKey,
            int canvasX,
            int canvasY
    ) {
        demoAccountGuard.assertWritable(accountId);
        AccountEntity account = validateAccount(accountId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Element name is required");
        }
        if (elementType == null) {
            throw new IllegalArgumentException("Element type is required");
        }
        if (iconName == null || iconName.isBlank()) {
            throw new IllegalArgumentException("Icon is required");
        }

        DeviceEntity device = null;
        if (deviceId != null) {
            device = validateAccountDevice(accountId, deviceId);
        }
        if (elementType == PowerplantElementType.DEVICE_CONTROL) {
            if (device == null) {
                throw new IllegalArgumentException("Device control element requires a standard device");
            }
            validateStandardRelayDevice(device);
            validateChannel(deviceChannel);
            measurementType = null;
            measurementKey = null;
        } else if (elementType == PowerplantElementType.INDICATOR && device != null) {
            if (measurementType == null) {
                throw new IllegalArgumentException("Measurement type is required when indicator is linked to a device");
            }
            measurementKey = measurementKey != null && !measurementKey.isBlank() ? measurementKey.strip() : defaultMeasurementKey(measurementType);
            validateMeasurementBinding(accountId, device.getId(), measurementType, measurementKey);
            deviceChannel = null;
        } else {
            device = null;
            deviceChannel = null;
            measurementType = null;
            measurementKey = null;
        }

        PowerplantElementEntity entity = elementId != null
                ? powerplantElementRepository.findByIdAndAccountId(elementId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Powerplant element not found: " + elementId))
                : new PowerplantElementEntity();
        boolean ruleEndpointChanged = elementId != null && ruleEndpointChanged(
                entity,
                elementType,
                device,
                deviceChannel,
                measurementType,
                measurementKey
        );

        entity.setAccount(account);
        entity.setName(name.strip());
        entity.setElementType(elementType);
        entity.setIconName(iconName);
        entity.setDisplayValue(displayValue);
        entity.setDisplayUnit(displayUnit != null && !displayUnit.isBlank() ? displayUnit.strip() : null);
        entity.setDevice(device);
        entity.setDeviceChannel(deviceChannel);
        entity.setMeasurementType(measurementType);
        entity.setMeasurementKey(measurementKey);
        entity.setCanvasX(Math.max(0, canvasX));
        entity.setCanvasY(Math.max(0, canvasY));

        PowerplantElementEntity saved = powerplantElementRepository.save(entity);
        if (ruleEndpointChanged) {
            rearmRulesForElement(saved);
        }
        return mapElement(saved);
    }

    @Transactional
    public void updateElementPosition(Long accountId, Long elementId, int canvasX, int canvasY) {
        demoAccountGuard.assertWritable(accountId);
        PowerplantElementEntity entity = powerplantElementRepository.findByIdAndAccountId(elementId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Powerplant element not found: " + elementId));
        entity.setCanvasX(Math.max(0, canvasX));
        entity.setCanvasY(Math.max(0, canvasY));
        powerplantElementRepository.save(entity);
    }

    @Transactional
    public void deleteElement(Long accountId, Long elementId) {
        demoAccountGuard.assertWritable(accountId);
        PowerplantElementEntity entity = powerplantElementRepository.findByIdAndAccountId(elementId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Powerplant element not found: " + elementId));
        powerplantRuleRepository.deleteAll(powerplantRuleRepository.findBySourceElement(entity));
        powerplantRuleRepository.deleteAll(powerplantRuleRepository.findByTargetElement(entity));
        powerplantElementRepository.delete(entity);
    }

    @Transactional
    public PowerplantRuleResponse saveRule(
            Long accountId,
            Long ruleId,
            Long sourceElementId,
            Long targetElementId,
            PowerplantComparisonType comparisonType,
            BigDecimal thresholdValue,
            BigDecimal hysteresisValue,
            ControlAction targetAction,
            boolean enabled,
            Integer cooldownSeconds
    ) {
        demoAccountGuard.assertWritable(accountId);
        AccountEntity account = validateAccount(accountId);
        if (comparisonType == null) {
            throw new IllegalArgumentException("Comparison type is required");
        }
        if (thresholdValue == null) {
            throw new IllegalArgumentException("Threshold value is required");
        }
        if (targetAction != ControlAction.TURN_ON && targetAction != ControlAction.TURN_OFF) {
            throw new IllegalArgumentException("Powerplant rules support only turn on / turn off actions");
        }

        PowerplantElementEntity source = powerplantElementRepository.findByIdAndAccountId(sourceElementId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Source element not found: " + sourceElementId));
        PowerplantElementEntity target = powerplantElementRepository.findByIdAndAccountId(targetElementId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Target element not found: " + targetElementId));

        validateRuleEndpoints(source, target);

        PowerplantRuleEntity entity = ruleId != null
                ? powerplantRuleRepository.findByIdAndAccountId(ruleId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Powerplant rule not found: " + ruleId))
                : new PowerplantRuleEntity();

        entity.setAccount(account);
        entity.setSourceElement(source);
        entity.setTargetElement(target);
        entity.setComparisonType(comparisonType);
        entity.setThresholdValue(thresholdValue);
        entity.setHysteresisValue(hysteresisValue != null && hysteresisValue.signum() > 0 ? hysteresisValue : null);
        entity.setTargetAction(targetAction);
        entity.setEnabled(enabled);
        entity.setCooldownSeconds(cooldownSeconds != null ? Math.max(0, cooldownSeconds) : 300);
        rearmRule(entity);

        return mapRule(powerplantRuleRepository.save(entity));
    }

    @Transactional
    public int rearmAllRules(Long accountId) {
        demoAccountGuard.assertWritable(accountId);
        validateAccount(accountId);
        List<PowerplantRuleEntity> rules = powerplantRuleRepository.findByAccountIdOrderByIdAsc(accountId);
        rules.forEach(this::rearmRule);
        return rules.size();
    }

    @Transactional
    public void deleteRule(Long accountId, Long ruleId) {
        demoAccountGuard.assertWritable(accountId);
        PowerplantRuleEntity entity = powerplantRuleRepository.findByIdAndAccountId(ruleId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Powerplant rule not found: " + ruleId));
        powerplantRuleRepository.delete(entity);
    }

    @Transactional
    public void updateRuleControlPoint(Long accountId, Long ruleId, int controlPointX, int controlPointY) {
        demoAccountGuard.assertWritable(accountId);
        PowerplantRuleEntity entity = powerplantRuleRepository.findByIdAndAccountId(ruleId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Powerplant rule not found: " + ruleId));
        entity.setControlPointX(Math.max(0, controlPointX));
        entity.setControlPointY(Math.max(0, controlPointY));
        powerplantRuleRepository.save(entity);
    }

    @Transactional
    public int evaluateEnabledRules() {
        Instant now = Instant.now();
        int sent = 0;
        for (PowerplantRuleEntity rule : powerplantRuleRepository.findByEnabledTrueOrderByIdAsc()) {
            if (evaluateRule(rule, now)) {
                sent++;
            }
        }
        return sent;
    }

    @Transactional(readOnly = true)
    public void sendDeviceControl(Long accountId, Long elementId, boolean on) {
        demoAccountGuard.assertWritable(accountId);
        PowerplantElementEntity entity = powerplantElementRepository.findByIdAndAccountId(elementId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Powerplant element not found: " + elementId));
        if (entity.getElementType() != PowerplantElementType.DEVICE_CONTROL || entity.getDevice() == null) {
            throw new IllegalArgumentException("Element is not a device control");
        }
        validateStandardRelayDevice(entity.getDevice());
        validateChannel(entity.getDeviceChannel());
        controlService.sendDebugMqttRelayCommand(
                accountId,
                entity.getDevice().getId(),
                entity.getDeviceChannel(),
                on
        );
    }

    private AccountEntity validateAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));
    }

    private DeviceEntity validateAccountDevice(Long accountId, Long deviceId) {
        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + deviceId));
        if (device.getAccount() == null || !device.getAccount().getId().equals(accountId)) {
            throw new IllegalStateException("Forbidden!");
        }
        return device;
    }

    private void validateStandardRelayDevice(DeviceEntity device) {
        if (device.getDeviceType() != DeviceType.STANDARD) {
            throw new IllegalArgumentException("Only standard relay devices are supported");
        }
    }

    private void validateChannel(Integer channel) {
        if (channel == null || channel < MIN_CHANNEL || channel > MAX_CHANNEL) {
            throw new IllegalArgumentException("Unsupported relay channel: " + channel);
        }
    }

    private void validateRuleEndpoints(PowerplantElementEntity source, PowerplantElementEntity target) {
        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Source and target cannot be the same element");
        }
        if (source.getElementType() != PowerplantElementType.INDICATOR
                || source.getDevice() == null
                || source.getMeasurementType() == null
                || source.getMeasurementKey() == null) {
            throw new IllegalArgumentException("Rule source must be an indicator linked to a reported measurement");
        }
        if (target.getElementType() != PowerplantElementType.DEVICE_CONTROL || target.getDevice() == null) {
            throw new IllegalArgumentException("Rule target must be a device control element");
        }
        validateStandardRelayDevice(target.getDevice());
        validateChannel(target.getDeviceChannel());
    }

    private boolean evaluateRule(PowerplantRuleEntity rule, Instant now) {
        rule.setLastEvaluatedAt(now);
        try {
            validateRuleEndpoints(rule.getSourceElement(), rule.getTargetElement());
            Optional<ZigbeeDeviceMeasurementEntity> measurement = latestMeasurement(rule.getSourceElement());
            if (measurement.isEmpty()) {
                markSkipped(rule, "Source measurement is missing");
                return false;
            }
            ZigbeeDeviceMeasurementEntity latest = measurement.get();
            if (latest.getMeasuredAt().isBefore(now.minus(MEASUREMENT_FRESHNESS))) {
                markSkipped(rule, "Source measurement is stale");
                return false;
            }

            boolean previouslyMatched = Boolean.TRUE.equals(rule.getLastConditionMatched());
            boolean matched = conditionMatches(rule, latest.getValue(), previouslyMatched);
            if (!matched) {
                rule.setLastConditionMatched(false);
                rule.setLastSkipReason("Condition does not match");
                return false;
            }
            if (previouslyMatched) {
                rule.setLastSkipReason("Condition already matched");
                return false;
            }
            if (rule.getLastCommandSentAt() != null
                    && rule.getCooldownSeconds() != null
                    && rule.getLastCommandSentAt().plusSeconds(rule.getCooldownSeconds()).isAfter(now)) {
                markSkipped(rule, "Cooldown is active");
                return false;
            }

            PowerplantElementEntity target = rule.getTargetElement();
            controlService.sendDebugMqttRelayCommand(
                    rule.getAccount().getId(),
                    target.getDevice().getId(),
                    target.getDeviceChannel(),
                    rule.getTargetAction() == ControlAction.TURN_ON
            );
            rule.setLastConditionMatched(true);
            rule.setLastCommandSentAt(now);
            rule.setLastSkipReason(null);
            return true;
        } catch (Exception ex) {
            markSkipped(rule, ex.getMessage());
            return false;
        }
    }

    private void markSkipped(PowerplantRuleEntity rule, String reason) {
        rule.setLastSkipReason(reason != null && reason.length() > 256 ? reason.substring(0, 256) : reason);
    }

    private void rearmRulesForElement(PowerplantElementEntity element) {
        powerplantRuleRepository.findBySourceElement(element).forEach(this::rearmRule);
        powerplantRuleRepository.findByTargetElement(element).forEach(this::rearmRule);
    }

    private void rearmRule(PowerplantRuleEntity rule) {
        rule.setLastConditionMatched(null);
        rule.setLastSkipReason(null);
    }

    private boolean ruleEndpointChanged(
            PowerplantElementEntity current,
            PowerplantElementType newType,
            DeviceEntity newDevice,
            Integer newDeviceChannel,
            ZigbeeMeasurementType newMeasurementType,
            String newMeasurementKey
    ) {
        Long currentDeviceId = current.getDevice() != null ? current.getDevice().getId() : null;
        Long newDeviceId = newDevice != null ? newDevice.getId() : null;
        return current.getElementType() != newType
                || !Objects.equals(currentDeviceId, newDeviceId)
                || !Objects.equals(current.getDeviceChannel(), newDeviceChannel)
                || current.getMeasurementType() != newMeasurementType
                || !Objects.equals(current.getMeasurementKey(), newMeasurementKey);
    }

    private boolean conditionMatches(PowerplantRuleEntity rule, BigDecimal value, boolean previouslyMatched) {
        BigDecimal threshold = rule.getThresholdValue();
        if (value == null || threshold == null) {
            return false;
        }
        BigDecimal effectiveThreshold = applyHysteresis(rule, previouslyMatched);
        int comparison = value.compareTo(effectiveThreshold);
        return switch (rule.getComparisonType()) {
            case LESS_THAN -> comparison < 0;
            case LESS_THAN_OR_EQUAL -> comparison <= 0;
            case GREATER_THAN -> comparison > 0;
            case GREATER_THAN_OR_EQUAL -> comparison >= 0;
            case EQUAL -> comparison == 0;
        };
    }

    private BigDecimal applyHysteresis(PowerplantRuleEntity rule, boolean previouslyMatched) {
        BigDecimal threshold = rule.getThresholdValue();
        BigDecimal hysteresis = rule.getHysteresisValue();
        if (hysteresis == null || !previouslyMatched) {
            return threshold;
        }
        return switch (rule.getComparisonType()) {
            case LESS_THAN, LESS_THAN_OR_EQUAL -> threshold.add(hysteresis);
            case GREATER_THAN, GREATER_THAN_OR_EQUAL -> threshold.subtract(hysteresis);
            case EQUAL -> threshold;
        };
    }

    private PowerplantElementResponse mapElement(PowerplantElementEntity entity) {
        Optional<ZigbeeDeviceMeasurementEntity> latestMeasurement = latestMeasurement(entity);
        Instant now = Instant.now();
        return PowerplantElementResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .elementType(entity.getElementType())
                .iconName(entity.getIconName())
                .displayValue(entity.getDisplayValue())
                .displayUnit(entity.getDisplayUnit())
                .device(entity.getDevice() != null ? mapDevice(entity.getDevice()) : null)
                .deviceChannel(entity.getDeviceChannel())
                .measurementType(entity.getMeasurementType())
                .measurementKey(entity.getMeasurementKey())
                .latestMeasurementValue(latestMeasurement.map(ZigbeeDeviceMeasurementEntity::getValue).orElse(null))
                .latestMeasuredAt(latestMeasurement.map(ZigbeeDeviceMeasurementEntity::getMeasuredAt).orElse(null))
                .latestReceivedAt(latestMeasurement.map(ZigbeeDeviceMeasurementEntity::getReceivedAt).orElse(null))
                .latestMeasurementFresh(latestMeasurement
                        .map(measurement -> !measurement.getMeasuredAt().isBefore(now.minus(MEASUREMENT_FRESHNESS)))
                        .orElse(false))
                .canvasX(entity.getCanvasX())
                .canvasY(entity.getCanvasY())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private PowerplantRuleResponse mapRule(PowerplantRuleEntity entity) {
        return PowerplantRuleResponse.builder()
                .id(entity.getId())
                .sourceElement(mapElement(entity.getSourceElement()))
                .targetElement(mapElement(entity.getTargetElement()))
                .comparisonType(entity.getComparisonType())
                .thresholdValue(entity.getThresholdValue())
                .hysteresisValue(entity.getHysteresisValue())
                .targetAction(entity.getTargetAction())
                .enabled(entity.isEnabled())
                .cooldownSeconds(entity.getCooldownSeconds())
                .lastConditionMatched(entity.getLastConditionMatched())
                .lastCommandSentAt(entity.getLastCommandSentAt())
                .lastEvaluatedAt(entity.getLastEvaluatedAt())
                .lastSkipReason(entity.getLastSkipReason())
                .controlPointX(entity.getControlPointX())
                .controlPointY(entity.getControlPointY())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private Optional<ZigbeeDeviceMeasurementEntity> latestMeasurement(PowerplantElementEntity entity) {
        if (entity.getElementType() != PowerplantElementType.INDICATOR
                || entity.getDevice() == null
                || entity.getMeasurementType() == null
                || entity.getMeasurementKey() == null
                || entity.getMeasurementKey().isBlank()) {
            return Optional.empty();
        }
        return measurementRepository.findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
                entity.getDevice().getId(),
                entity.getMeasurementType(),
                entity.getMeasurementKey()
        );
    }

    private void validateMeasurementBinding(
            Long accountId,
            Long deviceId,
            ZigbeeMeasurementType measurementType,
            String measurementKey
    ) {
        boolean exists = measurementRepository
                .findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
                        deviceId,
                        measurementType,
                        measurementKey
                )
                .filter(measurement -> measurement.getAccount().getId().equals(accountId))
                .isPresent();
        if (!exists) {
            throw new IllegalArgumentException("Selected measurement has not been reported by this account device");
        }
    }

    private PowerplantMeasurementOptionResponse mapMeasurementOption(ZigbeeDeviceMeasurementEntity measurement) {
        return PowerplantMeasurementOptionResponse.builder()
                .device(mapDevice(measurement.getDevice()))
                .measurementType(measurement.getMeasurementType())
                .measurementKey(measurement.getMeasurementKey())
                .zigbeeIeee(measurement.getZigbeeIeee())
                .profile(measurement.getProfile())
                .value(measurement.getValue())
                .measuredAt(measurement.getMeasuredAt())
                .receivedAt(measurement.getReceivedAt())
                .build();
    }

    private String defaultMeasurementKey(ZigbeeMeasurementType type) {
        return switch (type) {
            case TEMPERATURE -> "temperature";
            case HUMIDITY -> "humidity";
            case BATTERY_PERCENTAGE -> "batteryPercentage";
            case THERMOSTAT_SETPOINT -> "setpoint";
        };
    }

    private PowerplantSettingsResponse mapSettings(PowerplantSettingsEntity entity) {
        return PowerplantSettingsResponse.builder()
                .boardWidth(entity.getBoardWidth())
                .boardHeight(entity.getBoardHeight())
                .build();
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private DeviceResponse mapDevice(DeviceEntity device) {
        return DeviceResponse.builder()
                .id(device.getId())
                .uuid(device.getUuid())
                .deviceType(device.getDeviceType())
                .enabled(device.isEnabled())
                .deviceName(device.getDeviceName())
                .timezone(device.getTimezone())
                .lastCommunication(device.getLastCommunication())
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .accountId(device.getAccount().getId())
                .apiOnline(device.isApiOnline())
                .mqttOnline(device.isMqttOnline())
                .mqttUsername(device.getMqttUsername())
                .mqttPassword(device.getMqttPassword())
                .build();
    }

}
