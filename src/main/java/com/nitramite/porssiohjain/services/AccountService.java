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
import com.nitramite.porssiohjain.services.nordpool.NordpoolMarket;
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

        AccountEntity savedAccount = accountRepository.save(account);

        return AccountEntity.builder()
                .id(savedAccount.getId())
                .uuid(savedAccount.getUuid())
                .secret(rawSecret)
                .locale(savedAccount.getLocale())
                .marketIndexName(savedAccount.getMarketIndexName())
                .email(savedAccount.getEmail())
                .notifyPowerLimitExceeded(savedAccount.isNotifyPowerLimitExceeded())
                .notifyControlActivated(savedAccount.isNotifyControlActivated())
                .notifyDeviceOffline(savedAccount.isNotifyDeviceOffline())
                .notifyDeviceOnline(savedAccount.isNotifyDeviceOnline())
                .emailNotificationsEnabled(savedAccount.isEmailNotificationsEnabled())
                .pushNotificationsEnabled(savedAccount.isPushNotificationsEnabled())
                .tier(savedAccount.getTier())
                .deviceLimit(savedAccount.getDeviceLimit())
                .weeklyEmailNotificationCount(savedAccount.getWeeklyEmailNotificationCount())
                .weeklyEmailNotificationWeekStart(savedAccount.getWeeklyEmailNotificationWeekStart())
                .weeklyPushNotificationCount(savedAccount.getWeeklyPushNotificationCount())
                .weeklyPushNotificationWeekStart(savedAccount.getWeeklyPushNotificationWeekStart())
                .agreedTerms(savedAccount.isAgreedTerms())
                .agreedTermsAt(savedAccount.getAgreedTermsAt())
                .admin(savedAccount.isAdmin())
                .demo(savedAccount.isDemo())
                .blocked(savedAccount.isBlocked())
                .createdAt(savedAccount.getCreatedAt())
                .updatedAt(savedAccount.getUpdatedAt())
                .build();
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
    public boolean getNotifyControlActivated(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::isNotifyControlActivated)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean getNotifyDeviceOffline(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::isNotifyDeviceOffline)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean getNotifyDeviceOnline(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::isNotifyDeviceOnline)
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

    @Transactional(readOnly = true)
    public String getMarketIndexName(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::getMarketIndexName)
                .map(NordpoolMarket::normalize)
                .orElse(NordpoolMarket.DEFAULT_MARKET);
    }

    @Transactional
    public void updateAccountSettings(
            Long accountId,
            String email,
            boolean notifyPowerLimitExceeded,
            boolean notifyControlActivated,
            boolean notifyDeviceOffline,
            boolean notifyDeviceOnline,
            boolean emailNotificationsEnabled,
            boolean pushNotificationsEnabled,
            String locale,
            String marketIndexName
    ) {
        assertWritable(accountId);
        accountRepository.findById(accountId).ifPresent(account -> {
            account.setEmail(email != null && !email.isBlank() ? email.trim() : null);
            account.setNotifyPowerLimitExceeded(notifyPowerLimitExceeded);
            account.setNotifyControlActivated(notifyControlActivated);
            account.setNotifyDeviceOffline(notifyDeviceOffline);
            account.setNotifyDeviceOnline(notifyDeviceOnline);
            account.setEmailNotificationsEnabled(emailNotificationsEnabled);
            account.setPushNotificationsEnabled(pushNotificationsEnabled);
            account.setLocale(locale != null && !locale.isBlank() ? locale.trim() : "en");
            account.setMarketIndexName(NordpoolMarket.normalize(marketIndexName));
            accountRepository.save(account);
        });
    }

    @Transactional
    public boolean changeSecret(Long accountId, String currentSecret, String newSecret) {
        assertWritable(accountId);
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

    @Transactional
    public void deleteAccount(Long accountId) {
        assertWritable(accountId);
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        accountRepository.delete(account);
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

    @Transactional(readOnly = true)
    public boolean isDemoAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::isDemo)
                .orElse(false);
    }

    private void assertWritable(Long accountId) {
        if (isDemoAccount(accountId)) {
            throw new DemoAccountMutationException();
        }
    }

}
