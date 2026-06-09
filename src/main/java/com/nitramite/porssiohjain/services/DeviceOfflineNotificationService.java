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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceOfflineNotificationService {

    private final PushNotificationService pushNotificationService;
    private final PushNotificationTokenService pushNotificationTokenService;
    private final AccountLimitService accountLimitService;
    private final ConnectivityNotificationGuard connectivityNotificationGuard;

    public void sendIfDeviceWentOffline(
            DeviceEntity device,
            boolean wasApiOnline,
            boolean wasMqttOnline,
            String offlineSource,
            Instant detectedAt
    ) {
        boolean wasOnline = wasApiOnline || wasMqttOnline;
        boolean isOnline = device.isApiOnline() || device.isMqttOnline();
        if (!wasOnline || isOnline) {
            return;
        }
        if (connectivityNotificationGuard.isMuted(detectedAt)) {
            log.info("Device {} offline push not sent because connectivity notifications are muted", device.getId());
            return;
        }

        AccountEntity account = device.getAccount();
        if (account == null || !account.isNotifyDeviceOffline() || !account.isPushNotificationsEnabled()) {
            return;
        }
        if (!pushNotificationTokenService.hasActivePushToken(account.getId())) {
            log.info("Device offline push not sent because account {} has no active push tokens", account.getId());
            return;
        }
        if (!accountLimitService.tryConsumeWeeklyPushNotification(account.getId(), detectedAt)) {
            log.info("Device offline push not sent because account {} reached weekly push notification limit", account.getId());
            return;
        }

        Locale locale = Locale.forLanguageTag(account.getLocale() != null ? account.getLocale() : "en");
        try {
            pushNotificationService.sendDeviceOfflineNotification(account, device, offlineSource, detectedAt, locale);
        } catch (RuntimeException e) {
            log.error("Failed to send device {} offline push", device.getId(), e);
        }
    }

    public void sendIfDeviceCameOnline(
            DeviceEntity device,
            boolean wasApiOnline,
            boolean wasMqttOnline,
            String onlineSource,
            Instant detectedAt
    ) {
        boolean wasOnline = wasApiOnline || wasMqttOnline;
        boolean isOnline = device.isApiOnline() || device.isMqttOnline();
        if (wasOnline || !isOnline) {
            return;
        }
        if (connectivityNotificationGuard.isMuted(detectedAt)) {
            log.info("Device {} online push not sent because connectivity notifications are muted", device.getId());
            return;
        }

        AccountEntity account = device.getAccount();
        if (account == null || !account.isNotifyDeviceOnline() || !account.isPushNotificationsEnabled()) {
            return;
        }
        if (!pushNotificationTokenService.hasActivePushToken(account.getId())) {
            log.info("Device online push not sent because account {} has no active push tokens", account.getId());
            return;
        }
        if (!accountLimitService.tryConsumeWeeklyPushNotification(account.getId(), detectedAt)) {
            log.info("Device online push not sent because account {} reached weekly push notification limit", account.getId());
            return;
        }

        Locale locale = Locale.forLanguageTag(account.getLocale() != null ? account.getLocale() : "en");
        try {
            pushNotificationService.sendDeviceOnlineNotification(account, device, onlineSource, detectedAt, locale);
        } catch (RuntimeException e) {
            log.error("Failed to send device {} online push", device.getId(), e);
        }
    }
}
