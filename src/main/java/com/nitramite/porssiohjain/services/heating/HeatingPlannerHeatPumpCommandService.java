/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services.heating;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiAcStateResponse;
import com.nitramite.porssiohjain.services.mitsubishihome.MitsubishiMelCloudHomeState;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateHexEditorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HeatingPlannerHeatPumpCommandService {

    private static final Duration MAXIMUM_PLAN_AGE = Duration.ofMinutes(75);
    private static final long MITSUBISHI_HEATING_FLAGS = 0x01L | 0x02L | 0x04L;

    private final HeatingPlannerRoomHeatSourceRepository heatSourceRepository;
    private final HeatingPlannerPlanRepository planRepository;
    private final HeatingPlannerPlanPointRepository pointRepository;
    private final DeviceAcDataRepository acDataRepository;
    private final HeatingPlannerMeasurementService measurementService;
    private final ToshibaAcStateHexEditorService toshibaEditor;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<HeatPumpPlanCommand> currentCommands(Instant now) {
        List<HeatPumpPlanCommand> commands = new ArrayList<>();
        for (HeatingPlannerRoomHeatSourceEntity source : heatSourceRepository.findAll()) {
            if (!eligible(source, now)) continue;
            HeatingPlannerPlanEntity plan = latestPlan(source.getRoom(), now);
            if (plan == null) continue;
            HeatingPlannerPlanPointEntity point = pointRepository
                    .findByPlanVersionAndRoomIdAndPlannedTimeBetweenOrderByPlannedTimeAsc(
                            plan.getPlanVersion(), source.getRoom().getId(), now.minus(Duration.ofHours(2)), now)
                    .stream().filter(candidate -> candidate.getPlannedHeatPumpSetpoint() != null)
                    .reduce((left, right) -> right).orElse(null);
            if (point == null || point.getPlannedTime().isBefore(now.minus(MAXIMUM_PLAN_AGE))) continue;
            DeviceAcDataEntity acData = acDataRepository.findByDevice(source.getControllingDevice()).orElse(null);
            if (acData == null) continue;
            String state = stateWithSetpoint(acData, point);
            if (state != null) {
                commands.add(new HeatPumpPlanCommand(source.getControllingDevice(), state, source.getId(),
                        point.getReason() + "; requested room setpoint " + point.getPlannedHeatPumpSetpoint() + " °C",
                        point.getPlannedHeatPumpSetpoint()));
            }
        }
        return List.copyOf(commands);
    }

    private boolean eligible(HeatingPlannerRoomHeatSourceEntity source, Instant now) {
        return source.isEnabled()
                && source.getSourceType() == HeatingPlannerHeatSourceType.HEAT_PUMP
                && source.getControllingDevice() != null
                && source.getControllingDevice().isEnabled()
                && source.getRoom() != null && source.getRoom().isEnabled()
                && source.getRoom().getSettings() != null && source.getRoom().getSettings().isEnabled()
                && source.getRoom().getSettings().isHeatPumpControlEnabled()
                && measurementService.latestFreshRoomTemperature(source.getRoom(), now).fresh();
    }

    private HeatingPlannerPlanEntity latestPlan(HeatingPlannerRoomEntity room, Instant now) {
        return planRepository.findByAccountIdAndSiteIdOrderByCreatedAtDesc(
                        room.getAccount().getId(), room.getSite().getId()).stream()
                .filter(plan -> plan.getSettings().getId().equals(room.getSettings().getId()))
                .filter(plan -> plan.getStatus() == HeatingPlannerPlanStatus.SIMULATED
                        || plan.getStatus() == HeatingPlannerPlanStatus.ACTIVE)
                .filter(plan -> plan.getCreatedAt() != null && !plan.getCreatedAt().isBefore(now.minus(MAXIMUM_PLAN_AGE)))
                .filter(plan -> !plan.getHorizonStart().isAfter(now) && plan.getHorizonEnd().isAfter(now))
                .findFirst().orElse(null);
    }

    private String stateWithSetpoint(DeviceAcDataEntity acData, HeatingPlannerPlanPointEntity point) {
        String base = firstNonBlank(acData.getLastPolledStateHex(), acData.getLastSentStateHex());
        if (base == null) return null;
        if (acData.getAcType() == AcType.TOSHIBA) {
            try {
                int target = point.getPlannedHeatPumpSetpoint().setScale(0, RoundingMode.HALF_UP).intValue();
                return toshibaEditor.applyEditableSettings(base, true,
                        ToshibaAcStateHexEditorService.EditableMode.HEAT, target);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        if (acData.getAcType() == AcType.MITSUBISHI_MELCLOUD) {
            try {
                MitsubishiAcStateResponse state = objectMapper.readValue(base, MitsubishiAcStateResponse.class);
                double requested = point.getPlannedHeatPumpSetpoint().doubleValue();
                if (Boolean.TRUE.equals(state.getPower()) && Integer.valueOf(1).equals(state.getOperationMode())
                        && state.getSetTemperature() != null
                        && Math.abs(state.getSetTemperature() - requested) < 0.01d) {
                    return null;
                }
                state.setPower(true);
                state.setOperationMode(1);
                state.setSetTemperature(requested);
                state.setEffectiveFlags(MITSUBISHI_HEATING_FLAGS);
                state.setHasPendingCommand(true);
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state);
            } catch (JsonProcessingException ignored) {
                return null;
            }
        }
        if (acData.getAcType() == AcType.MITSUBISHI_MELCLOUD_HOME) {
            try {
                MitsubishiMelCloudHomeState state = objectMapper.readValue(base, MitsubishiMelCloudHomeState.class);
                if (state.getUnitType() != MitsubishiMelCloudHomeState.UnitType.AIR_TO_AIR) return null;
                double requested = point.getPlannedHeatPumpSetpoint().doubleValue();
                if (Boolean.TRUE.equals(state.getPower()) && "Heat".equals(state.getOperationMode())
                        && state.getSetTemperature() != null
                        && Math.abs(state.getSetTemperature() - requested) < 0.01d) return null;
                state.setPower(true);
                state.setOperationMode("Heat");
                state.setSetTemperature(requested);
                return objectMapper.writeValueAsString(state);
            } catch (JsonProcessingException ignored) {
                return null;
            }
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second == null || second.isBlank() ? null : second;
    }

    public record HeatPumpPlanCommand(DeviceEntity device, String state, Long sourceId, String reason,
                                      BigDecimal targetTemperature) { }
}
