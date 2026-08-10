/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.HeatingPlannerPlanEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerPlanPointEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerRoomEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerRoomHeatSourceEntity;
import com.nitramite.porssiohjain.entity.ZigbeeGatewayDeviceEntity;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerPlanPointRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerPlanRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerRoomHeatSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HeatingPlannerGatewayCommandService {

    private static final Duration POINT_LOOKBACK = Duration.ofHours(2);

    private final HeatingPlannerRoomHeatSourceRepository heatSourceRepository;
    private final HeatingPlannerPlanRepository planRepository;
    private final HeatingPlannerPlanPointRepository pointRepository;

    @Transactional(readOnly = true)
    public Optional<PlannerGatewayCommand> currentCommand(ZigbeeGatewayDeviceEntity link, Instant now) {
        if (link == null || link.getDevice() == null || link.getDevice().getId() == null || now == null) {
            return Optional.empty();
        }
        return heatSourceRepository.findByControllingDeviceIdAndEnabledTrueOrderByIdAsc(link.getDevice().getId())
                .stream()
                .filter(source -> source.getSourceType() == HeatingPlannerHeatSourceType.FLOOR_HEATING)
                .filter(source -> source.getRoom() != null && source.getRoom().isEnabled())
                .filter(source -> source.getRoom().getSettings() != null && source.getRoom().getSettings().isEnabled())
                .filter(source -> source.getRoom().getSettings().isActiveControlEnabled())
                .filter(source -> source.getAccount() != null && source.getAccount().getId().equals(link.getAccount().getId()))
                .flatMap(source -> currentPoint(source.getRoom(), now)
                        .map(point -> new PlannerGatewayCommand(
                                point.getPlannedFloorSetpoint(),
                                "HEAT",
                                point.getReason()))
                        .stream())
                .findFirst();
    }

    private Optional<HeatingPlannerPlanPointEntity> currentPoint(HeatingPlannerRoomEntity room, Instant now) {
        return latestUsablePlan(room)
                .flatMap(plan -> pointRepository
                        .findByPlanVersionAndRoomIdAndPlannedTimeBetweenOrderByPlannedTimeAsc(
                                plan.getPlanVersion(), room.getId(), now.minus(POINT_LOOKBACK), now)
                        .stream()
                        .filter(point -> point.getPlannedFloorSetpoint() != null)
                        .filter(point -> point.getOperatingMode() != HeatingPlanSimulationService.OperatingMode.INACTIVE)
                        .reduce((left, right) -> right));
    }

    private Optional<HeatingPlannerPlanEntity> latestUsablePlan(HeatingPlannerRoomEntity room) {
        return planRepository.findByAccountIdAndSiteIdOrderByCreatedAtDesc(
                        room.getAccount().getId(), room.getSite().getId())
                .stream()
                .filter(plan -> plan.getSettings().getId().equals(room.getSettings().getId()))
                .filter(plan -> plan.getStatus() == HeatingPlannerPlanStatus.ACTIVE)
                .findFirst();
    }

    public record PlannerGatewayCommand(
            BigDecimal targetTemperature,
            String mode,
            String reason
    ) {
    }
}
