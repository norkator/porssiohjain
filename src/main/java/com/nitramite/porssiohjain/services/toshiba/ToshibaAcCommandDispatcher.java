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

package com.nitramite.porssiohjain.services.toshiba;

import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.services.AcCommandDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToshibaAcCommandDispatcher implements AcCommandDispatcher {

    private final ToshibaRegisterControllerService toshibaRegisterControllerService;
    private final ToshibaAcAmqpSendService toshibaAcAmqpSendService;

    @Override
    public boolean supports(AcType acType) {
        return AcType.TOSHIBA == acType;
    }

    @Override
    public void dispatchHexState(
            DeviceAcDataEntity acData, String hexState
    ) {
        if (hexState == null || hexState.isBlank()) {
            throw new IllegalArgumentException("Hex state cannot be blank for Toshiba dispatch");
        }

        if (acData.getSasToken() == null || acData.getSasToken().isBlank()) {
            log.info("Toshiba dispatcher registering client because sasToken is empty. deviceId={}", acData.getDevice().getId());
            String sasToken = toshibaRegisterControllerService.registerClient(acData);
            if (sasToken == null) {
                throw new IllegalStateException("Failed to register Toshiba client before hex state dispatch");
            }
        }

        log.info(
                "Sending Toshiba heat pump hex state. deviceId={}, acDataId={}, acDeviceId={}, hex={}",
                acData.getDevice().getId(),
                acData.getId(),
                acData.getAcDeviceId(),
                hexState
        );
        toshibaAcAmqpSendService.sendHexState(acData, hexState);
    }

}
