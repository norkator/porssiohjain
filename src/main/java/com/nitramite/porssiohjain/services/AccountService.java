package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
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
    public AccountEntity createAccount(String ip) {
        if (!rateLimitService.allowAccountCreation(ip)) {
            throw new IllegalStateException("Rate limit exceeded. Try again later.");
        }

        String rawSecret = UUID.randomUUID().toString().replace("-", "");
        String hashedSecret = passwordEncoder.encode(rawSecret);

        AccountEntity account = AccountEntity.builder()
                .uuid(UUID.randomUUID())
                .secret(hashedSecret)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        accountRepository.save(account);
        account.setSecret(rawSecret);
        return account;
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

    @Transactional
    public void updateEmail(Long accountId, String email) {
        accountRepository.findById(accountId).ifPresent(account -> {
            account.setEmail(email);
            accountRepository.save(account);
        });
    }

    @Transactional
    public void updateNotifyPowerLimitExceeded(Long accountId, boolean notify) {
        accountRepository.findById(accountId).ifPresent(account -> {
            account.setNotifyPowerLimitExceeded(notify);
            accountRepository.save(account);
        });
    }

    @Transactional
    public void updateAccountSettings(Long accountId, String email, boolean notify) {
        accountRepository.findById(accountId).ifPresent(account -> {
            account.setEmail(email);
            account.setNotifyPowerLimitExceeded(notify);
            accountRepository.save(account);
        });
    }

}