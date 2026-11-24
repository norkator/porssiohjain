/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.HeatingPlannerSettingsEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerWoodRecommendationStatus;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.DemoAccountGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/** A narrow manual readiness update; it never records ignition or enables heating control. */
@Service
@RequiredArgsConstructor
public class HeatingPlannerStoveService {
    private final SiteRepository sites;
    private final HeatingPlannerSettingsRepository settings;
    private final HeatingPlannerWoodRecommendationRepository recommendations;
    private final DemoAccountGuard demoGuard;

    @Transactional(readOnly = true)
    public Readiness get(Long accountId, Long siteId) {
        SiteEntity site = ownedSite(accountId, siteId);
        return response(site, settings.findByAccountIdAndSiteId(accountId, siteId).orElse(null));
    }

    @Transactional
    public Readiness set(Long accountId, Long siteId, Boolean loaded, Long recommendationId) {
        SiteEntity site = ownedSite(accountId, siteId);
        if (loaded == null || (recommendationId != null && (recommendationId <= 0 || loaded)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose loaded or not loaded; a recommendation can only be declined");
        demoGuard.assertWritable(accountId);
        var selected = settings.lockByAccountIdAndSiteId(accountId, siteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Heating Planner is not configured for this site"));
        if (recommendationId != null) {
            var recommendation = recommendations.findByIdAndAccountIdAndSiteId(recommendationId, accountId, siteId)
                    .filter(row -> row.getSettings().getId().equals(selected.getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wood recommendation unavailable"));
            // Declining an old advisory still corrects readiness, but never rewrites a recorded burn.
            if (recommendation.getStatus() == HeatingPlannerWoodRecommendationStatus.LIT)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This recommendation already records a burn");
            recommendation.setStatus(HeatingPlannerWoodRecommendationStatus.SKIPPED);
            recommendations.save(recommendation);
        }
        selected.setStoveLoaded(loaded);
        if (!loaded) {
            var outstanding = recommendations.findBySettingsIdAndStatusIn(selected.getId(), List.of(
                    HeatingPlannerWoodRecommendationStatus.PENDING, HeatingPlannerWoodRecommendationStatus.SENT));
            outstanding.forEach(row -> row.setStatus(HeatingPlannerWoodRecommendationStatus.SUPERSEDED));
            recommendations.saveAll(outstanding);
        }
        settings.saveAndFlush(selected);
        return response(site, selected);
    }

    private SiteEntity ownedSite(Long accountId, Long siteId) {
        if (accountId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (siteId == null || siteId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive siteId is required");
        return sites.findByIdAndAccountId(siteId, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site unavailable"));
    }

    private static Readiness response(SiteEntity site, HeatingPlannerSettingsEntity settings) {
        return new Readiness(new Site(site.getId(), site.getName()), settings != null,
                settings == null ? null : settings.isStoveLoaded(), settings == null ? null : settings.getUpdatedAt());
    }

    public record Site(Long id, String name) { }
    public record Readiness(Site site, boolean configured, Boolean stoveLoaded, Instant updatedAt) { }
}
