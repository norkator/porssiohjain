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
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.MarketNotificationEntity;
import com.nitramite.porssiohjain.entity.ProductionNotificationEntity;
import com.nitramite.porssiohjain.entity.ProductionSourceEntity;
import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.MarketNotificationMetric;
import com.nitramite.porssiohjain.entity.repository.PushNotificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private PushNotificationTokenRepository pushNotificationTokenRepository;

    @Test
    void newAccountAdminPushUsesAccountDetails() {
        PushNotificationService pushNotificationService = spy(new PushNotificationService(
                messageSource,
                pushNotificationTokenRepository
        ));
        UUID uuid = UUID.fromString("78bc4f55-793d-42fc-8f9b-89fbd00b50fe");
        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        account.setUuid(uuid);
        account.setCreatedAt(createdAt);

        when(messageSource.getMessage(eq("push.admin.newAccount.title"), any(), eq(Locale.ENGLISH)))
                .thenReturn("New account created");
        when(messageSource.getMessage(eq("push.admin.newAccount.body"), any(), eq(Locale.ENGLISH)))
                .thenReturn("New account %s was created.".formatted(uuid));
        doReturn(true).when(pushNotificationService).sendToAdminAccounts(any(), any(), any());

        pushNotificationService.sendNewAccountCreatedAdminNotification(account, Locale.ENGLISH);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pushNotificationService).sendToAdminAccounts(
                eq("New account created"),
                eq("New account %s was created.".formatted(uuid)),
                dataCaptor.capture()
        );
        assertEquals("NEW_ACCOUNT_CREATED", dataCaptor.getValue().get("type"));
        assertEquals("1", dataCaptor.getValue().get("accountId"));
        assertEquals(uuid.toString(), dataCaptor.getValue().get("accountUuid"));
        assertEquals(createdAt.toString(), dataCaptor.getValue().get("createdAt"));
    }

    @Test
    void newDeviceAdminPushUsesDeviceAndAccountDetails() {
        PushNotificationService pushNotificationService = spy(new PushNotificationService(
                messageSource,
                pushNotificationTokenRepository
        ));
        UUID accountUuid = UUID.fromString("a5d74f4c-f1ca-43bf-abca-18f225a4a44d");
        UUID deviceUuid = UUID.fromString("aeb33bb4-af36-4fc0-9f2e-59bc40f4ed8d");
        Instant createdAt = Instant.parse("2026-02-01T10:00:00Z");
        AccountEntity account = new AccountEntity();
        account.setId(2L);
        account.setUuid(accountUuid);
        DeviceEntity device = new DeviceEntity();
        device.setId(3L);
        device.setUuid(deviceUuid);
        device.setDeviceName("Boiler relay");
        device.setDeviceType(DeviceType.STANDARD);
        device.setDevicePlatform(DevicePlatform.OPENBEKEN);
        device.setAccount(account);
        device.setCreatedAt(createdAt);

        when(messageSource.getMessage(eq("push.admin.newDevice.title"), any(), eq(Locale.ENGLISH)))
                .thenReturn("New device added");
        when(messageSource.getMessage(eq("push.admin.newDevice.body"), any(), eq(Locale.ENGLISH)))
                .thenReturn("Device \"Boiler relay\" was added to account %s.".formatted(accountUuid));
        doReturn(true).when(pushNotificationService).sendToAdminAccounts(any(), any(), any());

        pushNotificationService.sendNewDeviceCreatedAdminNotification(device, Locale.ENGLISH);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pushNotificationService).sendToAdminAccounts(
                eq("New device added"),
                eq("Device \"Boiler relay\" was added to account %s.".formatted(accountUuid)),
                dataCaptor.capture()
        );
        assertEquals("NEW_DEVICE_CREATED", dataCaptor.getValue().get("type"));
        assertEquals("3", dataCaptor.getValue().get("deviceId"));
        assertEquals(deviceUuid.toString(), dataCaptor.getValue().get("deviceUuid"));
        assertEquals("Boiler relay", dataCaptor.getValue().get("deviceName"));
        assertEquals("STANDARD", dataCaptor.getValue().get("deviceType"));
        assertEquals("OPENBEKEN", dataCaptor.getValue().get("devicePlatform"));
        assertEquals("2", dataCaptor.getValue().get("accountId"));
        assertEquals(accountUuid.toString(), dataCaptor.getValue().get("accountUuid"));
        assertEquals(createdAt.toString(), dataCaptor.getValue().get("createdAt"));
    }

    @Test
    void systemErrorAdminPushUsesErrorDetails() {
        PushNotificationService pushNotificationService = spy(new PushNotificationService(
                messageSource,
                pushNotificationTokenRepository
        ));
        RuntimeException error = new RuntimeException("Nordpool returned 520");
        doReturn(true).when(pushNotificationService).sendToAdminAccounts(any(), any(), any());

        pushNotificationService.sendSystemErrorAdminNotification("Error fetching Nordpool data", error);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pushNotificationService).sendToAdminAccounts(
                eq("System error"),
                eq("Error fetching Nordpool data: java.lang.RuntimeException: Nordpool returned 520"),
                dataCaptor.capture()
        );
        assertEquals("SYSTEM_ERROR", dataCaptor.getValue().get("type"));
        assertEquals("Error fetching Nordpool data", dataCaptor.getValue().get("context"));
        assertEquals("java.lang.RuntimeException: Nordpool returned 520", dataCaptor.getValue().get("error"));
    }

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

    @Test
    void productionNotificationPushUsesNotificationNameAndDescription() {
        PushNotificationService pushNotificationService = spy(new PushNotificationService(
                messageSource,
                pushNotificationTokenRepository
        ));
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        ProductionSourceEntity source = new ProductionSourceEntity();
        source.setId(2L);
        source.setName("Solar roof");
        source.setCurrentKw(BigDecimal.valueOf(6.5));
        ProductionNotificationEntity notification = ProductionNotificationEntity.builder()
                .id(3L)
                .name("Solar surplus")
                .description("Own production is high")
                .triggerKw(BigDecimal.valueOf(5))
                .build();
        ZonedDateTime detectedAt = ZonedDateTime.parse("2026-01-01T10:00:00Z");

        doReturn(true).when(pushNotificationService).sendToAccount(eq(1L), any(), any(), any());

        pushNotificationService.sendProductionNotification(
                account,
                source,
                notification,
                detectedAt,
                Locale.ENGLISH
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pushNotificationService).sendToAccount(
                eq(1L),
                eq("Solar surplus"),
                eq("Own production is high"),
                dataCaptor.capture()
        );
        assertEquals("Own production is high", dataCaptor.getValue().get("description"));
    }

    @Test
    void heatingPlannerWoodTestUsesCommandPayloadWithoutRealRecommendation() {
        PushNotificationService service = spy(new PushNotificationService(messageSource, pushNotificationTokenRepository));
        AccountEntity account = new AccountEntity();
        account.setId(7L);
        ZonedDateTime notifyAt = ZonedDateTime.parse("2026-01-15T16:15:00+02:00");
        ZonedDateTime startsAt = notifyAt.plusMinutes(45);
        ZonedDateTime endsAt = startsAt.plusHours(6);
        when(messageSource.getMessage(eq("push.heatingPlanner.wood.title"), any(), eq(Locale.ENGLISH)))
                .thenReturn("Time to light the wood stove");
        when(messageSource.getMessage(eq("push.heatingPlanner.wood.body"), any(), eq(Locale.ENGLISH)))
                .thenReturn("Test body");
        doReturn(true).when(service).sendToAccount(eq(7L), any(), any(), any());

        service.sendHeatingPlannerWoodRecommendationTest(account, 8L, "Living room",
                new BigDecimal("8.00"), notifyAt, startsAt, endsAt, Locale.ENGLISH);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(service).sendToAccount(eq(7L), eq("Time to light the wood stove"), eq("Test body"), dataCaptor.capture());
        Map<String, String> data = dataCaptor.getValue();
        assertEquals("HEATING_PLANNER_WOOD_RECOMMENDATION", data.get("type"));
        assertEquals("-1", data.get("recommendationId"));
        assertEquals("true", data.get("test"));
        assertEquals("Living room", data.get("roomName"));
        assertEquals("8.00", data.get("woodAmount"));
    }
}
