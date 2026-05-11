package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;

@Service
@RequiredArgsConstructor
public class AdminAuthorizationService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public AccountEntity requireAdmin(Long accountId) throws AccessDeniedException {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));
        if (!account.isAdmin()) {
            throw new AccessDeniedException("Admin access required");
        }
        return account;
    }
}
