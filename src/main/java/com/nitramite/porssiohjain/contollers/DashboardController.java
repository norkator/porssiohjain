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
