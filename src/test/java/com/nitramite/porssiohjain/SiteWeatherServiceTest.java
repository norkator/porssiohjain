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

package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.entity.repository.SiteWeatherRepository;
import com.nitramite.porssiohjain.services.SiteWeatherService;
import com.nitramite.porssiohjain.services.fmi.FmiWeatherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteWeatherServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private SiteWeatherRepository siteWeatherRepository;

    @Mock
    private FmiWeatherService fmiWeatherService;

    @Test
    void checksTomorrowWeatherDataUsingSiteTimezone() {
        SiteEntity site = SiteEntity.builder()
                .id(1L)
                .name("New York")
                .weatherPlace("New York")
                .timezone("America/New_York")
                .build();
        ZoneId zone = ZoneId.of(site.getTimezone());
        Instant expectedStart = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        Instant expectedEnd = LocalDate.now(zone).plusDays(2).atStartOfDay(zone).toInstant();

        when(siteRepository.findByEnabledTrueAndWeatherPlaceIsNotNull()).thenReturn(List.of(site));
        when(siteWeatherRepository.existsBySiteAndForecastTimeBetween(eq(site), eq(expectedStart), eq(expectedEnd)))
                .thenReturn(true);

        SiteWeatherService service = new SiteWeatherService(siteRepository, siteWeatherRepository, fmiWeatherService);

        boolean result = service.hasWeatherDataForTomorrowForConfiguredSites();

        assertTrue(result);
        verify(siteWeatherRepository).existsBySiteAndForecastTimeBetween(site, expectedStart, expectedEnd);
    }

}
