package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final RateLimitService rateLimitService;

    @Transactional
    public AccountEntity createAccount(String ip) {
        if (!rateLimitService.allowAccountCreation(ip)) {
            throw new IllegalStateException("Rate limit exceeded. Try again later.");
        }
        AccountEntity account = new AccountEntity();
        return accountRepository.save(account);
    }

}