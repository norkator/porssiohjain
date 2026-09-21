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

import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.services.AcCommandDispatcher;
import org.springframework.stereotype.Service;

@Service
public class MitsubishiMelCloudHomeCommandDispatcher implements AcCommandDispatcher {

    @Override
    public boolean supports(AcType acType) {
        return acType == AcType.MITSUBISHI_MELCLOUD_HOME;
    }

    @Override
    public void dispatchHexState(DeviceAcDataEntity acData, String state) {
        throw new UnsupportedOperationException("MELCloud Home command dispatch is not implemented yet");
    }
}
