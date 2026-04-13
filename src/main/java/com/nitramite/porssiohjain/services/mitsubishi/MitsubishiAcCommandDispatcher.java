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
        if (state.getEffectiveFlags() == null) {
            throw new IllegalArgumentException("EffectiveFlags is required for Mitsubishi dispatch");
        }

        String formattedState = formatState(state);
        log.info(
                "Dry-run Mitsubishi heat pump JSON state dispatch. deviceId={}, acDataId={}, acDeviceId={}, effectiveFlags={}",
                acData.getDevice().getId(),
                acData.getId(),
                acData.getAcDeviceId(),
                state.getEffectiveFlags()
        );
        systemLogService.log(String.format(
                "Dry-run Mitsubishi SetAta skipped. deviceId=%s, acDataId=%s, acDeviceId=%s, buildingId=%s, effectiveFlags=%s, payload=%s",
                acData.getDevice().getId(),
                acData.getId(),
                acData.getAcDeviceId(),
                acData.getBuildingId(),
                state.getEffectiveFlags(),
                formattedState
        ));

        // Uncomment this block to enable real Mitsubishi MELCloud SetAta dispatch.
        /*
        MitsubishiSetAcStateResponse response = mitsubishiSetAcStateService.setAcState(acData, state);
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException(getErrorMessage(response));
        }

        acData.setLastPolledStateHex(formattedState);
        deviceAcDataRepository.save(acData);
        */
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
