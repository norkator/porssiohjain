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
    public boolean getEmailNotificationsEnabled(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::isEmailNotificationsEnabled)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean getNotifyPowerLimitExceeded(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::isNotifyPowerLimitExceeded)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean getPushNotificationsEnabled(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::isPushNotificationsEnabled)
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
            boolean notifyPowerLimitExceeded,
            boolean emailNotificationsEnabled,
            boolean pushNotificationsEnabled,
            String locale
    ) {
        accountRepository.findById(accountId).ifPresent(account -> {
            account.setEmail(email != null && !email.isBlank() ? email.trim() : null);
            account.setNotifyPowerLimitExceeded(notifyPowerLimitExceeded);
            account.setEmailNotificationsEnabled(emailNotificationsEnabled);
            account.setPushNotificationsEnabled(pushNotificationsEnabled);
            account.setLocale(locale != null && !locale.isBlank() ? locale.trim() : "en");
            accountRepository.save(account);
        });
    }

    @Transactional
    public boolean changeSecret(Long accountId, String currentSecret, String newSecret) {
        if (!isValidSecret(newSecret)) {
            throw new IllegalArgumentException("New password does not meet requirements.");
        }

        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (!passwordEncoder.matches(currentSecret, account.getSecret())) {
            return false;
        }

        account.setSecret(passwordEncoder.encode(newSecret));
        accountRepository.save(account);
        return true;
    }

    public static boolean isValidSecret(String secret) {
        if (secret == null || secret.length() < 8) {
            return false;
        }
        return secret.chars().anyMatch(Character::isLetter)
                && secret.chars().anyMatch(Character::isDigit)
                && secret.chars().anyMatch(Character::isUpperCase);
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
