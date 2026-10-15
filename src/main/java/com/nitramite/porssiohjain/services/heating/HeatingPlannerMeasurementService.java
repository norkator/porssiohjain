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

package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.HeatingPlannerRoomEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerRoomHeatSourceEntity;
import com.nitramite.porssiohjain.entity.DeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import com.nitramite.porssiohjain.services.DeviceMeasurementService;
import com.nitramite.porssiohjain.services.tuya.TuyaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HeatingPlannerMeasurementService {

    public static final Duration DEFAULT_FRESHNESS = Duration.ofMinutes(60);
    public static final String DEFAULT_TEMPERATURE_KEY = "temperature";

    private final DeviceMeasurementService measurements;

    @Transactional(readOnly = true)
    public LatestMeasurement latestFreshRoomTemperature(HeatingPlannerRoomEntity room, Instant now) {
        return latestFreshTemperature(room, now, false);
    }

    @Transactional(readOnly = true)
    public LatestMeasurement latestFreshFloorTemperature(HeatingPlannerRoomEntity room, Instant now) {
        return latestFreshTemperature(room, now, true);
    }

    private LatestMeasurement latestFreshTemperature(HeatingPlannerRoomEntity room, Instant now, boolean floor) {
        if (room == null || now == null) {
            return LatestMeasurement.missing();
        }
        var device = floor ? room.getFloorSensorDevice() : room.getRoomSensorDevice();
        if (!floor && device == null) {
            device = room.getHeatSources().stream()
                    .filter(HeatingPlannerRoomHeatSourceEntity::isEnabled)
                    .map(HeatingPlannerRoomHeatSourceEntity::getControllingDevice)
                    .filter(candidate -> candidate != null && candidate.getDeviceType() == com.nitramite.porssiohjain.entity.enums.DeviceType.THERMOSTAT)
                    .findFirst()
                    .orElse(null);
        }
        String key = floor ? room.getFloorSensorMeasurementKey() : room.getRoomSensorMeasurementKey();
        if (device == null) {
            return LatestMeasurement.missing();
        }
        String measurementKey = key == null || key.isBlank() ? DEFAULT_TEMPERATURE_KEY : key;
        Optional<DeviceMeasurementEntity> measurement = measurements.latest(
                device, DeviceMeasurementType.TEMPERATURE, measurementKey);
        if (measurement.isEmpty()) {
            return LatestMeasurement.missing();
        }
        DeviceMeasurementEntity entity = measurement.get();
        boolean cloudSnapshot = entity.getMeasuredAt() == null;
        Duration freshness = cloudSnapshot ? TuyaService.POLL_FRESHNESS : DEFAULT_FRESHNESS;
        boolean fresh = entity.getObservedAt() != null && !entity.getObservedAt().isAfter(now)
                && !entity.getObservedAt().isBefore(now.minus(freshness));
        if (device.getDevicePlatform() == DevicePlatform.TUYA_SMART) {
            fresh &= device.isEnabled() && device.isApiOnline() && device.getLastCommunication() != null
                    && !device.getLastCommunication().isAfter(now)
                    && !device.getLastCommunication().isBefore(now.minus(TuyaService.POLL_FRESHNESS));
        }
        return new LatestMeasurement(entity.getValue(), entity.getMeasuredAt(),
                fresh ? Freshness.FRESH : Freshness.STALE, entity.getObservedAt());
    }

    public enum Freshness {
        FRESH,
        STALE,
        MISSING
    }

    public record LatestMeasurement(
            BigDecimal value,
            Instant measuredAt,
            Freshness freshness,
            Instant observedAt
    ) {
        public LatestMeasurement(BigDecimal value, Instant measuredAt, Freshness freshness) {
            this(value, measuredAt, freshness, measuredAt);
        }

        public boolean cloudSnapshot() {
            return value != null && measuredAt == null;
        }

        public static LatestMeasurement missing() {
            return new LatestMeasurement(null, null, Freshness.MISSING);
        }

        public boolean fresh() {
            return freshness == Freshness.FRESH;
        }
    }
}
