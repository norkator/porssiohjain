/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.HeatingPlannerPlanEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerPlanPointEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerRoomEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerRoomHeatSourceEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerSettingsEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerWoodRecommendationEntity;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanPointStatus;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerWoodRecommendationStatus;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerPlanPointRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerPlanRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerRoomRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerSettingsRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerWoodRecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HeatingPlannerPlanService {
    static final Duration OLD_PLAN_RETENTION = Duration.ofDays(14);

    private final HeatingPlannerSettingsRepository settingsRepository;
    private final HeatingPlannerRoomRepository roomRepository;
    private final HeatingPlannerPlanRepository planRepository;
    private final HeatingPlannerPlanPointRepository pointRepository;
    private final HeatingPlannerWoodRecommendationRepository woodRecommendationRepository;

    @Transactional(readOnly = true)
    public Map<String, List<HeatingPlanSimulationService.SimulationPoint>> chartHistory(
            Long accountId, Long siteId, Instant start, Instant end) {
        if (accountId == null || siteId == null || !start.isBefore(end)) {
            return Map.of();
        }
        Map<String, List<HeatingPlanSimulationService.SimulationPoint>> history = new LinkedHashMap<>();
        for (var point : pointRepository.findLatestHistoricalPredictions(accountId, siteId, start, end)) {
            history.computeIfAbsent(point.getRoom().getName(), ignored -> new ArrayList<>()).add(
                    new HeatingPlanSimulationService.SimulationPoint(point.getPlannedTime(),
                            point.getPriceCentsPerKwh(), point.getOutdoorTemperature(),
                            point.getPredictedFloorTemperature(), point.getPredictedRoomTemperature(),
                            point.getPlannedFloorSetpoint(), point.getPredictedWoodHeatRate(),
                            point.isHeating(), point.getOperatingMode(), point.getReason(), point.getComfortTarget()));
        }
        return history;
    }

    @Transactional
    public boolean persistSimulatedPlan(Long accountId, Long siteId,
                                        Map<String, HeatingPlanSimulationService.SimulationResult> resultsByRoomName) {
        if (accountId == null || siteId == null || resultsByRoomName == null || resultsByRoomName.isEmpty()) {
            return false;
        }
        var settings = settingsRepository.findByAccountIdAndSiteId(accountId, siteId).orElse(null);
        if (settings == null) {
            return false;
        }
        Map<String, HeatingPlannerRoomEntity> rooms = new LinkedHashMap<>();
        roomRepository.findBySettingsIdOrderBySortOrderAscIdAsc(settings.getId())
                .forEach(room -> rooms.put(room.getName(), room));
        if (!rooms.keySet().containsAll(resultsByRoomName.keySet())) {
            return false;
        }

        Instant supersededAt = Instant.now();
        for (HeatingPlannerPlanEntity previous : planRepository
                .findBySettingsIdAndStatusOrderByCreatedAtDesc(settings.getId(), HeatingPlannerPlanStatus.SIMULATED)) {
            previous.setStatus(HeatingPlannerPlanStatus.SUPERSEDED);
            previous.setSupersededAt(supersededAt);
            planRepository.save(previous);
            var oldPoints = pointRepository.findByPlanVersion(previous.getPlanVersion());
            oldPoints.forEach(point -> point.setStatus(HeatingPlannerPlanPointStatus.SUPERSEDED));
            pointRepository.saveAll(oldPoints);
            var oldRecommendations = woodRecommendationRepository.findByPlanIdAndStatus(
                    previous.getId(), HeatingPlannerWoodRecommendationStatus.PENDING);
            oldRecommendations.forEach(recommendation ->
                    recommendation.setStatus(HeatingPlannerWoodRecommendationStatus.SUPERSEDED));
            woodRecommendationRepository.saveAll(oldRecommendations);
        }

        Instant horizonStart = resultsByRoomName.values().stream()
                .flatMap(result -> result.points().stream())
                .map(HeatingPlanSimulationService.SimulationPoint::time)
                .min(Instant::compareTo).orElseThrow();
        Instant horizonEnd = resultsByRoomName.values().stream()
                .flatMap(result -> result.points().stream())
                .map(HeatingPlanSimulationService.SimulationPoint::time)
                .max(Instant::compareTo).orElseThrow();
        HeatingPlannerPlanEntity plan = planRepository.save(HeatingPlannerPlanEntity.builder()
                .settings(settings).account(settings.getAccount()).site(settings.getSite())
                .horizonStart(horizonStart).horizonEnd(horizonEnd)
                .triggerReason("Heating Planner simulation recalculated")
                .status(HeatingPlannerPlanStatus.SIMULATED).build());

        resultsByRoomName.forEach((roomName, result) -> {
            HeatingPlannerRoomEntity room = rooms.get(roomName);
            HeatingPlannerRoomHeatSourceEntity heatPump = room.getHeatSources().stream()
                    .filter(HeatingPlannerRoomHeatSourceEntity::isEnabled)
                    .filter(source -> source.getSourceType() == HeatingPlannerHeatSourceType.HEAT_PUMP)
                    .findFirst().orElse(null);
            result.points().forEach(point -> plan.getPoints().add(HeatingPlannerPlanPointEntity.builder()
                    .plan(plan).room(room).account(settings.getAccount()).site(settings.getSite())
                    .planVersion(plan.getPlanVersion()).plannedTime(point.time())
                    .priceCentsPerKwh(point.priceCentsPerKwh()).outdoorTemperature(point.outdoorTemperature())
                    .predictedFloorTemperature(point.floorTemperature())
                    .predictedRoomTemperature(point.roomTemperature()).plannedFloorSetpoint(point.floorSetpoint()).comfortTarget(point.comfortTarget())
                    .plannedHeatPumpSetpoint(heatPumpSetpoint(room, heatPump, point))
                    .predictedWoodHeatRate(point.woodRoomHeatingRate()).heating(point.heating())
                    .operatingMode(point.mode()).reason(point.reason())
                    .status(HeatingPlannerPlanPointStatus.SIMULATED).build()));
        });
        planRepository.save(plan);
        persistWoodRecommendation(plan, settings, rooms, resultsByRoomName);
        return true;
    }

    private BigDecimal heatPumpSetpoint(HeatingPlannerRoomEntity room,
                                        HeatingPlannerRoomHeatSourceEntity heatPump,
                                        HeatingPlanSimulationService.SimulationPoint point) {
        if (heatPump == null) {
            return null;
        }
        return plannedHeatPumpSetpoint(point.comfortTarget() == null ? room.getTargetRoomTemperature() : point.comfortTarget(), room.getMinimumRoomTemperature(),
                heatPump.getHeatPumpTemperatureAdjustment(), heatPump.isHeatPumpPriceOptimizationEnabled(), point);
    }

    public static BigDecimal plannedHeatPumpSetpoint(BigDecimal target, BigDecimal minimumRoomTemperature,
                                                      BigDecimal configuredAdjustment, boolean priceOptimizationEnabled,
                                                      HeatingPlanSimulationService.SimulationPoint point) {
        return plannedHeatPumpDecision(target, minimumRoomTemperature, configuredAdjustment,
                priceOptimizationEnabled, point).setpoint();
    }

    public static HeatPumpSetpointDecision plannedHeatPumpDecision(BigDecimal target, BigDecimal minimumRoomTemperature,
                                                                   BigDecimal configuredAdjustment, boolean priceOptimizationEnabled,
                                                                   HeatingPlanSimulationService.SimulationPoint point) {
        if (point == null || point.mode() == HeatingPlanSimulationService.OperatingMode.INACTIVE) {
            return new HeatPumpSetpointDecision(null, point == null ? "No current plan point" : point.reason());
        }
        BigDecimal adjustment = configuredAdjustment == null ? new BigDecimal("2.00") : configuredAdjustment;
        BigDecimal requested = target;
        String reason = "Maintain the comfort target";
        if (point.roomTemperature().compareTo(minimumRoomTemperature) < 0
                || point.mode() == HeatingPlanSimulationService.OperatingMode.COMFORT_RECOVERY) {
            requested = target.add(adjustment);
            reason = "Comfort recovery in the simulated plan; comfort minimum " + minimumRoomTemperature
                    + " °C; request comfort target + adjustment";
        } else if (priceOptimizationEnabled) {
            if (point.mode() == HeatingPlanSimulationService.OperatingMode.PREHEAT) {
                requested = target.add(adjustment);
                reason = "Selected cheap preheat period; request comfort target + adjustment";
            } else if (point.mode() == HeatingPlanSimulationService.OperatingMode.DISCHARGE) {
                requested = target.subtract(adjustment);
                reason = "Discharge in the simulated plan; price shifting requests comfort target - adjustment";
            }
        } else {
            BigDecimal deadband = new BigDecimal("0.25");
            if (point.roomTemperature().compareTo(target.subtract(deadband)) < 0) {
                requested = target.add(adjustment);
                reason = "Predicted room temperature is below the comfort target's ±0.25 °C band; request comfort target + adjustment";
            } else if (point.roomTemperature().compareTo(target.add(deadband)) > 0) {
                requested = target.subtract(adjustment);
                reason = "Predicted room temperature is above the comfort target's ±0.25 °C band; request comfort target - adjustment";
            }
        }
        return new HeatPumpSetpointDecision(requested.max(new BigDecimal("16.00")).min(new BigDecimal("30.00")),
                reason + " (target " + target + " °C, adjustment ±" + adjustment + " °C)");
    }

    public record HeatPumpSetpointDecision(BigDecimal setpoint, String reason) { }

    @Transactional
    public CleanupResult cleanupOldPlans(Instant now) {
        Instant cutoff = now.minus(OLD_PLAN_RETENTION);
        HeatingPlannerPlanStatus preservedStatus = HeatingPlannerPlanStatus.ACTIVE;
        int recommendationsDeleted = woodRecommendationRepository
                .deleteByPlanEndedBeforeAndPlanStatusNot(cutoff, preservedStatus);
        int pointsDeleted = pointRepository.deleteByPlanEndedBeforeAndPlanStatusNot(cutoff, preservedStatus);
        int plansDeleted = planRepository.deleteEndedBeforeAndStatusNot(cutoff, preservedStatus);
        return new CleanupResult(cutoff, plansDeleted, pointsDeleted, recommendationsDeleted);
    }

    private void persistWoodRecommendation(HeatingPlannerPlanEntity plan, HeatingPlannerSettingsEntity settings,
                                           Map<String, HeatingPlannerRoomEntity> rooms,
                                           Map<String, HeatingPlanSimulationService.SimulationResult> resultsByRoomName) {
        var selected = resultsByRoomName.entrySet().stream()
                .filter(entry -> entry.getValue().plannerActive())
                .filter(entry -> entry.getValue().woodStoveRecommendation() != null)
                .min(Comparator.comparing(entry -> entry.getValue().woodStoveRecommendation().notifyAt()))
                .orElse(null);
        if (selected == null) return;

        HeatingPlanSimulationService.WoodStoveRecommendation recommendation =
                selected.getValue().woodStoveRecommendation();
        HeatingPlannerWoodRecommendationEntity entity = woodRecommendationRepository
                .findBySettingsIdAndReleaseStartsAt(settings.getId(), recommendation.releaseStartsAt())
                .orElseGet(HeatingPlannerWoodRecommendationEntity::new);
        if (entity.getId() != null && entity.getStatus() != HeatingPlannerWoodRecommendationStatus.PENDING
                && entity.getStatus() != HeatingPlannerWoodRecommendationStatus.SUPERSEDED) {
            return;
        }
        entity.setSettings(settings);
        entity.setPlan(plan);
        entity.setRoom(rooms.get(selected.getKey()));
        entity.setAccount(settings.getAccount());
        entity.setSite(settings.getSite());
        entity.setPlanVersion(plan.getPlanVersion());
        entity.setLoadName(recommendation.loadName());
        entity.setWoodAmount(recommendation.woodAmount());
        entity.setNotifyAt(recommendation.notifyAt());
        entity.setReleaseStartsAt(recommendation.releaseStartsAt());
        entity.setReleaseEndsAt(recommendation.releaseEndsAt());
        entity.setInitialRoomHeatingRate(recommendation.initialRoomHeatingRate());
        entity.setReason(recommendation.reason());
        entity.setStatus(HeatingPlannerWoodRecommendationStatus.PENDING);
        woodRecommendationRepository.save(entity);
    }

    public record CleanupResult(Instant cutoff, int plansDeleted, int pointsDeleted, int recommendationsDeleted) {
    }
}
