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
import com.nitramite.porssiohjain.entity.TokenEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.TokenRepository;
import com.nitramite.porssiohjain.services.models.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final TokenRepository tokenRepository;
    private final RateLimitService rateLimitService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public LoginResponse login(String ip, UUID uuid, String secret) {
        AccountEntity account = accountRepository.findByUuid(uuid)
                .orElseThrow(() -> invalidCredentials(ip));

        if (!passwordEncoder.matches(secret, account.getSecret())) {
            throw invalidCredentials(ip);
        }

        rateLimitService.resetLoginFailures(ip);

        TokenEntity token = TokenEntity.builder()
                .token(UUID.randomUUID().toString().replace("-", ""))
                .account(account)
                .build();

        tokenRepository.save(token);

        return LoginResponse.builder()
                .token(token.getToken())
                .expiresAt(token.getExpiresAt())
                .accountId(account.getId())
                .locale(account.getLocale())
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

        return token.getAccount();
    }

    @Transactional
    public void deleteExpiredTokens() {
        tokenRepository.deleteAllExpiredTokens(Instant.now());
    }

}
