/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.HeatingPlannerPlanEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerRoomEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerRoomHeatSourceEntity;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanPointStatus;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerPlanPointRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerPlanRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerRoomRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerSettingsRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeGatewayDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HeatingPlannerActiveControlService {

    static final BigDecimal MINIMUM_MODEL_CONFIDENCE = new BigDecimal("0.2500");
    static final Duration MAXIMUM_PLAN_AGE = Duration.ofMinutes(30);
    static final Duration GATEWAY_FRESHNESS = Duration.ofMinutes(15);

    private final HeatingPlannerSettingsRepository settingsRepository;
    private final HeatingPlannerRoomRepository roomRepository;
    private final HeatingPlannerPlanRepository planRepository;
    private final HeatingPlannerPlanPointRepository pointRepository;
    private final ZigbeeGatewayDeviceRepository gatewayDeviceRepository;
    private final HeatingPlannerMeasurementService measurementService;

    @Transactional(readOnly = true)
    public Readiness readiness(Long accountId, Long siteId, Instant now) {
        var settings = settingsRepository.findByAccountIdAndSiteId(accountId, siteId).orElse(null);
        if (settings == null) return new Readiness(false, false, List.of("Heating Planner settings have not been saved"),
                null, null, null, null);
        List<String> issues = new ArrayList<>();
        if (!settings.isEnabled()) issues.add("Heating Planner master switch is disabled");

        List<HeatingPlannerRoomEntity> controlledRooms = roomRepository
                .findBySettingsIdOrderBySortOrderAscIdAsc(settings.getId()).stream()
                .filter(HeatingPlannerRoomEntity::isEnabled)
                .filter(this::hasEnabledFloorHeating)
                .toList();
        if (controlledRooms.isEmpty()) issues.add("No enabled floor-heating room is configured");
        for (HeatingPlannerRoomEntity room : controlledRooms) validateRoom(room, now, issues);

        HeatingPlannerPlanEntity candidate = latestSimulatedPlan(settings.getId());
        if (candidate == null) {
            issues.add("Recalculate a plan before enabling active control");
        } else {
            if (candidate.getCreatedAt() == null || candidate.getCreatedAt().isBefore(now.minus(MAXIMUM_PLAN_AGE)))
                issues.add("The latest plan is older than 30 minutes; recalculate it");
            if (candidate.getHorizonStart().isAfter(now) || !candidate.getHorizonEnd().isAfter(now))
                issues.add("The latest plan does not cover the current time");
            for (HeatingPlannerRoomEntity room : controlledRooms) {
                boolean hasPoints = pointRepository.findByPlanVersion(candidate.getPlanVersion()).stream()
                        .anyMatch(point -> point.getRoom().getId().equals(room.getId()));
                if (!hasPoints) issues.add(room.getName() + ": latest plan has no room points");
            }
        }
        return new Readiness(issues.isEmpty(), settings.isActiveControlEnabled(), List.copyOf(issues),
                candidate == null ? null : candidate.getPlanVersion().toString(), settings.getLastAutomaticPlanAt(),
                settings.getLastAutomaticActivationAt(), settings.getLastAutomationError());
    }

    @Transactional
    public void activate(Long accountId, Long siteId, Instant now) {
        Readiness readiness = readiness(accountId, siteId, now);
        if (!readiness.ready()) throw new IllegalStateException(String.join("; ", readiness.issues()));
        var settings = settingsRepository.findByAccountIdAndSiteId(accountId, siteId).orElseThrow();
        HeatingPlannerPlanEntity candidate = latestSimulatedPlan(settings.getId());
        for (HeatingPlannerPlanEntity active : planRepository
                .findBySettingsIdAndStatusOrderByCreatedAtDesc(settings.getId(), HeatingPlannerPlanStatus.ACTIVE)) {
            supersede(active, now);
        }
        candidate.setStatus(HeatingPlannerPlanStatus.ACTIVE);
        var points = pointRepository.findByPlanVersion(candidate.getPlanVersion());
        points.forEach(point -> point.setStatus(HeatingPlannerPlanPointStatus.ACTIVE));
        pointRepository.saveAll(points);
        planRepository.save(candidate);
        settings.setActiveControlEnabled(true);
        settingsRepository.save(settings);
    }

    @Transactional
    public void disable(Long accountId, Long siteId, Instant now) {
        var settings = settingsRepository.findByAccountIdAndSiteId(accountId, siteId).orElse(null);
        if (settings == null) return;
        settings.setActiveControlEnabled(false);
        settingsRepository.save(settings);
        planRepository.findBySettingsIdAndStatusOrderByCreatedAtDesc(settings.getId(), HeatingPlannerPlanStatus.ACTIVE)
                .forEach(plan -> supersede(plan, now));
        roomRepository.findBySettingsIdOrderBySortOrderAscIdAsc(settings.getId()).stream()
                .flatMap(room -> room.getHeatSources().stream())
                .map(HeatingPlannerRoomHeatSourceEntity::getControllingDevice)
                .filter(device -> device != null)
                .forEach(device -> gatewayDeviceRepository.findByDeviceId(device.getId()).ifPresent(link -> {
                    if ("HEATING_PLANNER".equals(link.getDesiredSource())) {
                        link.setDesiredExpiresAt(now);
                        gatewayDeviceRepository.save(link);
                    }
                }));
    }

    private void validateRoom(HeatingPlannerRoomEntity room, Instant now, List<String> issues) {
        String prefix = room.getName() + ": ";
        HeatingPlannerRoomHeatSourceEntity source = room.getHeatSources().stream()
                .filter(HeatingPlannerRoomHeatSourceEntity::isEnabled)
                .filter(candidate -> candidate.getSourceType() == HeatingPlannerHeatSourceType.FLOOR_HEATING)
                .findFirst().orElse(null);
        if (source == null || source.getControllingDevice() == null) {
            issues.add(prefix + "controlling thermostat is missing");
        } else {
            gatewayDeviceRepository.findByDeviceId(source.getControllingDevice().getId()).ifPresentOrElse(link -> {
                if (link.getLastSeen() == null || link.getLastSeen().isBefore(now.minus(GATEWAY_FRESHNESS)))
                    issues.add(prefix + "thermostat gateway report is stale");
                if (link.getDesiredVersion() > link.getAppliedVersion())
                    issues.add(prefix + "thermostat has an unacknowledged command");
                if (link.getReportedSetpoint() == null || link.getReportedMode() == null)
                    issues.add(prefix + "thermostat has not reported readable state");
            }, () -> issues.add(prefix + "thermostat is not linked to a Zigbee gateway"));
        }
        if (room.getRoomSensorDevice() == null) issues.add(prefix + "explicit room sensor is missing");
        if (room.getFloorSensorDevice() == null) issues.add(prefix + "explicit floor sensor is missing");
        if (!measurementService.latestFreshRoomTemperature(room, now).fresh())
            issues.add(prefix + "room-temperature measurement is missing or stale");
        if (!measurementService.latestFreshFloorTemperature(room, now).fresh())
            issues.add(prefix + "floor-temperature measurement is missing or stale");
        if (!room.isModelParametersLearned() || room.getModelConfidence() == null
                || room.getModelConfidence().compareTo(MINIMUM_MODEL_CONFIDENCE) < 0)
            issues.add(prefix + "learned model confidence must be at least 25%");
    }

    private boolean hasEnabledFloorHeating(HeatingPlannerRoomEntity room) {
        return room.getHeatSources().stream().anyMatch(source -> source.isEnabled()
                && source.getSourceType() == HeatingPlannerHeatSourceType.FLOOR_HEATING);
    }

    private HeatingPlannerPlanEntity latestSimulatedPlan(Long settingsId) {
        return planRepository.findBySettingsIdAndStatusOrderByCreatedAtDesc(settingsId, HeatingPlannerPlanStatus.SIMULATED)
                .stream().findFirst().orElse(null);
    }

    private void supersede(HeatingPlannerPlanEntity plan, Instant now) {
        plan.setStatus(HeatingPlannerPlanStatus.SUPERSEDED);
        plan.setSupersededAt(now);
        var points = pointRepository.findByPlanVersion(plan.getPlanVersion());
        points.forEach(point -> point.setStatus(HeatingPlannerPlanPointStatus.SUPERSEDED));
        pointRepository.saveAll(points);
        planRepository.save(plan);
    }

    public record Readiness(boolean ready, boolean active, List<String> issues, String candidatePlanVersion,
                            Instant lastAutomaticPlanAt, Instant lastAutomaticActivationAt,
                            String lastAutomationError) { }
}
