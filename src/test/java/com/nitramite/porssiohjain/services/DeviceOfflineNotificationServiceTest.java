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
import com.nitramite.porssiohjain.entity.DeviceEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceOfflineNotificationServiceTest {

    @Mock
    private PushNotificationService pushNotificationService;
    @Mock
    private PushNotificationTokenService pushNotificationTokenService;
    @Mock
    private AccountLimitService accountLimitService;

    private DeviceOfflineNotificationService service;

    @BeforeEach
    void setUp() {
        service = new DeviceOfflineNotificationService(
                pushNotificationService,
                pushNotificationTokenService,
                accountLimitService
        );
    }

    @Test
    void sendsPushWhenDeviceLosesLastOnlineState() {
        AccountEntity account = enabledAccount();
        DeviceEntity device = offlineDevice(account);
        Instant detectedAt = Instant.now();

        when(pushNotificationTokenService.hasActivePushToken(1L)).thenReturn(true);
        when(accountLimitService.tryConsumeWeeklyPushNotification(1L, detectedAt)).thenReturn(true);

        service.sendIfDeviceWentOffline(device, true, false, "API", detectedAt);

        verify(pushNotificationService).sendDeviceOfflineNotification(
                eq(account),
                eq(device),
                eq("API"),
                eq(detectedAt),
                eq(Locale.ENGLISH)
        );
    }

    @Test
    void skipsPushWhenDeviceStillHasAnotherOnlinePath() {
        AccountEntity account = enabledAccount();
        DeviceEntity device = offlineDevice(account);
        device.setMqttOnline(true);
        Instant detectedAt = Instant.now();

        service.sendIfDeviceWentOffline(device, true, true, "API", detectedAt);

        verify(pushNotificationService, never()).sendDeviceOfflineNotification(
                eq(account),
                eq(device),
                eq("API"),
                eq(detectedAt),
                eq(Locale.ENGLISH)
        );
    }

    @Test
    void skipsPushWhenAccountToggleIsDisabled() {
        AccountEntity account = enabledAccount();
        account.setNotifyDeviceOffline(false);
        DeviceEntity device = offlineDevice(account);
        Instant detectedAt = Instant.now();

        service.sendIfDeviceWentOffline(device, true, false, "API", detectedAt);

        verify(pushNotificationService, never()).sendDeviceOfflineNotification(
                eq(account),
                eq(device),
                eq("API"),
                eq(detectedAt),
                eq(Locale.ENGLISH)
        );
    }

    private AccountEntity enabledAccount() {
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        account.setLocale("en");
        account.setNotifyDeviceOffline(true);
        account.setPushNotificationsEnabled(true);
        return account;
    }

    private DeviceEntity offlineDevice(AccountEntity account) {
        DeviceEntity device = new DeviceEntity();
        device.setId(10L);
        device.setUuid(UUID.randomUUID());
        device.setDeviceName("Heat pump");
        device.setAccount(account);
        device.setApiOnline(false);
        device.setMqttOnline(false);
        return device;
    }
}
