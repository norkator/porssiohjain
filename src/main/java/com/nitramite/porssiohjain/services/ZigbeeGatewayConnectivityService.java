/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ZigbeeGatewayStatusEntity;
import com.nitramite.porssiohjain.entity.repository.ZigbeeGatewayStatusRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZigbeeGatewayConnectivityService {
    public static final Duration OFFLINE_AFTER = Duration.ofMinutes(15);

    private final ZigbeeGatewayStatusRepository statusRepository;
    private final PushNotificationService pushNotificationService;
    private final PushNotificationTokenService pushNotificationTokenService;
    private final AccountLimitService accountLimitService;
    private final ConnectivityNotificationGuard connectivityNotificationGuard;

    @Transactional
    public void recordHeartbeat(AccountEntity account, UUID gatewayId, Instant seenAt) {
        ZigbeeGatewayStatusEntity status = statusRepository.findByGatewayId(gatewayId).orElse(null);
        if (status == null) {
            statusRepository.save(ZigbeeGatewayStatusEntity.builder()
                    .account(account).gatewayId(gatewayId).lastSeen(seenAt).build());
            return;
        }
        if (!status.getAccount().getId().equals(account.getId())) {
            throw new IllegalStateException("Zigbee gateway belongs to another account");
        }
        boolean cameOnline = status.isOffline();
        status.setLastSeen(seenAt);
        status.setOffline(false);
        status.setOfflineDetectedAt(null);
        statusRepository.save(status);
        if (cameOnline) {
            sendOnline(status, seenAt);
        }
    }

    @Transactional
    public void detectOfflineGateways() {
        Instant detectedAt = Instant.now();
        Instant cutoff = detectedAt.minus(OFFLINE_AFTER);
        for (ZigbeeGatewayStatusEntity status
                : statusRepository.findByOfflineFalseAndLastSeenBefore(cutoff)) {
            status.setOffline(true);
            status.setOfflineDetectedAt(detectedAt);
            statusRepository.save(status);
            sendOffline(status, detectedAt);
        }
    }

    private void sendOffline(ZigbeeGatewayStatusEntity status, Instant detectedAt) {
        AccountEntity account = status.getAccount();
        if (!eligible(account, account.isNotifyDeviceOffline(), detectedAt)) return;
        try {
            pushNotificationService.sendZigbeeGatewayOfflineNotification(
                    account, status.getGatewayId(), detectedAt, locale(account));
        } catch (RuntimeException e) {
            log.error("Failed to send Zigbee gateway {} offline push", status.getGatewayId(), e);
        }
    }

    private void sendOnline(ZigbeeGatewayStatusEntity status, Instant detectedAt) {
        AccountEntity account = status.getAccount();
        if (!eligible(account, account.isNotifyDeviceOnline(), detectedAt)) return;
        try {
            pushNotificationService.sendZigbeeGatewayOnlineNotification(
                    account, status.getGatewayId(), detectedAt, locale(account));
        } catch (RuntimeException e) {
            log.error("Failed to send Zigbee gateway {} online push", status.getGatewayId(), e);
        }
    }

    private boolean eligible(AccountEntity account, boolean eventEnabled, Instant detectedAt) {
        if (!eventEnabled || !account.isPushNotificationsEnabled()
                || connectivityNotificationGuard.isMuted(detectedAt)) return false;
        if (!pushNotificationTokenService.hasActivePushToken(account.getId())) return false;
        return accountLimitService.tryConsumeWeeklyPushNotification(account.getId(), detectedAt);
    }

    private Locale locale(AccountEntity account) {
        return Locale.forLanguageTag(account.getLocale() == null ? "en" : account.getLocale());
    }
}
