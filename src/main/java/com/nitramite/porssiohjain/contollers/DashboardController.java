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
import com.nitramite.porssiohjain.services.ControlSavingsService;
import com.nitramite.porssiohjain.services.DashboardQueryService;
import com.nitramite.porssiohjain.services.models.ControlSavingsSummaryResponse;
import com.nitramite.porssiohjain.services.models.DashboardSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@RequireAuth
public class DashboardController {

    private final AuthContext authContext;
    private final DashboardQueryService dashboardQueryService;
    private final ControlSavingsService controlSavingsService;

    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary(
            @RequestParam(required = false) String timezone
    ) {
        return dashboardQueryService.getSummary(authContext.getAccountId(), timezone);
    }

    @GetMapping("/control-savings")
    public ControlSavingsSummaryResponse getControlSavings(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String timezone
    ) {
        return controlSavingsService.getSavings(authContext.getAccountId(), from, to, timezone);
    }
}
