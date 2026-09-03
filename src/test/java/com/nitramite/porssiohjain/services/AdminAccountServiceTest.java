/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.PushNotificationTokenRepository;
import com.nitramite.porssiohjain.entity.repository.TokenRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeGatewayStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {
    @Mock AccountRepository accounts;
    @Mock DeviceRepository devices;
    @Mock PushNotificationTokenRepository pushTokens;
    @Mock TokenRepository tokens;
    @Mock ZigbeeGatewayStatusRepository gatewayStatuses;

    AdminAccountService service;

    @BeforeEach
    void setUp() {
        service = new AdminAccountService(accounts, devices, pushTokens, tokens, gatewayStatuses);
    }

    @Test
    void latestDevicePollingCountsAsAccountActivity() {
        Instant accountActivity = Instant.parse("2026-08-10T10:00:00Z");
        Instant devicePoll = Instant.parse("2026-08-14T10:00:00Z");
        AccountEntity account = new AccountEntity();
        account.setId(7L);
        account.setUpdatedAt(accountActivity);
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(devices.findLatestLastCommunicationByAccountId(7L)).thenReturn(Optional.of(devicePoll));
        when(gatewayStatuses.findLatestLastSeenByAccountId(7L)).thenReturn(Optional.empty());
        when(pushTokens.findLatestLastSeenAtByAccountId(7L)).thenReturn(Optional.empty());
        when(tokens.findLatestExpiresAtByAccountId(7L)).thenReturn(Optional.empty());

        assertEquals(Optional.of(devicePoll), service.getLastActivity(7L));
    }

    @Test
    void zigbeeGatewayPollingCountsEvenWithoutDeviceReports() {
        Instant gatewayPoll = Instant.parse("2026-08-14T11:00:00Z");
        AccountEntity account = new AccountEntity();
        account.setId(7L);
        account.setUpdatedAt(Instant.parse("2026-08-10T10:00:00Z"));
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(devices.findLatestLastCommunicationByAccountId(7L)).thenReturn(Optional.empty());
        when(gatewayStatuses.findLatestLastSeenByAccountId(7L)).thenReturn(Optional.of(gatewayPoll));
        when(pushTokens.findLatestLastSeenAtByAccountId(7L)).thenReturn(Optional.empty());
        when(tokens.findLatestExpiresAtByAccountId(7L)).thenReturn(Optional.empty());

        assertEquals(Optional.of(gatewayPoll), service.getLastActivity(7L));
    }

    @Test
    void accessTokenExpiryIsConvertedBackToIssueTimeForActivity() {
        Instant tokenIssuedAt = Instant.parse("2026-08-14T21:30:00Z");
        Instant tokenExpiresAt = Instant.parse("2026-08-15T21:30:00Z");
        AccountEntity account = new AccountEntity();
        account.setId(7L);
        account.setUpdatedAt(Instant.parse("2026-08-10T10:00:00Z"));
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(devices.findLatestLastCommunicationByAccountId(7L)).thenReturn(Optional.empty());
        when(gatewayStatuses.findLatestLastSeenByAccountId(7L)).thenReturn(Optional.empty());
        when(pushTokens.findLatestLastSeenAtByAccountId(7L)).thenReturn(Optional.empty());
        when(tokens.findLatestExpiresAtByAccountId(7L)).thenReturn(Optional.of(tokenExpiresAt));

        assertEquals(Optional.of(tokenIssuedAt), service.getLastActivity(7L));
    }
}
