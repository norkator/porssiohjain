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
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerGatewayCommandService;
import com.nitramite.porssiohjain.services.models.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service

@RequiredArgsConstructor
@Transactional
public class ZigbeeGatewaySyncService {
    public static final int POLL_SECONDS = 300;
    private static final String THERMOSTAT_PROFILE = "schneider_wde002497";
    private final AccountRepository accountRepository;
    private final DeviceRepository deviceRepository;
    private final ZigbeeGatewayDeviceRepository zigbeeRepository;
    private final ZigbeeDeviceMeasurementRepository measurementRepository;
    private final ZigbeeGatewayConnectivityService connectivityService;
    private final DeviceOfflineNotificationService deviceOfflineNotificationService;
    private final HeatingPlannerGatewayCommandService heatingPlannerGatewayCommandService;

    public ZigbeeGatewaySyncResponse sync(Long accountId, UUID pathGatewayId, ZigbeeGatewaySyncRequest request) {
        if (request == null || request.getGatewayId() == null || !pathGatewayId.equals(request.getGatewayId())) {
            throw badRequest("Path and body gatewayId must match");
        }
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Instant now = Instant.now();
        connectivityService.recordHeartbeat(account, pathGatewayId, now);
        List<ZigbeeGatewaySyncResponse.DeviceCommand> commands = new ArrayList<>();
        for (ZigbeeGatewaySyncRequest.DeviceReport report : Optional.ofNullable(request.getDevices()).orElse(List.of())) {
            String ieee = normalizeIeee(report.getZigbeeIeee());
            String profile = normalizeProfile(report.getProfile());
            if (!isSupportedProfile(profile, report)) throw badRequest("Unsupported Zigbee profile");
            ZigbeeGatewayDeviceEntity link = zigbeeRepository.findByGatewayIdAndZigbeeIeee(pathGatewayId, ieee)
                    .map(existing -> requireOwner(existing, accountId))
                    .orElseGet(() -> register(account, pathGatewayId, ieee, report));
            updateReport(link, report, now);
            saveMeasurements(link, report, now);
            applyHeatingPlannerPriority(link, now);
            if (isThermostatProfile(link.getProfile())
                    && link.getDesiredVersion() > link.getAppliedVersion()
                    && link.getDesiredExpiresAt() != null && link.getDesiredExpiresAt().isAfter(now)) {
                commands.add(ZigbeeGatewaySyncResponse.DeviceCommand.builder()
                        .zigbeeIeee(ieee).version(link.getDesiredVersion())
                        .targetTemperature(link.getDesiredTemperature()).mode(link.getDesiredMode())
                        .expiresAt(link.getDesiredExpiresAt()).build());
            }
        }
        return ZigbeeGatewaySyncResponse.builder().pollAfterSeconds(POLL_SECONDS).devices(commands).build();
    }

    private void applyHeatingPlannerPriority(ZigbeeGatewayDeviceEntity link, Instant now) {
        if (!isThermostatProfile(link.getProfile())) {
            return;
        }
        heatingPlannerGatewayCommandService.currentCommand(link, now).ifPresent(command -> {
            boolean changed = link.getDesiredTemperature() == null
                    || command.targetTemperature().compareTo(link.getDesiredTemperature()) != 0
                    || !command.mode().equals(link.getDesiredMode());
            if (changed) {
                link.setDesiredVersion(link.getDesiredVersion() + 1);
                link.setDesiredTemperature(command.targetTemperature());
                link.setDesiredMode(command.mode());
                link.setDesiredAt(now);
                link.setLastError(null);
            }
            link.setDesiredExpiresAt(now.plus(Duration.ofMinutes(30)));
            zigbeeRepository.save(link);
        });
    }

    private ZigbeeGatewayDeviceEntity register(AccountEntity account, UUID gatewayId, String ieee,
            ZigbeeGatewaySyncRequest.DeviceReport report) {
        String name = cleanName(report.getCustomName());
        String profile = normalizeProfile(report.getProfile());
        boolean thermostat = isThermostatProfile(profile);
        DeviceEntity device = DeviceEntity.builder()
                .deviceType(thermostat ? DeviceType.THERMOSTAT : DeviceType.TEMPERATURE_SENSOR)
                .devicePlatform(DevicePlatform.ANDROID_ZIGBEE)
                .mqttDeviceProfile(thermostat ? MqttDeviceProfile.GENERIC_THERMOSTAT : MqttDeviceProfile.GENERIC_RELAY)
                .enabled(true)
                .deviceName(name.isBlank()
                        ? (thermostat ? "Zigbee thermostat " : "Zigbee temperature sensor ") + ieee.substring(8)
                        : name)
                .timezone("Europe/Helsinki").account(account).build();
        device = deviceRepository.save(device);
        return zigbeeRepository.save(ZigbeeGatewayDeviceEntity.builder()
                .account(account).device(device).gatewayId(gatewayId).zigbeeIeee(ieee)
                .profile(profile).customName(name).build());
    }

