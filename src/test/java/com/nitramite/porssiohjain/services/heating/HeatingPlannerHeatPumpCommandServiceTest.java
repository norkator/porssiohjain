/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services.heating;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateHexEditorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeatingPlannerHeatPumpCommandServiceTest {

    @Mock private HeatingPlannerRoomHeatSourceRepository sourceRepository;
    @Mock private HeatingPlannerPlanRepository planRepository;
    @Mock private HeatingPlannerPlanPointRepository pointRepository;
    @Mock private DeviceAcDataRepository acDataRepository;
    @Mock private HeatingPlannerMeasurementService measurementService;
    private HeatingPlannerHeatPumpCommandService service;

    @BeforeEach
    void setUp() {
        service = new HeatingPlannerHeatPumpCommandService(sourceRepository, planRepository, pointRepository,
                acDataRepository, measurementService, new ToshibaAcStateHexEditorService(), new ObjectMapper());
    }

    @Test
    void createsHeatingSetpointCommandForFreshOptedInRoom() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        SiteEntity site = new SiteEntity();
        site.setId(2L);
        HeatingPlannerSettingsEntity settings = HeatingPlannerSettingsEntity.builder()
                .id(3L).account(account).site(site).enabled(true).heatPumpControlEnabled(true).build();
        HeatingPlannerRoomEntity room = HeatingPlannerRoomEntity.builder()
                .id(4L).settings(settings).account(account).site(site).name("Living room").enabled(true).build();
        DeviceEntity device = DeviceEntity.builder().id(5L).deviceType(DeviceType.HEAT_PUMP).enabled(true).build();
        HeatingPlannerRoomHeatSourceEntity source = HeatingPlannerRoomHeatSourceEntity.builder()
                .id(6L).room(room).account(account).site(site).sourceType(HeatingPlannerHeatSourceType.HEAT_PUMP)
                .controllingDevice(device).enabled(true).build();
        UUID version = UUID.randomUUID();
        HeatingPlannerPlanEntity plan = HeatingPlannerPlanEntity.builder().id(7L).settings(settings)
                .account(account).site(site).planVersion(version).status(HeatingPlannerPlanStatus.SIMULATED)
                .horizonStart(now.minusSeconds(900)).horizonEnd(now.plusSeconds(3600)).createdAt(now.minusSeconds(60)).build();
        HeatingPlannerPlanPointEntity point = HeatingPlannerPlanPointEntity.builder().plannedTime(now.minusSeconds(60))
                .plannedHeatPumpSetpoint(new BigDecimal("24.00")).reason("Comfort recovery").build();
        DeviceAcDataEntity acData = DeviceAcDataEntity.builder().device(device).acType(AcType.TOSHIBA)
                .lastPolledStateHex("30431500000000000000000000000000000000").build();

        when(sourceRepository.findAll()).thenReturn(List.of(source));
        when(measurementService.latestFreshRoomTemperature(room, now)).thenReturn(
                new HeatingPlannerMeasurementService.LatestMeasurement(new BigDecimal("20.00"), now,
                        HeatingPlannerMeasurementService.Freshness.FRESH));
        when(planRepository.findByAccountIdAndSiteIdOrderByCreatedAtDesc(1L, 2L)).thenReturn(List.of(plan));
        when(pointRepository.findByPlanVersionAndRoomIdAndPlannedTimeBetweenOrderByPlannedTimeAsc(
                eq(version), eq(4L), any(), eq(now))).thenReturn(List.of(point));
        when(acDataRepository.findByDevice(device)).thenReturn(Optional.of(acData));

        List<HeatingPlannerHeatPumpCommandService.HeatPumpPlanCommand> commands = service.currentCommands(now);

        assertThat(commands).hasSize(1);
        assertThat(commands.getFirst().state()).startsWith("304318");
        assertThat(commands.getFirst().reason()).contains("24.00 °C");
    }

    @Test
    void doesNotControlWhenSiteSwitchIsOff() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        HeatingPlannerSettingsEntity settings = HeatingPlannerSettingsEntity.builder()
                .enabled(true).heatPumpControlEnabled(false).build();
        HeatingPlannerRoomEntity room = HeatingPlannerRoomEntity.builder().settings(settings).enabled(true).build();
        DeviceEntity device = DeviceEntity.builder().deviceType(DeviceType.HEAT_PUMP).enabled(true).build();
        HeatingPlannerRoomHeatSourceEntity source = HeatingPlannerRoomHeatSourceEntity.builder().room(room)
                .sourceType(HeatingPlannerHeatSourceType.HEAT_PUMP).controllingDevice(device).enabled(true).build();
        when(sourceRepository.findAll()).thenReturn(List.of(source));

        assertThat(service.currentCommands(now)).isEmpty();
    }
}
