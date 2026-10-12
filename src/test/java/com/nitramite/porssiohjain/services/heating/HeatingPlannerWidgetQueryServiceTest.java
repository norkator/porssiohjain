package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.heating.HeatingPlanSimulationService.OperatingMode;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerMeasurementService.*;
import com.nitramite.porssiohjain.services.models.HeatingPlannerWidgetSummaryResponse.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeatingPlannerWidgetQueryServiceTest {
    @Mock SiteRepository sites;
    @Mock HeatingPlannerSettingsRepository settingsRepository;
    @Mock HeatingPlannerRoomRepository rooms;
    @Mock HeatingPlannerPlanRepository plans;
    @Mock HeatingPlannerPlanPointRepository points;
    @Mock HeatingPlannerWoodRecommendationRepository wood;
    @Mock ZigbeeGatewayDeviceRepository gateways;
    @Mock HeatingPlannerMeasurementService measurements;

    HeatingPlannerWidgetQueryService service;
    HeatingPlannerSettingsEntity settings;
    SiteEntity site;
    HeatingPlannerRoomEntity kitchen;
    Instant now = Instant.parse("2026-07-15T09:05:00Z");

    @BeforeEach void setUp() {
        service = new HeatingPlannerWidgetQueryService(sites, settingsRepository, rooms, plans, points, wood, gateways, measurements);
        site = SiteEntity.builder().id(8L).name("Home").timezone("Europe/Helsinki").build();
        settings = HeatingPlannerSettingsEntity.builder().id(1L).site(site).enabled(true).build();
        kitchen = room(10L, "Kitchen");
        lenient().when(sites.findByIdAndAccountId(8L, 7L)).thenReturn(Optional.of(site));
        lenient().when(settingsRepository.findByAccountIdAndSiteId(7L, 8L)).thenReturn(Optional.of(settings));
        lenient().when(rooms.findBySettingsIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(kitchen));
        for (var status : List.of(HeatingPlannerPlanStatus.ACTIVE, HeatingPlannerPlanStatus.SIMULATED)) {
            lenient().when(plans.findFirstByAccountIdAndSiteIdAndSettingsIdAndStatusOrderByCreatedAtDescIdDesc(7L, 8L, 1L, status))
                    .thenReturn(Optional.empty());
        }
        fresh(kitchen);
    }

    @Test void missingOrForeignSiteDoesNotReadPlannerData() {
        assertThatThrownBy(() -> service.getSummary(99L, 8L, null, now)).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(settingsRepository, rooms, plans, points, wood, gateways, measurements);
    }

    @Test void rejectsInvalidIdsBeforeReadingAnything() {
        assertThatThrownBy(() -> service.getSummary(7L, 0L, null, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getSummary(7L, 8L, -1L, now)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(sites, settingsRepository, rooms, plans, points, wood, gateways, measurements);
    }

    @Test void ownedUnconfiguredSiteReturnsEmptySummaryWithoutCreatingSettings() {
        when(settingsRepository.findByAccountIdAndSiteId(7L, 8L)).thenReturn(Optional.empty());
        var summary = service.getSummary(7L, 8L, null, now);
        assertThat(summary.status().code()).isEqualTo(StatusCode.NOT_CONFIGURED);
        assertThat(summary.configured()).isFalse();
        assertThat(summary.rooms()).isEmpty();
        assertThat(summary.latestPlan()).isNull();
        verifyNoInteractions(rooms, plans, points, wood, gateways, measurements);
    }

    @Test void rejectsRoomFromAnotherSiteOrAccount() {
        assertThatThrownBy(() -> service.getSummary(7L, 8L, 123L, now)).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(plans, points, wood, gateways, measurements);
    }

    @Test void enabledWithoutPlanIsNotActiveEvenWhenOptedIn() {
        settings.setActiveControlEnabled(true);
        var summary = service.getSummary(7L, 8L, null, now);
        assertThat(summary.floorControlOptedIn()).isTrue();
        assertThat(summary.status().code()).isEqualTo(StatusCode.NO_PLAN);
        assertThat(summary.roomsWithActiveFloorPoints()).isZero();
        assertThat(summary.nextChange()).isNull();
    }

    @Test void nextChangeSkipsIdenticalQuartersAndNumericScaleDifferences() {
        var plan = plan(HeatingPlannerPlanStatus.SIMULATED, now.minusSeconds(60));
        var current = point(plan, kitchen, "2026-07-15T09:00:00Z", OperatingMode.NORMAL);
        var unchanged = point(plan, kitchen, "2026-07-15T09:15:00Z", OperatingMode.NORMAL);
        unchanged.setPlannedFloorSetpoint(new BigDecimal("23.0000"));
        var changed = point(plan, kitchen, "2026-07-15T09:30:00Z", OperatingMode.PREHEAT);
        stub(plan, List.of(current, unchanged, changed));
        var summary = service.getSummary(7L, 8L, null, now);
        assertThat(summary.status().code()).isEqualTo(StatusCode.SIMULATED);
        assertThat(summary.currentModeCounts()).containsEntry(OperatingMode.NORMAL, 1L);
        assertThat(summary.nextChange().at()).isEqualTo(changed.getPlannedTime());
        assertThat(summary.rooms().getFirst().roomTemperature().value()).isEqualByComparingTo("21.50");
        assertThat(summary.rooms().getFirst().currentPoint().predictedRoomTemperature()).isEqualByComparingTo("22.30");
        assertThat(summary.rooms().getFirst().currentPoint().predictedAt()).isEqualTo(Instant.parse("2026-07-15T09:15:00Z"));
    }

    @Test void activeFloorRoomRetainsItsActivePlanWhenNewCandidateExists() {
        settings.setActiveControlEnabled(true);
        var active = plan(HeatingPlannerPlanStatus.ACTIVE, now.minusSeconds(600));
        var candidate = plan(HeatingPlannerPlanStatus.SIMULATED, now.minusSeconds(60));
        stub(active, List.of(point(active, kitchen, "2026-07-15T09:00:00Z", OperatingMode.DISCHARGE)));
        stub(candidate, List.of(point(candidate, kitchen, "2026-07-15T09:00:00Z", OperatingMode.PREHEAT)));
        var summary = service.getSummary(7L, 8L, null, now);
        assertThat(summary.latestPlan().planVersion()).isEqualTo(candidate.getPlanVersion());
        assertThat(summary.activeFloorPlan().planVersion()).isEqualTo(active.getPlanVersion());
        assertThat(summary.rooms().getFirst().currentPoint().planVersion()).isEqualTo(active.getPlanVersion());
        assertThat(summary.currentModeCounts()).containsEntry(OperatingMode.DISCHARGE, 1L);
        assertThat(summary.roomsWithActiveFloorPoints()).isEqualTo(1);
    }

    @Test void partialActivationPreservesSimulatedExcludedRoomAndSimultaneousChanges() {
        settings.setActiveControlEnabled(true);
        var bedroom = room(11L, "Bedroom");
        fresh(bedroom);
        when(rooms.findBySettingsIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(kitchen, bedroom));
        var active = plan(HeatingPlannerPlanStatus.ACTIVE, now.minusSeconds(60));
        var excludedNow = point(active, bedroom, "2026-07-15T09:00:00Z", OperatingMode.NORMAL);
        excludedNow.setStatus(HeatingPlannerPlanPointStatus.SIMULATED);
        var excludedNext = point(active, bedroom, "2026-07-15T09:15:00Z", OperatingMode.PREHEAT);
        excludedNext.setStatus(HeatingPlannerPlanPointStatus.SIMULATED);
        stub(active, List.of(point(active, kitchen, "2026-07-15T09:00:00Z", OperatingMode.NORMAL), excludedNow,
                point(active, kitchen, "2026-07-15T09:15:00Z", OperatingMode.PREHEAT), excludedNext));
        var summary = service.getSummary(7L, 8L, null, now);
        assertThat(summary.status().code()).isEqualTo(StatusCode.PARTIAL);
        assertThat(summary.roomsWithActiveFloorPoints()).isEqualTo(1);
        assertThat(summary.nextChange().changes()).extracting(Change::roomId).containsExactly(10L, 11L);
        assertThat(summary.rooms().get(1).reasonCodes()).contains(ReasonCode.FLOOR_ROOM_NOT_ACTIVE);
    }

    @Test void planGapDoesNotCarryOldActivityIntoCurrentTime() {
        var plan = plan(HeatingPlannerPlanStatus.SIMULATED, now.minusSeconds(60));
        stub(plan, List.of(point(plan, kitchen, "2026-07-15T08:00:00Z", OperatingMode.PREHEAT),
                point(plan, kitchen, "2026-07-15T10:00:00Z", OperatingMode.PREHEAT)));
        var summary = service.getSummary(7L, 8L, null, now);
        assertThat(summary.status().code()).isEqualTo(StatusCode.STALE);
        assertThat(summary.rooms().getFirst().currentPoint()).isNull();
        assertThat(summary.latestPlan().coversNow()).isFalse();
        assertThat(summary.nextChange().at()).isEqualTo(Instant.parse("2026-07-15T10:00:00Z"));
    }

    @Test void oldPlanAndDisabledPlannerDoNotAdvertiseUpcomingActions() {
        var plan = plan(HeatingPlannerPlanStatus.SIMULATED, now.minusSeconds(4600));
        stub(plan, List.of(point(plan, kitchen, "2026-07-15T09:00:00Z", OperatingMode.PREHEAT),
                point(plan, kitchen, "2026-07-15T09:15:00Z", OperatingMode.DISCHARGE)));
        var summary = service.getSummary(7L, 8L, null, now);
        assertThat(summary.status().code()).isEqualTo(StatusCode.STALE);
        assertThat(summary.nextChange()).isNull();
        settings.setEnabled(false);
        assertThat(service.getSummary(7L, 8L, null, now).status().code()).isEqualTo(StatusCode.DISABLED);
    }

    @Test void inactivePlanUsesStructuredModeWithoutParsingReasonText() {
        var plan = plan(HeatingPlannerPlanStatus.SIMULATED, now.minusSeconds(60));
        var inactive = point(plan, kitchen, "2026-07-15T09:00:00Z", OperatingMode.INACTIVE);
        inactive.setReason("Any explanation");
        stub(plan, List.of(inactive));
        var summary = service.getSummary(7L, 8L, null, now);
        assertThat(summary.status().code()).isEqualTo(StatusCode.INACTIVE);
        assertThat(summary.status().reasonCodes()).contains(ReasonCode.PLANNER_INACTIVE);
    }

    @Test void staleMeasurementsRemainMeasurementsAndNeverBecomePredictions() {
        when(measurements.latestFreshRoomTemperature(kitchen, now)).thenReturn(new LatestMeasurement(
                new BigDecimal("19.20"), now.minusSeconds(7200), Freshness.STALE));
        when(measurements.latestFreshFloorTemperature(kitchen, now)).thenReturn(LatestMeasurement.missing());
        var plan = plan(HeatingPlannerPlanStatus.SIMULATED, now.minusSeconds(60));
        stub(plan, List.of(point(plan, kitchen, "2026-07-15T09:00:00Z", OperatingMode.PREHEAT)));
        var room = service.getSummary(7L, 8L, null, now).rooms().getFirst();
        assertThat(room.roomTemperature().freshness()).isEqualTo(Freshness.STALE);
        assertThat(room.roomTemperature().value()).isEqualByComparingTo("19.20");
        assertThat(room.floorTemperature().value()).isNull();
        assertThat(room.currentPoint().predictedRoomTemperature()).isEqualByComparingTo("22.30");
        assertThat(room.reasonCodes()).contains(ReasonCode.ROOM_TEMPERATURE_STALE, ReasonCode.FLOOR_TEMPERATURE_MISSING);
    }

    @Test void matchingReadbackDoesNotValidateExpiredDesiredCommand() {
        var device = DeviceEntity.builder().id(20L).build();
        kitchen.getHeatSources().getFirst().setControllingDevice(device);
        var link = ZigbeeGatewayDeviceEntity.builder().device(device).desiredSource("HEATING_PLANNER")
                .desiredTemperature(new BigDecimal("23.0")).desiredMode("HEAT").desiredExpiresAt(now)
                .reportedSetpoint(new BigDecimal("23.00")).reportedMode("HEAT").lastSeen(now.minusSeconds(60)).build();
        when(gateways.findByDeviceIdInAndAccountId(List.of(20L), 7L)).thenReturn(List.of(link));
        var thermostat = service.getSummary(7L, 8L, null, now).rooms().getFirst().thermostats().getFirst();
        assertThat(thermostat.reportFresh()).isTrue();
        assertThat(thermostat.readbackMatchesDesired()).isFalse();
        assertThat(thermostat.desiredExpired()).isTrue();
        link.setDesiredExpiresAt(now.plusSeconds(600));
        assertThat(service.getSummary(7L, 8L, null, now).rooms().getFirst().thermostats().getFirst()
                .readbackMatchesDesired()).isTrue();
        link.setLastSeen(now.minusSeconds(901));
        assertThat(service.getSummary(7L, 8L, null, now).rooms().getFirst().thermostats().getFirst()
                .readbackMatchesDesired()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"2026-03-28", "2026-10-24"})
    void tomorrowCoverageUsesEntireDstDayAndIncludesLocalMidnight(String date) {
        var day = LocalDate.parse(date);
        var zone = ZoneId.of("Europe/Helsinki");
        now = day.atStartOfDay(zone).toInstant();
        var plan = plan(HeatingPlannerPlanStatus.SIMULATED, now);
        Instant start = day.plusDays(1).atStartOfDay(zone).toInstant();
        Instant end = day.plusDays(2).atStartOfDay(zone).toInstant();
        List<HeatingPlannerPlanPointEntity> tomorrow = new ArrayList<>();
        for (Instant time = start; time.isBefore(end); time = time.plusSeconds(900)) {
            tomorrow.add(point(plan, kitchen, time.toString(), OperatingMode.NORMAL));
        }
        tomorrow.add(point(plan, kitchen, now.toString(), OperatingMode.NORMAL));
        stub(plan, tomorrow);
        fresh(kitchen);
        var summary = service.getSummary(7L, 8L, null, now);
        assertThat(summary.coverage().todayDate()).isEqualTo(day);
        assertThat(summary.coverage().tomorrowAvailability()).isEqualTo(Availability.COMPLETE);
        assertThat(summary.rooms().getFirst().currentPoint().startsAt()).isEqualTo(now);
        assertThat(summary.latestPlan().coverageEnd()).isEqualTo(end);
        assertThat(Duration.between(start, end).toHours()).isIn(23L, 25L);
        tomorrow.removeFirst();
        assertThat(service.getSummary(7L, 8L, null, now).coverage().tomorrowAvailability()).isEqualTo(Availability.PARTIAL);
    }

    @Test void roomFilterDoesNotHideSiteLevelStoveAdvisory() {
        settings.setStoveLoaded(true);
        var living = room(11L, "Living room");
        var recommendation = HeatingPlannerWoodRecommendationEntity.builder().id(5L).room(living)
                .planVersion(UUID.randomUUID()).notifyAt(now.minusSeconds(60)).releaseStartsAt(now.plusSeconds(2400))
                .releaseEndsAt(now.plusSeconds(18000)).loadName("Normal load").woodAmount(BigDecimal.valueOf(8))
                .status(HeatingPlannerWoodRecommendationStatus.SENT).build();
        when(wood.findUpcomingForWidget(eq(7L), eq(8L), eq(1L),
                eq(List.of(HeatingPlannerWoodRecommendationStatus.PENDING, HeatingPlannerWoodRecommendationStatus.SENT)),
                eq(HeatingPlannerPlanStatus.SUPERSEDED), eq(now), eq(Instant.parse("2026-07-16T21:00:00Z")), any()))
                .thenReturn(List.of(recommendation));
        var summary = service.getSummary(7L, 8L, 10L, now);
        assertThat(summary.nextWoodRecommendation().roomId()).isEqualTo(11L);
        assertThat(summary.nextWoodRecommendation().due()).isTrue();
        settings.setStoveLoaded(false);
        assertThat(service.getSummary(7L, 8L, 10L, now).nextWoodRecommendation()).isNull();
        verify(wood, times(1)).findUpcomingForWidget(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private HeatingPlannerRoomEntity room(Long id, String name) {
        var room = HeatingPlannerRoomEntity.builder().id(id).settings(settings).name(name).build();
        room.getHeatSources().add(HeatingPlannerRoomHeatSourceEntity.builder()
                .sourceType(HeatingPlannerHeatSourceType.FLOOR_HEATING).enabled(true).build());
        return room;
    }

    private void fresh(HeatingPlannerRoomEntity room) {
        lenient().when(measurements.latestFreshRoomTemperature(room, now)).thenReturn(
                new LatestMeasurement(new BigDecimal("21.50"), now, Freshness.FRESH));
        lenient().when(measurements.latestFreshFloorTemperature(room, now)).thenReturn(
                new LatestMeasurement(new BigDecimal("23.50"), now, Freshness.FRESH));
    }

    private HeatingPlannerPlanEntity plan(HeatingPlannerPlanStatus status, Instant generatedAt) {
        return HeatingPlannerPlanEntity.builder().id(status == HeatingPlannerPlanStatus.ACTIVE ? 1L : 2L)
                .settings(settings).status(status).createdAt(generatedAt).horizonStart(now.minusSeconds(3600))
                .horizonEnd(now.plusSeconds(200000)).build();
    }

    private HeatingPlannerPlanPointEntity point(HeatingPlannerPlanEntity plan, HeatingPlannerRoomEntity room,
                                               String time, OperatingMode mode) {
        return HeatingPlannerPlanPointEntity.builder().plan(plan).planVersion(plan.getPlanVersion()).room(room)
                .plannedTime(Instant.parse(time)).operatingMode(mode).plannedFloorSetpoint(new BigDecimal("23.00"))
                .predictedRoomTemperature(new BigDecimal("22.30"))
                .status(plan.getStatus() == HeatingPlannerPlanStatus.ACTIVE
                        ? HeatingPlannerPlanPointStatus.ACTIVE : HeatingPlannerPlanPointStatus.SIMULATED).reason("Test plan").build();
    }

    private void stub(HeatingPlannerPlanEntity plan, List<HeatingPlannerPlanPointEntity> data) {
        when(plans.findFirstByAccountIdAndSiteIdAndSettingsIdAndStatusOrderByCreatedAtDescIdDesc(7L, 8L, 1L, plan.getStatus()))
                .thenReturn(Optional.of(plan));
        when(points.findByPlanVersion(plan.getPlanVersion())).thenAnswer(invocation -> data);
    }
}
