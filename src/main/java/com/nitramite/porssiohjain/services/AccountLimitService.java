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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

@Service
@RequiredArgsConstructor
public class AccountLimitService {

    private static final int FREE_CONTROL_LIMIT = 4;
    private static final int FREE_PRODUCTION_SOURCE_LIMIT = 1;
    private static final int FREE_WEATHER_CONTROL_LIMIT = 2;
    private static final int FREE_DEVICE_LIMIT = 4;
    private static final int PRO_DEVICE_LIMIT = 50;
    private static final int BUSINESS_DEVICE_LIMIT = 99;
    private static final int FREE_WEEKLY_EMAIL_NOTIFICATION_LIMIT = 3;
    private static final int PAID_WEEKLY_EMAIL_NOTIFICATION_LIMIT = 100;
    private static final int FREE_WEEKLY_PUSH_NOTIFICATION_LIMIT = 10;
    private static final int PAID_WEEKLY_PUSH_NOTIFICATION_LIMIT = 200;

    private final AccountRepository accountRepository;
    private final DeviceRepository deviceRepository;
    private final ControlRepository controlRepository;
    private final ProductionSourceRepository productionSourceRepository;
    private final WeatherControlRepository weatherControlRepository;

    @Transactional(readOnly = true)
    public int getEffectiveDeviceLimit(Long accountId) {
        AccountEntity account = getAccount(accountId);
        if (account.getDeviceLimit() != null) {
            return account.getDeviceLimit();
        }
        return switch (account.getTier()) {
            case FREE -> FREE_DEVICE_LIMIT;
            case PRO -> PRO_DEVICE_LIMIT;
            case BUSINESS -> BUSINESS_DEVICE_LIMIT;
        };
    }

    @Transactional(readOnly = true)
    public AccountTier getTier(Long accountId) {
        return getAccount(accountId).getTier();
    }

    @Transactional(readOnly = true)
    public long getDeviceCount(Long accountId) {
        return deviceRepository.countByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public long getControlCount(Long accountId) {
        return controlRepository.countByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public long getProductionSourceCount(Long accountId) {
        return productionSourceRepository.countByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public long getWeatherControlCount(Long accountId) {
        return weatherControlRepository.countByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public Integer getEffectiveControlLimit(Long accountId) {
        return getAccount(accountId).getTier() == AccountTier.FREE ? FREE_CONTROL_LIMIT : null;
    }

    @Transactional(readOnly = true)
    public Integer getEffectiveProductionSourceLimit(Long accountId) {
        return getAccount(accountId).getTier() == AccountTier.FREE ? FREE_PRODUCTION_SOURCE_LIMIT : null;
    }

    @Transactional(readOnly = true)
    public Integer getEffectiveWeatherControlLimit(Long accountId) {
        return getAccount(accountId).getTier() == AccountTier.FREE ? FREE_WEATHER_CONTROL_LIMIT : null;
    }

    @Transactional(readOnly = true)
    public int getEffectiveWeeklyEmailNotificationLimit(Long accountId) {
        return getWeeklyEmailNotificationLimit(getAccount(accountId).getTier());
    }

    @Transactional(readOnly = true)
    public Integer getEffectiveWeeklyPushNotificationLimit(Long accountId) {
        return getWeeklyPushNotificationLimit(getAccount(accountId).getTier());
    }

    @Transactional(readOnly = true)
    public void assertCanCreateDevice(Long accountId) {
        int limit = getEffectiveDeviceLimit(accountId);
        long currentCount = deviceRepository.countByAccountId(accountId);
        if (currentCount >= limit) {
            throw new IllegalStateException("Device limit reached for this account (" + limit + ").");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanCreateControl(Long accountId) {
        Integer limit = getEffectiveControlLimit(accountId);
        if (limit != null && controlRepository.countByAccountId(accountId) >= limit) {
            throw new IllegalStateException("Control limit reached for this account (" + limit + ").");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanCreateProductionSource(Long accountId) {
        Integer limit = getEffectiveProductionSourceLimit(accountId);
        if (limit != null && productionSourceRepository.countByAccountId(accountId) >= limit) {
            throw new IllegalStateException("Production source limit reached for this account (" + limit + ").");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanCreateWeatherControl(Long accountId) {
        Integer limit = getEffectiveWeatherControlLimit(accountId);
        if (limit != null && weatherControlRepository.countByAccountId(accountId) >= limit) {
            throw new IllegalStateException("Weather control limit reached for this account (" + limit + ").");
        }
    }

    @Transactional
    public boolean tryConsumeWeeklyEmailNotification(Long accountId, Instant now) {
        AccountEntity account = accountRepository.findWithLockById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        return tryConsumeWeeklyNotification(
                account,
                now,
                getWeeklyEmailNotificationLimit(account.getTier()),
                account.getWeeklyEmailNotificationWeekStart(),
                account.getWeeklyEmailNotificationCount(),
                account::setWeeklyEmailNotificationWeekStart,
                account::setWeeklyEmailNotificationCount
        );
    }

    @Transactional
    public boolean tryConsumeWeeklyPushNotification(Long accountId, Instant now) {
        AccountEntity account = accountRepository.findWithLockById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        return tryConsumeWeeklyNotification(
                account,
                now,
                getWeeklyPushNotificationLimit(account.getTier()),
                account.getWeeklyPushNotificationWeekStart(),
                account.getWeeklyPushNotificationCount(),
                account::setWeeklyPushNotificationWeekStart,
                account::setWeeklyPushNotificationCount
        );
    }

    private int getWeeklyEmailNotificationLimit(AccountTier tier) {
        return switch (tier) {
            case FREE -> FREE_WEEKLY_EMAIL_NOTIFICATION_LIMIT;
            case PRO, BUSINESS -> PAID_WEEKLY_EMAIL_NOTIFICATION_LIMIT;
        };
    }

    private int getWeeklyPushNotificationLimit(AccountTier tier) {
        return switch (tier) {
            case FREE -> FREE_WEEKLY_PUSH_NOTIFICATION_LIMIT;
            case PRO, BUSINESS -> PAID_WEEKLY_PUSH_NOTIFICATION_LIMIT;
        };
    }

    private boolean tryConsumeWeeklyNotification(
            AccountEntity account,
            Instant now,
            int limit,
            LocalDate currentWeekStart,
            int currentCount,
            java.util.function.Consumer<LocalDate> weekStartSetter,
            java.util.function.IntConsumer countSetter
    ) {
        LocalDate weekStart = resolveWeekStart(now);
        int count = currentCount;
        if (!weekStart.equals(currentWeekStart)) {
            weekStartSetter.accept(weekStart);
            count = 0;
        }

        if (count >= limit) {
            return false;
        }

        countSetter.accept(count + 1);
        accountRepository.save(account);
        return true;
    }

    private LocalDate resolveWeekStart(Instant now) {
        return now.atZone(ZoneOffset.UTC)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    }

    private AccountEntity getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }
}
