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
import com.nitramite.porssiohjain.entity.ProductionNotificationEntity;
import com.nitramite.porssiohjain.entity.ProductionSourceEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionNotificationRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceRepository;
import com.nitramite.porssiohjain.entity.repository.ResourceSharingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionNotificationServiceTest {

    @Mock
    private ProductionNotificationRepository productionNotificationRepository;

    @Mock
    private ProductionSourceRepository productionSourceRepository;

    @Mock
    private ResourceSharingRepository resourceSharingRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PushNotificationService pushNotificationService;

    @Mock
    private PushNotificationTokenService pushNotificationTokenService;

    @Mock
    private AccountLimitService accountLimitService;

    @Mock
    private DemoAccountGuard demoAccountGuard;

    private ProductionNotificationService productionNotificationService;

    @BeforeEach
    void setUp() {
        productionNotificationService = new ProductionNotificationService(
                productionNotificationRepository,
                productionSourceRepository,
                resourceSharingRepository,
                accountRepository,
                emailService,
                pushNotificationService,
                pushNotificationTokenService,
                accountLimitService,
                demoAccountGuard
        );
    }

    @Test
    void sendsNotificationWhenThresholdMatches() {
        Instant now = Instant.parse("2026-01-01T10:00:15Z");
        ProductionNotificationEntity notification = notification(now, BigDecimal.valueOf(8.5));
        when(productionNotificationRepository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(notification));
        when(accountLimitService.tryConsumeWeeklyEmailNotification(1L, now)).thenReturn(true);

        productionNotificationService.sendDueNotifications(now);

        verify(emailService).sendProductionNotificationEmail(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(ZonedDateTime.class),
                any()
        );
    }

    @Test
    void doesNotSendWhenThresholdIsNotReached() {
        Instant now = Instant.parse("2026-01-01T10:00:15Z");
        ProductionNotificationEntity notification = notification(now, BigDecimal.valueOf(2.0));
        when(productionNotificationRepository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(notification));

        productionNotificationService.sendDueNotifications(now);

        verify(emailService, never()).sendProductionNotificationEmail(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(ZonedDateTime.class),
                any()
        );
    }

    @Test
    void weeklyLimitBlocksNotificationSend() {
        Instant now = Instant.parse("2026-01-01T10:00:15Z");
        ProductionNotificationEntity notification = notification(now, BigDecimal.valueOf(8.5));
        when(productionNotificationRepository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(notification));
        when(accountLimitService.tryConsumeWeeklyEmailNotification(1L, now)).thenReturn(false);

        productionNotificationService.sendDueNotifications(now);

        verify(emailService, never()).sendProductionNotificationEmail(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(ZonedDateTime.class),
                any()
        );
    }

    private ProductionNotificationEntity notification(Instant now, BigDecimal currentKw) {
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        account.setEmail("user@example.com");
        account.setLocale("en");
        account.setEmailNotificationsEnabled(true);
        account.setPushNotificationsEnabled(false);

        ProductionSourceEntity source = new ProductionSourceEntity();
        source.setId(1L);
        source.setName("Solar Roof");
        source.setTimezone("UTC");
        source.setEnabled(true);
        source.setCurrentKw(currentKw);

        return ProductionNotificationEntity.builder()
                .id(1L)
                .account(account)
                .productionSource(source)
                .name("Sunny")
                .description("Enough solar")
                .activeFrom(LocalTime.of(0, 0))
                .activeTo(LocalTime.of(23, 59))
                .enabled(true)
                .triggerKw(BigDecimal.valueOf(5.0))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
