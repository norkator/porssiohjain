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
import org.springframework.stereotype.Service;

@Service
public class MitsubishiMelCloudHomeLoginService {

    public void login(DeviceAcDataEntity acData) {
        throw new UnsupportedOperationException("MELCloud Home login is not implemented yet");
    }
}
