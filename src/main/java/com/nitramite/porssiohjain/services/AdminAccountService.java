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
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.PushNotificationTokenRepository;
import com.nitramite.porssiohjain.entity.repository.TokenRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AccountRepository accountRepository;
    private final DeviceRepository deviceRepository;
    private final PushNotificationTokenRepository pushNotificationTokenRepository;
    private final TokenRepository tokenRepository;

    @Transactional(readOnly = true)
    public Optional<Instant> getLastActivity(Long accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));

        return Stream.of(
                        Optional.ofNullable(account.getUpdatedAt()),
                        deviceRepository.findLatestLastCommunicationByAccountId(accountId),
                        pushNotificationTokenRepository.findLatestLastSeenAtByAccountId(accountId),
                        tokenRepository.findLatestExpiresAtByAccountId(accountId)
                )
                .flatMap(Optional::stream)
                .max(Instant::compareTo);
    }

    @Transactional
    public void deleteAccountAsAdmin(Long adminAccountId, Long targetAccountId) {
        AccountEntity adminAccount = accountRepository.findById(adminAccountId)
                .orElseThrow(() -> new EntityNotFoundException("Admin account not found: " + adminAccountId));
        if (!adminAccount.isAdmin()) {
            throw new IllegalArgumentException("Admin access required");
        }
        if (Objects.equals(adminAccountId, targetAccountId)) {
            throw new IllegalArgumentException("Cannot delete the current admin account.");
        }

        AccountEntity targetAccount = accountRepository.findById(targetAccountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + targetAccountId));
        accountRepository.delete(targetAccount);
    }
}
