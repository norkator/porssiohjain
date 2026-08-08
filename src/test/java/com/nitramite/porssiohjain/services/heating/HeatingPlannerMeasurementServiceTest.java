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

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerRoomEntity;
import com.nitramite.porssiohjain.entity.ZigbeeDeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
import com.nitramite.porssiohjain.entity.repository.ZigbeeDeviceMeasurementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeatingPlannerMeasurementServiceTest {

    @Mock ZigbeeDeviceMeasurementRepository measurements;

    @Test void returnsFreshLatestRoomTemperature() {
        HeatingPlannerMeasurementService service = new HeatingPlannerMeasurementService(measurements);
        HeatingPlannerRoomEntity room = room(14L);
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        when(measurements.findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
                14L, ZigbeeMeasurementType.TEMPERATURE, "temperature"))
                .thenReturn(Optional.of(ZigbeeDeviceMeasurementEntity.builder()
                        .value(new BigDecimal("21.50"))
                        .measuredAt(now.minusSeconds(120))
                        .build()));

        HeatingPlannerMeasurementService.LatestMeasurement latest =
                service.latestFreshRoomTemperature(room, now);

        assertEquals(new BigDecimal("21.50"), latest.value());
        assertEquals(HeatingPlannerMeasurementService.Freshness.FRESH, latest.freshness());
        assertTrue(latest.fresh());
    }

    @Test void marksOldRoomTemperatureStale() {
        HeatingPlannerMeasurementService service = new HeatingPlannerMeasurementService(measurements);
        HeatingPlannerRoomEntity room = room(14L);
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        when(measurements.findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
                14L, ZigbeeMeasurementType.TEMPERATURE, "temperature"))
                .thenReturn(Optional.of(ZigbeeDeviceMeasurementEntity.builder()
                        .value(new BigDecimal("20.00"))
                        .measuredAt(now.minusSeconds(7200))
                        .build()));

        HeatingPlannerMeasurementService.LatestMeasurement latest =
                service.latestFreshRoomTemperature(room, now);

        assertEquals(HeatingPlannerMeasurementService.Freshness.STALE, latest.freshness());
        assertFalse(latest.fresh());
    }

    @Test void returnsMissingWhenRoomHasNoSensor() {
        HeatingPlannerMeasurementService service = new HeatingPlannerMeasurementService(measurements);

        HeatingPlannerMeasurementService.LatestMeasurement latest =
                service.latestFreshRoomTemperature(new HeatingPlannerRoomEntity(), Instant.now());

        assertEquals(HeatingPlannerMeasurementService.Freshness.MISSING, latest.freshness());
        assertNull(latest.value());
    }

    private HeatingPlannerRoomEntity room(Long deviceId) {
        DeviceEntity device = new DeviceEntity();
        device.setId(deviceId);
        HeatingPlannerRoomEntity room = new HeatingPlannerRoomEntity();
        room.setRoomSensorDevice(device);
        return room;
    }
}
