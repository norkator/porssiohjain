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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final RateLimitService rateLimitService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AccountEntity createAccount(String ip, boolean agreedTerms) {
        if (!rateLimitService.allowAccountCreation(ip)) {
            throw new IllegalStateException("Rate limit exceeded. Try again later.");
        }
        if (!agreedTerms) {
            throw new IllegalArgumentException("Terms of service must be accepted before creating an account.");
        }

        String rawSecret = UUID.randomUUID().toString().replace("-", "");
        String hashedSecret = passwordEncoder.encode(rawSecret);
        Instant now = Instant.now();

        AccountEntity account = AccountEntity.builder()
                .uuid(UUID.randomUUID())
                .secret(hashedSecret)
                .agreedTerms(true)
                .agreedTermsAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        accountRepository.save(account);
        account.setSecret(rawSecret);
        return account;
    }

    @Transactional(readOnly = true)
    public AccountTier getTier(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::getTier)
                .orElse(AccountTier.FREE);
    }

    @Transactional(readOnly = true)
    public String getEmail(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::getEmail)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean getNotifyPowerLimitExceeded(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::isNotifyPowerLimitExceeded)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public String getLocale(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::getLocale)
                .orElse("en");
    }

    @Transactional
    public void updateAccountSettings(
            Long accountId,
            String email,
            boolean notify,
            String locale
    ) {
        accountRepository.findById(accountId).ifPresent(account -> {
            account.setEmail(email);
            account.setNotifyPowerLimitExceeded(notify);
            account.setLocale(locale);
            accountRepository.save(account);
        });
    }

    public UUID getUuidById(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::getUuid)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    public Long getIdByUuid(UUID uuid) {
        return accountRepository.findByUuid(uuid)
                .map(AccountEntity::getId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    @Transactional(readOnly = true)
    public Instant getCreatedAt(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::getCreatedAt)
                .orElse(null);
    }

}
