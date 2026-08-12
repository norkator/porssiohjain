/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanPointStatus;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeatingPlannerGatewayCommandServiceTest {
    @Mock HeatingPlannerRoomHeatSourceRepository sourceRepository;
    @Mock HeatingPlannerPlanRepository planRepository;
    @Mock HeatingPlannerPlanPointRepository pointRepository;
    @Mock HeatingPlannerMeasurementService measurementService;

    HeatingPlannerGatewayCommandService service;
    ZigbeeGatewayDeviceEntity link;
    HeatingPlannerRoomEntity room;
    HeatingPlannerPlanEntity plan;
    HeatingPlannerPlanPointEntity point;
    Instant now;

    @BeforeEach
    void setUp() {
        service = new HeatingPlannerGatewayCommandService(sourceRepository, planRepository, pointRepository, measurementService);
        now = Instant.parse("2026-01-15T12:00:00Z");
        AccountEntity account = new AccountEntity(); account.setId(7L);
        SiteEntity site = new SiteEntity(); site.setId(8L);
        DeviceEntity controller = new DeviceEntity(); controller.setId(20L); controller.setAccount(account);
        var settings = HeatingPlannerSettingsEntity.builder().id(1L).account(account).site(site)
                .enabled(true).activeControlEnabled(true).build();
        room = HeatingPlannerRoomEntity.builder().id(10L).settings(settings).account(account).site(site).name("Kitchen").build();
        var source = HeatingPlannerRoomHeatSourceEntity.builder().room(room).account(account).site(site).enabled(true)
                .sourceType(HeatingPlannerHeatSourceType.FLOOR_HEATING).controllingDevice(controller).build();
        plan = HeatingPlannerPlanEntity.builder().settings(settings).account(account).site(site)
                .status(HeatingPlannerPlanStatus.ACTIVE).horizonStart(now.minusSeconds(3600))
                .horizonEnd(now.plusSeconds(3600)).triggerReason("test").build();
        point = HeatingPlannerPlanPointEntity.builder().plan(plan).room(room).account(account).site(site)
                .planVersion(plan.getPlanVersion()).plannedTime(now.minusSeconds(60))
                .plannedFloorSetpoint(new BigDecimal("25.0"))
                .status(HeatingPlannerPlanPointStatus.ACTIVE)
                .operatingMode(HeatingPlanSimulationService.OperatingMode.PREHEAT).reason("cheap period").build();
        link = ZigbeeGatewayDeviceEntity.builder().account(account).device(controller).build();
        lenient().when(sourceRepository.findByControllingDeviceIdAndEnabledTrueOrderByIdAsc(20L)).thenReturn(List.of(source));
        lenient().when(planRepository.findByAccountIdAndSiteIdOrderByCreatedAtDesc(7L, 8L)).thenReturn(List.of(plan));
        lenient().when(pointRepository.findByPlanVersionAndRoomIdAndPlannedTimeBetweenOrderByPlannedTimeAsc(
                eq(plan.getPlanVersion()), eq(10L), any(), eq(now))).thenReturn(List.of(point));
        var fresh = new HeatingPlannerMeasurementService.LatestMeasurement(BigDecimal.TEN, now,
                HeatingPlannerMeasurementService.Freshness.FRESH);
        lenient().when(measurementService.latestFreshRoomTemperature(room, now)).thenReturn(fresh);
        lenient().when(measurementService.latestFreshFloorTemperature(room, now)).thenReturn(fresh);
    }

    @Test
    void emitsBoundedCommandWhenActiveInputsAreFresh() {
        var command = service.currentCommand(link, now);

        assertThat(command).isPresent();
        assertThat(command.orElseThrow().targetTemperature()).isEqualByComparingTo("25.0");
        assertThat(command.orElseThrow().expiresAt()).isEqualTo(now.plusSeconds(1800));
    }

    @Test
    void suppressesCommandWhenRoomMeasurementIsStale() {
        when(measurementService.latestFreshRoomTemperature(room, now))
                .thenReturn(HeatingPlannerMeasurementService.LatestMeasurement.missing());

        assertThat(service.currentCommand(link, now)).isEmpty();
    }

    @Test
    void suppressesCommandForExcludedRoomWithSimulatedPoints() {
        point.setStatus(HeatingPlannerPlanPointStatus.SIMULATED);

        assertThat(service.currentCommand(link, now)).isEmpty();
    }

    @Test
    void rateLimitsPlannerSetpointChanges() {
        link.setDesiredSource("HEATING_PLANNER");
        link.setDesiredTemperature(new BigDecimal("23.0"));
        link.setDesiredAt(now.minusSeconds(60));

        assertThat(service.currentCommand(link, now)).isEmpty();
    }
}
