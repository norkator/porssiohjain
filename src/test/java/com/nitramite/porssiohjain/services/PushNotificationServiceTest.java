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
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlNotificationEntity;
import com.nitramite.porssiohjain.entity.MarketNotificationEntity;
import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.MarketNotificationMetric;
import com.nitramite.porssiohjain.entity.repository.PushNotificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private PushNotificationTokenRepository pushNotificationTokenRepository;

    @Test
    void controlNotificationPushUsesNotificationNameAndDescription() {
        PushNotificationService pushNotificationService = spy(new PushNotificationService(
                messageSource,
                pushNotificationTokenRepository
        ));
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        ControlEntity control = new ControlEntity();
        control.setId(2L);
        control.setName("Floor heating");
        ControlNotificationEntity notification = ControlNotificationEntity.builder()
                .id(3L)
                .name("Heating active")
                .description("Heating is running")
                .build();
        ZonedDateTime activeSince = ZonedDateTime.parse("2026-01-01T10:00:00Z");

        doReturn(true).when(pushNotificationService).sendToAccount(eq(1L), any(), any(), any());

        pushNotificationService.sendControlNotification(
                account,
                control,
                notification,
                activeSince,
                Locale.ENGLISH
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pushNotificationService).sendToAccount(
                eq(1L),
                eq("Heating active"),
                eq("Heating is running"),
                dataCaptor.capture()
        );
        assertEquals("Heating is running", dataCaptor.getValue().get("description"));
    }

    @Test
    void marketNotificationPushUsesNotificationNameAndDescription() {
        PushNotificationService pushNotificationService = spy(new PushNotificationService(
                messageSource,
                pushNotificationTokenRepository
        ));
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        MarketNotificationEntity notification = MarketNotificationEntity.builder()
                .id(2L)
                .name("Cheap power")
                .description("Price is low")
                .metric(MarketNotificationMetric.CURRENT_PRICE)
                .comparisonType(ComparisonType.LESS_THAN)
                .thresholdPrice(BigDecimal.valueOf(5))
                .build();
        ZonedDateTime detectedAt = ZonedDateTime.parse("2026-01-01T10:00:00Z");

        doReturn(true).when(pushNotificationService).sendToAccount(eq(1L), any(), any(), any());

        pushNotificationService.sendMarketNotification(
                account,
                notification,
                BigDecimal.valueOf(4.5),
                detectedAt,
                Locale.ENGLISH
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pushNotificationService).sendToAccount(
                eq(1L),
                eq("Cheap power"),
                eq("Price is low"),
                dataCaptor.capture()
        );
        assertEquals("Price is low", dataCaptor.getValue().get("description"));
    }
}
