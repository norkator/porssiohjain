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
import com.nitramite.porssiohjain.entity.QrLoginChallengeEntity;
import com.nitramite.porssiohjain.entity.enums.QrLoginStatus;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.QrLoginChallengeRepository;
import com.nitramite.porssiohjain.services.models.CreateQrLoginChallengeRequest;
import com.nitramite.porssiohjain.services.models.LoginResponse;
import com.nitramite.porssiohjain.services.models.QrLoginChallengeResponse;
import com.nitramite.porssiohjain.services.models.QrLoginStatusResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QrLoginService {

    private static final Duration CHALLENGE_TTL = Duration.ofSeconds(120);
    private static final int POLL_INTERVAL_MS = 1500;

    private final QrLoginChallengeRepository qrLoginChallengeRepository;
    private final AccountRepository accountRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public QrLoginChallengeResponse createChallenge(
            String ip,
            String apiBaseUrl,
            CreateQrLoginChallengeRequest request
    ) {
        UUID challengeId = UUID.randomUUID();
        String browserSecret = randomSecret();
        String scanSecret = randomSecret();
        Instant expiresAt = Instant.now().plus(CHALLENGE_TTL);

        QrLoginChallengeEntity challenge = QrLoginChallengeEntity.builder()
                .challengeId(challengeId)
                .browserSecretHash(passwordEncoder.encode(browserSecret))
                .scanSecretHash(passwordEncoder.encode(scanSecret))
                .status(QrLoginStatus.PENDING)
                .createdIp(ip)
                .browserName(trimToNull(request != null ? request.getBrowserName() : null))
                .timeZone(trimToNull(request != null ? request.getTimeZone() : null))
                .expiresAt(expiresAt)
                .build();

        qrLoginChallengeRepository.save(challenge);

        String qrPayload = UriComponentsBuilder.newInstance()
                .scheme("porssiohjain")
                .host("qr-login")
                .queryParam("challengeId", challengeId)
                .queryParam("scanSecret", scanSecret)
                .queryParam("apiBaseUrl", apiBaseUrl)
                .build()
                .toUriString();

        return QrLoginChallengeResponse.builder()
                .challengeId(challengeId)
                .browserSecret(browserSecret)
                .qrPayload(qrPayload)
                .expiresAt(expiresAt)
                .pollIntervalMs(POLL_INTERVAL_MS)
                .build();
    }

    @Transactional
    public QrLoginStatusResponse approveChallenge(UUID challengeId, String scanSecret, Long accountId) {
        QrLoginChallengeEntity challenge = getChallenge(challengeId);
        expireIfNeeded(challenge);

        if (challenge.getStatus() != QrLoginStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "QR login challenge is not pending.");
        }

        if (scanSecret == null || !passwordEncoder.matches(scanSecret, challenge.getScanSecretHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid QR login challenge.");
        }

        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));

        challenge.setAccount(account);
        challenge.setStatus(QrLoginStatus.APPROVED);
        challenge.setApprovedAt(Instant.now());

        return QrLoginStatusResponse.builder()
                .status(challenge.getStatus())
                .expiresAt(challenge.getExpiresAt())
                .build();
    }

    @Transactional
    public Object completeChallenge(UUID challengeId, String browserSecret) {
        QrLoginChallengeEntity challenge = getChallenge(challengeId);
        expireIfNeeded(challenge);

        if (browserSecret == null || !passwordEncoder.matches(browserSecret, challenge.getBrowserSecretHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid QR login challenge.");
        }

        if (challenge.getStatus() == QrLoginStatus.PENDING) {
            return QrLoginStatusResponse.builder()
                    .status(challenge.getStatus())
                    .expiresAt(challenge.getExpiresAt())
                    .build();
        }

        if (challenge.getStatus() != QrLoginStatus.APPROVED || challenge.getAccount() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "QR login challenge cannot be completed.");
        }

        AccountEntity account = challenge.getAccount();
        LoginResponse loginResponse = authService.createTokenForAccount(account);
        challenge.setStatus(QrLoginStatus.CONSUMED);
        challenge.setConsumedAt(Instant.now());

        return loginResponse;
    }

    @Transactional
    public QrLoginStatusResponse cancelChallenge(UUID challengeId, String browserSecret) {
        QrLoginChallengeEntity challenge = getChallenge(challengeId);

        if (browserSecret == null || !passwordEncoder.matches(browserSecret, challenge.getBrowserSecretHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid QR login challenge.");
        }

        if (challenge.getStatus() == QrLoginStatus.PENDING) {
            challenge.setStatus(QrLoginStatus.CANCELLED);
        }

        return QrLoginStatusResponse.builder()
                .status(challenge.getStatus())
                .expiresAt(challenge.getExpiresAt())
                .build();
    }

    private QrLoginChallengeEntity getChallenge(UUID challengeId) {
        return qrLoginChallengeRepository.findByChallengeId(challengeId)
                .orElseThrow(() -> new EntityNotFoundException("QR login challenge not found"));
    }

    private void expireIfNeeded(QrLoginChallengeEntity challenge) {
        if (challenge.getExpiresAt().isAfter(Instant.now())) {
            return;
        }

        if (challenge.getStatus() == QrLoginStatus.PENDING || challenge.getStatus() == QrLoginStatus.APPROVED) {
            challenge.setStatus(QrLoginStatus.EXPIRED);
        }

        throw new ResponseStatusException(HttpStatus.CONFLICT, "QR login challenge has expired.");
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
