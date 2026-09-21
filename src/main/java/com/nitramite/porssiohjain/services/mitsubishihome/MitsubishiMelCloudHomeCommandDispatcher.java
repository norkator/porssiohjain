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
package com.nitramite.porssiohjain.services.mitsubishihome;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.services.AcCommandDispatcher;
import com.nitramite.porssiohjain.services.SystemLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MitsubishiMelCloudHomeCommandDispatcher implements AcCommandDispatcher {

    private static final Set<String> ATA_OPERATION_MODES = Set.of("Heat", "Cool", "Automatic", "Dry", "Fan");
    private static final Set<String> ATA_FAN_SPEEDS = Set.of("Auto", "One", "Two", "Three", "Four", "Five");
    private static final Set<String> ATA_VERTICAL_VANES = Set.of("Auto", "Swing", "One", "Two", "Three", "Four", "Five");
    private static final Set<String> ATA_HORIZONTAL_VANES = Set.of(
            "Auto", "Swing", "Left", "LeftCentre", "Centre", "RightCentre", "Right"
    );
    private static final Set<String> ATW_ZONE_MODES = Set.of(
            "HeatRoomTemperature", "HeatFlowTemperature", "HeatCurve",
            "CoolRoomTemperature", "CoolFlowTemperature"
    );

    private final MitsubishiMelCloudHomeLoginService loginService;
    private final MitsubishiMelCloudHomeApiClient apiClient;
    private final DeviceAcDataRepository deviceAcDataRepository;
    private final SystemLogService systemLogService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(AcType acType) {
        return acType == AcType.MITSUBISHI_MELCLOUD_HOME;
    }

    @Override
    public void dispatchHexState(DeviceAcDataEntity acData, String stateJson) {
        MitsubishiMelCloudHomeState state = parseState(stateJson);
        String unitId = requireConfiguredUnitId(acData);
        if (state.getUnitId() != null && !unitId.equals(state.getUnitId())) {
            throw new IllegalArgumentException("MELCloud Home state belongs to a different unit");
        }
        if (state.getUnitType() == null) {
            throw new IllegalArgumentException("MELCloud Home unit type is required in state JSON");
        }
        if (!loginService.login(acData).isSuccess()) {
            throw new MitsubishiMelCloudHomeException("MELCloud Home login failed before command dispatch");
        }

        if (state.getUnitType() == MitsubishiMelCloudHomeState.UnitType.AIR_TO_AIR) {
            validateOptional(state.getOperationMode(), ATA_OPERATION_MODES, "operation mode");
            validateOptional(state.getSetFanSpeed(), ATA_FAN_SPEEDS, "fan speed");
            validateOptional(state.getVaneVerticalDirection(), ATA_VERTICAL_VANES, "vertical vane direction");
            validateOptional(state.getVaneHorizontalDirection(), ATA_HORIZONTAL_VANES, "horizontal vane direction");
            apiClient.controlAtaUnit(acData.getAcAccessToken(), unitId, new MitsubishiMelCloudHomeApiClient.AtaControlRequest(
                    state.getPower(),
                    state.getOperationMode(),
                    state.getSetTemperature(),
                    state.getSetFanSpeed(),
                    state.getVaneVerticalDirection(),
                    state.getVaneHorizontalDirection(),
                    null,
                    state.getInStandbyMode()
            ));
        } else {
            validateOptional(state.getOperationModeZone1(), ATW_ZONE_MODES, "zone 1 operation mode");
            validateOptional(state.getOperationModeZone2(), ATW_ZONE_MODES, "zone 2 operation mode");
            apiClient.controlAtwUnit(acData.getAcAccessToken(), unitId, new MitsubishiMelCloudHomeApiClient.AtwControlRequest(
                    state.getPower(),
                    state.getSetTemperatureZone1(),
                    state.getSetTemperatureZone2(),
                    state.getOperationModeZone1(),
                    state.getOperationModeZone2(),
                    state.getSetTankWaterTemperature(),
                    state.getForcedHotWaterMode(),
                    state.getInStandbyMode(),
                    state.getSetHeatFlowTemperatureZone1(),
                    state.getSetCoolFlowTemperatureZone1(),
                    state.getSetHeatFlowTemperatureZone2(),
                    state.getSetCoolFlowTemperatureZone2()
            ));
        }

        String formattedState = formatState(state);
        acData.setLastSentStateHex(formattedState);
        deviceAcDataRepository.save(acData);
        log.info(
                "MELCloud Home AC state changed. name={}, unitId={}, unitType={}",
                state.getName() != null && !state.getName().isBlank() ? state.getName() : acData.getName(),
                unitId,
                state.getUnitType()
        );
        systemLogService.log(String.format(
                "MELCloud Home control sent. deviceId=%s, acDataId=%s, unitId=%s, unitType=%s",
                acData.getDevice() != null ? acData.getDevice().getId() : null,
                acData.getId(),
                unitId,
                state.getUnitType()
        ));
    }

    private MitsubishiMelCloudHomeState parseState(String stateJson) {
        if (stateJson == null || stateJson.isBlank()) {
            throw new IllegalArgumentException("MELCloud Home state JSON cannot be blank");
        }
        try {
            return objectMapper.readValue(stateJson, MitsubishiMelCloudHomeState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid MELCloud Home state JSON", e);
        }
    }

    private String formatState(MitsubishiMelCloudHomeState state) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to format MELCloud Home state JSON", e);
        }
    }

    private String requireConfiguredUnitId(DeviceAcDataEntity acData) {
        if (acData.getAcDeviceId() == null || acData.getAcDeviceId().isBlank()) {
            throw new IllegalArgumentException("MELCloud Home unit ID is required");
        }
        return acData.getAcDeviceId();
    }

    private void validateOptional(String value, Set<String> allowedValues, String fieldName) {
        if (value != null && !allowedValues.contains(value)) {
            throw new IllegalArgumentException("Unsupported MELCloud Home " + fieldName + ": " + value);
        }
    }
}
