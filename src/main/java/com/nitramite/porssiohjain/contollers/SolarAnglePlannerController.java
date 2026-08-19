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

import com.nitramite.porssiohjain.services.models.SolarAngleRecommendationResponse;
import com.nitramite.porssiohjain.services.solar.SolarAnglePlannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solar-angle-planner")
@RequiredArgsConstructor
public class SolarAnglePlannerController {

    private final SolarAnglePlannerService solarAnglePlannerService;

    @GetMapping("/recommendation")
    public SolarAngleRecommendationResponse recommendation(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "Europe/Helsinki") String timezone,
            @RequestParam(defaultValue = "35") double currentTilt,
            @RequestParam(defaultValue = "180") double currentAzimuth,
            @RequestParam(defaultValue = "2") double toleranceDegrees
    ) {
        return solarAnglePlannerService.calculateRecommendation(
                latitude,
                longitude,
                timezone,
                currentTilt,
                currentAzimuth,
                toleranceDegrees
        );
    }
}
