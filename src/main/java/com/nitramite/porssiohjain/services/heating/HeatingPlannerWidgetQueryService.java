/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanPointStatus;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerWoodRecommendationStatus;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.heating.HeatingPlanSimulationService.OperatingMode;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerMeasurementService.LatestMeasurement;
import com.nitramite.porssiohjain.services.models.HeatingPlannerWidgetSummaryResponse;
import com.nitramite.porssiohjain.services.models.HeatingPlannerWidgetSummaryResponse.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HeatingPlannerWidgetQueryService {
    private static final Duration PLAN_FRESHNESS = Duration.ofMinutes(75);
    private static final Duration REPORT_FRESHNESS = Duration.ofMinutes(15);

    private final SiteRepository siteRepository;
    private final HeatingPlannerSettingsRepository settingsRepository;
    private final HeatingPlannerRoomRepository roomRepository;
    private final HeatingPlannerPlanRepository planRepository;
    private final HeatingPlannerPlanPointRepository pointRepository;
    private final HeatingPlannerWoodRecommendationRepository woodRepository;
    private final ZigbeeGatewayDeviceRepository gatewayRepository;
    private final HeatingPlannerMeasurementService measurementService;

    @Transactional(readOnly = true)
    public HeatingPlannerWidgetSummaryResponse getSummary(Long accountId, Long siteId, Long roomId, Instant now) {
        if (siteId == null || siteId <= 0 || (roomId != null && roomId <= 0)) {
            throw new IllegalArgumentException("Site and room IDs must be positive");
        }
        SiteEntity site = siteRepository.findByIdAndAccountId(siteId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Site not found"));
        HeatingPlannerSettingsEntity settings = settingsRepository.findByAccountIdAndSiteId(accountId, siteId)
                .orElse(null);
        ZoneId zone = ZoneId.of(settings == null ? site.getTimezone() : settings.getTimezone());
        LocalDate today = now.atZone(zone).toLocalDate();
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant horizonEnd = today.plusDays(2).atStartOfDay(zone).toInstant();
        List<HeatingPlannerRoomEntity> allRooms = settings == null ? List.of()
                : roomRepository.findBySettingsIdOrderBySortOrderAscIdAsc(settings.getId());
        if (roomId != null && allRooms.stream().noneMatch(room -> room.getId().equals(roomId))) {
            throw new EntityNotFoundException("Room not found");
        }
        List<HeatingPlannerRoomEntity> rooms = allRooms.stream().filter(HeatingPlannerRoomEntity::isEnabled)
                .filter(room -> roomId == null || room.getId().equals(roomId)).toList();
        Duration step = Duration.ofMinutes(settings == null ? 15 : settings.getSimulationStepMinutes());

        Schedule active = schedule(accountId, siteId, settings, HeatingPlannerPlanStatus.ACTIVE);
        Schedule simulated = schedule(accountId, siteId, settings, HeatingPlannerPlanStatus.SIMULATED);
        Schedule latest = newer(active, simulated);
        List<Long> deviceIds = rooms.stream().flatMap(room -> room.getHeatSources().stream())
                .filter(source -> source.isEnabled() && source.getSourceType() == HeatingPlannerHeatSourceType.FLOOR_HEATING)
                .map(HeatingPlannerRoomHeatSourceEntity::getControllingDevice).filter(Objects::nonNull)
                .map(DeviceEntity::getId).distinct().toList();
        Map<Long, ZigbeeGatewayDeviceEntity> links = deviceIds.isEmpty() ? Map.of() : gatewayRepository
                .findByDeviceIdInAndAccountId(deviceIds, accountId).stream()
                .collect(Collectors.toMap(link -> link.getDevice().getId(), link -> link));
        List<Warning> warnings = new ArrayList<>();
        if (settings != null && settings.getLastAutomationError() != null && !settings.getLastAutomationError().isBlank()) {
            warnings.add(new Warning(ReasonCode.AUTOMATION_ERROR, null, settings.getLastAutomationError()));
        }
        List<Room> rows = new ArrayList<>();
        Map<OperatingMode, Long> modes = new EnumMap<>(OperatingMode.class);
        int activeRooms = 0;
        int currentRooms = 0;
        boolean outdated = false;
        List<List<HeatingPlannerPlanPointEntity>> selectedPoints = new ArrayList<>();
        for (HeatingPlannerRoomEntity room : rooms) {
            Schedule selected = selectRoomSchedule(room, settings, active, latest, now, step);
            List<HeatingPlannerPlanPointEntity> points = selected.roomPoints(room.getId()).stream()
                    .filter(point -> !point.getPlannedTime().isBefore(todayStart)
                            && point.getPlannedTime().isBefore(horizonEnd)).toList();
            selectedPoints.add(points);
            HeatingPlannerPlanPointEntity current = currentPoint(points, now, step);
            LatestMeasurement roomTemperature = measurementService.latestFreshRoomTemperature(room, now);
            LatestMeasurement floorTemperature = measurementService.latestFreshFloorTemperature(room, now);
            List<ReasonCode> reasons = new ArrayList<>();
            temperatureReason(roomTemperature, false, reasons);
            if (hasFloorHeating(room)) temperatureReason(floorTemperature, true, reasons);
            if (current == null) reasons.add(ReasonCode.CURRENT_POINT_MISSING);
            else {
                currentRooms++;
                modes.merge(current.getOperatingMode(), 1L, Long::sum);
                if (settings.isEnabled() && settings.isActiveControlEnabled() && hasFloorHeating(room)
                        && current.getStatus() == HeatingPlannerPlanPointStatus.ACTIVE
                        && current.getOperatingMode() != OperatingMode.INACTIVE
                        && selected.plan().getHorizonEnd().isAfter(now)) activeRooms++;
            }
            if (stale(selected, now)) {
                outdated = true;
                reasons.add(ReasonCode.PLAN_OUTDATED);
            }
            if (settings.isActiveControlEnabled() && hasFloorHeating(room)
                    && (current == null || current.getStatus() != HeatingPlannerPlanPointStatus.ACTIVE)) {
                reasons.add(ReasonCode.FLOOR_ROOM_NOT_ACTIVE);
            }
            List<Thermostat> thermostats = room.getHeatSources().stream().filter(HeatingPlannerRoomHeatSourceEntity::isEnabled)
                    .filter(source -> source.getSourceType() == HeatingPlannerHeatSourceType.FLOOR_HEATING)
                    .map(HeatingPlannerRoomHeatSourceEntity::getControllingDevice).filter(Objects::nonNull)
                    .map(device -> links.get(device.getId())).filter(Objects::nonNull)
                    .map(link -> thermostat(link, now)).toList();
            for (Thermostat thermostat : thermostats) {
                if (!thermostat.reportFresh()) addReason(reasons, ReasonCode.THERMOSTAT_REPORT_STALE);
                if ("HEATING_PLANNER".equals(thermostat.desiredSource()) && thermostat.desiredExpired()) {
                    addReason(reasons, ReasonCode.THERMOSTAT_COMMAND_EXPIRED);
                }
                if (thermostat.lastError() != null && !thermostat.lastError().isBlank()) addReason(reasons, ReasonCode.THERMOSTAT_ERROR);
            }
            reasons.forEach(reason -> warnings.add(new Warning(reason, room.getId(), null)));
            rows.add(new Room(room.getId(), room.getName(), roomTemperature, floorTemperature,
                    current == null ? null : currentPointResponse(current, step),
                    settings.isEnabled() && !stale(selected, now) ? nextChange(room, points, now, step) : null,
                    thermostats, List.copyOf(reasons)));
        }
        Availability tomorrow = availability(selectedPoints, tomorrowStart, horizonEnd, step);
        if (settings != null && settings.isEnabled() && tomorrow != Availability.COMPLETE) {
            warnings.add(new Warning(ReasonCode.TOMORROW_PLAN_INCOMPLETE, null, null));
        }
        if (settings != null && rooms.isEmpty()) warnings.add(new Warning(ReasonCode.NO_ENABLED_ROOMS, null, null));
        StatusCode code = status(settings, latest, rows, activeRooms, currentRooms, outdated,
                rooms.stream().filter(this::hasFloorHeating).count());
        if (code == StatusCode.DISABLED) warnings.add(new Warning(ReasonCode.PLANNER_DISABLED, null, null));
        if (code == StatusCode.NO_PLAN) warnings.add(new Warning(ReasonCode.NO_PLAN, null, null));
        if (code == StatusCode.INACTIVE) warnings.add(new Warning(ReasonCode.PLANNER_INACTIVE, null, null));
        return new HeatingPlannerWidgetSummaryResponse(1, now, new Site(site.getId(), site.getName()), zone.getId(),
                site.getOperationState(), settings != null, settings != null && settings.isEnabled(),
                settings != null && settings.isActiveControlEnabled(), settings != null && settings.isHeatPumpControlEnabled(),
                new Status(code, warnings.stream().map(Warning::code).distinct().toList()),
                settings == null ? new Automation(null, null, null) : new Automation(settings.getLastAutomaticPlanAt(),
                        settings.getLastAutomaticActivationAt(), settings.getLastAutomationError()),
                planResponse(latest, now, step), planResponse(active, now, step),
                new Coverage(today, today.plusDays(1), tomorrow, rooms.size(), currentRooms), Map.copyOf(modes), activeRooms,
                (int) rows.stream().filter(row -> !row.reasonCodes().isEmpty()).count(),
                earliestChange(rows), List.copyOf(rows), settings != null && settings.isStoveLoaded(),
                woodRecommendation(accountId, siteId, settings, now, horizonEnd), List.copyOf(warnings));
    }

    private Schedule schedule(Long accountId, Long siteId, HeatingPlannerSettingsEntity settings, HeatingPlannerPlanStatus status) {
        if (settings == null) return Schedule.empty();
        return planRepository.findFirstByAccountIdAndSiteIdAndSettingsIdAndStatusOrderByCreatedAtDescIdDesc(
                accountId, siteId, settings.getId(), status)
                .map(plan -> new Schedule(plan, pointRepository.findByPlanVersion(plan.getPlanVersion()).stream()
                        .filter(point -> point.getStatus() == HeatingPlannerPlanPointStatus.ACTIVE
                                || point.getStatus() == HeatingPlannerPlanPointStatus.SIMULATED)
                        .sorted(Comparator.comparing(HeatingPlannerPlanPointEntity::getPlannedTime)).toList()))
                .orElseGet(Schedule::empty);
    }

    private Schedule newer(Schedule first, Schedule second) {
        if (first.plan() == null) return second;
        if (second.plan() == null) return first;
        return second.plan().getCreatedAt().isAfter(first.plan().getCreatedAt()) ? second : first;
    }

    private Schedule selectRoomSchedule(HeatingPlannerRoomEntity room, HeatingPlannerSettingsEntity settings,
                                        Schedule active, Schedule latest, Instant now, Duration step) {
        if (settings.isActiveControlEnabled() && hasFloorHeating(room) && active.plan() != null
                && active.plan().getHorizonEnd().isAfter(now)
                && active.roomPoints(room.getId()).stream().anyMatch(point ->
                    point.getStatus() == HeatingPlannerPlanPointStatus.ACTIVE && point.getPlannedTime().plus(step).isAfter(now))) {
            return new Schedule(active.plan(), active.roomPoints(room.getId()).stream()
                    .filter(point -> point.getStatus() == HeatingPlannerPlanPointStatus.ACTIVE).toList());
        }
        return latest;
    }

    private boolean hasFloorHeating(HeatingPlannerRoomEntity room) {
        return room.getHeatSources().stream().anyMatch(source -> source.isEnabled()
                && source.getSourceType() == HeatingPlannerHeatSourceType.FLOOR_HEATING);
    }

    private HeatingPlannerPlanPointEntity currentPoint(List<HeatingPlannerPlanPointEntity> points, Instant now, Duration step) {
        return points.stream().filter(point -> !point.getPlannedTime().isAfter(now)
                && point.getPlannedTime().plus(step).isAfter(now)).reduce((left, right) -> right).orElse(null);
    }

    private CurrentPoint currentPointResponse(HeatingPlannerPlanPointEntity point, Duration step) {
        Instant end = point.getPlannedTime().plus(step);
        return new CurrentPoint(point.getPlanVersion(), point.getStatus(), point.getPlannedTime(), end,
                point.getOperatingMode(), point.getPlannedFloorSetpoint(), point.getPlannedHeatPumpSetpoint(),
                point.getPredictedRoomTemperature(), point.getPredictedFloorTemperature(), end,
                point.isHeating(), point.getReason());
    }

    private NextChange nextChange(HeatingPlannerRoomEntity room, List<HeatingPlannerPlanPointEntity> points,
                                  Instant now, Duration step) {
        HeatingPlannerPlanPointEntity previous = currentPoint(points, now, step);
        for (HeatingPlannerPlanPointEntity point : points) {
            if (!point.getPlannedTime().isAfter(now)) continue;
            if (previous == null || !point.getPlannedTime().equals(previous.getPlannedTime().plus(step))
                    || !sameActivity(previous, point)) {
                return new NextChange(point.getPlannedTime(), List.of(new Change(room.getId(), room.getName(),
                        point.getPlanVersion(), point.getStatus(), point.getOperatingMode(), point.getPlannedFloorSetpoint(),
                        point.getPlannedHeatPumpSetpoint(), point.getReason())));
            }
            previous = point;
        }
        return null;
    }

    private boolean sameActivity(HeatingPlannerPlanPointEntity first, HeatingPlannerPlanPointEntity second) {
        return first.getOperatingMode() == second.getOperatingMode() && first.getStatus() == second.getStatus()
                && sameNumber(first.getPlannedFloorSetpoint(), second.getPlannedFloorSetpoint())
                && sameNumber(first.getPlannedHeatPumpSetpoint(), second.getPlannedHeatPumpSetpoint());
    }

    private boolean sameNumber(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    private NextChange earliestChange(List<Room> rows) {
        Instant at = rows.stream().map(Room::nextChange).filter(Objects::nonNull).map(NextChange::at)
                .min(Instant::compareTo).orElse(null);
        return at == null ? null : new NextChange(at, rows.stream().map(Room::nextChange).filter(Objects::nonNull)
                .filter(change -> change.at().equals(at)).flatMap(change -> change.changes().stream()).toList());
    }

    private Availability availability(List<List<HeatingPlannerPlanPointEntity>> roomPoints, Instant from, Instant to, Duration step) {
        if (roomPoints.isEmpty()) return Availability.UNAVAILABLE;
        int complete = 0;
        boolean any = false;
        for (List<HeatingPlannerPlanPointEntity> points : roomPoints) {
            Instant cursor = from;
            for (HeatingPlannerPlanPointEntity point : points) {
                Instant start = point.getPlannedTime();
                if (start.isBefore(from) || !start.isBefore(to)) continue;
                any = true;
                if (start.equals(cursor)) cursor = start.plus(step);
            }
            if (!cursor.isBefore(to)) complete++;
        }
        return complete == roomPoints.size() ? Availability.COMPLETE : any ? Availability.PARTIAL : Availability.UNAVAILABLE;
    }

    private boolean stale(Schedule schedule, Instant now) {
        return schedule.plan() != null && (schedule.plan().getCreatedAt() == null
                || schedule.plan().getCreatedAt().isBefore(now.minus(PLAN_FRESHNESS)));
    }

    private Plan planResponse(Schedule schedule, Instant now, Duration step) {
        if (schedule.plan() == null) return null;
        HeatingPlannerPlanEntity plan = schedule.plan();
        Instant end = schedule.points().stream().map(HeatingPlannerPlanPointEntity::getPlannedTime)
                .max(Instant::compareTo).map(time -> time.plus(step)).orElse(plan.getHorizonEnd());
        return new Plan(plan.getPlanVersion(), plan.getStatus(), plan.getCreatedAt(), plan.getHorizonStart(),
                plan.getHorizonEnd(), end, currentPoint(schedule.points(), now, step) != null, stale(schedule, now));
    }

    private StatusCode status(HeatingPlannerSettingsEntity settings, Schedule latest, List<Room> rooms,
                              int activeRooms, int currentRooms, boolean outdated, long floorRooms) {
        if (settings == null) return StatusCode.NOT_CONFIGURED;
        if (!settings.isEnabled()) return StatusCode.DISABLED;
        if (latest.plan() == null || rooms.isEmpty()) return StatusCode.NO_PLAN;
        if (outdated || currentRooms == 0) return StatusCode.STALE;
        if (currentRooms < rooms.size()) return StatusCode.PARTIAL;
        if (rooms.stream().allMatch(room -> room.currentPoint().operatingMode() == OperatingMode.INACTIVE)) return StatusCode.INACTIVE;
        if (activeRooms > 0) {
            return activeRooms < floorRooms || rooms.stream().anyMatch(room -> !room.reasonCodes().isEmpty())
                    ? StatusCode.PARTIAL : StatusCode.ACTIVE_PLAN;
        }
        return StatusCode.SIMULATED;
    }

    private void temperatureReason(LatestMeasurement measurement, boolean floor, List<ReasonCode> reasons) {
        switch (measurement.freshness()) {
            case STALE -> reasons.add(floor ? ReasonCode.FLOOR_TEMPERATURE_STALE : ReasonCode.ROOM_TEMPERATURE_STALE);
            case MISSING -> reasons.add(floor ? ReasonCode.FLOOR_TEMPERATURE_MISSING : ReasonCode.ROOM_TEMPERATURE_MISSING);
            case FRESH -> { }
        }
    }

    private void addReason(List<ReasonCode> reasons, ReasonCode code) {
        if (!reasons.contains(code)) reasons.add(code);
    }

    private Thermostat thermostat(ZigbeeGatewayDeviceEntity link, Instant now) {
        boolean expired = link.getDesiredExpiresAt() == null || !link.getDesiredExpiresAt().isAfter(now);
        boolean fresh = link.getLastSeen() != null && !link.getLastSeen().isBefore(now.minus(REPORT_FRESHNESS))
                && link.getReportedSetpoint() != null && link.getReportedMode() != null;
        boolean matches = !expired && fresh && link.getDesiredTemperature() != null
                && sameNumber(link.getDesiredTemperature(), link.getReportedSetpoint())
                && Objects.equals(link.getDesiredMode(), link.getReportedMode());
        return new Thermostat(link.getDevice().getId(), link.getDesiredSource(), link.getDesiredTemperature(), link.getDesiredMode(),
                link.getDesiredVersion(), link.getDesiredAt(), link.getDesiredExpiresAt(), expired, link.getReportedSetpoint(),
                link.getReportedMode(), link.getLastSeen(), fresh, matches, link.getLastError());
    }

    private WoodRecommendation woodRecommendation(Long accountId, Long siteId, HeatingPlannerSettingsEntity settings,
                                                  Instant now, Instant horizonEnd) {
        if (settings == null || !settings.isEnabled() || !settings.isStoveLoaded()) return null;
        return woodRepository.findUpcomingForWidget(accountId, siteId, settings.getId(),
                        List.of(HeatingPlannerWoodRecommendationStatus.PENDING, HeatingPlannerWoodRecommendationStatus.SENT),
                        HeatingPlannerPlanStatus.SUPERSEDED, now, horizonEnd, PageRequest.of(0, 1)).stream()
                .map(recommendation -> new WoodRecommendation(recommendation.getId(), recommendation.getRoom().getId(),
                        recommendation.getRoom().getName(), recommendation.getPlanVersion(), recommendation.getNotifyAt(),
                        recommendation.getReleaseStartsAt(), recommendation.getReleaseEndsAt(), recommendation.getLoadName(),
                        recommendation.getWoodAmount(), recommendation.getStatus(), !recommendation.getNotifyAt().isAfter(now),
                        recommendation.getReason())).findFirst().orElse(null);
    }

    private record Schedule(HeatingPlannerPlanEntity plan, List<HeatingPlannerPlanPointEntity> points) {
        static Schedule empty() { return new Schedule(null, List.of()); }
        List<HeatingPlannerPlanPointEntity> roomPoints(Long roomId) {
            return points.stream().filter(point -> point.getRoom().getId().equals(roomId)).toList();
        }
    }
}
