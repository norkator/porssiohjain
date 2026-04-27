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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.enums.SiteType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.services.fmi.FmiWeatherService;
import com.nitramite.porssiohjain.services.models.SiteResponse;
import com.nitramite.porssiohjain.services.models.SiteWeatherForecastResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SiteRepository siteRepository;
    private final AccountRepository accountRepository;
    private final FmiWeatherService fmiWeatherService;
    private final SiteWeatherService siteWeatherService;

    public SiteEntity createSite(
            Long accountId, String name, SiteType type, Boolean enabled, String weatherPlace, String timezone
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        SiteEntity site = SiteEntity.builder()
                .name(name)
                .type(type)
                .enabled(enabled)
                .weatherPlace(normalizeWeatherPlace(weatherPlace))
                .timezone(normalizeTimezone(timezone))
                .account(account)
                .build();
        site = siteRepository.save(site);
        siteWeatherService.fetchForecastForSite(site);
        return site;
    }

    public SiteEntity updateSite(Long siteId, String name, SiteType type, Boolean enabled, String weatherPlace, String timezone) {
        SiteEntity site = siteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));
        return updateSite(site, name, type, enabled, weatherPlace, timezone);
    }

    public SiteEntity updateSite(Long accountId, Long siteId, String name, SiteType type, Boolean enabled, String weatherPlace, String timezone) {
        SiteEntity site = siteRepository.findByIdAndAccountId(siteId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));
        return updateSite(site, name, type, enabled, weatherPlace, timezone);
    }

    private SiteEntity updateSite(SiteEntity site, String name, SiteType type, Boolean enabled, String weatherPlace, String timezone) {
        site.setName(name);
        site.setType(type);
        site.setEnabled(enabled);
        site.setWeatherPlace(normalizeWeatherPlace(weatherPlace));
        site.setTimezone(normalizeTimezone(timezone));
        site = siteRepository.save(site);
        siteWeatherService.fetchForecastForSite(site);
        return site;
    }

    public List<SiteResponse> getAllSites(Long accountId) {
        return siteRepository.findByAccountId(accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SiteWeatherForecastResponse getSiteWeatherForecast(Long accountId, Long siteId) {
        SiteEntity site = siteRepository.findByIdAndAccountId(siteId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));
        return fmiWeatherService.getForecastForSite(site);
    }

    public SiteWeatherForecastResponse getStoredSiteWeatherForecast(Long accountId, Long siteId, Instant start, Instant end) {
        SiteEntity site = siteRepository.findByIdAndAccountId(siteId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));
        return siteWeatherService.getStoredForecastForSite(site, start, end);
    }

    private SiteResponse toResponse(SiteEntity site) {
        return SiteResponse.builder()
                .id(site.getId())
                .name(site.getName())
                .type(site.getType())
                .enabled(site.getEnabled())
                .weatherPlace(site.getWeatherPlace())
                .timezone(site.getTimezone())
                .createdAt(site.getCreatedAt())
                .updatedAt(site.getUpdatedAt())
                .build();
    }

    private String normalizeWeatherPlace(String weatherPlace) {
        if (weatherPlace == null) {
            return null;
        }
        String trimmed = weatherPlace.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "Europe/Helsinki";
        }
        return ZoneId.of(timezone.trim()).getId();
    }

}
