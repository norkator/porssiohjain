/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.services.heating.HeatingPlanSimulationService.OperatingMode;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerMeasurementService.LatestMeasurement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only widget data. Planned activity and thermostat read-back are separate evidence. */
public record HeatingPlannerWidgetSummaryResponse(
        int schemaVersion, Instant serverTime, Site site, String timezone, SiteOperationState operationState,
        boolean configured, boolean enabled, boolean floorControlOptedIn, boolean heatPumpControlOptedIn,
        Status status, Automation automation, Plan latestPlan, Plan activeFloorPlan, Coverage coverage,
        Map<OperatingMode, Long> currentModeCounts, int roomsWithActiveFloorPoints, int roomsWithWarnings,
        NextChange nextChange, List<Room> rooms, boolean stoveLoaded, WoodRecommendation nextWoodRecommendation,
        List<Warning> warnings
) {
    public enum StatusCode { NOT_CONFIGURED, DISABLED, NO_PLAN, SIMULATED, ACTIVE_PLAN, PARTIAL, INACTIVE, STALE }
    public enum Availability { COMPLETE, PARTIAL, UNAVAILABLE }
    public enum ReasonCode {
        PLANNER_DISABLED, NO_PLAN, NO_ENABLED_ROOMS, PLAN_OUTDATED, CURRENT_POINT_MISSING,
        PLANNER_INACTIVE, ROOM_TEMPERATURE_STALE, ROOM_TEMPERATURE_MISSING,
        FLOOR_TEMPERATURE_STALE, FLOOR_TEMPERATURE_MISSING, AUTOMATION_ERROR,
        TOMORROW_PLAN_INCOMPLETE, FLOOR_ROOM_NOT_ACTIVE, THERMOSTAT_REPORT_STALE,
        THERMOSTAT_COMMAND_EXPIRED, THERMOSTAT_ERROR
    }
    public record Site(Long id, String name) { }
    public record Status(StatusCode code, List<ReasonCode> reasonCodes) { }
    public record Automation(Instant lastAutomaticPlanAt, Instant lastAutomaticActivationAt, String lastAutomationError) { }
    public record Plan(UUID planVersion, HeatingPlannerPlanStatus status, Instant generatedAt,
                       Instant horizonStart, Instant horizonEnd, Instant coverageEnd, boolean coversNow, boolean stale) { }
    /** Availability describes persisted plan intervals, not verified price/weather completeness. */
    public record Coverage(LocalDate todayDate, LocalDate tomorrowDate, Availability tomorrowAvailability,
                           int enabledRoomCount, int roomsWithCurrentPoints) { }
    public record Warning(ReasonCode code, Long roomId, String detail) { }
    public record Room(Long roomId, String name, LatestMeasurement roomTemperature, LatestMeasurement floorTemperature,
                       CurrentPoint currentPoint, NextChange nextChange, List<Thermostat> thermostats,
                       List<ReasonCode> reasonCodes) { }
    public record CurrentPoint(UUID planVersion, HeatingPlannerPlanPointStatus pointStatus,
                               Instant startsAt, Instant endsAt, OperatingMode operatingMode,
                               BigDecimal plannedFloorSetpoint, BigDecimal plannedHeatPumpSetpoint,
                               BigDecimal predictedRoomTemperature, BigDecimal predictedFloorTemperature,
                               Instant predictedAt, boolean predictedHeating, String reason) { }
    public record Change(Long roomId, String roomName, UUID planVersion, HeatingPlannerPlanPointStatus pointStatus,
                         OperatingMode operatingMode, BigDecimal plannedFloorSetpoint,
                         BigDecimal plannedHeatPumpSetpoint, String reason) { }
    public record NextChange(Instant at, List<Change> changes) { }
    public record Thermostat(Long deviceId, String desiredSource, BigDecimal desiredSetpoint, String desiredMode,
                             long desiredVersion, Instant desiredAt, Instant desiredExpiresAt, boolean desiredExpired,
                             BigDecimal reportedSetpoint, String reportedMode, Instant reportedAt,
                             boolean reportFresh, boolean readbackMatchesDesired, String lastError) { }
    public record WoodRecommendation(Long id, Long roomId, String roomName, UUID planVersion,
                                     Instant notifyAt, Instant releaseStartsAt, Instant releaseEndsAt,
                                     String loadName, BigDecimal woodAmount, HeatingPlannerWoodRecommendationStatus status,
                                     boolean due, String reason) { }
}
