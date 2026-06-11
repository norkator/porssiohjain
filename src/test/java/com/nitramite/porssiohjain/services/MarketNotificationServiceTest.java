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
import com.nitramite.porssiohjain.entity.MarketNotificationEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.MarketNotificationMetric;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.MarketNotificationRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketNotificationServiceTest {

    @Mock
    private MarketNotificationRepository marketNotificationRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private NordpoolRepository nordpoolRepository;

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

    private MarketNotificationService marketNotificationService;

    @BeforeEach
    void setUp() {
        marketNotificationService = new MarketNotificationService(
                marketNotificationRepository,
                accountRepository,
                nordpoolRepository,
                emailService,
                pushNotificationService,
                pushNotificationTokenService,
                accountLimitService,
                demoAccountGuard
        );
    }

    @Test
    void sendsAgainWhenLastSentWasPreviousLocalDay() {
        Instant now = Instant.parse("2026-01-02T10:00:15Z");
        MarketNotificationEntity notification = notification(now.minusSeconds(24 * 60 * 60));
        when(marketNotificationRepository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(notification));
        when(nordpoolRepository.findFirstByMarketIndexNameAndDeliveryStartLessThanEqualAndDeliveryEndGreaterThan("FI", now, now)).thenReturn(Optional.of(price(now)));
        when(accountLimitService.tryConsumeWeeklyEmailNotification(1L, now)).thenReturn(true);

        marketNotificationService.sendDueNotifications(now);

        verify(emailService).sendMarketNotificationEmail(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(ZonedDateTime.class),
                any()
        );
        verify(marketNotificationRepository).save(notification);
    }

    @Test
    void doesNotSendAgainWhenLastSentWasSameLocalDay() {
        Instant now = Instant.parse("2026-01-02T10:00:15Z");
        MarketNotificationEntity notification = notification(now.minusSeconds(60 * 60));
        when(marketNotificationRepository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(notification));

        marketNotificationService.sendDueNotifications(now);

        verify(emailService, never()).sendMarketNotificationEmail(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(ZonedDateTime.class),
                any()
        );
        verify(marketNotificationRepository, never()).save(any());
    }

    private MarketNotificationEntity notification(Instant lastSentAt) {
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        account.setEmail("user@example.com");
        account.setLocale("en");
        account.setEmailNotificationsEnabled(true);
        account.setPushNotificationsEnabled(false);

        return MarketNotificationEntity.builder()
                .id(1L)
                .account(account)
                .name("Cheap power")
                .description("Price is low")
                .metric(MarketNotificationMetric.CURRENT_PRICE)
                .comparisonType(ComparisonType.GREATER_THAN)
                .thresholdPrice(BigDecimal.ONE)
                .activeFrom(LocalTime.of(0, 0))
                .activeTo(LocalTime.of(23, 59))
                .timezone("UTC")
                .enabled(true)
                .lastSentAt(lastSentAt)
                .createdAt(lastSentAt)
                .updatedAt(lastSentAt)
                .build();
    }

    private NordpoolEntity price(Instant now) {
        NordpoolEntity price = new NordpoolEntity();
        price.setId(1L);
        price.setDeliveryStart(now.minusSeconds(60 * 60));
        price.setDeliveryEnd(now.plusSeconds(60 * 60));
        price.setMarketIndexName("FI");
        price.setPriceFi(BigDecimal.valueOf(20));
        return price;
    }
}
