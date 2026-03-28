/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
