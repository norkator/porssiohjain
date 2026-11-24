/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerStoveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/heating-planner/stove-readiness")
@RequireAuth
@RequiredArgsConstructor
public class HeatingPlannerStoveController {
    private final AuthContext auth;
    private final HeatingPlannerStoveService service;

    @GetMapping
    public HeatingPlannerStoveService.Readiness get(@RequestParam Long siteId) {
        return service.get(auth.getAccountId(), siteId);
    }

    @PutMapping
    public HeatingPlannerStoveService.Readiness set(@RequestParam Long siteId, @RequestBody Update update) {
        return service.set(auth.getAccountId(), siteId, update.loaded(), update.recommendationId());
    }

    public record Update(Boolean loaded, Long recommendationId) { }
}
