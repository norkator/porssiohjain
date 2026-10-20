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
import com.nitramite.porssiohjain.entity.enums.AccountTier;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceRepository;
import com.nitramite.porssiohjain.entity.repository.WeatherControlRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountLimitServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private ControlRepository controlRepository;

    @Mock
    private ProductionSourceRepository productionSourceRepository;

    @Mock
    private WeatherControlRepository weatherControlRepository;

    @Mock
    private SiteRepository siteRepository;

    private AccountLimitService accountLimitService;

    @BeforeEach
    void setUp() {
        accountLimitService = new AccountLimitService(
                accountRepository,
                deviceRepository,
                controlRepository,
                productionSourceRepository,
                weatherControlRepository, siteRepository
        );
    }

    @Test
    void freeTierAllowsThreeEmailNotificationsPerWeek() {
        AccountEntity account = account(AccountTier.FREE, null, 0);
        when(accountRepository.findWithLockById(1L)).thenReturn(Optional.of(account));

        Instant first = Instant.parse("2026-04-27T08:00:00Z");

        assertTrue(accountLimitService.tryConsumeWeeklyEmailNotification(1L, first));
        assertTrue(accountLimitService.tryConsumeWeeklyEmailNotification(1L, first.plusSeconds(60)));
        assertTrue(accountLimitService.tryConsumeWeeklyEmailNotification(1L, first.plusSeconds(120)));
        assertFalse(accountLimitService.tryConsumeWeeklyEmailNotification(1L, first.plusSeconds(180)));
        assertEquals(3, account.getWeeklyEmailNotificationCount());
        assertEquals(LocalDate.parse("2026-04-27"), account.getWeeklyEmailNotificationWeekStart());
    }

    @Test
    void newWeekResetsNotificationCounter() {
        AccountEntity account = account(AccountTier.FREE, LocalDate.parse("2026-04-20"), 3);
        when(accountRepository.findWithLockById(1L)).thenReturn(Optional.of(account));

        assertTrue(accountLimitService.tryConsumeWeeklyEmailNotification(1L, Instant.parse("2026-04-27T00:00:00Z")));
        assertEquals(1, account.getWeeklyEmailNotificationCount());
        assertEquals(LocalDate.parse("2026-04-27"), account.getWeeklyEmailNotificationWeekStart());
    }

    @Test
    void paidTierUsesHigherWeeklyLimit() {
        AccountEntity account = account(AccountTier.PRO, LocalDate.parse("2026-04-27"), 99);
        when(accountRepository.findWithLockById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertTrue(accountLimitService.tryConsumeWeeklyEmailNotification(1L, Instant.parse("2026-04-27T08:00:00Z")));
        assertFalse(accountLimitService.tryConsumeWeeklyEmailNotification(1L, Instant.parse("2026-04-27T08:01:00Z")));
        assertEquals(100, accountLimitService.getEffectiveWeeklyEmailNotificationLimit(1L));
    }

    @Test
    void pushNotificationCounterIsTrackedSeparately() {
        AccountEntity account = account(AccountTier.FREE, null, 0);
        when(accountRepository.findWithLockById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        Instant first = Instant.parse("2026-04-27T08:00:00Z");

        for (int i = 0; i < 50; i++) {
            assertTrue(accountLimitService.tryConsumeWeeklyPushNotification(1L, first.plusSeconds(i * 60L)));
        }
        assertFalse(accountLimitService.tryConsumeWeeklyPushNotification(1L, first.plusSeconds(3000)));
        assertEquals(50, account.getWeeklyPushNotificationCount());
        assertEquals(LocalDate.parse("2026-04-27"), account.getWeeklyPushNotificationWeekStart());
        assertEquals(50, accountLimitService.getEffectiveWeeklyPushNotificationLimit(1L));
    }

    @ParameterizedTest
    @CsvSource({"FREE, 1", "PRO, 5"})
    void siteQuotaAllowsTheLastSlotAndRejectsAccountsAtOrAboveTheirAllowance(AccountTier tier, int limit) {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(tier, null, 0)));
        assertEquals(limit, accountLimitService.getEffectiveSiteLimit(1L));
        when(siteRepository.countByAccountId(1L)).thenReturn((long) limit - 1);
        assertDoesNotThrow(() -> accountLimitService.assertCanCreateSite(1L));
        when(siteRepository.countByAccountId(1L)).thenReturn((long) limit);
        assertEquals("Site limit reached for this account (" + limit + ").",
                assertThrows(IllegalArgumentException.class, () -> accountLimitService.assertCanCreateSite(1L)).getMessage());
        when(siteRepository.countByAccountId(1L)).thenReturn((long) limit + 10);
        assertThrows(IllegalArgumentException.class, () -> accountLimitService.assertCanCreateSite(1L));
    }

    @Test
    void businessHasNoSiteLimitAndDoesNotUseTheDeviceOverrideAsASiteAllowance() {
        AccountEntity business = account(AccountTier.BUSINESS, null, 0);
        business.setDeviceLimit(1);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(business));
        assertNull(accountLimitService.getEffectiveSiteLimit(1L));
        assertDoesNotThrow(() -> accountLimitService.assertCanCreateSite(1L));
        org.mockito.Mockito.verifyNoInteractions(siteRepository);
    }

    private AccountEntity account(AccountTier tier, LocalDate weekStart, int count) {
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        account.setTier(tier);
        account.setWeeklyEmailNotificationWeekStart(weekStart);
        account.setWeeklyEmailNotificationCount(count);
        return account;
    }
}
