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

package com.nitramite.porssiohjain.services.mitsubishi;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class MitsubishiAcCommandDispatcher implements AcCommandDispatcher {

    private static final long EFFECTIVE_FLAG_POWER = 0x01L;
    private static final long EFFECTIVE_FLAG_MODE = 0x02L;
    private static final long EFFECTIVE_FLAG_TEMPERATURE = 0x04L;
    private static final long EFFECTIVE_FLAG_FAN_SPEED = 0x08L;
    private static final long EFFECTIVE_FLAG_VANE_VERTICAL = 0x10L;
    private static final long EFFECTIVE_FLAG_VANE_HORIZONTAL = 0x100L;

    private final MitsubishiAcStateService mitsubishiAcStateService;
    private final MitsubishiSetAcStateService mitsubishiSetAcStateService;
    private final DeviceAcDataRepository deviceAcDataRepository;
    private final SystemLogService systemLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(AcType acType) {
        return AcType.MITSUBISHI == acType;
    }

    @Override
    public void dispatchHexState(DeviceAcDataEntity acData, String jsonState) {
        if (jsonState == null || jsonState.isBlank()) {
            throw new IllegalArgumentException("JSON state cannot be blank for Mitsubishi dispatch");
        }

        MitsubishiAcStateResponse state = parseState(jsonState);
        if (state.getDeviceId() == null) {
            state.setDeviceId(parseAcDeviceId(acData.getAcDeviceId()));
        }
        if (state.getEffectiveFlags() == null || state.getEffectiveFlags() == 0L) {
            throw new IllegalArgumentException("EffectiveFlags must include at least one Mitsubishi state field");
        }

        MitsubishiAcStateResponse setAtaState = buildSetAtaState(acData, state);
        String formattedState = formatState(setAtaState);
        log.info(
                "Dry-run Mitsubishi heat pump JSON state dispatch. deviceId={}, acDataId={}, acDeviceId={}, effectiveFlags={}",
                acData.getDevice().getId(),
                acData.getId(),
                acData.getAcDeviceId(),
                setAtaState.getEffectiveFlags()
        );
        systemLogService.log(String.format(
                "Dry-run Mitsubishi SetAta skipped. deviceId=%s, acDataId=%s, acDeviceId=%s, buildingId=%s, effectiveFlags=%s, payload=%s",
                acData.getDevice().getId(),
                acData.getId(),
                acData.getAcDeviceId(),
                acData.getBuildingId(),
                setAtaState.getEffectiveFlags(),
                formattedState
        ));

        // Uncomment this block to enable real Mitsubishi MELCloud SetAta dispatch.
        /*
        MitsubishiSetAcStateResponse response = mitsubishiSetAcStateService.setAcState(acData, setAtaState);
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException(getErrorMessage(response));
        }

        acData.setLastPolledStateHex(formattedState);
        deviceAcDataRepository.save(acData);
        */
    }

    private MitsubishiAcStateResponse buildSetAtaState(DeviceAcDataEntity acData, MitsubishiAcStateResponse commandState) {
        MitsubishiAcStateResponse currentState = mitsubishiAcStateService.getAcState(acData);
        if (currentState == null) {
            throw new IllegalStateException("Failed to poll current Mitsubishi AC state before dispatch");
        }

        if (currentState.getDeviceId() == null) {
            currentState.setDeviceId(commandState.getDeviceId() != null
                    ? commandState.getDeviceId()
                    : parseAcDeviceId(acData.getAcDeviceId()));
        }

        Long effectiveFlags = commandState.getEffectiveFlags();
        if (hasFlag(effectiveFlags, EFFECTIVE_FLAG_POWER)) {
            currentState.setPower(requireValue(commandState.getPower(), "Power"));
        }
        if (hasFlag(effectiveFlags, EFFECTIVE_FLAG_MODE)) {
            currentState.setOperationMode(requireValue(commandState.getOperationMode(), "OperationMode"));
        }
        if (hasFlag(effectiveFlags, EFFECTIVE_FLAG_TEMPERATURE)) {
            currentState.setSetTemperature(requireValue(commandState.getSetTemperature(), "SetTemperature"));
        }
        if (hasFlag(effectiveFlags, EFFECTIVE_FLAG_FAN_SPEED)) {
            currentState.setSetFanSpeed(requireValue(commandState.getSetFanSpeed(), "SetFanSpeed"));
        }
        if (hasFlag(effectiveFlags, EFFECTIVE_FLAG_VANE_VERTICAL)) {
            currentState.setVaneVertical(requireValue(commandState.getVaneVertical(), "VaneVertical"));
        }
        if (hasFlag(effectiveFlags, EFFECTIVE_FLAG_VANE_HORIZONTAL)) {
            currentState.setVaneHorizontal(requireValue(commandState.getVaneHorizontal(), "VaneHorizontal"));
        }

        currentState.setEffectiveFlags(effectiveFlags);
        currentState.setHasPendingCommand(true);
        return currentState;
    }

    private boolean hasFlag(Long effectiveFlags, long flag) {
        return effectiveFlags != null && (effectiveFlags & flag) == flag;
    }

    private <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required when EffectiveFlags includes it");
        }
        return value;
    }

    private MitsubishiAcStateResponse parseState(String jsonState) {
        try {
            return objectMapper.readValue(jsonState, MitsubishiAcStateResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid Mitsubishi state JSON", e);
        }
    }

    private String formatState(MitsubishiAcStateResponse state) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to format Mitsubishi state JSON", e);
        }
    }

    private Long parseAcDeviceId(String acDeviceId) {
        if (acDeviceId == null || acDeviceId.isBlank()) {
            throw new IllegalArgumentException("Mitsubishi DeviceID is required");
        }
        return Long.valueOf(acDeviceId);
    }

    private String getErrorMessage(MitsubishiSetAcStateResponse response) {
        if (response == null) {
            return "Empty Mitsubishi SetAta response";
        }
        if (response.getErrorMessage() != null && !response.getErrorMessage().isBlank()) {
            return response.getErrorMessage();
        }
        return response.getErrorCode() != null
                ? "Mitsubishi SetAta failed with error code " + response.getErrorCode()
                : "Mitsubishi SetAta failed";
    }

}
