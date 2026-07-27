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
import com.nitramite.porssiohjain.entity.RefreshTokenEntity;
import com.nitramite.porssiohjain.entity.TokenEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.RefreshTokenRepository;
import com.nitramite.porssiohjain.entity.repository.TokenRepository;
import com.nitramite.porssiohjain.services.models.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final TokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RateLimitService rateLimitService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.refresh-token-lifetime-days:7}")
    private long refreshTokenLifetimeDays;

    @Transactional
    public LoginResponse login(String ip, UUID uuid, String secret) {
        AccountEntity account = accountRepository.findByUuid(uuid)
                .orElseThrow(() -> invalidCredentials(ip));

        if (!passwordEncoder.matches(secret, account.getSecret())) {
            throw invalidCredentials(ip);
        }
        assertNotBlocked(account);

        rateLimitService.resetLoginFailures(ip);

        return createTokenForAccount(account);
    }

    @Transactional
    public LoginResponse createTokenForAccount(AccountEntity account) {
        assertNotBlocked(account);

        return createTokenPair(account);
    }

    @Transactional
    public LoginResponse refresh(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        RefreshTokenEntity refreshToken = refreshTokenRepository
                .findByTokenHashWithAccount(hashToken(refreshTokenValue))
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        Instant now = Instant.now();
        if (refreshToken.getRevokedAt() != null || !refreshToken.getExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }

        assertNotBlocked(refreshToken.getAccount());
        refreshToken.setRevokedAt(now);
        return createTokenPair(refreshToken.getAccount());
    }

    @Transactional
    public void revokeRefreshToken(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashWithAccount(hashToken(refreshTokenValue))
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    private LoginResponse createTokenPair(AccountEntity account) {
        TokenEntity token = TokenEntity.builder()
                .token(UUID.randomUUID().toString().replace("-", ""))
                .account(account)
                .build();

        tokenRepository.save(token);

        String refreshTokenValue = randomToken();
        Instant refreshExpiresAt = Instant.now().plusSeconds(refreshTokenLifetimeDays * 24 * 60 * 60);
        refreshTokenRepository.save(RefreshTokenEntity.builder()
                .tokenHash(hashToken(refreshTokenValue))
                .account(account)
                .createdAt(Instant.now())
                .expiresAt(refreshExpiresAt)
                .build());

        return LoginResponse.builder()
                .token(token.getToken())
                .expiresAt(token.getExpiresAt())
                .refreshToken(refreshTokenValue)
                .refreshTokenExpiresAt(refreshExpiresAt)
                .accountId(account.getId())
                .locale(account.getLocale())
                .demo(account.isDemo())
                .build();
    }

    private IllegalArgumentException invalidCredentials(String ip) {
        rateLimitService.recordFailedLogin(ip);
        return new IllegalArgumentException("Invalid credentials");
    }

    @Transactional(readOnly = true)
    public AccountEntity authenticate(String tokenValue) {
        TokenEntity token = tokenRepository.findByTokenWithAccount(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (token.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new IllegalArgumentException("Token expired");
        }
        assertNotBlocked(token.getAccount());

        return token.getAccount();
    }

    @Transactional(readOnly = true)
    public AccountEntity getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    @Transactional
    public void deleteExpiredTokens() {
        tokenRepository.deleteAllExpiredTokens(Instant.now());
        refreshTokenRepository.deleteAllExpiredTokens(Instant.now());
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void assertNotBlocked(AccountEntity account) {
        if (account.isBlocked()) {
            throw new IllegalArgumentException("Account is blocked");
        }
    }

}
