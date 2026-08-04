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
import com.nitramite.porssiohjain.services.models.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ZigbeeGatewaySyncService {
    public static final int POLL_SECONDS = 300;
    private static final String SUPPORTED_PROFILE = "schneider_wde002497";
    private final AccountRepository accountRepository;
    private final DeviceRepository deviceRepository;
    private final ZigbeeGatewayDeviceRepository zigbeeRepository;

    public ZigbeeGatewaySyncResponse sync(Long accountId, UUID pathGatewayId, ZigbeeGatewaySyncRequest request) {
        if (request == null || request.getGatewayId() == null || !pathGatewayId.equals(request.getGatewayId())) {
            throw badRequest("Path and body gatewayId must match");
        }
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Instant now = Instant.now();
        List<ZigbeeGatewaySyncResponse.DeviceCommand> commands = new ArrayList<>();
        for (ZigbeeGatewaySyncRequest.DeviceReport report : Optional.ofNullable(request.getDevices()).orElse(List.of())) {
            String ieee = normalizeIeee(report.getZigbeeIeee());
            if (!SUPPORTED_PROFILE.equals(report.getProfile())) throw badRequest("Unsupported Zigbee thermostat profile");
            ZigbeeGatewayDeviceEntity link = zigbeeRepository.findByGatewayIdAndZigbeeIeee(pathGatewayId, ieee)
                    .map(existing -> requireOwner(existing, accountId))
                    .orElseGet(() -> register(account, pathGatewayId, ieee, report));
            updateReport(link, report, now);
            if (link.getDesiredVersion() > link.getAppliedVersion()
                    && link.getDesiredExpiresAt() != null && link.getDesiredExpiresAt().isAfter(now)) {
                commands.add(ZigbeeGatewaySyncResponse.DeviceCommand.builder()
                        .zigbeeIeee(ieee).version(link.getDesiredVersion())
                        .targetTemperature(link.getDesiredTemperature()).mode(link.getDesiredMode())
                        .expiresAt(link.getDesiredExpiresAt()).build());
            }
        }
        return ZigbeeGatewaySyncResponse.builder().pollAfterSeconds(POLL_SECONDS).devices(commands).build();
    }

    private ZigbeeGatewayDeviceEntity register(AccountEntity account, UUID gatewayId, String ieee,
            ZigbeeGatewaySyncRequest.DeviceReport report) {
        String name = cleanName(report.getCustomName());
        DeviceEntity device = DeviceEntity.builder()
                .deviceType(DeviceType.THERMOSTAT).devicePlatform(DevicePlatform.ANDROID_ZIGBEE)
                .mqttDeviceProfile(MqttDeviceProfile.GENERIC_THERMOSTAT).enabled(true)
                .deviceName(name.isBlank() ? "Zigbee thermostat " + ieee.substring(8) : name)
                .timezone("Europe/Helsinki").account(account).build();
        device = deviceRepository.save(device);
        return zigbeeRepository.save(ZigbeeGatewayDeviceEntity.builder()
                .account(account).device(device).gatewayId(gatewayId).zigbeeIeee(ieee)
                .profile(report.getProfile()).customName(name).build());
    }

    private void updateReport(ZigbeeGatewayDeviceEntity link, ZigbeeGatewaySyncRequest.DeviceReport report, Instant now) {
        if (report.getLastAppliedVersion() < link.getAppliedVersion()
                || report.getLastAppliedVersion() > link.getDesiredVersion()) {
            throw badRequest("Invalid applied Zigbee command version");
        }
        link.setCustomName(cleanName(report.getCustomName()));
        link.setLastSeen(now);
        link.setReportedTemperature(report.getTemperature());
        link.setReportedSetpoint(report.getSetpoint());
        link.setReportedMode(normalizeMode(report.getMode(), true));
        if (Boolean.TRUE.equals(report.getSuccess()) && report.getLastAppliedVersion() > link.getAppliedVersion()) {
            link.setAppliedVersion(report.getLastAppliedVersion());
            link.setLastError(null);
        } else if (Boolean.FALSE.equals(report.getSuccess())) {
            link.setLastError(truncate(report.getError(), 512));
        }
        zigbeeRepository.save(link);
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
