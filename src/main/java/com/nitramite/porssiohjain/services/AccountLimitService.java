/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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

@Service
@RequiredArgsConstructor
public class AccountLimitService {

    private static final int FREE_RESOURCE_LIMIT = 4;
    private static final int FREE_DEVICE_LIMIT = 4;
    private static final int PRO_DEVICE_LIMIT = 50;
    private static final int BUSINESS_DEVICE_LIMIT = 99;

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
    public Integer getEffectiveControlLimit(Long accountId) {
        return getAccount(accountId).getTier() == AccountTier.FREE ? FREE_RESOURCE_LIMIT : null;
    }

    @Transactional(readOnly = true)
    public Integer getEffectiveProductionSourceLimit(Long accountId) {
        return getAccount(accountId).getTier() == AccountTier.FREE ? FREE_RESOURCE_LIMIT : null;
    }

    @Transactional(readOnly = true)
    public Integer getEffectiveWeatherControlLimit(Long accountId) {
        return getAccount(accountId).getTier() == AccountTier.FREE ? FREE_RESOURCE_LIMIT : null;
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

    private AccountEntity getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }
}
