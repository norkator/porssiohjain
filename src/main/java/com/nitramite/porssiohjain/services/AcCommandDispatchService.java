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
