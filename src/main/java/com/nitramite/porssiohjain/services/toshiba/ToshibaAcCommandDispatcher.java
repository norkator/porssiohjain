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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ToshibaAcCommandDispatcher implements AcCommandDispatcher {

    @Override
    public boolean supports(AcType acType) {
        return AcType.TOSHIBA == acType;
    }

    @Override
    public void dispatchHexState(DeviceAcDataEntity acData, String hexState) {
        log.info(
                "Heat pump scheduler would register Toshiba client and send hex state. deviceId={}, acDataId={}, acDeviceId={}, hex={}",
                acData.getDevice().getId(),
                acData.getId(),
                acData.getAcDeviceId(),
                hexState
        );
    }

}
