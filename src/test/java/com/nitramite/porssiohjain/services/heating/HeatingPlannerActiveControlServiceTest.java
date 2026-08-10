/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeatingPlannerActiveControlServiceTest {

    @Mock HeatingPlannerSettingsRepository settingsRepository;
    @Mock HeatingPlannerRoomRepository roomRepository;
    @Mock HeatingPlannerPlanRepository planRepository;
    @Mock HeatingPlannerPlanPointRepository pointRepository;
    @Mock ZigbeeGatewayDeviceRepository gatewayRepository;
    @Mock HeatingPlannerMeasurementService measurementService;

    HeatingPlannerActiveControlService service;
    HeatingPlannerSettingsEntity settings;
    HeatingPlannerRoomEntity room;
    HeatingPlannerRoomHeatSourceEntity source;
    HeatingPlannerPlanEntity plan;
    HeatingPlannerPlanPointEntity point;
    ZigbeeGatewayDeviceEntity gatewayLink;
    Instant now;

    @BeforeEach
    void setUp() {
        service = new HeatingPlannerActiveControlService(settingsRepository, roomRepository, planRepository,
                pointRepository, gatewayRepository, measurementService);
        now = Instant.parse("2026-01-15T12:00:00Z");
        AccountEntity account = new AccountEntity(); account.setId(7L);
        SiteEntity site = new SiteEntity(); site.setId(8L);
        settings = HeatingPlannerSettingsEntity.builder().id(1L).account(account).site(site).enabled(true).build();
        DeviceEntity controller = DeviceEntity.builder().id(20L).account(account).deviceType(DeviceType.THERMOSTAT).build();
        DeviceEntity roomSensor = DeviceEntity.builder().id(21L).account(account).deviceType(DeviceType.TEMPERATURE_SENSOR).build();
        DeviceEntity floorSensor = DeviceEntity.builder().id(22L).account(account).deviceType(DeviceType.TEMPERATURE_SENSOR).build();
        room = HeatingPlannerRoomEntity.builder().id(10L).settings(settings).account(account).site(site).name("Kitchen")
                .roomSensorDevice(roomSensor).floorSensorDevice(floorSensor).modelParametersLearned(true)
                .modelConfidence(new BigDecimal("0.6000")).build();
        source = HeatingPlannerRoomHeatSourceEntity.builder().id(11L).room(room).account(account).site(site)
                .sourceType(HeatingPlannerHeatSourceType.FLOOR_HEATING).controllingDevice(controller).enabled(true).build();
        room.getHeatSources().add(source);
        plan = HeatingPlannerPlanEntity.builder().id(30L).settings(settings).account(account).site(site)
                .horizonStart(now.minusSeconds(3600)).horizonEnd(now.plusSeconds(86400))
                .triggerReason("test").status(HeatingPlannerPlanStatus.SIMULATED).build();
        plan.setCreatedAt(now.minusSeconds(60));
        point = HeatingPlannerPlanPointEntity.builder().id(31L).plan(plan).room(room).account(account).site(site)
                .planVersion(plan.getPlanVersion()).plannedTime(now).operatingMode(HeatingPlanSimulationService.OperatingMode.NORMAL)
                .reason("test").build();
        gatewayLink = ZigbeeGatewayDeviceEntity.builder().device(controller).account(account).lastSeen(now.minusSeconds(60))
                .desiredVersion(4).appliedVersion(4).reportedSetpoint(new BigDecimal("21.0")).reportedMode("HEAT").build();

        lenient().when(settingsRepository.findByAccountIdAndSiteId(7L, 8L)).thenReturn(Optional.of(settings));
        lenient().when(roomRepository.findBySettingsIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(room));
        lenient().when(planRepository.findBySettingsIdAndStatusOrderByCreatedAtDesc(1L, HeatingPlannerPlanStatus.SIMULATED))
                .thenReturn(List.of(plan));
        lenient().when(planRepository.findBySettingsIdAndStatusOrderByCreatedAtDesc(1L, HeatingPlannerPlanStatus.ACTIVE))
                .thenReturn(List.of());
        lenient().when(pointRepository.findByPlanVersion(plan.getPlanVersion())).thenReturn(List.of(point));
        lenient().when(gatewayRepository.findByDeviceId(20L)).thenReturn(Optional.of(gatewayLink));
        var fresh = new HeatingPlannerMeasurementService.LatestMeasurement(new BigDecimal("21"), now,
                HeatingPlannerMeasurementService.Freshness.FRESH);
        lenient().when(measurementService.latestFreshRoomTemperature(room, now)).thenReturn(fresh);
        lenient().when(measurementService.latestFreshFloorTemperature(room, now)).thenReturn(fresh);
    }

    @Test
    void promotesReadyPlanAndEnablesControlAtomically() {
        assertThat(service.readiness(7L, 8L, now).ready()).isTrue();

        service.activate(7L, 8L, now);

        assertThat(settings.isActiveControlEnabled()).isTrue();
        assertThat(plan.getStatus()).isEqualTo(HeatingPlannerPlanStatus.ACTIVE);
        assertThat(point.getStatus()).isEqualTo(HeatingPlannerPlanPointStatus.ACTIVE);
        verify(settingsRepository).save(settings);
        verify(planRepository).save(plan);
    }

    @Test
    void refusesActivationWhenFloorMeasurementIsStale() {
        when(measurementService.latestFreshFloorTemperature(room, now))
                .thenReturn(HeatingPlannerMeasurementService.LatestMeasurement.missing());

        var readiness = service.readiness(7L, 8L, now);

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.issues()).anyMatch(issue -> issue.contains("floor-temperature"));
    }

    @Test
    void disableExpiresPlannerDesiredStateAndSupersedesActivePlan() {
        settings.setActiveControlEnabled(true);
        plan.setStatus(HeatingPlannerPlanStatus.ACTIVE);
        gatewayLink.setDesiredSource("HEATING_PLANNER");
        gatewayLink.setDesiredExpiresAt(now.plusSeconds(1800));
        when(planRepository.findBySettingsIdAndStatusOrderByCreatedAtDesc(1L, HeatingPlannerPlanStatus.ACTIVE))
                .thenReturn(List.of(plan));

        service.disable(7L, 8L, now);

        assertThat(settings.isActiveControlEnabled()).isFalse();
        assertThat(plan.getStatus()).isEqualTo(HeatingPlannerPlanStatus.SUPERSEDED);
        assertThat(gatewayLink.getDesiredExpiresAt()).isEqualTo(now);
        verify(gatewayRepository).save(gatewayLink);
    }
}
