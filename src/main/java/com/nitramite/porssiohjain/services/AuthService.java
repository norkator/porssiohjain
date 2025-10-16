package com.nitramite.porssiohjain.services;


import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.TokenEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.TokenRepository;
import com.nitramite.porssiohjain.services.models.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final TokenRepository tokenRepository;
    private final RateLimitService rateLimitService;

    @Transactional
    public LoginResponse login(
            String ip, UUID uuid, String secret
    ) {
        if (!rateLimitService.allowLogin(ip)) {
            throw new IllegalStateException("Rate limit exceeded. Try again later.");
        }

        AccountEntity account = accountRepository.findByUuid(uuid)
                .filter(a -> a.getSecret().equals(secret))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        TokenEntity token = TokenEntity.builder()
                .token(UUID.randomUUID().toString().replace("-", ""))
                .account(account)
                .build();

        tokenRepository.save(token);

        return LoginResponse.builder()
                .token(token.getToken())
                .expiresAt(token.getExpiresAt())
                .build();
    }

    @Transactional(readOnly = true)
    public AccountEntity authenticate(String tokenValue) {
        TokenEntity token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (token.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new IllegalArgumentException("Token expired");
        }

        return token.getAccount();
    }
}