    private void updateReport(ZigbeeGatewayDeviceEntity link, ZigbeeGatewaySyncRequest.DeviceReport report, Instant now) {
        if (isThermostatProfile(link.getProfile())) {
            if (report.getLastAppliedVersion() < link.getAppliedVersion()
                    || report.getLastAppliedVersion() > link.getDesiredVersion()) {
                throw badRequest("Invalid applied Zigbee command version");
            }
        } else if (report.getLastAppliedVersion() != 0) {
            throw badRequest("Sensor reports must not acknowledge thermostat command versions");
        }
        link.setCustomName(cleanName(report.getCustomName()));
        link.setLastSeen(now);
        link.setReportedTemperature(report.getTemperature());
        link.setReportedSetpoint(report.getSetpoint());
        link.setReportedMode(normalizeMode(report.getMode(), true));
        if (isThermostatProfile(link.getProfile())
                && Boolean.TRUE.equals(report.getSuccess()) && report.getLastAppliedVersion() > link.getAppliedVersion()) {
            link.setAppliedVersion(report.getLastAppliedVersion());
            link.setLastError(null);
        } else if (Boolean.FALSE.equals(report.getSuccess())) {
            link.setLastError(truncate(report.getError(), 512));
        }
        if (isThermostatProfile(link.getProfile())
                && reportedStateDrifted(link) && link.getAppliedVersion() >= link.getDesiredVersion()) {
            link.setDesiredVersion(link.getDesiredVersion() + 1);
            link.setDesiredAt(now);
            link.setLastError("Reported thermostat state drifted from desired cloud state");
        }
        zigbeeRepository.save(link);

        DeviceEntity device = link.getDevice();
        boolean wasApiOnline = device.isApiOnline();
        boolean wasMqttOnline = device.isMqttOnline();
        device.setLastCommunication(now);
        device.setApiOnline(true);
        deviceRepository.save(device);
        deviceOfflineNotificationService.sendIfDeviceCameOnline(
                device,
                wasApiOnline,
                wasMqttOnline,
                "API",
                now
        );
    }

    private void saveMeasurements(ZigbeeGatewayDeviceEntity link, ZigbeeGatewaySyncRequest.DeviceReport report, Instant now) {
        Instant measuredAt = report.getMeasuredAt() == null ? now : report.getMeasuredAt();
        saveMeasurement(link, ZigbeeMeasurementType.TEMPERATURE, "temperature", report.getTemperature(), measuredAt, now);
        saveMeasurement(link, ZigbeeMeasurementType.HUMIDITY, "humidity", report.getHumidity(), measuredAt, now);
        saveMeasurement(link, ZigbeeMeasurementType.BATTERY_PERCENTAGE, "batteryPercentage", report.getBatteryPercentage(), measuredAt, now);
        if (isThermostatProfile(link.getProfile())) {
            saveMeasurement(link, ZigbeeMeasurementType.THERMOSTAT_SETPOINT, "setpoint", report.getSetpoint(), measuredAt, now);
        }
    }

    private void saveMeasurement(ZigbeeGatewayDeviceEntity link, ZigbeeMeasurementType type, String key,
                                 BigDecimal value, Instant measuredAt, Instant receivedAt) {
        if (value == null) {
            return;
        }
        measurementRepository.save(ZigbeeDeviceMeasurementEntity.builder()
                .account(link.getAccount())
                .device(link.getDevice())
                .gatewayId(link.getGatewayId())
                .zigbeeIeee(link.getZigbeeIeee())
                .profile(link.getProfile())
                .measurementType(type)
                .measurementKey(key)
                .value(value)
                .measuredAt(measuredAt)
                .receivedAt(receivedAt)
                .build());
    }

    private boolean reportedStateDrifted(ZigbeeGatewayDeviceEntity link) {
        if (link.getDesiredVersion() <= 0 || link.getDesiredTemperature() == null || link.getDesiredMode() == null) {
            return false;
        }
        boolean setpointDrifted = link.getReportedSetpoint() != null
                && link.getReportedSetpoint().compareTo(link.getDesiredTemperature()) != 0;
        boolean modeDrifted = link.getReportedMode() != null && !link.getReportedMode().equals(link.getDesiredMode());
        return setpointDrifted || modeDrifted;
    }

    private ZigbeeGatewayDeviceEntity requireOwner(ZigbeeGatewayDeviceEntity link, Long accountId) {
        if (!Objects.equals(link.getAccount().getId(), accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return link;
    }

    public static String normalizeIeee(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("0x")) normalized = normalized.substring(2);
        if (!normalized.matches("[0-9a-f]{16}")) throw badRequest("Invalid Zigbee IEEE address");
        return normalized;
    }

    private static String normalizeProfile(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isSupportedProfile(String profile, ZigbeeGatewaySyncRequest.DeviceReport report) {
        return isThermostatProfile(profile) || hasSensorMeasurement(report);
    }

    private static boolean isThermostatProfile(String profile) {
        return THERMOSTAT_PROFILE.equals(profile);
    }

    private static boolean hasSensorMeasurement(ZigbeeGatewaySyncRequest.DeviceReport report) {
        return report.getTemperature() != null || report.getHumidity() != null || report.getBatteryPercentage() != null;
    }

    static String normalizeMode(String value, boolean nullable) {
        if (nullable && (value == null || value.isBlank())) return null;
        String mode = value == null ? "" : value.toUpperCase(Locale.ROOT);
        if (!mode.equals("HEAT") && !mode.equals("OFF")) throw badRequest("Mode must be HEAT or OFF");
        return mode;
    }

    private static String cleanName(String value) { return truncate(value == null ? "" : value.trim(), 64); }
    private static String truncate(String value, int length) { return value == null ? null : value.substring(0, Math.min(length, value.length())); }
    private static ResponseStatusException badRequest(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
}
