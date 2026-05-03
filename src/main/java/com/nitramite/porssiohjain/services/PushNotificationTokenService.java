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
import com.nitramite.porssiohjain.entity.PushNotificationTokenEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.PushNotificationTokenRepository;
import com.nitramite.porssiohjain.services.models.PushNotificationTokenResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class PushNotificationTokenService {

    private static final String DEFAULT_PLATFORM = "android";

    private final PushNotificationTokenRepository pushNotificationTokenRepository;
    private final AccountRepository accountRepository;

    public List<PushNotificationTokenResponse> getPushNotificationTokens(Long accountId) {
        return pushNotificationTokenRepository.findByAccountIdAndInvalidatedAtIsNullOrderByUpdatedAtDesc(accountId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public PushNotificationTokenResponse registerPushNotificationToken(
            Long accountId,
            String token,
            String platform,
            String deviceName
    ) {
        String normalizedToken = normalizeToken(token);
        String normalizedPlatform = normalizePlatform(platform);
        String normalizedDeviceName = normalizeDeviceName(deviceName);
        Instant now = Instant.now();

        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));

        PushNotificationTokenEntity entity = pushNotificationTokenRepository.findByToken(normalizedToken)
                .orElseGet(PushNotificationTokenEntity::new);

        entity.setAccount(account);
        entity.setToken(normalizedToken);
        entity.setPlatform(normalizedPlatform);
        entity.setDeviceName(normalizedDeviceName);
        entity.setLastSeenAt(now);
        entity.setInvalidatedAt(null);

        return mapToResponse(pushNotificationTokenRepository.save(entity));
    }

    public void deletePushNotificationToken(Long accountId, Long tokenId) {
        PushNotificationTokenEntity entity = pushNotificationTokenRepository.findByIdAndAccountId(tokenId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Push notification token not found: " + tokenId));
        entity.setInvalidatedAt(Instant.now());
        pushNotificationTokenRepository.save(entity);
    }

    public boolean hasActivePushToken(Long accountId) {
        return pushNotificationTokenRepository.existsByAccountIdAndInvalidatedAtIsNull(accountId);
    }

    private String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Push notification token is required");
        }
        String normalized = token.trim();
        if (normalized.length() > 512) {
            throw new IllegalArgumentException("Push notification token is too long");
        }
        return normalized;
    }

    private String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return DEFAULT_PLATFORM;
        }
        String normalized = platform.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 32) {
            throw new IllegalArgumentException("Push notification platform is too long");
        }
        return normalized;
    }

    private String normalizeDeviceName(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return null;
        }
        String normalized = deviceName.trim();
        if (normalized.length() > 120) {
            throw new IllegalArgumentException("Push notification device name is too long");
        }
        return normalized;
    }

    private PushNotificationTokenResponse mapToResponse(PushNotificationTokenEntity entity) {
        return PushNotificationTokenResponse.builder()
                .id(entity.getId())
                .platform(entity.getPlatform())
                .deviceName(entity.getDeviceName())
                .lastSeenAt(entity.getLastSeenAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
