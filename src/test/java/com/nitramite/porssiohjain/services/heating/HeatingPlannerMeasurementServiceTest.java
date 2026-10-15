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
import com.nitramite.porssiohjain.entity.HeatingPlannerRoomHeatSourceEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.DeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import com.nitramite.porssiohjain.services.DeviceMeasurementService;
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

    @Mock DeviceMeasurementService measurements;

    @Test void returnsFreshLatestRoomTemperature() {
        HeatingPlannerMeasurementService service = new HeatingPlannerMeasurementService(measurements);
        HeatingPlannerRoomEntity room = room(14L);
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        when(measurements.latest(
                room.getRoomSensorDevice(), DeviceMeasurementType.TEMPERATURE, "temperature"))
                .thenReturn(Optional.of(DeviceMeasurementEntity.builder()
                        .value(new BigDecimal("21.50"))
                        .measuredAt(now.minusSeconds(120)).observedAt(now.minusSeconds(120))
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
        when(measurements.latest(
                room.getRoomSensorDevice(), DeviceMeasurementType.TEMPERATURE, "temperature"))
                .thenReturn(Optional.of(DeviceMeasurementEntity.builder()
                        .value(new BigDecimal("20.00"))
                        .measuredAt(now.minusSeconds(7200)).observedAt(now.minusSeconds(7200))
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

    @Test void usesControllingThermostatActualTemperatureWhenRoomSensorIsNotConfigured() {
        HeatingPlannerMeasurementService service = new HeatingPlannerMeasurementService(measurements);
        DeviceEntity thermostat = new DeviceEntity();
        thermostat.setId(22L);
        thermostat.setDeviceType(DeviceType.THERMOSTAT);
        HeatingPlannerRoomEntity room = new HeatingPlannerRoomEntity();
        room.getHeatSources().add(HeatingPlannerRoomHeatSourceEntity.builder()
                .enabled(true)
                .controllingDevice(thermostat)
                .build());
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        when(measurements.latest(
                thermostat, DeviceMeasurementType.TEMPERATURE, "temperature"))
                .thenReturn(Optional.of(DeviceMeasurementEntity.builder()
                        .value(new BigDecimal("20.75"))
                        .measuredAt(now.minusSeconds(60)).observedAt(now.minusSeconds(60))
                        .build()));

        HeatingPlannerMeasurementService.LatestMeasurement latest =
                service.latestFreshRoomTemperature(room, now);

        assertEquals(new BigDecimal("20.75"), latest.value());
        assertEquals(HeatingPlannerMeasurementService.Freshness.FRESH, latest.freshness());
    }

    private HeatingPlannerRoomEntity room(Long deviceId) {
        DeviceEntity device = new DeviceEntity();
        device.setId(deviceId);
        HeatingPlannerRoomEntity room = new HeatingPlannerRoomEntity();
        room.setRoomSensorDevice(device);
        return room;
    }

    @Test void onlineTuyaCloudSnapshotCanBeUsedAsExplicitFloorSensorWithoutInventingMeasuredAt() {
        var service = new HeatingPlannerMeasurementService(measurements);
        var room = room(14L);
        var device = room.getRoomSensorDevice();
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        device.setDevicePlatform(DevicePlatform.TUYA_SMART);
        device.setEnabled(true);
        device.setApiOnline(true);
        device.setLastCommunication(now.minusSeconds(300));
        room.setFloorSensorDevice(device);
        when(measurements.latest(device, DeviceMeasurementType.TEMPERATURE, "temperature"))
                .thenReturn(Optional.of(DeviceMeasurementEntity.builder().value(new BigDecimal("24.1"))
                        .observedAt(now.minusSeconds(300)).receivedAt(now.minusSeconds(300)).build()));

        var latest = service.latestFreshFloorTemperature(room, now);
        assertTrue(latest.fresh());
        assertTrue(latest.cloudSnapshot());
        assertNull(latest.measuredAt());
        assertEquals(now.minusSeconds(300), latest.observedAt());

        device.setApiOnline(false);
        assertFalse(service.latestFreshFloorTemperature(room, now).fresh());
        device.setApiOnline(true);
        assertFalse(service.latestFreshFloorTemperature(room, now.plusSeconds(301)).fresh());
        device.setEnabled(false);
        assertFalse(service.latestFreshFloorTemperature(room, now).fresh());
    }

    @Test void roomSensorIsNeverUsedAsFloorSensorImplicitly() {
        var service = new HeatingPlannerMeasurementService(measurements);
        assertEquals(HeatingPlannerMeasurementService.Freshness.MISSING,
                service.latestFreshFloorTemperature(room(14L), Instant.now()).freshness());
    }

    @Test void futureAndOldReportedTuyaReadingsDoNotBecomeFreshFromPolling() {
        var service = new HeatingPlannerMeasurementService(measurements);
        var room = room(14L);
        var device = room.getRoomSensorDevice();
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        device.setDevicePlatform(DevicePlatform.TUYA_SMART);
        device.setApiOnline(true);
        device.setEnabled(true);
        device.setLastCommunication(now);
        var reading = DeviceMeasurementEntity.builder().value(new BigDecimal("21"))
                .measuredAt(now.minusSeconds(7200)).observedAt(now.minusSeconds(7200)).receivedAt(now).build();
        when(measurements.latest(device, DeviceMeasurementType.TEMPERATURE, "temperature"))
                .thenReturn(Optional.of(reading));
        assertFalse(service.latestFreshRoomTemperature(room, now).fresh());
        reading.setMeasuredAt(now.plusSeconds(1));
        reading.setObservedAt(now.plusSeconds(1));
        assertFalse(service.latestFreshRoomTemperature(room, now).fresh());
    }
}
