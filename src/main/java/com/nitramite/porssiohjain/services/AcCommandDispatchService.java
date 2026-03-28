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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.enums.AcType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcCommandDispatchService {

    private final List<AcCommandDispatcher> dispatchers;

    public void dispatchHexState(DeviceAcDataEntity acData, String hexState) {
        AcType acType = acData.getAcType();
        dispatchers.stream()
                .filter(dispatcher -> dispatcher.supports(acType))
                .findFirst()
                .ifPresentOrElse(
                        dispatcher -> dispatcher.dispatchHexState(acData, hexState),
                        () -> log.warn(
                                "No AC command dispatcher configured for deviceId={} acType={}",
                                acData.getDevice().getId(),
                                acType
                        )
                );
    }

}
