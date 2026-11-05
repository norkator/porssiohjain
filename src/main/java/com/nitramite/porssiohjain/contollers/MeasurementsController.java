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

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import com.nitramite.porssiohjain.services.MeasurementWidgetQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/measurements")
@RequiredArgsConstructor
@RequireAuth
public class MeasurementsController {
    private final AuthContext authContext;
    private final MeasurementWidgetQueryService query;

    @GetMapping("/latest")
    public List<MeasurementWidgetQueryService.Reading> latest() {
        return query.latest(authContext.getAccountId());
    }

    @GetMapping("/history")
    public MeasurementWidgetQueryService.History history(@RequestParam Long deviceId,
            @RequestParam DeviceMeasurementType type, @RequestParam String key,
            @RequestParam(defaultValue = "24") int hours) {
        return query.history(authContext.getAccountId(), deviceId, type, key, hours, Instant.now());
    }
}
