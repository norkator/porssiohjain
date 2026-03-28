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
import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SiteRepository siteRepository;
    private final AccountRepository accountRepository;
    private final FmiWeatherService fmiWeatherService;
    private final SiteWeatherService siteWeatherService;

    public void createSite(
            Long accountId, String name, SiteType type, Boolean enabled, String weatherPlace
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        SiteEntity site = SiteEntity.builder()
                .name(name)
                .type(type)
                .enabled(enabled)
                .weatherPlace(normalizeWeatherPlace(weatherPlace))
                .account(account)
                .build();
        siteRepository.save(site);
    }

    public void updateSite(Long siteId, String name, SiteType type, Boolean enabled, String weatherPlace) {
        SiteEntity site = siteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));
        site.setName(name);
        site.setType(type);
        site.setEnabled(enabled);
        site.setWeatherPlace(normalizeWeatherPlace(weatherPlace));
        siteRepository.save(site);
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

}
