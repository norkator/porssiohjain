/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ZigbeeGatewayStatusEntity;
import com.nitramite.porssiohjain.entity.repository.ZigbeeGatewayStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZigbeeGatewayConnectivityServiceTest {
    @Mock ZigbeeGatewayStatusRepository statuses;
    @Mock PushNotificationService pushes;
    @Mock PushNotificationTokenService tokens;
    @Mock AccountLimitService limits;
    @Mock ConnectivityNotificationGuard guard;

    ZigbeeGatewayConnectivityService service;
    AccountEntity account;
    UUID gatewayId;

    @BeforeEach
    void setUp() {
        service = new ZigbeeGatewayConnectivityService(statuses, pushes, tokens, limits, guard);
        account = new AccountEntity();
        account.setId(4L);
        account.setLocale("en");
        account.setPushNotificationsEnabled(true);
        account.setNotifyDeviceOffline(true);
        account.setNotifyDeviceOnline(true);
        gatewayId = UUID.randomUUID();
    }

    @Test
    void firstHeartbeatRegistersGatewayWithoutRecoveryNotification() {
        Instant now = Instant.now();
        when(statuses.findByGatewayId(gatewayId)).thenReturn(Optional.empty());

        service.recordHeartbeat(account, gatewayId, now);

        verify(statuses).save(argThat(status -> status.getGatewayId().equals(gatewayId)
                && status.getLastSeen().equals(now) && !status.isOffline()));
        verifyNoInteractions(pushes);
    }

    @Test
    void staleGatewayIsMarkedOfflineAndNotifiedOnce() {
        ZigbeeGatewayStatusEntity status = status(true);
        status.setOffline(false);
        when(statuses.findByOfflineFalseAndLastSeenBefore(any())).thenReturn(List.of(status));
        when(tokens.hasActivePushToken(4L)).thenReturn(true);
        when(limits.tryConsumeWeeklyPushNotification(eq(4L), any())).thenReturn(true);

        service.detectOfflineGateways();

        assertTrue(status.isOffline());
        assertNotNull(status.getOfflineDetectedAt());
        verify(pushes).sendZigbeeGatewayOfflineNotification(
                eq(account), eq(gatewayId), eq(status.getOfflineDetectedAt()), eq(Locale.ENGLISH));
    }

    @Test
    void heartbeatAfterOfflineStateSendsRecoveryNotification() {
        ZigbeeGatewayStatusEntity status = status(true);
        Instant now = Instant.now();
        when(statuses.findByGatewayId(gatewayId)).thenReturn(Optional.of(status));
        when(tokens.hasActivePushToken(4L)).thenReturn(true);
        when(limits.tryConsumeWeeklyPushNotification(4L, now)).thenReturn(true);

        service.recordHeartbeat(account, gatewayId, now);

        assertFalse(status.isOffline());
        assertEquals(now, status.getLastSeen());
        assertNull(status.getOfflineDetectedAt());
        verify(pushes).sendZigbeeGatewayOnlineNotification(
                account, gatewayId, now, Locale.ENGLISH);
    }

    private ZigbeeGatewayStatusEntity status(boolean offline) {
        return ZigbeeGatewayStatusEntity.builder()
                .id(2L).account(account).gatewayId(gatewayId)
                .lastSeen(Instant.now().minusSeconds(1200)).offline(offline)
                .offlineDetectedAt(offline ? Instant.now().minusSeconds(300) : null)
                .build();
    }
}
