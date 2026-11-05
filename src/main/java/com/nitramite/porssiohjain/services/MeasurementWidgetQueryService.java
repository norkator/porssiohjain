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

import com.nitramite.porssiohjain.entity.DeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Read-only native widgets use exactly the Measurements view's common Zigbee/integration store. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeasurementWidgetQueryService {
    private final DeviceMeasurementService measurements;
    private final DeviceRepository devices;

    public List<Reading> latest(Long accountId) {
        requireAccount(accountId);
        return measurements.latestForAccount(accountId, Instant.EPOCH).stream()
                // A retained measurement must not grant access after its device changes owner.
                .filter(row -> row.getDevice() != null && row.getDevice().getAccount() != null
                        && Objects.equals(row.getDevice().getAccount().getId(), accountId))
                .map(this::reading).toList();
    }

    public History history(Long accountId, Long deviceId, DeviceMeasurementType type, String key, int hours, Instant now) {
        requireAccount(accountId);
        if (deviceId == null || deviceId <= 0 || type == null || key == null || key.isBlank() || key.length() > 64
                || (hours != 24 && hours != 48)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid measurement or history period");
        }
        devices.findWithAccountById(deviceId)
                .filter(device -> device.getAccount() != null && Objects.equals(device.getAccount().getId(), accountId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "Measurement unavailable"));
        Reading latest = latest(accountId).stream().filter(row -> row.deviceId().equals(deviceId)
                && row.measurementType() == type && row.measurementKey().equals(key)).findFirst().orElse(null);
        Instant from = now.minus(Duration.ofHours(hours));
        // This complete [from,to) window deliberately does not use the private dialog's 500-row limit.
        List<Point> points = measurements.accountHistory(accountId, deviceId, type, key, from, now).stream()
                .map(row -> new Point(row.getObservedAt(), row.getValue())).toList();
        return new History(deviceId, type, key, from, now, latest, points);
    }

    private void requireAccount(Long accountId) {
        if (accountId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    private Reading reading(DeviceMeasurementEntity row) {
        return new Reading(row.getDevice().getId(), row.getDevice().getDeviceName(), row.getMeasurementType(),
                row.getMeasurementKey(), row.getSource() == null ? null : row.getSource().name(), row.getSourceKey(),
                row.getUnit(), row.getValue(), row.getObservedAt(), row.getMeasuredAt(), row.getReceivedAt());
    }

    // Only these explicit fields leave the service: no entities, tokens, network keys or device credentials.
    public record Reading(Long deviceId, String deviceName, DeviceMeasurementType measurementType, String measurementKey,
                          String source, String sourceKey, String unit, BigDecimal value, Instant observedAt,
                          Instant measuredAt, Instant receivedAt) { }
    public record Point(Instant observedAt, BigDecimal value) { }
    public record History(Long deviceId, DeviceMeasurementType measurementType, String measurementKey,
                          Instant from, Instant to, Reading latest, List<Point> points) { }
}
