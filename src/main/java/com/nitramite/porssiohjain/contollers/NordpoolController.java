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

import com.nitramite.porssiohjain.services.NordpoolService;
import com.nitramite.porssiohjain.services.models.TodayPriceChartResponse;
import com.nitramite.porssiohjain.services.models.TodayPriceStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/nordpool")
@RequiredArgsConstructor
public class NordpoolController {

    private final NordpoolService nordpoolService;

    @GetMapping("/today-stats")
    public TodayPriceStatsResponse getTodayStats(
            @RequestParam(required = false) String timezone
    ) {
        return nordpoolService.getTodayStats(null, timezone);
    }

    @GetMapping("/today-chart")
    public TodayPriceChartResponse getTodayChart(
            @RequestParam(required = false) String timezone
    ) {
        return nordpoolService.getTodayChart(null, timezone);
    }
}
