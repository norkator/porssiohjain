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

package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.services.PowerLimitService;
import com.nitramite.porssiohjain.services.models.CurrentKwRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/power")
@RequiredArgsConstructor
public class PowerController {

    private final PowerLimitService powerLimitService;

    @PostMapping("/{deviceUuid}")
    public void updateCurrentKw(
            @PathVariable String deviceUuid,
            @RequestBody CurrentKwRequest request
    ) {
        powerLimitService.updateCurrentKw(
                deviceUuid, request
        );
    }

}