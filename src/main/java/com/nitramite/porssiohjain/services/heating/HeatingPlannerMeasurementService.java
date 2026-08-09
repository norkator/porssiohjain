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
import com.nitramite.porssiohjain.entity.ZigbeeDeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
import com.nitramite.porssiohjain.entity.repository.ZigbeeDeviceMeasurementRepository;
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

    private final ZigbeeDeviceMeasurementRepository measurementRepository;

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
        Optional<ZigbeeDeviceMeasurementEntity> measurement =
                measurementRepository.findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
                        device.getId(), ZigbeeMeasurementType.TEMPERATURE, measurementKey);
        if (measurement.isEmpty()) {
            return LatestMeasurement.missing();
        }
        ZigbeeDeviceMeasurementEntity entity = measurement.get();
        boolean fresh = !entity.getMeasuredAt().isBefore(now.minus(DEFAULT_FRESHNESS));
        return new LatestMeasurement(entity.getValue(), entity.getMeasuredAt(), fresh ? Freshness.FRESH : Freshness.STALE);
    }

    public enum Freshness {
        FRESH,
        STALE,
        MISSING
    }

    public record LatestMeasurement(
            BigDecimal value,
            Instant measuredAt,
            Freshness freshness
    ) {
        public static LatestMeasurement missing() {
            return new LatestMeasurement(null, null, Freshness.MISSING);
        }

        public boolean fresh() {
            return freshness == Freshness.FRESH;
        }
    }
}
