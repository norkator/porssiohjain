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
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanPointStatus;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerPlanPointRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerPlanRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerRoomRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HeatingPlannerPlanService {

    private final HeatingPlannerSettingsRepository settingsRepository;
    private final HeatingPlannerRoomRepository roomRepository;
    private final HeatingPlannerPlanRepository planRepository;
    private final HeatingPlannerPlanPointRepository pointRepository;

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
            result.points().forEach(point -> plan.getPoints().add(HeatingPlannerPlanPointEntity.builder()
                    .plan(plan).room(room).account(settings.getAccount()).site(settings.getSite())
                    .planVersion(plan.getPlanVersion()).plannedTime(point.time())
                    .priceCentsPerKwh(point.priceCentsPerKwh()).outdoorTemperature(point.outdoorTemperature())
                    .predictedFloorTemperature(point.floorTemperature())
                    .predictedRoomTemperature(point.roomTemperature()).plannedFloorSetpoint(point.floorSetpoint())
                    .predictedWoodHeatRate(point.woodRoomHeatingRate()).heating(point.heating())
                    .operatingMode(point.mode()).reason(point.reason())
                    .status(HeatingPlannerPlanPointStatus.SIMULATED).build()));
        });
        planRepository.save(plan);
        return true;
    }
}
