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
                .desiredVersion(4).reportedSetpoint(new BigDecimal("21.0")).reportedMode("HEAT").build();

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
    void automaticallyPromotesRecalculatedPlanWhenControlIsAlreadyOptedIn() {
        settings.setActiveControlEnabled(true);

        assertThat(service.activateLatestRecalculatedPlanIfOptedIn(7L, 8L, now)).isTrue();

        assertThat(plan.getStatus()).isEqualTo(HeatingPlannerPlanStatus.ACTIVE);
        assertThat(point.getStatus()).isEqualTo(HeatingPlannerPlanPointStatus.ACTIVE);
        assertThat(settings.isActiveControlEnabled()).isTrue();
    }

    @Test
    void leavesRecalculatedPlanSimulatedAfterControlWasDisabled() {
        settings.setActiveControlEnabled(false);

        assertThat(service.activateLatestRecalculatedPlanIfOptedIn(7L, 8L, now)).isFalse();

        assertThat(plan.getStatus()).isEqualTo(HeatingPlannerPlanStatus.SIMULATED);
        verify(planRepository, never()).save(any());
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
    void refusesActivationWhenLatestPlanIsWeatherGateInactive() {
        point.setOperatingMode(HeatingPlanSimulationService.OperatingMode.INACTIVE);
        point.setReason("Forecast stays above the configured Heating Planner activation temperature");

        var readiness = service.readiness(7L, 8L, now);

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.issues()).anyMatch(issue -> issue.contains("forecast stays above"));
    }

    @Test
    void pendingThermostatCommandDoesNotBlockActivation() {
        gatewayLink.setDesiredVersion(5);
        gatewayLink.setDesiredExpiresAt(now.plusSeconds(300));

        var readiness = service.readiness(7L, 8L, now);

        assertThat(readiness.ready()).isTrue();
    }

    @Test
    void expiredPendingThermostatCommandDoesNotBlockActivation() {
        gatewayLink.setDesiredVersion(5);
        gatewayLink.setDesiredExpiresAt(now.minusSeconds(1));

        var readiness = service.readiness(7L, 8L, now);

        assertThat(readiness.ready()).isTrue();
    }

    @Test
    void activatesReadyRoomAndLeavesLowConfidenceRoomOnFallback() {
        DeviceEntity showerController = DeviceEntity.builder().id(40L).account(settings.getAccount())
                .deviceType(DeviceType.THERMOSTAT).build();
        DeviceEntity showerRoomSensor = DeviceEntity.builder().id(41L).account(settings.getAccount())
                .deviceType(DeviceType.TEMPERATURE_SENSOR).build();
        DeviceEntity showerFloorSensor = DeviceEntity.builder().id(42L).account(settings.getAccount())
                .deviceType(DeviceType.TEMPERATURE_SENSOR).build();
        HeatingPlannerRoomEntity shower = HeatingPlannerRoomEntity.builder().id(12L).settings(settings)
                .account(settings.getAccount()).site(settings.getSite()).name("Shower")
                .roomSensorDevice(showerRoomSensor).floorSensorDevice(showerFloorSensor)
                .modelParametersLearned(true).modelConfidence(new BigDecimal("0.2000")).build();
        HeatingPlannerRoomHeatSourceEntity showerSource = HeatingPlannerRoomHeatSourceEntity.builder().id(13L)
                .room(shower).account(settings.getAccount()).site(settings.getSite())
                .sourceType(HeatingPlannerHeatSourceType.FLOOR_HEATING).controllingDevice(showerController)
                .enabled(true).build();
        shower.getHeatSources().add(showerSource);
        HeatingPlannerPlanPointEntity showerPoint = HeatingPlannerPlanPointEntity.builder().id(32L).plan(plan)
                .room(shower).account(settings.getAccount()).site(settings.getSite()).planVersion(plan.getPlanVersion())
                .plannedTime(now).operatingMode(HeatingPlanSimulationService.OperatingMode.NORMAL).reason("test").build();
        ZigbeeGatewayDeviceEntity showerLink = ZigbeeGatewayDeviceEntity.builder().device(showerController)
                .account(settings.getAccount()).lastSeen(now.minusSeconds(60)).desiredVersion(1)
                .reportedSetpoint(new BigDecimal("21.0")).reportedMode("HEAT").desiredSource("HEATING_PLANNER")
                .desiredExpiresAt(now.plusSeconds(1800)).build();
        var fresh = new HeatingPlannerMeasurementService.LatestMeasurement(new BigDecimal("21"), now,
                HeatingPlannerMeasurementService.Freshness.FRESH);
        when(roomRepository.findBySettingsIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(room, shower));
        when(pointRepository.findByPlanVersion(plan.getPlanVersion())).thenReturn(List.of(point, showerPoint));
        when(gatewayRepository.findByDeviceId(40L)).thenReturn(Optional.of(showerLink));
        when(measurementService.latestFreshRoomTemperature(shower, now)).thenReturn(fresh);
        when(measurementService.latestFreshFloorTemperature(shower, now)).thenReturn(fresh);

        var readiness = service.readiness(7L, 8L, now);
        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.issues()).contains("Shower: learned model confidence must be at least 25%");

        service.activate(7L, 8L, now);

        assertThat(point.getStatus()).isEqualTo(HeatingPlannerPlanPointStatus.ACTIVE);
        assertThat(showerPoint.getStatus()).isEqualTo(HeatingPlannerPlanPointStatus.SIMULATED);
        assertThat(showerLink.getDesiredExpiresAt()).isEqualTo(now);
        verify(gatewayRepository).save(showerLink);
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
