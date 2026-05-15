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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private FmiWeatherService fmiWeatherService;

    @Mock
    private SiteWeatherService siteWeatherService;

    @Test
    void createSiteRejectsUnsupportedWeatherPlace() {
        SiteService service = new SiteService(
                siteRepository,
                accountRepository,
                fmiWeatherService,
                siteWeatherService,
                new FinnishWeatherPlaceService()
        );
        when(accountRepository.findById(1L)).thenReturn(Optional.of(AccountEntity.builder().id(1L).build()));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createSite(1L, "Test", SiteType.HOME, true, "Berlin", "Europe/Helsinki")
        );

        assertEquals(
                "Weather place must be selected from the supported Finnish city list. Other European cities are not supported yet.",
                exception.getMessage()
        );
    }

    @Test
    void createSiteNormalizesSupportedWeatherPlaceCaseInsensitively() {
        SiteService service = new SiteService(
                siteRepository,
                accountRepository,
                fmiWeatherService,
                siteWeatherService,
                new FinnishWeatherPlaceService()
        );
        AccountEntity account = AccountEntity.builder().id(1L).build();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(siteRepository.save(org.mockito.ArgumentMatchers.any(SiteEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SiteEntity site = service.createSite(1L, "Test", SiteType.HOME, true, "helsinki", "Europe/Helsinki");

        assertEquals("Helsinki", site.getWeatherPlace());
        verify(siteWeatherService).fetchForecastForSite(site);
    }
}
