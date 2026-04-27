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
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.enums.SiteType;
import com.nitramite.porssiohjain.services.SiteService;
import com.nitramite.porssiohjain.services.models.SiteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
@RequireAuth
public class SitesController {

    private final AuthContext authContext;
    private final SiteService siteService;

    @GetMapping
    public List<SiteResponse> listSites() {
        return siteService.getAllSites(authContext.getAccountId());
    }

    @PostMapping
    public SiteResponse createSite(@RequestBody SiteRequest request) {
        SiteEntity site = siteService.createSite(
                authContext.getAccountId(),
                request.name(),
                request.type(),
                request.enabled(),
                request.weatherPlace(),
                request.timezone()
        );
        return siteService.getAllSites(authContext.getAccountId()).stream()
                .filter(item -> item.getId().equals(site.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Created site not found"));
    }

    @PutMapping("/{siteId}")
    public SiteResponse updateSite(@PathVariable Long siteId, @RequestBody SiteRequest request) {
        siteService.updateSite(
                authContext.getAccountId(),
                siteId,
                request.name(),
                request.type(),
                request.enabled(),
                request.weatherPlace(),
                request.timezone()
        );
        return siteService.getAllSites(authContext.getAccountId()).stream()
                .filter(site -> site.getId().equals(siteId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Updated site not found"));
    }

    public record SiteRequest(
            String name,
            SiteType type,
            boolean enabled,
            String weatherPlace,
            String timezone
    ) {
    }
}
